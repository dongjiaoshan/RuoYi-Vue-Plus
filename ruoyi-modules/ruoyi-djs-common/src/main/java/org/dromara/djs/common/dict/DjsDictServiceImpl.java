package org.dromara.djs.common.dict;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.djs.common.constant.DictTypeConstants;
import org.dromara.djs.common.constant.DjsAuthConstants;
import org.dromara.djs.common.constant.DjsRedisKey;
import org.dromara.system.domain.vo.SysDictDataVo;
import org.dromara.system.service.ISysDictTypeService;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
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
 *       命中 {@code CacheNames.SYS_DICT} cache，不再读库</li>
 *   <li>{@link #queryAllDjsTypes()} 聚合 38 个 {@link DictTypeConstants} 常量，
 *       Map key 用 {@link LinkedHashMap} 保持 dict_type 字典序，方便 hash 稳定</li>
 *   <li>{@link #currentVersion()} 用 Jackson 序列化 → SHA-256 → hex；先读 redis key
 *       {@link DjsRedisKey#DICT_VERSION}，命中直返，未命中重算后写回（TTL 1h）</li>
 *   <li>{@link #queryFull()} 类似：先读 {@link DjsRedisKey#DICT_FULL}，未命中重算 + 写回</li>
 *   <li>SHA-256 用 JDK {@link MessageDigest}，不引外部依赖</li>
 * </ul>
 *
 * <p>V1 多农场 disabled，所有 redis key 的 farmId 段固定为 {@link DjsAuthConstants#DEFAULT_FARM_ID}，
 * V2 启用时可在 {@link #resolveFarmId()} 内接入 {@code LoginHelper.getCurrentFarmId()}。</p>
 *
 * @author djs
 * @since SYS-INFRA-005
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DjsDictServiceImpl implements IDjsDictService {

    /**
     * 字典版本号 / 全量缓存 TTL。
     */
    private static final Duration CACHE_TTL = Duration.ofHours(1);

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
        String key = DjsRedisKey.DICT_VERSION.formatted(resolveFarmId());
        String cached = redisGet(key);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        // 未命中：触发 queryFull() 内部重算（顺便填充 DICT_FULL 缓存，避免双重计算）
        return queryFull().getVersion();
    }

    @Override
    public DjsDictFullVo queryFull() {
        String farmId = resolveFarmId();
        String fullKey = DjsRedisKey.DICT_FULL.formatted(farmId);

        DjsDictFullVo cached = redisGet(fullKey);
        if (cached != null && cached.getVersion() != null) {
            return cached;
        }

        Map<String, List<SysDictDataVo>> all = queryAllDjsTypes();
        String version = sha256Hex(serialize(all));
        DjsDictFullVo vo = new DjsDictFullVo(version, all);

        redisSet(fullKey, vo, CACHE_TTL);
        redisSet(DjsRedisKey.DICT_VERSION.formatted(farmId), version, CACHE_TTL);

        return vo;
    }

    @Override
    public void evictCache() {
        String farmId = resolveFarmId();
        redisDel(DjsRedisKey.DICT_VERSION.formatted(farmId));
        redisDel(DjsRedisKey.DICT_FULL.formatted(farmId));
        log.info("djs dict cache evicted, farmId={}", farmId);
    }

    /**
     * 当前农场 ID（V1 固定主农场；V2 接入 {@code LoginHelper.getCurrentFarmId()} 即可）。
     */
    protected String resolveFarmId() {
        return DjsAuthConstants.DEFAULT_FARM_ID;
    }

    /**
     * Redis 读操作的薄包装，便于单测覆盖。
     *
     * <p>{@link RedisUtils#getCacheObject(String)} 的静态初始化依赖 Spring context，无法在
     * 纯 Mockito（不启 Spring）单测中直接 {@code mockStatic}；故抽出 protected 钩子，
     * 单测继承本类后覆盖即可，生产代码零成本。</p>
     */
    protected <T> T redisGet(String key) {
        return RedisUtils.getCacheObject(key);
    }

    protected <T> void redisSet(String key, T value, Duration ttl) {
        RedisUtils.setCacheObject(key, value, ttl);
    }

    protected void redisDel(String key) {
        RedisUtils.deleteObject(key);
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
     * 仅供单测：暴露 38 个常量的快照。
     */
    static List<String> allDjsDictTypesForTest() {
        return Arrays.asList(ALL_DJS_DICT_TYPES.toArray(new String[0]));
    }
}
