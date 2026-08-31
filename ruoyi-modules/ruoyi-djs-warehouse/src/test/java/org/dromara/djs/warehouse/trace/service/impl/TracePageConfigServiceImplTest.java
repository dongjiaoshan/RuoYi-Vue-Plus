package org.dromara.djs.warehouse.trace.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.warehouse.trace.domain.TracePageConfig;
import org.dromara.djs.warehouse.trace.domain.bo.TracePageConfigImageBo;
import org.dromara.djs.warehouse.trace.domain.vo.TracePageConfigVo;
import org.dromara.djs.warehouse.trace.mapper.TracePageConfigMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TracePageConfigServiceImpl} 单测（V6-R146 追溯码配置管理）。
 *
 * <p>覆盖：</p>
 * <ol>
 *   <li>queryList happy：两行按 id 升序返回，图片 URL 批量回填</li>
 *   <li>updateImage happy：图片列 + 审计两列进 SET 子句，绑定值 = 新 ossId</li>
 *   <li>updateImage 清空：图片列仍在 SET 子句里、绑定值为 null（H5 据此回落内置版式）</li>
 *   <li>updateImage 行不存在：抛 ServiceException 且不 update</li>
 * </ol>
 *
 * <p><b>为什么断的是 wrapper 的 SET 子句而不是实体字段</b>：清空这条路径唯一会坏的地方，
 * 就是「列有没有真进 UPDATE 语句」——{@code updateById(entity)} 在 MP 默认
 * {@code updateStrategy=NOT_NULL} 下会把 null 列整条剥掉，而实体上的 {@code setXxx(null)} 照样成立。
 * 所以断实体字段的测试对这个 bug 永远绿；只有断「SET 子句里有这一列且绑定值为 null」才锁得住。
 * 每个 update 用例都额外 {@code verify(never()).updateById}，防止回退到实体路径。</p>
 *
 * @author djs
 * @since V6-R146
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TracePageConfigServiceImpl 单元测试")
class TracePageConfigServiceImplTest {

    @Mock
    private TracePageConfigMapper baseMapper;
    @Mock
    private ImageUrlResolver imageUrlResolver;

    private TracePageConfigServiceImpl service;

    private static final Long PORK_ID = 1L;
    private static final Long VEG_ID = 2L;
    private static final String OSS_ID = "1935672310889041921";
    private static final String OSS_URL = "https://djs-staging.oss-cn-hangzhou.aliyuncs.com/trace/base-pork.jpg";
    private static final String COL_IMAGE = "base_intro_image_oss_id";

