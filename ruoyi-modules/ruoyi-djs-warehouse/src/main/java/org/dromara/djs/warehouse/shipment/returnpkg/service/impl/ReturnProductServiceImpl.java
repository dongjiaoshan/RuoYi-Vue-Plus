package org.dromara.djs.warehouse.shipment.returnpkg.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.common.util.I18nMessages;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.purchase.service.IWarehousePurchaseInService;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.ReturnProduct;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.bo.ReturnConfirmBo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.bo.ReturnProductBo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.query.ReturnProductQuery;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.vo.ReturnProductVo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.vo.ReturnStoreDailyVo;
import org.dromara.djs.warehouse.shipment.returnpkg.domain.vo.ReturnStoreGroupVo;
import org.dromara.djs.warehouse.shipment.returnpkg.mapper.ReturnProductMapper;
import org.dromara.djs.warehouse.shipment.returnpkg.service.IReturnProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 退货管理 Service 实现（WMS-SHIP-001）。
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Slf4j
@Service
public class ReturnProductServiceImpl
    extends DjsBaseServiceImpl<ReturnProductMapper, ReturnProduct>
    implements IReturnProductService {

    /**
     * 「今日」算法时区（D-FIX-24 决策 #6a 退货管理当天过滤）：不依赖 DB CURDATE() 时区，
     * 避免部署到非 UTC+8 实例时「今日」偏移埋雷。
     */
    private static final ZoneId RETURN_TODAY_ZONE = ZoneId.of("Asia/Shanghai");

    private static final String DIRECTION_STORE_TO_WAREHOUSE = "store_to_warehouse";

    /** 产品业务分类 belong_type — 果蔬（成品退回入库须换算原材料）。 */
    private static final String BELONG_TYPE_VEGETABLE = "vegetable";

    /** 门店退回入库：FIX-WMS-FLOWDICT-001 起从通用 return_in 拆为 store_return_in（来源 = 门店退货确认）。 */
    private static final String FLOW_TYPE_RETURN_IN = "store_return_in";

    private static final String INOUT_IN = "IN";

    private static final String STATUS_PENDING = "pending";

    private static final String STATUS_CONFIRMED = "confirmed";

    private final StockFlowMapper stockFlowMapper;

    private final IBizCodeGenerator bizCodeGenerator;

    private final StoreMapper storeMapper;

    private final ProductInfoMapper productInfoMapper;

    private final LocationInfoMapper locationInfoMapper;

    private final LocationStockMapper locationStockMapper;

    private final IWarehousePurchaseInService purchaseInService;

    public ReturnProductServiceImpl(ReturnProductMapper baseMapper,
                                    StockFlowMapper stockFlowMapper,
                                    IBizCodeGenerator bizCodeGenerator,
                                    StoreMapper storeMapper,
                                    ProductInfoMapper productInfoMapper,
                                    LocationInfoMapper locationInfoMapper,
                                    LocationStockMapper locationStockMapper,
                                    IWarehousePurchaseInService purchaseInService) {
        super(baseMapper);
        this.stockFlowMapper = stockFlowMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.storeMapper = storeMapper;
        this.productInfoMapper = productInfoMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.locationStockMapper = locationStockMapper;
        this.purchaseInService = purchaseInService;
    }

    @Override
    public TableDataInfo<ReturnProductVo> queryPageList(ReturnProductQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<ReturnProduct> wrapper = buildQueryWrapper(query);
        if (wrapper == null) {
            // returnCategory 命中 0 个产品 → 空结果
            return TableDataInfo.build();
        }
        Page<ReturnProductVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        enrichVos(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public TableDataInfo<ReturnStoreDailyVo> queryStoreDailyPage(ReturnProductQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<ReturnProduct> wrapper = buildQueryWrapper(query);
        if (wrapper == null) {
            // returnCategory 命中 0 个产品 → 空结果
            return TableDataInfo.build();
        }
        // 1. 拉出符合筛选的全部退货行（内存聚合范式同 listPendingGroups）。
        List<ReturnProduct> rows = baseMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            return TableDataInfo.build(new ArrayList<>());
        }
        // 2. 按 退货日期(apply_time 截到天) + store_id 分组（混合 pending / confirmed）。
        //    无 apply_time / store_id 的行不进汇总（外层视图按门店当日组织）。
        Map<String, List<ReturnProduct>> byGroup = rows.stream()
            .filter(r -> r.getApplyTime() != null && r.getStoreId() != null)
            .collect(Collectors.groupingBy(
                r -> r.getApplyTime().toLocalDate() + "|" + r.getStoreId(),
                LinkedHashMap::new, Collectors.toList()));
        if (byGroup.isEmpty()) {
            return TableDataInfo.build(new ArrayList<>());
        }
        // 3. 批量回填门店名（无 N+1）。
        Set<Long> storeIds = byGroup.values().stream()
            .map(g -> g.get(0).getStoreId()).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> storeNameMap = loadStoreNameMap(storeIds);
        // 4. 逐组算汇总。
        List<ReturnStoreDailyVo> all = new ArrayList<>(byGroup.size());
        for (List<ReturnProduct> group : byGroup.values()) {
            ReturnProduct any = group.get(0);
            ReturnStoreDailyVo vo = new ReturnStoreDailyVo();
            vo.setReturnDate(any.getApplyTime().toLocalDate());
            vo.setStoreId(any.getStoreId());
            vo.setStoreName(storeNameMap.get(any.getStoreId()));
            vo.setProductKindCount((int) group.stream()
                .map(ReturnProduct::getProductId).filter(Objects::nonNull).distinct().count());
            BigDecimal returnTotal = group.stream()
                .map(ReturnProduct::getReturnWeight).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal confirmTotal = group.stream()
                .map(ReturnProduct::getConfirmWeight).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            vo.setReturnWeightTotal(returnTotal);
            vo.setConfirmWeightTotal(confirmTotal);
            vo.setWeightDiffTotal(returnTotal.subtract(confirmTotal));
            // 确认时间 / 确认人 = 该组最近一条已确认行（confirm_time 最大）。
            group.stream()
                .filter(r -> r.getConfirmTime() != null)
                .max(Comparator.comparing(ReturnProduct::getConfirmTime))
                .ifPresent(latest -> {
                    vo.setConfirmTime(latest.getConfirmTime());
                    vo.setConfirmUser(latest.getConfirmUser());
                });
            all.add(vo);
        }
        // 5. 退货日期倒序、再门店倒序的稳定排序。
        all.sort(Comparator
            .comparing(ReturnStoreDailyVo::getReturnDate, Comparator.reverseOrder())
            .thenComparing(ReturnStoreDailyVo::getStoreId, Comparator.reverseOrder()));
        // 6. 内存分页。
        int total = all.size();
        int pageNum = Math.max(pageQuery == null || pageQuery.getPageNum() == null ? 1 : pageQuery.getPageNum(), 1);
        int pageSize = pageQuery == null || pageQuery.getPageSize() == null ? 10 : pageQuery.getPageSize();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        TableDataInfo<ReturnStoreDailyVo> dataInfo = new TableDataInfo<>();
        dataInfo.setCode(200);
        dataInfo.setRows(new ArrayList<>(all.subList(from, to)));
        dataInfo.setTotal(total);
        return dataInfo;
    }

    @Override
    public List<ReturnProductVo> queryList(ReturnProductQuery query) {
        LambdaQueryWrapper<ReturnProduct> wrapper = buildQueryWrapper(query);
        if (wrapper == null) {
            return List.of();
        }
        List<ReturnProductVo> list = baseMapper.selectVoList(wrapper);
        enrichVos(list);
        return list;
    }

    @Override
    public ReturnProductVo queryById(Long id) {
        ReturnProductVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            enrichVos(List.of(vo));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertByBo(ReturnProductBo bo) {
        ReturnProduct entity = MapstructUtils.convert(bo, ReturnProduct.class);
        // 业务默认
        entity.setReturnNo(generateReturnNo());
        entity.setApplyTime(bo.getApplyTime() == null ? LocalDateTime.now() : bo.getApplyTime());
        entity.setIsConfirm(0);
        entity.setReturnStatus(STATUS_PENDING);
        entity.setReturnDirection(StringUtils.isBlank(bo.getReturnDirection())
            ? DIRECTION_STORE_TO_WAREHOUSE : bo.getReturnDirection());
        baseMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(ReturnProductBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException(I18nMessages.t("return.id.required"));
        }
        ReturnProduct existing = baseMapper.selectById(bo.getId());
        if (existing == null) {
            throw new ServiceException(I18nMessages.t("return.not_found", bo.getId()), 404);
        }
        if (!STATUS_PENDING.equals(existing.getReturnStatus())) {
            throw new ServiceException(I18nMessages.t("return.status_immutable", existing.getReturnStatus()), 400);
        }
        ReturnProduct entity = MapstructUtils.convert(bo, ReturnProduct.class);
        // 不允许通过 update 改 returnNo / isConfirm / returnStatus / confirmUser 等关键字段
        entity.setReturnNo(null);
        entity.setIsConfirm(null);
        entity.setReturnStatus(null);
        entity.setConfirmUser(null);
        entity.setConfirmTime(null);
        entity.setConfirmWeight(null);
        return baseMapper.updateById(entity);
    }

    @Override
    public int deleteByIds(Collection<Long> ids) {
        // 走 DjsBaseServiceImpl#softDelete
        return softDelete(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReturn(Long id, ReturnConfirmBo bo) {
        Long userId = LoginHelper.getUserId();
        ReturnProduct entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new ServiceException(I18nMessages.t("return.not_found", id), 404);
        }
        if (entity.getIsConfirm() != null && entity.getIsConfirm() == 1) {
            throw new ServiceException(I18nMessages.t("return.already_confirmed"), 400);
        }

        // 1. UPDATE 行
        LocalDateTime now = LocalDateTime.now();
        ReturnProduct upd = new ReturnProduct();
        upd.setId(id);
        upd.setIsConfirm(1);
        upd.setReturnStatus(STATUS_CONFIRMED);
        upd.setConfirmUser(userId);
        upd.setConfirmTime(now);
        upd.setConfirmWeight(bo.getConfirmWeight());
        if (StringUtils.isNotBlank(bo.getRemark())) {
            upd.setRemark(bo.getRemark());
        }
        // 并发守卫（对齐 markDeliveryChecked 范式）：UPDATE 带未确认谓词（is_confirm 空或 ≠1），
        // 双击 / 两人同点同一单时只有一个请求真正命中；affected==0 = 已被并发确认 → 幂等返回，
        // 不再回补库存，杜绝双倍 location_stock + 双份 store_return_in 流水。
        int rows = baseMapper.update(upd, new LambdaUpdateWrapper<ReturnProduct>()
            .eq(ReturnProduct::getId, id)
            .and(w -> w.isNull(ReturnProduct::getIsConfirm).or().ne(ReturnProduct::getIsConfirm, 1)));
        if (rows == 0) {
            log.info("[WMS-SHIP-001] confirmReturn returnId={} 确认守卫未命中（已被并发确认），幂等跳过回补库存", id);
            return;
        }

        // 2. 仅 store_to_warehouse 方向真回库存（其他方向 V1 占位不联动）。
        //    门店退回到仓库 = 真把 confirmWeight 加回 location_stock + 写一条 return_in 流水，
        //    不是只写流水的「库存黑洞」（P9）。
        if (DIRECTION_STORE_TO_WAREHOUSE.equals(entity.getReturnDirection())) {
            replenishStockOnReturn(entity, bo.getConfirmWeight(), userId);
        } else {
            log.info("[WMS-SHIP-001] confirmReturn returnId={} direction={} placeholder（不联动 stock_flow，V2 实现）",
                id, entity.getReturnDirection());
        }
    }

    /**
     * 门店退回到仓库确认后真回库存（P9）。
     *
     * <p>入库目标产品先按 {@link #resolveReturnInboundProductId} 换算：果蔬成品退回入库用其原材料
     * {@code product_material}（不以成品入库，对齐 store 侧 StoreReturnServiceImpl 同规则）；
     * 其余自产成品（row42「生产产品不入库」，货架无成品账）→ 不动 {@code location_stock} 也不写流水，
     * 退货台账 {@code t_warehouse_return_product} 本身即记录，避免货架账变成只进不出的退货残堆。</p>
     *
     * <p>可入库产品解析出有效库位后 → 委托 {@link IWarehousePurchaseInService#inbound} 以
     * {@code flow_type='store_return_in'} 一次性完成「{@code location_stock += confirmWeight}」+
     * 「一条流水」（避免重复写两条流水）。</p>
     *
     * <p>容错：产品已删 / 系统未配置任何库位时无法定位入库目标 —— 不阻断确认流程（确认行已更新），
     * 退而求其次只写一条 store_return_in 流水（不增库存）并记 warn，由仓管事后手工盘点纠偏。</p>
     *
     * @param entity        退货行
     * @param confirmWeight 确认实收重量（> 0，调用前已由确认表单约束）
     * @param userId        操作人
     */
    private void replenishStockOnReturn(ReturnProduct entity, BigDecimal confirmWeight, Long userId) {
        Long productId = entity.getProductId();
        String remark = "门店退货入库 return_no=" + entity.getReturnNo() + " store_id=" + entity.getStoreId();

        ProductInfo product = productId == null ? null
            : productInfoMapper.selectOne(new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getId, productId).last("LIMIT 1"));
        // 兜底流水的记账产品：默认原产品；成品换算出原材料后取原材料（成品不应再产 IN 流水）。
        Long fallbackProductId = productId;
        if (product != null) {
            Long inboundProductId = resolveReturnInboundProductId(product);
            if (inboundProductId == null) {
                // row42：不入库成品无货架账，也不写 IN 流水（写了会让总览回放期末虚增），台账即退货记录。
                log.info("[WMS-SHIP-001] confirmReturn returnId={} productId={} 为不入库成品，"
                    + "不回 location_stock / 不写流水（退货记录见退货台账）", entity.getId(), productId);
                return;
            }
            fallbackProductId = inboundProductId;
            ProductInfo inboundProduct = Objects.equals(inboundProductId, productId) ? product
                : productInfoMapper.selectOne(new LambdaQueryWrapper<ProductInfo>()
                    .eq(ProductInfo::getId, inboundProductId).last("LIMIT 1"));
            Long locationId = inboundProduct == null ? null : resolveReturnLocationId(inboundProduct);
            if (locationId != null) {
                // 真回库存：inbound 内部 addByProductLocation（库存不存在则 INSERT 新行）+ 写一条流水。
                purchaseInService.inbound(inboundProductId, locationId, confirmWeight, FLOW_TYPE_RETURN_IN, remark);
                log.info("[WMS-SHIP-001] confirmReturn returnId={} → 真回库存 productId={} inboundProductId={} "
                        + "locationId={} +{}（store_return_in）",
                    entity.getId(), productId, inboundProductId, locationId, confirmWeight);
                return;
            }
        }

        // 兜底：无法定位入库库位（产品已删 / 无任何库位 / 产品无预设且无历史库存）→ 只写流水不增库存，不抛断流程。
        log.warn("[WMS-SHIP-001] confirmReturn returnId={} productId={} 无法解析入库库位，"
                + "仅写 store_return_in 流水不增库存（需仓管手工盘点纠偏）", entity.getId(), productId);
        StockFlow flow = new StockFlow();
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", INOUT_IN);
        flow.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx));
        flow.setFlowDate(new Date());
        flow.setProductId(fallbackProductId);
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(FLOW_TYPE_RETURN_IN);
        flow.setChangeNum(confirmWeight);
        flow.setChangeQuantity(confirmWeight);
        flow.setOperatorId(userId);
        flow.setRemark(remark + "（未定位库位，未增库存）");
        stockFlowMapper.insert(flow);
    }

    /**
     * 退回入库目标产品 id（对齐 store 侧 StoreReturnServiceImpl#resolveInboundProductId 规则）：
     * <ol>
     *   <li>原材料 / 外购商品（非「自产成品」）→ 用产品自身 id；</li>
     *   <li>果蔬成品（自产成品 + belongType=vegetable）→ 用其原材料 {@code product_material}
     *       （docx：不以成品入库，用原材料 ID）；</li>
     *   <li>其余自产成品（productType=1 且 productAttr=1，row42「生产产品不入库」货架无成品账）
     *       → 返 {@code null}，调用方跳过入库（成品只由打包产出/发货扣减，回货架会成退货残堆）。</li>
     * </ol>
     *
     * <p>与 store 侧差异：果蔬成品未配 {@code product_material} 时不阻断确认（本链容错契约），
     * 视同不入库成品返 {@code null}；运营补配 FK 后新退货自然走原材料入库。</p>
     *
     * @param product 退货产品
     * @return 入库目标产品 id，或 {@code null}（不入库成品，跳过库存联动）
     */
    private Long resolveReturnInboundProductId(ProductInfo product) {
        boolean selfMadeFinished = Integer.valueOf(1).equals(product.getProductType())
            && Integer.valueOf(1).equals(product.getProductAttr());
        if (!selfMadeFinished) {
            return product.getId();
        }
        if (BELONG_TYPE_VEGETABLE.equals(product.getBelongType()) && product.getProductMaterial() != null) {
            return product.getProductMaterial();
        }
        return null;
    }

    /**
     * 解析退货入库目标库位（P9）。
     *
     * <p>{@code ReturnProduct} 无 {@code location_id} 字段（不新增 DDL）；按产品预设 + 历史库存解析：</p>
     * <ol>
     *   <li>产品 {@code store_location_id}（逗号分隔预设库位列表）首个存在的有效项；</li>
     *   <li>否则取该产品当前库存最多的库位（{@code selectDefaultLocationByProduct}）；</li>
     *   <li>都没有 → 返 {@code null}（调用方走「只写流水不增库存」兜底）。</li>
     * </ol>
     *
     * <p>容错非数字 / 空 token：跳过继续下一个，不抛异常。</p>
     *
     * @param product 退货产品
     * @return 有效库位 id，或 {@code null}（无可定位库位）
     */
    private Long resolveReturnLocationId(ProductInfo product) {
        // ① 产品预设入库库位（store_location_id 逗号分隔，取首个存在的有效项）
        if (StringUtils.isNotBlank(product.getStoreLocationId())) {
            for (String token : product.getStoreLocationId().split(",")) {
                String trimmed = token.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                Long candidate;
                try {
                    candidate = Long.valueOf(trimmed);
                } catch (NumberFormatException ex) {
                    log.warn("[WMS-SHIP-001] 产品 productId={} store_location_id 含非法库位 token=[{}]，跳过",
                        product.getId(), trimmed);
                    continue;
                }
                if (locationInfoMapper.selectById(candidate) != null) {
                    return candidate;
                }
            }
        }
        // ② 该产品当前库存最多的库位（V1 单库位常态唯一）
        return locationStockMapper.selectDefaultLocationByProduct(product.getId());
    }

    @Override
    public List<ReturnStoreGroupVo> listPendingGroups() {
        // 1. 只取 mp 退货管理这条链：store_to_warehouse 方向 + 有门店 + 状态 pending/confirmed。
        //    （其他 2 方向是 admin 端占位录入，不进 mp 分组卡）
        //    只看当天退货：按业务日期 apply_time 落在今天（Asia/Shanghai）过滤，非 create_time，
        //    配合「当天退货当天确认」（D-FIX-24 决策 #6a）。t_warehouse_return_product 无独立 return_date 列，
        //    业务日期即申请时间 apply_time。
        LocalDate today = LocalDate.now(RETURN_TODAY_ZONE);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        List<ReturnProduct> rows = baseMapper.selectList(new LambdaQueryWrapper<ReturnProduct>()
            .eq(ReturnProduct::getReturnDirection, DIRECTION_STORE_TO_WAREHOUSE)
            .isNotNull(ReturnProduct::getStoreId)
            .in(ReturnProduct::getReturnStatus, List.of(STATUS_PENDING, STATUS_CONFIRMED))
            .ge(ReturnProduct::getApplyTime, todayStart)
            .lt(ReturnProduct::getApplyTime, tomorrowStart));
        if (rows.isEmpty()) {
            return List.of();
        }
        // 2. group by store_id（一门店一张卡，状态为派生值）。
        Map<Long, List<ReturnProduct>> byStore = rows.stream()
            .collect(Collectors.groupingBy(ReturnProduct::getStoreId));
        // 3. 批量填门店名（无 N+1）。
        Set<Long> storeIds = byStore.keySet();
        Map<Long, String> storeNameMap = loadStoreNameMap(storeIds);
        // 4. 每组算派生状态 + 品种数（不分状态去重）+ 最近退货时间。
        List<ReturnStoreGroupVo> list = new ArrayList<>(byStore.size());
        byStore.forEach((storeId, group) -> {
            ReturnStoreGroupVo vo = new ReturnStoreGroupVo();
            vo.setStoreId(storeId);
            vo.setStoreName(storeNameMap.get(storeId));
            // 派生状态：该门店当天退回记录全部 confirmed → 已确认，否则待确认。
            boolean allConfirmed = group.stream()
                .allMatch(r -> STATUS_CONFIRMED.equals(r.getReturnStatus()));
            vo.setReturnStatus(allConfirmed ? STATUS_CONFIRMED : STATUS_PENDING);
            vo.setProductKindCount((int) group.stream()
                .map(ReturnProduct::getProductId).filter(Objects::nonNull).distinct().count());
            vo.setReturnTime(group.stream()
                .map(ReturnProduct::getApplyTime).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null));
            list.add(vo);
        });
        // 5. 稳定排序：待确认在前，再按退货时间倒序。
        list.sort(Comparator
            .comparing((ReturnStoreGroupVo v) -> STATUS_PENDING.equals(v.getReturnStatus()) ? 0 : 1)
            .thenComparing(v -> v.getReturnTime() == null ? LocalDateTime.MIN : v.getReturnTime(),
                Comparator.reverseOrder()));
        return list;
    }

    @Override
    public List<ReturnProductVo> listByStoreAndStatus(Long storeId, String returnStatus) {
        if (storeId == null) {
            throw new ServiceException(I18nMessages.t("return.store.id_required"), 400);
        }
        if (StringUtils.isBlank(returnStatus)) {
            throw new ServiceException(I18nMessages.t("return.status.required"), 400);
        }
        return baseMapper.selectVoList(new LambdaQueryWrapper<ReturnProduct>()
            .eq(ReturnProduct::getReturnDirection, DIRECTION_STORE_TO_WAREHOUSE)
            .eq(ReturnProduct::getStoreId, storeId)
            .eq(ReturnProduct::getReturnStatus, returnStatus)
            .orderByDesc(ReturnProduct::getApplyTime));
    }

    // ---------- private helpers ----------

    /**
     * 批量取门店名（StoreMapper 跨域 in 查，无 N+1；参 ShipmentServiceImpl#loadStoreNameMap 范式）。
     */
    private Map<Long, String> loadStoreNameMap(Set<Long> storeIds) {
        if (storeIds.isEmpty()) {
            return Map.of();
        }
        return storeMapper.selectList(new LambdaQueryWrapper<Store>()
                .select(Store::getId, Store::getStoreName)
                .in(Store::getId, storeIds))
            .stream().collect(Collectors.toMap(Store::getId, Store::getStoreName, (a, b) -> a,
                LinkedHashMap::new));
    }

    /**
     * 构建列表查询条件。
     *
     * <p>返回 {@code null} 表示「退货品类」过滤命中 0 个产品 → 调用方应直接返回空结果。</p>
     */
    private LambdaQueryWrapper<ReturnProduct> buildQueryWrapper(ReturnProductQuery q) {
        LambdaQueryWrapper<ReturnProduct> w = new LambdaQueryWrapper<>();
        if (q == null) {
            return w.orderByDesc(ReturnProduct::getId);
        }
        // 退货品类（belongType）→ 命中产品集合，再按 product_id IN 过滤退货记录
        List<Long> categoryProductIds = null;
        if (StringUtils.isNotBlank(q.getReturnCategory())) {
            categoryProductIds = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                    .select(ProductInfo::getId)
                    .eq(ProductInfo::getBelongType, q.getReturnCategory()))
                .stream().map(ProductInfo::getId).filter(Objects::nonNull).collect(Collectors.toList());
            if (categoryProductIds.isEmpty()) {
                return null;
            }
        }
        w.like(StringUtils.isNotBlank(q.getReturnNo()), ReturnProduct::getReturnNo, q.getReturnNo())
            .eq(q.getStoreId() != null, ReturnProduct::getStoreId, q.getStoreId())
            .eq(q.getProductId() != null, ReturnProduct::getProductId, q.getProductId())
            .in(categoryProductIds != null, ReturnProduct::getProductId, categoryProductIds)
            .eq(q.getIsConfirm() != null, ReturnProduct::getIsConfirm, q.getIsConfirm())
            .eq(StringUtils.isNotBlank(q.getReturnDirection()),
                ReturnProduct::getReturnDirection, q.getReturnDirection())
            .eq(StringUtils.isNotBlank(q.getReturnStatus()),
                ReturnProduct::getReturnStatus, q.getReturnStatus())
            .ge(q.getApplyDateFrom() != null, ReturnProduct::getApplyTime,
                q.getApplyDateFrom() == null ? null : q.getApplyDateFrom().atStartOfDay())
            .le(q.getApplyDateTo() != null, ReturnProduct::getApplyTime,
                q.getApplyDateTo() == null ? null : q.getApplyDateTo().atTime(23, 59, 59))
            .orderByDesc(ReturnProduct::getId);
        return w;
    }

    /**
     * 批量回填列表派生列（对齐原型）：门店名 / 退货品类(belongType) / 退货产品编号 / 退货单位 /
     * 产品原材料名 / 重量差异。一次 in 查门店 + 产品 + 原材料，无 N+1。
     */
    private void enrichVos(List<ReturnProductVo> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        // 1. 门店名
        Set<Long> storeIds = vos.stream().map(ReturnProductVo::getStoreId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> storeNameMap = loadStoreNameMap(storeIds);
        // 2. 产品主数据（编号 / 单位 / 品类 / 原材料 FK）
        Set<Long> productIds = vos.stream().map(ReturnProductVo::getProductId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProductInfo> productMap = loadProductMap(productIds);
        // 3. 原材料产品名（product_material FK → 另一产品）
        Set<Long> materialIds = productMap.values().stream()
            .map(ProductInfo::getProductMaterial).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProductInfo> materialMap = loadProductMap(materialIds);
        // 4. 逐行回填
        for (ReturnProductVo vo : vos) {
            if (vo.getStoreId() != null) {
                vo.setStoreName(storeNameMap.get(vo.getStoreId()));
            }
            ProductInfo p = vo.getProductId() == null ? null : productMap.get(vo.getProductId());
            if (p != null) {
                vo.setReturnCategory(p.getBelongType());
                vo.setReturnProductCode(p.getProductId());
                vo.setProductUnit(p.getProductUnit());
                // 原材料：优先取 product_material 关联产品名；无关联则取自身名（自身即原材料）
                ProductInfo material = p.getProductMaterial() == null ? null : materialMap.get(p.getProductMaterial());
                vo.setProductMaterialName(material != null ? material.getProductName() : p.getProductName());
            }
            // 重量差异 = 退货重量 - 实收重量（未确认时 confirmWeight 为 null → 差异留空）
            if (vo.getReturnWeight() != null && vo.getConfirmWeight() != null) {
                vo.setWeightDiff(vo.getReturnWeight().subtract(vo.getConfirmWeight()));
            }
        }
    }

    /** 批量取产品主数据 map（无 N+1）。 */
    private Map<Long, ProductInfo> loadProductMap(Set<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .select(ProductInfo::getId, ProductInfo::getProductId, ProductInfo::getProductName,
                    ProductInfo::getProductUnit, ProductInfo::getBelongType, ProductInfo::getProductMaterial)
                .in(ProductInfo::getId, productIds))
            .stream().collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * 生成 return_no：{@code RET{yyyyMMdd}{seq4}}，走 {@link IBizCodeGenerator} 的
     * {@link BizCodeType#RETURN_NO} 规则（每日重置 + Redisson 锁 + 序号表 UNIQUE 双保护，
     * 与 BURN_NO / CUT_NO / BAR_NO 范式一致）。
     */
    private String generateReturnNo() {
        return bizCodeGenerator.generate(BizCodeType.RETURN_NO, Map.of());
    }
}
