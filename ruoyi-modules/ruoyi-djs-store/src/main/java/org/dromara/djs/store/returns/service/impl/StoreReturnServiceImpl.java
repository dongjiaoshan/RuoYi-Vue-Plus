package org.dromara.djs.store.returns.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
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
import org.dromara.djs.store.returns.domain.query.StoreReturnQuery;
import org.dromara.djs.store.returns.domain.vo.StoreReturnVo;
import org.dromara.djs.store.returns.mapper.StoreReturnMapper;
import org.dromara.djs.store.returns.service.IStoreReturnService;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.purchase.service.IWarehousePurchaseInService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** 门店主场景默认方向（K4 简化后只此一态）。 */
    private static final String DIRECTION_CUSTOMER_TO_STORE = "customer_to_store";

    /** 退回入库流水类型 djs_flow_type（与材料退回 / 仓库退货一致，不污染采购入库列表）。 */
    private static final String FLOW_TYPE_RETURN_IN = "return_in";

    /** 退货状态 djs_store_return_status：待仓库确认。 */
    private static final String STATUS_PENDING = "pending";

    /** 退货状态 djs_store_return_status：已入库（仓库确认实收后）。 */
    private static final String STATUS_RECEIVED = "received";

    private final StoreMapper storeMapper;
    private final ProductInfoMapper productInfoMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final IWarehousePurchaseInService purchaseInService;

    public StoreReturnServiceImpl(StoreReturnMapper baseMapper,
                                  StoreMapper storeMapper,
                                  ProductInfoMapper productInfoMapper,
                                  LocationInfoMapper locationInfoMapper,
                                  IBizCodeGenerator bizCodeGenerator,
                                  IWarehousePurchaseInService purchaseInService) {
        super(baseMapper);
        this.storeMapper = storeMapper;
        this.productInfoMapper = productInfoMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.purchaseInService = purchaseInService;
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
        purchaseInService.inbound(bo.getProductId(), bo.getLocationId(), bo.getReturnQuantity(),
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
        int created = 0;
        for (StoreReturnBatchBo.Item item : bo.getItems()) {
            ProductInfo product = productInfoMapper.selectById(item.getProductId());
            if (product == null) {
                throw new ServiceException("产品不存在或已删除：" + item.getProductId(), 404);
            }
            StoreReturn entity = new StoreReturn();
            entity.setReturnNo(generateReturnNo());
            entity.setReturnDirection(DIRECTION_CUSTOMER_TO_STORE);
            entity.setStoreId(bo.getStoreId());
            entity.setProductId(item.getProductId());
            // 退回操作录入的是「退回产品重量(KG)」→ 同时落 goods_weight 与 return_quantity（V1 按重量计量）
            entity.setReturnQuantity(item.getReturnWeight());
            entity.setGoodsWeight(item.getReturnWeight());
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
    @Transactional(rollbackFor = Exception.class)
    public int confirm(StoreReturnConfirmBo bo) {
        StoreReturn existing = baseMapper.selectById(bo.getId());
        if (existing == null) {
            throw new ServiceException("退回记录不存在：" + bo.getId(), 404);
        }
        if (STATUS_RECEIVED.equals(existing.getReturnStatus())) {
            throw new ServiceException("该退回记录已确认入库，请勿重复确认", 400);
        }

        StoreReturn entity = new StoreReturn();
        entity.setId(bo.getId());
        entity.setLocationId(bo.getLocationId());
        entity.setReceivedQty(bo.getReceivedQty());
        // 实收重量缺省按实收量计（V1：果蔬/猪肉退回多按重量计量）
        entity.setReceivedWeight(bo.getReceivedWeight() == null ? bo.getReceivedQty() : bo.getReceivedWeight());
        entity.setConfirmUserId(LoginHelper.getUserId());
        entity.setConfirmTime(LocalDateTime.now());
        entity.setReturnStatus(STATUS_RECEIVED);
        int rows = baseMapper.updateById(entity);

        // 确认实收时才联动外购入库：同事务 UPSERT location_stock + stock_flow(return_in)，
        // inbound 内部校验库位 / 数量，失败抛 → 整体回滚（确认与入库一致，不留半态）。
        purchaseInService.inbound(existing.getProductId(), bo.getLocationId(), bo.getReceivedQty(),
            FLOW_TYPE_RETURN_IN, "门店退回仓库确认入库：" + existing.getReturnNo());

        log.info("[STORE-RETURN-REALIGN-001] confirm id={} no={} location={} receivedQty={} → received 联动入库",
            bo.getId(), existing.getReturnNo(), bo.getLocationId(), bo.getReceivedQty());
        return rows;
    }

    @Override
    public int deleteByIds(Collection<Long> ids) {
        // 走 DjsBaseServiceImpl#softDelete
        return softDelete(ids);
    }

    // ---------- private helpers ----------

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
