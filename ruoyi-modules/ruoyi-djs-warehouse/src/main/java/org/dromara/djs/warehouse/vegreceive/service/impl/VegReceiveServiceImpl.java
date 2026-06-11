package org.dromara.djs.warehouse.vegreceive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.common.supplier.domain.Supplier;
import org.dromara.djs.common.supplier.mapper.SupplierMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.vegreceive.domain.VegReceive;
import org.dromara.djs.warehouse.vegreceive.domain.bo.VegInboundBo;
import org.dromara.djs.warehouse.vegreceive.domain.bo.VegPurchaseBo;
import org.dromara.djs.warehouse.vegreceive.domain.vo.VegInboundPlotVo;
import org.dromara.djs.warehouse.vegreceive.domain.vo.VegReceiveItemVo;
import org.dromara.djs.warehouse.vegreceive.mapper.VegReceiveMapper;
import org.dromara.djs.warehouse.vegreceive.service.IVegReceiveService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 果蔬月台收货 Service 实现（FIX-WMS-VEGRECEIVE-001）。
 *
 * <h3>跨表事务一致性（照 {@link org.dromara.djs.warehouse.flow.service.impl.PackingFlowServiceImpl} 范式）</h3>
 * <ul>
 *   <li>{@link #inbound} 自产：本表 INSERT（receiveType=1）+ {@code location_stock} 按 {@code plotId} 维度
 *       UPSERT（行锁增量 / 无行 INSERT）+ {@code stock_flow} INSERT（{@code veg_receive_in / IN}）。
 *       入库前用 {@link VegReceiveMapper#selectRemainInboundWeight} 校验剩余可入量，超量抛 {@link ServiceException}。</li>
 *   <li>{@link #purchase} 外购：resolve supplier（业务短码 → id+name）+ 本表 INSERT（receiveType=2）+
 *       {@code location_stock} 按 {@code productId} 维度 UPSERT（复用
 *       {@link LocationStockMapper#addByProductLocation}）+ {@code stock_flow} INSERT（{@code veg_purchase_in / IN}）。</li>
 *   <li>任一 RuntimeException / ServiceException 触发整体回滚（{@code @Transactional(rollbackFor = Exception.class)}）。</li>
 * </ul>
 *
 * <h3>库存增量行锁（防超扣 / 并发）</h3>
 * <p>plot 维度走 {@link VegReceiveMapper#addStockByPlotLocation}、product 维度走
 * {@link LocationStockMapper#addByProductLocation}；UPDATE 行锁 + {@code del_flag} 限定，affectedRows=0
 * 时兜底 INSERT 新库存行。入库是"加库存"无上限校验，超量约束在收货记录侧（剩余可入量）。</p>
 *
 * @author djs
 * @since FIX-WMS-VEGRECEIVE-001
 */
@Slf4j
@Service
public class VegReceiveServiceImpl implements IVegReceiveService {

    /**
     * 出入库方向：IN=入（DDL CHAR(3)；果蔬收货仅入库）。
     */
    private static final String INOUT_IN = "IN";

    /**
     * 收货类型 {@code djs_veg_receive_type}：1=自产 / 2=外购。
     */
    private static final int RECEIVE_TYPE_SELF = 1;
    private static final int RECEIVE_TYPE_PURCHASE = 2;

    /**
     * 收货状态 {@code djs_veg_receive_status}：自产标记入库完成时 done，否则 processing；外购单次入库恒 done。
     */
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_DONE = "done";

    /**
     * 是否入库完成 {@code djs_yes_no}：1=是 / 2=否。
     */
    private static final int FINISH_YES = 1;
    private static final int FINISH_NO = 2;

    /**
     * {@code djs_flow_type} 字典 value（V202606200900 DDL 已 seed）。
     */
    private static final String FLOW_VEG_RECEIVE_IN = "veg_receive_in";
    private static final String FLOW_VEG_PURCHASE_IN = "veg_purchase_in";

    /**
     * 果蔬缩略图 L2 兜底分类键（IMG-LIB-001；自产 / 外购果蔬统一蔬菜默认图）。
     */
    private static final String CROP_BELONG_TYPE = "vegetable";

    private final VegReceiveMapper vegReceiveMapper;
    private final LocationStockMapper locationStockMapper;
    private final StockFlowMapper stockFlowMapper;
    private final ProductInfoMapper productInfoMapper;
    private final SupplierMapper supplierMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final ImageUrlResolver imageUrlResolver;

    public VegReceiveServiceImpl(VegReceiveMapper vegReceiveMapper,
                                 LocationStockMapper locationStockMapper,
                                 StockFlowMapper stockFlowMapper,
                                 ProductInfoMapper productInfoMapper,
                                 SupplierMapper supplierMapper,
                                 IBizCodeGenerator bizCodeGenerator,
                                 ImageUrlResolver imageUrlResolver) {
        this.vegReceiveMapper = vegReceiveMapper;
        this.locationStockMapper = locationStockMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.productInfoMapper = productInfoMapper;
        this.supplierMapper = supplierMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.imageUrlResolver = imageUrlResolver;
    }

    @Override
    public List<VegReceiveItemVo> listSelf() {
        return fillThumb(vegReceiveMapper.selectSelfPending());
    }

    @Override
    public List<VegReceiveItemVo> listPurchased(String productName, String productType) {
        // productType V1 不参与过滤（外购果蔬恒「果蔬产品」文案，预留扩展），仅按产品名模糊
        return fillThumb(vegReceiveMapper.selectPurchasedPending(productName));
    }

    /**
     * 批量回填 thumbUrl（IMG-LIB-001：L1 作物/产品 image_oss_id → L2 蔬菜默认图 → L3 全局），禁 N+1。
     */
    private List<VegReceiveItemVo> fillThumb(List<VegReceiveItemVo> list) {
        if (list.isEmpty()) {
            return list;
        }
        List<ImageUrlResolver.Item> items = list.stream()
            .map(v -> new ImageUrlResolver.Item(v.getImageOssId(), CROP_BELONG_TYPE))
            .toList();
        List<String> urls = imageUrlResolver.resolveList(items);
        if (urls.size() == list.size()) {
            for (int i = 0; i < list.size(); i++) {
                list.get(i).setThumbUrl(urls.get(i));
            }
        }
        return list;
    }

    @Override
    public List<VegInboundPlotVo> listInboundPlots(Long cropId) {
        if (cropId == null) {
            throw new ServiceException("作物 ID 不能为空");
        }
        return vegReceiveMapper.selectInboundPlots(cropId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long inbound(VegInboundBo bo) {
        Long userId = resolveOperator(bo.getOperatorId());

        // 1. 校验剩余可入量（月台量 − 已入 self 量），超量拒绝（不凭空入库）
        BigDecimal remain = vegReceiveMapper.selectRemainInboundWeight(bo.getCropId(), bo.getPlotId());
        BigDecimal remainSafe = remain != null ? remain : BigDecimal.ZERO;
        if (bo.getWeight().compareTo(remainSafe) > 0) {
            throw new ServiceException(
                "入库重量超过该地块剩余可入量（剩余 " + remainSafe.stripTrailingZeros().toPlainString()
                    + "kg，本次 " + bo.getWeight().stripTrailingZeros().toPlainString() + "kg）");
        }

        boolean finished = bo.getIsFinish() != null && bo.getIsFinish() == FINISH_YES;
        String cropName = vegReceiveMapper.selectCropName(bo.getCropId());

        // 2. INSERT 收货记录（自产）
        String flowNo = generateFlowNo();
        VegReceive receive = new VegReceive();
        receive.setReceiveNo(flowNo);
        receive.setReceiveType(RECEIVE_TYPE_SELF);
        receive.setCropId(bo.getCropId());
        receive.setCropName(cropName);
        receive.setPlotId(bo.getPlotId());
        receive.setWeight(bo.getWeight());
        receive.setLocationId(bo.getLocationId());
        receive.setIsFinish(finished ? FINISH_YES : FINISH_NO);
        receive.setReceiveStatus(finished ? STATUS_DONE : STATUS_PROCESSING);
        receive.setOperatorId(userId);
        receive.setReceiveTime(new Date());
        vegReceiveMapper.insert(receive);

        // 3. UPSERT location_stock（plot 维度行锁增量；无行兜底 INSERT 建账）
        int affected = vegReceiveMapper.addStockByPlotLocation(
            bo.getLocationId(), bo.getPlotId(), bo.getWeight(), userId);
        if (affected == 0) {
            insertPlotStockRow(bo.getLocationId(), bo.getPlotId(), cropName, bo.getWeight(), userId);
        }

        // 4. INSERT stock_flow（veg_receive_in / IN，plot 关联）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(flowNo);
        flow.setFlowDate(new Date());
        flow.setWarehouseId(bo.getLocationId());
        flow.setPlotId(bo.getPlotId());
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(FLOW_VEG_RECEIVE_IN);
        flow.setChangeNum(bo.getWeight());
        flow.setChangeQuantity(bo.getWeight());
        flow.setOperatorId(userId);
        stockFlowMapper.insert(flow);

        return receive.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long purchase(VegPurchaseBo bo) {
        Long userId = resolveOperator(bo.getOperatorId());

        // 1. 校验外购产品（product_type=2）存在
        ProductInfo product = requirePurchaseProduct(bo.getCropId());

        // 2. resolve supplier（业务短码 → id+name；查不到只存名称、id 置 null，不阻塞入库）
        Supplier supplier = resolveSupplier(bo.getSupplier());

        // 3. INSERT 收货记录（外购）
        String flowNo = generateFlowNo();
        VegReceive receive = new VegReceive();
        receive.setReceiveNo(flowNo);
        receive.setReceiveType(RECEIVE_TYPE_PURCHASE);
        receive.setCropId(bo.getCropId());
        receive.setCropName(product.getProductName());
        receive.setProductId(bo.getCropId());
        receive.setWeight(bo.getWeight());
        receive.setSupplierId(supplier != null ? supplier.getId() : null);
        receive.setSupplierName(supplier != null ? supplier.getSupplierName() : bo.getSupplier());
        receive.setLocationId(bo.getLocationId());
        receive.setIsFinish(FINISH_NO);
        receive.setReceiveStatus(STATUS_DONE);
        receive.setOperatorId(userId);
        receive.setReceiveTime(new Date());
        vegReceiveMapper.insert(receive);

        // 4. UPSERT location_stock（product 维度行锁增量；无行兜底 INSERT 建账）
        int affected = locationStockMapper.addByProductLocation(
            bo.getLocationId(), bo.getCropId(), bo.getWeight(), userId);
        if (affected == 0) {
            insertProductStockRow(bo.getLocationId(), product, bo.getWeight(), userId);
        }

        // 5. INSERT stock_flow（veg_purchase_in / IN，product + supplier 关联）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(flowNo);
        flow.setFlowDate(new Date());
        flow.setProductId(bo.getCropId());
        flow.setWarehouseId(bo.getLocationId());
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(FLOW_VEG_PURCHASE_IN);
        flow.setChangeNum(bo.getWeight());
        flow.setChangeQuantity(bo.getWeight());
        flow.setSupplierId(supplier != null ? supplier.getId() : null);
        flow.setOperatorId(userId);
        stockFlowMapper.insert(flow);

        return receive.getId();
    }

    // ============================ 内部辅助 ============================

    /**
     * 解析操作人：bo 传了用传的；为空取当前登录用户（端点已 {@code @SaCheckLogin}）。
     *
     * <p>protected 便于单测 stub。</p>
     */
    protected Long resolveOperator(Long operatorId) {
        return operatorId != null ? operatorId : LoginHelper.getUserId();
    }

    /**
     * 校验外购果蔬产品（{@code product_type=2}）存在，否则抛异常。
     *
     * <p>protected 便于单测 stub。</p>
     */
    protected ProductInfo requirePurchaseProduct(Long productId) {
        ProductInfo p = productInfoMapper.selectOne(
            new LambdaQueryWrapper<ProductInfo>().eq(ProductInfo::getId, productId).last("LIMIT 1"));
        if (p == null) {
            throw new ServiceException("外购产品不存在或已删除：" + productId);
        }
        if (p.getProductType() == null || p.getProductType() != RECEIVE_TYPE_PURCHASE) {
            throw new ServiceException("该产品非外购类型，不能走外购收货入库：" + p.getProductName());
        }
        return p;
    }

    /**
     * 按业务短码 resolve 供应商；查不到 / 短码空返 null（service 容忍，只存名称串到 supplierName）。
     *
     * <p>protected 便于单测 stub。</p>
     */
    protected Supplier resolveSupplier(String supplierCode) {
        if (supplierCode == null || supplierCode.isBlank()) {
            return null;
        }
        return supplierMapper.selectOne(
            new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getSupplierCode, supplierCode)
                .last("LIMIT 1"));
    }

    /**
     * plot 维度无库存行时 INSERT 新行（自产果蔬首次入某库位 / 地块）。
     */
    private void insertPlotStockRow(Long locationId, Long plotId, String cropName,
                                    BigDecimal stockQty, Long userId) {
        LocationStock stock = new LocationStock();
        stock.setLocationId(locationId);
        stock.setPlotId(plotId);
        stock.setProductName(cropName);
        stock.setProductStock(stockQty);
        stock.setProductUnit("kg");
        stock.setIsEnd(0);
        stock.setOperatorId(userId);
        locationStockMapper.insert(stock);
    }

    /**
     * product 维度无库存行时 INSERT 新行（外购果蔬首次入某库位，照 PackingFlowServiceImpl 建账范式）。
     */
    private void insertProductStockRow(Long locationId, ProductInfo product,
                                       BigDecimal stockQty, Long userId) {
        LocationStock stock = new LocationStock();
        stock.setLocationId(locationId);
        stock.setProductId(product.getId());
        stock.setProductName(product.getProductName());
        stock.setProductStock(stockQty);
        stock.setProductUnit(product.getProductUnit());
        stock.setIsEnd(0);
        stock.setOperatorId(userId);
        locationStockMapper.insert(stock);
    }

    /**
     * 生成入库流水号（复用 SYS-INFRA-004 BizCodeService，果蔬收货恒入库 ioCode=IN）。
     *
     * <p>protected 便于单测固定返值。</p>
     */
    protected String generateFlowNo() {
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", INOUT_IN);
        return bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx);
    }

}
