package org.dromara.djs.warehouse.demand.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.domain.DemandPig;
import org.dromara.djs.warehouse.demand.domain.vo.DispatchHomeVo;
import org.dromara.djs.warehouse.demand.domain.vo.DispatchListItemVo;
import org.dromara.djs.warehouse.demand.domain.vo.DispatchStatsVo;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.demand.mapper.DemandPigMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link AppletDemandServiceImpl} 单测（WMS-DEMAND-002）。
 *
 * <p>覆盖 mp 调度只读聚合 3 方法 happy path：</p>
 * <ol>
 *   <li>dispatchHome：count 按状态白名单分桶填 KPI（白条/蔬菜入口卡 + 今日 KPI）</li>
 *   <li>dispatchList：列表 enrich storeName + 白条 enrich 已指定猪只数</li>
 *   <li>dispatchStats：当日 demand SUM 发货/确认量 + 占位 0（损耗/退货）</li>
 * </ol>
 *
 * <p>LambdaQueryWrapper 在 mock 路径下触发 TableInfoHelper.getTableInfo 解析列名，
 * 故 {@code @BeforeAll} 预热 DemandManage / DemandPig / Store entity cache
 * （参 skill coder-mp-entity-cache-test）。</p>
 *
 * @author djs
 * @since WMS-DEMAND-002
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AppletDemandServiceImpl 单元测试")
class AppletDemandServiceImplTest {

    @Mock
    private DemandManageMapper demandManageMapper;

    @Mock
    private DemandPigMapper demandPigMapper;

    @Mock
    private StoreMapper storeMapper;

    @InjectMocks
    private AppletDemandServiceImpl service;

    /**
     * MyBatis-Plus 单测 entity cache 预热：service 内 LambdaQueryWrapper 在 mock 路径下
     * 也会触发 TableInfoHelper.getTableInfo() 解析 lambda 列名，必须先注册 entity。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, DemandManage.class);
        TableInfoHelper.initTableInfo(assistant, DemandPig.class);
        TableInfoHelper.initTableInfo(assistant, Store.class);
    }

    @BeforeEach
    void setUp() {
        // 默认所有 count 返回 0；具体用例 override
        when(demandManageMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    @DisplayName("dispatchHome：count 分桶正确填 7 个 KPI 字段")
    void dispatchHomeHappy() {
        // 每次 selectCount 顺序返回不同值，验证 7 次调用顺序与字段映射
        // 顺序：whiteBarPending / whiteBarConfirmed / vegetablePending / vegetableConfirmed
        //       / confirmedCount / pendingCount / toShipCount
        when(demandManageMapper.selectCount(any()))
            .thenReturn(2L, 5L, 1L, 3L, 8L, 4L, 6L);

        DispatchHomeVo vo = service.dispatchHome();

        assertThat(vo.getWhiteBarPending()).isEqualTo(2);
        assertThat(vo.getWhiteBarConfirmed()).isEqualTo(5);
        assertThat(vo.getVegetablePending()).isEqualTo(1);
        assertThat(vo.getVegetableConfirmed()).isEqualTo(3);
        assertThat(vo.getConfirmedCount()).isEqualTo(8);
        assertThat(vo.getPendingCount()).isEqualTo(4);
        assertThat(vo.getToShipCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("dispatchList：白条列表 enrich storeName + 已指定猪只数")
    void dispatchListWhiteBarHappy() {
        DemandManage d = new DemandManage();
        d.setId(1001L);
        d.setDemandNo("D202606130001");
        d.setStoreId(2001L);
        d.setProductType("white_bar");
        d.setProductName("白条猪");
        d.setDemandQuantity(new BigDecimal("3"));
        d.setProductUnit("头");
        d.setDemandStatus("SUBMITTED");
        d.setDemandDate(LocalDate.now());
        when(demandManageMapper.selectList(any())).thenReturn(List.of(d));

        Store s = new Store();
        s.setId(2001L);
        s.setStoreName("矿业门店");
        when(storeMapper.selectList(any())).thenReturn(List.of(s));

        DemandPig p1 = new DemandPig();
        p1.setDemandId(1001L);
        p1.setEarNo("250613-001");
        DemandPig p2 = new DemandPig();
        p2.setDemandId(1001L);
        p2.setEarNo("250613-002");
        when(demandPigMapper.selectList(any())).thenReturn(List.of(p1, p2));

        List<DispatchListItemVo> list = service.dispatchList("white_bar");

        assertThat(list).hasSize(1);
        DispatchListItemVo item = list.get(0);
        assertThat(item.getId()).isEqualTo(1001L);
        assertThat(item.getStoreName()).isEqualTo("矿业门店");
        assertThat(item.getAssignedPigCount()).isEqualTo(2);
        assertThat(item.getProductType()).isEqualTo("white_bar");
    }

    @Test
    @DisplayName("dispatchStats：当日 SUM 发货/确认量 + 损耗退货占位 0")
    void dispatchStatsHappy() {
        DemandManage a = new DemandManage();
        a.setShippedCount(new BigDecimal("10.5"));
        a.setConfirmedCount(new BigDecimal("20"));
        DemandManage b = new DemandManage();
        b.setShippedCount(new BigDecimal("4.5"));
        b.setConfirmedCount(null); // null 量跳过累加
        when(demandManageMapper.selectList(any())).thenReturn(List.of(a, b));

        DispatchStatsVo vo = service.dispatchStats(LocalDate.of(2026, 6, 13));

        assertThat(vo.getStatDate()).isEqualTo("2026-06-13");
        assertThat(vo.getShippedQuantity()).isEqualByComparingTo("15.0");
        assertThat(vo.getConfirmedQuantity()).isEqualByComparingTo("20");
        assertThat(vo.getLossQuantity()).isEqualByComparingTo("0");
        assertThat(vo.getReturnCount()).isZero();
    }
}
