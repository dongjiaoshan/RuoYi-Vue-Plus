package org.dromara.djs.warehouse.veg.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.veg.domain.FeedLog;
import org.dromara.djs.warehouse.veg.domain.HandleRecord;
import org.dromara.djs.warehouse.veg.domain.PlantingRecord;
import org.dromara.djs.warehouse.veg.domain.VegetableHandle;
import org.dromara.djs.warehouse.veg.domain.bo.HandleRecordSubmitBo;
import org.dromara.djs.warehouse.veg.domain.bo.HarvestSubmitBo;
import org.dromara.djs.warehouse.veg.domain.bo.ProcessSubmitBo;
import org.dromara.djs.warehouse.veg.domain.query.VegHandleQuery;
import org.dromara.djs.warehouse.veg.domain.vo.HandleRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.PendingPlantingRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegCropVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegPlotDetailVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegetableHandleVo;
import org.dromara.djs.warehouse.veg.mapper.FeedLogMapper;
import org.dromara.djs.warehouse.veg.mapper.HandleRecordMapper;
import org.dromara.djs.warehouse.veg.mapper.PlantingRecordMapper;
import org.dromara.djs.warehouse.veg.mapper.VegetableHandleMapper;
import org.dromara.djs.warehouse.veg.service.IVegetableHandleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 毛菜处理 Service 实现（WMS-VEG-001）。
 *
 * <h3>事务一致性</h3>
 * <p>{@link #submitHandleRecord} 同事务依次：</p>
 * <ol>
 *   <li>校验 planting_record 存在 + handle_status ∈ {pending, processing}</li>
 *   <li>校验 record_type=2 时 handle_target 必填；handle_target=1 时 location_id 必填且 location 存在</li>
 *   <li>找到 / 创建 vegetable_handle 汇总行（按 planting_record_id 唯一）</li>
 *   <li>INSERT handle_record</li>
 *   <li>聚合 UPDATE vegetable_handle 各重量字段</li>
 *   <li>若 handle_target=1 → INSERT stock_flow（flow_type=veg_stock_in, inout_type=IN）</li>
 *   <li>同步 planting_record.handle_status pending → processing；若 bo.isFinish=1 → 推 done</li>
 * </ol>
 *
 * <h3>并发安全</h3>
 * <p>同一 planting_record 并发写入：MySQL InnoDB REPEATABLE_READ + 事务内 SELECT + 后续 UPDATE 同行
 * 通过行锁串行化。UNIQUE 维度由业务上"一个 planting_record 对应一个 handle 汇总"约束，
 * 由 {@link VegetableHandleMapper#selectByPlantingRecordId} + 事务隔离保证。</p>
 *
 * <h3>损耗计算</h3>
 * <p>{@code loss = picked - handled}（客户 2026-06-20 口径：损耗 = 采摘 − 处理，饲料去向③不算「已处理」、计入损耗）。
 * 仅在「处理完成」(is_finish=1) 时结算，未完成损耗恒 0。负值取 0 并 WARN log。</p>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Slf4j
@Service
public class VegetableHandleServiceImpl
    extends DjsBaseServiceImpl<VegetableHandleMapper, VegetableHandle>
    implements IVegetableHandleService {

    /**
     * djs_veg_handle_status 取值。
     */
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_DONE = "done";

    /**
     * djs_record_type 取值。
     */
    private static final int RECORD_TYPE_PICK = 1;
    private static final int RECORD_TYPE_HANDLE = 2;

    /**
     * djs_handle_target 取值。
     */
    private static final int HANDLE_TARGET_STOCK_IN = 1;
    private static final int HANDLE_TARGET_PLATFORM = 2;
    private static final int HANDLE_TARGET_FEED = 3;

    /**
     * stock_flow.flow_type 蔬菜入库。
     */
    private static final String FLOW_TYPE_VEG_STOCK_IN = "veg_stock_in";

    /**
     * 毛菜鲜品库库位业务码（果蔬全流程 spec 步6 去向①默认入库库位）。按 location_code 查 id，不硬编码 id。
     */
    private static final String LOCATION_CODE_FRESH_VEG = "L0006";

    /**
     * stock_flow.inout_type CHAR(3) IN=入库。
     */
    private static final String INOUT_IN = "IN";

    private final HandleRecordMapper handleRecordMapper;
    private final PlantingRecordMapper plantingRecordMapper;
    private final StockFlowMapper stockFlowMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final ImageUrlResolver imageUrlResolver;
    private final CropInfoMapper cropInfoMapper;
    private final FeedLogMapper feedLogMapper;
    /** 种植明细 mapper：毛菜处理称重回写 actual_yield，让种植「采摘详情·已摘」反映仓库真实称重。 */
    private final PlantDetailsMapper plantDetailsMapper;
    /** 库位库存 mapper：毛菜处理入库回写 location_stock 余额（物资领用读余额表）。 */
    private final LocationStockMapper locationStockMapper;
    /** 产品 mapper：入库前校验解析出的 product_id 真实存在（不存在则跳过余额、不建孤儿行）。 */
    private final ProductInfoMapper productInfoMapper;

    /**
     * 作物图 L2 兜底分类键（作物无 belong_type，统一走"蔬菜默认图"）。
     */
    private static final String CROP_BELONG_TYPE = "vegetable";

    public VegetableHandleServiceImpl(VegetableHandleMapper baseMapper,
                                      HandleRecordMapper handleRecordMapper,
                                      PlantingRecordMapper plantingRecordMapper,
                                      StockFlowMapper stockFlowMapper,
                                      LocationInfoMapper locationInfoMapper,
                                      IBizCodeGenerator bizCodeGenerator,
                                      ImageUrlResolver imageUrlResolver,
                                      CropInfoMapper cropInfoMapper,
                                      FeedLogMapper feedLogMapper,
                                      PlantDetailsMapper plantDetailsMapper,
                                      LocationStockMapper locationStockMapper,
                                      ProductInfoMapper productInfoMapper) {
        super(baseMapper);
        this.handleRecordMapper = handleRecordMapper;
        this.plantingRecordMapper = plantingRecordMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.imageUrlResolver = imageUrlResolver;
        this.cropInfoMapper = cropInfoMapper;
        this.feedLogMapper = feedLogMapper;
        this.plantDetailsMapper = plantDetailsMapper;
        this.locationStockMapper = locationStockMapper;
        this.productInfoMapper = productInfoMapper;
    }

    /**
     * 按作物 {@code crop.related_product}（FK → t_warehouse_product_info.id）解析果蔬成品 product_id。
     *
     * <p>甲方《果疏产品全流程处理.docx》规则：作物→产品转换走 {@code t_plant_crop_info.related_product}，
     * 毛菜处理产出的 product_id 取自该映射（重量不变）。</p>
     *
     * <p><b>优雅降级</b>：客户未在 admin 作物录入页填 related_product 时（现网全 NULL），返回
     * {@code fallback} 并 {@code log.warn}，不抛、不阻塞采摘/处理流程。fallback 通常是
     * {@code planting.getProductId()}（旧来源，多为 null），由调用方再行 0 兜底。</p>
     *
     * @param cropId   作物 id（planting_record.crop_id）
     * @param fallback related_product 为空时的兜底 product_id（可为 null）
     * @return 解析出的果蔬成品 product_id；无映射时返 fallback
     */
    private Long resolveProductIdByCrop(Long cropId, Long fallback) {
        if (cropId == null) {
            return fallback;
        }
        CropInfo crop = cropInfoMapper.selectById(cropId);
        if (crop == null || crop.getRelatedProduct() == null) {
            log.warn("作物 related_product 未配置，product_id 降级为 {} — cropId={}（请在 admin 作物录入页填写"
                + "「关联产品」建立作物↔果蔬成品映射）", fallback, cropId);
            return fallback;
        }
        return crop.getRelatedProduct();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitHandleRecord(HandleRecordSubmitBo bo) {
        Long userId = LoginHelper.getUserId();
        Date now = new Date();

        // Step 1：校验 planting_record 存在 + handle_status 合法
        PlantingRecord planting = plantingRecordMapper.selectById(bo.getPlantingRecordId());
        if (planting == null) {
            throw new ServiceException("种植记录不存在：" + bo.getPlantingRecordId());
        }
        if (STATUS_DONE.equals(planting.getHandleStatus())) {
            throw new ServiceException("该种植记录已处理完成，不能再录入");
        }

        // Step 2：参数校验（record_type=2 必须有 handle_target；target=1 必须有 location）
        Integer recordType = bo.getRecordType();
        if (recordType == null || (recordType != RECORD_TYPE_PICK && recordType != RECORD_TYPE_HANDLE)) {
            throw new ServiceException("记录类型非法（必须 1 采收 / 2 处理）");
        }
        Integer handleTarget = bo.getHandleTarget();
        if (recordType == RECORD_TYPE_HANDLE) {
            if (handleTarget == null) {
                throw new ServiceException("处理录入必须指定处理目标（1=入库 / 2=月台 / 3=饲料）");
            }
            if (handleTarget != HANDLE_TARGET_STOCK_IN
                && handleTarget != HANDLE_TARGET_PLATFORM
                && handleTarget != HANDLE_TARGET_FEED) {
                throw new ServiceException("处理目标非法：" + handleTarget);
            }
            if (handleTarget == HANDLE_TARGET_STOCK_IN && bo.getLocationId() == null) {
                throw new ServiceException("入库需指定库位");
            }
        }

        BigDecimal weight = bo.getRecordWeight();
        if (weight == null || weight.signum() <= 0) {
            throw new ServiceException("本次重量必须 > 0");
        }

        // Step 3：找到 / 创建 vegetable_handle 汇总
        VegetableHandle handle = baseMapper.selectByPlantingRecordId(planting.getId());
        if (handle == null) {
            if (recordType != RECORD_TYPE_PICK) {
                throw new ServiceException("首次录入必须是采收记录（record_type=1）");
            }
            handle = createHandleRow(planting, now);
        }

        // Step 4：校验 location（如有 target=1）
        if (recordType == RECORD_TYPE_HANDLE && handleTarget == HANDLE_TARGET_STOCK_IN) {
            LocationInfo loc = locationInfoMapper.selectById(bo.getLocationId());
            if (loc == null) {
                throw new ServiceException("入库库位不存在：" + bo.getLocationId());
            }
        }

        // Step 5：INSERT handle_record
        HandleRecord record = new HandleRecord();
        record.setHandleId(handle.getId());
        record.setPlotId(planting.getPlotId());
        record.setCropId(planting.getCropId());
        record.setRecordType(recordType);
        record.setRecordWeight(weight);
        record.setHandleTarget(handleTarget);
        record.setLocationId(bo.getLocationId());
        record.setHandleUser(userId);
        record.setHandleTime(now);
        record.setProofOssIds(bo.getProofOssIds());
        record.setRemark(bo.getRemark());
        record.setIsFinish(bo.getIsFinish() == null ? 2 : bo.getIsFinish());
        handleRecordMapper.insert(record);

        // Step 6：聚合 UPDATE vegetable_handle
        VegetableHandle delta = new VegetableHandle();
        BigDecimal picked = nullSafe(handle.getPickedWeight());
        BigDecimal handled = nullSafe(handle.getHandledWeight());
        BigDecimal feed = nullSafe(handle.getFeedWeight());
        BigDecimal sendPlatform = nullSafe(handle.getSendPlatformWeight());
        BigDecimal stockIn = nullSafe(handle.getStockInWeight());

        if (recordType == RECORD_TYPE_PICK) {
            picked = picked.add(weight);
        } else {
            // RECORD_TYPE_HANDLE
            if (handleTarget == HANDLE_TARGET_STOCK_IN) {
                stockIn = stockIn.add(weight);
                handled = handled.add(weight);
            } else if (handleTarget == HANDLE_TARGET_PLATFORM) {
                sendPlatform = sendPlatform.add(weight);
                handled = handled.add(weight);
            } else {
                // FEED：仅累加汇总 feed_weight。注意此 admin 端 /submit 旧路径【不写 t_warehouse_feed_log】，
                // 饲料台账仅由 mp 录入路径 submitProcess 去向③写（WMS-VEG-FEED-LOG-001）；
                // 两路径不对称是有意的——admin /submit 为兼容遗留入口，非果蔬全流程主链路。
                feed = feed.add(weight);
            }
        }

        // 序号9-Req1：仅「处理完成」(is_finish=1) 才结算损耗，未完成损耗恒 0（客户 2026-06-20）
        boolean recordDone = bo.getIsFinish() != null && bo.getIsFinish() == 1;
        BigDecimal loss = recordDone ? recomputeLoss(picked, handled, handle.getId()) : BigDecimal.ZERO;

        delta.setId(handle.getId());
        delta.setPickedWeight(picked);
        delta.setHandledWeight(handled);
        delta.setFeedWeight(feed);
        delta.setSendPlatformWeight(sendPlatform);
        delta.setStockInWeight(stockIn);
        delta.setLossWeight(loss);

        if (STATUS_PENDING.equals(handle.getHandleStatus())) {
            delta.setHandleStatus(STATUS_PROCESSING);
        }
        if (bo.getIsFinish() != null && bo.getIsFinish() == 1) {
            delta.setIsFinish(1);
            delta.setHandleStatus(STATUS_DONE);
            delta.setPickEndTime(now);
        }
        baseMapper.updateById(delta);

        // 采收记录回写种植 actual_yield（与 submitHarvest 同口径，仓库称重 = 实际采摘产量）
        if (recordType == RECORD_TYPE_PICK) {
            syncActualYieldToPlant(planting.getPlotId(), planting.getCropId(), picked);
        }

        // Step 7：条件 INSERT stock_flow（仅入库）
        if (recordType == RECORD_TYPE_HANDLE && handleTarget == HANDLE_TARGET_STOCK_IN) {
            insertVegStockInFlow(handle, planting, bo.getLocationId(), weight, userId, now);
        }

        // Step 8：同步 planting_record.handle_status
        if (STATUS_PENDING.equals(planting.getHandleStatus())) {
            plantingRecordMapper.advanceHandleStatus(
                planting.getId(), STATUS_PENDING, STATUS_PROCESSING, userId);
        }
        if (bo.getIsFinish() != null && bo.getIsFinish() == 1) {
            plantingRecordMapper.advanceHandleStatus(
                planting.getId(), STATUS_PROCESSING, STATUS_DONE, userId);
        }

        return handle.getId();
    }

    private void insertVegStockInFlow(VegetableHandle handle, PlantingRecord planting,
                                      Long locationId, BigDecimal weight, Long userId, Date now) {
        StockFlow flow = new StockFlow();
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", INOUT_IN);
        flow.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx));
        flow.setFlowDate(now);
        // 甲方《果疏产品全流程处理.docx》：product_id 走作物 related_product（作物↔果蔬成品映射）解析；
        // fallback 链 crop.related_product → handle.product_id（建汇总行时已解析存入）→ planting.product_id（旧来源）；
        // 全 null 则 0 + warning log（作物未配 related_product 时；V1 蔬菜业态产品 0 兜底不影响 admin 列表显示，
        // 仅 product 维度聚合查询会漏，待客户在 admin 录入作物↔成品映射后自然消除）。
        Long resolvedProductId = resolveProductIdByCrop(
            planting.getCropId(), firstNonNull(handle.getProductId(), planting.getProductId()));
        if (resolvedProductId == null) {
            log.warn("stock_flow.product_id 兜底为 0 — plantingRecordId={} cropId={} crop={}（请在 admin 作物录入页"
                + "填写「关联产品」建立作物↔果蔬成品映射）", planting.getId(), planting.getCropId(), planting.getCropName());
            resolvedProductId = 0L;
        }
        flow.setProductId(resolvedProductId);
        flow.setWarehouseId(locationId);
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(FLOW_TYPE_VEG_STOCK_IN);
        flow.setChangeNum(weight);
        flow.setChangeQuantity(weight);
        flow.setPlotId(planting.getPlotId());
        flow.setOperatorId(userId);
        flow.setRemark("蔬菜处理入库 plantingRecordId=" + planting.getId() + " crop=" + planting.getCropName());
        stockFlowMapper.insert(flow);

        // 余额回写（对齐采购/打包入库的 product 维度 UPSERT，WarehousePurchaseInServiceImpl.inbound 范式）：
        // 毛菜处理入库后把 weight 累加进 t_warehouse_location_stock，物资领用·蔬菜 tab（以 product_info 为主表
        // JOIN location_stock）才看得到可领用库存。原仅写 stock_flow 流水、不写余额，是入库对物资领用不可见的主因。
        // resolvedProductId 必须真实存在于 product_info；作物 related_product 未正确关联果蔬成品时（配置缺失/占位值）
        // 跳过余额写入 + warn，不建挂空 product 的孤儿余额行、不阻断入库。
        ProductInfo product = productInfoMapper.selectById(resolvedProductId);
        if (product == null) {
            log.warn("毛菜处理入库余额未回写：product_id={} 在 product_info 不存在（作物未正确关联果蔬成品产品）"
                + " — plantingRecordId={} crop={}", resolvedProductId, planting.getId(), planting.getCropName());
            return;
        }
        Long plotId = planting.getPlotId();
        if (plotId != null) {
            // 自产果蔬原料按「地块篮子」入冷库（plot_id = 篮子标签，对齐猪肉分割 ear_no 篮子，doc/14 §1）：
            // 每次毛菜处理入库建一篮（带 plot_id）。领用按篮 FIFO 把 plot 带到 product_inhouse → 果蔬打包
            // 右台显「对应地块」（而非领用记录）。同 plot 多次入库 = 多篮，打包页 plotToggle 按 plot 去重。
            LocationStock basket = new LocationStock();
            basket.setLocationId(locationId);
            basket.setProductId(resolvedProductId);
            basket.setPlotId(plotId);            // 篮子标签 = 地块 → 打包追溯键
            basket.setProductName(product.getProductName());
            basket.setProductUnit(product.getProductUnit());
            basket.setProductStock(weight);
            basket.setIsEnd(0);
            basket.setOperatorId(userId);
            locationStockMapper.insert(basket);
            return;
        }
        // 兜底（毛菜处理来源 planting 理论必有 plot）：无地块 → product 维度 UPSERT（旧行为）
        int updated = locationStockMapper.addByProductLocation(locationId, resolvedProductId, weight, userId);
        if (updated == 0) {
            LocationStock fresh = new LocationStock();
            fresh.setLocationId(locationId);
            fresh.setProductId(resolvedProductId);
            fresh.setProductName(product.getProductName());
            fresh.setProductUnit(product.getProductUnit());
            fresh.setProductStock(weight);
            fresh.setIsEnd(0);
            fresh.setOperatorId(userId);
            locationStockMapper.insert(fresh);
        }
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 返回第一个非 null 值（两者皆 null 则 null）。用于 product_id fallback 链。
     */
    private static Long firstNonNull(Long a, Long b) {
        return a != null ? a : b;
    }

    /**
     * 首次录入时建 vegetable_handle 汇总行（picked/handled/feed/sendPlatform/stockIn/loss 全 0，
     * is_weighed=2 / is_finish=2 / handle_status=processing）。tenant_id 走 MP 自动 fill。
     */
    private VegetableHandle createHandleRow(PlantingRecord planting, Date now) {
        VegetableHandle handle = new VegetableHandle();
        handle.setPlantingRecordId(planting.getId());
        handle.setPlotId(planting.getPlotId());
        handle.setCropId(planting.getCropId());
        // product_id 优先按作物 related_product（作物↔果蔬成品映射）解析；未配置则降级回旧来源 planting.product_id
        handle.setProductId(resolveProductIdByCrop(planting.getCropId(), planting.getProductId()));
        handle.setPickStartTime(now);
        handle.setPickedWeight(BigDecimal.ZERO);
        handle.setHandledWeight(BigDecimal.ZERO);
        handle.setFeedWeight(BigDecimal.ZERO);
        handle.setSendPlatformWeight(BigDecimal.ZERO);
        handle.setStockInWeight(BigDecimal.ZERO);
        handle.setLossWeight(BigDecimal.ZERO);
        handle.setIsWeighed(2);
        handle.setIsFinish(2);
        handle.setHandleStatus(STATUS_PROCESSING);
        baseMapper.insert(handle);
        return handle;
    }

    /**
     * 重算损耗 {@code loss = picked - handled}（客户 2026-06-20 口径：损耗 = 采摘录入重量之和 − 果蔬处理重量之和，
     * 饲料去向不算「已处理」，故饲料量计入损耗）。仅在「处理完成」时由调用方决定是否结算（未完成损耗恒 0）。
     * 负值归零并 WARN，结果保留 3 位。
     */
    private BigDecimal recomputeLoss(BigDecimal picked, BigDecimal handled, Long handleId) {
        BigDecimal loss = picked.subtract(handled);
        if (loss.signum() < 0) {
            log.warn("loss_weight 负值（picked={} handled={}），归零处理 handleId={}",
                picked, handled, handleId);
            loss = BigDecimal.ZERO;
        }
        return loss.setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * 毛菜处理称重回写种植产量：仓库称重 = 实际采摘产量，把累计 picked 写回 {@code t_plant_plant_details.actual_yield}，
     * 让种植「采摘详情·已摘」反映仓库真实称重（#3=a 后采收 tab 不录重量，actual_yield 在种植侧无来源，唯一来源在此）。
     *
     * <p>定位：{@code planting_record} 不存 detail_id，按 (plot_id, crop_id) 匹配，优先已完成采摘（{@code harvest_status='completed'}）
     * 的明细（V1 plot+crop 基本 1:1）。方向为 warehouse→plant 写，与本类已有 {@link CropInfoMapper} 依赖同向、不成环
     * （plant 不反向依赖 warehouse）。</p>
     *
     * @param plotId      地块 id
     * @param cropId      作物 id
     * @param pickedTotal 该地块该作物累计采摘重量（vegetable_handle.picked_weight）
     */
    private void syncActualYieldToPlant(Long plotId, Long cropId, BigDecimal pickedTotal) {
        if (plotId == null || cropId == null || pickedTotal == null) {
            return;
        }
        List<PlantDetails> matches = plantDetailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getPlotId, plotId)
                .eq(PlantDetails::getCropId, cropId)
                .orderByDesc(PlantDetails::getId));
        if (matches == null || matches.isEmpty()) {
            log.warn("毛菜处理回写采摘产量：未找到 plant_details plotId={} cropId={}，跳过", plotId, cropId);
            return;
        }
        PlantDetails target = matches.stream()
            .filter(d -> "completed".equals(d.getHarvestStatus()))
            .findFirst().orElse(matches.get(0));
        plantDetailsMapper.update(null,
            new LambdaUpdateWrapper<PlantDetails>()
                .eq(PlantDetails::getId, target.getId())
                .set(PlantDetails::getActualYield, pickedTotal)
                .set(PlantDetails::getUpdateBy, LoginHelper.getUserId()));
    }

    @Override
    public List<PendingPlantingRecordVo> listPending() {
        return plantingRecordMapper.selectPendingList();
    }

    @Override
    public List<VegCropVo> listCrops() {
        List<VegCropVo> list = baseMapper.selectCropAggList();
        // IMG-LIB-001：thumbUrl 走 4 层 resolver（L1 作物 image_oss_id → L2 蔬菜默认图 → L3 全局），禁 N+1
        if (!list.isEmpty()) {
            List<ImageUrlResolver.Item> items = list.stream()
                .map(v -> new ImageUrlResolver.Item(v.getImageOssId(), CROP_BELONG_TYPE))
                .toList();
            List<String> urls = imageUrlResolver.resolveList(items);
            if (urls.size() == list.size()) {
                for (int i = 0; i < list.size(); i++) {
                    list.get(i).setThumbUrl(urls.get(i));
                }
            }
        }
        return list;
    }

    @Override
    public List<VegPlotDetailVo> listPlotsByCrop(Long cropId) {
        if (cropId == null) {
            throw new ServiceException("缺少作物 ID");
        }
        return plantingRecordMapper.selectPlotDetailByCrop(cropId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitHarvest(HarvestSubmitBo bo) {
        Date now = new Date();
        Long userId = bo.getWeighUserId();

        // Step 1：校验 planting_record 存在 + 未完成
        PlantingRecord planting = plantingRecordMapper.selectById(bo.getPlantingRecordId());
        if (planting == null) {
            throw new ServiceException("种植记录不存在：" + bo.getPlantingRecordId());
        }
        if (STATUS_DONE.equals(planting.getHandleStatus())) {
            throw new ServiceException("该种植记录已处理完成，不能再录入");
        }

        BigDecimal weight = bo.getHarvestWeight();
        boolean weighDone = bo.getWeighFinish() != null && bo.getWeighFinish() == 1;

        // Step 2：找到 / 创建 vegetable_handle 汇总
        VegetableHandle handle = baseMapper.selectByPlantingRecordId(planting.getId());
        if (handle == null) {
            handle = createHandleRow(planting, now);
        }

        // Step 2.1：spec 步4「地块称重完成后不再允许新增采摘录入」后端强约束
        // （createHandleRow 把新行 isWeighed=2，不误伤本次新建的首录）
        if (handle.getIsWeighed() != null && handle.getIsWeighed() == 1) {
            throw new ServiceException("该地块已称重完成，不能再录入采摘重量");
        }

        // Step 3：INSERT handle_record（采收）
        HandleRecord record = new HandleRecord();
        record.setHandleId(handle.getId());
        record.setPlotId(planting.getPlotId());
        record.setCropId(planting.getCropId());
        record.setRecordType(RECORD_TYPE_PICK);
        record.setRecordWeight(weight);
        record.setIsWeighed(weighDone ? 1 : 2);
        record.setIsFinish(2);
        record.setHandleTarget(null);
        record.setLocationId(null);
        record.setHandleUser(userId);
        record.setHandleTime(now);
        handleRecordMapper.insert(record);

        // Step 4：聚合 UPDATE vegetable_handle（picked_weight += weight）
        // 序号9-Req1：采摘阶段 is_finish 恒为 2（未处理完成）→ 损耗恒置 0，不在采摘时结算损耗（客户 2026-06-20）
        BigDecimal picked = nullSafe(handle.getPickedWeight()).add(weight);

        VegetableHandle delta = new VegetableHandle();
        delta.setId(handle.getId());
        delta.setPickedWeight(picked);
        delta.setLossWeight(BigDecimal.ZERO);
        if (STATUS_PENDING.equals(handle.getHandleStatus())) {
            delta.setHandleStatus(STATUS_PROCESSING);
        }
        // 采摘录入只动 is_weighed，不动 is_finish，不推 done
        if (weighDone) {
            delta.setIsWeighed(1);
        }
        baseMapper.updateById(delta);

        // Step 4.1：回写种植 actual_yield（仓库称重 = 实际采摘产量），让种植「采摘详情·已摘」反映真实称重
        syncActualYieldToPlant(planting.getPlotId(), planting.getCropId(), picked);

        // Step 5：同步 planting_record.handle_status pending → processing
        if (STATUS_PENDING.equals(planting.getHandleStatus())) {
            plantingRecordMapper.advanceHandleStatus(
                planting.getId(), STATUS_PENDING, STATUS_PROCESSING, userId);
        }

        return handle.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitProcess(ProcessSubmitBo bo) {
        Date now = new Date();
        Long userId = bo.getProcessUserId();

        // Step 1：校验 planting_record 存在 + 未完成
        PlantingRecord planting = plantingRecordMapper.selectById(bo.getPlantingRecordId());
        if (planting == null) {
            throw new ServiceException("种植记录不存在：" + bo.getPlantingRecordId());
        }
        if (STATUS_DONE.equals(planting.getHandleStatus())) {
            throw new ServiceException("该种植记录已处理完成，不能再录入");
        }

        // Step 2：校验去向
        Integer handleTarget = bo.getHandleTarget();
        if (handleTarget == null
            || (handleTarget != HANDLE_TARGET_STOCK_IN
            && handleTarget != HANDLE_TARGET_PLATFORM
            && handleTarget != HANDLE_TARGET_FEED)) {
            throw new ServiceException("处理目标非法（必须 1=入库 / 2=月台 / 3=饲料）：" + handleTarget);
        }

        BigDecimal weight = bo.getProcessWeight();
        boolean processDone = bo.getProcessFinish() != null && bo.getProcessFinish() == 1;

        // Step 3：找汇总行（必须先有采摘）
        VegetableHandle handle = baseMapper.selectByPlantingRecordId(planting.getId());
        if (handle == null) {
            throw new ServiceException("请先录入采摘重量");
        }

        // Step 3.0a 序号6-Req2：果蔬处理（入库 + 月台）累计不得超过采摘累计（客户 2026-06-20）。
        // 饲料去向③不受此限（饲料不算「已处理」，计入损耗）。
        if (handleTarget == HANDLE_TARGET_STOCK_IN || handleTarget == HANDLE_TARGET_PLATFORM) {
            BigDecimal projectedHandled = nullSafe(handle.getHandledWeight()).add(weight);
            if (projectedHandled.compareTo(nullSafe(handle.getPickedWeight())) > 0) {
                throw new ServiceException("果蔬处理重量不得大于果蔬采摘录入重量");
            }
        }

        // Step 3.0b 序号9-Req2：未「称重完成」(is_weighed=1) 不得标记「处理完成」（客户 2026-06-20）
        if (processDone && (handle.getIsWeighed() == null || handle.getIsWeighed() != 1)) {
            throw new ServiceException("请先完成地块称重，再标记处理完成");
        }

        // Step 3.1：去向①入库默认落毛菜鲜品库（L0006），按 location_code 查 id（不硬编码 id）
        Long stockInLocationId = null;
        if (handleTarget == HANDLE_TARGET_STOCK_IN) {
            LocationInfo freshVegLoc = locationInfoMapper.selectOne(
                new LambdaQueryWrapper<LocationInfo>()
                    .eq(LocationInfo::getLocationCode, LOCATION_CODE_FRESH_VEG)
                    .last("LIMIT 1"));
            if (freshVegLoc == null) {
                throw new ServiceException("毛菜鲜品库（库位编码 " + LOCATION_CODE_FRESH_VEG + "）不存在，请先在库位管理维护");
            }
            stockInLocationId = freshVegLoc.getId();
        }

        // Step 4：INSERT handle_record（处理）
        //   - 去向①入库：location_id = L0006、plot_id 保留
        //   - 去向②月台：location_id = null、plot_id 保留
        //   - 去向③饲料：location_id = null、plot_id = null（spec 步8 该类记录不记录地块编号）
        HandleRecord record = new HandleRecord();
        record.setHandleId(handle.getId());
        // 三去向 handle_record 均记 plot_id（t_warehouse_handle_record.plot_id NOT NULL）。
        // spec 步8「饲料饲喂不记地块」由专用台账表 t_warehouse_feed_log（无 plot_id 列）满足；
        // handle_record 是毛菜处理事件日志（非饲料专用表），保留 plot_id 作处理来源上下文。
        record.setPlotId(planting.getPlotId());
        record.setCropId(planting.getCropId());
        record.setRecordType(RECORD_TYPE_HANDLE);
        record.setRecordWeight(weight);
        record.setHandleTarget(handleTarget);
        record.setLocationId(stockInLocationId);
        record.setIsFinish(processDone ? 1 : 2);
        record.setHandleUser(userId);
        record.setHandleTime(now);
        handleRecordMapper.insert(record);

        // Step 5：聚合 UPDATE vegetable_handle（按 target 分流；重算 loss）
        BigDecimal picked = nullSafe(handle.getPickedWeight());
        BigDecimal handled = nullSafe(handle.getHandledWeight());
        BigDecimal feed = nullSafe(handle.getFeedWeight());
        BigDecimal sendPlatform = nullSafe(handle.getSendPlatformWeight());
        BigDecimal stockIn = nullSafe(handle.getStockInWeight());

        if (handleTarget == HANDLE_TARGET_STOCK_IN) {
            stockIn = stockIn.add(weight);
            handled = handled.add(weight);
        } else if (handleTarget == HANDLE_TARGET_PLATFORM) {
            sendPlatform = sendPlatform.add(weight);
            handled = handled.add(weight);
        } else {
            // FEED：只累加 feed，不计入 handled
            feed = feed.add(weight);
        }

        // 序号9-Req1：仅「处理完成」(is_finish=1) 才结算损耗（= 采摘 − 处理），未完成损耗恒 0（客户 2026-06-20）
        BigDecimal loss = processDone ? recomputeLoss(picked, handled, handle.getId()) : BigDecimal.ZERO;

        VegetableHandle delta = new VegetableHandle();
        delta.setId(handle.getId());
        delta.setHandledWeight(handled);
        delta.setFeedWeight(feed);
        delta.setSendPlatformWeight(sendPlatform);
        delta.setStockInWeight(stockIn);
        delta.setLossWeight(loss);
        delta.setHandleStatus(STATUS_PROCESSING);
        if (processDone) {
            delta.setIsFinish(1);
            delta.setHandleStatus(STATUS_DONE);
            delta.setPickEndTime(now);
        }
        baseMapper.updateById(delta);

        // Step 6：去向①入库 → 生成入库流水（产品维度 + 地块编号 + 库位 L0006，spec 步6）；
        //         去向③饲料 → 写饲料台账（按日 × 作物品类，不记地块，spec 步8）
        if (handleTarget == HANDLE_TARGET_STOCK_IN) {
            insertVegStockInFlow(handle, planting, stockInLocationId, weight, userId, now);
        } else if (handleTarget == HANDLE_TARGET_FEED) {
            insertFeedLog(planting, weight, now);
        }

        // Step 7：同步 planting_record.handle_status
        if (STATUS_PENDING.equals(planting.getHandleStatus())) {
            plantingRecordMapper.advanceHandleStatus(
                planting.getId(), STATUS_PENDING, STATUS_PROCESSING, userId);
        }
        if (processDone) {
            plantingRecordMapper.advanceHandleStatus(
                planting.getId(), STATUS_PROCESSING, STATUS_DONE, userId);
        }

        return handle.getId();
    }

    /**
     * 去向③饲料饲喂 → 插入饲料台账（{@code t_warehouse_feed_log}，spec 步8）。
     *
     * <p>按自然日 × 作物品类记录重量，{@code 不记录地块编号}（无 plot_id）。tenant_id 走 MP 自动 fill。</p>
     */
    private void insertFeedLog(PlantingRecord planting, BigDecimal weight, Date now) {
        FeedLog feedLog = new FeedLog();
        feedLog.setFeedDate(now);
        feedLog.setCropId(planting.getCropId());
        feedLog.setCropName(planting.getCropName());
        feedLog.setFeedWeight(weight);
        feedLogMapper.insert(feedLog);
    }

    @Override
    public TableDataInfo<VegetableHandleVo> queryPageList(VegHandleQuery query, PageQuery pageQuery) {
        Page<VegetableHandleVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(query));
        fillPlantingNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<VegetableHandleVo> queryList(VegHandleQuery query) {
        List<VegetableHandleVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillPlantingNames(list);
        return list;
    }

    @Override
    public VegetableHandleVo queryById(Long id) {
        VegetableHandleVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillPlantingNames(List.of(vo));
        }
        return vo;
    }

    @Override
    public List<HandleRecordVo> listRecords(Long handleId) {
        return handleRecordMapper.selectVoList(
            new LambdaQueryWrapper<HandleRecord>()
                .eq(HandleRecord::getHandleId, handleId)
                .orderByAsc(HandleRecord::getHandleTime));
    }

    @Override
    public TableDataInfo<HandleRecordVo> myRecords(PageQuery pageQuery) {
        Long userId = LoginHelper.getUserId();
        Page<HandleRecordVo> page = handleRecordMapper.selectVoPage(pageQuery.build(),
            new LambdaQueryWrapper<HandleRecord>()
                .eq(HandleRecord::getHandleUser, userId)
                .orderByDesc(HandleRecord::getHandleTime));
        return TableDataInfo.build(page);
    }

    /**
     * 用 planting_record 冗余 plot_name / crop_name 回填 VO（避免跨模块依赖 plant）。
     */
    private void fillPlantingNames(List<VegetableHandleVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> plantingIds = records.stream()
            .map(VegetableHandleVo::getPlantingRecordId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (plantingIds.isEmpty()) {
            return;
        }
        List<PlantingRecord> plantings = plantingRecordMapper.selectList(
            new LambdaQueryWrapper<PlantingRecord>().in(PlantingRecord::getId, plantingIds));
        Map<Long, PlantingRecord> map = plantings.stream()
            .collect(Collectors.toMap(PlantingRecord::getId, p -> p, (a, b) -> a));
        for (VegetableHandleVo vo : records) {
            if (vo.getPlantingRecordId() != null) {
                PlantingRecord p = map.get(vo.getPlantingRecordId());
                if (p != null) {
                    vo.setPlotName(p.getPlotName());
                    vo.setCropName(p.getCropName());
                }
            }
        }
    }

    private LambdaQueryWrapper<VegetableHandle> buildQueryWrapper(VegHandleQuery query) {
        LambdaQueryWrapper<VegetableHandle> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(VegetableHandle::getId);
        }
        wrapper.eq(query.getPlotId() != null, VegetableHandle::getPlotId, query.getPlotId())
            .eq(query.getCropId() != null, VegetableHandle::getCropId, query.getCropId())
            .eq(query.getPlantingRecordId() != null, VegetableHandle::getPlantingRecordId, query.getPlantingRecordId())
            .eq(query.getHandleStatus() != null && !query.getHandleStatus().isBlank(),
                VegetableHandle::getHandleStatus, query.getHandleStatus())
            .ge(query.getPickStartTimeFrom() != null, VegetableHandle::getPickStartTime, query.getPickStartTimeFrom())
            .le(query.getPickStartTimeTo() != null, VegetableHandle::getPickStartTime, query.getPickStartTimeTo())
            .orderByDesc(VegetableHandle::getId);
        return wrapper;
    }

}
