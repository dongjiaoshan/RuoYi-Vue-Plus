package org.dromara.djs.warehouse.cutout.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.cutout.domain.query.CutOutDailyDetailQuery;
import org.dromara.djs.warehouse.cutout.domain.query.CutOutDailyQuery;
import org.dromara.djs.warehouse.cutout.domain.vo.CutOutDailyDetailVo;
import org.dromara.djs.warehouse.cutout.domain.vo.CutOutDailyVo;
import org.dromara.djs.warehouse.cutout.mapper.CutOutDailyMapper;
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
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CutOutDailyServiceImpl} 单测（V7-R6 猪肉日分割出库统计）。
 *
 * <ol>
 *   <li>列表 happy：仓库出库行翻字典去向、门店需求行显示「门店需求」、重量 3 位小数</li>
 *   <li>筛选决定分支：门店需求 / 选门店 → 只开门店分支；字典去向 → 只开仓库分支；
 *       字典去向 + 门店 → 交集为空，不打库</li>
 *   <li>明细：单位恒 kg、空耳号兜 "-"；门店明细必须带门店</li>
 * </ol>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CutOutDailyServiceImpl 单元测试（V7-R6）")
class CutOutDailyServiceImplTest {

    @Mock
    private CutOutDailyMapper cutOutDailyMapper;

    @Mock
    private DictService dictService;

    @InjectMocks
    private CutOutDailyServiceImpl service;

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

    private static CutOutDailyVo warehouseRow() {
        CutOutDailyVo row = new CutOutDailyVo();
        row.setStatDate(new Date());
        row.setDestType("warehouse");
        row.setOutDest("kitchen");
        row.setTotalWeight(new BigDecimal("12.5"));
        return row;
    }

    private static CutOutDailyVo storeRow() {
        CutOutDailyVo row = new CutOutDailyVo();
        row.setStatDate(new Date());
        row.setDestType("store");
        row.setOutDest(CutOutDailyQuery.OUT_DEST_STORE_DEMAND);
        row.setStoreId(9429800000000001L);
        row.setStoreName("大冶两湖店");
        row.setTotalWeight(new BigDecimal("30.25"));
        return row;
    }

    @Test
    @DisplayName("queryPage: 两类行都出，去向 label 回填、重量 3 位小数")
    void testQueryPage_HappyPath() {
        Page<CutOutDailyVo> page = new Page<>(1, 10);
        page.setRecords(List.of(warehouseRow(), storeRow()));
        page.setTotal(2);
        when(cutOutDailyMapper.selectDailyPage(any(), eq("1001"), any(CutOutDailyQuery.class), eq(true), eq(true)))
            .thenReturn(page);
        when(dictService.getDictLabel("djs_stock_out_dest", "kitchen")).thenReturn("厨房");

        TableDataInfo<CutOutDailyVo> result = service.queryPage(new CutOutDailyQuery(), new PageQuery(10, 1));

        assertThat(result.getTotal()).isEqualTo(2);
        CutOutDailyVo wh = result.getRows().get(0);
        assertThat(wh.getOutDestLabel()).isEqualTo("厨房");
        assertThat(wh.getStoreName()).isNull();
        assertThat(wh.getTotalWeight()).isEqualByComparingTo("12.500");
        assertThat(wh.getTotalWeight().scale()).isEqualTo(3);
        CutOutDailyVo st = result.getRows().get(1);
        assertThat(st.getOutDestLabel()).isEqualTo("门店需求");
        assertThat(st.getStoreName()).isEqualTo("大冶两湖店");
        assertThat(st.getTotalWeight().toPlainString()).isEqualTo("30.250");
    }

    @Test
    @DisplayName("queryPage: 选「门店需求」只开门店分支；选字典去向只开仓库分支；选门店只开门店分支")
    void testQueryPage_BranchSwitch() {
        Page<CutOutDailyVo> empty = new Page<>(1, 10);
        when(cutOutDailyMapper.selectDailyPage(any(), anyString(), any(), anyBoolean(), anyBoolean())).thenReturn(empty);

        CutOutDailyQuery storeDemand = new CutOutDailyQuery();
        storeDemand.setOutDest(CutOutDailyQuery.OUT_DEST_STORE_DEMAND);
        service.queryPage(storeDemand, new PageQuery(10, 1));
        verify(cutOutDailyMapper).selectDailyPage(any(), eq("1001"), eq(storeDemand), eq(false), eq(true));

        CutOutDailyQuery dictDest = new CutOutDailyQuery();
        dictDest.setOutDest("mine");
        service.queryPage(dictDest, new PageQuery(10, 1));
        verify(cutOutDailyMapper).selectDailyPage(any(), eq("1001"), eq(dictDest), eq(true), eq(false));

        CutOutDailyQuery byStore = new CutOutDailyQuery();
        byStore.setStoreId(1L);
        service.queryPage(byStore, new PageQuery(10, 1));
        verify(cutOutDailyMapper).selectDailyPage(any(), eq("1001"), eq(byStore), eq(false), eq(true));
    }

    @Test
    @DisplayName("queryPage: 字典去向 + 门店 交集为空，直接返空页不打库")
    void testQueryPage_DictDestWithStoreIsEmpty() {
        CutOutDailyQuery q = new CutOutDailyQuery();
        q.setOutDest("mine");
        q.setStoreId(1L);

        TableDataInfo<CutOutDailyVo> result = service.queryPage(q, new PageQuery(10, 1));

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRows()).isEmpty();
        verify(cutOutDailyMapper, never()).selectDailyPage(any(), anyString(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("queryDetail: 门店明细单位恒 kg、空耳号兜 -、量 3 位小数")
    void testQueryDetail_Store() {
        CutOutDailyDetailVo row = new CutOutDailyDetailVo();
        row.setProductCode("Y0322");
        row.setProductName("通排");
        row.setQuantity(new BigDecimal("2.3"));
        row.setEarNo(null);
        row.setOutTime(new Date());
        when(cutOutDailyMapper.selectStoreDetail(eq("1001"), any())).thenReturn(List.of(row));

        CutOutDailyDetailQuery q = new CutOutDailyDetailQuery();
        q.setStatDate(new Date());
        q.setDestType("store");
        q.setStoreId(9429800000000001L);
        List<CutOutDailyDetailVo> list = service.queryDetail(q);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getUnit()).isEqualTo("kg");
        assertThat(list.get(0).getEarNo()).isEqualTo("-");
        assertThat(list.get(0).getQuantity().toPlainString()).isEqualTo("2.300");
        verify(cutOutDailyMapper, never()).selectWarehouseDetail(anyString(), any());
    }

    @Test
    @DisplayName("queryDetail: 仓库明细走仓库 SQL；门店明细不带门店直接拒")
    void testQueryDetail_WarehouseAndStoreRequired() {
        when(cutOutDailyMapper.selectWarehouseDetail(eq("1001"), any())).thenReturn(List.of());
        CutOutDailyDetailQuery wh = new CutOutDailyDetailQuery();
        wh.setStatDate(new Date());
        wh.setDestType("warehouse");
        wh.setOutDest("kitchen");
        assertThat(service.queryDetail(wh)).isEmpty();
        verify(cutOutDailyMapper).selectWarehouseDetail(eq("1001"), eq(wh));

        CutOutDailyDetailQuery st = new CutOutDailyDetailQuery();
        st.setStatDate(new Date());
        st.setDestType("store");
        assertThatThrownBy(() -> service.queryDetail(st)).isInstanceOf(ServiceException.class);
    }
}
