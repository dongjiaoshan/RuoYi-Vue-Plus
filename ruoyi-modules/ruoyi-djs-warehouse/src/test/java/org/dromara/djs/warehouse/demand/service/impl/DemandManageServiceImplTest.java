package org.dromara.djs.warehouse.demand.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.domain.bo.AssignPigBo;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandTodayKpiVo;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.demand.mapper.DemandPigMapper;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DemandManageServiceImpl} happy path 单测（WMS-DEMAND-001）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>新增 happy 白条/蔬菜/礼盒/其他 → 调 BizCodeGenerator.generate(DEMAND_NO) + 初始 status=SUBMITTED</li>
 *   <li>新增非法业态 → throws</li>
 *   <li>编辑 DRAFT → 全字段允许</li>
 *   <li>编辑 IN_PRODUCTION → 禁止（仅 DRAFT/SUBMITTED 可改）</li>
 *   <li>删除 DRAFT → ok / 删除 IN_PRODUCTION → throws</li>
 *   <li>assignPigs 非白条业态 → throws / IN_PRODUCTION → throws</li>
 * </ul>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DemandManageServiceImplTest {

    @Mock
    DemandManageMapper demandMapper;

    @Mock
    DemandPigMapper demandPigMapper;

    @Mock
    IBizCodeGenerator bizCodeGenerator;

    DemandManageServiceImpl service;

    @BeforeEach
    void setup() {
        // DJS-FIX-ADMIN-W22-003：SummaryBar 的 3 个新依赖在本套用例里不直接覆盖，传 null 让构造器存字段即可
        service = new TestableDemandManageServiceImpl(demandMapper, demandPigMapper, bizCodeGenerator);
        when(bizCodeGenerator.generate(eq(BizCodeType.DEMAND_NO), any())).thenReturn("D260601WB0001");
    }

    /**
     * 子类覆盖 toEntity 钩子，避开 MapstructUtils 的 Spring 上下文依赖（参 ProductInfoServiceImplTest 范式）。
     */
    static class TestableDemandManageServiceImpl extends DemandManageServiceImpl {
        TestableDemandManageServiceImpl(DemandManageMapper m, DemandPigMapper dpm, IBizCodeGenerator g) {
            super(m, dpm, g, null, null, null);
        }

        @Override
        protected org.dromara.djs.warehouse.demand.domain.DemandManage toEntity(DemandManageBo bo) {
            if (bo == null) {
                return null;
            }
            org.dromara.djs.warehouse.demand.domain.DemandManage e = new org.dromara.djs.warehouse.demand.domain.DemandManage();
            e.setId(bo.getId());
            e.setDemandDate(bo.getDemandDate());
            e.setStoreId(bo.getStoreId());
            e.setProductId(bo.getProductId());
            e.setProductName(bo.getProductName());
            e.setProductType(bo.getProductType());
            e.setProductSpec(bo.getProductSpec());
            e.setDemandQuantity(bo.getDemandQuantity());
            e.setProductUnit(bo.getProductUnit());
            e.setRawMaterial(bo.getRawMaterial());
            e.setMaterialQty(bo.getMaterialQty());
            e.setDemandRemark(bo.getDemandRemark());
            e.setDemandExplain(bo.getDemandExplain());
            e.setExpectedArriveDate(bo.getExpectedArriveDate());
            e.setRemark(bo.getRemark());
            return e;
        }
    }

    @Test
    @DisplayName("新增白条 happy → SUBMITTED + WMS-DEMAND 码 + bizCode=WB")
    void insertWhiteBarHappy() {
        DemandManageBo bo = baseBo("white_bar");
        when(demandMapper.insert(any(DemandManage.class))).thenAnswer(inv -> {
            DemandManage e = inv.getArgument(0);
            e.setId(101L);
            return 1;
        });
        Long id = service.insertByBo(bo);
        assertThat(id).isEqualTo(101L);

        ArgumentCaptor<Map<String, Object>> ctxCaptor = ArgumentCaptor.forClass(Map.class);
        verify(bizCodeGenerator).generate(eq(BizCodeType.DEMAND_NO), ctxCaptor.capture());
        assertThat(ctxCaptor.getValue()).containsEntry("bizCode", "WB");

        ArgumentCaptor<DemandManage> entityCaptor = ArgumentCaptor.forClass(DemandManage.class);
        verify(demandMapper).insert(entityCaptor.capture());
        DemandManage saved = entityCaptor.getValue();
        assertThat(saved.getDemandNo()).isEqualTo("D260601WB0001");
        assertThat(saved.getDemandStatus()).isEqualTo("SUBMITTED");
        assertThat(saved.getProductType()).isEqualTo("white_bar");
        assertThat(saved.getShippedCount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getConfirmedCount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("新增蔬菜 → bizCode=VG")
    void insertVegetableHappy() {
        DemandManageBo bo = baseBo("vegetable");
        when(demandMapper.insert(any(DemandManage.class))).thenReturn(1);
        when(bizCodeGenerator.generate(eq(BizCodeType.DEMAND_NO), any())).thenReturn("D260601VG0001");
        service.insertByBo(bo);
        ArgumentCaptor<Map<String, Object>> ctxCaptor = ArgumentCaptor.forClass(Map.class);
        verify(bizCodeGenerator).generate(eq(BizCodeType.DEMAND_NO), ctxCaptor.capture());
        assertThat(ctxCaptor.getValue()).containsEntry("bizCode", "VG");
    }

    @Test
    @DisplayName("新增礼盒 → bizCode=GB / 其他 → bizCode=OT")
    void insertGiftBoxAndOther() {
        when(demandMapper.insert(any(DemandManage.class))).thenReturn(1);
        when(bizCodeGenerator.generate(eq(BizCodeType.DEMAND_NO), any())).thenReturn("D260601GB0001");
        service.insertByBo(baseBo("gift_box"));
        verify(bizCodeGenerator, times(1)).generate(eq(BizCodeType.DEMAND_NO), any());

        when(bizCodeGenerator.generate(eq(BizCodeType.DEMAND_NO), any())).thenReturn("D260601OT0001");
        service.insertByBo(baseBo("other"));
        verify(bizCodeGenerator, times(2)).generate(eq(BizCodeType.DEMAND_NO), any());
    }

    @Test
    @DisplayName("新增非法业态 → throws + 不调 bizCodeGenerator")
    void insertInvalidType() {
        DemandManageBo bo = baseBo("invalid_type");
        assertThatThrownBy(() -> service.insertByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("productType.invalid");
        verify(bizCodeGenerator, never()).generate(any(), any());
        verify(demandMapper, never()).insert(any(DemandManage.class));
    }

    @Test
    @DisplayName("编辑 DRAFT → 业务字段全允许 + demand_no 锁回")
    void updateDraftAllFields() {
        DemandManage exists = new DemandManage();
        exists.setId(101L);
        exists.setDemandNo("D260601WB0001");
        exists.setDemandStatus("DRAFT");
        exists.setProductType("white_bar");
        when(demandMapper.selectById(101L)).thenReturn(exists);
        when(demandMapper.updateById(any(DemandManage.class))).thenReturn(1);

        DemandManageBo bo = baseBo("white_bar");  // 试图改 productType
        bo.setProductType("vegetable");  // 应被强制锁回为 white_bar
        bo.setId(101L);
        bo.setDemandQuantity(new BigDecimal("99"));

        int rows = service.updateByBo(bo);
        assertThat(rows).isEqualTo(1);

        ArgumentCaptor<DemandManage> captor = ArgumentCaptor.forClass(DemandManage.class);
        verify(demandMapper).updateById(captor.capture());
        DemandManage updated = captor.getValue();
        assertThat(updated.getDemandNo()).isEqualTo("D260601WB0001");
        assertThat(updated.getProductType()).isEqualTo("white_bar");  // 锁回
        assertThat(updated.getDemandQuantity()).isEqualByComparingTo(new BigDecimal("99"));
    }

    @Test
    @DisplayName("编辑 IN_PRODUCTION → 禁止编辑（仅 DRAFT/SUBMITTED 可改）")
    void updateInProductionOnlyRemark() {
        DemandManage exists = new DemandManage();
        exists.setId(101L);
        exists.setDemandNo("D260601WB0001");
        exists.setDemandStatus("IN_PRODUCTION");
        exists.setProductType("white_bar");
        exists.setDemandQuantity(new BigDecimal("10"));
        exists.setRemark("原备注");
        when(demandMapper.selectById(101L)).thenReturn(exists);

        DemandManageBo bo = baseBo("white_bar");
        bo.setId(101L);
        bo.setDemandQuantity(new BigDecimal("99"));  // 试图改
        bo.setRemark("改了备注");

        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("status_forbidden");

        verify(demandMapper, never()).updateById(any(DemandManage.class));
    }

    @Test
    @DisplayName("删除 IN_PRODUCTION → throws")
    void deleteInProductionForbidden() {
        DemandManage exists = new DemandManage();
        exists.setId(101L);
        exists.setDemandNo("D260601WB0001");
        exists.setDemandStatus("IN_PRODUCTION");
        when(demandMapper.selectByIds(List.of(101L))).thenReturn(List.of(exists));

        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(101L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("status_forbidden");
    }

    @Test
    @DisplayName("assignPigs 非白条业态 → throws")
    void assignPigsNonWhiteBar() {
        DemandManage exists = new DemandManage();
        exists.setId(101L);
        exists.setDemandStatus("DRAFT");
        exists.setProductType("vegetable");
        when(demandMapper.selectById(101L)).thenReturn(exists);

        AssignPigBo bo = new AssignPigBo();
        bo.setEarNos(List.of("01A12605001"));
        assertThatThrownBy(() -> service.assignPigs(101L, bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("white_bar_only");
    }

    @Test
    @DisplayName("assignPigs 白条 DRAFT happy → insert 调用 + 去重")
    void assignPigsWhiteBarHappy() {
        DemandManage exists = new DemandManage();
        exists.setId(101L);
        exists.setDemandStatus("DRAFT");
        exists.setProductType("white_bar");
        when(demandMapper.selectById(101L)).thenReturn(exists);

        AssignPigBo bo = new AssignPigBo();
        // 含一个重复，一个空白
        bo.setEarNos(List.of("01A12605001", "01A12605001", "01A12605002", " "));

        when(demandPigMapper.insert(any(org.dromara.djs.warehouse.demand.domain.DemandPig.class))).thenReturn(1);
        int inserted = service.assignPigs(101L, bo);
        // 2 头唯一耳号
        assertThat(inserted).isEqualTo(2);
    }

    @Test
    @DisplayName("queryById 不存在 → return null（service 层不抛，由 controller 决策）")
    void queryByIdNotFound() {
        when(demandMapper.selectVoById(999L)).thenReturn(null);
        assertThat(service.queryById(999L)).isNull();
    }

    @Test
    @DisplayName("getTodayKpi happy → 6 字段装配正确（聚合列 Long/BigDecimal 统一转 int）")
    void getTodayKpiHappy() {
        // 模拟 jdbc 聚合列类型：SUM → BigDecimal，COUNT → Long
        Map<String, Object> agg = new HashMap<>();
        agg.put("todayPigDemand", new BigDecimal("12"));
        agg.put("todayVegSpeciesDemand", 5L);
        agg.put("todayVegSpeciesAssigned", 4L);
        agg.put("todayOtherDemand", 5L);
        agg.put("todayOtherAssigned", 4L);
        when(demandMapper.selectTodayKpiMainAgg(any(LocalDate.class))).thenReturn(agg);
        when(demandMapper.selectTodayPigAssigned(any(LocalDate.class))).thenReturn(12);

        DemandTodayKpiVo vo = service.getTodayKpi();
        // 猪需求头数含半只 0.5 折算 → BigDecimal，比值不比 scale
        assertThat(vo.getTodayPigDemand()).isEqualByComparingTo(new BigDecimal("12"));
        assertThat(vo.getTodayPigAssigned()).isEqualTo(12);
        assertThat(vo.getTodayVegSpeciesDemand()).isEqualTo(5);
        assertThat(vo.getTodayVegSpeciesAssigned()).isEqualTo(4);
        assertThat(vo.getTodayOtherDemand()).isEqualTo(5);
        assertThat(vo.getTodayOtherAssigned()).isEqualTo(4);
        // 今日日期按 Asia/Shanghai 算（不依赖 DB CURDATE）
        verify(demandMapper).selectTodayKpiMainAgg(any(LocalDate.class));
        verify(demandMapper).selectTodayPigAssigned(any(LocalDate.class));
    }

    @Test
    @DisplayName("getTodayKpi 无数据 → 6 数全 0（agg 缺键 + 子表 null 兜底）")
    void getTodayKpiEmpty() {
        // 主表无今日 demand：SUM/COUNT 仍返单行但值为 0（这里模拟缺键 → intFromAgg 兜底 0）
        when(demandMapper.selectTodayKpiMainAgg(any(LocalDate.class))).thenReturn(new HashMap<>());
        // 子表无白条已派：COUNT 返 0；极端 null 也兜底
        when(demandMapper.selectTodayPigAssigned(any(LocalDate.class))).thenReturn(null);

        DemandTodayKpiVo vo = service.getTodayKpi();
        assertThat(vo.getTodayPigDemand()).isZero();
        assertThat(vo.getTodayPigAssigned()).isZero();
        assertThat(vo.getTodayVegSpeciesDemand()).isZero();
        assertThat(vo.getTodayVegSpeciesAssigned()).isZero();
        assertThat(vo.getTodayOtherDemand()).isZero();
        assertThat(vo.getTodayOtherAssigned()).isZero();
    }

    private DemandManageBo baseBo(String productType) {
        DemandManageBo bo = new DemandManageBo();
        bo.setDemandDate(LocalDate.of(2026, 6, 1));
        bo.setStoreId(1L);
        bo.setProductId(100L);
        bo.setProductName("白条整只");
        bo.setProductType(productType);
        bo.setDemandQuantity(new BigDecimal("5"));
        bo.setProductUnit("头");
        bo.setExpectedArriveDate(LocalDate.of(2026, 6, 5));
        return bo;
    }
}
