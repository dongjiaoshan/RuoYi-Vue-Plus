package org.dromara.djs.common.image.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 图片 URL 解析器：把记录自身的 {@code image_oss_id} 批量转成 OSS public URL。
 *
 * <p>记录没有自己的图（{@code image_oss_id} 空）→ 返 null，前端占位兜底。产品 / 作物的图统一在
 * 各自「配置」表单里手动上传，不再有图库自动匹配 / 分类默认图兜底。</p>
 *
 * <p>列表场景用 {@link #resolveList}：先收集所有 ossId 一次性 {@code selectVoBatchIds} 查 {@code sys_oss}，
 * 再逐行取 url，<b>禁 N+1</b>。{@code sys_oss.url} 每次按需批量查（不缓存，url 可能换）。</p>
 *
 * @author djs
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageUrlResolver {

    private final SysOssMapper sysOssMapper;

    /**
     * 单条解析（image_oss_id → public URL）。内部走批量接口，单条用不强求性能。
     *
     * @param imageOssId 记录主图 ossId（可空）
     * @param belongType 分类键（保留入参以兼容批量调用签名，当前不参与解析）
     * @return public URL（ossId 空或查不到 url 返 null）
     */
    public String resolve(String imageOssId, String belongType) {
        return resolveList(List.of(new Item(imageOssId, belongType))).get(0);
    }

    /**
     * 批量解析（列表必用）。入参顺序与返回 URL 顺序一一对应（ossId 空或查不到的位置返 null）。
     *
     * @param items (imageOssId, belongType) 列表
     * @return 与 items 等长的 URL 列表
     */
    public List<String> resolveList(List<Item> items) {
        if (CollUtil.isEmpty(items)) {
            return List.of();
        }
        // 1. 收集所有需要查 url 的 ossId
        Set<String> ossIds = new HashSet<>();
        for (Item it : items) {
            if (StrUtil.isNotBlank(it.imageOssId())) {
                ossIds.add(it.imageOssId());
            }
        }
        Map<String, String> urlMap = batchUrl(ossIds);

        // 2. 逐行取 url
        List<String> result = new ArrayList<>(items.size());
        for (Item it : items) {
            String ossId = it.imageOssId();
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
     * @param imageOssId 主图 ossId
     * @param belongType 分类键（保留以兼容批量调用签名，当前不参与解析）
     */
    public record Item(String imageOssId, String belongType) {
    }

}
