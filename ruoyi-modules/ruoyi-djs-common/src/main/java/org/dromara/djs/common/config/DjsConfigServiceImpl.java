package org.dromara.djs.common.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.config.ConfigTableConstants.ConfigTable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * djs 业务配置版本聚合服务实现（CROSS-DICT-001）。
 *
 * <p>范式与 {@link org.dromara.djs.common.dict.DjsDictServiceImpl} 同构（SHA-256 实时聚合）：</p>
 * <ul>
 *   <li>枚举 {@link ConfigTableConstants#CONFIG_TABLES} 全部客户可调 config 表，对每张表
 *       走 {@link DjsConfigMapper#selectConfigRows} 只读拿<b>业务参数列</b>实时值。</li>
 *   <li>按表名顺序（{@code CONFIG_TABLES} 已字典序）装进 {@link LinkedHashMap} 保序，
 *       表内行由 SQL {@code orderBy} 稳定排序，保证同一份数据跨进程 / 重启 hash 一致。</li>
 *   <li>{@link #currentVersion()} 每次实时聚合 → Jackson 序列化 → SHA-256 → hex，
 *       <b>不做 djs 层缓存</b>。若叠一层带 TTL 的版本缓存，会出现"后台改完配置、version
 *       被缓存 → 小程序比对永远相等 → 永远不重拉"——这正是选 SHA-256 实时聚合（而非 Redis
 *       自增计数器）的核心优势：version 是配置数据的纯函数，admin 改任一行下次探测自然算出新值，
 *       <b>各 config 控制器代码一行不动</b>（零侵入，无 bump hook）。</li>
 *   <li>只哈希业务参数列，<b>不含</b> 6 个审计字段 + tenant_id + del_unique：否则任何一次保存
 *       （即便值没变 {@code update_time=NOW()}）都会抖 hash → 小程序被无谓重拉，且 hash 不再是
 *       配置值的纯函数。</li>
 *   <li>SHA-256 用 JDK {@link MessageDigest}，不引外部依赖。</li>
 * </ul>
 *
 * @author djs
 * @since CROSS-DICT-001
 */
@Service
@RequiredArgsConstructor
public class DjsConfigServiceImpl implements IDjsConfigService {

    /** V1 单农场固定租户（拦截器关闭，原生 SQL 显式过滤）。 */
    private static final String DEFAULT_TENANT = "1001";

    private final DjsConfigMapper djsConfigMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String currentVersion() {
        return sha256Hex(serialize(aggregate()));
    }

    /**
     * 实时聚合所有可调 config 表的业务参数列。
     *
     * <p>外层 {@link LinkedHashMap} 按表名顺序保序，value 为该表全部行（每行是列名→值的
     * {@link LinkedHashMap}，保列序），整体序列化后 hash 稳定。</p>
     */
    private Map<String, List<Map<String, Object>>> aggregate() {
        Map<String, List<Map<String, Object>>> result =
            new LinkedHashMap<>(ConfigTableConstants.CONFIG_TABLES.size() * 2);
        for (ConfigTable t : ConfigTableConstants.CONFIG_TABLES) {
            String columns = String.join(", ", t.valueColumns());
            List<Map<String, Object>> rows =
                djsConfigMapper.selectConfigRows(t.table(), columns, t.orderBy(), DEFAULT_TENANT);
            result.put(t.table(), rows == null ? List.of() : rows);
        }
        return result;
    }

    /**
     * Jackson 序列化为稳定字符串（{@link LinkedHashMap} 保表名 + 列名顺序，
     * 同一份数据在不同进程 / 重启后 hash 一致）。
     */
    private String serialize(Map<String, List<Map<String, Object>>> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new ServiceException("业务配置序列化失败: " + e.getMessage());
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
}
