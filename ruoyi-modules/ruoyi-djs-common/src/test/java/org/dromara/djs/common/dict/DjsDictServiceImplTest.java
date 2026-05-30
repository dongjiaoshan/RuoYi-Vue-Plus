package org.dromara.djs.common.dict;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.djs.common.constant.DictTypeConstants;
import org.dromara.djs.common.constant.DjsRedisKey;
import org.dromara.system.domain.vo.SysDictDataVo;
import org.dromara.system.service.ISysDictTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DjsDictServiceImpl} happy path 单测（纯 Mockito，不启 Spring）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>queryAllDjsTypes：遍历 46 个 dict_type 全部调 ruoyi 一次</li>
 *   <li>currentVersion：redis 命中走缓存 / 未命中触发 queryFull 重算 + 回写</li>
 *   <li>queryFull：未命中 redis → 计算 SHA-256 → 双写 DICT_VERSION + DICT_FULL（TTL 1h）</li>
 *   <li>queryFull：命中 redis 整体缓存直接返，不调 ruoyi service</li>
 *   <li>SHA-256 输出：64 字符小写 hex（不是 MD5 32 字符）</li>
 *   <li>常量加载：DictTypeConstants 反射出来正好 46 项</li>
 * </ul>
 *
 * <p>{@link org.dromara.common.redis.utils.RedisUtils} 的静态初始化依赖 Spring context，
 * 故不能用 {@code mockStatic}；改为继承 {@link DjsDictServiceImpl} 覆盖 4 个 protected
 * 钩子（{@code redisGet / redisSet / redisDel}），用 {@link HashMap} 模拟 redis 状态。</p>
 *
 * @author djs
 * @since SYS-INFRA-005
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DjsDictServiceImpl 单元测试")
@Tag("local")
@Tag("dev")
class DjsDictServiceImplTest {

    @Mock
    private ISysDictTypeService sysDictTypeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 模拟 redis 的内存 map（key→value）。 */
    private Map<String, Object> redisStore;

    /** 记录 setCacheObject 的 TTL，便于断言 TTL=1h。 */
    private Map<String, Duration> redisTtlStore;

    /** 记录 deleteObject 的 key 集合。 */
    private Set<String> redisDeleted;

    private TestableService service;

    /**
     * 用内存 map 模拟 redis 的可测试子类。
     */
    static class TestableService extends DjsDictServiceImpl {
        final Map<String, Object> store;
        final Map<String, Duration> ttlStore;
        final Set<String> deleted;

        TestableService(ISysDictTypeService sysDictTypeService, ObjectMapper objectMapper,
                        Map<String, Object> store, Map<String, Duration> ttlStore, Set<String> deleted) {
            super(sysDictTypeService, objectMapper);
            this.store = store;
            this.ttlStore = ttlStore;
            this.deleted = deleted;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected <T> T redisGet(String key) {
            return (T) store.get(key);
        }

        @Override
        protected <T> void redisSet(String key, T value, Duration ttl) {
            store.put(key, value);
            ttlStore.put(key, ttl);
        }

        @Override
        protected void redisDel(String key) {
            store.remove(key);
            deleted.add(key);
        }
    }

    @BeforeEach
    void setup() {
        redisStore = new HashMap<>();
        redisTtlStore = new HashMap<>();
        redisDeleted = new HashSet<>();
        service = new TestableService(sysDictTypeService, objectMapper, redisStore, redisTtlStore, redisDeleted);

        // 默认 ruoyi 返单元素列表（按 dict_type 区分 value，便于产生不同 hash）
        when(sysDictTypeService.selectDictDataByType(anyString())).thenAnswer(inv -> {
            String dt = inv.getArgument(0);
            SysDictDataVo v = new SysDictDataVo();
            v.setDictType(dt);
            v.setDictLabel("label-" + dt);
            v.setDictValue("v");
            v.setDictSort(0);
            List<SysDictDataVo> list = new ArrayList<>(1);
            list.add(v);
            return list;
        });
    }

    @Test
    @DisplayName("DictTypeConstants 反射出来正好 46 项")
    void allDjsDictTypesCount() {
        assertThat(DjsDictServiceImpl.allDjsDictTypesForTest())
            .hasSize(46)
            .allSatisfy(s -> assertThat(s).startsWith(DictTypeConstants.DJS_PREFIX));
    }

