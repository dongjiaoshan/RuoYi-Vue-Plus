package org.dromara.djs.warehouse.trace.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.trace.domain.TraceCode;
import org.dromara.djs.warehouse.trace.domain.query.TraceCodeQuery;
import org.dromara.djs.warehouse.trace.domain.vo.TraceCodeDetailVo;
import org.dromara.djs.warehouse.trace.domain.vo.TraceCodeListVo;
import org.dromara.djs.warehouse.trace.domain.vo.TraceEventVo;
import org.dromara.djs.warehouse.trace.mapper.TraceCodeMapper;
import org.dromara.djs.warehouse.trace.mapper.TraceEventMapper;
import org.dromara.djs.warehouse.trace.mapper.TraceFarmNameMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link TraceCodeAdminServiceImpl} 单测（TRC-ADMIN-001，admin only 纯只读）。
 *
 * <p>覆盖核心场景：</p>
 * <ol>
 *   <li>queryPage：按 codeType=pork 过滤命中 + 内存 JOIN 填产品名</li>
 *   <li>getDetail：组装主表 + 事件时间轴，断言 events 按 trace_time 升序</li>
 *   <li>getDetail 不存在：抛 ServiceException</li>
 *   <li>getEvents 空 produceCode：返空列表（不查库）</li>
 * </ol>
 *
 * <p>追溯链不可篡改：本 service 无任何写方法，单测仅验证只读组装逻辑。</p>
 *
 * @author djs
 * @since TRC-ADMIN-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TraceCodeAdminServiceImpl 单元测试")
class TraceCodeAdminServiceImplTest {

    @Mock private TraceCodeMapper traceCodeMapper;
    @Mock private TraceEventMapper traceEventMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private StoreMapper storeMapper;
    @Mock private PlotInfoMapper plotInfoMapper;
    @Mock private TraceFarmNameMapper traceFarmNameMapper;

    private TraceCodeAdminServiceImpl service;
    private MockedStatic<TenantHelper> tenantHelperMock;

    private static final Long CODE_ID = 70001L;
    private static final Long PRODUCT_ID = 8001L;
    private static final String PRODUCE_CODE = "T20260601PK000001";
    private static final String TENANT = "1001";

    /**
     * MyBatis-Plus 单测 entity cache 预热（skill coder-mp-entity-cache-test）：service 内
     * LambdaQueryWrapper 在 mock 路径下也可能触发 TableInfoHelper.getTableInfo() 解析 lambda 列名。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, TraceCode.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
        TableInfoHelper.initTableInfo(assistant, Store.class);
    }

    @BeforeEach
    void setup() {
        service = new TraceCodeAdminServiceImpl(
            traceCodeMapper, traceEventMapper, productInfoMapper, storeMapper, plotInfoMapper, traceFarmNameMapper);
        tenantHelperMock = Mockito.mockStatic(TenantHelper.class);
        tenantHelperMock.when(TenantHelper::getTenantId).thenReturn(TENANT);
    }

    @AfterEach
    void tearDown() {
        tenantHelperMock.close();
    }

    private TraceCode porkCode() {
        TraceCode c = new TraceCode();
        c.setId(CODE_ID);
        c.setProduceCode(PRODUCE_CODE);
        c.setCodeType("pork");
        c.setProductId(PRODUCT_ID);
        c.setPigEarNo("E20260601001");
        c.setCreateTime(Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()));
        return c;
    }

    private ProductInfo product() {
        ProductInfo p = new ProductInfo();
        p.setId(PRODUCT_ID);
        p.setProductName("有机黑猪肉");
        p.setProductSpec("500g/份");
        return p;
    }

    @Test
    @DisplayName("queryPage：按 codeType=pork 过滤命中 + 内存 JOIN 填产品名")
    @SuppressWarnings("unchecked")
    void testQueryPage_FilterByCodeTypeAndJoinProductName() {
        Page<TraceCode> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(porkCode()));
        when(traceCodeMapper.selectPage(any(), any(Wrapper.class))).thenReturn(page);
        when(productInfoMapper.selectByIds(any())).thenReturn(List.of(product()));

        TraceCodeQuery q = new TraceCodeQuery();
        q.setCodeType("pork");
        TableDataInfo<TraceCodeListVo> result = service.queryPage(q, new PageQuery(1, 10));

        assertThat(result.getRows()).hasSize(1);
        TraceCodeListVo vo = result.getRows().get(0);
        assertThat(vo.getCodeType()).isEqualTo("pork");
        assertThat(vo.getProduceCode()).isEqualTo(PRODUCE_CODE);
        assertThat(vo.getProductName()).isEqualTo("有机黑猪肉");
        assertThat(vo.getProductSpec()).isEqualTo("500g/份");
    }

    @Test
    @DisplayName("getDetail：组装主表 + 事件时间轴（断言 events 按 trace_time 升序）")
    void testGetDetail_AssembleEventsInOrder() {
        when(traceCodeMapper.selectById(CODE_ID)).thenReturn(porkCode());
        when(productInfoMapper.selectByIds(any())).thenReturn(List.of(product()));

        TraceEventVo e1 = new TraceEventVo();
        e1.setProduceCode(PRODUCE_CODE);
        e1.setTraceContent("marketing");
        e1.setTraceTime(LocalDateTime.of(2026, 6, 1, 8, 0));
        TraceEventVo e2 = new TraceEventVo();
        e2.setProduceCode(PRODUCE_CODE);
        e2.setTraceContent("slaughter");
        e2.setTraceTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        // mapper 已 orderByAsc(trace_time)，模拟返回有序列表
        when(traceEventMapper.selectVoList(any())).thenReturn(List.of(e1, e2));

        TraceCodeDetailVo detail = service.getDetail(CODE_ID);

        assertThat(detail.getProduceCode()).isEqualTo(PRODUCE_CODE);
        assertThat(detail.getProductName()).isEqualTo("有机黑猪肉");
        assertThat(detail.getEvents()).hasSize(2);
        assertThat(detail.getEvents().get(0).getTraceContent()).isEqualTo("marketing");
        assertThat(detail.getEvents().get(1).getTraceContent()).isEqualTo("slaughter");
        // 时间轴升序：第一个节点时间 < 第二个
        assertThat(detail.getEvents().get(0).getTraceTime())
            .isBefore(detail.getEvents().get(1).getTraceTime());
    }

    @Test
    @DisplayName("getDetail 不存在：抛 ServiceException")
    void testGetDetail_NotFound() {
        when(traceCodeMapper.selectById(CODE_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.getDetail(CODE_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("getEvents 空 produceCode：返空列表（不查库）")
    void testGetEvents_BlankProduceCode() {
        assertThat(service.getEvents("")).isEmpty();
        assertThat(service.getEvents(null)).isEmpty();
    }

}
