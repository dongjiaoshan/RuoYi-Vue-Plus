package org.dromara.djs.common.dict;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.constant.DictTypeConstants;
import org.dromara.system.domain.vo.SysDictDataVo;
import org.dromara.system.service.ISysDictTypeService;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * djs 业务字典聚合查询服务实现（SYS-INFRA-005）。
 *
 * <p>实现策略：</p>
 * <ul>
 *   <li>单个 dict_type 拉取走 ruoyi {@link ISysDictTypeService#selectDictDataByType(String)}，
 *       命中 {@code CacheNames.SYS_DICT} cache，不再读库。该 cache 在 admin 改字典
 *       （insert / update / delete）时由 ruoyi 即时维护（{@code @CachePut} / {@code @CacheEvict}），
 *       因此本服务实时聚合即拿到最新数据。</li>
 *   <li>{@link #queryAllDjsTypes()} 聚合 {@link DictTypeConstants} 全部 djs_ 前缀常量，
 *       Map key 用 {@link LinkedHashMap} 保持 dict_type 字典序，保证 hash 稳定。</li>
 *   <li>{@link #currentVersion()} / {@link #queryFull()} 每次实时聚合 → Jackson 序列化
 *       → SHA-256 → hex，<b>不做 djs 层缓存</b>。底层 ruoyi cache 已随字典改动即时失效，
 *       若再叠一层带 TTL 的版本 / 全量缓存，会出现"后台改完字典、小程序仍显示旧值"
 *       （version 被缓存 → 小程序比对永远相等 → 不拉全量）。序列化 + hash 只作用于
 *       已命中内存 cache 的全量字典，开销可忽略。</li>
 *   <li>SHA-256 用 JDK {@link MessageDigest}，不引外部依赖。</li>
 * </ul>
 *
 * @author djs
 * @since SYS-INFRA-005
 */
@Service
@RequiredArgsConstructor
public class DjsDictServiceImpl implements IDjsDictService {

    private final ISysDictTypeService sysDictTypeService;
    private final ObjectMapper objectMapper;

    /**
     * 通过反射拿到 {@link DictTypeConstants} 内所有 {@code public static final String} 字段值，
     * 排除 {@code DJS_PREFIX}。本表是编译期常量，结果可缓存。
     */
    private static final List<String> ALL_DJS_DICT_TYPES = loadAllDjsDictTypes();

    private static List<String> loadAllDjsDictTypes() {
        List<String> list = new ArrayList<>(40);
        for (Field f : DictTypeConstants.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            if (!java.lang.reflect.Modifier.isFinal(f.getModifiers())) continue;
            if (!String.class.equals(f.getType())) continue;
            if ("DJS_PREFIX".equals(f.getName())) continue;
            try {
                String v = (String) f.get(null);
                if (v != null && v.startsWith(DictTypeConstants.DJS_PREFIX)) {
                    list.add(v);
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("read DictTypeConstants.%s failed".formatted(f.getName()), e);
            }
        }
        Collections.sort(list);
        return Collections.unmodifiableList(list);
    }

    @Override
    public Map<String, List<SysDictDataVo>> queryAllDjsTypes() {
        Map<String, List<SysDictDataVo>> result = new LinkedHashMap<>(ALL_DJS_DICT_TYPES.size() * 2);
        for (String dictType : ALL_DJS_DICT_TYPES) {
            List<SysDictDataVo> items = sysDictTypeService.selectDictDataByType(dictType);
            result.put(dictType, items == null ? Collections.emptyList() : items);
        }
        return result;
    }

    @Override
    public String currentVersion() {
        return queryFull().getVersion();
    }

    @Override
    public DjsDictFullVo queryFull() {
        Map<String, List<SysDictDataVo>> all = queryAllDjsTypes();
        String version = sha256Hex(serialize(all));
        return new DjsDictFullVo(version, all);
    }

    /**
     * Jackson 序列化为稳定字符串（{@link LinkedHashMap} 保持 dict_type 字典序，
     * 同一份数据在不同进程 / 重启后 hash 一致）。
     */
    private String serialize(Map<String, List<SysDictDataVo>> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new ServiceException("字典序列化失败: " + e.getMessage());
        }
    }

    /**
     * JDK {@link MessageDigest} 计算 SHA-256，hex 小写输出（64 字符）。
     */
    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 强制实现，理论上不会到这里
            throw new IllegalStateException("SHA-256 unavailable on this JVM", e);
        }
    }

    /**
     * 仅供单测：暴露 djs_ 字典常量快照。
     */
    static List<String> allDjsDictTypesForTest() {
        return Arrays.asList(ALL_DJS_DICT_TYPES.toArray(new String[0]));
    }
}
