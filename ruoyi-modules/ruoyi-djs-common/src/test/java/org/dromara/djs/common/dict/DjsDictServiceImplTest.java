package org.dromara.djs.common.dict;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.djs.common.constant.DictTypeConstants;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 *   <li>queryAllDjsTypes：遍历 32 个 dict_type 全部调 ruoyi 一次</li>
 *   <li>currentVersion / queryFull：每次实时聚合计算 SHA-256（不读写 redis）</li>
 *   <li>SHA-256 输出：64 字符小写 hex（不是 MD5 32 字符）</li>
 *   <li>hash 稳定性：相同字典两次算一致</li>
 *   <li><b>回归（甲方反馈）</b>：底层字典数据变化 → version 立即变化，不残留旧值</li>
 *   <li>常量加载：DictTypeConstants 反射出来正好 32 项</li>
 * </ul>
 *
 * <p>服务实时聚合底层 ruoyi {@code selectDictDataByType}（命中 {@code CacheNames.SYS_DICT}），
 * 自身不碰 redis，故无需模拟 redis 状态，直接 {@code new DjsDictServiceImpl} 即可测。</p>
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

    private DjsDictServiceImpl service;

    @BeforeEach
    void setup() {
        service = new DjsDictServiceImpl(sysDictTypeService, objectMapper);
        // 默认 ruoyi 按 dict_type 返单元素列表，便于不同 dict_type 产生不同 hash
        when(sysDictTypeService.selectDictDataByType(anyString())).thenAnswer(inv ->
            singleItem(inv.getArgument(0), "v"));
    }

    private static List<SysDictDataVo> singleItem(String dictType, String value) {
        SysDictDataVo v = new SysDictDataVo();
        v.setDictType(dictType);
        v.setDictLabel("label-" + value);
        v.setDictValue(value);
        v.setDictSort(0);
        List<SysDictDataVo> list = new ArrayList<>(1);
        list.add(v);
        return list;
    }

    @Test
    @DisplayName("DictTypeConstants 反射出来正好 32 项")
    void allDjsDictTypesCount() {
        assertThat(DjsDictServiceImpl.allDjsDictTypesForTest())
            .hasSize(32)
            .allSatisfy(s -> assertThat(s).startsWith(DictTypeConstants.DJS_PREFIX));
    }

    @Test
    @DisplayName("queryAllDjsTypes：遍历 32 个 dict_type 全部调 ruoyi 一次")
    void queryAllDjsTypes_callsRuoyiOncePerType() {
        Map<String, List<SysDictDataVo>> all = service.queryAllDjsTypes();

        assertThat(all).hasSize(32);
        assertThat(all.keySet()).contains(
            DictTypeConstants.PIG_SEX, DictTypeConstants.STORE_STATUS,
            DictTypeConstants.BUY_CLASS,
            DictTypeConstants.INTRODUCE_TYPE, DictTypeConstants.BARN_TYPE,
            DictTypeConstants.HANDLE_TARGET);
        verify(sysDictTypeService, times(32)).selectDictDataByType(anyString());
    }

    @Test
    @DisplayName("queryFull：实时聚合算 SHA-256（64 hex），返全量 32 项")
    void queryFull_computesRealtime() {
        DjsDictFullVo vo = service.queryFull();

        assertThat(vo.getVersion()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(vo.getData()).hasSize(32);
        verify(sysDictTypeService, times(32)).selectDictDataByType(anyString());
    }

    @Test
    @DisplayName("currentVersion：实时聚合，与 queryFull().version 一致")
    void currentVersion_matchesQueryFull() {
        String version = service.currentVersion();

        assertThat(version).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(version).isEqualTo(service.queryFull().getVersion());
    }

    @Test
    @DisplayName("hash 稳定性：相同字典两次算 version 一致")
    void hash_stableBetweenRuns() {
        String first = service.queryFull().getVersion();
        String second = service.queryFull().getVersion();

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("回归：底层某字典数据改了 → version 立即变（不残留旧值）")
    void version_reflectsDictDataChange() {
        String before = service.currentVersion();

        // 模拟 admin 改了 PIG_SEX 字典：ruoyi SYS_DICT cache 即时更新 →
        // 本服务下次实时聚合拿到新值（旧实现下 version 被缓存 1h，这里会失败）
        when(sysDictTypeService.selectDictDataByType(DictTypeConstants.PIG_SEX))
            .thenReturn(singleItem(DictTypeConstants.PIG_SEX, "CHANGED"));

        String after = service.currentVersion();

        assertThat(after).isNotEqualTo(before);
    }
}
