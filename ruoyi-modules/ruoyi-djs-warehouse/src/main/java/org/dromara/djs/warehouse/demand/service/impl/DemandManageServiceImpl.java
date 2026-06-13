package org.dromara.djs.warehouse.demand.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.demand.core.enums.DemandStatus;
import org.dromara.djs.common.util.I18nMessages;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.domain.DemandPig;
import org.dromara.djs.warehouse.demand.domain.bo.AssignPigBo;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.domain.query.DemandManageQuery;
import org.dromara.djs.warehouse.demand.domain.vo.AuditHistoryEntryVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPigVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandProductStoreDetailVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandSummaryVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandTodayKpiVo;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.demand.mapper.DemandPigMapper;
import org.dromara.djs.warehouse.demand.service.IDemandManageService;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.breed.core.service.IPigQueryService;
import org.dromara.djs.plant.plan.domain.vo.PlantPlanSummaryVo;
import org.dromara.djs.plant.plan.service.IPlantPlanService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 需求管理服务实现（WMS-DEMAND-001）。
 *
 * <p>4 业态共表：表单字段在 admin 端按 product_type 切；service 端做应用层校验，
 * DDL 不写 CHECK 约束（与 WMS-MD-002 ProductInfoServiceImpl 同范式）。</p>
 *
 * <p>软删走基类 {@link DjsBaseServiceImpl#softDelete}；删除前校验：仅 DRAFT / CANCELLED 态可删。</p>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
@Slf4j
@Service
public class DemandManageServiceImpl extends DjsBaseServiceImpl<DemandManageMapper, DemandManage>
    implements IDemandManageService {

    /** 7 业态码 → 业务编码 2 位（DEMAND_NO pattern 用 {bizCode2}）。 */
    private static final Map<String, String> PRODUCT_TYPE_TO_BIZ_CODE = Map.of(
        "white_bar", "WB",
        "pig", "PG",
        "vegetable", "VG",
        "dry", "DR",
        "egg", "EG",
        "gift_box", "GB",
        "other", "OT"
    );

    /** 业态白名单（service 端硬校验，避免下游靠字典维护）。 */
    private static final Set<String> ALLOWED_PRODUCT_TYPES = PRODUCT_TYPE_TO_BIZ_CODE.keySet();

    /** 允许硬删的状态。 */
    private static final Set<String> DELETABLE_STATUSES = Set.of(
        DemandStatus.DRAFT.name(), DemandStatus.CANCELLED.name()
    );

    /** 允许全字段编辑的状态（其他态仅可改 remark）。 */
    private static final Set<String> FULLY_EDITABLE_STATUSES = Set.of(
        DemandStatus.DRAFT.name(), DemandStatus.SUBMITTED.name()
    );

    /**
     * 今日日期算法时区（DJS-FIX-ADMIN-W22-007 KPI 横条）：不依赖 DB CURDATE() 时区，
     * 避免后续部署到非 UTC+8 实例时"今日"偏移埋雷。
     */
    private static final ZoneId TODAY_ZONE = ZoneId.of("Asia/Shanghai");

    private final DemandPigMapper demandPigMapper;

    private final IBizCodeGenerator bizCodeGenerator;

    /** SummaryBar 用：跨域薄壳查可出栏猪只头数（DJS-FIX-ADMIN-W22-003）。 */
    private final IPigQueryService pigQueryService;

    /** SummaryBar 用：跨域薄壳查"进行中"种植计划聚合（DJS-FIX-ADMIN-W22-003）。 */
    private final IPlantPlanService plantPlanService;

    /** SummaryBar 用：按 belong_type / product_attr 聚合 location_stock（DJS-FIX-ADMIN-W22-003）。 */
    private final LocationStockMapper locationStockMapper;

    public DemandManageServiceImpl(DemandManageMapper baseMapper,
                                   DemandPigMapper demandPigMapper,
                                   IBizCodeGenerator bizCodeGenerator,
                                   IPigQueryService pigQueryService,
                                   IPlantPlanService plantPlanService,
                                   LocationStockMapper locationStockMapper) {
        super(baseMapper);
        this.demandPigMapper = demandPigMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.pigQueryService = pigQueryService;
        this.plantPlanService = plantPlanService;
        this.locationStockMapper = locationStockMapper;
    }

    @Override
    public TableDataInfo<DemandManageVo> queryPageList(DemandManageQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<DemandManage> wrapper = buildQueryWrapper(query);
        Page<DemandManageVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillStoreCount(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 回填「有该产品需求的去重门店数」storeCount（D-FIX-24 决策 #8）。
     *
     * <p>按当前页涉及的 product_id 一次聚合查 DB（避免前端分页下跨页聚合不全）。
     * 无 product_id 的行 storeCount 保持 null。</p>
     */
    private void fillStoreCount(List<DemandManageVo> rows) {
        if (CollUtil.isEmpty(rows)) {
            return;
        }
        List<Long> productIds = rows.stream()
            .map(DemandManageVo::getProductId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (productIds.isEmpty()) {
            return;
        }
        Map<Long, Integer> countByProduct = new HashMap<>();
        for (Map<String, Object> r : baseMapper.selectStoreCountByProductIds(productIds)) {
            Object pid = r.get("productId");
            Object cnt = r.get("storeCount");
            if (pid != null && cnt != null) {
                countByProduct.put(((Number) pid).longValue(), ((Number) cnt).intValue());
            }
        }
        for (DemandManageVo vo : rows) {
            if (vo.getProductId() != null) {
                vo.setStoreCount(countByProduct.getOrDefault(vo.getProductId(), 0));
            }
        }
    }

    @Override
    public List<DemandProductStoreDetailVo> listProductStoreDetail(Long productId) {
        if (productId == null) {
            return List.of();
        }
        return baseMapper.selectProductStoreDetail(productId);
    }

    @Override
    public List<DemandManageVo> queryList(DemandManageQuery query) {
        return baseMapper.selectVoList(buildQueryWrapper(query));
    }

    @Override
    public DemandManageVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertByBo(DemandManageBo bo) {
        validateProductType(bo.getProductType());
        DemandManage entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException(I18nMessages.t("demand.bo.convert_failed"));
        }
        // 生成需求单号
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("bizCode", PRODUCT_TYPE_TO_BIZ_CODE.get(bo.getProductType()));
        String demandNo = bizCodeGenerator.generate(BizCodeType.DEMAND_NO, ctx);
        entity.setDemandNo(demandNo);
        entity.setDemandStatus(DemandStatus.DRAFT.name());
        applyInsertDefaults(entity);

        try {
            baseMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new ServiceException(I18nMessages.t("demand.demand_no.duplicate", demandNo));
        }
        log.info("[WMS-DEMAND-001] created demandNo={} id={} type={}", demandNo, entity.getId(), bo.getProductType());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(DemandManageBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException(I18nMessages.t("demand.id.required"));
        }
        DemandManage exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException(I18nMessages.t("demand.not_found", bo.getId()), 404);
        }
        // 编辑路径下：业态不可改（业态 confined to insert）
        bo.setProductType(exists.getProductType());

        // 仅 DRAFT / SUBMITTED 态允许全字段编辑，其他态仅允许改 remark
        if (!FULLY_EDITABLE_STATUSES.contains(exists.getDemandStatus())) {
            // 只 patch remark；其他字段全部回滚到 exists 原值
            exists.setRemark(bo.getRemark());
            return baseMapper.updateById(exists);
        }
        DemandManage entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException(I18nMessages.t("demand.bo.convert_failed"));
        }
        // 锁回不可改字段
        entity.setDemandNo(exists.getDemandNo());
        entity.setDemandStatus(exists.getDemandStatus());
        entity.setDemandConfirmer(exists.getDemandConfirmer());
        entity.setConfirmerTime(exists.getConfirmerTime());
        entity.setShippedCount(exists.getShippedCount());
        entity.setConfirmedCount(exists.getConfirmedCount());
        entity.setAuditHistory(exists.getAuditHistory());
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return 0;
        }
        // 校验所有 id 状态都允许删除
        List<DemandManage> demands = baseMapper.selectByIds(ids);
        for (DemandManage d : demands) {
            if (!DELETABLE_STATUSES.contains(d.getDemandStatus())) {
                throw new ServiceException(
                    I18nMessages.t("demand.delete.status_forbidden", d.getDemandNo(), d.getDemandStatus()));
            }
        }
        int rows = softDelete(ids);
        // 级联软删关联猪只（白条业态）
        for (Long id : ids) {
            softDeleteDemandPigsByDemandId(id);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int assignPigs(Long demandId, AssignPigBo bo) {
        DemandManage demand = baseMapper.selectById(demandId);
        if (demand == null) {
            throw new ServiceException(I18nMessages.t("demand.not_found", demandId), 404);
        }
        // 指定猪只仅限白条 / 猪业态（原型白条 + 猪 tab 均有「指定猪只」）
        if (!"white_bar".equals(demand.getProductType()) && !"pig".equals(demand.getProductType())) {
            throw new ServiceException(I18nMessages.t("demand.assign_pig.white_bar_only"), 400);
        }
        // 只允许 DRAFT / SUBMITTED 态指定（CONFIRMED 之后猪只已"锁定"，要修改走移除 + 重指定，但要校验状态）
        if (DemandStatus.fromCodeSafe(demand.getDemandStatus()) == null
            || DemandStatus.fromCodeSafe(demand.getDemandStatus()).isTerminal()
            || "IN_PRODUCTION".equals(demand.getDemandStatus())
            || "PARTIAL_SHIPPED".equals(demand.getDemandStatus())) {
            throw new ServiceException(
                I18nMessages.t("demand.assign_pig.status_forbidden", demand.getDemandStatus()));
        }
        Long operator = currentUserIdSafe();
        LocalDateTime now = LocalDateTime.now();
        int inserted = 0;
        Set<String> dedup = new HashSet<>();
        for (String earNo : bo.getEarNos()) {
            if (StrUtil.isBlank(earNo) || !dedup.add(earNo.trim())) {
                continue;
            }
            DemandPig dp = new DemandPig();
            dp.setDemandId(demandId);
            dp.setEarNo(earNo.trim());
            dp.setAssignedAt(now);
            dp.setAssignedBy(operator);
            try {
                demandPigMapper.insert(dp);
                inserted++;
            } catch (DuplicateKeyException e) {
                // UNIQUE(tenant_id, demand_id, ear_no, del_unique) 兜底重复指定
                log.warn("[WMS-DEMAND-001] earNo={} already assigned to demand={}, skip", earNo, demandId);
            }
        }
        return inserted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeAssignedPig(Long demandId, String earNo) {
        DemandManage demand = baseMapper.selectById(demandId);
        if (demand == null) {
            throw new ServiceException(I18nMessages.t("demand.not_found", demandId), 404);
        }
        // 仅 DRAFT / SUBMITTED 可移除
        if (!FULLY_EDITABLE_STATUSES.contains(demand.getDemandStatus())) {
            throw new ServiceException(
                I18nMessages.t("demand.assign_pig.status_forbidden", demand.getDemandStatus()));
        }
        List<DemandPig> rows = demandPigMapper.selectList(
            new LambdaQueryWrapper<DemandPig>()
                .eq(DemandPig::getDemandId, demandId)
                .eq(DemandPig::getEarNo, earNo));
        if (rows.isEmpty()) {
            return 0;
        }
        List<Long> ids = rows.stream().map(DemandPig::getId).toList();
        return softDeleteDemandPigsByIds(ids);
    }

    @Override
    public List<DemandPigVo> listAssignedPigs(Long demandId) {
        return demandPigMapper.selectVoList(
            new LambdaQueryWrapper<DemandPig>()
                .eq(DemandPig::getDemandId, demandId)
                .orderByDesc(DemandPig::getAssignedAt));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateExplain(Long demandId, String explain) {
        DemandManage exists = baseMapper.selectById(demandId);
        if (exists == null) {
            throw new ServiceException(I18nMessages.t("demand.not_found", demandId), 404);
        }
        if (explain != null && explain.length() > 500) {
            throw new ServiceException(I18nMessages.t("demand.explain.too_long"));
        }
        // 仅 patch demand_explain，不触碰状态/业务字段/audit_history
        DemandManage patch = new DemandManage();
        patch.setId(demandId);
        patch.setDemandExplain(explain);
        return baseMapper.updateById(patch);
    }

    @Override
    public List<AuditHistoryEntryVo> getAuditHistory(Long demandId) {
        DemandManage demand = baseMapper.selectById(demandId);
        if (demand == null) {
            throw new ServiceException(I18nMessages.t("demand.not_found", demandId), 404);
        }
        String raw = demand.getAuditHistory();
        if (StrUtil.isBlank(raw)) {
            return new ArrayList<>();
        }
        try {
            ObjectMapper om = JsonUtils.getObjectMapper();
            List<Map<String, Object>> parsed = om.readValue(
                raw, new TypeReference<List<Map<String, Object>>>() {
                });
            if (parsed == null) {
                return new ArrayList<>();
            }
            return parsed.stream().map(this::toEntryVo).filter(Objects::nonNull).toList();
        } catch (Exception e) {
            log.warn("[WMS-DEMAND-001] audit_history parse failed demandId={} raw={}", demandId, raw, e);
            return new ArrayList<>();
        }
    }

    @Override
    public DemandSummaryVo getSummary(String productType) {
        validateProductType(productType);
        DemandSummaryVo vo = new DemandSummaryVo();
        vo.setProductType(productType);
        switch (productType) {
            // 白条 / 猪：可出栏育肥猪头数（原型「可出栏猪只头数」）
            case "white_bar", "pig" -> vo.setAvailablePigs(pigQueryService.countAvailableForOutbound());
            case "vegetable" -> {
                PlantPlanSummaryVo agg = plantPlanService.aggregateForDemandSummary();
                vo.setPlotCount(agg.getPlotCount());
                vo.setExpectedYieldKg(agg.getExpectedYieldKg());
                vo.setEarliestPickDate(agg.getEarliestPickDate());
                // 当前蔬菜成品库存：product_info.belong_type='vegetable' 关联 location_stock SUM
                BigDecimal stock = locationStockMapper.sumStockByBelongType("vegetable");
                vo.setCurrentStockKg(stock != null ? stock : BigDecimal.ZERO);
            }
            case "gift_box" -> {
                // V1 兜底：D11 product_inhouse 未建，用 product_info.belong_type='gift_box' 关联 location_stock SUM
                BigDecimal stock = locationStockMapper.sumStockByBelongType("gift_box");
                vo.setGiftBoxStock(stock != null ? stock : BigDecimal.ZERO);
            }
            // 干货 / 鸡蛋 / 其他：原材料库存合计（原型干货·鸡蛋 tab「原材料库存」列）
            case "dry", "egg", "other" -> {
                // 原料/干货合计：product_attr=2（原材料）的所有 location_stock 之和
                BigDecimal stock = locationStockMapper.sumRawMaterialStock();
                vo.setRawMaterialStockKg(stock != null ? stock : BigDecimal.ZERO);
            }
            default -> throw new ServiceException(I18nMessages.t("demand.field.productType.invalid", productType));
        }
        return vo;
    }

    @Override
    public DemandTodayKpiVo getTodayKpi() {
        LocalDate today = LocalDate.now(TODAY_ZONE);
        DemandTodayKpiVo vo = new DemandTodayKpiVo();

        // 主表 1 query：白条需求头数 + 果蔬需求/已调配品种数 + 其他需求/已调配条数
        //（「已调配」= demand_status IN CONFIRMED/IN_PRODUCTION/PARTIAL_SHIPPED/COMPLETED，写死在 mapper SQL）
        Map<String, Object> agg = baseMapper.selectTodayKpiMainAgg(today);
        vo.setTodayPigDemand(intFromAgg(agg, "todayPigDemand"));
        vo.setTodayVegSpeciesDemand(intFromAgg(agg, "todayVegSpeciesDemand"));
        vo.setTodayVegSpeciesAssigned(intFromAgg(agg, "todayVegSpeciesAssigned"));
        vo.setTodayOtherDemand(intFromAgg(agg, "todayOtherDemand"));
        vo.setTodayOtherAssigned(intFromAgg(agg, "todayOtherAssigned"));

        // 子表 1 query：白条已调配头数（去重耳号）
        Integer pigAssigned = baseMapper.selectTodayPigAssigned(today);
        vo.setTodayPigAssigned(pigAssigned == null ? 0 : pigAssigned);

        return vo;
    }

    // ---------- 内部辅助 ----------

    /**
     * 从聚合 Map 取 int；null（无行/无值）兜底为 0。
     *
     * <p>MySQL 聚合列在 jdbc 层可能映射为 {@code Long / BigDecimal / BigInteger}，统一走
     * {@link Number#intValue()}；非 Number（理论不会发生）兜底 0。</p>
     */
    private int intFromAgg(Map<String, Object> agg, String key) {
        if (agg == null) {
            return 0;
        }
        Object v = agg.get(key);
        return v instanceof Number num ? num.intValue() : 0;
    }

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus，{@code protected} 便于单测覆盖以避开
     * Spring 上下文（{@code MapstructUtils.convert} 静态字段依赖 {@code SpringUtils.getBean}）。
     */
    protected DemandManage toEntity(DemandManageBo bo) {
        return MapstructUtils.convert(bo, DemandManage.class);
    }

    private AuditHistoryEntryVo toEntryVo(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        AuditHistoryEntryVo vo = new AuditHistoryEntryVo();
        vo.setFrom(asString(map.get("from")));
        vo.setTo(asString(map.get("to")));
        vo.setEvent(asString(map.get("event")));
        Object op = map.get("operator");
        if (op instanceof Number num) {
            vo.setOperator(num.longValue());
        } else if (op instanceof String s && !s.isEmpty()) {
            try {
                vo.setOperator(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
                // ignore invalid operator id
            }
        }
        vo.setOperatorName(asString(map.get("operatorName")));
        Object ts = map.get("ts");
        if (ts != null) {
            try {
                vo.setTs(LocalDateTime.parse(ts.toString()));
            } catch (Exception ignored) {
                // ignore invalid ts
            }
        }
        vo.setRemark(asString(map.get("remark")));
        return vo;
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private void validateProductType(String type) {
        if (StrUtil.isBlank(type) || !ALLOWED_PRODUCT_TYPES.contains(type)) {
            throw new ServiceException(I18nMessages.t("demand.field.productType.invalid", type));
        }
    }

    /** 插入前缺省值兜底。 */
    private void applyInsertDefaults(DemandManage entity) {
        if (entity.getShippedCount() == null) {
            entity.setShippedCount(java.math.BigDecimal.ZERO);
        }
        if (entity.getConfirmedCount() == null) {
            entity.setConfirmedCount(java.math.BigDecimal.ZERO);
        }
        if (entity.getDemandStatus() == null) {
            entity.setDemandStatus(DemandStatus.DRAFT.name());
        }
    }

    /** 软删某 demand 的所有关联猪只。 */
    private int softDeleteDemandPigsByDemandId(Long demandId) {
        List<DemandPig> rows = demandPigMapper.selectList(
            new LambdaQueryWrapper<DemandPig>().eq(DemandPig::getDemandId, demandId));
        if (rows.isEmpty()) {
            return 0;
        }
        return softDeleteDemandPigsByIds(rows.stream().map(DemandPig::getId).toList());
    }

    /**
     * 软删 demand_pig 行：本类不能复用基类 {@link DjsBaseServiceImpl#softDelete}（
     * 基类绑定主 mapper {@code DemandManageMapper}）；直接做 wrapper-only update。
     */
    private int softDeleteDemandPigsByIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return 0;
        }
        Long updateBy = currentUserIdSafe();
        java.util.Date now = new java.util.Date();
        int count = 0;
        for (Long id : ids) {
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<DemandPig> wrapper =
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<DemandPig>update()
                    .eq("id", id)
                    .eq("del_flag", "0")
                    .set("del_flag", "1")
                    .set("del_unique", id)
                    .set("update_by", updateBy)
                    .set("update_time", now);
            count += demandPigMapper.update(null, wrapper);
        }
        return count;
    }

    private LambdaQueryWrapper<DemandManage> buildQueryWrapper(DemandManageQuery query) {
        LambdaQueryWrapper<DemandManage> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(DemandManage::getDemandDate).orderByDesc(DemandManage::getId);
        }
        wrapper.like(StringUtils.isNotBlank(query.getDemandNo()), DemandManage::getDemandNo, query.getDemandNo())
            .like(StringUtils.isNotBlank(query.getProductName()), DemandManage::getProductName, query.getProductName())
            .eq(StringUtils.isNotBlank(query.getProductType()), DemandManage::getProductType, query.getProductType())
            .eq(StringUtils.isNotBlank(query.getDemandStatus()), DemandManage::getDemandStatus, query.getDemandStatus())
            .eq(query.getStoreId() != null, DemandManage::getStoreId, query.getStoreId())
            .ge(query.getBeginDate() != null, DemandManage::getDemandDate, query.getBeginDate())
            .le(query.getEndDate() != null, DemandManage::getDemandDate, query.getEndDate())
            .orderByDesc(DemandManage::getDemandDate)
            .orderByDesc(DemandManage::getId);
        return wrapper;
    }
}