    @Test
    @DisplayName("queryAllDjsTypes：遍历 46 个 dict_type 全部调 ruoyi 一次")
    void queryAllDjsTypes_callsRuoyiOncePerType() {
        Map<String, List<SysDictDataVo>> all = service.queryAllDjsTypes();

        assertThat(all).hasSize(46);
        assertThat(all.keySet()).contains(
            DictTypeConstants.PIG_SEX, DictTypeConstants.PIG_BREED,
            DictTypeConstants.USER_STATUS, DictTypeConstants.WAREHOUSE_TYPE);
        verify(sysDictTypeService, times(46)).selectDictDataByType(anyString());
    }

    @Test
    @DisplayName("queryFull：未命中 redis → 计算 SHA-256 → 双写 DICT_VERSION + DICT_FULL（TTL 1h）")
    void queryFull_cacheMiss_writesBothKeys() {
        DjsDictFullVo vo = service.queryFull();

        assertThat(vo.getVersion()).hasSize(64);                      // SHA-256 hex
        assertThat(vo.getVersion()).matches("[0-9a-f]{64}");          // 小写 hex
        assertThat(vo.getData()).hasSize(46);

        String fullKey = DjsRedisKey.DICT_FULL.formatted("1001");
        String versionKey = DjsRedisKey.DICT_VERSION.formatted("1001");
        assertThat(redisStore).containsKeys(fullKey, versionKey);
        assertThat(redisStore.get(versionKey)).isEqualTo(vo.getVersion());
        assertThat(redisTtlStore.get(fullKey)).isEqualTo(Duration.ofHours(1));
        assertThat(redisTtlStore.get(versionKey)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("queryFull：命中 redis 整体缓存直接返，不调 ruoyi service")
    void queryFull_cacheHit_skipsRuoyi() {
        DjsDictFullVo cached = new DjsDictFullVo("deadbeef".repeat(8), new HashMap<>());
        redisStore.put(DjsRedisKey.DICT_FULL.formatted("1001"), cached);

        DjsDictFullVo vo = service.queryFull();

        assertThat(vo).isSameAs(cached);
        verify(sysDictTypeService, times(0)).selectDictDataByType(anyString());
    }

    @Test
    @DisplayName("currentVersion：redis 命中走缓存，不触发全量计算")
    void currentVersion_cacheHit() {
        String hex = "a".repeat(64);
        redisStore.put(DjsRedisKey.DICT_VERSION.formatted("1001"), hex);

        String v = service.currentVersion();

        assertThat(v).isEqualTo(hex);
        verify(sysDictTypeService, times(0)).selectDictDataByType(anyString());
    }

    @Test
    @DisplayName("currentVersion：redis 未命中 → 触发 queryFull 重算 + 返新 hash")
    void currentVersion_cacheMiss_triggersQueryFull() {
        String v = service.currentVersion();

        assertThat(v).hasSize(64).matches("[0-9a-f]{64}");
        verify(sysDictTypeService, times(46)).selectDictDataByType(anyString());
        // 重算后两个 key 都被写入
        assertThat(redisStore).containsKeys(
            DjsRedisKey.DICT_VERSION.formatted("1001"),
            DjsRedisKey.DICT_FULL.formatted("1001"));
    }

    @Test
    @DisplayName("evictCache：删除 DICT_VERSION + DICT_FULL 两个 redis key")
    void evictCache_deletesBothKeys() {
        redisStore.put(DjsRedisKey.DICT_VERSION.formatted("1001"), "x");
        redisStore.put(DjsRedisKey.DICT_FULL.formatted("1001"), new DjsDictFullVo());

        service.evictCache();

        assertThat(redisDeleted).contains(
            DjsRedisKey.DICT_VERSION.formatted("1001"),
            DjsRedisKey.DICT_FULL.formatted("1001"));
        assertThat(redisStore).doesNotContainKeys(
            DjsRedisKey.DICT_VERSION.formatted("1001"),
            DjsRedisKey.DICT_FULL.formatted("1001"));
    }

    @Test
    @DisplayName("hash 稳定性：相同输入两次重算 version 一致")
    void hash_stableBetweenRuns() {
        DjsDictFullVo first = service.queryFull();
        // 清空 redis 模拟下次重启重算
        redisStore.clear();
        redisTtlStore.clear();
        DjsDictFullVo second = service.queryFull();

        assertThat(second.getVersion()).isEqualTo(first.getVersion());
    }
}
