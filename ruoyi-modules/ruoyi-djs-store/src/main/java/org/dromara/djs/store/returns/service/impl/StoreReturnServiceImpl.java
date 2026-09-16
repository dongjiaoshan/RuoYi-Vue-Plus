package org.dromara.djs.store.returns.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.service.UserService;
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
import org.dromara.djs.store.returns.domain.StoreReturn;
import org.dromara.djs.store.returns.domain.bo.StoreReturnBatchBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnConfirmBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnUnitBo;
import org.dromara.djs.store.returns.domain.query.StoreReturnQuery;
import org.dromara.djs.store.returns.domain.vo.StoreReturnDetailExportVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnOpsItemVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnAppletItemVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnGroupVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnPorkCandidateVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnOwnerOptionVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnStoreDailyVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnUnitCandidateVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnVegCandidateVo;
import org.dromara.djs.store.returns.mapper.StoreReturnMapper;
import org.dromara.djs.store.returns.service.IStoreReturnService;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.domain.vo.LocationPickerVo;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /**
     * 退回类型（字典 {@code djs_store_return_type}，STR-RETURN-OPS-001）：
     * {@code store}=门店退回（门店发起，待仓库处理） / {@code unit}=单位退回（admin 直录，建单即已处理）。
     */
    private static final String RETURN_TYPE_STORE = "store";
    private static final String RETURN_TYPE_UNIT = "unit";

    /**
     * 退回单位配置字典（STR-RETURN-OPS-001，甲方「单位的配置数据源从出库去向里获取具体值，
     * 配置到退回单位配置里」）：值取自 {@code djs_stock_out_dest}，由客户在 admin 字典管理增删。
     * 迁移已把出库去向拷一份作初始示例。
     */
    private static final String DICT_RETURN_UNIT = "djs_return_unit";

    /** 门店退回入库流水类型 djs_flow_type（FIX-WMS-FLOWDICT-001：门店退货走 store_return_in，与领用退回 pick_return_in 区分来源）。 */
    private static final String FLOW_TYPE_RETURN_IN = "store_return_in";

    /** 退货状态 djs_store_return_status：待仓库确认。 */
    private static final String STATUS_PENDING = "pending";

    /** 退货状态 djs_store_return_status：已入库（仓库确认实收后）。 */
    private static final String STATUS_RECEIVED = "received";

    /** 处置方式（{@code t_store_return.is_discard}）：退回入库（默认，写库存）。 */
    private static final Integer DISCARD_NO = 0;

    /** 处置方式：产品丢弃（不写库存）。 */
    private static final Integer DISCARD_YES = 1;

    /** mp 词表（djs_return_status）：待确认。store 的 pending 直接对应。 */
    private static final String MP_STATUS_PENDING = "pending";

    /** mp 词表（djs_return_status）：已确认。映射 store 的 received（mp 页用 confirmed，避免改 mp UI）。 */
    private static final String MP_STATUS_CONFIRMED = "confirmed";

    /**
     * 白条产品退回字典（DENGBO-R11，dict_value=产品业务码 product_id）。门店退回操作「白条产品」来源。
     * 空字典客户在 admin 字典管理自配；单位取对应产品原材料单位、按重量退货。
     */
    private static final String DICT_WHITE_BAR_RETURN_PRODUCT = "djs_white_bar_return_product";

    /**
     * 退回产品清单字典（V6-R214，dict_value=产品业务码 product_id）——门店退回操作三个 tab 候选的**唯一**来源。
     *
     * <p>甲方 2026-09-13 row214：候选不再由门店当日盘点台账推导，改由本清单配置，按产品自身
     * {@code belong_type} 分流到猪肉 / 果蔬 / 其他三个 tab，且清单内产品的退回量**不封顶**。
     * 清单外产品仍然一律拒绝退回（唯一保留的闸，见 {@link #assertInReturnProductList}）。</p>
     */
    private static final String DICT_RETURN_PRODUCT_LIST = "djs_return_product_list";

    /** 退回操作猪肉 tab 产品子类（DENGBO-R11）：pork=猪肉产品(到店成品,按份) / white_bar=白条产品(字典,按重量)。 */
    private static final String SUB_CAT_PORK = "pork";
    private static final String SUB_CAT_WHITE_BAR = "white_bar";

    /**
     * 退回操作页的三个 tab（V6-R214）。分流只看产品自身 {@code belong_type}：
     * pork / white_bar → 猪肉产品；vegetable → 果蔬产品；**其余一律** → 其他产品
     * （甲方原话「其他的类型统一显示在其他产品里」，故其他 tab 是兜底而非白名单，
     * belong_type 为空的外购产品也落这里）。
     */
    private static final String RETURN_TAB_PORK = "pork";
    private static final String RETURN_TAB_VEG = "veg";
    private static final String RETURN_TAB_OTHER = "other";

    /** 果蔬归属类型（字典 djs_belong_type）：退回入库回退到原材料 product_material 的判定。 */
    private static final String BELONG_TYPE_VEGETABLE = "vegetable";

    /** 产品属性（字典 djs_product_attr）：1=生产产品/成品，2=原材料。只有果蔬「成品」缺料才阻断退回入库。 */
    private static final int PRODUCT_ATTR_FINISHED = 1;

    /** 白条归属类型（字典 djs_belong_type）：门店当日白条到店判定。 */
    private static final String BELONG_TYPE_WHITE_BAR = "white_bar";

    /** 猪肉归属类型（字典 djs_belong_type）。 */
    private static final String BELONG_TYPE_PORK = "pork";

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

    /** 库位启用态（字典 {@code djs_common_status}：1=启用 / 2=停用）。 */
    private static final Integer LOCATION_STATUS_ENABLED = 1;

    /**
     * 猪肉产品入库的两个专用库位名（row145.3 / STR-RETURN-OPS-001）。
     *
     * <p>两库 {@code location_type} 在 v3 reseed 后都是 {@code warehouse}，靠类型过滤挑不出来，
     * 只能按库位名精确匹配（与 {@code PigBurnRecordServiceImpl.queryPorkOptionLocations} 同一套）。</p>
     */
    private static final String PORK_FRESH_LOCATION_NAME = "猪肉鲜品库";
    private static final String FROZEN_LOCATION_NAME = "冻品库";

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
    /** row178：导出「确认人」用（FastExcel 不走 Jackson，@Translation 不生效，只能 service 预填）。 */
    private final UserService userService;

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
                                  UserService userService) {
        super(baseMapper);
        this.userService = userService;
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
        // 3. row178：拦礼盒。本方法对所有方向都无条件走下面的 inboundReturnBasket 真写仓库库存，
        //    所以闸也不按方向区分（顾客退门店同样会写 location_stock）。
        assertReturnable(product);
        String direction = StringUtils.isBlank(bo.getReturnDirection())
            ? DIRECTION_CUSTOMER_TO_STORE : bo.getReturnDirection();
        // 4. **门店退仓库方向必须过两道闸**（成员资格 + 到店量封顶），与 batchCreate 同源同口径。
        //    这条路（admin 单条新增 POST /djs/store/return）直接 received + 立刻真写 location_stock，
        //    比 batchCreate 的两段式更危险：没有仓库二次确认这一关。此前完全无闸，实测能从一家从没收过
        //    生菜的门店登记 55555kg 生菜并即时进仓库库存。
        //    ⚠️ 两道闸必须成对出现：row221 把成员资格从「只认清单」放宽到「清单 ∪ 当日到店」之后，
        //    只补第一道会让当日到过店的产品在这条路上**无上限**退（batch 路拒 99999，这条路照收），
        //    等于把刚堵住的洞在旁边重新开一个。
        //    顾客退门店（customer_to_store）不套此闸 —— 顾客退的是以前买的货，本就不该受当日到店量约束。
        if (DIRECTION_STORE_TO_WAREHOUSE.equals(direction) && bo.getStoreId() != null) {
            assertInReturnProductList(product, bo.getStoreId());
            assertWithinArrivedQuantity(bo.getStoreId(), product, bo.getReturnQuantity(),
                new HashSet<>(returnListAllowedIds()));
        }

        StoreReturn entity = new StoreReturn();
        entity.setReturnNo(generateReturnNo());
        entity.setReturnDirection(direction);
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
            FLOW_TYPE_RETURN_IN, returnInboundRemark("门店退回入库", entity.getReturnNo(), product));

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
        // row178：改方向也要过一遍礼盒闸，否则可以先按别的方向建、再 PUT 把方向改成门店退仓库绕过去
        // （productId 建后锁死不可改，取存量行的产品判定）。
        if (StringUtils.isNotBlank(bo.getReturnDirection()) && existing.getProductId() != null) {
            ProductInfo product = productInfoMapper.selectById(existing.getProductId());
            if (product != null) {
                assertReturnable(product);
            }
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
        // 甲方 2026-09-13 row214：候选改由字典「退回产品清单」配置，退回量**不再封顶**。
        // 唯一保留的闸 = 成员资格：不在清单里的产品一律拒绝（见 assertInReturnProductList）。
        // 允许集整批算一次 —— admin「退回操作」是整表一次提交，放循环里就是 N 次重复的字典 + IN 查询。
        Set<Long> allowedIds = returnAllowedIds(bo.getStoreId());
        // row221：当日到店的生产产品按到店量封顶（清单产品不封顶，见 D-0055）。整批算一次，
        // 放循环里就是每行一次到店聚合查询。
        Set<Long> returnListIds = returnListAllowedIds();
        int created = 0;
        for (StoreReturnBatchBo.Item item : bo.getItems()) {
            ProductInfo product = productInfoMapper.selectById(item.getProductId());
            if (product == null) {
                throw new ServiceException("产品不存在或已删除：" + item.getProductId(), 404);
            }
            assertReturnable(product);
            // 退回量度量：果蔬行用退回量（份/把/盒），猪肉行无退回量时回退退回重量（kg）——与下方落库口径一致。
            BigDecimal returnMetric = item.getReturnQuantity() != null
                ? item.getReturnQuantity() : item.getReturnWeight();
            BigDecimal rw = item.getReturnWeight() == null ? BigDecimal.ZERO : item.getReturnWeight();
            // row214：清单成员资格闸（唯一一道）。放弃的是「退回量不超过当日盘点账面可退量」那道封顶
            // （甲方 row205 / D-0004 口径）——甲方 2026-09-13 明确「对于其退回量不做限制」，
            // 且候选已不再来自台账、没有账面基数可比。成员资格必须留着：它挡的是
            // 「任意产品凭空退成仓库库存」（实测曾从没收过生菜的门店登记 55555kg 生菜并即时进库）。
            assertInReturnProductList(product, allowedIds);
            assertWithinArrivedQuantity(bo.getStoreId(), product, returnMetric, returnListIds);

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
     * 门店退仓库唯一的提交闸（V6-R214）：产品必须配在字典「退回产品清单」里。
     *
     * <p><b>退回量本身不封顶</b>——甲方 2026-09-13 row214「对于其退回量不做限制」。原先那道
     * 「不超过当日盘点账面可退量（期初+入库−销售−赠送−已退）」的封顶（甲方 row205 / D-0004）
     * 随候选口径一起作废：候选已改由本清单配置、不再来自台账，没有账面基数可比。</p>
     *
     * <p><b>成员资格这一道必须留着。</b>它挡的是「凭空给仓库造库存」：实测过从一家从没收过生菜的门店
     * 退 55555 kg 生菜，提交 200、确认后仓库真多出 1000 kg 库存（mp「退回录入」页用的是通用
     * ProductPicker，仓库工人在正常界面里就能选到任意产品）。清单把可退产品收敛成一份人工配置的白名单，
     * 数量放开但产品范围没放开。</p>
     */
    private void assertInReturnProductList(ProductInfo product, Long storeId) {
        assertInReturnProductList(product, returnAllowedIds(storeId));
    }

    /** 批量提交用：允许 id 集由调用方算一次传进来，避免每行重查一次字典 + 一次 IN 查询。 */
    private void assertInReturnProductList(ProductInfo product, Set<Long> allowedIds) {
        if (allowedIds.contains(product.getId())) {
            return;
        }
        // 被 V6 row226 剔掉的要给专属文案。用通用那句会说成「当日也没有到店记录」——
        // 而它今天**确实**到店了（mp「退回录入」用的是无过滤的通用 ProductPicker，工人选得到它），
        // 照那句提示去查发货记录只会查出相反的结论，然后来提二次工单。
        // 判「这个产品在不在集合里」，不是「集合非空」—— 查一个 id 却按「有没有返回」下结论，
        // 一旦上游把别的产品也带进结果（测试桩里就发生了），会把无辜产品报成「勾了原材料售卖」。
        if (porkMaterialSoldIds(List.of(product.getId())).contains(product.getId())) {
            throw new ServiceException("产品「" + product.getProductName() + "」勾了「原材料售卖」，"
                + "按原材料计价出售，退回请改登记它的原材料" + materialNameHint(product)
                + "；这类猪肉产品不在退回操作里显示。", 400);
        }
        throw new ServiceException("产品「" + product.getProductName()
            + "」既不在「退回产品清单」里、当日也没有到店记录，无法退回。"
            + "请先在 admin 字典管理 → 退回产品清单里配置它的产品编码，或确认该产品今天确实发到了这家门店。", 400);
    }

    /** 报错里带上原材料名（查得到才带）：只说「改登记原材料」而不说是哪个，工人还得自己猜。 */
    private String materialNameHint(ProductInfo product) {
        if (product.getProductMaterial() == null) {
            return "";
        }
        ProductInfo material = productInfoMapper.selectById(product.getProductMaterial());
        return material == null || StringUtils.isBlank(material.getProductName())
            ? "" : "「" + material.getProductName() + "」";
    }

    /**
     * 当日到店生产产品的退回量封顶（V6 row221 / D-0069 fallback）：{@code 退回量 ≤ 当日到店量 − 今日已退}。
     *
     * <p><b>只作用于「不在退回产品清单里」的那一半候选</b>。清单产品甲方 row214 明确
     * 「对于其退回量不做限制」（D-0055），在这里被直接跳过 —— 两半各按各的规则，
     * 这不是两边兼容：候选本来就是两个来源的并集，来源决定规则。</p>
     *
     * <p>已退量与到店量必须同窗口（都取今天）：到店量按 {@code delivery_check_time} 当天聚合，
     * 已退量若算进昨天，同一批货今天就能被多退一轮。</p>
     *
     * <p>{@code storeId} 为空时不设限 —— 没有门店就没有「到店」可言（单位退回走的是另一条路，
     * 根本不会走到这里）。</p>
     */
    private void assertWithinArrivedQuantity(Long storeId, ProductInfo product,
                                             BigDecimal returnMetric, Set<Long> returnListIds) {
        if (storeId == null || returnListIds.contains(product.getId())) {
            return;
        }
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        BigDecimal arrived = arrivedQuantityOf(storeId, product, today);
        BigDecimal returned = sumReturnedQuantityTodayForProduct(storeId, today, product.getId());
        BigDecimal limit = arrived.subtract(returned == null ? BigDecimal.ZERO : returned);
        BigDecimal qty = returnMetric == null ? BigDecimal.ZERO : returnMetric;
        if (qty.compareTo(limit) > 0) {
            if (limit.signum() <= 0) {
                throw new ServiceException("产品「" + product.getProductName()
                    + "」当日到店量已被今天的退回抵完，不能再退。若要不受到店量限制，请把它配进「退回产品清单」。", 400);
            }
            throw new ServiceException("产品「" + product.getProductName() + "」退回量("
                + qty.toPlainString() + ")不能超过当日到店量减今日已退(" + limit.toPlainString() + ")", 400);
        }
    }

    /**
     * 提交闸允许的产品 id 集（V6 row221）= 清单允许集 ∪ <b>该门店当日到店的生产产品</b>。
     *
     * <p>候选列出了什么，闸就必须放行什么 —— 否则当日到店的生产产品在页面上点得到、一提交必 400。
     * 与候选侧 {@link #listReturnCandidates} 是同一条规则的两半，不是两套口径。</p>
     *
     * <p>{@code storeId} 为空（如单位退回，本就没有门店）时退化成纯清单允许集：
     * 「当日到店」这个概念对没有门店的单据不成立，兜底放行反而会把闸开成一个洞。</p>
     */
    private Set<Long> returnAllowedIds(Long storeId) {
        Set<Long> allowed = new LinkedHashSet<>(returnListAllowedIds());
        if (storeId == null) {
            return allowed;
        }
        for (String tab : List.of(RETURN_TAB_PORK, RETURN_TAB_VEG, RETURN_TAB_OTHER)) {
            for (StoreReturnVegCandidateVo c : listArrivedProductionCandidates(storeId, tab)) {
                if (c.getProductId() != null) {
                    allowed.add(c.getProductId());
                }
            }
        }
        // 果蔬候选会走材料外售折叠（成品行整行换成原材料，id 都换了），提交上来的可能是原材料 id，
        // 所以闸也得认那个原材料。
        // ⚠️ 门槛必须与 foldVegMaterialSold 逐字对齐 —— 它**只折叠果蔬 tab**。
        // 不加 belong_type 判据的话，猪肉的「材料外售」产品（实测「通排」attr=1/is_material_sold=1）
        // 会把它的原材料也放进允许集，而那个原材料从不出现在任何候选里：成了一个页面上看不见、
        // 却提得动的入口（今天只被到店量封顶兜住，哪天它自己到过店就真开了）。
        productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .select(ProductInfo::getId, ProductInfo::getIsMaterialSold,
                    ProductInfo::getProductMaterial, ProductInfo::getBelongType)
                .in(ProductInfo::getId, new ArrayList<>(allowed)))
            .stream()
            .filter(p -> BELONG_TYPE_VEGETABLE.equals(p.getBelongType()))
            .filter(p -> Objects.equals(1, p.getIsMaterialSold()) && p.getProductMaterial() != null)
            .forEach(p -> allowed.add(p.getProductMaterial()));
        // V6 row226：候选侧剔掉的「原材料售卖」猪肉生产产品，闸这边也必须拒 ——
        // 列不出来却提得动，就是一个页面上看不见的入口（row221 刚栽过一次同样的跟头）。
        allowed.removeAll(porkMaterialSoldIds(new ArrayList<>(allowed)));
        return allowed;
    }

    /**
     * 闸允许的产品 id 集 = 清单产品**本身** ∪ 它们材料外售折叠后的**原材料**。
     *
     * <p>两份都要，因为提交上来的 id 未必等于字典里配的那个：果蔬候选会过
     * {@link #foldVegMaterialSold}，把「材料外售」的成品行整行换成它的原材料（id/名/单位都换）。
     * 客户在字典里配的自然是他在产品列表里看到的**成品**（如「有机上海青250g」），
     * 折叠后前端拿到的却是**原材料**（「上海青」）的 id —— 只比字典本身的话，
     * 候选里点得到、一提交必 400，正是换口径前那段注释警告过的坑。</p>
     *
     * <p>这不是「两边兼容」：规则只有一条 —— <b>能退的 = 清单里那些产品（折叠后是谁就是谁）</b>。
     * 折叠是候选侧既有的既定行为，闸跟着它走才是同一套口径。</p>
     */
    private Set<Long> returnListAllowedIds() {
        List<Long> configured = resolveReturnListProductIds();
        if (configured.isEmpty()) {
            return Set.of();
        }
        Set<Long> allowed = new LinkedHashSet<>(configured);
        // ⚠️ 门槛必须与 foldVegMaterialSold 对齐 —— 它**只折叠果蔬**。不加 belong_type 判据的话，
        // 猪肉的材料外售产品（「通排」）会把它的原材料 id 也放进允许集，而那个原材料从不出现在任何候选里：
        // 实测字典里配上「通排」后，直接提它的原材料 9303000000000107 退 99999 会 200 建单成功 ——
        // 一个页面上看不见、却提得动且不封顶的入口。V6 row226 把通排本体藏起来之后，这条后门就成了唯一通路。
        productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .select(ProductInfo::getId, ProductInfo::getIsMaterialSold,
                    ProductInfo::getProductMaterial, ProductInfo::getBelongType)
                .in(ProductInfo::getId, configured))
            .stream()
            .filter(p -> BELONG_TYPE_VEGETABLE.equals(p.getBelongType()))
            .filter(p -> Objects.equals(1, p.getIsMaterialSold()) && p.getProductMaterial() != null)
            .forEach(p -> allowed.add(p.getProductMaterial()));
        // V6 row226：勾了「原材料售卖」的猪肉生产产品即便被配进清单也不许退（候选侧同样剔除）。
        // 放在这里而不是只放 returnAllowedIds：单位退回走的是本方法，不经过 returnAllowedIds。
        allowed.removeAll(porkMaterialSoldIds(new ArrayList<>(allowed)));
        return allowed;
    }

    @Override
    public List<StoreReturnPorkCandidateVo> listPorkCandidates(Long storeId) {
        if (storeId == null) {
            return List.of();
        }
        // V6-R214：候选取自字典「退回产品清单」，按产品 belong_type 分流（pork / white_bar → 本 tab）。
        // 白条按 Kevin 口径继续留在猪肉 tab，只用 subCategory 区分展示。
        List<StoreReturnPorkCandidateVo> result = new ArrayList<>();
        // 仍回填今日已退量：不再用来封顶，但要让录入页显示「今天已经退过多少」。
        for (StoreReturnVegCandidateVo c : fillReturnedQuantity(storeId,
                listReturnCandidates(RETURN_TAB_PORK, storeId))) {
            StoreReturnPorkCandidateVo vo = new StoreReturnPorkCandidateVo();
            vo.setProductId(c.getProductId());
            vo.setProductName(c.getProductName());
            vo.setProductUnit(c.getProductUnit());
            vo.setBelongType(c.getBelongType());
            vo.setInReturnList(c.getInReturnList());
            vo.setSubCategory(BELONG_TYPE_WHITE_BAR.equals(c.getBelongType()) ? SUB_CAT_WHITE_BAR : SUB_CAT_PORK);
            vo.setArrivedQuantity(c.getArrivedQuantity());
            vo.setReturnedQuantity(c.getReturnedQuantity());
            result.add(vo);
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
     * 「仓库实收量」的计量口径单位（甲方 row13/row14/row15 统一模型，全域唯一判据）。
     *
     * <p>= 产品原材料（{@code product_material}）的单位；<b>无原材料 / 原材料无单位时回落产品自身单位</b>。
     * 该单位是 kg → {@code received_weight} 落的是<b>重量</b>（kg，3 位小数）；不是 kg → 落的是
     * <b>件数</b>（按产品单位，整数）。三端（admin 明细 / 门店退回记录 / mp 确认页）与统计口径全按它分流。</p>
     *
     * @param products 已加载的产品（须含 {@code productMaterial} 与 {@code productUnit} 两列）
     * @return productId → 计量口径单位（每个入参产品都有值，除非产品自身单位也为空）
     */
    private Map<Long, String> resolveMetricUnits(List<ProductInfo> products) {
        Map<Long, String> materialUnits = resolveMaterialUnits(products);
        Map<Long, String> result = new LinkedHashMap<>();
        for (ProductInfo p : products) {
            String unit = materialUnits.get(p.getId());
            result.put(p.getId(), StringUtils.isBlank(unit) ? p.getProductUnit() : unit);
        }
        return result;
    }

    /** 单个产品的计量口径单位（{@link #resolveMetricUnits} 的单条版，confirm 逐行校验用）。 */
    private String resolveMetricUnit(ProductInfo product) {
        return resolveMetricUnits(List.of(product)).get(product.getId());
    }

    /**
     * 计量规则（甲方 row24）：一件该生产产品折算多少原材料（{@code material_num}，如 30 枚装礼盒 = 30）。
     *
     * <p>未配 / ≤0 一律回落 <b>1</b>：产品本身即原材料时（鸡蛋按枚、白条部位按 kg）本来就是 1:1，
     * 回落 0 会把上限算成 0、把整单卡死。</p>
     */
    private static BigDecimal materialRatio(ProductInfo p) {
        BigDecimal n = p == null ? null : p.getMaterialNum();
        return n == null || n.signum() <= 0 ? BigDecimal.ONE : n;
    }

    /**
     * 件数口径下的仓库实收上限（甲方 row24）：{@code 退回量 × 计量规则}。
     * 退回量为空 → {@code null}（不封顶，避免历史脏数据把合法确认拦死）。
     */
    private static BigDecimal maxConfirmQty(BigDecimal returnQuantity, ProductInfo p) {
        if (returnQuantity == null || returnQuantity.signum() <= 0) {
            return null;
        }
        // 至少 1：material_num 配成小数（如 0.25）时 setScale(0) 会把上限抹成 0，
        // 那一行任何正数实收都会被拒、整单永久卡死。宁可放宽到 1，也不制造死锁。
        return returnQuantity.multiply(materialRatio(p))
            .setScale(0, RoundingMode.HALF_UP).max(BigDecimal.ONE);
    }

    /**
     * 该产品能否「退回入库」（甲方 row24 第 2 点）。
     *
     * <p>不能的唯一情形：**生产产品（{@code product_attr=1}）却没配原材料**。仓库只存原材料，
     * 成品缺料就无从确定往哪个原材料头上记库存，只能丢弃。
     * 产品本身即原材料（{@code product_attr=2}，如白条部位 / 鸡蛋 / 外购原料）{@code product_material}
     * 天然为空，按自身 id 入库，照常放行 —— 别把这一大票正常退回也拦了。</p>
     */
    private static boolean canInbound(ProductInfo p) {
        if (p == null) {
            return false;
        }
        return p.getProductMaterial() != null
            || !Integer.valueOf(PRODUCT_ATTR_FINISHED).equals(p.getProductAttr());
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

    /**
     * 字典「退回产品清单」驱动的退回候选（V6-R214，三个 tab 共用一套口径）。
     *
     * <p>候选 = 字典 {@link #DICT_RETURN_PRODUCT_LIST} 配置的产品编码 resolve 出来的产品，
     * 按产品自身 {@code belong_type} 分流（{@link #returnTabOf}）。
     * <b>{@code arrivedQuantity} 恒为 null = 不封顶</b>——甲方 row214「对于其退回量不做限制」，
     * 前端 {@code :max} 据此不封顶，后端提交闸只查成员资格（{@link #assertInReturnProductList}）。</p>
     *
     * <p><b>为什么从台账换成字典</b>：原口径（当日盘点台账 期初+入库−销售−赠送 &gt; 0）要求门店先盘点、
     * 且账面还有余量，候选每天都在变；甲方 2026-09-13 改成一份人工维护的固定清单，
     * 退回操作因此不再依赖门店盘点。项目铁律禁止「两边兼容」，台账那条取数链整条删除、不保留分支。</p>
     *
     * <p>放弃的是「退回量不超过账面可退量」这道封顶（甲方 row205 / D-0004）。保留的是产品白名单：
     * 清单外产品一律拒绝，凭空造仓库库存这条路仍然是堵死的。</p>
     *
     * @param tab {@link #RETURN_TAB_PORK} / {@link #RETURN_TAB_VEG} / {@link #RETURN_TAB_OTHER}
     * @return 候选（productId / name / unit / belongType；arrivedQuantity 恒 null）
     */
    private List<StoreReturnVegCandidateVo> listReturnListCandidates(String tab) {
        List<Long> ids = resolveReturnListProductIds();
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<ProductInfo> products = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
            .in(ProductInfo::getId, ids).orderByAsc(ProductInfo::getId));
        List<StoreReturnVegCandidateVo> result = new ArrayList<>();
        for (ProductInfo p : products) {
            if (!tab.equals(returnTabOf(p.getBelongType()))) {
                continue;
            }
            // 礼盒即便被配进清单也退不进仓库（多种原料组合，拆不回单一原材料，assertReturnable 硬拒）。
            // 列出来就是让工人白填一遍再吃 400，故候选侧直接剔掉 —— 与提交闸同一条规则的两半，不是两套口径。
            if (BELONG_TYPE_GIFT_BOX.equals(p.getBelongType())) {
                log.info("[STORE-RETURN] 退回候选剔除礼盒 productId={} name={}", p.getId(), p.getProductName());
                continue;
            }
            StoreReturnVegCandidateVo vo = new StoreReturnVegCandidateVo();
            vo.setProductId(p.getId());
            vo.setProductName(p.getProductName());
            vo.setProductUnit(p.getProductUnit());
            vo.setBelongType(p.getBelongType());
            vo.setInReturnList(Boolean.TRUE);
            // arrivedQuantity 留 null：前端据此不封顶（甲方 row214「退回量不做限制」）。
            // returnedQuantity 不在这里填 —— 果蔬要先过材料外售折叠（会改写 productId），
            // 统一由 fillReturnedQuantity 按最终 id 回填。
            result.add(vo);
        }
        return result;
    }

    /**
     * 退回候选 = 「退回产品清单」字典产品 ∪ <b>当日到店的生产产品</b>（V6 row221）。
     *
     * <p>row214 只说了「猪肉退回不再取字典项【白条产品退回项】，而是取【退回产品清单】的数据」，
     * 点名替换的是白条那一本字典；实现却把整个候选源换成了清单，把当日到店的生产产品一并挤掉。
     * 甲方 row221 把话说清楚了：「只是不再显示额外的原材料的产品，现在显示的内容是
     * 退回清单的产品 + 当日到店的生产产品」。</p>
     *
     * <p><b>重叠时清单赢</b>：同一个产品既配在清单里又当日到店，按清单那一份保留
     * （{@code arrivedQuantity} 为 null = 不封顶，D-0055 / row214「对于其退回量不做限制」）。
     * 反过来让到店那份覆盖，等于把甲方刚放开的限制又装回去。</p>
     *
     * @param tab     {@link #RETURN_TAB_PORK} / {@link #RETURN_TAB_VEG} / {@link #RETURN_TAB_OTHER}
     * @param storeId 门店（到店量按 门店 + 当日 聚合）
     */
    private List<StoreReturnVegCandidateVo> listReturnCandidates(String tab, Long storeId) {
        List<StoreReturnVegCandidateVo> merged = new ArrayList<>(listReturnListCandidates(tab));
        Set<Long> seen = merged.stream().map(StoreReturnVegCandidateVo::getProductId)
            .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        for (StoreReturnVegCandidateVo c : listArrivedProductionCandidates(storeId, tab)) {
            if (c.getProductId() != null && seen.add(c.getProductId())) {
                merged.add(c);
            }
        }
        return dropPorkMaterialSold(merged, tab);
    }

    /**
     * 猪肉页签剔除「勾了原材料售卖」的生产产品（V6 row226）。
     *
     * <p>甲方原话：「对于猪肉类的生产产品在退回时，如果产品是勾选了原材料售卖的选项，则不显示在退回操作里」。
     * 这类产品按原材料计价出售，退回该落到它的<b>原材料</b>头上；果蔬侧有
     * {@link #foldVegMaterialSold} 把成品行整行换成原材料，猪肉侧没有这道折叠，
     * 列出来就是两行都叫「通排」——工人分不清退的是成品还是原材料（实测全库
     * {@code is_material_sold=1} 的产品只有它一个：Y0322 pork/attr=1，原材料指向同名的 9303000000000107）。</p>
     *
     * <p><b>只作用于猪肉页签</b>，这一点是甲方的原话、也是必须的：果蔬的同类产品会被折叠成原材料后留下，
     * 在这里一起剔会把折叠后的那一行也带走，果蔬候选凭空少一项。</p>
     *
     * <p><b>只剔生产产品（{@code product_attr=1}，D-0042）</b>：产品本身就是原材料时
     * （{@code attr=2}，如白条部位）{@code is_material_sold} 没有「成品 vs 原材料」的歧义可言，
     * 它就是那个原材料，照常可退。</p>
     */
    private List<StoreReturnVegCandidateVo> dropPorkMaterialSold(List<StoreReturnVegCandidateVo> candidates,
                                                                 String tab) {
        if (!RETURN_TAB_PORK.equals(tab) || candidates.isEmpty()) {
            return candidates;
        }
        List<Long> ids = candidates.stream().map(StoreReturnVegCandidateVo::getProductId)
            .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return candidates;
        }
        Set<Long> excluded = porkMaterialSoldIds(ids);
        if (excluded.isEmpty()) {
            return candidates;
        }
        List<StoreReturnVegCandidateVo> kept = new ArrayList<>(candidates.size());
        for (StoreReturnVegCandidateVo c : candidates) {
            if (excluded.contains(c.getProductId())) {
                log.info("[STORE-RETURN] 退回候选剔除「原材料售卖」的猪肉生产产品 productId={} name={}（V6 row226）",
                    c.getProductId(), c.getProductName());
                continue;
            }
            kept.add(c);
        }
        return kept;
    }

    /**
     * 给定产品 id 里，属于「猪肉生产产品 + 勾了原材料售卖」的那些（V6 row226 的唯一判据实现）。
     *
     * <p>候选侧剔除与提交闸拒绝共用本方法 —— 候选列不出来、闸却放行，等于留一个页面上看不见
     * 却提得动的入口；反过来候选列得出、闸拒绝，工人白填一遍再吃 400。两边必须同源。</p>
     */
    private Set<Long> porkMaterialSoldIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Set.of();
        }
        return productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .select(ProductInfo::getId, ProductInfo::getBelongType,
                    ProductInfo::getProductAttr, ProductInfo::getIsMaterialSold)
                .in(ProductInfo::getId, productIds))
            .stream()
            .filter(p -> RETURN_TAB_PORK.equals(returnTabOf(p.getBelongType())))
            .filter(p -> Integer.valueOf(PRODUCT_ATTR_FINISHED).equals(p.getProductAttr()))
            .filter(StoreReturnServiceImpl::isMaterialSold)
            .map(ProductInfo::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 当日到店的<b>生产产品</b>候选（V6 row221，口径见 D-0069）。
     *
     * <p>「生产产品」判据 = {@code product_attr=1}（D-0042 的 fallback，与入库侧对仗）；
     * 「当日到店」判据 = {@code is_delivery_check=1} 且 {@code delivery_check_time} 是今天、
     * 门店经 {@code demand_id → demand.store_id} 关联（与到店量聚合同源，见
     * {@code ProductProductionMapper#selectDeliveredProductIdsToStore}）。</p>
     *
     * <p>{@code arrivedQuantity} 按产品单位分流回填（{@link #arrivedQuantityOf}），前端据此封顶 ——
     * 这正是甲方说的「生产产品的退回逻辑和历史逻辑保持一致」：到店多少才能退多少，
     * 不依赖门店当天有没有盘点。清单产品那一半仍然不封顶，两半各按各的规则，不是两边兼容。</p>
     */
    private List<StoreReturnVegCandidateVo> listArrivedProductionCandidates(Long storeId, String tab) {
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        List<Long> deliveredIds = productProductionMapper.selectDeliveredProductIdsToStore(storeId, today);
        if (deliveredIds == null || deliveredIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<ProductInfo> products = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
            .in(ProductInfo::getId, deliveredIds)
            .eq(ProductInfo::getProductAttr, PRODUCT_ATTR_FINISHED)
            .orderByAsc(ProductInfo::getId));
        List<StoreReturnVegCandidateVo> result = new ArrayList<>();
        for (ProductInfo p : products) {
            if (!tab.equals(returnTabOf(p.getBelongType()))) {
                continue;
            }
            // 礼盒退不进仓库（多种原料组合拆不回单一原材料，assertReturnable 硬拒），
            // 与清单候选同一条剔除规则 —— 列出来只会让工人白填一遍再吃 400。
            if (BELONG_TYPE_GIFT_BOX.equals(p.getBelongType())) {
                continue;
            }
            StoreReturnVegCandidateVo vo = new StoreReturnVegCandidateVo();
            vo.setProductId(p.getId());
            vo.setProductName(p.getProductName());
            vo.setProductUnit(p.getProductUnit());
            vo.setBelongType(p.getBelongType());
            vo.setInReturnList(Boolean.FALSE);
            vo.setArrivedQuantity(arrivedQuantityOf(storeId, p, today));
            // returnedQuantity 交给 fillReturnedQuantity 按最终 id 统一回填（果蔬还要先过材料外售折叠）。
            result.add(vo);
        }
        return result;
    }

    /**
     * 该产品当日到店量，按<b>产品自身单位</b>分流（与退回量录入的计量口径对齐）。
     *
     * <p>kg 类取到店<b>重量</b>（{@code SUM(product_weight)}）；计数类（份 / 盒 / 把…）取到店
     * <b>需求订购份数</b>（{@code SUM(demand_quantity)}，Kevin 2026-07-12 口径）。
     * 两者不能互换：份数产品每份一条 production 行、重量常年为 0，拿重量当上限会把整类产品钉死在 0。</p>
     */
    private BigDecimal arrivedQuantityOf(Long storeId, ProductInfo p, LocalDate date) {
        BigDecimal v = isKgUnit(p.getProductUnit())
            ? productProductionService.sumDeliveredWeightToStore(storeId, p.getId(), date)
            : productProductionMapper.sumDeliveredQuantityToStore(storeId, p.getId(), date);
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 产品归属类型 → 退回操作 tab。其他 tab 是**兜底**（甲方原话「其他的类型统一显示在其他产品里」），
     * 所以 {@code belongType} 为 null 的外购产品也落到其他，不会被静默丢掉。
     */
    private static String returnTabOf(String belongType) {
        if (BELONG_TYPE_PORK.equals(belongType) || BELONG_TYPE_WHITE_BAR.equals(belongType)) {
            return RETURN_TAB_PORK;
        }
        if (BELONG_TYPE_VEGETABLE.equals(belongType)) {
            return RETURN_TAB_VEG;
        }
        return RETURN_TAB_OTHER;
    }

    /**
     * 退回产品清单的产品雪花 id（读字典 {@link #DICT_RETURN_PRODUCT_LIST} 的 dict_value = 产品业务码）。
     * 空字典 → 空清单：三个 tab 都空、任何退回提交都会被成员资格闸拒绝（由客户在字典管理配置）。
     */
    private List<Long> resolveReturnListProductIds() {
        Map<String, String> dict = dictService.getAllDictByDictType(DICT_RETURN_PRODUCT_LIST);
        if (dict == null || dict.isEmpty()) {
            log.warn("[STORE-RETURN] 字典 {} 为空，退回候选为空（待客户在 admin 字典管理配置）", DICT_RETURN_PRODUCT_LIST);
            return List.of();
        }
        List<String> codes = dict.keySet().stream()
            .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
        if (codes.isEmpty()) {
            return List.of();
        }
        List<ProductInfo> found = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
            .in(ProductInfo::getProductId, codes).select(ProductInfo::getId, ProductInfo::getProductId));
        // 配错一个字符的编码会静默消失在所有 tab 里、客户查不出原因 —— 至少让它在日志里留痕。
        if (found.size() < codes.size()) {
            Set<String> resolved = found.stream().map(ProductInfo::getProductId).collect(Collectors.toSet());
            List<String> missing = codes.stream().filter(c -> !resolved.contains(c)).collect(Collectors.toList());
            log.warn("[STORE-RETURN] 字典 {} 里有 {} 个产品编码在产品主数据里找不到，这些行不会出现在任何 tab：{}",
                DICT_RETURN_PRODUCT_LIST, missing.size(), missing);
        }
        return found.stream().map(ProductInfo::getId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    /**
     * 按候选的<b>最终</b>产品 id 回填「今日已退量」—— 必须在所有会改写 productId 的加工
     * （{@link #foldVegMaterialSold} 的材料外售折叠）<b>之后</b>调用。
     *
     * <p>已退窗口必须与可退量的窗口**同宽**，否则一边宽一边窄会算错剩余。可退量取的是
     * 「今天这一张台账」，而台账的 {@code opening_qty} 是上一次盘点的期末结转过来的
     * ——昨天退掉的货已经体现在今天的期初里了，这里再扣一次昨天的退回量就是重复扣。故已退量只取今天。</p>
     *
     * @param storeId    门店
     * @param candidates 已完成折叠 / 改写的候选（原地回填）
     * @return 同一个 list，便于链式调用
     */
    private List<StoreReturnVegCandidateVo> fillReturnedQuantity(Long storeId, List<StoreReturnVegCandidateVo> candidates) {
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        for (StoreReturnVegCandidateVo vo : candidates) {
            if (vo.getProductId() == null) {
                continue;
            }
            vo.setReturnedQuantity(sumReturnedQuantitySinceForProduct(storeId, today, today, vo.getProductId()));
        }
        return candidates;
    }

    @Override
    public List<StoreReturnVegCandidateVo> listOtherCandidates(Long storeId) {
        if (storeId == null) {
            return List.of();
        }
        // V6-R214 甲方原话「其他的类型统一显示在其他产品里」→ 非猪肉非果蔬的一律落这个 tab（含 belong_type 为空的外购品）。
        return fillReturnedQuantity(storeId, listReturnCandidates(RETURN_TAB_OTHER, storeId));
    }

    @Override
    public List<StoreReturnVegCandidateVo> listVegCandidates(Long storeId) {
        if (storeId == null) {
            return List.of();
        }
        // V6-R214：候选取自字典「退回产品清单」里 belong_type=vegetable 的产品。
        // 材料外售折叠（成品→原材料）仍保留：清单里若配的是成品，退回入库要落到它的原材料上。
        // 顺序关键：先折叠（可能改写 productId）→ 再按最终 id 回填已退量。
        return fillReturnedQuantity(storeId,
            foldVegMaterialSold(listReturnCandidates(RETURN_TAB_VEG, storeId)));
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
            String vBelong = c.getBelongType();
            StoreReturnVegCandidateVo vo = folded.computeIfAbsent(key, k -> {
                StoreReturnVegCandidateVo v = new StoreReturnVegCandidateVo();
                v.setProductId(key);
                v.setProductName(vName);
                v.setProductUnit(vUnit);
                v.setBelongType(vBelong);
                // ⚠️ arrivedQuantity 种子必须是 null 不是 ZERO：null = 不封顶（row214 起恒为 null），
                // 种 ZERO 会把「不封顶」折成「上限 0」，前端 maxOf 算出 0 → 输入框直接禁用，果蔬整个 tab 填不了。
                v.setArrivedQuantity(null);
                v.setReturnedQuantity(BigDecimal.ZERO);
                v.setInReturnList(Boolean.FALSE);
                return v;
            });
            // 折叠成一行的几个成品里只要有一个是清单产品，这一行就按清单规则走（不封顶 + 两位小数）：
            // 清单产品的「不封顶」是甲方明确放开的（row214 / D-0055），被同组的到店产品拖回封顶
            // 等于把它又关上；反过来放宽只影响那一个原材料，两害相权取轻。
            if (Boolean.TRUE.equals(c.getInReturnList())) {
                vo.setInReturnList(Boolean.TRUE);
            }
            // row41：多成品共享同一原材料折叠成一行 → 到店量累加。
            BigDecimal arrived = c.getArrivedQuantity();
            if (arrived != null) {
                vo.setArrivedQuantity(vo.getArrivedQuantity() == null ? arrived : vo.getArrivedQuantity().add(arrived));
            }
            // ⚠️ 已退量必须跟着一起累加、不能漏搬：漏了它 returnedQuantity 恒 null，
            // 提交闸 mergeRemain 会把 used 当 0 → 剩余额度每次都按满额算 → **同一产品可以无限次退**
            // （实测：上限 10 的上海青连退 10 + 1 全部放行）。到店量与已退量必须成对搬运。
            BigDecimal returned = c.getReturnedQuantity();
            if (returned != null) {
                vo.setReturnedQuantity(vo.getReturnedQuantity() == null ? returned : vo.getReturnedQuantity().add(returned));
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

        // row35：仓库称重上限校验。仓库确认页录入的实收（receivedWeight，缺省回退 receivedQty）不得离谱：
        //   · 产品单位 = kg：录的是重量 → ≤ 门店录入重量（goods_weight）的一倍；
        //   · 产品单位 ≠ kg 且**原材料单位 = kg**：录的仍是重量（如 80g 干货礼盒录 0.080）
        //     → ≤ 当天到店该产品总重量（sumDeliveredWeightToStore，kg）；
        //   · 产品单位 ≠ kg 且**原材料单位 ≠ kg**：录的是<b>件数</b>（枚 / 份，row13）→ 与 kg 到店重不同量纲，
        //     不能拿到店重量比（30 枚礼盒录 30、到店重按 kg 算只有几公斤，一比就误伤）。
        //     row24 甲方给了本量纲下的算法：上限 = 门店退回量 × 计量规则(material_num)，即
        //     「退了几件成品 × 每件折算多少原材料」。mp 侧同一个数做输入上限，这里复核，两边同源。
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
            } else if (isKgUnit(resolveMetricUnit(returnProduct))) {
                LocalDate arriveDate = existing.getReturnDate() != null
                    ? existing.getReturnDate().toLocalDate() : LocalDate.now(ZONE_SHANGHAI);
                BigDecimal arrivedTotal = productProductionMapper.sumDeliveredWeightToStore(
                    existing.getStoreId(), existing.getProductId(), arriveDate);
                if (arrivedTotal != null && arrivedTotal.signum() > 0 && weighed.compareTo(arrivedTotal) > 0) {
                    throw new ServiceException("仓库称重重量(" + weighed.toPlainString()
                        + "kg)不能超过该产品当天到店总重量(" + arrivedTotal.toPlainString() + "kg)", 400);
                }
            } else {
                // 件数口径（row24）：上限 = 退回量 × 计量规则
                BigDecimal cap = maxConfirmQty(existing.getReturnQuantity(), returnProduct);
                if (cap != null && weighed.compareTo(cap) > 0) {
                    String unit = StringUtils.isBlank(returnProduct.getProductUnit()) ? "" : returnProduct.getProductUnit();
                    // row33：件数口径下 mp 那一格的标题已改叫「仓库接收量」，报错文案跟着叫同一个名字——
                    // 工人在标着「仓库接收量」的框里填数，弹出来却说「仓库实收数量」会以为是另一个字段。
                    throw new ServiceException("仓库接收量(" + weighed.stripTrailingZeros().toPlainString()
                        + ")不能超过可退上限(" + cap.toPlainString() + ")：门店退回 "
                        + existing.getReturnQuantity().stripTrailingZeros().toPlainString() + unit
                        + " × 计量规则 " + materialRatio(returnProduct).stripTrailingZeros().toPlainString(), 400);
                }
            }
        }

        // row24 第 2 点：生产产品没配原材料 → 只能丢弃，不能退回仓库（仓库只存原材料，无从确定入哪个料的库存）
        if (!DISCARD_YES.equals(bo.getIsDiscard()) && !canInbound(returnProduct)) {
            throw new ServiceException("产品「" + (returnProduct == null ? existing.getProductId() : returnProduct.getProductName())
                + "」未配置原材料，无法退回入库，只能标记为产品丢弃", 400);
        }

        // 小程序 row269：处置方式。丢弃的产品不进库存，故整条入库链路（解析原材料 / 定库位 / 写库存）全跳过。
        boolean discard = DISCARD_YES.equals(bo.getIsDiscard());

        Long inboundProductId = null;
        Long locationId = null;
        if (!discard) {
            // 入库目标产品：配了 product_material 的成品(果蔬/猪肉)→原材料 product_material（缺料阻断），
            // 本身即原材料(白条字典 kg 产品/外购原料)→按产品ID入库（邓博 2026-07-16：退回入库都是原材料）。
            inboundProductId = resolveInboundProductId(existing.getProductId());
            // 入库库位：前端显式选优先；mp 确认页只填实收量不选库位 → 按入库产品预设库位 / 库存最多库位兜底；
            // 仍无 → 阻断（不做「只写流水不增库存」的库存黑洞，提示运营先补库位）。
            locationId = bo.getLocationId() != null ? bo.getLocationId() : resolveDefaultLocation(inboundProductId);
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
        // 丢弃也要落 is_discard + 实收量：admin 行203「入库数/丢弃数」与损耗统计都要靠这两个数
        entity.setIsDiscard(discard ? DISCARD_YES : DISCARD_NO);
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

        if (discard) {
            // 小程序 row269：产品丢弃 —— 状态照常推到 received（这单已处理完），但**不写任何库存与入库流水**。
            log.info("[STORE-RETURN-UNIFY-001] confirm id={} no={} receivedQty={} → received 但标记丢弃，不入库",
                bo.getId(), existing.getReturnNo(), bo.getReceivedQty());
            return rows;
        }

        // 确认实收时才联动外购入库：同事务 UPSERT location_stock + stock_flow(store_return_in)，
        // inbound 内部校验库位 / 数量，失败抛 → 整体回滚（确认与入库一致，不留半态）。
        // row31：入「退货专属篮」（plot/ear/white_bar 全空），不并进地块/耳号行；再领用/发货不带追溯（客户确认符合）。
        purchaseInService.inboundReturnBasket(inboundProductId, locationId, bo.getReceivedQty(),
            FLOW_TYPE_RETURN_IN, returnInboundRemark("门店退回仓库确认入库", existing.getReturnNo(), returnProduct));

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

    @Override
    public List<StoreReturnDetailExportVo> queryDetailExportList(StoreReturnQuery query) {
        List<StoreReturnVo> rows = queryList(query);
        List<StoreReturnDetailExportVo> out = new ArrayList<>(rows.size());
        for (StoreReturnVo r : rows) {
            StoreReturnDetailExportVo vo = new StoreReturnDetailExportVo();
            vo.setProductName(r.getProductName());
            vo.setProductUnit(r.getProductUnit());
            vo.setReturnQuantity(formatQtyByUnit(r.getReturnQuantity(), r.getProductUnit()));
            vo.setReceivedAmount(formatReceivedAmount(r.getReceivedWeight(), r.getProductUnit(), r.getMaterialUnit()));
            vo.setQuantityDiff(formatQuantityDiff(r));
            vo.setIsDiscard(formatDiscardText(r));
            vo.setReturnStatus(formatReturnStatusText(r));
            vo.setLocationName(StringUtils.isBlank(r.getLocationName()) ? EXPORT_EMPTY : r.getLocationName());
            vo.setConfirmTime(r.getConfirmTime() == null ? EXPORT_EMPTY : EXPORT_TIME_FORMAT.format(r.getConfirmTime()));
            out.add(vo);
        }
        return out;
    }

    /** 导出里的空值占位符（与弹窗里的 em dash 一致）。 */
    private static final String EXPORT_EMPTY = "—";

    private static final DateTimeFormatter EXPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 数量按单位格式化：kg → 三位小数；计件单位 → 去尾零（对齐前端 {@code formatQtyByUnit}）。 */
    private static String formatQtyByUnit(BigDecimal v, String unit) {
        if (v == null) {
            return EXPORT_EMPTY;
        }
        return isKgUnit(unit) ? v.setScale(3, RoundingMode.HALF_UP).toPlainString()
            : v.stripTrailingZeros().toPlainString();
    }

    /**
     * 仓库实收量文本（对齐前端 {@code formatReceivedAmount}）：计量单位由**原材料单位**决定，
     * 原材料按 kg → {@code X.XXXkg}；原材料按件 → {@code 整数 + 原材料单位}。
     * 后缀必须跟判据同源，否则 30 枚鸡蛋会被标成「30份」= 仓库收了 30 个礼盒。
     */
    private static String formatReceivedAmount(BigDecimal v, String productUnit, String materialUnit) {
        if (v == null) {
            return EXPORT_EMPTY;
        }
        String metric = StringUtils.isNotBlank(materialUnit) ? materialUnit.trim()
            : (productUnit == null ? "" : productUnit);
        return isKgUnit(metric) ? v.setScale(3, RoundingMode.HALF_UP).toPlainString() + "kg"
            : v.setScale(0, RoundingMode.HALF_UP).toPlainString() + metric;
    }

    /**
     * 差异量 = 退回量 − 实收量，**只有两边同量纲才有意义**：退回量按产品单位计、实收量按原材料单位计，
     * 故仅当产品单位与计量单位都是 kg 时才相减；未确认（实收为空）同样不给数。
     */
    private static String formatQuantityDiff(StoreReturnVo r) {
        String metric = StringUtils.isNotBlank(r.getMaterialUnit()) ? r.getMaterialUnit().trim() : r.getProductUnit();
        if (!isKgUnit(r.getProductUnit()) || !isKgUnit(metric)
            || r.getReturnQuantity() == null || r.getReceivedWeight() == null) {
            return EXPORT_EMPTY;
        }
        return r.getReturnQuantity().subtract(r.getReceivedWeight())
            .setScale(3, RoundingMode.HALF_UP).toPlainString() + "kg";
    }

    /** 是否丢弃：只有已确认行才有处置结论（pending 行库里是建表默认 0，不能当成「否」）。 */
    private static String formatDiscardText(StoreReturnVo r) {
        if (!STATUS_RECEIVED.equals(r.getReturnStatus())) {
            return EXPORT_EMPTY;
        }
        return DISCARD_YES.equals(r.getIsDiscard()) ? "是" : "否";
    }

    /** 退回状态：丢弃行单独出「已丢弃」，不沿用字典的「已入库」（它压根没进库存）。 */
    private static String formatReturnStatusText(StoreReturnVo r) {
        if (STATUS_RECEIVED.equals(r.getReturnStatus())) {
            return DISCARD_YES.equals(r.getIsDiscard()) ? "已丢弃" : "已入库";
        }
        if (STATUS_PENDING.equals(r.getReturnStatus())) {
            // 与字典 djs_store_return_status 的 label 同口径（甲方 row213 第 2/3 条：待处理 / 已处理）。
            // 已确认行仍分「已入库 / 已丢弃」——明细导出比列表多一层信息，不折回两态。
            return "待处理";
        }
        return StringUtils.isBlank(r.getReturnStatus()) ? EXPORT_EMPTY : r.getReturnStatus();
    }

    /**
     * 仓库「退货记录」/ admin「门店退回操作」外层主从视图聚合（仅门店→仓库方向，不含 customer_to_store），
     * 按 退回日期(截到天) + 退回类型 + 门店/退回单位 聚合，退货日期倒序、再组内倒序的稳定排序。分页 / 导出共用。
     *
     * <p>STR-RETURN-OPS-001 起分组键多了「退回类型 + 退回单位」两维：单位退回没有门店
     * （{@code store_id} 恒 NULL），沿用旧的「日期|门店」键会把所有单位退回折进 {@code ...|null} 一组、
     * 而且旧代码的 {@code filter(storeId != null)} 会直接把它们静默丢掉。</p>
     */
    @Override
    public List<StoreReturnOwnerOptionVo> listStoreDailyOwnerOptions() {
        // 与列表同源：只看门店→仓库方向的退回记录，不带任何其他筛选条件 ——
        // 下拉项跟着当前搜索条件变会造成「筛完就只剩自己那一项」的死循环，选项池必须是全量的。
        List<StoreReturn> rows = baseMapper.selectList(new LambdaQueryWrapper<StoreReturn>()
            .select(StoreReturn::getReturnType, StoreReturn::getStoreId, StoreReturn::getReturnUnit)
            .eq(StoreReturn::getReturnDirection, DIRECTION_STORE_TO_WAREHOUSE));
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> storeIds = rows.stream()
            .filter(r -> !RETURN_TYPE_UNIT.equals(r.getReturnType()))
            .map(StoreReturn::getStoreId).filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> unitValues = rows.stream()
            .filter(r -> RETURN_TYPE_UNIT.equals(r.getReturnType()))
            .map(StoreReturn::getReturnUnit).filter(StringUtils::isNotBlank)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, String> storeNames = storeNameMap(new ArrayList<>(storeIds));
        // ⚠️ getAllDictByDictType 返的是 value → label，别反着取（与列表列 unitLabel 同一套取名规则）。
        Map<String, String> unitLabels = dictService.getAllDictByDictType(DICT_RETURN_UNIT);

        List<StoreReturnOwnerOptionVo> options = new ArrayList<>(storeIds.size() + unitValues.size());
        for (Long storeId : storeIds) {
            StoreReturnOwnerOptionVo vo = new StoreReturnOwnerOptionVo();
            vo.setReturnType(RETURN_TYPE_STORE);
            vo.setStoreId(storeId);
            // 门店被删/改名后查不到名字时退回 id 字符串：宁可显示一串数字，也不能让这一项从下拉里消失
            // ——它背后确实有记录，消失了用户就再也筛不到那些行。
            vo.setLabel(StringUtils.isNotBlank(storeNames.get(storeId))
                ? storeNames.get(storeId) : String.valueOf(storeId));
            options.add(vo);
        }
        for (String unit : unitValues) {
            StoreReturnOwnerOptionVo vo = new StoreReturnOwnerOptionVo();
            vo.setReturnType(RETURN_TYPE_UNIT);
            vo.setReturnUnit(unit);
            vo.setLabel(unitLabel(unitLabels, unit));
            options.add(vo);
        }
        // 门店在前、单位在后，各自按显示名排序（列表默认按日期倒序，下拉再跟着数据顺序走会忽前忽后）
        options.sort(Comparator.comparing((StoreReturnOwnerOptionVo o) -> RETURN_TYPE_UNIT.equals(o.getReturnType()))
            .thenComparing(StoreReturnOwnerOptionVo::getLabel, Comparator.nullsLast(String::compareTo)));
        return options;
    }

    private List<StoreReturnStoreDailyVo> buildStoreDailyList(StoreReturnQuery query) {
        StoreReturnQuery q = query == null ? new StoreReturnQuery() : query;
        q.setReturnDirection(DIRECTION_STORE_TO_WAREHOUSE);
        // 「退回状态」是**组级**结论（组内还有 pending 就是待处理），不能下推到行：
        // 下推后一张「3 行里 2 行已确认」的单，按「已处理」筛会只回那 2 行 → 组内全 received →
        // 整组被判成已处理、操作列变成「查看详情」，剩下那 1 行待处理的货再也点不开；
        // 品类数也只数到被筛剩的行。故这里摘掉它，聚合完再按组过滤。
        String groupStatus = q.getReturnStatus();
        q.setReturnStatus(null);
        List<StoreReturn> rows = baseMapper.selectList(buildQueryWrapper(q));
        q.setReturnStatus(groupStatus);
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        // 分组键 = 日期 | 类型 | 门店（单位退回用退回单位名，永不与门店 id 撞）
        Map<String, List<StoreReturn>> byGroup = rows.stream()
            .filter(r -> r.getReturnDate() != null)
            .collect(Collectors.groupingBy(StoreReturnServiceImpl::storeDailyGroupKey,
                LinkedHashMap::new, Collectors.toList()));
        if (byGroup.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> storeIds = byGroup.values().stream()
            .map(g -> g.get(0).getStoreId()).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> storeNames = storeNameMap(new ArrayList<>(storeIds));
        // 单位退回的 return_unit 存的是**字典 value**（如 yejiazhuang_cun），甲方要的「退回门店」列是
        // **名称**（叶家庄村）。存 value、展示换 label：客户在字典里改名后列表跟着变，历史行不用回刷。
        // ⚠️ getAllDictByDictType 返的是 value → label，别反着取。
        Map<String, String> unitLabels = dictService.getAllDictByDictType(DICT_RETURN_UNIT);
        // row57：重量三列只算按重量计（kg）行，份数产品单独归「非重量产品退回重量」。
        // row15：份数产品里**只有原材料单位 = kg 的**才累加（它的 received_weight 才是重量）。
        // 一次查全部行产品（单位 + 原材料 + 归属类型）避免 N+1；归属类型还要喂三类品类数。
        List<ProductInfo> rowProducts = productsOfReturns(rows);
        Map<Long, String> unitByProduct = productUnitMap(rowProducts);
        Map<Long, String> metricUnitByProduct = resolveMetricUnits(rowProducts);
        Map<Long, String> belongByProduct = rowProducts.stream().collect(Collectors.toMap(
            ProductInfo::getId, p -> p.getBelongType() == null ? "" : p.getBelongType(), (a, b) -> a));
        List<StoreReturnStoreDailyVo> all = new ArrayList<>(byGroup.size());
        for (List<StoreReturn> group : byGroup.values()) {
            StoreReturn any = group.get(0);
            boolean unitType = RETURN_TYPE_UNIT.equals(any.getReturnType());
            StoreReturnStoreDailyVo vo = new StoreReturnStoreDailyVo();
            vo.setReturnDate(any.getReturnDate().toLocalDate());
            vo.setReturnType(unitType ? RETURN_TYPE_UNIT : RETURN_TYPE_STORE);
            vo.setReturnUnit(unitType ? any.getReturnUnit() : null);
            vo.setStoreId(any.getStoreId());
            // 「退回门店」列：单位退回展示退回单位名（甲方 row213 第 5 条），门店退回展示门店名。
            // 两处同源回填，旧「仓库退回记录」页也不会因为多出单位退回而显示空门店。
            vo.setStoreName(unitType ? unitLabel(unitLabels, any.getReturnUnit())
                : storeNames.get(any.getStoreId()));
            // 三类品类数（甲方 row213 第 3 条）：**后端算**，前端 filter 当前页在分页/导出时必错。
            // 分流口径复用 returnTabOf，与 mp 退回操作三个 tab 同一份，不另写一套。
            Set<Long> porkIds = new LinkedHashSet<>();
            Set<Long> vegIds = new LinkedHashSet<>();
            Set<Long> otherIds = new LinkedHashSet<>();
            for (StoreReturn r : group) {
                if (r.getProductId() == null) {
                    continue;
                }
                switch (returnTabOf(belongByProduct.get(r.getProductId()))) {
                    case RETURN_TAB_PORK -> porkIds.add(r.getProductId());
                    case RETURN_TAB_VEG -> vegIds.add(r.getProductId());
                    default -> otherIds.add(r.getProductId());
                }
            }
            vo.setProductKindCount((int) group.stream()
                .map(StoreReturn::getProductId).filter(Objects::nonNull).distinct().count());
            vo.setPorkKindCount(porkIds.size());
            vo.setVegKindCount(vegIds.size());
            vo.setOtherKindCount(otherIds.size());
            // 退回状态：组内还有 pending 行就是「待处理」（还有货没处理完），全部 received 才是「已处理」。
            boolean allReceived = group.stream().allMatch(r -> STATUS_RECEIVED.equals(r.getReturnStatus()));
            vo.setReturnStatus(allReceived ? STATUS_RECEIVED : STATUS_PENDING);
            // 退回操作人/时间：操作人取组内最早一条有操作人的行；时间取 create_time（缺省回落 return_date）。
            // 不能用 return_date 顶替「退回时间」：单位退回的 return_date 是甲方填的退回日期（可补录昨天），
            // 与「提交那一刻」是两回事。
            group.stream().filter(r -> r.getOperatorId() != null)
                .min(Comparator.comparing(StoreReturn::getId))
                .ifPresent(first -> {
                    vo.setOperatorId(first.getOperatorId());
                    vo.setReturnTime(first.getCreateTime() != null
                        ? toLocalDateTime(first.getCreateTime()) : first.getReturnDate());
                });
            if (vo.getReturnTime() == null) {
                vo.setReturnTime(any.getReturnDate());
            }
            // row57：① 确认重量 = Σ kg 行 received_weight；② 退货重量 = Σ kg 行 goods_weight；
            //        ③ 重量差异 = 退货 − 确认（同为 kg 口径）。
            // row15（甲方 2026-08-04 口径）：④ 非重量产品退回重量 = Σ「产品单位 ≠ kg **且原材料单位 = kg**」行的
            //        received_weight。原材料单位非 kg 的行（鸡蛋按枚、礼盒按份）实收落的是<b>件数</b>，
            //        累进 kg 合计就是把 30 枚当 30 公斤 —— 实测 31.430kg 里 31.000 全是这么来的，正确值 0.430。
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
                } else if (isKgUnit(metricUnitByProduct.get(r.getProductId())) && r.getReceivedWeight() != null) {
                    nonWeightReturnTotal = nonWeightReturnTotal.add(r.getReceivedWeight());
                }
            }
            vo.setReturnWeightTotal(returnTotal);
            vo.setConfirmWeightTotal(confirmTotal);
            vo.setWeightDiffTotal(returnTotal.subtract(confirmTotal));
            vo.setNonWeightReturnWeightTotal(nonWeightReturnTotal);
            // admin row203：「确认进度」由 已确认/总数 改为 **入库数/丢弃数**（只统计已确认的行，
            // 按 is_discard 分两侧）。确认时间 / 确认人仍取「最近一条已确认行」。
            // totalCount / confirmedCount 保留：前端把「共 N 条、待确认 M 条」挪进 tooltip，
            // 否则改完只剩两个数、看不出还有几条没确认。
            int confirmedCount = (int) group.stream()
                .filter(r -> STATUS_RECEIVED.equals(r.getReturnStatus())).count();
            int inboundCount = (int) group.stream()
                .filter(r -> STATUS_RECEIVED.equals(r.getReturnStatus()) && !DISCARD_YES.equals(r.getIsDiscard()))
                .count();
            int discardCount = (int) group.stream()
                .filter(r -> STATUS_RECEIVED.equals(r.getReturnStatus()) && DISCARD_YES.equals(r.getIsDiscard()))
                .count();
            vo.setConfirmedCount(confirmedCount);
            vo.setTotalCount(group.size());
            vo.setInboundCount(inboundCount);
            vo.setDiscardCount(discardCount);
            vo.setConfirmProgress(inboundCount + "/" + discardCount);
            group.stream().filter(r -> r.getConfirmTime() != null)
                .max(Comparator.comparing(StoreReturn::getConfirmTime))
                .ifPresent(latest -> {
                    vo.setConfirmTime(latest.getConfirmTime());
                    vo.setConfirmUser(latest.getConfirmUserId());
                });
            all.add(vo);
        }
        // 组级「退回状态」过滤（见方法开头：这一维不能下推到行）。
        if (StringUtils.isNotBlank(groupStatus)) {
            all.removeIf(v -> !groupStatus.equals(v.getReturnStatus()));
        }
        // row178：确认人姓名 service 侧预填。@Translation 只跑 Jackson 序列化链，导出走 FastExcel
        // 直接读字段，光靠注解「确认人」整列是空的。STR-RETURN-OPS-001 的「退回操作人」同理会空。
        fillConfirmUserNames(all);
        fillOperatorNames(all);
        // 排序：退回日期倒序 → 门店 id 倒序（单位退回 storeId 为 null，用 MIN_VALUE 排到最后，
        // 不做 null 处理 Comparator.comparing 会直接 NPE）。
        all.sort(Comparator
            .comparing(StoreReturnStoreDailyVo::getReturnDate, Comparator.reverseOrder())
            .thenComparing(v -> v.getStoreId() == null ? Long.MIN_VALUE : v.getStoreId(),
                Comparator.reverseOrder()));
        return all;
    }

    /**
     * {@code BaseEntity.createTime} 是 {@link java.util.Date}，本 VO 统一用 {@link LocalDateTime}
     * （与 {@code return_date} / {@code confirm_time} 同型）。按业务时区换算，避免 UTC 偏 8 小时。
     */
    private static LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZONE_SHANGHAI);
    }

    /**
     * 退回单位 value → 展示名（字典 {@code djs_return_unit} 的 label）。
     * 字典里查不到（客户把那条删了、或历史行存的是旧值）时原样回退 value，不让整列变空。
     */
    private static String unitLabel(Map<String, String> unitLabels, String unitValue) {
        if (StringUtils.isBlank(unitValue)) {
            return null;
        }
        String label = unitLabels == null ? null : unitLabels.get(unitValue);
        return StringUtils.isNotBlank(label) ? label : unitValue;
    }

    /** 门店退回操作外层分组键：{@code 日期|类型|门店或退回单位}（单位退回没有门店，用单位名占位）。 */
    private static String storeDailyGroupKey(StoreReturn r) {
        String type = RETURN_TYPE_UNIT.equals(r.getReturnType()) ? RETURN_TYPE_UNIT : RETURN_TYPE_STORE;
        String owner = RETURN_TYPE_UNIT.equals(type)
            ? "U:" + (r.getReturnUnit() == null ? "" : r.getReturnUnit())
            : "S:" + r.getStoreId();
        return r.getReturnDate().toLocalDate() + "|" + type + "|" + owner;
    }

    /** row178：按 confirmUser 批量回填确认人姓名（去重后逐个查，组数量级为「门店 × 天」，无 N+1 风险）。 */
    private void fillConfirmUserNames(List<StoreReturnStoreDailyVo> rows) {
        Map<Long, String> cache = new LinkedHashMap<>();
        for (StoreReturnStoreDailyVo vo : rows) {
            Long uid = vo.getConfirmUser();
            if (uid == null) {
                continue;
            }
            vo.setConfirmUserName(cache.computeIfAbsent(uid, userService::selectNicknameById));
        }
    }

    /**
     * STR-RETURN-OPS-001：按 operatorId 批量回填「退回操作人」姓名。
     *
     * <p>同 {@link #fillConfirmUserNames} 的理由 —— {@code @Translation} 只在 Jackson 序列化链上生效，
     * 导出走 FastExcel 直接读字段，光靠注解这一列会整列空。</p>
     */
    private void fillOperatorNames(List<StoreReturnStoreDailyVo> rows) {
        Map<Long, String> cache = new LinkedHashMap<>();
        for (StoreReturnStoreDailyVo vo : rows) {
            Long uid = vo.getOperatorId();
            if (uid == null) {
                continue;
            }
            vo.setOperatorName(cache.computeIfAbsent(uid, userService::selectNicknameById));
        }
    }

    /**
     * row57/row15：一次查出退回行涉及的全部产品，供「产品单位」/「计量口径单位」/ 三类品类数 /
     * 退回处理抽屉四套用途共用，避免逐行 selectById。
     *
     * <p><b>投影必须够宽</b>（STR-RETURN-OPS-001 踩过）：抽屉行要用 {@code productName / productSpec /
     * materialNum / productAttr / storeLocationId}，缺一个就会被静默降级 —— {@code materialRatio} 回落 1、
     * {@code canConvert} 判成 false、名字列空白。<b>必须带 {@code productMaterial}</b>：
     * {@link #resolveMetricUnits} 靠它解析原材料单位。</p>
     */
    private List<ProductInfo> productsOfReturns(List<StoreReturn> rows) {
        List<Long> pids = rows.stream().map(StoreReturn::getProductId)
            .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (pids.isEmpty()) {
            return List.of();
        }
        return productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
            .select(ProductInfo::getId, ProductInfo::getProductName, ProductInfo::getProductSpec,
                ProductInfo::getProductUnit, ProductInfo::getProductMaterial, ProductInfo::getBelongType,
                ProductInfo::getMaterialNum, ProductInfo::getProductAttr, ProductInfo::getStoreLocationId)
            .in(ProductInfo::getId, pids));
    }

    /** row57：退回行产品单位 map（productId → productUnit，空单位归 ""），供 kg / 非 kg 分流。 */
    private Map<Long, String> productUnitMap(List<ProductInfo> products) {
        return products.stream().collect(Collectors.toMap(ProductInfo::getId,
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
        // row13：必须带 product_material —— mp 确认页靠「原材料单位」决定称重框是重量(kg,小数)还是件数(整数)。
        // row24：再带 material_num（计量规则，算件数上限）+ product_attr（判「成品缺原材料 → 只能丢弃」）。
        List<ProductInfo> products = productIds.isEmpty() ? List.<ProductInfo>of()
            : productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .select(ProductInfo::getId, ProductInfo::getProductName, ProductInfo::getBelongType,
                    ProductInfo::getProductUnit, ProductInfo::getProductSpec, ProductInfo::getProductMaterial,
                    ProductInfo::getMaterialNum, ProductInfo::getProductAttr)
                .in(ProductInfo::getId, productIds));
        Map<Long, ProductInfo> productMap = products.stream()
            .collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));
        // 批量解析（一次 IN 查原材料产品），不逐行查库
        Map<Long, String> metricUnits = resolveMetricUnits(products);
        // V6-R214/R217：清单内产品的录入精度另有口径（非 kg 也放开到两位小数，D-0054），随行下发免得 mp 再查一次字典
        Set<Long> returnListIds = new HashSet<>(resolveReturnListProductIds());
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
            String metricUnit = p == null ? null : metricUnits.get(p.getId());
            vo.setMaterialUnit(metricUnit);
            // row24：件数口径才给上限（= 退回量 × 计量规则）；kg 口径由重量分支另行封顶，给了反而误导
            vo.setMaterialNum(materialRatio(p));
            vo.setMaxConfirmQty(isKgUnit(metricUnit) ? null : maxConfirmQty(r.getReturnQuantity(), p));
            vo.setInReturnList(p != null && returnListIds.contains(p.getId()));
            vo.setCanInbound(canInbound(p));
            vo.setProductSpec(p == null ? null : p.getProductSpec());
            vo.setReturnQuantity(r.getReturnQuantity());
            vo.setReturnWeight(r.getGoodsWeight());
            vo.setConfirmWeight(r.getReceivedWeight());
            vo.setIsConfirm(received ? 1 : 0);
            // row269：回显处置方式；未确认行库里是默认 0，正好等于 mp 卡片「默认退回入库」的初始态
            vo.setIsDiscard(r.getIsDiscard() == null ? DISCARD_NO : r.getIsDiscard());
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

    // ---------- STR-RETURN-OPS-001 admin「门店退回操作」 ----------

    @Override
    public List<StoreReturnOpsItemVo> listOperationItems(StoreReturnQuery query) {
        StoreReturnQuery q = query == null ? new StoreReturnQuery() : query;
        q.setReturnDirection(DIRECTION_STORE_TO_WAREHOUSE);
        boolean unitType = RETURN_TYPE_UNIT.equals(q.getReturnType());
        if (unitType && StringUtils.isBlank(q.getReturnUnit())) {
            // 单位退回按「退回单位」区分是不同的一张单（都没有门店，只靠日期 + 类型会串单）
            throw new ServiceException("查询单位退回明细时必须指定退回单位", 400);
        }
        List<StoreReturn> rows = baseMapper.selectList(buildQueryWrapper(q));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<ProductInfo> products = productsOfReturns(rows);
        Map<Long, ProductInfo> productById = products.stream()
            .collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a, LinkedHashMap::new));
        // 批量补原材料产品（换算单位 / 入库库位默认都按原材料走），避免逐行 selectById
        Set<Long> materialIds = products.stream().map(ProductInfo::getProductMaterial)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!materialIds.isEmpty()) {
            productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                    .select(ProductInfo::getId, ProductInfo::getProductUnit, ProductInfo::getStoreLocationId,
                        ProductInfo::getProductName)
                    .in(ProductInfo::getId, materialIds))
                .forEach(m -> productById.putIfAbsent(m.getId(), m));
        }
        Map<Long, String> metricUnits = resolveMetricUnits(products);
        Set<Long> returnListIds = new HashSet<>(resolveReturnListProductIds());
        Map<Long, LocationPickerVo> enabledLocations = allEnabledLocationMap();
        List<StoreReturnOpsItemVo> result = new ArrayList<>(rows.size());
        for (StoreReturn r : rows) {
            ProductInfo p = r.getProductId() == null ? null : productById.get(r.getProductId());
            StoreReturnOpsItemVo vo = new StoreReturnOpsItemVo();
            vo.setId(r.getId());
            vo.setReturnNo(r.getReturnNo());
            vo.setReturnType(RETURN_TYPE_UNIT.equals(r.getReturnType()) ? RETURN_TYPE_UNIT : RETURN_TYPE_STORE);
            vo.setReturnUnit(r.getReturnUnit());
            vo.setStoreId(r.getStoreId());
            vo.setProductId(r.getProductId());
            vo.setProductName(p == null ? null : p.getProductName());
            vo.setProductSpec(p == null ? null : p.getProductSpec());
            vo.setProductUnit(p == null ? null : p.getProductUnit());
            String metricUnit = p == null ? null : metricUnits.get(p.getId());
            vo.setMaterialUnit(metricUnit);
            vo.setMaterialNum(materialRatio(p));
            vo.setInReturnList(p != null && returnListIds.contains(p.getId()));
            vo.setCanInbound(canInbound(p));
            vo.setCanConvert(canConvert(p, metricUnit));
            vo.setReturnQuantity(r.getReturnQuantity());
            vo.setReturnWeight(r.getGoodsWeight());
            vo.setReceivedQty(r.getReceivedQty());
            vo.setReceivedWeight(r.getReceivedWeight());
            vo.setLocationId(r.getLocationId());
            vo.setIsDiscard(r.getIsDiscard() == null ? DISCARD_NO : r.getIsDiscard());
            vo.setReturnStatus(r.getReturnStatus());
            vo.setReturnDate(r.getReturnDate());
            vo.setOperatorId(r.getOperatorId());
            vo.setConfirmUserId(r.getConfirmUserId());
            vo.setConfirmTime(r.getConfirmTime());
            LocationChoice choice = resolveLocationChoice(p, productById, enabledLocations);
            vo.setDefaultLocationId(choice.defaultLocationId());
            vo.setLocationOptions(choice.options());
            result.add(vo);
        }
        // 库位名 + 人员姓名按需回填（去重后逐个查，单张单行数量级小）
        Map<Long, String> locationNames = locationNameMap(result.stream()
            .map(StoreReturnOpsItemVo::getLocationId).filter(Objects::nonNull).distinct().toList());
        Map<Long, String> userNames = new LinkedHashMap<>();
        for (StoreReturnOpsItemVo vo : result) {
            if (vo.getLocationId() != null) {
                vo.setLocationName(locationNames.get(vo.getLocationId()));
            }
            if (vo.getOperatorId() != null) {
                vo.setOperatorName(userNames.computeIfAbsent(vo.getOperatorId(), userService::selectNicknameById));
            }
            if (vo.getConfirmUserId() != null) {
                vo.setConfirmUserName(userNames.computeIfAbsent(vo.getConfirmUserId(), userService::selectNicknameById));
            }
        }
        return result;
    }

    @Override
    public List<StoreReturnUnitCandidateVo> listUnitCandidates() {
        List<Long> ids = resolveReturnListProductIds();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<ProductInfo> products = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
            .select(ProductInfo::getId, ProductInfo::getProductId, ProductInfo::getProductName,
                ProductInfo::getProductSpec, ProductInfo::getProductUnit, ProductInfo::getBelongType,
                ProductInfo::getProductMaterial, ProductInfo::getMaterialNum, ProductInfo::getProductAttr,
                ProductInfo::getStoreLocationId)
            .in(ProductInfo::getId, ids).orderByAsc(ProductInfo::getId));
        if (products.isEmpty()) {
            return List.of();
        }
        Map<Long, ProductInfo> productById = products.stream()
            .collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a, LinkedHashMap::new));
        Set<Long> materialIds = products.stream().map(ProductInfo::getProductMaterial)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!materialIds.isEmpty()) {
            productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                    .select(ProductInfo::getId, ProductInfo::getProductUnit, ProductInfo::getStoreLocationId,
                        ProductInfo::getProductName)
                    .in(ProductInfo::getId, materialIds))
                .forEach(m -> productById.putIfAbsent(m.getId(), m));
        }
        Map<Long, String> metricUnits = resolveMetricUnits(new ArrayList<>(productById.values()));
        Set<Long> returnListIds = new HashSet<>(ids);
        Map<Long, LocationPickerVo> enabledLocations = allEnabledLocationMap();
        // V6 row226 对「退回管理」菜单下的两个入口一视同仁：门店退回那一页剔掉了，单位退回这边也剔。
        // 剔的理由（猪肉侧没有成品→原材料的自动折叠，列出来分不清退的是哪一个）与从哪个入口进来无关；
        // 只在门店那页剔，同一个菜单下就会出现两套口径。
        // ⚠️ 判据下在**产出行的这个循环**上，不下在上面的 id 列表上：行是另一次查询的结果，
        // 过滤 id 却放过行，等于没过滤（改的时候被单测当场抓到过一次）。
        Set<Long> excluded = porkMaterialSoldIds(
            products.stream().map(ProductInfo::getId).filter(Objects::nonNull).collect(Collectors.toList()));
        List<StoreReturnUnitCandidateVo> result = new ArrayList<>(products.size());
        for (ProductInfo p : products) {
            if (excluded.contains(p.getId())) {
                log.info("[STORE-RETURN] 单位退回候选剔除「原材料售卖」的猪肉生产产品 productId={} name={}（V6 row226）",
                    p.getId(), p.getProductName());
                continue;
            }
            // 礼盒即便被配进清单也退不进仓库（多种原料组合，拆不回单一原材料，提交时硬拒）。
            // 列出来就是让用户白填一遍再吃 400，候选侧直接剔掉 —— 与提交闸同一条规则的两半。
            if (BELONG_TYPE_GIFT_BOX.equals(p.getBelongType())) {
                continue;
            }
            String metricUnit = metricUnits.get(p.getId());
            StoreReturnUnitCandidateVo vo = new StoreReturnUnitCandidateVo();
            vo.setProductId(p.getId());
            vo.setProductCode(p.getProductId());
            vo.setProductName(p.getProductName());
            vo.setProductSpec(p.getProductSpec());
            vo.setBelongType(p.getBelongType());
            vo.setProductUnit(p.getProductUnit());
            vo.setMaterialUnit(metricUnit);
            vo.setMaterialNum(materialRatio(p));
            vo.setInReturnList(p.getId() != null && returnListIds.contains(p.getId()));
            vo.setCanInbound(canInbound(p));
            vo.setCanConvert(canConvert(p, metricUnit));
            LocationChoice choice = resolveLocationChoice(p, productById, enabledLocations);
            vo.setDefaultLocationId(choice.defaultLocationId());
            vo.setLocationOptions(choice.options());
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int createUnitReturns(StoreReturnUnitBo bo) {
        // 退回单位必须来自「退回单位配置」字典（值取自出库去向）。字典为空时不拦 ——
        // 客户还没配的时候不能让整个功能不可用（迁移已拷了一份初始示例，正常不会为空）。
        //
        // ⚠️ `getAllDictByDictType` 返的是 **dictValue → dictLabel**，所以成员资格判 **keySet**。
        // 判 values() 等于拿中文 label 去比前端传来的 value，永远不命中 —— 字典一配就整条路走不通
        // （实测：字典里有「矿山E2E示例=mine_e2e」，传 mine_e2e 被拒）。
        Map<String, String> unitDict = dictService.getAllDictByDictType(DICT_RETURN_UNIT);
        if (unitDict != null && !unitDict.isEmpty() && !unitDict.containsKey(bo.getReturnUnit())) {
            throw new ServiceException("退回单位「" + bo.getReturnUnit()
                + "」不在「退回单位配置」字典里，请先在 admin 字典管理 → 退回单位配置 里配置", 400);
        }
        Long operatorId = LoginHelper.getUserId();
        LocalDateTime operateTime = LocalDateTime.now();
        // 退回日期只到天（甲方填的是日期），时刻部分归零：同一天多次新增仍是同一张单、同一分组。
        LocalDateTime returnDate = bo.getReturnDate().atStartOfDay();
        Set<Long> allowedIds = returnListAllowedIds();
        int created = 0;
        for (StoreReturnUnitBo.Item item : bo.getItems()) {
            ProductInfo product = productInfoMapper.selectById(item.getProductId());
            if (product == null) {
                throw new ServiceException("产品不存在或已删除：" + item.getProductId(), 404);
            }
            assertReturnable(product);
            // 与门店退回同一道闸：不在「退回产品清单」里的产品一律拒绝（防凭空造仓库库存）
            assertInReturnProductList(product, allowedIds);
            BigDecimal qty = item.getReturnQuantity();
            if (qty == null || qty.signum() <= 0) {
                throw new ServiceException("产品「" + product.getProductName() + "」的退回量必须大于 0", 400);
            }
            boolean discard = DISCARD_YES.equals(item.getIsDiscard());
            Long inboundProductId = null;
            Long locationId = null;
            if (!discard) {
                if (!canInbound(product)) {
                    throw new ServiceException("产品「" + product.getProductName()
                        + "」未配置原材料，无法退回入库，只能标记为产品丢弃", 400);
                }
                String metricUnit = resolveMetricUnit(product);
                if (!canConvert(product, metricUnit)) {
                    throw new ServiceException("产品「" + product.getProductName() + "」的退回单位("
                        + product.getProductUnit() + ")与原材料单位(" + metricUnit
                        + ")不同但未配「计量规则」，无法换算入库量；请先在产品档案补配，或把该行改为产品丢弃", 400);
                }
                inboundProductId = resolveInboundProductId(product.getId());
                locationId = item.getLocationId() != null ? item.getLocationId() : resolveDefaultLocation(inboundProductId);
                if (locationId == null) {
                    throw new ServiceException("产品「" + product.getProductName()
                        + "」未指定入库库位且无法自动定位（无预设库位 / 无历史库存），请选择库位后再确认", 400);
                }
            }
            // 已处理量：与 mp 确认页 / admin「退回处理」同一套换算（退回单位量 × 计量规则 → 原材料量），
            // 否则同一批货在三条路径下会写出三个不同的 received_weight。
            BigDecimal materialQty = toConfirmWeight(product, qty);
            StoreReturn entity = new StoreReturn();
            entity.setReturnNo(generateReturnNo());
            // 方向沿用 store_to_warehouse：退回到仓库是同一件事，仓库侧退货记录 / 汇总据此可见。
            entity.setReturnDirection(DIRECTION_STORE_TO_WAREHOUSE);
            entity.setReturnType(RETURN_TYPE_UNIT);
            entity.setReturnUnit(bo.getReturnUnit());
            // 🔴 单位退回**没有门店**：store_id 恒 null，绝不复用（会污染门店维度所有统计）。
            entity.setProductId(product.getId());
            entity.setLocationId(locationId);
            entity.setReturnQuantity(qty);
            // kg 产品的「报退货物重量」取退回量本身（与门店退回 row15 的派生口径一致）；
            // 非 kg 产品是份数，与 kg 不同量纲，落 0（旧行为同）。
            entity.setGoodsWeight(isKgUnit(product.getProductUnit()) ? qty : BigDecimal.ZERO);
            entity.setReturnDate(returnDate);
            entity.setOperatorId(operatorId);
            // 甲方「退回状态默认为已处理」：建单即两态终态，四个人员时间列全部填当前操作人与当前时刻。
            entity.setReturnStatus(STATUS_RECEIVED);
            entity.setReceivedQty(materialQty);
            entity.setReceivedWeight(materialQty);
            entity.setIsDiscard(discard ? DISCARD_YES : DISCARD_NO);
            entity.setConfirmUserId(operatorId);
            entity.setConfirmTime(operateTime);
            entity.setRemark("单位退回：" + bo.getReturnUnit());
            baseMapper.insert(entity);
            if (!discard) {
                // 未丢弃的行写入库，入库方式 store_return_in「门店退回」（甲方第 5 条）。
                purchaseInService.inboundReturnBasket(inboundProductId, locationId, materialQty,
                    FLOW_TYPE_RETURN_IN, returnInboundRemark("单位退回入库", entity.getReturnNo(), product));
            }
            created++;
        }
        log.info("[STR-RETURN-OPS-001] createUnitReturns unit={} date={} 行数={} → 直接 received（未丢弃行已入库）",
            bo.getReturnUnit(), bo.getReturnDate(), created);
        return created;
    }

    // ---------- private helpers ----------

    /**
     * 入库库位候选（默认值 + 下拉选项），STR-RETURN-OPS-001。
     *
     * <p>规则（甲方 row213 第 4/5 条）：</p>
     * <ol>
     *   <li><b>猪肉产品</b>（{@code belong_type='pork'}，白条不分流——Kevin 口径）：下拉固定
     *       「猪肉鲜品库 + 冻品库」两个启用库位，默认取产品存储库位（在这两库里时）否则第一个；</li>
     *   <li>其余产品：下拉 = 入库产品的 {@code store_location_id} 配置启用库位（保序），
     *       默认取其中第一个 / 命中「产品预设 → 库存最多」兜底的那个；</li>
     *   <li>配置为空 → 下拉回落全部启用库位（不能让下拉是空的、整行卡死），默认仍走
     *       {@link #resolveDefaultLocation}（产品预设 → 库存最多库位 → null）。</li>
     * </ol>
     */
    private LocationChoice resolveLocationChoice(ProductInfo product, Map<Long, ProductInfo> productById,
                                                 Map<Long, LocationPickerVo> enabledLocations) {
        List<LocationPickerVo> all = new ArrayList<>(enabledLocations.values());
        if (product == null) {
            return new LocationChoice(all.isEmpty() ? null : all.get(0).getId(), all);
        }
        // 入库落在原材料头上（果蔬成品 / 猪肉成品都折算），库位默认也跟着原材料走，
        // 与 confirm() 里 resolveDefaultLocation(inboundProductId) 完全同源。
        ProductInfo inboundProduct = product;
        if (product.getProductMaterial() != null) {
            ProductInfo material = productById.get(product.getProductMaterial());
            if (material != null) {
                inboundProduct = material;
            }
        }
        Long stockMost = resolveDefaultLocation(inboundProduct.getId());
        if (isPorkProduct(product)) {
            List<LocationPickerVo> pork = new ArrayList<>(2);
            for (String name : List.of(PORK_FRESH_LOCATION_NAME, FROZEN_LOCATION_NAME)) {
                enabledLocations.values().stream()
                    .filter(l -> name.equals(l.getLocationName()))
                    .findFirst().ifPresent(pork::add);
            }
            List<LocationPickerVo> options = pork.isEmpty() ? all : pork;
            Long def = options.stream().map(LocationPickerVo::getId)
                .filter(id -> id.equals(stockMost)).findFirst().orElse(null);
            if (def == null && !options.isEmpty()) {
                def = options.get(0).getId();
            }
            if (def == null) {
                def = stockMost;
            }
            return new LocationChoice(def, options);
        }
        List<LocationPickerVo> configured = configuredLocations(inboundProduct, enabledLocations);
        if (!configured.isEmpty()) {
            Long def = configured.stream().map(LocationPickerVo::getId)
                .filter(id -> id.equals(stockMost)).findFirst()
                .orElse(configured.get(0).getId());
            return new LocationChoice(def, configured);
        }
        Long def = stockMost != null ? stockMost : (all.isEmpty() ? null : all.get(0).getId());
        return new LocationChoice(def, all);
    }

    /** 产品 {@code store_location_id}（逗号分隔）里仍然启用的库位，保持配置顺序。 */
    private static List<LocationPickerVo> configuredLocations(ProductInfo product,
                                                              Map<Long, LocationPickerVo> enabledLocations) {
        if (product == null || StringUtils.isBlank(product.getStoreLocationId())) {
            return List.of();
        }
        List<LocationPickerVo> result = new ArrayList<>();
        for (String token : product.getStoreLocationId().split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Long id;
            try {
                id = Long.valueOf(trimmed);
            } catch (NumberFormatException ex) {
                continue;
            }
            LocationPickerVo vo = enabledLocations.get(id);
            if (vo != null) {
                result.add(vo);
            }
        }
        return result;
    }

    /** 全部启用库位（id → picker VO，按 location_sort 升序 / id 倒序，与库位一览页同口径）。 */
    private Map<Long, LocationPickerVo> allEnabledLocationMap() {
        return locationInfoMapper.selectList(new LambdaQueryWrapper<LocationInfo>()
                .eq(LocationInfo::getLocationStatus, LOCATION_STATUS_ENABLED)
                .orderByAsc(LocationInfo::getLocationSort)
                .orderByDesc(LocationInfo::getId))
            .stream()
            .map(l -> {
                LocationPickerVo vo = new LocationPickerVo();
                vo.setId(l.getId());
                vo.setLocationCode(l.getLocationCode());
                vo.setLocationName(l.getLocationName());
                vo.setLocationType(l.getLocationType());
                vo.setLocationSort(l.getLocationSort());
                return vo;
            })
            .collect(Collectors.toMap(LocationPickerVo::getId, v -> v, (a, b) -> a, LinkedHashMap::new));
    }

    /** 库位候选结果：默认值 + 下拉选项（record 只为把两个值一起从 helper 里带出来）。 */
    private record LocationChoice(Long defaultLocationId, List<LocationPickerVo> options) {
    }

    /** 是否猪肉产品（{@code belong_type='pork'}）——仅 pork 走鲜/冻库分流（白条不分流，Kevin 口径）。 */
    private static boolean isPorkProduct(ProductInfo product) {
        return product != null && BELONG_TYPE_PORK.equals(product.getBelongType());
    }

    /**
     * 退回单位量 → 原材料量（admin 侧 {@code metric.ts#toConfirmWeight} 的 Java 等价物）。
     *
     * <p>mp 确认页界面按**退回单位**录，提交前乘 {@code material_num} 换算回原材料量再落
     * {@code received_weight}。admin 抽屉 / 单位退回新增必须用同一套换算，否则同一张单
     * admin 处理与 mp 处理会写出两个数（验收 §3 第 5 条）。</p>
     *
     * <p>kg 行 {@code material_num} 恒为 1（退回单位就是原材料单位），乘完等于原值；结果保留 3 位
     * （后端列是 DECIMAL(12,3)，不裁会被 MySQL 静默四舍五入）。</p>
     */
    static BigDecimal toConfirmWeight(ProductInfo product, BigDecimal input) {
        if (input == null) {
            return null;
        }
        if (product == null || isKgUnit(product.getProductUnit())) {
            return input;
        }
        return input.multiply(materialRatio(product)).setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * 该行能不能做单位换算（admin 侧 {@code metric.ts#canConvert} 的 Java 等价物）。
     *
     * <p>需要换算（原材料单位与退回单位不同）却没配 {@code material_num} 时返 false ——
     * 这时任何取值都是瞎猜：按 1 折算会把「3 只」原样记成「3 kg」进库存。
     * 调用方据此锁行 / 拒绝提交，而不是静默记一个错数。</p>
     */
    static boolean canConvert(ProductInfo product, String materialUnit) {
        if (product == null) {
            return false;
        }
        if (isKgUnit(product.getProductUnit())) {
            return true;
        }
        String metric = materialUnit == null ? "" : materialUnit.trim();
        String own = product.getProductUnit() == null ? "" : product.getProductUnit().trim();
        if (metric.isEmpty() || metric.equalsIgnoreCase(own)) {
            return true;
        }
        BigDecimal ratio = product.getMaterialNum();
        return ratio != null && ratio.signum() > 0;
    }

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
     * row178：退回产品准入闸——礼盒（{@code belong_type=gift_box}）不收。
     *
     * <p>礼盒是多种原料的组合装，退回入库要拆回哪些原材料、各多少，单值 {@code product_material}
     * 表达不了；不拦的话工人选得到、提交得成功，直到仓库确认那步才抛 400，门店端已经以为退完了。
     * 选品接口（{@link #listPorkCandidates} / {@link #listVegCandidates}）不出礼盒是体验，
     * 这里才是把关（mp / 三方可直接 POST 任意 productId）。</p>
     *
     * <p><b>不按方向区分</b>：{@link #insertByBo} 对所有方向（含 {@code customer_to_store}）都无条件调
     * {@code inboundReturnBasket} 真写 {@code location_stock} + {@code stock_flow}，礼盒走哪个方向
     * 都会把错账写进仓库库存。故这里一律拦。</p>
     *
     * @param product 退回产品（非空）
     */
    private void assertReturnable(ProductInfo product) {
        if (BELONG_TYPE_GIFT_BOX.equals(product.getBelongType())) {
            throw new ServiceException("礼盒「" + product.getProductName()
                + "」由多种原料组合而成，无法按单一原材料退回入库，暂不支持退回", 400);
        }
    }

    /**
     * row178：退回入库流水备注带上「退回的那个成品名」。
     *
     * <p>退回入库记在原材料名下（{@code resolveInboundProductId} 折算），流水行本身没有任何字段
     * 指回成品；一个原材料常被 2-6 个规格成品共享（如「猪脚」对 500g/750g/1000g 三个规格），
     * 只靠 {@code product_id} 无法回答「我退的那个规格入库了没」。把成品名写进备注，
     * 入库记录按成品名搜时就能精确定位到这一笔（见 {@code StockFlowServiceImpl.buildWrapper}）。</p>
     *
     * @param action   动作描述（如「门店退回入库」）
     * @param returnNo 退回单号
     * @param product  退回的成品（可空 → 只写单号）
     */
    private String returnInboundRemark(String action, String returnNo, ProductInfo product) {
        String base = action + "：" + returnNo;
        return product == null || StringUtils.isBlank(product.getProductName())
            ? base : base + " 退回产品：" + product.getProductName();
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

    /** 退回记录猪肉 tab 业态（与前端 ReturnRecordList 同口径：pork/white_bar 归猪肉）。 */
    private static final List<String> PORK_BELONG_TYPES = List.of(BELONG_TYPE_PORK, BELONG_TYPE_WHITE_BAR);

    /** 退回记录果蔬 tab 业态（row10 起<b>只认 vegetable</b>，不再是「非猪肉即果蔬」）。 */
    private static final List<String> VEG_BELONG_TYPES = List.of(BELONG_TYPE_VEGETABLE);

    /**
     * tab 白名单下推：产品集非空 → {@code IN}；<b>空 → 恒假条件返回空页</b>（不能不加条件，
     * 那会退化成「不过滤 = 全量」，猪肉 tab 里冒出全部果蔬）。
     */
    private void applyProductIdWhitelist(LambdaQueryWrapper<StoreReturn> w, List<Long> productIds) {
        if (productIds.isEmpty()) {
            w.eq(StoreReturn::getId, -1L);
        } else {
            w.in(StoreReturn::getProductId, productIds);
        }
    }

    /**
     * 按业态白名单取产品 id 集（tab 下推过滤用）。
     */
    private List<Long> productIdsByBelongTypes(Collection<String> belongTypes) {
        return productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .select(ProductInfo::getId)
                .in(ProductInfo::getBelongType, belongTypes))
            .stream().map(ProductInfo::getId).filter(Objects::nonNull).toList();
    }

    private LambdaQueryWrapper<StoreReturn> buildQueryWrapper(StoreReturnQuery q) {
        LambdaQueryWrapper<StoreReturn> w = new LambdaQueryWrapper<>();
        if (q == null) {
            return w.orderByDesc(StoreReturn::getReturnDate).orderByDesc(StoreReturn::getId);
        }
        boolean hasStoreIds = q.getStoreIds() != null && !q.getStoreIds().isEmpty();
        boolean hasProductIds = q.getProductIds() != null && !q.getProductIds().isEmpty();
        // 按门店筛 ⇒ 只看门店退回。单位退回按定义没有门店，本来指望「store_id 为 NULL 所以天然不匹配」，
        // 但实测有历史脏数据是反例（RET202609150001：return_type='unit' 却带着 store_id），
        // 于是按「门店AC」筛会筛出一行「退回门店」列写着退回单位名的记录 —— 正是 row222 要消灭的那类对不上。
        // 判据显式化，不再依赖数据恰好为 NULL。return_type 为空的历史行按门店退回算（那时还没有单位退回）。
        boolean hasStoreFilter = hasStoreIds || q.getStoreId() != null;
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
        // row10：业态 tab 下推改**三值**（此前只有猪肉/果蔬两值，果蔬 = NOT IN 猪肉 → 干货/蛋类/礼盒/其他
        // 的退回全被塞进「果蔬产品」tab，用户在猪肉里找不到、在果蔬里也认不出，看起来像「没写进记录」）。
        //   · pork      = IN 猪肉产品集（pork / white_bar）
        //   · vegetable = IN 果蔬产品集（只认 vegetable，不再兜底收其余）
        //   · other     = NOT IN (猪肉 ∪ 果蔬)，即**其余全部**（dry_good / egg / gift_box / other /
        //                 belong_type 为空 / 产品已删）——用「其余全部」而不是白名单，保证任何一条记录
        //                 都能在某个 tab 里被看到，不会有记录彻底消失。
        //     （t_store_return.product_id 是 NOT NULL，NOT IN 不会因 NULL 语义漏行。）
        if (StringUtils.isNotBlank(q.getBelongCategory())) {
            String tab = q.getBelongCategory();
            if ("pork".equals(tab)) {
                applyProductIdWhitelist(w, productIdsByBelongTypes(PORK_BELONG_TYPES));
            } else if ("vegetable".equals(tab)) {
                applyProductIdWhitelist(w, productIdsByBelongTypes(VEG_BELONG_TYPES));
            } else if ("other".equals(tab)) {
                List<String> classified = Stream.concat(PORK_BELONG_TYPES.stream(), VEG_BELONG_TYPES.stream())
                    .toList();
                List<Long> classifiedIds = productIdsByBelongTypes(classified);
                // 一个已分类产品都没有 → 全部记录都属「其他」，不加条件
                if (!classifiedIds.isEmpty()) {
                    w.notIn(StoreReturn::getProductId, classifiedIds);
                }
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
            // STR-RETURN-OPS-001：退回类型 / 退回单位两维下推。
            .eq(StringUtils.isNotBlank(q.getReturnType()), StoreReturn::getReturnType, q.getReturnType())
            .eq(StringUtils.isNotBlank(q.getReturnUnit()), StoreReturn::getReturnUnit, q.getReturnUnit())
            .and(hasStoreFilter, w2 -> w2.isNull(StoreReturn::getReturnType)
                .or().ne(StoreReturn::getReturnType, RETURN_TYPE_UNIT))
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
        // row14：「仓库实收量」的计量口径单位（原材料单位，缺省回落产品单位）。一次 IN 批量解析，不逐行查库。
        Map<Long, String> metricUnits = resolveMetricUnits(new ArrayList<>(products.values()));
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
                    vo.setMaterialUnit(metricUnits.get(p.getId()));
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