    /**
     * MyBatis-Plus 单测 entity cache 预热（skill coder-mp-entity-cache-test）：
     * queryList 里的 LambdaQueryWrapper 需要 TableInfoHelper 解析列名。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, TracePageConfig.class);
    }

    @BeforeEach
    void setup() {
        service = new TracePageConfigServiceImpl(baseMapper, imageUrlResolver);
    }

    @Test
    @DisplayName("queryList：固定两行按 id 升序返回，图片 URL 批量回填（未配图为 null）")
    void queryList_happy() {
        TracePageConfigVo pork = new TracePageConfigVo();
        pork.setId(PORK_ID);
        pork.setCodeType("pork");
        pork.setConfigName("猪肉追溯码");
        pork.setBaseIntroImageOssId(OSS_ID);
        TracePageConfigVo veg = new TracePageConfigVo();
        veg.setId(VEG_ID);
        veg.setCodeType("veg");
        veg.setConfigName("果蔬追溯码");

        when(baseMapper.selectVoList(any())).thenReturn(List.of(pork, veg));
        when(imageUrlResolver.resolveList(any())).thenReturn(java.util.Arrays.asList(OSS_URL, null));

        List<TracePageConfigVo> list = service.queryList();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).getConfigName()).isEqualTo("猪肉追溯码");
        assertThat(list.get(0).getBaseIntroImageUrl()).isEqualTo(OSS_URL);
        assertThat(list.get(1).getConfigName()).isEqualTo("果蔬追溯码");
        assertThat(list.get(1).getBaseIntroImageUrl()).isNull();
    }

    @Test
    @DisplayName("updateImage：图片列与 update_by/update_time 一起进 SET 子句，绑定值 = 新 ossId")
    void updateImage_happy() {
        TracePageConfig entity = new TracePageConfig();
        entity.setId(PORK_ID);
        entity.setCodeType("pork");
        when(baseMapper.selectById(PORK_ID)).thenReturn(entity);
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        TracePageConfigImageBo bo = new TracePageConfigImageBo();
        bo.setId(PORK_ID);
        bo.setBaseIntroImageOssId(OSS_ID);

        int rows = service.updateImage(bo);

        assertThat(rows).isEqualTo(1);
        LambdaUpdateWrapper<TracePageConfig> wrapper = captureWrapper();
        assertThat(setValueOf(wrapper, COL_IMAGE)).isEqualTo(OSS_ID);
        // 审计两列：wrapper-only update 不触发 MetaObjectHandler.updateFill，必须由 service 显式 set，
        // 否则列表「更新人 / 更新时间」两列会停在上一次的值
        assertThat(setValueOf(wrapper, "update_by")).isNotNull();
        assertThat(setValueOf(wrapper, "update_time")).isNotNull();
        assertThat(wrapper.getExpression().getNormal().getSqlSegment()).contains("id");
        verify(baseMapper, never()).updateById(any(TracePageConfig.class));
    }

    @Test
    @DisplayName("updateImage：ossId 传空白串 → 图片列仍进 SET 子句、绑定值为 null（真清空，不是静默 no-op）")
    void updateImage_clear() {
        TracePageConfig entity = new TracePageConfig();
        entity.setId(VEG_ID);
        entity.setBaseIntroImageOssId(OSS_ID);
        when(baseMapper.selectById(VEG_ID)).thenReturn(entity);
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        TracePageConfigImageBo bo = new TracePageConfigImageBo();
        bo.setId(VEG_ID);
        bo.setBaseIntroImageOssId("   ");

        service.updateImage(bo);

        LambdaUpdateWrapper<TracePageConfig> wrapper = captureWrapper();
        // 这两条断言合起来 = 「UPDATE ... SET base_intro_image_oss_id = NULL」真的会发给 DB
        assertThat(setClauseColumns(wrapper)).contains(COL_IMAGE);
        assertThat(setValueOf(wrapper, COL_IMAGE)).isNull();
        verify(baseMapper, never()).updateById(any(TracePageConfig.class));
    }

    @Test
    @DisplayName("updateImage：ossId 字段整个不传（null）→ 同样写 NULL 清空")
    void updateImage_clearByOmittedField() {
        TracePageConfig entity = new TracePageConfig();
        entity.setId(VEG_ID);
        entity.setBaseIntroImageOssId(OSS_ID);
        when(baseMapper.selectById(VEG_ID)).thenReturn(entity);
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        TracePageConfigImageBo bo = new TracePageConfigImageBo();
        bo.setId(VEG_ID);

        service.updateImage(bo);

        LambdaUpdateWrapper<TracePageConfig> wrapper = captureWrapper();
        assertThat(setClauseColumns(wrapper)).contains(COL_IMAGE);
        assertThat(setValueOf(wrapper, COL_IMAGE)).isNull();
        verify(baseMapper, never()).updateById(any(TracePageConfig.class));
    }

    @Test
    @DisplayName("updateImage：配置行不存在 → 抛 ServiceException 且不落库")
    void updateImage_notFound() {
        when(baseMapper.selectById(99L)).thenReturn(null);

        TracePageConfigImageBo bo = new TracePageConfigImageBo();
        bo.setId(99L);
        bo.setBaseIntroImageOssId(OSS_ID);

        assertThatThrownBy(() -> service.updateImage(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("追溯码配置不存在");
        verify(baseMapper, never()).updateById(any(TracePageConfig.class));
        verify(baseMapper, never()).update(isNull(), any());
    }

    /* ---------- wrapper SET 子句解析（把 MP 的更新策略锁进断言，别再断实体字段） ---------- */

    /** MP 生成的参数占位：{@code #{ew.paramNameValuePairs.MPGENVAL1}}。 */
    private static final Pattern PARAM_REF = Pattern.compile("#\\{\\w+\\.paramNameValuePairs\\.(\\w+)}");

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<TracePageConfig> captureWrapper() {
        ArgumentCaptor<LambdaUpdateWrapper<TracePageConfig>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(baseMapper).update(isNull(), captor.capture());
        return captor.getValue();
    }

    /** SET 子句里出现的列名集合（{@code "a=#{...},b=#{...}"} → {@code [a, b]}）。 */
    private static List<String> setClauseColumns(LambdaUpdateWrapper<TracePageConfig> wrapper) {
        String sqlSet = wrapper.getSqlSet();
        assertThat(sqlSet).as("SET 子句为空 —— 说明没走 wrapper update").isNotNull();
        return java.util.Arrays.stream(sqlSet.split(","))
            .map(fragment -> fragment.split("=", 2)[0].trim())
            .toList();
    }

    /**
     * 取 SET 子句里某一列实际绑定的值；列不在 SET 子句里直接 fail
     * （这正是「清空静默 no-op」的形态，必须让测试红）。
     */
    private static Object setValueOf(LambdaUpdateWrapper<TracePageConfig> wrapper, String column) {
        String sqlSet = wrapper.getSqlSet();
        assertThat(sqlSet).as("SET 子句为空 —— 说明没走 wrapper update").isNotNull();
        for (String fragment : sqlSet.split(",")) {
            String[] kv = fragment.split("=", 2);
            if (kv.length != 2 || !kv[0].trim().equals(column)) {
                continue;
            }
            String raw = kv[1].trim();
            Matcher matcher = PARAM_REF.matcher(raw);
            if (matcher.matches()) {
                return wrapper.getParamNameValuePairs().get(matcher.group(1));
            }
            if ("null".equalsIgnoreCase(raw)) {
                return null;
            }
            return fail("SET 片段既非参数占位也非字面 null，无法断言绑定值：%s", fragment);
        }
        return fail("SET 子句里没有列 %s（清空会变成静默 no-op）：%s", column, sqlSet);
    }
}
