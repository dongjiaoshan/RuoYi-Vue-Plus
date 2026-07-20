package org.dromara.djs.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.djs.common.config.ConfigTableConstants.ConfigTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DjsConfigServiceImpl} happy path 单测（纯 Mockito，不启 Spring）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>currentVersion：实时聚合所有可调 config 表 → SHA-256（64 字符小写 hex）</li>
 *   <li>聚合面：遍历 {@link ConfigTableConstants#CONFIG_TABLES} 每张表只读查一次</li>
 *   <li>hash 稳定性：相同配置数据两次算一致</li>
 *   <li>变化性：某 config 行业务参数改了 → version 立即变（不残留旧值）</li>
 *   <li>纯函数：改回原值 → version 回到原值（反缓存 + 纯函数双证）</li>
 *   <li>租户过滤：Mapper 入参 tenantId 恒为 '1001'（V1 拦截器关闭，显式过滤）</li>
 * </ul>
 *
 * <p>服务通过只读 {@link DjsConfigMapper}（{@code @Select} 返 {@code List<Map>}）拿数据，
 * 不用 {@code LambdaQueryWrapper} method-ref，故无 MP entity lambda cache 依赖，
 * 直接 {@code new DjsConfigServiceImpl} 纯 Mockito 即可测。</p>
 *
 * @author djs
 * @since CROSS-DICT-001
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DjsConfigServiceImpl 单元测试")
@Tag("local")
@Tag("dev")
class DjsConfigServiceImplTest {

    @Mock
    private DjsConfigMapper djsConfigMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DjsConfigServiceImpl service;

    /** 可变的"被测配置快照"，模拟 admin 改表后下次实时聚合拿到新值。 */
    private Map<String, List<Map<String, Object>>> snapshot;

    @BeforeEach
    void setup() {
        service = new DjsConfigServiceImpl(djsConfigMapper, objectMapper);
        snapshot = buildDefaultSnapshot();
        // Mapper 按表名返当前快照对应表的行；service 每次实时聚合都重新读 snapshot（模拟 DB 实时态）
        when(djsConfigMapper.selectConfigRows(anyString(), anyString(), anyString(), anyString()))
            .thenAnswer(inv -> {
                String table = inv.getArgument(0);
                return snapshot.getOrDefault(table, List.of());
            });
    }

    /** 每张可调 config 表造 1 行业务参数（值任意，只要不同表 hash 可区分）。 */
    private Map<String, List<Map<String, Object>>> buildDefaultSnapshot() {
        Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
        for (ConfigTable t : ConfigTableConstants.CONFIG_TABLES) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String col : t.valueColumns()) {
                row.put(col, "v-" + col);
            }
            m.put(t.table(), List.of(row));
        }
        return m;
    }

    @Test
    @DisplayName("currentVersion：实时聚合算 SHA-256，64 字符小写 hex")
    void currentVersion_isSha256Hex() {
        String version = service.currentVersion();

        assertThat(version).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("聚合面：遍历 CONFIG_TABLES 每张表只读查一次，tenant 恒为 '1001'")
    void aggregate_queriesEachTableOnceWithDefaultTenant() {
        service.currentVersion();

        verify(djsConfigMapper, times(ConfigTableConstants.CONFIG_TABLES.size()))
            .selectConfigRows(anyString(), anyString(), anyString(), eq("1001"));
        // 每张白名单表都被查到
        for (ConfigTable t : ConfigTableConstants.CONFIG_TABLES) {
            verify(djsConfigMapper).selectConfigRows(eq(t.table()), anyString(), anyString(), eq("1001"));
        }
    }

    @Test
    @DisplayName("hash 稳定性：相同配置数据两次算 version 一致")
    void hash_stableBetweenRuns() {
        String first = service.currentVersion();
        String second = service.currentVersion();

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("变化性：某 config 业务参数改了 → version 立即变（不残留旧值）")
    void version_reflectsConfigChange() {
        String before = service.currentVersion();

        // 模拟 admin 改了生产周期配置某行：实时聚合拿到新值（旧的 Redis 缓存方案下 version 会残留）
        ConfigTable cycle = ConfigTableConstants.PRODUCTION_CYCLE;
        Map<String, Object> changed = new LinkedHashMap<>();
        for (String col : cycle.valueColumns()) {
            changed.put(col, "CHANGED-" + col);
        }
        snapshot.put(cycle.table(), List.of(changed));

        String after = service.currentVersion();

        assertThat(after).isNotEqualTo(before);
    }

    @Test
    @DisplayName("纯函数：改回原值 → version 回到原值（version 是配置值的纯函数）")
    void version_isPureFunctionOfData() {
        String original = service.currentVersion();

        // 改一行
        ConfigTable boar = ConfigTableConstants.BOAR;
        Map<String, Object> changed = new LinkedHashMap<>();
        for (String col : boar.valueColumns()) {
            changed.put(col, "X-" + col);
        }
        snapshot.put(boar.table(), List.of(changed));
        String afterChange = service.currentVersion();
        assertThat(afterChange).isNotEqualTo(original);

        // 改回原值
        snapshot = buildDefaultSnapshot();
        when(djsConfigMapper.selectConfigRows(anyString(), anyString(), anyString(), anyString()))
            .thenAnswer(inv -> snapshot.getOrDefault(inv.getArgument(0), List.of()));
        String afterRevert = service.currentVersion();

        assertThat(afterRevert).isEqualTo(original);
    }

    @Test
    @DisplayName("空表容错：所有 config 表无行 → 仍返合法 64 hex（不抛错）")
    void emptyTables_stillReturnsValidHash() {
        snapshot.clear();

        String version = service.currentVersion();

        assertThat(version).hasSize(64).matches("[0-9a-f]{64}");
    }
}
