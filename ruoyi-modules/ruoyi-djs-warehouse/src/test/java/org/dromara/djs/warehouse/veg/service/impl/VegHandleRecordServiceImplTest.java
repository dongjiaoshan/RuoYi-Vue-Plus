package org.dromara.djs.warehouse.veg.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.veg.domain.query.VegHandleRecordQuery;
import org.dromara.djs.warehouse.veg.domain.vo.VegHandleRecordVo;
import org.dromara.djs.warehouse.veg.mapper.HandleRecordMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VegHandleRecordServiceImpl} 单测（FIX-ADMIN-R130 毛菜间处理记录）。
 *
 * <ol>
 *   <li>happy path：分页透传筛选条件 + 结果按 TableDataInfo 回包</li>
 *   <li>query 为 null（无任何筛选项）→ 兜底空 query 对象，不 NPE</li>
 *   <li>导出不分页列表透传同一 query</li>
 * </ol>
 *
 * @author djs
 * @since FIX-ADMIN-R130
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("VegHandleRecordServiceImpl 单元测试（FIX-ADMIN-R130）")
class VegHandleRecordServiceImplTest {

    @Mock
    private HandleRecordMapper handleRecordMapper;

    @InjectMocks
    private VegHandleRecordServiceImpl service;

    /** TenantHelper 是静态工具且依赖 Spring 上下文，单测里固定成 V1 租户 '1001'。 */
    private MockedStatic<TenantHelper> tenantHelper;

    @BeforeEach
    void setUp() {
        tenantHelper = Mockito.mockStatic(TenantHelper.class);
        tenantHelper.when(TenantHelper::getTenantId).thenReturn("1001");
    }

    @AfterEach
    void tearDown() {
        tenantHelper.close();
    }

    private static VegHandleRecordVo row(String statSource, String handleMethod, String weight) {
        VegHandleRecordVo vo = new VegHandleRecordVo();
        vo.setStatSource(statSource);
        vo.setHandleMethod(handleMethod);
        vo.setHandleWeight(new BigDecimal(weight));
        vo.setCropName("糯玉米");
        vo.setPlotCode("A-A1西-1-004");
        vo.setRecorderName("孙仓库");
        return vo;
    }

    @Test
    @DisplayName("分页：两来源行原样回包，筛选条件透传 mapper")
    void queryPage_happyPath() {
        Page<VegHandleRecordVo> page = new Page<>(1, 10);
        page.setRecords(List.of(row("1", "veg_fresh", "35.000"), row("2", "platform", "100.000")));
        page.setTotal(2);
        when(handleRecordMapper.selectVegHandleRecordPage(any(), eq("1001"), any(VegHandleRecordQuery.class)))
            .thenReturn(page);

        VegHandleRecordQuery q = new VegHandleRecordQuery();
        q.setCropName("玉米");
        q.setStatSource("2");
        q.setHandleMethod("platform");

        TableDataInfo<VegHandleRecordVo> result = service.queryPage(q, new PageQuery(1, 10));

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRows().get(0).getStatSource()).isEqualTo("1");
        assertThat(result.getRows().get(1).getHandleMethod()).isEqualTo("platform");
        verify(handleRecordMapper).selectVegHandleRecordPage(any(), eq("1001"), eq(q));
    }

    @Test
    @DisplayName("分页：query 为 null 时兜底空对象，不 NPE")
    void queryPage_nullQuery() {
        Page<VegHandleRecordVo> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(handleRecordMapper.selectVegHandleRecordPage(any(), eq("1001"), any(VegHandleRecordQuery.class)))
            .thenReturn(page);

        TableDataInfo<VegHandleRecordVo> result = service.queryPage(null, new PageQuery(1, 10));

        assertThat(result.getTotal()).isZero();
        verify(handleRecordMapper).selectVegHandleRecordPage(any(), eq("1001"), any(VegHandleRecordQuery.class));
    }

    @Test
    @DisplayName("导出：不分页列表透传同一 query")
    void queryList_happyPath() {
        when(handleRecordMapper.selectVegHandleRecordList(eq("1001"), any(VegHandleRecordQuery.class)))
            .thenReturn(List.of(row("1", "loss", "12.500")));

        VegHandleRecordQuery q = new VegHandleRecordQuery();
        q.setPlotCode("A-A1");

        List<VegHandleRecordVo> list = service.queryList(q);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getHandleMethod()).isEqualTo("loss");
        verify(handleRecordMapper).selectVegHandleRecordList(eq("1001"), eq(q));
    }

    @Test
    @DisplayName("V6 row49：产品名称原样透出 —— 处理流水行有值，结算损耗 / 采摘活动行为空（页面显 '-'）")
    void queryPage_carriesProductName() {
        VegHandleRecordVo handleRow = row("1", "veg_fresh", "35.000");
        handleRow.setProductName("红薯杆");
        // 结算损耗行：地块级跨产品结算，SQL 恒给 NULL
        VegHandleRecordVo lossRow = row("1", "loss", "12.500");
        // 采摘活动行：plant_activity 无产品维度，SQL 恒给 NULL
        VegHandleRecordVo activityRow = row("2", "platform", "100.000");

        Page<VegHandleRecordVo> page = new Page<>(1, 10);
        page.setRecords(List.of(handleRow, lossRow, activityRow));
        page.setTotal(3);
        when(handleRecordMapper.selectVegHandleRecordPage(any(), eq("1001"), any(VegHandleRecordQuery.class)))
            .thenReturn(page);

        TableDataInfo<VegHandleRecordVo> result = service.queryPage(new VegHandleRecordQuery(), new PageQuery(1, 10));

        assertThat(result.getRows()).hasSize(3);
        assertThat(result.getRows().get(0).getProductName()).isEqualTo("红薯杆");
        assertThat(result.getRows().get(1).getProductName()).isNull();
        assertThat(result.getRows().get(2).getProductName()).isNull();
        // 统计来源中文派生不得把产品名覆盖掉
        assertThat(result.getRows().get(0).getStatSourceLabel()).isEqualTo("毛菜处理间");
    }
}
