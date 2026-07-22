package org.dromara.djs.warehouse.veg.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.activity.domain.bo.PickActivityRecordBo;
import org.dromara.djs.plant.activity.service.IPlantActivityService;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.loss.domain.LossFlow;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
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
import org.dromara.djs.warehouse.veg.domain.bo.PickActivitySubmitBo;
import org.dromara.djs.warehouse.veg.domain.bo.PickDestSubmitBo;
import org.dromara.djs.warehouse.veg.domain.bo.ProcessSubmitBo;
import org.dromara.djs.warehouse.veg.domain.query.PickDetailQuery;
import org.dromara.djs.warehouse.veg.domain.query.VegHandleQuery;
import org.dromara.djs.warehouse.veg.domain.vo.HandleRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.PendingPlantingRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.PickDetailVo;
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
 * <p>{@code loss = picked - stockIn - sendPlatform - feed}（行59 口径：毛菜处理损耗 = 毛菜间称重总重 −
 * 鲜品库入库重 − 发往月台重 − 饲料饲喂重）。仅在「处理完成」(is_finish=1) 时结算，未完成损耗恒 0；
 * 负值取 0 并 WARN log。结算时在 {@code loss_weight} 列之上双写一条统一损耗台账
 * {@code t_warehouse_loss_flow}（loss_type=veg_handle_loss，WMS-LOSS-001）。</p>
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
     * djs_pick_status「已完成」value（种植端采摘完成信号；{@code t_plant_plant_details.harvest_status}）。
     */
    private static final String PICK_STATUS_COMPLETED = "completed";

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

    /**
     * djs_pick_dest 采摘去向（DENGBO-R4 决策 A，非销售去向映射到毛菜处理写入机制）。
     * sale（销售）不写仓库、不进本类，故无常量。
     */
    private static final String PICK_DEST_VEG_FRESH = "veg_fresh";
    private static final String PICK_DEST_PLATFORM = "platform";
    private static final String PICK_DEST_LOSS = "loss";
    private static final String PICK_DEST_FEED = "feed";

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
    /** 统一损耗门面：处理完成结算损耗时在原 loss_weight 列之上双写一条 t_warehouse_loss_flow 明细。 */
    private final ILossFlowService lossFlowService;
    /** 采摘活动 service（DENGBO-R4 采摘去向编排：先写 plant activity 行 + 产量分摊，再写仓库台账）。 */
    private final IPlantActivityService plantActivityService;

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
                                      ProductInfoMapper productInfoMapper,
                                      ILossFlowService lossFlowService,
                                      IPlantActivityService plantActivityService) {
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
        this.lossFlowService = lossFlowService;
        this.plantActivityService = plantActivityService;
    }

    /**
     * 按作物 {@code crop.related_product}（FK → t_warehouse_product_info.id）解析果蔬成品 product_id。
     *
     * <p>甲方《果疏产品全流程处理.docx》规则：作物→产品转换走 {@code t_plant_crop_info.related_product}，
     * 毛菜处理产出的 product_id 取自该映射（重量不变）。</p>
     *
     * <p><b>优雅降级</b>：客户未在 admin 作物录入页填 related_product 时（现网全 NULL），返回
     * {@code fallback} 并 {@code log.warn}，不抛、不阻塞采摘/处理流程。fallback 通常是
     * {@code planting.getProductId()}（旧来源，多为 null）；写 stock_flow 的调用方对 null 结果
     * 显式失败（product_id=0 兜底已废除，防库存总览无名幽灵行）。</p>
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
        // 行59 新口径：损耗 = 称重总重 − 入库 − 月台 − 饲料
        boolean recordDone = bo.getIsFinish() != null && bo.getIsFinish() == 1;
        BigDecimal loss = recordDone
            ? recomputeLoss(picked, stockIn, sendPlatform, feed, handle.getId())
            : BigDecimal.ZERO;

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
        // 全 null → 显式失败（product_id=0 会在库存总览产生无名幽灵行，且 product 维度聚合全漏）。
        Long resolvedProductId = resolveProductIdByCrop(
            planting.getCropId(), firstNonNull(handle.getProductId(), planting.getProductId()));
        if (resolvedProductId == null) {
            throw new ServiceException("作物「" + planting.getCropName() + "」未关联果蔬成品，无法入库："
                + "请先在 admin 作物录入页填写「关联产品」建立作物↔成品映射后再提交");
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
     * 重算损耗 {@code loss = picked - stockIn - sendPlatform - feed}（行59 新口径：
     * 毛菜处理损耗 = 毛菜间称重总重 − 鲜品库入库重 − 发往月台重 − 饲料饲喂重）。
     * 仅在「处理完成」时由调用方决定是否结算（未完成损耗恒 0）。负值归零并 WARN，结果保留 3 位。
     */
    private BigDecimal recomputeLoss(BigDecimal picked, BigDecimal stockIn,
                                     BigDecimal sendPlatform, BigDecimal feed, Long handleId) {
        BigDecimal loss = nullSafe(picked)
            .subtract(nullSafe(stockIn))
            .subtract(nullSafe(sendPlatform))
            .subtract(nullSafe(feed));
        if (loss.signum() < 0) {
            log.warn("loss_weight 负值（picked={} stockIn={} sendPlatform={} feed={}），归零处理 handleId={}",
                picked, stockIn, sendPlatform, feed, handleId);
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

    /**
     * 采摘「地块称重完成」前置门：校验该地块该作物的种植采摘已完成
     * （{@code t_plant_plant_details.harvest_status='completed'}，dict {@code djs_pick_status}「已完成」）。
     *
     * <p>口径：{@code planting_record} 不存 detail_id，按 (plot_id, crop_id) 匹配 plant_details（V1 plot+crop
     * 基本 1:1，与 {@link #syncActualYieldToPlant} 同定位规则）；只要存在一条 {@code harvest_status='completed'}
     * 即视为该地块采摘完成，放行。无任何匹配明细 / 全部未完成 → 抛 {@link ServiceException}「请先完成地块采收操作」。</p>
     *
     * <p>方向 warehouse→plant 只读查询，与本类已有 plant 依赖同向不成环。</p>
     *
     * @param plotId 地块 id
     * @param cropId 作物 id
     */
    private void requirePlantHarvestCompleted(Long plotId, Long cropId) {
        if (plotId == null || cropId == null) {
            throw new ServiceException("请先完成地块采收操作");
        }
        Long completedCount = plantDetailsMapper.selectCount(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getPlotId, plotId)
                .eq(PlantDetails::getCropId, cropId)
                .eq(PlantDetails::getHarvestStatus, PICK_STATUS_COMPLETED));
        if (completedCount == null || completedCount == 0L) {
            throw new ServiceException("请先完成地块采收操作");
        }
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

        // Step 1.1：勾「地块称重完成」(weighFinish=1) 才能提交的前置门——该地块对应种植采摘必须已完成
        // （种植端 t_plant_plant_details.harvest_status='completed'，dict djs_pick_status「已完成」）。
        // 未完成不允许在仓库侧标记称重完成（避免采摘未结束就锁死地块）。未勾完成（仅追加重量）不校验。
        if (weighDone) {
            requirePlantHarvestCompleted(planting.getPlotId(), planting.getCropId());
        }

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

        // Step 3.0a 行2：去向（入库 + 月台 + 饲料）累计不得超过采摘累计（客户 2026-06-20，默认 a）。
        // 三去向均纳入封顶：projected = 已入库 + 已月台 + 已饲料 + 本次 > picked → 拦截（饲料不再豁免）。
        BigDecimal projectedHandled = nullSafe(handle.getHandledWeight())
            .add(nullSafe(handle.getFeedWeight()))
            .add(weight);
        if (projectedHandled.compareTo(nullSafe(handle.getPickedWeight())) > 0) {
            throw new ServiceException("果蔬处理重量不得大于果蔬采摘录入重量");
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

        // 序号9-Req1：仅「处理完成」(is_finish=1) 才结算损耗，未完成损耗恒 0（客户 2026-06-20）
        // 行59 新口径：损耗 = 称重总重 − 入库 − 月台 − 饲料
        BigDecimal loss = processDone
            ? recomputeLoss(picked, stockIn, sendPlatform, feed, handle.getId())
            : BigDecimal.ZERO;

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
            insertFeedLog(planting, weight, userId, now);
        }

        // Step 6.1：处理完成结算时在 loss_weight 列之上双写统一损耗台账（行59 WMS-LOSS-001）。
        // productId 经 crop→related_product 反解（拿不到传 null）；门面对 loss<=0 自动跳过。
        if (processDone && loss.signum() > 0) {
            LossFlow lossFlow = new LossFlow();
            lossFlow.setLossType("veg_handle_loss");
            lossFlow.setLossWeight(loss);
            lossFlow.setProductId(resolveProductIdByCrop(
                planting.getCropId(), firstNonNull(handle.getProductId(), planting.getProductId())));
            lossFlow.setPlotId(planting.getPlotId());
            lossFlow.setBelongType(CROP_BELONG_TYPE);
            lossFlow.setSourceBizType("veg_handle");
            lossFlow.setSourceBizId(handle.getId());
            lossFlow.setOperatorId(userId);
            lossFlowService.record(lossFlow);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitPickActivity(PickActivitySubmitBo bo) {
        if (bo == null) {
            throw new ServiceException("采摘去向录入参数为空");
        }
        boolean sale = "sale".equals(bo.getPickDest());

        // 1. plant 侧：写 activity per-event 行 + 非销售去向累加所选地块产量（plant 自管 plant_details）
        PickActivityRecordBo plantBo = new PickActivityRecordBo();
        plantBo.setCropId(bo.getCropId());
        plantBo.setActivityDate(bo.getActivityDate());
        plantBo.setPickWeight(bo.getPickWeight());
        plantBo.setPickDest(bo.getPickDest());
        plantBo.setPlotId(bo.getPlotId());
        plantBo.setRecorderId(bo.getRecorderId());
        plantBo.setFinishFlag(bo.getFinishFlag()); // DENGBO-R24 录入完成标志透传
        Long activityId = plantActivityService.recordPickActivity(plantBo);

        // 2. 非销售去向：写仓库台账（销售不写仓库库存、只进产量分摊，已在 step1 plant 侧完成行写入）
        //    DENGBO-R24：结算-only（仅录入完成、无本次重量）→ activityId 为 null，不写任何去向台账。
        if (activityId != null && !sale) {
            String cropName = bo.getCropName();
            if (cropName == null || cropName.isBlank()) {
                CropInfo crop = cropInfoMapper.selectById(bo.getCropId());
                cropName = crop != null ? crop.getCropName() : null;
            }
            PickDestSubmitBo destBo = new PickDestSubmitBo();
            destBo.setCropId(bo.getCropId());
            destBo.setCropName(cropName);
            destBo.setPlotId(bo.getPlotId());
            destBo.setProductId(resolveProductIdByCrop(bo.getCropId(), null));
            destBo.setPickDest(bo.getPickDest());
            destBo.setWeight(bo.getPickWeight());
            destBo.setRecorderId(bo.getRecorderId());
            recordPickDestination(destBo);
        }
        return activityId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordPickDestination(PickDestSubmitBo bo) {
        if (bo == null) {
            throw new ServiceException("采摘去向入账参数为空");
        }
        String dest = bo.getPickDest();
        BigDecimal weight = bo.getWeight();
        if (dest == null || dest.isBlank()) {
            throw new ServiceException("采摘去向不能为空");
        }
        if (weight == null || weight.signum() <= 0) {
            throw new ServiceException("采摘重量必须大于 0");
        }
        Long userId = bo.getRecorderId() != null ? bo.getRecorderId() : LoginHelper.getUserId();
        Date now = new Date();

        switch (dest) {
            case PICK_DEST_VEG_FRESH ->
                // 毛菜保鲜室 = 复用毛菜处理入库（stock_flow veg_stock_in + location_stock 按 plot 篮，落 L0006）
                insertPickStockIn(bo, weight, userId, now);
            case PICK_DEST_PLATFORM ->
                // DENGBO-R22：果蔬月台 = 写一行 t_warehouse_vegetable_handle 承载「发往月台重量」，
                // 使采摘活动直送月台的果蔬出现在「自产产品收货」待入库列表（selectSelfPending 读 send_platform_weight）、可入库。
                insertPickPlatform(bo, weight, userId, now);
            case PICK_DEST_LOSS ->
                // 损耗 = 复用统一损耗台账 loss_flow（loss_type=veg_handle_loss）
                insertPickLoss(bo, weight, userId);
            case PICK_DEST_FEED ->
                // 饲料饲喂 = 复用饲料台账 feed_log（feed_type=veg_handle）
                insertPickFeed(bo, weight, userId, now);
            default -> throw new ServiceException("非法/不入仓库的采摘去向：" + dest
                + "（销售去向不应调用本入账方法）");
        }
    }

    /**
     * DENGBO-R22 采摘去向[果蔬月台]：写一行 {@code t_warehouse_vegetable_handle} 承载 send_platform_weight，
     * 使采摘活动直送月台的果蔬进入「自产产品收货」待入库列表（{@code selectSelfPending} 按 send_platform_weight 聚合）、可入库；
     * 其余处理量字段置 0，复用现有月台待收货/入库/损耗全链路。
     *
     * <p>Kevin 2026-07-16 定 A：同写一行 {@code t_warehouse_handle_record}(handle_target=2 发往月台，按 handle_time)，
     * 使采摘直送月台的量计入「发往月台果蔬总重」日统计（{@code WarehouseStatAggregateMapper.sumSendPlatformWeight}
     * 读 handle_record）+ 作物维发往口径，与入库后计入的「月台接收」（veg_receive）对称、不再漏计。
     * handle_record 按 handle_id/handle_user 归属本行，不污染毛菜处理列表；待入库仍只读 vegetable_handle，不双计。</p>
     */
    private void insertPickPlatform(PickDestSubmitBo bo, BigDecimal weight, Long userId, Date now) {
        VegetableHandle handle = new VegetableHandle();
        handle.setPlotId(bo.getPlotId());
        handle.setCropId(bo.getCropId());
        handle.setProductId(resolveProductIdByCrop(bo.getCropId(), bo.getProductId()));
        handle.setPickStartTime(now);
        handle.setPickedWeight(BigDecimal.ZERO);
        handle.setHandledWeight(BigDecimal.ZERO);
        handle.setFeedWeight(BigDecimal.ZERO);
        handle.setSendPlatformWeight(weight);
        handle.setStockInWeight(BigDecimal.ZERO);
        handle.setLossWeight(BigDecimal.ZERO);
        handle.setIsWeighed(2);
        handle.setIsFinish(2);
        handle.setHandleStatus(STATUS_PROCESSING);
        baseMapper.insert(handle);

        HandleRecord record = new HandleRecord();
        record.setHandleId(handle.getId());
        record.setPlotId(bo.getPlotId());
        record.setCropId(bo.getCropId());
        record.setRecordType(RECORD_TYPE_HANDLE);
        record.setRecordWeight(weight);
        record.setHandleTarget(HANDLE_TARGET_PLATFORM);
        record.setLocationId(null);
        record.setIsFinish(2);
        record.setHandleUser(userId);
        record.setHandleTime(now);
        handleRecordMapper.insert(record);
    }

    /**
     * 采摘去向[毛菜保鲜室]入库：落毛菜鲜品库 L0006，写 stock_flow(veg_stock_in) + location_stock(按 plot 篮)。
     * 复用 {@link #insertVegStockInFlow} 的库存写入口径，但不依赖 PlantingRecord/VegetableHandle 上下文。
     */
    private void insertPickStockIn(PickDestSubmitBo bo, BigDecimal weight, Long userId, Date now) {
        LocationInfo freshVegLoc = locationInfoMapper.selectOne(
            new LambdaQueryWrapper<LocationInfo>()
                .eq(LocationInfo::getLocationCode, LOCATION_CODE_FRESH_VEG)
                .last("LIMIT 1"));
        if (freshVegLoc == null) {
            throw new ServiceException("毛菜鲜品库（库位编码 " + LOCATION_CODE_FRESH_VEG + "）不存在，请先在库位管理维护");
        }
        Long locationId = freshVegLoc.getId();
        Long productId = resolveProductIdByCrop(bo.getCropId(), bo.getProductId());

        // stock_flow（与 insertVegStockInFlow 同口径：veg_stock_in / IN / 带 plot）
        StockFlow flow = new StockFlow();
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", INOUT_IN);
        flow.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx));
        flow.setFlowDate(now);
        if (productId == null) {
            throw new ServiceException("作物「" + bo.getCropName() + "」未关联果蔬成品，无法入库："
                + "请先在 admin 作物录入页填写「关联产品」建立作物↔成品映射后再提交");
        }
        flow.setProductId(productId);
        flow.setWarehouseId(locationId);
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(FLOW_TYPE_VEG_STOCK_IN);
        flow.setChangeNum(weight);
        flow.setChangeQuantity(weight);
        flow.setPlotId(bo.getPlotId());
        flow.setOperatorId(userId);
        flow.setRemark("采摘去向[毛菜保鲜室]入库 crop=" + bo.getCropName());
        stockFlowMapper.insert(flow);

        // location_stock 余额（与 insertVegStockInFlow 同：product 真实存在才写，按 plot 建篮）
        ProductInfo product = productInfoMapper.selectById(productId);
        if (product == null) {
            log.warn("采摘去向入库余额未回写：product_id={} 在 product_info 不存在（作物未正确关联果蔬成品）"
                + " — cropId={} crop={}", productId, bo.getCropId(), bo.getCropName());
            return;
        }
        if (bo.getPlotId() != null) {
            LocationStock basket = new LocationStock();
            basket.setLocationId(locationId);
            basket.setProductId(productId);
            basket.setPlotId(bo.getPlotId());
            basket.setProductName(product.getProductName());
            basket.setProductUnit(product.getProductUnit());
            basket.setProductStock(weight);
            basket.setIsEnd(0);
            basket.setOperatorId(userId);
            locationStockMapper.insert(basket);
            return;
        }
        int updated = locationStockMapper.addByProductLocation(locationId, productId, weight, userId);
        if (updated == 0) {
            LocationStock fresh = new LocationStock();
            fresh.setLocationId(locationId);
            fresh.setProductId(productId);
            fresh.setProductName(product.getProductName());
            fresh.setProductUnit(product.getProductUnit());
            fresh.setProductStock(weight);
            fresh.setIsEnd(0);
            fresh.setOperatorId(userId);
            locationStockMapper.insert(fresh);
        }
    }

    /**
     * 采摘去向[损耗]：写统一损耗台账 loss_flow（loss_type=veg_handle_loss，与毛菜处理损耗同类型）。
     */
    private void insertPickLoss(PickDestSubmitBo bo, BigDecimal weight, Long userId) {
        LossFlow lossFlow = new LossFlow();
        lossFlow.setLossType("veg_handle_loss");
        lossFlow.setLossWeight(weight);
        lossFlow.setProductId(resolveProductIdByCrop(bo.getCropId(), bo.getProductId()));
        lossFlow.setPlotId(bo.getPlotId());
        lossFlow.setBelongType(CROP_BELONG_TYPE);
        lossFlow.setSourceBizType("pick_dest");
        lossFlow.setOperatorId(userId);
        lossFlowService.record(lossFlow);
    }

    /**
     * 采摘去向[饲料饲喂]：写饲料台账 feed_log（feed_type=veg_handle，不记地块）。
     */
    private void insertPickFeed(PickDestSubmitBo bo, BigDecimal weight, Long userId, Date now) {
        FeedLog feedLog = new FeedLog();
        feedLog.setFeedDate(now);
        feedLog.setCropId(bo.getCropId());
        feedLog.setCropName(bo.getCropName());
        feedLog.setFeedType("veg_handle");
        feedLog.setProductId(resolveProductIdByCrop(bo.getCropId(), bo.getProductId()));
        feedLog.setOperatorId(userId);
        feedLog.setFeedWeight(weight);
        feedLogMapper.insert(feedLog);
    }

    /**
     * 去向③饲料饲喂 → 插入饲料台账（{@code t_warehouse_feed_log}，spec 步8）。
     *
     * <p>按自然日 × 作物品类记录重量，{@code 不记录地块编号}（无 plot_id）。tenant_id 走 MP 自动 fill。</p>
     *
     * <p>行64 来源①「毛菜间」：feed_type=veg_handle；productId 经 crop→related_product 反解（可空）；
     * operatorId = 当前处理操作人。</p>
     */
    private void insertFeedLog(PlantingRecord planting, BigDecimal weight, Long operatorId, Date now) {
        FeedLog feedLog = new FeedLog();
        feedLog.setFeedDate(now);
        feedLog.setCropId(planting.getCropId());
        feedLog.setCropName(planting.getCropName());
        feedLog.setFeedType("veg_handle");
        feedLog.setProductId(resolveProductIdByCrop(planting.getCropId(), planting.getProductId()));
        feedLog.setOperatorId(operatorId);
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
        boolean hasHandleStatuses = query.getHandleStatuses() != null && !query.getHandleStatuses().isEmpty();
        wrapper.eq(query.getPlotId() != null, VegetableHandle::getPlotId, query.getPlotId())
            .eq(query.getCropId() != null, VegetableHandle::getCropId, query.getCropId())
            .eq(query.getPlantingRecordId() != null, VegetableHandle::getPlantingRecordId, query.getPlantingRecordId())
            .in(hasHandleStatuses, VegetableHandle::getHandleStatus, query.getHandleStatuses())
            .eq(!hasHandleStatuses && query.getHandleStatus() != null && !query.getHandleStatus().isBlank(),
                VegetableHandle::getHandleStatus, query.getHandleStatus())
            .ge(query.getPickStartTimeFrom() != null, VegetableHandle::getPickStartTime, query.getPickStartTimeFrom())
            .le(query.getPickStartTimeTo() != null, VegetableHandle::getPickStartTime, query.getPickStartTimeTo())
            .orderByDesc(VegetableHandle::getId);
        return wrapper;
    }

    @Override
    public TableDataInfo<PickDetailVo> queryPickDetailPage(PickDetailQuery query, PageQuery pageQuery) {
        PickDetailQuery q = query == null ? new PickDetailQuery() : query;
        Page<PickDetailVo> page = handleRecordMapper.selectPickDetailPage(pageQuery.build(), q);
        return TableDataInfo.build(page);
    }

    @Override
    public void exportPickDetail(PickDetailQuery query, HttpServletResponse response) {
        PickDetailQuery q = query == null ? new PickDetailQuery() : query;
        List<PickDetailVo> list = handleRecordMapper.selectPickDetailList(q);
        ExcelUtil.exportExcel(list, "采摘明细", PickDetailVo.class, response);
    }

}
