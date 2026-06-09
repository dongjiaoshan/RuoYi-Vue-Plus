package org.dromara.djs.warehouse.vegdock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.vegdock.domain.VegPurchase;
import org.dromara.djs.warehouse.vegdock.domain.bo.VegPurchaseInboundBo;
import org.dromara.djs.warehouse.vegdock.domain.bo.VegPurchaseReceiveBo;
import org.dromara.djs.warehouse.vegdock.domain.vo.VegPurchaseCropGroupVo;
import org.dromara.djs.warehouse.vegdock.domain.vo.VegPurchaseVo;
import org.dromara.djs.warehouse.vegdock.mapper.VegPurchaseMapper;
import org.dromara.djs.warehouse.vegdock.service.IVegPurchaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 外购果蔬月台收货 + 果蔬间入库 Service 实现（FIX-WMS-MP-VEGDOCK-001）。
 *
 * <h3>两个写入入口</h3>
 * <ol>
 *   <li>{@link #receive} —— 外购收货登记：单 INSERT 一行 {@code t_warehouse_veg_purchase}
 *       （status=pending，pendingWeight=arrivalWeight，actualWeight=0），不动库存。</li>
 *   <li>{@link #inbound} —— 果蔬间入库确认：单 {@code @Transactional} 跨 3 步：
 *     <ol>
 *       <li>校验记录存在 + 未入完 + 本次量 ≤ 待入库量；UPDATE 记录（累加 actual / 递减 pending /
 *           推进 status：pending→processing→done）</li>
 *       <li>UPSERT location_stock（先原子 add，affected=0 → INSERT 新行；与 PurchaseInServiceImpl 同范式）</li>
 *       <li>INSERT stock_flow（flow_type={@code veg_purchase_in}, inout_type=IN）</li>
 *     </ol>
 *   </li>
 * </ol>
 *
 * <p>独立于自产 {@code VegetableHandleServiceImpl}：本 service 只承载「外购」一类，stock_flow 用专属
 * {@code flow_type=veg_purchase_in} 区分，不与自产 {@code veg_stock_in} 混算（DoD：入库后库存增、与处理链不重复计）。</p>
 *
 * <h3>productId 兜底</h3>
 * <p>外购果蔬可能无 product 主数据（V1 蔬菜业态产品单一）。优先用 bo/记录上的 productId；为空则按 cropId
 * 名称匹配 product；仍空 → 0 + WARN（不影响 admin 列表，仅 product 维度聚合会漏，与 VegetableHandle 同处理）。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-VEGDOCK-001
 */
@Slf4j
@Service
public class VegPurchaseServiceImpl implements IVegPurchaseService {

    /** djs_veg_purchase_status 取值。 */
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_DONE = "done";

    /** stock_flow.flow_type 外购果蔬入库（djs_flow_type 新增）。 */
    private static final String FLOW_TYPE_VEG_PURCHASE_IN = "veg_purchase_in";

    /** stock_flow.inout_type CHAR(3) IN=入库。 */
    private static final String INOUT_IN = "IN";

    private final VegPurchaseMapper baseMapper;
    private final StockFlowMapper stockFlowMapper;
    private final LocationStockMapper locationStockMapper;
    private final ProductInfoMapper productInfoMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;

    public VegPurchaseServiceImpl(VegPurchaseMapper baseMapper,
                                  StockFlowMapper stockFlowMapper,
                                  LocationStockMapper locationStockMapper,
                                  ProductInfoMapper productInfoMapper,
                                  LocationInfoMapper locationInfoMapper,
                                  IBizCodeGenerator bizCodeGenerator) {
        this.baseMapper = baseMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.locationStockMapper = locationStockMapper;
        this.productInfoMapper = productInfoMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long receive(VegPurchaseReceiveBo bo) {
        if (bo.getArrivalWeight() == null || bo.getArrivalWeight().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("到货重量必须大于 0");
        }
        Long userId = LoginHelper.getUserId();
        Date now = new Date();

        VegPurchase entity = new VegPurchase();
        entity.setCropId(bo.getCropId());
        entity.setCropName(bo.getCropName());
        entity.setProductId(bo.getProductId());
        entity.setSource(bo.getSource());
        entity.setSupplierCode(bo.getSupplierCode());
        entity.setSupplierName(bo.getSupplierName());
        entity.setArrivalWeight(bo.getArrivalWeight());
        entity.setPendingWeight(bo.getArrivalWeight());
        entity.setActualWeight(BigDecimal.ZERO);
        entity.setArrivalTime(bo.getArrivalTime() == null ? now : bo.getArrivalTime());
        entity.setStatus(STATUS_PENDING);
        entity.setProofOssIds(bo.getProofOssIds());
        entity.setRemark(bo.getRemark());
        // tenant_id / create_by / create_time / create_dept / update_* / del_flag 走 DjsMetaObjectHandler.insertFill
        baseMapper.insert(entity);
        log.info("外购果蔬收货登记 id={} cropId={} arrival={} user={}",
            entity.getId(), bo.getCropId(), bo.getArrivalWeight(), userId);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long inbound(VegPurchaseInboundBo bo) {
        BigDecimal weight = bo.getActualWeight();
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("实际入库量必须大于 0");
        }
        Long userId = LoginHelper.getUserId();
        Date now = new Date();

        // Step 1：校验记录 + 待入库量
        VegPurchase record = baseMapper.selectById(bo.getId());
        if (record == null) {
            throw new ServiceException("外购到货记录不存在：" + bo.getId());
        }
        if (STATUS_DONE.equals(record.getStatus())) {
            throw new ServiceException("该到货记录已入库完成，不能再入库");
        }
        BigDecimal pending = nullSafe(record.getPendingWeight());
        if (weight.compareTo(pending) > 0) {
            throw new ServiceException("实际入库量(" + weight + ") 超过待入库量(" + pending + ")");
        }
        LocationInfo location = locationInfoMapper.selectById(bo.getLocationId());
        if (location == null) {
            throw new ServiceException("入库库位不存在：" + bo.getLocationId());
        }

        // Step 2：UPDATE 记录（累加 actual / 递减 pending / 推进 status）
        BigDecimal newActual = nullSafe(record.getActualWeight()).add(weight);
        BigDecimal newPending = pending.subtract(weight);
        VegPurchase delta = new VegPurchase();
        delta.setId(record.getId());
        delta.setActualWeight(newActual);
        delta.setPendingWeight(newPending);
        delta.setLocationId(bo.getLocationId());
        delta.setStatus(newPending.compareTo(BigDecimal.ZERO) <= 0 ? STATUS_DONE : STATUS_PROCESSING);
        baseMapper.updateById(delta);

        // Step 3：UPSERT location_stock + INSERT stock_flow
        Long productId = resolveProductId(record);
        upsertLocationStock(bo.getLocationId(), productId, record, weight, userId);
        StockFlow flow = new StockFlow();
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", INOUT_IN);
        flow.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx));
        flow.setFlowDate(now);
        flow.setProductId(productId);
        flow.setWarehouseId(bo.getLocationId());
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(FLOW_TYPE_VEG_PURCHASE_IN);
        flow.setChangeNum(weight);
        flow.setChangeQuantity(weight);
        flow.setOperatorId(userId);
        flow.setRemark("外购果蔬入库 vegPurchaseId=" + record.getId() + " crop=" + record.getCropName());
        stockFlowMapper.insert(flow);

        return flow.getId();
    }

    /**
     * UPSERT location_stock（先原子 add，不存在则 INSERT 新行）。与 PurchaseInServiceImpl 同范式。
     */
    private void upsertLocationStock(Long locationId, Long productId, VegPurchase record,
                                     BigDecimal weight, Long userId) {
        int updated = locationStockMapper.addByProductLocation(locationId, productId, weight, userId);
        if (updated == 0) {
            LocationStock fresh = new LocationStock();
            fresh.setLocationId(locationId);
            fresh.setProductId(productId);
            fresh.setProductName(record.getCropName());
            fresh.setProductUnit("kg");
            fresh.setProductStock(weight);
            fresh.setIsEnd(0);
            fresh.setOperatorId(userId);
            // tenant_id / create_* / update_* / del_flag 走 DjsMetaObjectHandler.insertFill
            locationStockMapper.insert(fresh);
        }
    }

    /**
     * 解析 productId：优先记录上的 productId；为空则按 cropName 匹配蔬菜产品；仍空 → 0 + WARN。
     *
     * <p>protected 方便单测覆盖。</p>
     */
    protected Long resolveProductId(VegPurchase record) {
        if (record.getProductId() != null) {
            return record.getProductId();
        }
        if (record.getCropName() != null && !record.getCropName().isBlank()) {
            ProductInfo p = productInfoMapper.selectOne(
                new LambdaQueryWrapper<ProductInfo>()
                    .eq(ProductInfo::getProductName, record.getCropName())
                    .last("LIMIT 1"));
            if (p != null) {
                return p.getId();
            }
        }
        log.warn("外购果蔬 stock_flow.product_id 兜底为 0 — vegPurchaseId={} cropId={} crop={}",
            record.getId(), record.getCropId(), record.getCropName());
        return 0L;
    }

    @Override
    public List<VegPurchaseCropGroupVo> listCropGroups() {
        return baseMapper.selectCropGroups();
    }

    @Override
    public List<VegPurchaseVo> listByCrop(Long cropId) {
        if (cropId == null) {
            throw new ServiceException("缺少 cropId 参数");
        }
        return baseMapper.selectVoList(
            new LambdaQueryWrapper<VegPurchase>()
                .eq(VegPurchase::getCropId, cropId)
                .ne(VegPurchase::getStatus, STATUS_DONE)
                .orderByDesc(VegPurchase::getArrivalTime)
                .orderByDesc(VegPurchase::getId));
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

}
