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
import org.dromara.djs.warehouse.demand.domain.vo.DemandGroupVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPigVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandProductStoreDetailVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandSummaryVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandTodayKpiVo;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.demand.mapper.DemandPigAvailableMapper;
import org.dromara.djs.warehouse.demand.mapper.DemandPigMapper;
import org.dromara.djs.warehouse.demand.service.IDemandManageService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.breed.core.domain.vo.PigAvailableVo;
import org.dromara.djs.breed.production.service.IProductionCycleConfigService;
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
 * <p>软删走基类 {@link DjsBaseServiceImpl#softDelete}；删除前校验：仅 DRAFT / SUBMITTED / CANCELLED 态可删，
 * 删除时同时置 demand_status='DELETED' 终态留痕。</p>
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

    /** 自产产品(product_type=1) 属性 djs_product_attr：1=生产产品（成品）/ 2=原材料。 */
    private static final int PRODUCT_TYPE_SELF = 1;
    private static final int PRODUCT_ATTR_MATERIAL = 2;

    /**
     * 「门店不能下单原材料」守门生效的业态（egg/dry_good/other/vegetable）：这些域里原料(attr=2)
     * 领用后须打包成成品(attr=1)才可售（蛋/干货/其他 → 其他产品打包；毛菜 → 果蔬打包），
     * 原料本身不可被门店下单（doc/14 §5）。
     * 白条(white_bar) 整只/半只虽也是 attr=2，但属门店现卖单位（活跃下单流程），故豁免不纳入守门
     * （Kevin 2026-06-25 拍板）。范围如需调整在此集合增删业态即可。
     */
    private static final Set<String> RAW_MATERIAL_FORBIDDEN_BELONG_TYPES = Set.of("egg", "dry_good", "other", "vegetable");

    /** 允许删除的状态（删除 = 置 DELETED 终态 + 软删；待确认 SUBMITTED 亦可删）。 */
    private static final Set<String> DELETABLE_STATUSES = Set.of(
        DemandStatus.DRAFT.name(), DemandStatus.SUBMITTED.name(), DemandStatus.CANCELLED.name()
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

    /** 指定猪只「按出栏日龄过滤」可出栏查询（test row48；需求域自有 mapper，不改 breed）。 */
    private final DemandPigAvailableMapper demandPigAvailableMapper;

    /** 取养殖出栏日龄阈值 {@code slaughter_age_days}（test row48 指定猪只过滤用）。 */
    private final IProductionCycleConfigService productionCycleConfigService;

    /** SummaryBar 用：跨域薄壳查"进行中"种植计划聚合（DJS-FIX-ADMIN-W22-003）。 */
    private final IPlantPlanService plantPlanService;

    /** SummaryBar 用：按 belong_type / product_attr 聚合 location_stock（DJS-FIX-ADMIN-W22-003）。 */
    private final LocationStockMapper locationStockMapper;

    /** 下单守门：校验目标产品不是原材料（attr=2），门店只能下单生产产品。 */
    private final ProductInfoMapper productInfoMapper;

    public DemandManageServiceImpl(DemandManageMapper baseMapper,
                                   DemandPigMapper demandPigMapper,
                                   IBizCodeGenerator bizCodeGenerator,
                                   DemandPigAvailableMapper demandPigAvailableMapper,
                                   IProductionCycleConfigService productionCycleConfigService,
                                   IPlantPlanService plantPlanService,
                                   LocationStockMapper locationStockMapper,
                                   ProductInfoMapper productInfoMapper) {
        super(baseMapper);
        this.demandPigMapper = demandPigMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.demandPigAvailableMapper = demandPigAvailableMapper;
        this.productionCycleConfigService = productionCycleConfigService;
        this.plantPlanService = plantPlanService;
        this.locationStockMapper = locationStockMapper;
        this.productInfoMapper = productInfoMapper;
    }

    /** 出栏日龄阈值兜底（配置缺失时，与 mp PigAppletController.slaughterAge 一致）。 */
    private static final int DEFAULT_SLAUGHTER_AGE_DAYS = 175;

    /** 取生效出栏日龄阈值（custom 优先 → default → 175 兜底）。 */
    private int resolveSlaughterAgeDays() {
        Integer v = productionCycleConfigService.getValue("slaughter_age_days");
        return v != null && v > 0 ? v : DEFAULT_SLAUGHTER_AGE_DAYS;
    }

    @Override
    public TableDataInfo<PigAvailableVo> listAvailablePigsForOutbound(PageQuery pageQuery) {
        PageQuery pq = pageQuery != null ? pageQuery : new PageQuery(1, 10);
        int minAgeDays = resolveSlaughterAgeDays();
        return TableDataInfo.build(demandPigAvailableMapper.selectAvailableForOutboundPage(pq.build(), minAgeDays));
    }

    @Override
    public TableDataInfo<DemandManageVo> queryPageList(DemandManageQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<DemandManage> wrapper = buildQueryWrapper(query);
        Page<DemandManageVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillStoreCount(page.getRecords());
        fillStoreDemandStatus(page.getRecords());
        // 确认页下钻场景（带 productId）才回填「是否指定猪只」，主列表不查省一次子表 IN
        if (query != null && query.getProductId() != null) {
            fillPigAssigned(page.getRecords());
        }
        return TableDataInfo.build(page);
    }

    /**
     * 回填门店视角派生状态 {@code storeDemandStatus}（0613-04 门店端 5 态）。
     *
     * <p>映射规则：{@code SUBMITTED→待确认 / CONFIRMED 且未收货→已确认 / CONFIRMED 且已收货→确认到店(ARRIVED) /
     * PARTIAL_SHIPPED|COMPLETED→已发货(SHIPPED) / DELETED→已删除 / CANCELLED→已删除（门店端不区分取消/删除，统一灰显）}。
     * 仓库列表不读本字段（用 {@code demandStatus}），只为门店列表 dict-tag 提供门店语义状态。</p>
     */
    private void fillStoreDemandStatus(List<DemandManageVo> rows) {
        if (CollUtil.isEmpty(rows)) {
            return;
        }
        for (DemandManageVo vo : rows) {
            vo.setStoreDemandStatus(toStoreDemandStatus(vo.getDemandStatus(), vo.getReceivedTime() != null));
        }
    }

    /**
     * 仓库 7 态 → 门店视角 5 态码（{@code djs_store_demand_status}）。
     *
     * @param status   仓库 demand_status
     * @param received 是否已门店收货（received_time != null）
     * @return 门店视角状态码（SUBMITTED/CONFIRMED/SHIPPED/ARRIVED/DELETED）；未知态回退原值
     */
    private String toStoreDemandStatus(String status, boolean received) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "SUBMITTED" -> "SUBMITTED";
            case "CONFIRMED" -> received ? "ARRIVED" : "CONFIRMED";
            case "PARTIAL_SHIPPED", "COMPLETED" -> received ? "ARRIVED" : "SHIPPED";
            case "DELETED", "CANCELLED" -> "DELETED";
            // DRAFT 等门店端不可见态：回退原值（门店列表不展示这些行）
            default -> status;
        };
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

    /**
     * 回填「是否指定猪只」pigAssigned（0613-11 确认页）。
     *
     * <p>按当前页 demand id 一次批量查 {@code t_warehouse_demand_pig}，命中集合内的行置 true，其余 false。
     * 仅确认页（带 productId）调用。</p>
     */
    private void fillPigAssigned(List<DemandManageVo> rows) {
        if (CollUtil.isEmpty(rows)) {
            return;
        }
        List<Long> demandIds = rows.stream()
            .map(DemandManageVo::getId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (demandIds.isEmpty()) {
            return;
        }
        Set<Long> withPig = new HashSet<>(baseMapper.selectDemandIdsWithPig(demandIds));
        for (DemandManageVo vo : rows) {
            vo.setPigAssigned(vo.getId() != null && withPig.contains(vo.getId()));
        }
    }

    @Override
    public TableDataInfo<DemandGroupVo> queryGroupList(DemandManageQuery query, PageQuery pageQuery) {
        String productName = query == null ? null : query.getProductName();
        String productType = query == null ? null : query.getProductType();
        String demandStatus = query == null ? null : query.getDemandStatus();
        Long storeId = query == null ? null : query.getStoreId();
        LocalDate begin = query == null ? null : query.getBeginDate();
        LocalDate end = query == null ? null : query.getEndDate();
        // productType（业态）+ storeId（需求门店）组内/行级过滤，均下推到 mapper WHERE
        List<DemandGroupVo> all = baseMapper.selectDemandGroupList(productName, productType, storeId, begin, end);
        // 三态需求状态 + 确认率（按 storeCount / confirmedStoreCount 算）
        for (DemandGroupVo vo : all) {
            int storeCount = vo.getStoreCount() == null ? 0 : vo.getStoreCount();
            int confirmed = vo.getConfirmedStoreCount() == null ? 0 : vo.getConfirmedStoreCount();
            vo.setDemandStatus(groupStatus(storeCount, confirmed));
            vo.setConfirmRate(storeCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(confirmed).divide(BigDecimal.valueOf(storeCount), 4, java.math.RoundingMode.HALF_UP));
        }
        // 需求状态是聚合三态（PENDING/ALL_CONFIRMED/PARTIAL），算完后再内存过滤（不能下推 mapper WHERE）
        if (StringUtils.isNotBlank(demandStatus)) {
            all = all.stream()
                .filter(vo -> demandStatus.equals(vo.getDemandStatus()))
                .toList();
        }
        // 聚合后内存分页（分组行数小）
        int total = all.size();
        int pageNum = pageQuery == null || pageQuery.getPageNum() == null ? 1 : pageQuery.getPageNum();
        int pageSize = pageQuery == null || pageQuery.getPageSize() == null ? 10 : pageQuery.getPageSize();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(total, from + pageSize);
        List<DemandGroupVo> pageRows = from >= total ? List.of() : all.subList(from, to);
        TableDataInfo<DemandGroupVo> rsp = new TableDataInfo<>();
        rsp.setRows(pageRows);
        rsp.setTotal(total);
        rsp.setCode(200);
        rsp.setMsg("查询成功");
        return rsp;
    }

    /**
     * 分组三态（0613-10 点4）：无一已确认门店 → PENDING；全部确认 → ALL_CONFIRMED；否则 PARTIAL。
     */
    private String groupStatus(int storeCount, int confirmedStoreCount) {
        if (confirmedStoreCount <= 0) {
            return "PENDING";
        }
        if (confirmedStoreCount >= storeCount) {
            return "ALL_CONFIRMED";
        }
        return "PARTIAL";
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
        // 门店只能下单生产产品（成品 / 外购 / 礼盒），不能下单原材料（type=1 & attr=2）：
        // admin + mp 下单都收口到此 insertByBo，故服务端在此硬守门（客户端过滤只是 UX，可被旧缓存/直连绕过）。
        // 原料是仓库内部流转物（领用 → 打包成成品），由打包成品反查原料履约，不应直接挂门店需求（doc/14 §5）。
        validateNotRawMaterial(bo.getProductId(), bo.getProductName());
        DemandManage entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException(I18nMessages.t("demand.bo.convert_failed"));
        }
        // 生成需求单号
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("bizCode", PRODUCT_TYPE_TO_BIZ_CODE.get(bo.getProductType()));
        String demandNo = bizCodeGenerator.generate(BizCodeType.DEMAND_NO, ctx);
        entity.setDemandNo(demandNo);
        // 新增即「待确认」（SUBMITTED）：admin 购物车 / mp 门店录入都是「提交需求给仓库」动作，
        // 无独立「存草稿」环节。落 DRAFT 会让确认页无法确认（canConfirm 仅 SUBMITTED）+ 分组列表
        // 误显示状态，故初始态用状态机起点之后的「已提交」。
        entity.setDemandStatus(DemandStatus.SUBMITTED.name());
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

        // 仅 DRAFT / SUBMITTED（待确认）态允许编辑；其他态拒绝修改
        if (!FULLY_EDITABLE_STATUSES.contains(exists.getDemandStatus())) {
            throw new ServiceException(
                I18nMessages.t("demand.update.status_forbidden", exists.getDemandNo(), exists.getDemandStatus()), 400);
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
        // 校验所有 id 状态都允许删除（DRAFT/SUBMITTED/CANCELLED）
        List<DemandManage> demands = baseMapper.selectByIds(ids);
        for (DemandManage d : demands) {
            if (!DELETABLE_STATUSES.contains(d.getDemandStatus())) {
                throw new ServiceException(
                    I18nMessages.t("demand.delete.status_forbidden", d.getDemandNo(), d.getDemandStatus()));
            }
        }
        // 删除即置「已删除」终态留痕（行随后软删，从列表消失但 DB 可追溯）
        for (Long id : ids) {
            DemandManage patch = new DemandManage();
            patch.setId(id);
            patch.setDemandStatus(DemandStatus.DELETED.name());
            baseMapper.updateById(patch);
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
            // 白条 / 猪：可出栏育肥猪头数（原型「可出栏猪只头数」）。
            // row48：与「指定猪只」列表口径一致，按出栏日龄阈值过滤（只数到龄肥猪）。
            case "white_bar", "pig" -> vo.setAvailablePigs(demandPigAvailableMapper.countAvailableForOutbound(resolveSlaughterAgeDays()));
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
        // 白条猪需求头数含半只 0.5 折算，可出小数 → BigDecimal（其余仍为整数计数）
        vo.setTodayPigDemand(bdFromAgg(agg, "todayPigDemand"));
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
     * 从聚合 Map 取 BigDecimal；null（无行/无值）兜底为 {@link BigDecimal#ZERO}。
     *
     * <p>用于含 0.5 折算的「今日猪需求头数」（半只 ×0.5），不能走 {@link #intFromAgg} 截断；
     * 末尾去多余 0（如 7.50 → 7.5、7.00 → 7），前端直接 stringify 展示。</p>
     */
    private BigDecimal bdFromAgg(Map<String, Object> agg, String key) {
        if (agg == null) {
            return BigDecimal.ZERO;
        }
        Object v = agg.get(key);
        if (v instanceof BigDecimal bd) {
            return bd.stripTrailingZeros();
        }
        return v instanceof Number num ? BigDecimal.valueOf(num.doubleValue()).stripTrailingZeros() : BigDecimal.ZERO;
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

    /**
     * 守门：其他产品打包域（egg/dry_good/other）的目标产品不能是原材料（自产 type=1 且 attr=2）。
     * 门店只能下单生产产品（成品），原料由打包成成品后反查履约。
     * 产品不存在不在此拦（交由 productId.required + 后续业务），仅对「确为原料」的产品抛业务异常。
     * 白条 carcass / 蔬菜暂不纳入（见 {@link #RAW_MATERIAL_FORBIDDEN_BELONG_TYPES}）。
     */
    private void validateNotRawMaterial(Long productId, String productName) {
        if (productId == null) {
            return;
        }
        ProductInfo product = productInfoMapper.selectById(productId);
        if (product == null) {
            return;
        }
        boolean isRawMaterial = product.getProductType() != null && product.getProductType() == PRODUCT_TYPE_SELF
            && product.getProductAttr() != null && product.getProductAttr() == PRODUCT_ATTR_MATERIAL
            && RAW_MATERIAL_FORBIDDEN_BELONG_TYPES.contains(product.getBelongType());
        if (isRawMaterial) {
            String name = StrUtil.isNotBlank(product.getProductName()) ? product.getProductName()
                : StrUtil.isNotBlank(productName) ? productName : String.valueOf(productId);
            throw new ServiceException(I18nMessages.t("demand.field.product.raw_material_forbidden", name));
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
            entity.setDemandStatus(DemandStatus.SUBMITTED.name());
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
            .eq(query.getProductId() != null, DemandManage::getProductId, query.getProductId())
            .eq(query.getDemandDate() != null, DemandManage::getDemandDate, query.getDemandDate())
            .ge(query.getBeginDate() != null, DemandManage::getDemandDate, query.getBeginDate())
            .le(query.getEndDate() != null, DemandManage::getDemandDate, query.getEndDate())
            .orderByDesc(DemandManage::getDemandDate)
            .orderByDesc(DemandManage::getId);
        return wrapper;
    }
}
