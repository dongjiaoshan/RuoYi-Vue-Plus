package org.dromara.djs.store.returns.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.store.returns.domain.StoreReturn;
import org.dromara.djs.store.returns.domain.bo.StoreReturnBatchBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnConfirmBo;
import org.dromara.djs.store.ledger.domain.StoreDailyLedger;
import org.dromara.djs.store.ledger.mapper.StoreDailyLedgerMapper;
import org.dromara.djs.store.returns.domain.query.StoreReturnQuery;
import org.dromara.djs.store.returns.domain.vo.StoreReturnVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnAppletItemVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnGroupVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnPorkCandidateVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnStoreDailyVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnVegCandidateVo;
import org.dromara.djs.store.returns.mapper.StoreReturnMapper;
import org.dromara.djs.store.returns.service.IStoreReturnService;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.purchase.service.IWarehousePurchaseInService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 门店退回管理 Service 实现（STR-RETURN-REBUILD-001，K4 简化重做）。
 *
 * <h3>范围（K4：顾客退回门店一态 + 联动外购入库）</h3>
 * <p>只做主场景 {@code customer_to_store}（绕开 P0#8 退回方向死结）。新增退回登记时<b>同事务联动外购入库</b>：
 * 调用 {@link IWarehousePurchaseInService#inbound} 把退回产品按指定库位加回 {@code location_stock}
 * 并写 {@code stock_flow(flow_type='return_in', IN)}。门店退回走外购入库通道，<b>不写
 * {@code t_warehouse_return_product}</b>（仓库侧退货由 WMS-SHIP-001 负责，避免双写库存）。</p>
 *
 * <h3>编辑/删除与库存的边界（V1）</h3>
 * <ul>
 *   <li>新增 = 一次性外购入库事件，库存随之 +。</li>
 *   <li>编辑只改元数据（门店/原因/日期/备注）；<b>产品/库位/数量为入库驱动字段，建后不可改</b>
 *       （{@link #updateByBo} 不回写这三列），避免记录与库存流水不一致。</li>
 *   <li>删除仅软删登记记录，<b>不冲销库存</b>（V1 限制，需冲销时另录一笔反向流水）。</li>
 * </ul>
 *
 * @author djs
 * @since STR-RETURN-REBUILD-001
 */
@Slf4j
@Service
public class StoreReturnServiceImpl
    extends DjsBaseServiceImpl<StoreReturnMapper, StoreReturn>
    implements IStoreReturnService {

    /** 顾客退回门店方向（insertByBo 单条直登的历史默认；与门店退仓库是两件事）。 */
    private static final String DIRECTION_CUSTOMER_TO_STORE = "customer_to_store";

    /** 门店退回仓库方向（退回操作 batchCreate 走此态：门店发起 → 仓库确认入库）。 */
    private static final String DIRECTION_STORE_TO_WAREHOUSE = "store_to_warehouse";

    /** 门店退回入库流水类型 djs_flow_type（FIX-WMS-FLOWDICT-001：门店退货走 store_return_in，与领用退回 pick_return_in 区分来源）。 */
    private static final String FLOW_TYPE_RETURN_IN = "store_return_in";

    /** 退货状态 djs_store_return_status：待仓库确认。 */
    private static final String STATUS_PENDING = "pending";

    /** 退货状态 djs_store_return_status：已入库（仓库确认实收后）。 */
    private static final String STATUS_RECEIVED = "received";

    /** mp 词表（djs_return_status）：待确认。store 的 pending 直接对应。 */
    private static final String MP_STATUS_PENDING = "pending";

    /** mp 词表（djs_return_status）：已确认。映射 store 的 received（mp 页用 confirmed，避免改 mp UI）。 */
    private static final String MP_STATUS_CONFIRMED = "confirmed";

    /**
     * 「猪肉产品」tab 归属类型（字典 djs_belong_type）：猪肉 + 白条。
     * 退回操作猪肉 tab 取这两类产品做固定候选清单，与门店关联无关。
     */
    private static final List<String> PORK_BELONG_TYPES = List.of("pork", "white_bar");

    /** 猪肉产品退回字典（阶段0 seed，dict_value=产品业务码 product_id）。 */
    private static final String DICT_PORK_RETURN_PRODUCT = "djs_pork_return_product";

    /** 果蔬归属类型（字典 djs_belong_type）：退回入库回退到原材料 product_material 的判定。 */
    private static final String BELONG_TYPE_VEGETABLE = "vegetable";

    /** 业务日时区（与项目其余「今日」口径一致，避免 DB CURDATE() 时区雷）。 */
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final StoreMapper storeMapper;
    private final ProductInfoMapper productInfoMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final IWarehousePurchaseInService purchaseInService;
    private final DemandManageMapper demandManageMapper;
    private final DictService dictService;
    private final StoreDailyLedgerMapper storeDailyLedgerMapper;

    public StoreReturnServiceImpl(StoreReturnMapper baseMapper,
                                  StoreMapper storeMapper,
                                  ProductInfoMapper productInfoMapper,
                                  LocationInfoMapper locationInfoMapper,
                                  IBizCodeGenerator bizCodeGenerator,
                                  IWarehousePurchaseInService purchaseInService,
                                  DemandManageMapper demandManageMapper,
                                  DictService dictService,
                                  StoreDailyLedgerMapper storeDailyLedgerMapper) {
        super(baseMapper);
        this.storeMapper = storeMapper;
        this.productInfoMapper = productInfoMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.purchaseInService = purchaseInService;
        this.demandManageMapper = demandManageMapper;
        this.dictService = dictService;
        this.storeDailyLedgerMapper = storeDailyLedgerMapper;
    }

    @Override
    public TableDataInfo<StoreReturnVo> queryPageList(StoreReturnQuery query, PageQuery pageQuery) {
        Page<StoreReturnVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(query));
        fillNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<StoreReturnVo> queryList(StoreReturnQuery query) {
        List<StoreReturnVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillNames(list);
        return list;
    }

    @Override
    public StoreReturnVo queryById(Long id) {
        StoreReturnVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillNames(List.of(vo));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertByBo(StoreReturnBo bo) {
        // 1. 产品必校验
        ProductInfo product = productInfoMapper.selectById(bo.getProductId());
        if (product == null) {
            throw new ServiceException("产品不存在或已删除：" + bo.getProductId(), 404);
        }
        // 2. 门店非空才校验存在（customer_to_store 主场景必填，其余方向可空）
        if (bo.getStoreId() != null && storeMapper.selectById(bo.getStoreId()) == null) {
            throw new ServiceException("门店不存在或已删除：" + bo.getStoreId(), 404);
        }

        StoreReturn entity = new StoreReturn();
        entity.setReturnNo(generateReturnNo());
        entity.setReturnDirection(StringUtils.isBlank(bo.getReturnDirection())
            ? DIRECTION_CUSTOMER_TO_STORE : bo.getReturnDirection());
        entity.setStoreId(bo.getStoreId());
        entity.setProductId(bo.getProductId());
        entity.setLocationId(bo.getLocationId());
        entity.setReturnQuantity(bo.getReturnQuantity());
        entity.setReturnReason(bo.getReturnReason());
        // member_id / trace_code 仅存值，无 FK 校验（t_store_member 同日并行、t_trace_code D14 才建）
        entity.setTraceCode(bo.getTraceCode());
        entity.setMemberId(bo.getMemberId());
        entity.setReturnDate(bo.getReturnDate() == null ? LocalDateTime.now() : bo.getReturnDate());
        entity.setOperatorId(LoginHelper.getUserId());
        entity.setRemark(bo.getRemark());
        // 单条直登即时入库 → 状态直接置 received（两段式的 confirm 态由 batchCreate + confirm 走）
        entity.setReturnStatus(STATUS_RECEIVED);
        baseMapper.insert(entity);

        // K4 联动外购入库：同事务 UPSERT location_stock + stock_flow(return_in)。
        // inbound 内部校验库位存在 / 数量 > 0，失败抛 → 整体回滚（退回记录一并撤销，不留半态）。
        purchaseInService.inbound(resolveInboundProductId(bo.getProductId()), bo.getLocationId(), bo.getReturnQuantity(),
            FLOW_TYPE_RETURN_IN, "门店退回入库：" + entity.getReturnNo());

        log.info("[STR-RETURN-REBUILD-001] return id={} no={} product={} location={} qty={} → return_in 联动入库",
            entity.getId(), entity.getReturnNo(), bo.getProductId(), bo.getLocationId(), bo.getReturnQuantity());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(StoreReturnBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("退回记录 ID 不能为空", 400);
        }
        StoreReturn existing = baseMapper.selectById(bo.getId());
        if (existing == null) {
            throw new ServiceException("退回记录不存在：" + bo.getId(), 404);
        }
        if (bo.getStoreId() != null && storeMapper.selectById(bo.getStoreId()) == null) {
            throw new ServiceException("门店不存在或已删除：" + bo.getStoreId(), 404);
        }

        // 只更新元数据：returnNo / operatorId / productId / locationId / returnQuantity 均不回写
        // （后三者是外购入库驱动字段，建后改会与已写 location_stock / stock_flow 不一致 → 锁死，见类注释）
        StoreReturn entity = new StoreReturn();
        entity.setId(bo.getId());
        if (StringUtils.isNotBlank(bo.getReturnDirection())) {
            entity.setReturnDirection(bo.getReturnDirection());
        }
        entity.setStoreId(bo.getStoreId());
        entity.setReturnReason(bo.getReturnReason());
        entity.setTraceCode(bo.getTraceCode());
        entity.setMemberId(bo.getMemberId());
        if (bo.getReturnDate() != null) {
            entity.setReturnDate(bo.getReturnDate());
        }
        entity.setRemark(bo.getRemark());
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchCreate(StoreReturnBatchBo bo) {
        if (storeMapper.selectById(bo.getStoreId()) == null) {
            throw new ServiceException("门店不存在或已删除：" + bo.getStoreId(), 404);
        }
        Long operatorId = LoginHelper.getUserId();
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        int created = 0;
        for (StoreReturnBatchBo.Item item : bo.getItems()) {
            ProductInfo product = productInfoMapper.selectById(item.getProductId());
            if (product == null) {
                throw new ServiceException("产品不存在或已删除：" + item.getProductId(), 404);
            }
            // 退回量度量：果蔬行用退回量（份/把/盒），猪肉行无退回量时回退退回重量（kg）——与下方落库口径一致。
            BigDecimal returnMetric = item.getReturnQuantity() != null
                ? item.getReturnQuantity() : item.getReturnWeight();
            // 上限校验：退回量 ≤ 该产品当日台账「期初库存 + 当日到货」。
            validateReturnWithinLedger(bo.getStoreId(), item.getProductId(), today, returnMetric, product.getProductName());

            StoreReturn entity = new StoreReturn();
            entity.setReturnNo(generateReturnNo());
            // 退回操作 = 门店退货给仓库 → 方向 store_to_warehouse（仓库侧据此过滤可见、门店盘点退回量据此聚合）。
            entity.setReturnDirection(DIRECTION_STORE_TO_WAREHOUSE);
            entity.setStoreId(bo.getStoreId());
            entity.setProductId(item.getProductId());
            // 退回重量(KG) 始终落 goods_weight；退回量（果蔬份数/把/盒）落 return_quantity，
            // 猪肉行无 returnQuantity 时回退 returnWeight（按重量计量，保留旧行为）。
            entity.setGoodsWeight(item.getReturnWeight());
            entity.setReturnQuantity(returnMetric);
            entity.setTraceCode(item.getTraceCode());
            entity.setReturnDate(LocalDateTime.now());
            entity.setOperatorId(operatorId);
            // 两段式：待仓库确认，不联动入库（库存联动延后到 confirm）
            entity.setReturnStatus(STATUS_PENDING);
            baseMapper.insert(entity);
            created++;
        }
        log.info("[STORE-RETURN-REALIGN-001] batchCreate store={} 行数={} → pending（未入库）",
            bo.getStoreId(), created);
        return created;
    }

    @Override
    public List<StoreReturnPorkCandidateVo> listPorkCandidates() {
        List<Long> dictIds = resolvePorkReturnProductIds();
        LambdaQueryWrapper<ProductInfo> w = new LambdaQueryWrapper<>();
        if (!dictIds.isEmpty()) {
            w.in(ProductInfo::getId, dictIds);
        } else {
            // 字典 djs_pork_return_product 为空 → 回退 belong_type 候选（向后兼容）
            w.in(ProductInfo::getBelongType, PORK_BELONG_TYPES);
        }
        List<ProductInfo> products = productInfoMapper.selectList(w.orderByAsc(ProductInfo::getId));
        return products.stream().map(p -> {
            StoreReturnPorkCandidateVo vo = new StoreReturnPorkCandidateVo();
            vo.setProductId(p.getId());
            vo.setProductName(p.getProductName());
            vo.setProductUnit(p.getProductUnit());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 猪肉退回候选产品 id：读字典 djs_pork_return_product 的 dict_value（产品业务码 product_id）
     * → 经 t_warehouse_product_info.product_id resolve 成雪花主键。字典空 → 返空（调用方回退 belong_type）。
     */
    private List<Long> resolvePorkReturnProductIds() {
        Map<String, String> dict = dictService.getAllDictByDictType(DICT_PORK_RETURN_PRODUCT);
        if (dict == null || dict.isEmpty()) {
            log.warn("[STORE-RETURN] 字典 {} 为空，猪肉退回候选回退 belong_type", DICT_PORK_RETURN_PRODUCT);
            return List.of();
        }
        List<String> codes = dict.keySet().stream()
            .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
        if (codes.isEmpty()) {
            return List.of();
        }
        return productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .in(ProductInfo::getProductId, codes).select(ProductInfo::getId))
            .stream().map(ProductInfo::getId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    @Override
    public List<StoreReturnVegCandidateVo> listVegCandidates(Long storeId) {
        if (storeId == null) {
            return List.of();
        }
        // docx：近两日（今天 + 昨天）发往该门店的果蔬。复用 selectStoreReceivedVegProducts 按天取，合并去重。
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        rows.addAll(demandManageMapper.selectStoreReceivedVegProducts(storeId, today));
        rows.addAll(demandManageMapper.selectStoreReceivedVegProducts(storeId, today.minusDays(1)));
        Map<Long, StoreReturnVegCandidateVo> dedup = new java.util.LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            Object pid = r.get("productId");
            if (pid == null) {
                continue;
            }
            Long productId = Long.valueOf(pid.toString());
            dedup.computeIfAbsent(productId, k -> {
                StoreReturnVegCandidateVo vo = new StoreReturnVegCandidateVo();
                vo.setProductId(productId);
                Object name = r.get("productName");
                vo.setProductName(name == null ? null : name.toString());
                Object unit = r.get("productUnit");
                vo.setProductUnit(unit == null ? null : unit.toString());
                return vo;
            });
        }
        return new java.util.ArrayList<>(dedup.values());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int confirm(StoreReturnConfirmBo bo) {
        StoreReturn existing = baseMapper.selectById(bo.getId());
        if (existing == null) {
            throw new ServiceException("退回记录不存在：" + bo.getId(), 404);
        }
        if (STATUS_RECEIVED.equals(existing.getReturnStatus())) {
            throw new ServiceException("该退回记录已确认入库，请勿重复确认", 400);
        }

        // 入库目标产品：果蔬成品→原材料 product_material（docx，缺料阻断），猪肉/其他→成品本身。
        Long inboundProductId = resolveInboundProductId(existing.getProductId());
        // 入库库位：前端显式选优先；mp 确认页只填实收量不选库位 → 按入库产品预设库位 / 库存最多库位兜底；
        // 仍无 → 阻断（不做「只写流水不增库存」的库存黑洞，提示运营先补库位）。
        Long locationId = bo.getLocationId() != null ? bo.getLocationId() : resolveDefaultLocation(inboundProductId);
        if (locationId == null) {
            throw new ServiceException("未指定入库库位且无法自动定位（产品无预设库位/无历史库存），请选择库位后再确认", 400);
        }

        StoreReturn entity = new StoreReturn();
        entity.setId(bo.getId());
        entity.setLocationId(locationId);
        entity.setReceivedQty(bo.getReceivedQty());
        // 实收重量缺省按实收量计（V1：果蔬/猪肉退回多按重量计量）
        entity.setReceivedWeight(bo.getReceivedWeight() == null ? bo.getReceivedQty() : bo.getReceivedWeight());
        entity.setConfirmUserId(LoginHelper.getUserId());
        entity.setConfirmTime(LocalDateTime.now());
        entity.setReturnStatus(STATUS_RECEIVED);
        int rows = baseMapper.updateById(entity);

        // 确认实收时才联动外购入库：同事务 UPSERT location_stock + stock_flow(store_return_in)，
        // inbound 内部校验库位 / 数量，失败抛 → 整体回滚（确认与入库一致，不留半态）。
        purchaseInService.inbound(inboundProductId, locationId, bo.getReceivedQty(),
            FLOW_TYPE_RETURN_IN, "门店退回仓库确认入库：" + existing.getReturnNo());

        log.info("[STORE-RETURN-UNIFY-001] confirm id={} no={} location={} receivedQty={} inboundProduct={} → received 联动入库",
            bo.getId(), existing.getReturnNo(), locationId, bo.getReceivedQty(), inboundProductId);
        return rows;
    }

    @Override
    public TableDataInfo<StoreReturnStoreDailyVo> queryStoreDailyPage(StoreReturnQuery query, PageQuery pageQuery) {
        // 仓库「退货记录」外层主从视图：仅门店→仓库方向（不含 customer_to_store），按 退回日期(截到天)+门店 聚合。
        StoreReturnQuery q = query == null ? new StoreReturnQuery() : query;
        q.setReturnDirection(DIRECTION_STORE_TO_WAREHOUSE);
        List<StoreReturn> rows = baseMapper.selectList(buildQueryWrapper(q));
        if (rows.isEmpty()) {
            return TableDataInfo.build(new ArrayList<>());
        }
        Map<String, List<StoreReturn>> byGroup = rows.stream()
            .filter(r -> r.getReturnDate() != null && r.getStoreId() != null)
            .collect(Collectors.groupingBy(
                r -> r.getReturnDate().toLocalDate() + "|" + r.getStoreId(),
                LinkedHashMap::new, Collectors.toList()));
        if (byGroup.isEmpty()) {
            return TableDataInfo.build(new ArrayList<>());
        }
        Set<Long> storeIds = byGroup.values().stream()
            .map(g -> g.get(0).getStoreId()).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> storeNames = storeNameMap(new ArrayList<>(storeIds));
        List<StoreReturnStoreDailyVo> all = new ArrayList<>(byGroup.size());
        for (List<StoreReturn> group : byGroup.values()) {
            StoreReturn any = group.get(0);
            StoreReturnStoreDailyVo vo = new StoreReturnStoreDailyVo();
            vo.setReturnDate(any.getReturnDate().toLocalDate());
            vo.setStoreId(any.getStoreId());
            vo.setStoreName(storeNames.get(any.getStoreId()));
            vo.setProductKindCount((int) group.stream()
                .map(StoreReturn::getProductId).filter(Objects::nonNull).distinct().count());
            BigDecimal returnTotal = group.stream().map(StoreReturn::getGoodsWeight).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal confirmTotal = group.stream().map(StoreReturn::getReceivedWeight).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            vo.setReturnWeightTotal(returnTotal);
            vo.setConfirmWeightTotal(confirmTotal);
            vo.setWeightDiffTotal(returnTotal.subtract(confirmTotal));
            group.stream().filter(r -> r.getConfirmTime() != null)
                .max(Comparator.comparing(StoreReturn::getConfirmTime))
                .ifPresent(latest -> {
                    vo.setConfirmTime(latest.getConfirmTime());
                    vo.setConfirmUser(latest.getConfirmUserId());
                });
            all.add(vo);
        }
        all.sort(Comparator
            .comparing(StoreReturnStoreDailyVo::getReturnDate, Comparator.reverseOrder())
            .thenComparing(StoreReturnStoreDailyVo::getStoreId, Comparator.reverseOrder()));
        int total = all.size();
        int pageNum = Math.max(pageQuery == null || pageQuery.getPageNum() == null ? 1 : pageQuery.getPageNum(), 1);
        int pageSize = pageQuery == null || pageQuery.getPageSize() == null ? 10 : pageQuery.getPageSize();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        TableDataInfo<StoreReturnStoreDailyVo> dataInfo = new TableDataInfo<>();
        dataInfo.setCode(200);
        dataInfo.setRows(new ArrayList<>(all.subList(from, to)));
        dataInfo.setTotal(total);
        return dataInfo;
    }

    @Override
    public List<StoreReturnGroupVo> listPendingGroups() {
        // mp 退货管理分组卡：门店→仓库退回，按门店分组，状态派生（全 received→confirmed 否则 pending）。
        // 收件箱语义：pending（待确认）不限日期全列出，含历史未确认；received（已确认）只取近 7 天防无限堆积。
        LocalDateTime receivedFloor = LocalDate.now(ZONE_SHANGHAI).minusDays(7).atStartOfDay();
        List<StoreReturn> rows = baseMapper.selectList(new LambdaQueryWrapper<StoreReturn>()
            .eq(StoreReturn::getReturnDirection, DIRECTION_STORE_TO_WAREHOUSE)
            .isNotNull(StoreReturn::getStoreId)
            .and(w -> w
                .eq(StoreReturn::getReturnStatus, STATUS_PENDING)
                .or(o -> o
                    .eq(StoreReturn::getReturnStatus, STATUS_RECEIVED)
                    .ge(StoreReturn::getReturnDate, receivedFloor))));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<StoreReturn>> byStore = rows.stream()
            .collect(Collectors.groupingBy(StoreReturn::getStoreId));
        Map<Long, String> storeNames = storeNameMap(new ArrayList<>(byStore.keySet()));
        List<StoreReturnGroupVo> list = new ArrayList<>(byStore.size());
        byStore.forEach((storeId, group) -> {
            StoreReturnGroupVo vo = new StoreReturnGroupVo();
            vo.setStoreId(storeId);
            vo.setStoreName(storeNames.get(storeId));
            boolean allReceived = group.stream().allMatch(r -> STATUS_RECEIVED.equals(r.getReturnStatus()));
            vo.setReturnStatus(allReceived ? MP_STATUS_CONFIRMED : MP_STATUS_PENDING);
            vo.setProductKindCount((int) group.stream()
                .map(StoreReturn::getProductId).filter(Objects::nonNull).distinct().count());
            vo.setReturnTime(group.stream().map(StoreReturn::getReturnDate).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null));
            list.add(vo);
        });
        list.sort(Comparator
            .comparing((StoreReturnGroupVo v) -> MP_STATUS_PENDING.equals(v.getReturnStatus()) ? 0 : 1)
            .thenComparing(v -> v.getReturnTime() == null ? LocalDateTime.MIN : v.getReturnTime(),
                Comparator.reverseOrder()));
        return list;
    }

    @Override
    public List<StoreReturnAppletItemVo> listAppletItemsByStoreAndStatus(Long storeId, String mpStatus) {
        if (storeId == null) {
            throw new ServiceException("门店 ID 不能为空", 400);
        }
        if (StringUtils.isBlank(mpStatus)) {
            throw new ServiceException("退货状态不能为空", 400);
        }
        // mp 词表 → store 词表：confirmed→received，其余按 pending。
        String storeStatus = MP_STATUS_CONFIRMED.equals(mpStatus) ? STATUS_RECEIVED : STATUS_PENDING;
        List<StoreReturn> rows = baseMapper.selectList(new LambdaQueryWrapper<StoreReturn>()
            .eq(StoreReturn::getReturnDirection, DIRECTION_STORE_TO_WAREHOUSE)
            .eq(StoreReturn::getStoreId, storeId)
            .eq(StoreReturn::getReturnStatus, storeStatus)
            .orderByDesc(StoreReturn::getReturnDate));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = rows.stream().map(StoreReturn::getProductId)
            .filter(Objects::nonNull).distinct().toList();
        Map<Long, ProductInfo> productMap = productIds.isEmpty() ? Map.of()
            : productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                    .select(ProductInfo::getId, ProductInfo::getProductName, ProductInfo::getBelongType)
                    .in(ProductInfo::getId, productIds))
                .stream().collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));
        boolean received = STATUS_RECEIVED.equals(storeStatus);
        return rows.stream().map(r -> {
            StoreReturnAppletItemVo vo = new StoreReturnAppletItemVo();
            vo.setId(r.getId());
            vo.setReturnNo(r.getReturnNo());
            vo.setStoreId(r.getStoreId());
            vo.setApplyTime(r.getReturnDate());
            vo.setProductId(r.getProductId());
            ProductInfo p = r.getProductId() == null ? null : productMap.get(r.getProductId());
            vo.setProductName(p == null ? null : p.getProductName());
            vo.setProductCategory(p == null ? null : p.getBelongType());
            vo.setReturnWeight(r.getGoodsWeight());
            vo.setConfirmWeight(r.getReceivedWeight());
            vo.setIsConfirm(received ? 1 : 0);
            vo.setReturnReason(r.getReturnReason());
            vo.setReturnDirection(r.getReturnDirection());
            vo.setReturnStatus(received ? MP_STATUS_CONFIRMED : MP_STATUS_PENDING);
            vo.setRemark(r.getRemark());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public int deleteByIds(Collection<Long> ids) {
        // 走 DjsBaseServiceImpl#softDelete
        return softDelete(ids);
    }

    // ---------- private helpers ----------

    /**
     * 退回入库目标产品 id：果蔬成品退回入库用其原材料 product_material（docx：不以成品入库，用原材料 ID）；
     * 猪肉/其他用成品 id 本身。
     *
     * <p>果蔬成品未配 {@code product_material} → <b>阻断</b>退回入库（抛 {@link ServiceException}），
     * 不再静默回退用成品 id（成品不应有 return_in 加成品行、污染 location_stock 成品账；成品只由打包产出/发货扣减）。
     * 缺料属数据未配置，提示运营先在产品主数据补「果蔬成品→原材料」FK。</p>
     */
    private Long resolveInboundProductId(Long productId) {
        if (productId == null) {
            return null;
        }
        ProductInfo p = productInfoMapper.selectById(productId);
        if (p == null) {
            return productId;
        }
        if (BELONG_TYPE_VEGETABLE.equals(p.getBelongType())) {
            Long material = p.getProductMaterial();
            if (material == null) {
                throw new ServiceException(
                    "果蔬成品「" + p.getProductName() + "」未配原材料(product_material)，无法退回入库；请先在产品主数据配置后再确认退回", 400);
            }
            return material;
        }
        return productId;
    }

    /**
     * 退回入库默认库位兜底（确认未显式选库位时，如 mp 确认页只填实收量）：
     * <ol>
     *   <li>入库产品预设库位 {@code store_location_id}（逗号分隔，取首个存在的有效项）；</li>
     *   <li>否则取该产品当前库存最多的库位（{@code selectDefaultLocationByProduct}，V1 单库位常态唯一）；</li>
     *   <li>都无 → 返 {@code null}（调用方阻断确认，提示运营补库位）。</li>
     * </ol>
     * 非数字 / 空 token 跳过继续，不抛。入参为已解析的入库产品 id（果蔬已转原材料）。
     */
    private Long resolveDefaultLocation(Long inboundProductId) {
        if (inboundProductId == null) {
            return null;
        }
        ProductInfo p = productInfoMapper.selectById(inboundProductId);
        if (p != null && StringUtils.isNotBlank(p.getStoreLocationId())) {
            for (String token : p.getStoreLocationId().split(",")) {
                String trimmed = token.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                Long candidate;
                try {
                    candidate = Long.valueOf(trimmed);
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (locationInfoMapper.selectById(candidate) != null) {
                    return candidate;
                }
            }
        }
        // 取库存最多的「有效」库位（JOIN location_info 过滤已删库位的幽灵 stock 行，
        // 避免选中已删库位致 inbound 报「库位不存在或已删除」）。
        return baseMapper.selectStockMostValidLocation(inboundProductId);
    }

    /**
     * 退回上限校验（row52）：退回量不得超过该产品当日台账 {@code t_store_daily_ledger} 的
     * 「期初库存 {@code opening_qty} + 当日到货 {@code inbound_qty}」之和。
     *
     * <p><b>度量单位口径</b>：台账每行只有一个 opening/inbound 数值，按产品计量单位记
     * （果蔬=份/把/盒，猪肉=kg 按重量）；本校验用的 {@code returnMetric} 同口径——果蔬取退回量、
     * 猪肉取退回重量（与 {@link #batchCreate} 落 {@code return_quantity} 完全一致），故无需单重换算。</p>
     *
     * <p><b>无台账行时跳过</b>：门店盘点（建台账行）与退回操作是两个独立动作，退回时当日台账行
     * 常未建。此时无「期初/到货」记录可作上限，<b>不阻断</b>（仅 warn），避免误拦正常退回。
     * 台账行已存在时才以 opening+inbound 为硬上限。</p>
     *
     * @param storeId     门店
     * @param productId   产品（雪花主键）
     * @param ledgerDate  台账日期（当日，Asia/Shanghai）
     * @param returnMetric 本次退回量（与台账同口径；null 视为 0 不校验）
     * @param productName 产品名（提示用）
     */
    private void validateReturnWithinLedger(Long storeId, Long productId, LocalDate ledgerDate,
                                            BigDecimal returnMetric, String productName) {
        if (returnMetric == null || returnMetric.signum() <= 0) {
            return;
        }
        StoreDailyLedger ledger = storeDailyLedgerMapper.selectOne(
            new LambdaQueryWrapper<StoreDailyLedger>()
                .eq(StoreDailyLedger::getStoreId, storeId)
                .eq(StoreDailyLedger::getProductId, productId)
                .eq(StoreDailyLedger::getLedgerDate, ledgerDate)
                .last("LIMIT 1"));
        if (ledger == null) {
            log.warn("[STORE-RETURN] 产品 {}(store={}, date={}) 当日无台账行，跳过退回上限校验",
                productId, storeId, ledgerDate);
            return;
        }
        BigDecimal opening = ledger.getOpeningQty() == null ? BigDecimal.ZERO : ledger.getOpeningQty();
        BigDecimal inbound = ledger.getInboundQty() == null ? BigDecimal.ZERO : ledger.getInboundQty();
        BigDecimal limit = opening.add(inbound);
        if (returnMetric.compareTo(limit) > 0) {
            throw new ServiceException(
                "产品「" + productName + "」退回量(" + returnMetric.toPlainString()
                    + ")不能超过当日期初库存与到货之和(" + limit.toPlainString() + ")", 400);
        }
    }

    private LambdaQueryWrapper<StoreReturn> buildQueryWrapper(StoreReturnQuery q) {
        LambdaQueryWrapper<StoreReturn> w = new LambdaQueryWrapper<>();
        if (q == null) {
            return w.orderByDesc(StoreReturn::getReturnDate).orderByDesc(StoreReturn::getId);
        }
        w.like(StringUtils.isNotBlank(q.getReturnNo()), StoreReturn::getReturnNo, q.getReturnNo())
            .eq(q.getStoreId() != null, StoreReturn::getStoreId, q.getStoreId())
            .eq(q.getProductId() != null, StoreReturn::getProductId, q.getProductId())
            .eq(StringUtils.isNotBlank(q.getReturnStatus()),
                StoreReturn::getReturnStatus, q.getReturnStatus())
            .eq(StringUtils.isNotBlank(q.getReturnDirection()),
                StoreReturn::getReturnDirection, q.getReturnDirection())
            .ge(q.getReturnDateFrom() != null, StoreReturn::getReturnDate,
                q.getReturnDateFrom() == null ? null : q.getReturnDateFrom().atStartOfDay())
            .le(q.getReturnDateTo() != null, StoreReturn::getReturnDate,
                q.getReturnDateTo() == null ? null : q.getReturnDateTo().atTime(23, 59, 59))
            .orderByDesc(StoreReturn::getReturnDate)
            .orderByDesc(StoreReturn::getId);
        return w;
    }

    /**
     * 生成 return_no：{@code RET{yyyyMMdd}{seq4}}，复用 {@link BizCodeType#RETURN_NO}
     * （D11 BIZCODE-GOV 加，daily_reset + Redisson 锁 + 序号表 UNIQUE 双保护）。
     * protected 便于单测 stub。
     */
    protected String generateReturnNo() {
        return bizCodeGenerator.generate(BizCodeType.RETURN_NO, Map.of());
    }

    /**
     * 批量填 storeName + productName（一次性查 store / product 内存聚合，避免 N+1）。
     */
    private void fillNames(List<StoreReturnVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<Long, String> storeNames = storeNameMap(list.stream()
            .map(StoreReturnVo::getStoreId).filter(Objects::nonNull).distinct().toList());
        Map<Long, String> productNames = productNameMap(list.stream()
            .map(StoreReturnVo::getProductId).filter(Objects::nonNull).distinct().toList());
        Map<Long, String> locationNames = locationNameMap(list.stream()
            .map(StoreReturnVo::getLocationId).filter(Objects::nonNull).distinct().toList());
        for (StoreReturnVo vo : list) {
            if (vo.getStoreId() != null) {
                vo.setStoreName(storeNames.get(vo.getStoreId()));
            }
            if (vo.getProductId() != null) {
                vo.setProductName(productNames.get(vo.getProductId()));
            }
            if (vo.getLocationId() != null) {
                vo.setLocationName(locationNames.get(vo.getLocationId()));
            }
        }
    }

    private Map<Long, String> storeNameMap(List<Long> storeIds) {
        if (storeIds.isEmpty()) {
            return Map.of();
        }
        return storeMapper.selectList(
                new LambdaQueryWrapper<Store>().in(Store::getId, storeIds))
            .stream()
            .collect(Collectors.toMap(Store::getId, Store::getStoreName, (a, b) -> a));
    }

    private Map<Long, String> productNameMap(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>().in(ProductInfo::getId, productIds))
            .stream()
            .collect(Collectors.toMap(ProductInfo::getId, ProductInfo::getProductName, (a, b) -> a));
    }

    private Map<Long, String> locationNameMap(List<Long> locationIds) {
        if (locationIds.isEmpty()) {
            return Map.of();
        }
        return locationInfoMapper.selectList(
                new LambdaQueryWrapper<LocationInfo>().in(LocationInfo::getId, locationIds))
            .stream()
            .collect(Collectors.toMap(LocationInfo::getId, LocationInfo::getLocationName, (a, b) -> a));
    }
}
