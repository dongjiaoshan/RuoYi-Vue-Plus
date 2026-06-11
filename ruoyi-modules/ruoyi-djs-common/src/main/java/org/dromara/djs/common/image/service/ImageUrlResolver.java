package org.dromara.djs.common.image.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.common.image.domain.DefaultImage;
import org.dromara.djs.common.image.mapper.DefaultImageMapper;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.mapper.SysOssMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 图片 URL 解析器（IMG-LIB-001）—— 4 层兜底 resolver。
 *
 * <pre>
 * imageUrl = resolve(
 *   记录.image_oss_id           // L1 用户手改(source=1)或自动匹配存入(source=0)
 *   ?? 分类默认图[belong_type]   // L2 字段为 null 时，按 belong_type 6 类兜底
 *   ?? 全局默认图               // L3 最后兜底（category_key='global'）
 * ) → 转成 OSS public URL
 * </pre>
 *
 * <p>列表场景必用 {@link #resolveList}：先收集所有候选 ossId（L1 行 + 涉及的默认图）一次性
 * {@code selectVoBatchIds} 查 {@code sys_oss}，再逐行兜底，<b>禁 N+1</b>。</p>
 *
 * <p>默认图（最多 7 行）启动后缓存到内存；{@code sys_oss.url} 每次按需批量查（不缓存，url 可能换）。</p>
 *
 * @author djs
 * @since IMG-LIB-001
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageUrlResolver {

    /**
     * 全局兜底分类键（L3）。
     */
    private static final String GLOBAL_KEY = "global";

    private final SysOssMapper sysOssMapper;
    private final DefaultImageMapper defaultImageMapper;

    /**
     * 单条解析（L1 → L2 → L3 → public URL）。内部仍走批量接口，单条用不强求性能。
     *
     * @param imageOssId       记录主图 ossId（L1，可空）
     * @param belongTypeOrNull 分类键（L2，作物传 "vegetable"，商品传 belong_type；可空）
     * @return public URL（全空兜底也查不到时返 null）
     */
    public String resolve(String imageOssId, String belongTypeOrNull) {
        return resolveList(List.of(new Item(imageOssId, belongTypeOrNull))).get(0);
    }

    /**
     * 批量解析（列表必用）。入参顺序与返回 URL 顺序一一对应（兜底查不到的位置返 null）。
     *
     * @param items (imageOssId, belongType) 列表
     * @return 与 items 等长的 URL 列表
     */
    public List<String> resolveList(List<Item> items) {
        if (CollUtil.isEmpty(items)) {
            return List.of();
        }
        Map<String, DefaultImage> defaultMap = loadDefaults();

        // 1. 收集所有需要查 url 的 ossId：L1 行 ossId + 命中的默认图 ossId
        Set<String> ossIds = new HashSet<>();
        for (Item it : items) {
            String ossId = pickOssId(it, defaultMap);
            if (StrUtil.isNotBlank(ossId)) {
                ossIds.add(ossId);
            }
        }
        Map<String, String> urlMap = batchUrl(ossIds);

        // 2. 逐行兜底 → url
        List<String> result = new ArrayList<>(items.size());
        for (Item it : items) {
            String ossId = pickOssId(it, defaultMap);
            result.add(StrUtil.isBlank(ossId) ? null : urlMap.get(ossId));
        }
        return result;
    }

    /**
     * 批量 ossId → url（一次查 sys_oss，禁 N+1）。
     *
     * @param ossIds 字符串 ossId 集合
     * @return Map(ossIdStr → url)
     */
    public Map<String, String> batchUrl(Collection<String> ossIds) {
        Map<String, String> map = new HashMap<>();
        if (CollUtil.isEmpty(ossIds)) {
            return map;
        }
        List<Long> longIds = new ArrayList<>(ossIds.size());
        for (String s : ossIds) {
            Long l = toLongSafe(s);
            if (l != null) {
                longIds.add(l);
            }
        }
        if (longIds.isEmpty()) {
            return map;
        }
        List<SysOssVo> list = sysOssMapper.selectVoByIds(longIds);
        for (SysOssVo vo : list) {
            if (vo.getOssId() != null && StrUtil.isNotBlank(vo.getUrl())) {
                map.put(String.valueOf(vo.getOssId()), vo.getUrl());
            }
        }
        return map;
    }

    /**
     * 选出一行最终 ossId：L1 image_oss_id → L2 默认图[belongType] → L3 默认图[global]。
     */
    private String pickOssId(Item it, Map<String, DefaultImage> defaultMap) {
        if (StrUtil.isNotBlank(it.imageOssId())) {
            return it.imageOssId();
        }
        if (StrUtil.isNotBlank(it.belongType())) {
            DefaultImage l2 = defaultMap.get(it.belongType());
            if (l2 != null && StrUtil.isNotBlank(l2.getOssId())) {
                return l2.getOssId();
            }
        }
        DefaultImage l3 = defaultMap.get(GLOBAL_KEY);
        return l3 != null ? l3.getOssId() : null;
    }

    /**
     * 加载默认图（最多 7 行；未删行）。每次查 DB（行数极少，不引缓存层保持简单）。
     */
    private Map<String, DefaultImage> loadDefaults() {
        List<DefaultImage> list = defaultImageMapper.selectList(null);
        Map<String, DefaultImage> map = new HashMap<>(list.size());
        for (DefaultImage d : list) {
            map.put(d.getCategoryKey(), d);
        }
        return map;
    }

    /**
     * ossId 字符串 → Long。ossId 由后端 snowflake 生成，合法 long；非法值跳过（返 null）。
     */
    private Long toLongSafe(String s) {
        if (StrUtil.isBlank(s)) {
            return null;
        }
        try {
            return Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            log.warn("非法 ossId（非数字），跳过: {}", s);
            return null;
        }
    }

    /**
     * 解析入参条目。
     *
     * @param imageOssId 主图 ossId（L1）
     * @param belongType 分类键（L2）
     */
    public record Item(String imageOssId, String belongType) {
    }

}
