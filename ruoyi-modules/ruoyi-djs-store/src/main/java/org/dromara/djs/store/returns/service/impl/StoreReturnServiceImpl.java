package org.dromara.djs.store.returns.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.dromara.djs.common.store.service.IStoreService;
import org.dromara.djs.store.ledger.domain.StoreDailyLedger;
import org.dromara.djs.store.ledger.mapper.StoreDailyLedgerMapper;
import org.dromara.djs.store.returns.domain.StoreReturn;
import org.dromara.djs.store.returns.domain.bo.StoreReturnBatchBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnConfirmBo;
import org.dromara.djs.store.returns.domain.query.StoreReturnQuery;
import org.dromara.djs.store.returns.domain.vo.StoreReturnVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnAppletItemVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnGroupVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnPorkCandidateVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnStoreDailyVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnVegCandidateVo;
import org.dromara.djs.store.returns.mapper.StoreReturnMapper;
import org.dromara.djs.store.returns.service.IStoreReturnService;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.pack.service.IProductProductionService;
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
import java.util.LinkedHashSet;
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
     * 白条产品退回字典（DENGBO-R11，dict_value=产品业务码 product_id）。门店退回操作「白条产品」来源。
     * 空字典客户在 admin 字典管理自配；单位取对应产品原材料单位、按重量退货。
     */
    private static final String DICT_WHITE_BAR_RETURN_PRODUCT = "djs_white_bar_return_product";

    /** 退回操作猪肉 tab 产品子类（DENGBO-R11）：pork=猪肉产品(到店成品,按份) / white_bar=白条产品(字典,按重量)。 */
    private static final String SUB_CAT_PORK = "pork";
    private static final String SUB_CAT_WHITE_BAR = "white_bar";

    /** 果蔬归属类型（字典 djs_belong_type）：退回入库回退到原材料 product_material 的判定。 */
    private static final String BELONG_TYPE_VEGETABLE = "vegetable";

    /** 产品属性（字典 djs_product_attr）：1=生产产品/成品，2=原材料。只有果蔬「成品」缺料才阻断退回入库。 */
    private static final int PRODUCT_ATTR_FINISHED = 1;

    /** 白条归属类型（字典 djs_belong_type）：门店当日白条到店判定。 */
    private static final String BELONG_TYPE_WHITE_BAR = "white_bar";

    /**
     * 礼盒归属类型（字典 {@code djs_belong_type}）：门店退回不支持。
     *
     * <p>row178：礼盒是多种原料的组合装，{@code product_material} 单值表达不了它拆回哪些原材料，
     * 退回确认时无法解析入库产品 / 库位，只会在确认那一步抛 400（mp 上仅弹一条红 toast，
     * 极易被当成网络抖动划过去，门店以为退成功、仓库永远收不到）。故在选品与提交两层直接拦掉。</p>
     */
    private static final String BELONG_TYPE_GIFT_BOX = "gift_box";

    /** product_production.is_delivery_check=1：已发货清点（到店白条口径与门店猪肉打包一致）。 */
    private static final Integer DELIVERY_CHECKED = 1;

    /** 业务日时区（与项目其余「今日」口径一致，避免 DB CURDATE() 时区雷）。 */
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final StoreMapper storeMapper;
    private final ProductInfoMapper productInfoMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final IWarehousePurchaseInService purchaseInService;
    private final DemandManageMapper demandManageMapper;
    private final DictService dictService;
    private final IProductProductionService productProductionService;
    private final ProductProductionMapper productProductionMapper;
    private final IStoreService storeService;
    private final StoreDailyLedgerMapper storeDailyLedgerMapper;

    public StoreReturnServiceImpl(StoreReturnMapper baseMapper,
                                  StoreMapper storeMapper,
                                  ProductInfoMapper productInfoMapper,
                                  LocationInfoMapper locationInfoMapper,
                                  IBizCodeGenerator bizCodeGenerator,
                                  IWarehousePurchaseInService purchaseInService,
                                  DemandManageMapper demandManageMapper,
                                  DictService dictService,
                                  IProductProductionService productProductionService,
                                  ProductProductionMapper productProductionMapper,
                                  IStoreService storeService,
                                  StoreDailyLedgerMapper storeDailyLedgerMapper) {
        super(baseMapper);
        this.storeMapper = storeMapper;
        this.productInfoMapper = productInfoMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.purchaseInService = purchaseInService;
        this.demandManageMapper = demandManageMapper;
        this.dictService = dictService;
        this.productProductionService = productProductionService;
        this.productProductionMapper = productProductionMapper;
        this.storeService = storeService;
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
        // 0. 已终止合作门店禁止退回（storeId 可空的方向由 assertStoreActive 直接放行）
        storeService.assertStoreActive(bo.getStoreId());
        // 1. 产品必校验
        ProductInfo product = productInfoMapper.selectById(bo.getProductId());
        if (product == null) {
            throw new ServiceException("产品不存在或已删除：" + bo.getProductId(), 404);
        }
        // 2. 门店非空才校验存在（customer_to_store 主场景必填，其余方向可空）
        if (bo.getStoreId() != null && storeMapper.selectById(bo.getStoreId()) == null) {
            throw new ServiceException("门店不存在或已删除：" + bo.getStoreId(), 404);
        }
        // 3. row178：门店→仓库方向拦礼盒（顾客退门店不受影响——那是门店自己收回，不回仓库库存）
        if (DIRECTION_STORE_TO_WAREHOUSE.equals(bo.getReturnDirection())) {
            assertReturnableToWarehouse(product);
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
        // row31：门店退回猪肉/果蔬成品无地块/耳号来源 → 入「退货专属篮」（plot/ear/white_bar 全空），
        // 不并进自产果蔬地块行/分割猪肉耳号行；再领用/发货不带追溯（客户确认符合）。
        purchaseInService.inboundReturnBasket(resolveInboundProductId(bo.getProductId()), bo.getLocationId(), bo.getReturnQuantity(),
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
        // 已终止合作门店禁止批量退回
        storeService.assertStoreActive(bo.getStoreId());
        if (storeMapper.selectById(bo.getStoreId()) == null) {
            throw new ServiceException("门店不存在或已删除：" + bo.getStoreId(), 404);
        }
        Long operatorId = LoginHelper.getUserId();
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        // 退回上限按 belong_type + admin row9 分流（懒算 + 逐条累加）：
        //   · 白条产品本身：累计 ≤ 当日到店白条总重（row142，保留）。
        //   · 案例①（admin row9）：当日到店猪肉成品配置的原材料产品 → ≤ 当日到店对应猪肉成品总重。
        //   · 案例②（DENGBO-R11）：当日到店白条 + 该产品属字典 djs_white_bar_return_product（白条产品）→ ≤ (当日到店白条总重 − 今日已退白条配置产品累计)。
        //   · 其余：逐产品 ≤ 当日到店该产品重（现状回退）。
        Map<Long, BigDecimal> arrivedWeightByMaterial = new LinkedHashMap<>(buildArrivedPorkWeightByMaterial(bo.getStoreId(), today));
        // row67：果蔬材料外售成品的退回候选被折叠成其原材料产品（foldVegMaterialSold），
        // 判定须镜像猪肉「按原材料聚合到店成品重」的口径，否则原材料产品在生产表无行、到店恒 0 → 无法退回。
        buildArrivedVegWeightByMaterial(bo.getStoreId(), today)
            .forEach((mid, w) -> arrivedWeightByMaterial.merge(mid, w, BigDecimal::add));
        List<Long> dictReturnProductIds = resolveWhiteBarReturnProductIds();
        boolean whiteBarArrivedToday = hasWhiteBarArrivedToday(bo.getStoreId());
        BigDecimal whiteBarDeliveredTotal = null;
        // 白条累计起点必须是「当日已入库的退回总重」，不能每次请求从 0 起：
        // 从 0 起的话闸只在单次请求内生效，连提 N 次每次都能退满当日到店白条总重。
        BigDecimal whiteBarReturnedAccum = null;
        Map<Long, BigDecimal> returnedBaselineByProduct = new LinkedHashMap<>();
        Map<Long, BigDecimal> returnedAccumByProduct = new LinkedHashMap<>();
        // row119：与前端 :max 同源的「剩余可退」闸——直接取候选接口算好的 到店量 − 今日已退，
        // 前端封顶只是体验，这里才是把关。候选里没有的产品（mp 退货录入可传任意产品）不进此闸，
        // 仍由下面按业态分流的重量校验兜底。
        Map<Long, BigDecimal> remainByProduct = buildRemainReturnableByProduct(bo.getStoreId());
        Map<Long, BigDecimal> remainAccumByProduct = new LinkedHashMap<>();
        int created = 0;
        for (StoreReturnBatchBo.Item item : bo.getItems()) {
            ProductInfo product = productInfoMapper.selectById(item.getProductId());
            if (product == null) {
                throw new ServiceException("产品不存在或已删除：" + item.getProductId(), 404);
            }
            assertReturnableToWarehouse(product);
            // 退回量度量：果蔬行用退回量（份/把/盒），猪肉行无退回量时回退退回重量（kg）——与下方落库口径一致。
            BigDecimal returnMetric = item.getReturnQuantity() != null
                ? item.getReturnQuantity() : item.getReturnWeight();
            BigDecimal rw = item.getReturnWeight() == null ? BigDecimal.ZERO : item.getReturnWeight();
            // row119：剩余可退闸（口径 = 候选接口的 到店量 − 今日已退；本批多行同产品继续累加）。
            assertWithinRemainReturnable(product, returnMetric, remainByProduct, remainAccumByProduct);
            BigDecimal materialLimit = arrivedWeightByMaterial.get(item.getProductId());
            if (BELONG_TYPE_WHITE_BAR.equals(product.getBelongType())) {
                // 白条：累计已退 + 本次 ≤ 当日到店白条总重（不按单个白条产品分别封顶）。
                if (whiteBarDeliveredTotal == null) {
                    whiteBarDeliveredTotal = sumWhiteBarDeliveredToStore(bo.getStoreId(), today);
                }
                if (whiteBarReturnedAccum == null) {
                    whiteBarReturnedAccum = sumReturnedWhiteBarTodayForStore(bo.getStoreId(), today);
                }
                BigDecimal projected = whiteBarReturnedAccum.add(rw);
                if (projected.compareTo(whiteBarDeliveredTotal) > 0) {
                    throw new ServiceException("白条产品退回重量累计(" + projected.toPlainString()
                        + ")不能超过当日到店白条总重(" + whiteBarDeliveredTotal.toPlainString() + ")", 400);
                }
                whiteBarReturnedAccum = projected;
            } else if (whiteBarArrivedToday && dictReturnProductIds.contains(item.getProductId())) {
                // admin row101 / row119：按产品用 max(当日盘点 期初+入库, 对应材料外售成品到店重) 独立封顶
                //（与候选接口 arrivedQuantity 同一口径，见 resolveWhiteBarDictArrived）。
                BigDecimal allowed = resolveWhiteBarDictArrived(bo.getStoreId(), today, item.getProductId(), materialLimit);
                BigDecimal baseline = returnedBaselineByProduct.computeIfAbsent(item.getProductId(),
                    productId -> sumReturnedTodayForProduct(bo.getStoreId(), today, productId));
                BigDecimal accumulated = returnedAccumByProduct.getOrDefault(item.getProductId(), BigDecimal.ZERO);
                BigDecimal projected = baseline.add(accumulated).add(rw);
                if (projected.compareTo(allowed) > 0) {
                    BigDecimal remain = allowed.subtract(baseline).subtract(accumulated).max(BigDecimal.ZERO);
                    throw new ServiceException("产品「" + product.getProductName() + "」退回重量(" + rw.toPlainString()
                        + ")不能超过当日到店量扣除今日已退后的剩余(" + remain.toPlainString() + ")", 400);
                }
                returnedAccumByProduct.put(item.getProductId(), accumulated.add(rw));
            } else if (materialLimit != null) {
                // 案例①：材料外售原材料(不在白条退回字典)→ ≤ 当日到店对应成品总重（猪肉/果蔬同口径）。
                if (rw.compareTo(materialLimit) > 0) {
                    throw new ServiceException("产品「" + product.getProductName() + "」退回重量(" + rw.toPlainString()
                        + ")不能超过当日到店对应成品总重(" + materialLimit.toPlainString() + ")", 400);
                }
            } else {
                // 其余生产产品：退回重量 ≤ 当日送达该店该产品的总重量（逐产品封顶，现状回退）。
                // row15：非 kg 产品前端派生 rw=0 → 该重量封顶自动放行（口径变更已在报告标注）。
                validateReturnWithinDelivered(bo.getStoreId(), item.getProductId(), today, rw, product.getProductName());
            }

            StoreReturn entity = new StoreReturn();
            entity.setReturnNo(generateReturnNo());
            // 退回操作 = 门店退货给仓库 → 方向 store_to_warehouse（仓库侧据此过滤可见、门店盘点退回量据此聚合）。
            entity.setReturnDirection(DIRECTION_STORE_TO_WAREHOUSE);
            entity.setStoreId(bo.getStoreId());
            entity.setProductId(item.getProductId());
            // 退回产品重量(kg) 落 goods_weight（row15：kg 产品=退回量派生、非 kg=0，null 兜底 0）；
            // 退回量按产品单位落 return_quantity。
            entity.setGoodsWeight(rw);
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

    /**
     * row119：按产品算「剩余可退量」= 到店量 − 今日已退量，口径直接复用退回操作页的候选接口
     * （{@link #listPorkCandidates} / {@link #listVegCandidates}），保证前端 {@code :max} 与后端闸门同源。
     *
     * <p>到店量为空（不封顶）的候选不进 map；到店量为 0 的候选进 map 且值为 0 → 只能填 0（不可退）。
     * 猪肉、果蔬两 tab 同产品出现时取较小的剩余额度，避免绕闸。</p>
     *
     * @param storeId 门店
     * @return 产品 id → 剩余可退量（按产品单位；≥ 0）
     */
    private Map<Long, BigDecimal> buildRemainReturnableByProduct(Long storeId) {
        Map<Long, BigDecimal> remain = new LinkedHashMap<>();
        List<StoreReturnPorkCandidateVo> porkCandidates = listPorkCandidates(storeId);
        for (StoreReturnPorkCandidateVo c : porkCandidates) {
            mergeRemain(remain, c.getProductId(), c.getArrivedQuantity(), c.getReturnedQuantity());
        }
        for (StoreReturnVegCandidateVo c : listVegCandidates(storeId)) {
            mergeRemain(remain, c.getProductId(), c.getArrivedQuantity(), c.getReturnedQuantity());
        }
        return remain;
    }

    /** 剩余可退量并入 map：到店量为空 → 不封顶（不写入）；同产品多来源取较小额度。 */
    private void mergeRemain(Map<Long, BigDecimal> remain, Long productId, BigDecimal arrived, BigDecimal returned) {
        if (productId == null || arrived == null) {
            return;
        }
        BigDecimal used = returned == null ? BigDecimal.ZERO : returned;
        BigDecimal left = arrived.subtract(used).max(BigDecimal.ZERO);
        remain.merge(productId, left, BigDecimal::min);
    }

    /**
     * row119：单行退回量不得超过「剩余可退量」（本批同产品多行累加）。
     * 产品不在候选 map（不封顶 / mp 退货录入的任意产品）→ 直接放行，交给按业态分流的重量校验兜底。
     */
    private void assertWithinRemainReturnable(ProductInfo product, BigDecimal returnMetric,
                                              Map<Long, BigDecimal> remainByProduct,
                                              Map<Long, BigDecimal> remainAccumByProduct) {
        BigDecimal limit = remainByProduct.get(product.getId());
        if (limit == null) {
            return;
        }
        BigDecimal metric = returnMetric == null ? BigDecimal.ZERO : returnMetric;
        if (metric.signum() <= 0) {
            return;
        }
        BigDecimal accumulated = remainAccumByProduct.getOrDefault(product.getId(), BigDecimal.ZERO);
        BigDecimal projected = accumulated.add(metric);
        if (projected.compareTo(limit) > 0) {
            if (limit.signum() <= 0) {
                throw new ServiceException("产品「" + product.getProductName()
                    + "」当日没有到店（或今日已退完），不能退回", 400);
            }
            throw new ServiceException("产品「" + product.getProductName() + "」退回量("
                + projected.toPlainString() + ")不能超过当日到店量扣除今日已退后的剩余("
                + limit.toPlainString() + ")", 400);
        }
        remainAccumByProduct.put(product.getId(), projected);
    }

    @Override
    public List<StoreReturnPorkCandidateVo> listPorkCandidates(Long storeId) {
        if (storeId == null) {
            return List.of();
        }
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        // DENGBO-R11：猪肉 tab 候选拆两子类（保序、跨子类去重）：
        //   · 猪肉产品(pork)：当日到店的猪肉成品（belong_type=pork 且配了原材料 product_material）——按份退回，
        //     单位=成品自身单位（份，如「黑毛猪筒子骨700g/份」），退回量+单位+退回产品重量三列与果蔬一致（Kevin 2026-07-12）；
        //   · 白条产品(white_bar)：字典 djs_white_bar_return_product 配置产品（当日有白条到店才列）——按重量退货，单位=对应产品原材料单位。
        List<StoreReturnPorkCandidateVo> result = new ArrayList<>();
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        // row40：材料外售原材料行的到店量 = 对应成品当日到店重（kg），一次聚合避免逐行查（与 batchCreate 案例① 同口径）。
        Map<Long, BigDecimal> arrivedWeightByMaterial = buildArrivedPorkWeightByMaterial(storeId, today);

        for (ProductInfo finished : resolveArrivedPorkFinishedProducts(storeId, today)) {
            // DENGBO 原材料外售：成品若配置「是否原材料外售=是」且有原材料，候选改列其原材料产品
            // （name/单位取原材料，一般 kg → 按重量退货）；多成品共享同一原材料时 seen 去重成一行。
            Integer sold = finished.getIsMaterialSold();
            if (sold != null && sold == 1 && finished.getProductMaterial() != null) {
                ProductInfo material = productInfoMapper.selectById(finished.getProductMaterial());
                if (material != null && seen.add(material.getId())) {
                    // 材料外售原材料行到店量 = 对应成品到店重（kg）。
                    result.add(buildPorkCandidate(material, SUB_CAT_PORK, material.getProductUnit(),
                        arrivedWeightByMaterial.get(material.getId()), storeId, today));
                }
            } else if (seen.add(finished.getId())) {
                // 猪肉产品(按份)行到店量 = 当日到店该产品需求订购份数 SUM(demand_quantity)。
                result.add(buildPorkCandidate(finished, SUB_CAT_PORK, finished.getProductUnit(),
                    productProductionMapper.sumDeliveredQuantityToStore(storeId, finished.getId(), today), storeId, today));
            }
        }
        if (hasWhiteBarArrivedToday(storeId)) {
            List<ProductInfo> whiteBarProducts = resolveWhiteBarReturnDictProducts();
            Map<Long, String> materialUnits = resolveMaterialUnits(whiteBarProducts);
            for (ProductInfo p : whiteBarProducts) {
                BigDecimal allowed = resolveWhiteBarDictArrived(storeId, today, p.getId(),
                    arrivedWeightByMaterial.getOrDefault(p.getId(), BigDecimal.ZERO));
                if (seen.add(p.getId())) {
                    result.add(buildPorkCandidate(p, SUB_CAT_WHITE_BAR,
                        materialUnits.getOrDefault(p.getId(), p.getProductUnit()), allowed, storeId, today));
                } else {
                    result.stream().filter(candidate -> Objects.equals(candidate.getProductId(), p.getId()))
                        .findFirst().ifPresent(candidate -> {
                            candidate.setSubCategory(SUB_CAT_WHITE_BAR);
                            candidate.setProductUnit(materialUnits.getOrDefault(p.getId(), p.getProductUnit()));
                            candidate.setArrivedQuantity(allowed);
                        });
                }
            }
        }
        return result;
    }

    private StoreReturnPorkCandidateVo buildPorkCandidate(ProductInfo p, String subCategory, String unit,
                                                          BigDecimal arrivedQuantity, Long storeId, LocalDate today) {
        StoreReturnPorkCandidateVo vo = new StoreReturnPorkCandidateVo();
        vo.setProductId(p.getId());
        vo.setProductName(p.getProductName());
        vo.setProductUnit(unit);
        vo.setSubCategory(subCategory);
        // row178：归属类型回传前端做礼盒二次过滤（猪肉 tab 的候选源已按 belong_type IN (pork,white_bar) 过滤，
        // 这里只是把判据显式化，前后端同源）。
        vo.setBelongType(p.getBelongType());
        vo.setArrivedQuantity(arrivedQuantity);
        vo.setReturnedQuantity(sumReturnedQuantityTodayForProduct(storeId, today, p.getId()));
        return vo;
    }

    /**
     * row119：白条字典产品「当日到店量」（退回上限口径）
     * = {@code max(当日盘点账面可支配量, 材料外售成品当日到店重)}。
     *
     * <p>账面可支配量 = {@code t_store_daily_ledger} 当日 {@code opening_qty + inbound_qty}——门店在盘点里自报的
     * 「期初 + 当日入库」，与盘点明细「当日入库」列同源。白条分割成的部件由门店手工录入库量，材料外售成品送来的
     * 那部分也要求门店计入同一行的入库量，因此两者<b>取大不相加</b>：相加会把外售那部分双计，让退回量超过盘点
     * 当日入库（客户实测 扇子骨 退 3.000 > 入库 2.000），进而把盘点损耗算成负数。</p>
     *
     * <p>盘点尚未录入时账面为 0，回落材料外售成品当日到店重，保证盘点前仍能按实到重量退回。</p>
     */
    private BigDecimal resolveWhiteBarDictArrived(Long storeId, LocalDate today, Long productId,
                                                  BigDecimal materialSoldArrived) {
        BigDecimal ledgerAvailable = sumStoreLedgerAvailable(storeId, today, productId);
        BigDecimal fallback = materialSoldArrived == null ? BigDecimal.ZERO : materialSoldArrived;
        return ledgerAvailable.max(fallback);
    }

    /** 白条产品退回字典 djs_white_bar_return_product 配置产品（空字典 → 空，不回退 belong_type）。 */
    private List<ProductInfo> resolveWhiteBarReturnDictProducts() {
        List<Long> ids = resolveWhiteBarReturnProductIds();
        if (ids.isEmpty()) {
            return List.of();
        }
        return productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
            .in(ProductInfo::getId, ids).orderByAsc(ProductInfo::getId));
    }

    /**
     * 白条产品退回候选产品 id：读字典 {@link #DICT_WHITE_BAR_RETURN_PRODUCT} 的 dict_value（产品业务码 product_id）
     * → resolve 成雪花主键。空字典 → 空（不回退 belong_type，白条产品完全由字典驱动，客户自配）。
     */
    private List<Long> resolveWhiteBarReturnProductIds() {
        Map<String, String> dict = dictService.getAllDictByDictType(DICT_WHITE_BAR_RETURN_PRODUCT);
        if (dict == null || dict.isEmpty()) {
            log.warn("[STORE-RETURN] 字典 {} 为空，白条产品退回候选为空（待客户在 admin 字典管理配置）", DICT_WHITE_BAR_RETURN_PRODUCT);
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

    /**
     * 白条产品 → 对应产品原材料（{@code product_material}）的单位（DENGBO-R11）。
     * 无原材料 / 材料无单位 → 缺省（调用方回落产品自身单位）。
     */
    private Map<Long, String> resolveMaterialUnits(List<ProductInfo> products) {
        Map<Long, Long> productToMaterial = products.stream()
            .filter(p -> p.getProductMaterial() != null)
            .collect(Collectors.toMap(ProductInfo::getId, ProductInfo::getProductMaterial, (a, b) -> a, LinkedHashMap::new));
        if (productToMaterial.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> materialUnit = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .in(ProductInfo::getId, new LinkedHashSet<>(productToMaterial.values()))
                .select(ProductInfo::getId, ProductInfo::getProductUnit))
            .stream().filter(m -> m.getProductUnit() != null)
            .collect(Collectors.toMap(ProductInfo::getId, ProductInfo::getProductUnit, (a, b) -> a));
        Map<Long, String> result = new LinkedHashMap<>();
        productToMaterial.forEach((pid, mid) -> {
            String u = materialUnit.get(mid);
            if (u != null) {
                result.put(pid, u);
            }
        });
        return result;
    }

    /**
     * DENGBO-R11：当日到店的猪肉成品（belong_type=pork 且配置了原材料 product_material）。
     * 猪肉产品退回候选直接展示这些成品（按份，单位=成品自身单位），退回量+单位+退回产品重量三列与果蔬一致。
     * 无到店成品/无配置 → 空。退回校验落「其余」分支（≤ 当日到店该成品总重）。
     */
    private List<ProductInfo> resolveArrivedPorkFinishedProducts(Long storeId, LocalDate today) {
        List<Long> deliveredIds = productProductionMapper.selectDeliveredProductIdsToStore(storeId, today);
        if (deliveredIds == null || deliveredIds.isEmpty()) {
            return List.of();
        }
        return productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
            .in(ProductInfo::getId, deliveredIds)
            .eq(ProductInfo::getBelongType, "pork")
            .isNotNull(ProductInfo::getProductMaterial)
            .orderByAsc(ProductInfo::getId));
    }

    /**
     * admin row8/row9：当日到店猪肉成品按「配置的原材料产品」聚合到店重量。
     * key = product_material（原材料产品 id），value = 该原材料对应的当日到店猪肉成品总重（kg，SUM product_weight）。
     * 供退回候选（案例①原材料）与退回校验（案例①上限）共用，一次查询避免 N+1。
     */
    private Map<Long, BigDecimal> buildArrivedPorkWeightByMaterial(Long storeId, LocalDate today) {
        List<Long> deliveredIds = productProductionMapper.selectDeliveredProductIdsToStore(storeId, today);
        if (deliveredIds == null || deliveredIds.isEmpty()) {
            return Map.of();
        }
        List<ProductInfo> arrivedFinished = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
            .in(ProductInfo::getId, deliveredIds)
            .eq(ProductInfo::getBelongType, "pork")
            .isNotNull(ProductInfo::getProductMaterial));
        Map<Long, BigDecimal> byMaterial = new LinkedHashMap<>();
        for (ProductInfo q : arrivedFinished) {
            BigDecimal w = productProductionService.sumDeliveredWeightToStore(storeId, q.getId(), today);
            byMaterial.merge(q.getProductMaterial(), w == null ? BigDecimal.ZERO : w, BigDecimal::add);
        }
        return byMaterial;
    }

    /**
     * row67：当日到店的果蔬「材料外售」成品按「配置的原材料产品」聚合到店重量。
     * 门槛与退回候选折叠 {@link #foldVegMaterialSold} 一致：belong_type=vegetable 且 is_material_sold=1 且配了 product_material。
     * key = product_material（原材料产品 id），value = 该原材料对应的当日到店果蔬成品总重（kg，SUM product_weight）。
     * 候选折叠后原材料产品自身在生产表无行、到店恒 0；本聚合让退回校验案例①按对应成品到店重封顶。
     */
    private Map<Long, BigDecimal> buildArrivedVegWeightByMaterial(Long storeId, LocalDate today) {
        List<Long> deliveredIds = productProductionMapper.selectDeliveredProductIdsToStore(storeId, today);
        if (deliveredIds == null || deliveredIds.isEmpty()) {
            return Map.of();
        }
        List<ProductInfo> arrivedFinished = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
            .in(ProductInfo::getId, deliveredIds)
            .eq(ProductInfo::getBelongType, "vegetable")
            .eq(ProductInfo::getIsMaterialSold, 1)
            .isNotNull(ProductInfo::getProductMaterial));
        Map<Long, BigDecimal> byMaterial = new LinkedHashMap<>();
        for (ProductInfo q : arrivedFinished) {
            BigDecimal w = productProductionService.sumDeliveredWeightToStore(storeId, q.getId(), today);
            byMaterial.merge(q.getProductMaterial(), w == null ? BigDecimal.ZERO : w, BigDecimal::add);
        }
        return byMaterial;
    }

    /**
     * row119：门店当日盘点账面可支配量 = {@code t_store_daily_ledger} 当日该产品 {@code opening_qty + inbound_qty} 之和。
     * 无盘点行 → 0。与盘点明细「期初库存 / 当日入库」两列同源，退回上限据此封顶后损耗公式
     * {@code 期初+入库−销售−赠送+退货−退回−期末} 不会因退回超量变负。
     */
    private BigDecimal sumStoreLedgerAvailable(Long storeId, LocalDate today, Long productId) {
        List<StoreDailyLedger> rows = storeDailyLedgerMapper.selectList(new LambdaQueryWrapper<StoreDailyLedger>()
            .eq(StoreDailyLedger::getStoreId, storeId)
            .eq(StoreDailyLedger::getLedgerDate, today)
            .eq(StoreDailyLedger::getProductId, productId));
        BigDecimal total = BigDecimal.ZERO;
        for (StoreDailyLedger row : rows) {
            if (row.getOpeningQty() != null) {
                total = total.add(row.getOpeningQty());
            }
            if (row.getInboundQty() != null) {
                total = total.add(row.getInboundQty());
            }
        }
        return total;
    }

    /**
     * row119：该产品今日已提交的门店退仓<b>量</b>（{@code SUM(return_quantity)}，按产品单位计）。
     * 与 {@link #sumReturnedTodayForProduct}（按 kg 重量）区分：份 / 盒等计件产品重量恒 0，
     * 只有按量累计才能对「到店量」封顶，避免多次提交各自过闸。
     */
    private BigDecimal sumReturnedQuantityTodayForProduct(Long storeId, LocalDate today, Long productId) {
        return sumReturnedQuantitySinceForProduct(storeId, today, today, productId);
    }

    /**
     * 该产品在 [from, today] 窗口内已提交的门店退仓<b>量</b>（{@code SUM(return_quantity)}，按产品单位计）。
     *
     * <p>已退窗口必须与「到店量」的统计窗口一致，否则同一批到店量能被跨天重复退：
     * 果蔬到店量算今天 + 昨天两天（{@link #listVegCandidates}），已退量若只算今天，
     * 昨天退掉的部分今天不会被扣，1.000 的到店量能退成 2.000，负损耗照旧出现。</p>
     */
    private BigDecimal sumReturnedQuantitySinceForProduct(Long storeId, LocalDate from, LocalDate today, Long productId) {
        List<StoreReturn> rows = baseMapper.selectList(new LambdaQueryWrapper<StoreReturn>()
            .eq(StoreReturn::getStoreId, storeId)
            .eq(StoreReturn::getReturnDirection, DIRECTION_STORE_TO_WAREHOUSE)
            .eq(StoreReturn::getProductId, productId)
            .ge(StoreReturn::getReturnDate, from.atStartOfDay())
            .lt(StoreReturn::getReturnDate, today.plusDays(1).atStartOfDay()));
        BigDecimal total = BigDecimal.ZERO;
        for (StoreReturn r : rows) {
            if (r.getReturnQuantity() != null) {
                total = total.add(r.getReturnQuantity());
            }
        }
        return total;
    }

    /**
     * 该门店今日已提交的<b>白条业态</b>退仓重量合计，作为白条累计闸的起点基线。
     *
     * <p>白条按「当日到店白条总重」整体封顶、不按单产品分摊，所以基线也按业态整体取；
     * 缺了它，闸只在单次请求内生效，连提多次每次都能退满。</p>
     */
    private BigDecimal sumReturnedWhiteBarTodayForStore(Long storeId, LocalDate today) {
        List<Long> whiteBarProductIds = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getBelongType, BELONG_TYPE_WHITE_BAR)
                .select(ProductInfo::getId))
            .stream().map(ProductInfo::getId).filter(Objects::nonNull).collect(Collectors.toList());
        if (whiteBarProductIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<StoreReturn> rows = baseMapper.selectList(new LambdaQueryWrapper<StoreReturn>()
            .eq(StoreReturn::getStoreId, storeId)
            .eq(StoreReturn::getReturnDirection, DIRECTION_STORE_TO_WAREHOUSE)
            .in(StoreReturn::getProductId, whiteBarProductIds)
            .ge(StoreReturn::getReturnDate, today.atStartOfDay())
            .lt(StoreReturn::getReturnDate, today.plusDays(1).atStartOfDay()));
        BigDecimal total = BigDecimal.ZERO;
        for (StoreReturn r : rows) {
            if (r.getGoodsWeight() != null) {
                total = total.add(r.getGoodsWeight());
            }
        }
        return total;
    }

    /** admin row101：该产品今日已提交的门店退仓重量，供产品级额度扣减。 */
    private BigDecimal sumReturnedTodayForProduct(Long storeId, LocalDate today, Long productId) {
        List<StoreReturn> rows = baseMapper.selectList(new LambdaQueryWrapper<StoreReturn>()
            .eq(StoreReturn::getStoreId, storeId)
            .eq(StoreReturn::getReturnDirection, DIRECTION_STORE_TO_WAREHOUSE)
            .eq(StoreReturn::getProductId, productId)
            .ge(StoreReturn::getReturnDate, today.atStartOfDay())
            .lt(StoreReturn::getReturnDate, today.plusDays(1).atStartOfDay()));
        BigDecimal total = BigDecimal.ZERO;
        for (StoreReturn r : rows) {
            if (r.getGoodsWeight() != null) {
                total = total.add(r.getGoodsWeight());
            }
        }
        return total;
    }

    /**
     * 该门店「当日是否有白条产品到店」：该店当日确认收货（{@code received_time}=今天）的需求下，
     * 存在已发货清点（{@code is_delivery_check=1}）的 white_bar 业态成品。口径与门店猪肉打包可追溯白条一致
     * （门店确认收货后现场分割）。{@code storeId} 为空 → false。
     */
    private boolean hasWhiteBarArrivedToday(Long storeId) {
        if (storeId == null) {
            return false;
        }
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        List<Long> demandIds = demandManageMapper.selectList(new LambdaQueryWrapper<DemandManage>()
                .eq(DemandManage::getStoreId, storeId)
                .ge(DemandManage::getReceivedTime, today.atStartOfDay())
                .lt(DemandManage::getReceivedTime, today.plusDays(1).atStartOfDay())
                .select(DemandManage::getId))
            .stream().map(DemandManage::getId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (demandIds.isEmpty()) {
            return false;
        }
        List<Long> whiteBarProductIds = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getBelongType, BELONG_TYPE_WHITE_BAR)
                .select(ProductInfo::getId))
            .stream().map(ProductInfo::getId).filter(Objects::nonNull).collect(Collectors.toList());
        if (whiteBarProductIds.isEmpty()) {
            return false;
        }
        Long cnt = productProductionMapper.selectCount(new LambdaQueryWrapper<ProductProduction>()
            .in(ProductProduction::getDemandId, demandIds)
            .in(ProductProduction::getProductId, whiteBarProductIds)
            .eq(ProductProduction::getIsDeliveryCheck, DELIVERY_CHECKED));
        return cnt != null && cnt > 0;
    }

    /**
     * row142：当日该店「到店白条总重」= 所有 white_bar 业态产品当日送达该店重量之和。
     * 复用逐产品口径 {@link IProductProductionService#sumDeliveredWeightToStore}（按 delivery_check 当天、
     * demand.store_id 关联）对每个白条产品求和；与「其他产品逐产品封顶」同一到店口径，仅白条合并成总重封顶。
     */
    private BigDecimal sumWhiteBarDeliveredToStore(Long storeId, LocalDate date) {
        List<Long> whiteBarProductIds = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getBelongType, BELONG_TYPE_WHITE_BAR)
                .select(ProductInfo::getId))
            .stream().map(ProductInfo::getId).filter(Objects::nonNull).collect(Collectors.toList());
        BigDecimal total = BigDecimal.ZERO;
        for (Long pid : whiteBarProductIds) {
            BigDecimal w = productProductionService.sumDeliveredWeightToStore(storeId, pid, date);
            if (w != null) {
                total = total.add(w);
            }
        }
        return total;
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
            // row41：computeIfAbsent 仅建首条 VO（name/unit 取首条），到店量 arrivedQuantity 需把今天+昨天两天累加。
            StoreReturnVegCandidateVo vo = dedup.computeIfAbsent(productId, k -> {
                StoreReturnVegCandidateVo v = new StoreReturnVegCandidateVo();
                v.setProductId(productId);
                Object name = r.get("productName");
                v.setProductName(name == null ? null : name.toString());
                Object unit = r.get("productUnit");
                v.setProductUnit(unit == null ? null : unit.toString());
                v.setArrivedQuantity(BigDecimal.ZERO);
                return v;
            });
            BigDecimal arrived = toBigDecimal(r.get("arrivedQuantity"));
            if (arrived != null) {
                vo.setArrivedQuantity(vo.getArrivedQuantity() == null ? arrived : vo.getArrivedQuantity().add(arrived));
            }
        }
        // row52：镜像猪肉候选路径——果蔬成品若「是否原材料外售=是」且配了原材料，候选折叠为其原材料产品
        //（name/unit 取原材料；多成品共享同一原材料时合并成一行，保序）。
        List<StoreReturnVegCandidateVo> folded = dropGiftBoxCandidates(
            foldVegMaterialSold(new java.util.ArrayList<>(dedup.values())));
        // row119：折叠后按最终产品 id 填已退量，前端剩余可退 = 到店量 − 已退量。
        // 已退窗口取「昨天 + 今天」，与上面到店量的两天窗口对齐——只扣今天的话，昨天到店的量昨天退过一次、
        // 今天还能再拿到同样额度退第二次，负损耗照旧复现。
        for (StoreReturnVegCandidateVo vo : folded) {
            vo.setReturnedQuantity(sumReturnedQuantitySinceForProduct(storeId, today.minusDays(1), today, vo.getProductId()));
        }
        return folded;
    }

    /**
     * row178：果蔬候选剔除礼盒（{@code belong_type=gift_box}）并回填归属类型。
     *
     * <p>候选源按需求业态 {@code product_type='vegetable'} 取，礼盒需求走 {@code product_type='gift_box'}
     * 本不该混进来；但业态是需求录入时选的、与产品自身 {@code belong_type} 各存一份，选错就会漏出来，
     * 而礼盒退回到确认那步必然 400（拆不回单一原材料）。这里按产品自身归属再兜一道。</p>
     *
     * @param candidates 折叠后的候选（可空集）
     * @return 剔除礼盒并回填 {@code belongType} 的候选
     */
    private List<StoreReturnVegCandidateVo> dropGiftBoxCandidates(List<StoreReturnVegCandidateVo> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        List<Long> productIds = candidates.stream().map(StoreReturnVegCandidateVo::getProductId)
            .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (productIds.isEmpty()) {
            return candidates;
        }
        Map<Long, String> belongMap = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .select(ProductInfo::getId, ProductInfo::getBelongType)
                .in(ProductInfo::getId, productIds))
            .stream().filter(p -> p.getBelongType() != null)
            .collect(Collectors.toMap(ProductInfo::getId, ProductInfo::getBelongType, (a, b) -> a));
        List<StoreReturnVegCandidateVo> kept = new ArrayList<>(candidates.size());
        for (StoreReturnVegCandidateVo c : candidates) {
            String belongType = belongMap.get(c.getProductId());
            if (BELONG_TYPE_GIFT_BOX.equals(belongType)) {
                log.info("[STORE-RETURN] 果蔬退回候选剔除礼盒 productId={} name={}", c.getProductId(), c.getProductName());
                continue;
            }
            c.setBelongType(belongType);
            kept.add(c);
        }
        return kept;
    }

    /**
     * row52：果蔬候选的「材料外售 → 原材料」折叠（镜像 {@link #listPorkCandidates} 的原材料外售路径）。
     *
     * <p>门槛必须 {@code is_material_sold==1} 且配了 {@code product_material}——只看 product_material 会误伤
     * 「有机牛心甘蓝500g(is_material_sold=0)」这类正常成品。命中的候选替换成其原材料产品（id/name/unit 取原材料），
     * 未命中原样；再按有效 id 用 {@link LinkedHashMap} 去重合并、保序（多成品同原材料 → 一行）。</p>
     */
    private List<StoreReturnVegCandidateVo> foldVegMaterialSold(List<StoreReturnVegCandidateVo> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        List<Long> productIds = candidates.stream().map(StoreReturnVegCandidateVo::getProductId)
            .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, ProductInfo> infoMap = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .select(ProductInfo::getId, ProductInfo::getProductName, ProductInfo::getProductUnit,
                    ProductInfo::getIsMaterialSold, ProductInfo::getProductMaterial)
                .in(ProductInfo::getId, productIds))
            .stream().collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));
        // 收集命中的原材料 id，再批量查原材料产品的 name/unit（避免 N+1）。
        Set<Long> materialIds = new LinkedHashSet<>();
        for (StoreReturnVegCandidateVo c : candidates) {
            ProductInfo info = infoMap.get(c.getProductId());
            if (info != null && isMaterialSold(info) && info.getProductMaterial() != null) {
                materialIds.add(info.getProductMaterial());
            }
        }
        Map<Long, ProductInfo> materialMap = materialIds.isEmpty() ? Map.of()
            : productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                    .select(ProductInfo::getId, ProductInfo::getProductName, ProductInfo::getProductUnit)
                    .in(ProductInfo::getId, materialIds))
                .stream().collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));
        Map<Long, StoreReturnVegCandidateVo> folded = new LinkedHashMap<>();
        for (StoreReturnVegCandidateVo c : candidates) {
            ProductInfo info = infoMap.get(c.getProductId());
            Long effectiveId = c.getProductId();
            String name = c.getProductName();
            String unit = c.getProductUnit();
            if (info != null && isMaterialSold(info) && info.getProductMaterial() != null) {
                ProductInfo material = materialMap.get(info.getProductMaterial());
                if (material != null) {
                    effectiveId = material.getId();
                    name = material.getProductName();
                    unit = material.getProductUnit();
                }
            }
            Long key = effectiveId;
            String vName = name;
            String vUnit = unit;
            StoreReturnVegCandidateVo vo = folded.computeIfAbsent(key, k -> {
                StoreReturnVegCandidateVo v = new StoreReturnVegCandidateVo();
                v.setProductId(key);
                v.setProductName(vName);
                v.setProductUnit(vUnit);
                v.setArrivedQuantity(BigDecimal.ZERO);
                return v;
            });
            // row41：多成品共享同一原材料折叠成一行 → 到店量累加。
            BigDecimal arrived = c.getArrivedQuantity();
            if (arrived != null) {
                vo.setArrivedQuantity(vo.getArrivedQuantity() == null ? arrived : vo.getArrivedQuantity().add(arrived));
            }
        }
        return new ArrayList<>(folded.values());
    }

    /** 产品「是否原材料外售=是」判定（row52 折叠门槛：仅 is_material_sold==1 生效）。 */
    private static boolean isMaterialSold(ProductInfo p) {
        Integer sold = p.getIsMaterialSold();
        return sold != null && sold == 1;
    }

    /** row41：把 mapper 原生聚合结果（BigDecimal / Number / String）安全转 BigDecimal；空 → null（不封顶）。 */
    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(v.toString());
    }

    /** 产品单位是否按重量计（kg/公斤，不区分大小写；空 → false 按份数口径）。 */
    private static boolean isKgUnit(String unit) {
        if (unit == null) {
            return false;
        }
        String s = unit.trim().toLowerCase();
        return "kg".equals(s) || "公斤".equals(s);
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

        // row35：仓库称重重量上限校验。仓库确认页录入的仓库称重（receivedWeight，缺省回退 receivedQty）不得离谱：
        //   · 份数产品（单位非 kg）：≤ 当天到店该产品总重量（sumDeliveredWeightToStore，kg）；
        //   · 重量产品（单位 kg）：≤ 门店录入重量（goods_weight）的一倍。
        // 兜底放行：取不到到店重 / 门店录入重（0 或空）时不拦（避免历史数据/跨日确认误伤合法退货）。
        ProductInfo returnProduct = productInfoMapper.selectById(existing.getProductId());
        BigDecimal weighed = bo.getReceivedWeight() != null ? bo.getReceivedWeight()
            : (bo.getReceivedQty() != null ? bo.getReceivedQty() : BigDecimal.ZERO);
        if (returnProduct != null && weighed.signum() > 0) {
            if (isKgUnit(returnProduct.getProductUnit())) {
                BigDecimal storeEntered = existing.getGoodsWeight();
                if (storeEntered != null && storeEntered.signum() > 0 && weighed.compareTo(storeEntered) > 0) {
                    throw new ServiceException("仓库称重重量(" + weighed.toPlainString()
                        + "kg)不能超过门店录入重量(" + storeEntered.toPlainString() + "kg)", 400);
                }
            } else {
                LocalDate arriveDate = existing.getReturnDate() != null
                    ? existing.getReturnDate().toLocalDate() : LocalDate.now(ZONE_SHANGHAI);
                BigDecimal arrivedTotal = productProductionMapper.sumDeliveredWeightToStore(
                    existing.getStoreId(), existing.getProductId(), arriveDate);
                if (arrivedTotal != null && arrivedTotal.signum() > 0 && weighed.compareTo(arrivedTotal) > 0) {
                    throw new ServiceException("仓库称重重量(" + weighed.toPlainString()
                        + "kg)不能超过该产品当天到店总重量(" + arrivedTotal.toPlainString() + "kg)", 400);
                }
            }
        }

        // 入库目标产品：配了 product_material 的成品(果蔬/猪肉)→原材料 product_material（缺料阻断），
        // 本身即原材料(白条字典 kg 产品/外购原料)→按产品ID入库（邓博 2026-07-16：退回入库都是原材料）。
        Long inboundProductId = resolveInboundProductId(existing.getProductId());
        // 入库库位：前端显式选优先；mp 确认页只填实收量不选库位 → 按入库产品预设库位 / 库存最多库位兜底；
        // 仍无 → 阻断（不做「只写流水不增库存」的库存黑洞，提示运营先补库位）。
        Long locationId = bo.getLocationId() != null ? bo.getLocationId() : resolveDefaultLocation(inboundProductId);
        // row145.3：猪肉退货指定入库库位（鲜品库/冻品库，整单一次选，仅 pork 生效——白条不分流，Kevin 口径）
        if (StringUtils.isNotBlank(bo.getTargetLocationType()) && isPorkProduct(existing.getProductId())) {
            Long porkLoc = resolveReturnLocationByType(bo.getTargetLocationType());
            if (porkLoc != null) {
                locationId = porkLoc;
            }
        }
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
        // 并发守卫（对齐 markDeliveryChecked 范式）：UPDATE 带状态谓词，仅未确认行可置 received。
        // 慢网双击 / 两人同点同一单时只有一个请求真正命中；affected==0 = 已被并发确认 → 幂等返回，
        // 不再联动入库，杜绝双倍回补库存 + 双份 store_return_in 流水。
        int rows = baseMapper.update(entity, new LambdaUpdateWrapper<StoreReturn>()
            .eq(StoreReturn::getId, bo.getId())
            .ne(StoreReturn::getReturnStatus, STATUS_RECEIVED));
        if (rows == 0) {
            log.info("[STORE-RETURN-UNIFY-001] confirm id={} 状态守卫未命中（已被并发确认），幂等跳过入库", bo.getId());
            return 0;
        }

        // 确认实收时才联动外购入库：同事务 UPSERT location_stock + stock_flow(store_return_in)，
        // inbound 内部校验库位 / 数量，失败抛 → 整体回滚（确认与入库一致，不留半态）。
        // row31：入「退货专属篮」（plot/ear/white_bar 全空），不并进地块/耳号行；再领用/发货不带追溯（客户确认符合）。
        purchaseInService.inboundReturnBasket(inboundProductId, locationId, bo.getReceivedQty(),
            FLOW_TYPE_RETURN_IN, "门店退回仓库确认入库：" + existing.getReturnNo());

        log.info("[STORE-RETURN-UNIFY-001] confirm id={} no={} location={} receivedQty={} inboundProduct={} → received 联动入库",
            bo.getId(), existing.getReturnNo(), locationId, bo.getReceivedQty(), inboundProductId);
        return rows;
    }

    @Override
    public TableDataInfo<StoreReturnStoreDailyVo> queryStoreDailyPage(StoreReturnQuery query, PageQuery pageQuery) {
        List<StoreReturnStoreDailyVo> all = buildStoreDailyList(query);
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
    public List<StoreReturnStoreDailyVo> queryStoreDailyList(StoreReturnQuery query) {
        return buildStoreDailyList(query);
    }

    /**
     * 仓库「退货记录」外层主从视图聚合（仅门店→仓库方向，不含 customer_to_store），
     * 按 退回日期(截到天)+门店 聚合，退货日期倒序、再门店倒序的稳定排序。分页 / 导出共用。
     */
    private List<StoreReturnStoreDailyVo> buildStoreDailyList(StoreReturnQuery query) {
        StoreReturnQuery q = query == null ? new StoreReturnQuery() : query;
        q.setReturnDirection(DIRECTION_STORE_TO_WAREHOUSE);
        List<StoreReturn> rows = baseMapper.selectList(buildQueryWrapper(q));
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, List<StoreReturn>> byGroup = rows.stream()
            .filter(r -> r.getReturnDate() != null && r.getStoreId() != null)
            .collect(Collectors.groupingBy(
                r -> r.getReturnDate().toLocalDate() + "|" + r.getStoreId(),
                LinkedHashMap::new, Collectors.toList()));
        if (byGroup.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> storeIds = byGroup.values().stream()
            .map(g -> g.get(0).getStoreId()).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> storeNames = storeNameMap(new ArrayList<>(storeIds));
        // row57：重量三列只算按重量计（kg）行，份数产品单独归「非重量产品退回重量」。一次查全部行产品单位避免 N+1。
        Map<Long, String> unitByProduct = productUnitMap(rows);
        List<StoreReturnStoreDailyVo> all = new ArrayList<>(byGroup.size());
        for (List<StoreReturn> group : byGroup.values()) {
            StoreReturn any = group.get(0);
            StoreReturnStoreDailyVo vo = new StoreReturnStoreDailyVo();
            vo.setReturnDate(any.getReturnDate().toLocalDate());
            vo.setStoreId(any.getStoreId());
            vo.setStoreName(storeNames.get(any.getStoreId()));
            vo.setProductKindCount((int) group.stream()
                .map(StoreReturn::getProductId).filter(Objects::nonNull).distinct().count());
            // row57：① 确认重量 = Σ kg 行 received_weight；② 退货重量 = Σ kg 行 goods_weight；
            //        ③ 重量差异 = 退货 − 确认（同为 kg 口径）；④ 非重量产品退回重量 = Σ 非 kg 行 received_weight（仓库称重）。
            BigDecimal returnTotal = BigDecimal.ZERO;
            BigDecimal confirmTotal = BigDecimal.ZERO;
            BigDecimal nonWeightReturnTotal = BigDecimal.ZERO;
            for (StoreReturn r : group) {
                if (isKgUnit(unitByProduct.get(r.getProductId()))) {
                    if (r.getGoodsWeight() != null) {
                        returnTotal = returnTotal.add(r.getGoodsWeight());
                    }
                    if (r.getReceivedWeight() != null) {
                        confirmTotal = confirmTotal.add(r.getReceivedWeight());
                    }
                } else if (r.getReceivedWeight() != null) {
                    nonWeightReturnTotal = nonWeightReturnTotal.add(r.getReceivedWeight());
                }
            }
            vo.setReturnWeightTotal(returnTotal);
            vo.setConfirmWeightTotal(confirmTotal);
            vo.setWeightDiffTotal(returnTotal.subtract(confirmTotal));
            vo.setNonWeightReturnWeightTotal(nonWeightReturnTotal);
            // row178：确认进度 n/m。确认时间 / 确认人取「最近一条已确认行」，只要 1 条确认过就有值，
            // 部分确认（3/4）与全部确认在外层看不出差别；显式给出已确认 / 总行数，未全确认前端标警告色。
            int confirmedCount = (int) group.stream()
                .filter(r -> STATUS_RECEIVED.equals(r.getReturnStatus())).count();
            vo.setConfirmedCount(confirmedCount);
            vo.setTotalCount(group.size());
            vo.setConfirmProgress(confirmedCount + "/" + group.size());
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
        return all;
    }

    /** row57：批量取退回行产品单位 map（productId → productUnit），供 kg / 非 kg 分流，避免逐行 selectById。 */
    private Map<Long, String> productUnitMap(List<StoreReturn> rows) {
        List<Long> pids = rows.stream().map(StoreReturn::getProductId)
            .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (pids.isEmpty()) {
            return Map.of();
        }
        return productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .select(ProductInfo::getId, ProductInfo::getProductUnit)
                .in(ProductInfo::getId, pids))
            .stream().collect(Collectors.toMap(ProductInfo::getId,
                p -> p.getProductUnit() == null ? "" : p.getProductUnit(), (a, b) -> a));
    }

    @Override
    public List<StoreReturnGroupVo> listPendingGroups() {
        // mp 退货管理分组卡：门店→仓库退回，按「退回日期(截到天)+门店」分组（row174）。每卡 = 一门店某天的
        // 全部待确认退回行——按门店退货日期归组，进详情时该卡明细也按当天过滤（能翻历史某天，绝不硬编码今天）。
        // 收件箱语义 —— 只列「待确认」（pending），已确认（received）不再进列表（仓库工人接受入库后即从收件箱消失）。
        // pending 不限日期全列出，含历史未确认。
        List<StoreReturn> rows = baseMapper.selectList(new LambdaQueryWrapper<StoreReturn>()
            .eq(StoreReturn::getReturnDirection, DIRECTION_STORE_TO_WAREHOUSE)
            .isNotNull(StoreReturn::getStoreId)
            .eq(StoreReturn::getReturnStatus, STATUS_PENDING));
        if (rows.isEmpty()) {
            return List.of();
        }
        // 按「退回日(天)+门店」分组，与 buildStoreDailyList 同一 key 范式（保序 LinkedHashMap）；
        // 无退回日期的脏行剔除（无法归属某天卡）。
        Map<String, List<StoreReturn>> byGroup = rows.stream()
            .filter(r -> r.getReturnDate() != null && r.getStoreId() != null)
            .collect(Collectors.groupingBy(
                r -> r.getReturnDate().toLocalDate() + "|" + r.getStoreId(),
                LinkedHashMap::new, Collectors.toList()));
        if (byGroup.isEmpty()) {
            return List.of();
        }
        Set<Long> storeIds = byGroup.values().stream()
            .map(g -> g.get(0).getStoreId()).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> storeNames = storeNameMap(new ArrayList<>(storeIds));
        List<StoreReturnGroupVo> list = new ArrayList<>(byGroup.size());
        for (List<StoreReturn> group : byGroup.values()) {
            StoreReturn any = group.get(0);
            StoreReturnGroupVo vo = new StoreReturnGroupVo();
            vo.setStoreId(any.getStoreId());
            vo.setStoreName(storeNames.get(any.getStoreId()));
            vo.setReturnDate(any.getReturnDate().toLocalDate());
            // 列表只含 pending 行，状态恒为待确认。
            vo.setReturnStatus(MP_STATUS_PENDING);
            // 品种数 / 退回时间按「当天组内」算（不跨天混算）。
            vo.setProductKindCount((int) group.stream()
                .map(StoreReturn::getProductId).filter(Objects::nonNull).distinct().count());
            vo.setReturnTime(group.stream().map(StoreReturn::getReturnDate).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null));
            list.add(vo);
        }
        // 排序：退回日倒序 → 组内最近退回时间倒序（最新退回置顶）。
        list.sort(Comparator
            .comparing((StoreReturnGroupVo v) -> v.getReturnDate() == null ? LocalDate.MIN : v.getReturnDate(),
                Comparator.reverseOrder())
            .thenComparing(v -> v.getReturnTime() == null ? LocalDateTime.MIN : v.getReturnTime(),
                Comparator.reverseOrder()));
        return list;
    }

    @Override
    public List<StoreReturnAppletItemVo> listAppletItemsByStoreAndStatus(Long storeId, String mpStatus, String returnDate) {
        if (storeId == null) {
            throw new ServiceException("门店 ID 不能为空", 400);
        }
        if (StringUtils.isBlank(mpStatus)) {
            throw new ServiceException("退回状态不能为空", 400);
        }
        // mp 词表 → store 词表：confirmed→received，其余按 pending。
        String storeStatus = MP_STATUS_CONFIRMED.equals(mpStatus) ? STATUS_RECEIVED : STATUS_PENDING;
        LambdaQueryWrapper<StoreReturn> wrapper = new LambdaQueryWrapper<StoreReturn>()
            .eq(StoreReturn::getReturnDirection, DIRECTION_STORE_TO_WAREHOUSE)
            .eq(StoreReturn::getStoreId, storeId)
            .eq(StoreReturn::getReturnStatus, storeStatus);
        // row174：分组卡携带退回日期非空 → 限定该门店该天的退回行（能翻历史某天，只看当天明细，绝不硬编码今天）；
        // 空则维持现状（不限日期，向后兼容旧入口/直接调用）。
        if (StringUtils.isNotBlank(returnDate)) {
            LocalDate day = LocalDate.parse(returnDate);
            wrapper.ge(StoreReturn::getReturnDate, day.atStartOfDay())
                .lt(StoreReturn::getReturnDate, day.plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(StoreReturn::getReturnDate);
        List<StoreReturn> rows = baseMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = rows.stream().map(StoreReturn::getProductId)
            .filter(Objects::nonNull).distinct().toList();
        Map<Long, ProductInfo> productMap = productIds.isEmpty() ? Map.of()
            : productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                    .select(ProductInfo::getId, ProductInfo::getProductName, ProductInfo::getBelongType,
                        ProductInfo::getProductUnit, ProductInfo::getProductSpec)
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
            vo.setProductUnit(p == null ? null : p.getProductUnit());
            vo.setProductSpec(p == null ? null : p.getProductSpec());
            vo.setReturnQuantity(r.getReturnQuantity());
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
        // 邓博 2026-07-16：退回入库一律记「原材料」——生产产品(成品)本身无库存概念，仓库只存原材料，
        // 打包后才成生产产品发往门店。故凡配了 product_material 的成品(果蔬份/猪肉份)一律回退到其原材料入库；
        // 产品本身即原材料(product_material 空，如白条字典的后腿肉/龙骨、外购原料)则按产品ID直接入库。
        Long material = p.getProductMaterial();
        if (material != null) {
            return material;
        }
        // 判别成品 vs 原材料的权威字段是 product_attr（djs_product_attr：1=生产产品/成品，2=原材料）。
        // 果蔬「成品」(product_attr=1) 理应配原材料——缺料阻断，防成品入库库存黑洞；
        // 果蔬本身即原材料(product_attr=2，如采摘直接入库的净菜/毛菜) product_material 天然为空，按自身 id 直接入库
        //（猪肉/白条原材料本身 product_material 也空、直接入库）。
        if (BELONG_TYPE_VEGETABLE.equals(p.getBelongType())
            && Integer.valueOf(PRODUCT_ATTR_FINISHED).equals(p.getProductAttr())) {
            throw new ServiceException(
                "果蔬成品「" + p.getProductName() + "」未配原材料(product_material)，无法退回入库；请先在产品主数据配置后再确认退回", 400);
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
     * row145.3：猪肉退货入库库位类型 → 库位 id（{@code fresh}=猪肉鲜品库 / {@code frozen}=冻品库，按库位名解析）。
     * 未匹配 / 无该库位返 null（调用方回落默认库位）。
     */
    private Long resolveReturnLocationByType(String type) {
        String name = switch (type) {
            case "fresh" -> "猪肉鲜品库";
            case "frozen" -> "冻品库";
            default -> null;
        };
        if (name == null) {
            return null;
        }
        LocationInfo loc = locationInfoMapper.selectOne(
            new LambdaQueryWrapper<LocationInfo>()
                .eq(LocationInfo::getLocationName, name)
                .last("LIMIT 1"));
        return loc == null ? null : loc.getId();
    }

    /**
     * row178：门店退回仓库的产品准入闸——礼盒（{@code belong_type=gift_box}）不收。
     *
     * <p>礼盒是多种原料的组合装，退回入库要拆回哪些原材料、各多少，单值 {@code product_material}
     * 表达不了；不拦的话工人选得到、提交得成功，直到仓库确认那步才抛 400，门店端已经以为退完了。
     * 选品接口（{@link #listPorkCandidates} / {@link #listVegCandidates}）不出礼盒是体验，
     * 这里才是把关（mp / 三方可直接 POST 任意 productId）。</p>
     *
     * @param product 退回产品（非空）
     */
    private void assertReturnableToWarehouse(ProductInfo product) {
        if (BELONG_TYPE_GIFT_BOX.equals(product.getBelongType())) {
            throw new ServiceException("礼盒「" + product.getProductName()
                + "」由多种原料组合而成，无法按单一原材料退回入库，暂不支持退回仓库", 400);
        }
    }

    /** 退货产品是否猪肉（{@code belong_type=pork}）——仅 pork 走鲜/冻库分流（白条不分流，Kevin 口径）。 */
    private boolean isPorkProduct(Long productId) {
        if (productId == null) {
            return false;
        }
        ProductInfo p = productInfoMapper.selectById(productId);
        return p != null && "pork".equals(p.getBelongType());
    }

    /**
     * 退回上限校验（row52，Kevin 2026-06-30 定重量口径）：退回重量不得超过该产品
     * <b>当日送达该店的总重量</b>（果蔬每份产品都有对应重量，按重量封顶）。
     *
     * <p>「送达该店」= 当日发货清点（{@code is_delivery_check=1}、{@code delivery_check_time} 当天）
     * 送达该店的成品 {@code product_weight} 之和；门店归属经 {@code demand_id → demand.store_id} 关联
     * （pack 链不写 production.store_id）。仅按「当日送达」，不含往日期初库存。</p>
     *
     * @param storeId      门店
     * @param productId    产品（雪花主键）
     * @param date         业务日（当日，Asia/Shanghai）
     * @param returnWeight 本次退回重量 kg（null/≤0 → 不校验）
     * @param productName  产品名（提示用）
     */
    private void validateReturnWithinDelivered(Long storeId, Long productId, LocalDate date,
                                               BigDecimal returnWeight, String productName) {
        if (returnWeight == null || returnWeight.signum() <= 0) {
            return;
        }
        BigDecimal limit = productProductionService.sumDeliveredWeightToStore(storeId, productId, date);
        if (returnWeight.compareTo(limit) > 0) {
            throw new ServiceException(
                "产品「" + productName + "」退回重量(" + returnWeight.toPlainString()
                    + ")不能超过当日送达该店的总重量(" + limit.toPlainString() + ")", 400);
        }
    }

    /** 猪肉类业态（与前端 ReturnRecordList categoryOf 同口径：pork/white_bar 归猪肉 tab，其余含空归果蔬）。 */
    private static final List<String> PORK_BELONG_TYPES = List.of("pork", "white_bar");

    private LambdaQueryWrapper<StoreReturn> buildQueryWrapper(StoreReturnQuery q) {
        LambdaQueryWrapper<StoreReturn> w = new LambdaQueryWrapper<>();
        if (q == null) {
            return w.orderByDesc(StoreReturn::getReturnDate).orderByDesc(StoreReturn::getId);
        }
        boolean hasStoreIds = q.getStoreIds() != null && !q.getStoreIds().isEmpty();
        boolean hasProductIds = q.getProductIds() != null && !q.getProductIds().isEmpty();
        // 产品名称模糊：先查产品 id 集下推（跨页正确；命中 0 个 → 恒假条件返回空页而非退化全量）
        if (StringUtils.isNotBlank(q.getProductName())) {
            List<Long> nameIds = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                    .select(ProductInfo::getId)
                    .like(ProductInfo::getProductName, q.getProductName()))
                .stream().map(ProductInfo::getId).filter(Objects::nonNull).toList();
            if (nameIds.isEmpty()) {
                w.eq(StoreReturn::getId, -1L);
            } else {
                w.in(StoreReturn::getProductId, nameIds);
            }
        }
        // 业态 tab 下推：pork=IN 猪肉产品集；vegetable=NOT IN 猪肉产品集（belong_type 空天然归果蔬）
        if (StringUtils.isNotBlank(q.getBelongCategory())) {
            List<Long> porkIds = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                    .select(ProductInfo::getId)
                    .in(ProductInfo::getBelongType, PORK_BELONG_TYPES))
                .stream().map(ProductInfo::getId).filter(Objects::nonNull).toList();
            if ("pork".equals(q.getBelongCategory())) {
                if (porkIds.isEmpty()) {
                    w.eq(StoreReturn::getId, -1L);
                } else {
                    w.in(StoreReturn::getProductId, porkIds);
                }
            } else if ("vegetable".equals(q.getBelongCategory()) && !porkIds.isEmpty()) {
                w.notIn(StoreReturn::getProductId, porkIds);
            }
        }
        w.like(StringUtils.isNotBlank(q.getReturnNo()), StoreReturn::getReturnNo, q.getReturnNo())
            .in(hasStoreIds, StoreReturn::getStoreId, q.getStoreIds())
            .eq(!hasStoreIds && q.getStoreId() != null, StoreReturn::getStoreId, q.getStoreId())
            .in(hasProductIds, StoreReturn::getProductId, q.getProductIds())
            .eq(!hasProductIds && q.getProductId() != null, StoreReturn::getProductId, q.getProductId())
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
        Map<Long, ProductInfo> products = productMap(list.stream()
            .map(StoreReturnVo::getProductId).filter(Objects::nonNull).distinct().toList());
        Map<Long, String> locationNames = locationNameMap(list.stream()
            .map(StoreReturnVo::getLocationId).filter(Objects::nonNull).distinct().toList());
        for (StoreReturnVo vo : list) {
            if (vo.getStoreId() != null) {
                vo.setStoreName(storeNames.get(vo.getStoreId()));
            }
            if (vo.getProductId() != null) {
                ProductInfo p = products.get(vo.getProductId());
                if (p != null) {
                    vo.setProductName(p.getProductName());
                    // 归属类型 + 业务码 + 规格 + 单位后端一次性回填（前端退回记录猪肉/果蔬 tab 归属靠 belongType，
                    // 不再靠前端 listProduct 分页 join —— 产品超分页容量时 join 丢失会把猪肉退回默认成果蔬 tab）。
                    vo.setBelongType(p.getBelongType());
                    vo.setProductCode(p.getProductId());
                    vo.setProductSpec(p.getProductSpec());
                    vo.setProductUnit(p.getProductUnit());
                }
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

    private Map<Long, ProductInfo> productMap(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>().in(ProductInfo::getId, productIds))
            .stream()
            .collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));
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
