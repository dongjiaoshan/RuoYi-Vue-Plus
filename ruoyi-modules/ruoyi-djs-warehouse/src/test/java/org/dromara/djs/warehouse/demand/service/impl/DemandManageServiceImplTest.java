package org.dromara.djs.warehouse.demand.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.demand.domain.bo.AssignPigBo;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.domain.query.DemandManageQuery;
import org.dromara.djs.warehouse.demand.domain.vo.DemandGroupVo;
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
 *   <li>group-list 分页只切片不重排（排序权威在 mapper ORDER BY）+ 三态/确认率回填（row32）</li>
 *   <li>selectDemandGroupList 默认 ORDER BY = 确认率升序 + 日期/产品名 tie-breaker（row32 契约钉死）</li>
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

    @Mock
    org.dromara.djs.warehouse.product.mapper.ProductInfoMapper productInfoMapper;

    @Mock
    org.dromara.djs.warehouse.demand.mapper.DemandAdjustRecordMapper adjustRecordMapper;

    @Mock
    org.dromara.djs.common.store.mapper.StoreMapper storeMapper;

    DemandManageServiceImpl service;

    @BeforeEach
    void setup() {
        // DJS-FIX-ADMIN-W22-003：SummaryBar 的 3 个新依赖在本套用例里不直接覆盖，传 null 让构造器存字段即可
        service = new TestableDemandManageServiceImpl(
            demandMapper, demandPigMapper, bizCodeGenerator, productInfoMapper, adjustRecordMapper, storeMapper);
        when(bizCodeGenerator.generate(eq(BizCodeType.DEMAND_NO), any())).thenReturn("D260601WB0001");
    }

    /**
     * 子类覆盖 toEntity 钩子，避开 MapstructUtils 的 Spring 上下文依赖（参 ProductInfoServiceImplTest 范式）。
     */
    static class TestableDemandManageServiceImpl extends DemandManageServiceImpl {
        TestableDemandManageServiceImpl(DemandManageMapper m, DemandPigMapper dpm, IBizCodeGenerator g,
                                        org.dromara.djs.warehouse.product.mapper.ProductInfoMapper pim,
                                        org.dromara.djs.warehouse.demand.mapper.DemandAdjustRecordMapper arm,
                                        org.dromara.djs.common.store.mapper.StoreMapper sm) {
            // 新增依赖（DemandPigAvailableMapper 出栏日龄过滤 + 周期配置 + 计划 + 库存
            // + IProductDisplayNameResolver 下单定格展示名 DENGBO-R16
            // + DemandAdjustRecordMapper / StoreMapper 需求量调整留痕 V6-R140）本套用例不直接覆盖，
            // 传 null 让构造器存字段即可；
            // ProductInfoMapper 原料下单守门在 insertByBo 主链路必经，传 mock（未 stub 返回 null → 守门放行）
            super(m, dpm, g, null, null, null, null, pim, null, arm, sm);
        }

        /** 纯 Mockito 用例没有 sa-token 上下文，覆盖掉静态 LoginHelper 取值。 */
        @Override
        protected Long resolveAdjusterId() {
            return 42L;
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
    @DisplayName("getTodayKpi happy → 8 字段装配正确（聚合列 Long/BigDecimal 统一转 int）")
    void getTodayKpiHappy() {
        // 模拟 jdbc 聚合列类型：SUM → BigDecimal，COUNT → Long
        Map<String, Object> agg = new HashMap<>();
        agg.put("todayPigDemand", new BigDecimal("12"));
        agg.put("todayPigAssigned", new BigDecimal("6.5"));
        agg.put("todayPorkDemand", 3L);
        agg.put("todayPorkAssigned", 2L);
        agg.put("todayVegSpeciesDemand", 5L);
        agg.put("todayVegSpeciesAssigned", 4L);
        agg.put("todayOtherDemand", 5L);
        agg.put("todayOtherAssigned", 4L);
        when(demandMapper.selectTodayKpiMainAgg(any(LocalDate.class))).thenReturn(agg);

        DemandTodayKpiVo vo = service.getTodayKpi();
        // 猪需求/已配头数含半只 0.5 折算 → BigDecimal，比值不比 scale
        assertThat(vo.getTodayPigDemand()).isEqualByComparingTo(new BigDecimal("12"));
        assertThat(vo.getTodayPigAssigned()).isEqualByComparingTo("6.5");
        assertThat(vo.getTodayPorkDemand()).isEqualTo(3);
        assertThat(vo.getTodayPorkAssigned()).isEqualTo(2);
        assertThat(vo.getTodayVegSpeciesDemand()).isEqualTo(5);
        assertThat(vo.getTodayVegSpeciesAssigned()).isEqualTo(4);
        assertThat(vo.getTodayOtherDemand()).isEqualTo(5);
        assertThat(vo.getTodayOtherAssigned()).isEqualTo(4);
        // 今日日期按 Asia/Shanghai 算（不依赖 DB CURDATE）
        verify(demandMapper).selectTodayKpiMainAgg(any(LocalDate.class));
    }

    @Test
    @DisplayName("getTodayKpi 无数据 → 8 数全 0（agg 缺键 + 子表 null 兜底）")
    void getTodayKpiEmpty() {
        // 主表无今日 demand：SUM/COUNT 仍返单行但值为 0（这里模拟缺键 → intFromAgg/bdFromAgg 兜底 0）
        when(demandMapper.selectTodayKpiMainAgg(any(LocalDate.class))).thenReturn(new HashMap<>());

        DemandTodayKpiVo vo = service.getTodayKpi();
        assertThat(vo.getTodayPigDemand()).isZero();
        assertThat(vo.getTodayPigAssigned()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getTodayPorkDemand()).isZero();
        assertThat(vo.getTodayPorkAssigned()).isZero();
        assertThat(vo.getTodayVegSpeciesDemand()).isZero();
        assertThat(vo.getTodayVegSpeciesAssigned()).isZero();
        assertThat(vo.getTodayOtherDemand()).isZero();
        assertThat(vo.getTodayOtherAssigned()).isZero();
    }

    @Test
    @DisplayName("group-list 分页只切片不重排 → 排序权威在 mapper ORDER BY（确认率升序），跨页有序")
    void queryGroupListKeepsMapperOrder() {
        // mapper 已按确认率升序返回全量（0% → 33% → 100%）；service 只能切片，不得再排
        List<DemandGroupVo> ordered = new java.util.ArrayList<>(List.of(
            groupRow(LocalDate.of(2026, 8, 5), 3, 0),    // 0%
            groupRow(LocalDate.of(2026, 8, 6), 3, 1),    // 33%
            groupRow(LocalDate.of(2026, 8, 7), 2, 2)));  // 100%
        when(demandMapper.selectDemandGroupList(any(), any(), any(), any(), any(), any(), any())).thenReturn(ordered);

        // ⚠️ PageQuery 构造器形参是 (pageSize, pageNum)，不是常见的 (pageNum, pageSize)
        PageQuery page1 = new PageQuery(2, 1);
        TableDataInfo<DemandGroupVo> p1 = service.queryGroupList(new DemandManageQuery(), page1);
        assertThat(p1.getTotal()).isEqualTo(3);
        assertThat(p1.getRows()).hasSize(2);
        assertThat(p1.getRows().get(0).getConfirmRate()).isEqualByComparingTo("0");
        assertThat(p1.getRows().get(0).getDemandStatus()).isEqualTo("PENDING");
        assertThat(p1.getRows().get(1).getConfirmRate()).isEqualByComparingTo("0.3333");
        assertThat(p1.getRows().get(1).getDemandStatus()).isEqualTo("PARTIAL");

        PageQuery page2 = new PageQuery(2, 2);
        TableDataInfo<DemandGroupVo> p2 = service.queryGroupList(new DemandManageQuery(), page2);
        assertThat(p2.getRows()).hasSize(1);
        // 第 2 页拿到的是全量序列的第 3 条（100%），不是被 service 重排后的另一条
        assertThat(p2.getRows().get(0).getConfirmRate()).isEqualByComparingTo("1");
        assertThat(p2.getRows().get(0).getDemandStatus()).isEqualTo("ALL_CONFIRMED");
    }

    @Test
    @DisplayName("selectDemandGroupList 默认 ORDER BY = 确认率升序 + (日期倒序,产品名升序) tie-breaker")
    void groupListSqlOrdersByConfirmRateAsc() throws Exception {
        // 排序是 SQL 层契约（列表先聚合全量再内存分页，前端排不了跨页），改回来会静默退化 → 在此钉死
        org.apache.ibatis.annotations.Select select = DemandManageMapper.class
            .getMethod("selectDemandGroupList", String.class, String.class, List.class,
                Long.class, List.class, LocalDate.class, LocalDate.class)
            .getAnnotation(org.apache.ibatis.annotations.Select.class);
        assertThat(select).isNotNull();
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");
        assertThat(sql).contains(
            "ORDER BY COALESCE(COUNT(CASE WHEN dm.demand_status "
                + "IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED') THEN 1 END) "
                + "/ NULLIF(COUNT(*), 0), 0) ASC, dm.demand_date DESC, MAX(dm.product_name) ASC");
    }

    /** 分组行样例：只填排序 / 三态计算用到的字段（需求日期 + 组内单数 + 已确认单数）。 */
    private DemandGroupVo groupRow(LocalDate date, int demandCount, int confirmedCount) {
        DemandGroupVo vo = new DemandGroupVo();
        vo.setDemandDate(date);
        vo.setProductId(100L + demandCount);
        vo.setDemandCount(demandCount);
        vo.setConfirmedDemandCount(confirmedCount);
        return vo;
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

    // ---------------- 业态自校正（谎报 productType 会拿到错的单号段）----------------

    private org.dromara.djs.warehouse.product.domain.ProductInfo prod(Long id, String belong, int attr) {
        org.dromara.djs.warehouse.product.domain.ProductInfo p =
            new org.dromara.djs.warehouse.product.domain.ProductInfo();
        p.setId(id);
        p.setProductName("产品" + id);
        p.setBelongType(belong);
        p.setProductType(1);
        p.setProductAttr(attr);
        return p;
    }

    @Test
    @DisplayName("果蔬成品被声明成 gift_box → 服务端改回 vegetable（否则拿到礼盒段单号、下游按礼盒筛）")
    void insertByBo_correctsLiedProductType() {
        when(productInfoMapper.selectById(7001L)).thenReturn(prod(7001L, "vegetable", 1));
        DemandManageBo bo = new DemandManageBo();
        bo.setStoreId(9001L);
        bo.setProductId(7001L);
        bo.setProductName("有机苕尖350g");
        bo.setProductType("gift_box");
        bo.setDemandDate(java.time.LocalDate.now());
        bo.setDemandQuantity(new java.math.BigDecimal("1"));
        bo.setProductUnit("份");
        service.insertByBo(bo);
        assertThat(bo.getProductType()).isEqualTo("vegetable");
    }

    @Test
    @DisplayName("猪肉/干货/鸡蛋等无独立业态的三类不被压平（仓库侧会用更细的 pig/dry/egg 拿各自单号段）")
    void insertByBo_keepsFinerGrainedTypeForOtherBucket() {
        when(productInfoMapper.selectById(7002L)).thenReturn(prod(7002L, "pork", 1));
        DemandManageBo bo = new DemandManageBo();
        bo.setStoreId(9001L);
        bo.setProductId(7002L);
        bo.setProductName("黑毛猪五花肉500g");
        bo.setProductType("pig");
        bo.setDemandDate(java.time.LocalDate.now());
        bo.setDemandQuantity(new java.math.BigDecimal("1"));
        bo.setProductUnit("份");
        service.insertByBo(bo);
        assertThat(bo.getProductType()).isEqualTo("pig");
    }

    @Test
    @DisplayName("编辑：已终止合作 / 不存在的门店不得再调整其需求（闸下沉到共用落库路径）")
    void updateRejectsTerminatedStore() {
        DemandManage exists = new DemandManage();
        exists.setId(101L);
        exists.setDemandNo("D20260811VG0041");
        exists.setDemandStatus("SUBMITTED");
        exists.setProductType("vegetable");
        exists.setStoreId(9315000000009999L);
        exists.setDemandQuantity(new BigDecimal("5.5"));
        when(demandMapper.selectById(101L)).thenReturn(exists);
        when(demandMapper.countTerminatedStore(9315000000009999L)).thenReturn(1);

        DemandManageBo bo = baseBo("vegetable");
        bo.setId(101L);
        bo.setStoreId(9315000000009999L);
        bo.setDemandQuantity(new BigDecimal("123"));
        // 独立验收实测：修复前 mp /quantity 与 admin /edit 都能把量从 5.5 一路改到 123，全程 200
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已终止合作或不存在");
    }

    @Test
    @DisplayName("编辑：不得把需求换成另一个业态的产品（单号 bizCode 段已定格）")
    void updateRejectsCrossBusinessTypeProduct() {
        DemandManage exists = new DemandManage();
        exists.setId(101L);
        exists.setDemandNo("D20260811VG0041");
        exists.setDemandStatus("SUBMITTED");
        exists.setProductType("vegetable");
        exists.setProductId(9304000000000136L);
        exists.setDemandQuantity(new BigDecimal("5"));
        when(demandMapper.selectById(101L)).thenReturn(exists);

        ProductInfo whiteBar = new ProductInfo();
        whiteBar.setId(9303000000000142L);
        whiteBar.setProductName("半扇");
        whiteBar.setProductType(1);
        whiteBar.setBelongType("white_bar");
        when(productInfoMapper.selectById(9303000000000142L)).thenReturn(whiteBar);

        DemandManageBo bo = baseBo("vegetable");
        bo.setId(101L);
        bo.setProductId(9303000000000142L);
        // 独立验收实测：修复前返 200，落库 product_id=半扇 但 product_type 仍 vegetable、单号仍 VG 段，
        // 下游立刻自相矛盾（该行产品是半扇，指定猪只却被拒）
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能改成其它业态的产品");
    }

    // ---------------- V6-R140 调整需求量 ----------------

    @Test
    @DisplayName("adjustQuantity: 已确认态 happy → 写一行留痕（8 项齐全）+ 只 patch demand_quantity")
    void testAdjustQuantity_ConfirmedHappy() {
        DemandManage exists = new DemandManage();
        exists.setId(501L);
        exists.setDemandNo("D260828VG0001");
        exists.setDemandDate(LocalDate.of(2026, 8, 28));
        exists.setStoreId(9001L);
        exists.setProductId(7001L);
        exists.setProductName("上海青");
        exists.setDemandQuantity(new BigDecimal("5.000"));
        exists.setShippedCount(BigDecimal.ZERO);
        exists.setDemandStatus("CONFIRMED");
        when(demandMapper.selectById(501L)).thenReturn(exists);

        org.dromara.djs.common.store.domain.Store store = new org.dromara.djs.common.store.domain.Store();
        store.setStoreName("徐汇旗舰店");
        when(storeMapper.selectById(9001L)).thenReturn(store);
        ProductInfo product = new ProductInfo();
        product.setProductId("PROD-VG-0007");
        when(productInfoMapper.selectById(7001L)).thenReturn(product);
        when(demandMapper.compareAndSetQuantity(eq(501L), any(BigDecimal.class), any(BigDecimal.class), any()))
            .thenReturn(1);

        org.dromara.djs.warehouse.demand.domain.bo.DemandAdjustBo bo =
            new org.dromara.djs.warehouse.demand.domain.bo.DemandAdjustBo();
        bo.setDemandQuantity(new BigDecimal("8"));
        bo.setAdjustRemark("门店临时加量");

        int rows = service.adjustQuantity(501L, bo);
        assertThat(rows).isEqualTo(1);

        ArgumentCaptor<org.dromara.djs.warehouse.demand.domain.DemandAdjustRecord> recCap =
            ArgumentCaptor.forClass(org.dromara.djs.warehouse.demand.domain.DemandAdjustRecord.class);
        verify(adjustRecordMapper, times(1)).insert(recCap.capture());
        org.dromara.djs.warehouse.demand.domain.DemandAdjustRecord rec = recCap.getValue();
        // 甲方点名要留的 8 项
        assertThat(rec.getDemandDate()).isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(rec.getStoreName()).isEqualTo("徐汇旗舰店");
        assertThat(rec.getProductCode()).isEqualTo("PROD-VG-0007");
        assertThat(rec.getOldQuantity()).isEqualByComparingTo("5.000");
        assertThat(rec.getNewQuantity()).isEqualByComparingTo("8");
        assertThat(rec.getAdjustRemark()).isEqualTo("门店临时加量");
        assertThat(rec.getAdjusterId()).isEqualTo(42L);
        assertThat(rec.getAdjustTime()).isNotNull();

        // 落库走 CAS：WHERE 里带着调整人读到的旧值，只改 demand_quantity，
        // 状态 / 确认人 / audit_history 连碰都没碰（updateById 全程没被调用）
        ArgumentCaptor<BigDecimal> oldCap = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> newCap = ArgumentCaptor.forClass(BigDecimal.class);
        verify(demandMapper).compareAndSetQuantity(eq(501L), oldCap.capture(), newCap.capture(), eq(42L));
        assertThat(oldCap.getValue()).isEqualByComparingTo("5.000");
        assertThat(newCap.getValue()).isEqualByComparingTo("8");
        verify(demandMapper, never()).updateById(any(DemandManage.class));
    }

    @Test
    @DisplayName("adjustQuantity: CAS 落空（期间被别人改过）→ 409 且不留痕，不静默覆盖")
    void testAdjustQuantity_ConcurrentModifiedRejected() {
        DemandManage exists = new DemandManage();
        exists.setId(505L);
        exists.setDemandNo("D260828VG0005");
        exists.setDemandQuantity(new BigDecimal("10.000"));
        exists.setShippedCount(BigDecimal.ZERO);
        exists.setDemandStatus("CONFIRMED");
        when(demandMapper.selectById(505L)).thenReturn(exists);
        // 并发：别人先落了盘，CAS 的 WHERE 匹配不上 → 0 行
        when(demandMapper.compareAndSetQuantity(eq(505L), any(BigDecimal.class), any(BigDecimal.class), any()))
            .thenReturn(0);

        org.dromara.djs.warehouse.demand.domain.bo.DemandAdjustBo bo =
            new org.dromara.djs.warehouse.demand.domain.bo.DemandAdjustBo();
        bo.setDemandQuantity(new BigDecimal("7"));

        assertThatThrownBy(() -> service.adjustQuantity(505L, bo)).isInstanceOf(ServiceException.class);
        verify(adjustRecordMapper, never()).insert(any(org.dromara.djs.warehouse.demand.domain.DemandAdjustRecord.class));
    }

    @Test
    @DisplayName("adjustQuantity: 白条已指定 3 头猪 → 调到 1 头被拒（多出来的猪会被凭空锁死）")
    void testAdjustQuantity_BelowAssignedPigsRejected() {
        DemandManage exists = new DemandManage();
        exists.setId(506L);
        exists.setDemandNo("D260828WB0006");
        exists.setProductType("white_bar");
        exists.setDemandQuantity(new BigDecimal("3.000"));
        exists.setShippedCount(BigDecimal.ZERO);
        exists.setDemandStatus("SUBMITTED");
        when(demandMapper.selectById(506L)).thenReturn(exists);
        when(demandPigMapper.selectCount(any())).thenReturn(3L);

        org.dromara.djs.warehouse.demand.domain.bo.DemandAdjustBo bo =
            new org.dromara.djs.warehouse.demand.domain.bo.DemandAdjustBo();
        bo.setDemandQuantity(new BigDecimal("1"));

        assertThatThrownBy(() -> service.adjustQuantity(506L, bo)).isInstanceOf(ServiceException.class);
        verify(adjustRecordMapper, never()).insert(any(org.dromara.djs.warehouse.demand.domain.DemandAdjustRecord.class));
        verify(demandMapper, never()).compareAndSetQuantity(any(), any(), any(), any());
    }

    @Test
    @DisplayName("adjustQuantity: 白条已指定 3 头猪 → 调到 5 头放行（加量不会锁死猪）")
    void testAdjustQuantity_AboveAssignedPigsAllowed() {
        DemandManage exists = new DemandManage();
        exists.setId(507L);
        exists.setDemandNo("D260828WB0007");
        exists.setProductType("white_bar");
        exists.setDemandQuantity(new BigDecimal("3.000"));
        exists.setShippedCount(BigDecimal.ZERO);
        exists.setDemandStatus("CONFIRMED");
        when(demandMapper.selectById(507L)).thenReturn(exists);
        when(demandPigMapper.selectCount(any())).thenReturn(3L);
        when(demandMapper.compareAndSetQuantity(eq(507L), any(BigDecimal.class), any(BigDecimal.class), any()))
            .thenReturn(1);

        org.dromara.djs.warehouse.demand.domain.bo.DemandAdjustBo bo =
            new org.dromara.djs.warehouse.demand.domain.bo.DemandAdjustBo();
        bo.setDemandQuantity(new BigDecimal("5"));

        assertThat(service.adjustQuantity(507L, bo)).isEqualTo(1);
        verify(adjustRecordMapper, times(1)).insert(any(org.dromara.djs.warehouse.demand.domain.DemandAdjustRecord.class));
    }

    @Test
    @DisplayName("adjustQuantity: 部分发货态 → throws（甲方「发货之后不给调」），且不写留痕")
    void testAdjustQuantity_AfterShippedRejected() {
        DemandManage exists = new DemandManage();
        exists.setId(502L);
        exists.setDemandNo("D260828VG0002");
        exists.setDemandQuantity(new BigDecimal("5.000"));
        exists.setDemandStatus("PARTIAL_SHIPPED");
        when(demandMapper.selectById(502L)).thenReturn(exists);

        org.dromara.djs.warehouse.demand.domain.bo.DemandAdjustBo bo =
            new org.dromara.djs.warehouse.demand.domain.bo.DemandAdjustBo();
        bo.setDemandQuantity(new BigDecimal("8"));

        assertThatThrownBy(() -> service.adjustQuantity(502L, bo)).isInstanceOf(ServiceException.class);
        verify(adjustRecordMapper, never()).insert(any(org.dromara.djs.warehouse.demand.domain.DemandAdjustRecord.class));
        verify(demandMapper, never()).compareAndSetQuantity(any(), any(), any(), any());
    }

    @Test
    @DisplayName("adjustQuantity: 新量 == 原量（5 vs 5.000）→ throws，不写空留痕")
    void testAdjustQuantity_UnchangedRejected() {
        DemandManage exists = new DemandManage();
        exists.setId(503L);
        exists.setDemandNo("D260828VG0003");
        exists.setDemandQuantity(new BigDecimal("5.000"));
        exists.setDemandStatus("SUBMITTED");
        when(demandMapper.selectById(503L)).thenReturn(exists);

        org.dromara.djs.warehouse.demand.domain.bo.DemandAdjustBo bo =
            new org.dromara.djs.warehouse.demand.domain.bo.DemandAdjustBo();
        bo.setDemandQuantity(new BigDecimal("5"));

        assertThatThrownBy(() -> service.adjustQuantity(503L, bo)).isInstanceOf(ServiceException.class);
        verify(adjustRecordMapper, never()).insert(any(org.dromara.djs.warehouse.demand.domain.DemandAdjustRecord.class));
    }

    @Test
    @DisplayName("adjustQuantity: 新量低于已发货量 → throws（已发 > 需求 会让下游确认率自相矛盾）")
    void testAdjustQuantity_BelowShippedRejected() {
        DemandManage exists = new DemandManage();
        exists.setId(504L);
        exists.setDemandNo("D260828VG0004");
        exists.setDemandQuantity(new BigDecimal("10.000"));
        exists.setShippedCount(new BigDecimal("6.000"));
        exists.setDemandStatus("CONFIRMED");
        when(demandMapper.selectById(504L)).thenReturn(exists);

        org.dromara.djs.warehouse.demand.domain.bo.DemandAdjustBo bo =
            new org.dromara.djs.warehouse.demand.domain.bo.DemandAdjustBo();
        bo.setDemandQuantity(new BigDecimal("3"));

        assertThatThrownBy(() -> service.adjustQuantity(504L, bo)).isInstanceOf(ServiceException.class);
        verify(adjustRecordMapper, never()).insert(any(org.dromara.djs.warehouse.demand.domain.DemandAdjustRecord.class));
    }
}
