package org.dromara.djs.warehouse.cut.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.common.supplier.domain.Supplier;
import org.dromara.djs.common.supplier.mapper.SupplierMapper;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.cut.domain.PigCutRecord;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutDoneBo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutOutBo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutPickupBo;
import org.dromara.djs.warehouse.cut.domain.query.PigCutRecordQuery;
import org.dromara.djs.warehouse.cut.domain.vo.BarInfoVo;
import org.dromara.djs.warehouse.cut.domain.vo.BarPickupItemVo;
import org.dromara.djs.warehouse.cut.domain.vo.CutProductTypeVo;
import org.dromara.djs.warehouse.cut.domain.vo.PigCutRecordVo;
import org.dromara.djs.warehouse.cut.mapper.PigCutRecordMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.loss.domain.LossFlow;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.dromara.djs.warehouse.trace.domain.TraceContentConst;
import org.dromara.djs.warehouse.trace.service.ITraceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 分割工序记录 Service 实现（WMS-PIG-002）。
 *
 * <h3>3 阶段事务跨表一致性</h3>
 * <ul>
 *   <li>{@link #submitPickup}：cut_record INSERT + bar_info status 推进（乐观锁）</li>
 *   <li>{@link #submitCutOut}：多部位 INSERT product_inhouse + N+1 行 stock_flow + cut_record/bar_info status 推进</li>
 *   <li>{@link #submitCutDone}：cut_record done + bar_info cut_done + 出库字段写入</li>
 * </ul>
 *
 * <h3>并发安全</h3>
 * <ul>
 *   <li>bar_info status 推进用 {@code WHERE status='?'} 乐观锁，affectedRows==0 → 抛"白条状态不符"</li>
 *   <li>cut_record status 推进同样乐观锁</li>
 *   <li>UNIQUE (tenant_id, cut_id, del_unique) 保证 cut_id 幂等</li>
 * </ul>
 *
 * <h3>cut_id 生成（inline，同 D8 burn_id 模式）</h3>
 * <p>{@code CUT+yyMMdd+4 位}（日内重置）。SYS-INFRA-004 BizCodeService 尚无 {@code CUT_NO} 类型，
 * inline 用 {@code selectMaxCutIdByDate} + UNIQUE 兜底。后续 follow-up D10/D11 治理。</p>
 *
 * @author djs
 * @since WMS-PIG-002
 */
@Slf4j
@Service
public class PigCutRecordServiceImpl
    extends DjsBaseServiceImpl<PigCutRecordMapper, PigCutRecord>
    implements org.dromara.djs.warehouse.cut.service.IPigCutRecordService {

    /**
     * 字典 djs_pig_cut_part → 业务码后缀映射（{@code lean → LEAN}）。
     * 分割品标准 SKU 业务码 {@code PROD-PIG-<PART>-01}，由本 ticket V202606071100 seed。
     */
    private static final String CUT_PART_PRODUCT_CODE_PREFIX = "PROD-PIG-";
    private static final String CUT_PART_PRODUCT_CODE_SUFFIX = "-01";

    /**
     * 白条产品业务码（D08-CLOSING seed PROD-WHITE-BAR-01）。
     */
    private static final String WHITE_BAR_PRODUCT_BIZ_CODE = "PROD-WHITE-BAR-01";

    /**
     * 分割车间车间码（{@code t_warehouse_product_info.product_workshop}，字典 djs_product_workshop = 2）。
     */
    private static final Integer PRODUCT_WORKSHOP_CUT = 2;

    /**
     * 分割成品 belong_type（猪肉）。
     */
    private static final String CUT_PRODUCT_BELONG_TYPE = "pork";

    /**
     * 产品属性=原材料（djs_product_attr：1=生产产品 / 2=原材料）。分割车间只产出/可选原材料（doc/14 §3）。
     */
    private static final Integer PRODUCT_ATTR_MATERIAL = 2;

    /**
     * stock_flow.flow_type 分割品入冻品库流水。
     */
    private static final String FLOW_TYPE_CUT_OUT_IN = "cut_out_in";

    /**
     * stock_flow.flow_type 白条分割出库流水。
     */
    private static final String FLOW_TYPE_CUT_OUT = "cut_out";
    /** 出库去向：白条分割（FIX-WMS-FLOWDICT-001，白条出库固定去分割间）。 */
    private static final String STOCK_OUT_DEST_BAR_CUT = "bar_cut";

    /** 损耗类型（字典 djs_loss_type）：预冷损耗 = 白条入库重 − 出库重（= dripLoss）。 */
    private static final String LOSS_TYPE_PRECOOL = "precool_loss";
    /** 损耗类型（字典 djs_loss_type）：分割损耗 = 白条出库重 − 分割产品重量之和。 */
    private static final String LOSS_TYPE_CUT = "cut_loss";
    /** loss_flow.source_biz_type 来源标识：分割。 */
    private static final String LOSS_SOURCE_BIZ_CUT = "cut";

    /**
     * stock_flow.inout_type CHAR(3) IN=入库 / OT=出库。
     */
    private static final String INOUT_IN = "IN";

    private static final String INOUT_OUT = "OT";

    /**
     * cut_record.cut_status 值。
     */
    private static final String CUT_STATUS_PICKED = "picked";
    private static final String CUT_STATUS_CUTTING = "cutting";
    private static final String CUT_STATUS_DONE = "done";

    /**
     * bar_info.status 值。
     */
    private static final String BAR_STATUS_IN_STOCK = "in_stock";
    private static final String BAR_STATUS_PENDING_CUT = "pending_cut";
    private static final String BAR_STATUS_CUTTING = "cutting";

    private final BarInfoMapper barInfoMapper;
    private final StockFlowMapper stockFlowMapper;
    private final ProductInfoMapper productInfoMapper;
    private final ProductInhouseMapper productInhouseMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final LocationStockMapper locationStockMapper;
    private final SupplierMapper supplierMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final ITraceService traceService;
    private final ImageUrlResolver imageUrlResolver;
    private final IStockCheckService stockCheckService;
    private final ILossFlowService lossFlowService;

    public PigCutRecordServiceImpl(PigCutRecordMapper baseMapper,
                                   BarInfoMapper barInfoMapper,
                                   StockFlowMapper stockFlowMapper,
                                   ProductInfoMapper productInfoMapper,
                                   ProductInhouseMapper productInhouseMapper,
                                   LocationInfoMapper locationInfoMapper,
                                   LocationStockMapper locationStockMapper,
                                   SupplierMapper supplierMapper,
                                   IBizCodeGenerator bizCodeGenerator,
                                   ITraceService traceService,
                                   ImageUrlResolver imageUrlResolver,
                                   IStockCheckService stockCheckService,
                                   ILossFlowService lossFlowService) {
        super(baseMapper);
        this.barInfoMapper = barInfoMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.productInfoMapper = productInfoMapper;
        this.productInhouseMapper = productInhouseMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.locationStockMapper = locationStockMapper;
        this.supplierMapper = supplierMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.traceService = traceService;
        this.imageUrlResolver = imageUrlResolver;
        this.stockCheckService = stockCheckService;
        this.lossFlowService = lossFlowService;
    }

    /**
     * 阶段 1：白条领用。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitPickup(PigCutPickupBo bo) {
        Long userId = LoginHelper.getUserId();

        // Step 1：SELECT bar_info，校验 status='in_stock'
        BarInfo bar = barInfoMapper.selectById(bo.getBarInfoId());
        if (bar == null) {
            throw new ServiceException("白条不存在：" + bo.getBarInfoId());
        }
        if (!BAR_STATUS_IN_STOCK.equals(bar.getStatus())) {
            throw new ServiceException("白条状态不符（当前：" + bar.getStatus() + "，需 in_stock）");
        }

        // FIX-WMS-CUTPICKUP-SPLIT-001：admin 按燎毛产出行逐条领用（inhouseId 非空）→ 拆条路径；
        // mp 旧端 / 整只兜底（inhouseId 空）→ 整猪路径（行为同旧）。
        if (bo.getInhouseId() != null) {
            return pickupByInhouseRow(bo, bar, userId);
        }
        return pickupWholeBar(bo, bar, userId);
    }

    /**
     * 整猪领用（mp 旧端 / 白条无燎毛产出行兜底）：推 bar in_stock→pending_cut + 建一个整猪 cut_record。
     */
    private Long pickupWholeBar(PigCutPickupBo bo, BarInfo bar, Long userId) {
        // 领用称重：现场录入优先，未录入回落 in_weight 快照（兼容 mp 旧端 / 不录重场景）
        BigDecimal pickupWeight = bo.getPickupWeight() != null ? bo.getPickupWeight() : bar.getInWeight();
        // 校验：领用称重不应大于该白条出栏重量（marketing_weight）
        if (pickupWeight != null && bar.getMarketingWeight() != null
            && pickupWeight.compareTo(bar.getMarketingWeight()) > 0) {
            throw new ServiceException("领用称重（" + pickupWeight + "kg）不应大于该白条出栏重量（"
                + bar.getMarketingWeight() + "kg）");
        }
        // UPDATE bar_info status → pending_cut（乐观锁）
        int affected = barInfoMapper.updateStatusToPendingCut(bar.getId(), userId);
        if (affected == 0) {
            throw new ServiceException("白条已被并发领用，请刷新重试");
        }
        return insertCutRecord(bar, pickupWeight, bo.getLocationId(), bo.getTargetStoreId(),
            bo.getTargetDemandId(), bo.getIsHalf() == null ? 2 : bo.getIsHalf(), bo.getRemark(), userId);
    }

    /**
     * 按燎毛产出行领用（admin 拆条，FIX-WMS-CUTPICKUP-SPLIT-001）：
     * 置该行 {@code pickup_status=1} + 记 {@code pickup_weight}；该白条所有产出行领满 →
     * 推 bar in_stock→pending_cut + 建一个整猪 cut_record（pickup_weight = 各行之和）。下游分割/损耗/追溯仍按整猪聚合。
     *
     * @return 领满 → 新 cut_record id；未领满（还有其他半只待领）→ {@code null}
     */
    private Long pickupByInhouseRow(PigCutPickupBo bo, BarInfo bar, Long userId) {
        ProductInhouse row = productInhouseMapper.selectById(bo.getInhouseId());
        if (row == null || !bar.getId().equals(row.getWhiteBarId())) {
            throw new ServiceException("燎毛产出行不存在或不属于该白条：" + bo.getInhouseId());
        }
        if (row.getPickupStatus() != null && row.getPickupStatus() == 1) {
            throw new ServiceException("该产出行已领用，请刷新重试");
        }
        // 本行领用过磅：现场录入优先，未录入回落该产出行燎毛入库重量
        BigDecimal rowWeight = bo.getPickupWeight() != null ? bo.getPickupWeight() : row.getProductWeight();
        if (rowWeight == null || rowWeight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("领用过磅重量须大于 0");
        }
        // 校验：该白条「已领行之和 + 本次」不应大于出栏重量（整猪上界，累计口径）
        BigDecimal alreadyPicked = sumPickedRowWeight(bar.getId());
        if (bar.getMarketingWeight() != null
            && alreadyPicked.add(rowWeight).compareTo(bar.getMarketingWeight()) > 0) {
            throw new ServiceException("该白条累计领用（" + alreadyPicked.add(rowWeight)
                + "kg）不应大于出栏重量（" + bar.getMarketingWeight() + "kg）");
        }
        // 乐观锁置该行已领（WHERE pickup_status=0/NULL 防并发重复领）
        int marked = productInhouseMapper.update(null,
            new LambdaUpdateWrapper<ProductInhouse>()
                .eq(ProductInhouse::getId, row.getId())
                .and(w -> w.eq(ProductInhouse::getPickupStatus, 0).or().isNull(ProductInhouse::getPickupStatus))
                .set(ProductInhouse::getPickupStatus, 1)
                .set(ProductInhouse::getPickupWeight, rowWeight));
        if (marked == 0) {
            throw new ServiceException("该产出行已被并发领用，请刷新重试");
        }
        // 该白条还有未领产出行 → 不推 bar、不建 cut_record（分两次领，剩余行继续显示）
        Long remaining = productInhouseMapper.selectCount(
            new LambdaQueryWrapper<ProductInhouse>()
                .eq(ProductInhouse::getWhiteBarId, bar.getId())
                .and(w -> w.eq(ProductInhouse::getPickupStatus, 0).or().isNull(ProductInhouse::getPickupStatus)));
        if (remaining != null && remaining > 0) {
            return null;
        }
        // 全部领满 → 推 bar in_stock→pending_cut + 建一个整猪 cut_record（pickup_weight = 各行之和）
        int affected = barInfoMapper.updateStatusToPendingCut(bar.getId(), userId);
        if (affected == 0) {
            throw new ServiceException("白条已被并发领用，请刷新重试");
        }
        BigDecimal totalPicked = sumPickedRowWeight(bar.getId());
        // 多产出行（被拆半只领的）→ isHalf=1（半扇分割）；单行 → 2（整只）
        Long pickedRows = productInhouseMapper.selectCount(
            new LambdaQueryWrapper<ProductInhouse>()
                .eq(ProductInhouse::getWhiteBarId, bar.getId())
                .eq(ProductInhouse::getPickupStatus, 1));
        int isHalf = pickedRows != null && pickedRows > 1 ? 1 : 2;
        return insertCutRecord(bar, totalPicked, bo.getLocationId(), bo.getTargetStoreId(),
            bo.getTargetDemandId(), isHalf, bo.getRemark(), userId);
    }

    /** 该白条已领产出行 pickup_weight 之和（pickup_status=1）。 */
    private BigDecimal sumPickedRowWeight(Long whiteBarId) {
        List<ProductInhouse> picked = productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .select(ProductInhouse::getPickupWeight)
                .eq(ProductInhouse::getWhiteBarId, whiteBarId)
                .eq(ProductInhouse::getPickupStatus, 1));
        BigDecimal sum = BigDecimal.ZERO;
        for (ProductInhouse r : picked) {
            if (r.getPickupWeight() != null) {
                sum = sum.add(r.getPickupWeight());
            }
        }
        return sum;
    }

    /** 建整猪 cut_record（cut_status=picked），整猪 / 拆条领满两路径共用。 */
    private Long insertCutRecord(BarInfo bar, BigDecimal pickupWeight, Long locationId,
                                 Long targetStoreId, Long targetDemandId, int isHalf,
                                 String remark, Long userId) {
        PigCutRecord record = new PigCutRecord();
        record.setCutId(generateCutId());
        record.setWhiteBarId(bar.getId());
        record.setBarId(bar.getBarId());
        record.setEarNo(bar.getEarNo());
        record.setPickupTime(new Date());
        record.setPickupWeight(pickupWeight);
        record.setOperatorId(userId);
        record.setLocationId(locationId);
        record.setTargetStoreId(targetStoreId);
        record.setTargetDemandId(targetDemandId);
        record.setIsHalf(isHalf);
        record.setCutStatus(CUT_STATUS_PICKED);
        record.setRemark(remark);
        baseMapper.insert(record);
        return record.getId();
    }

    /**
     * 阶段 2：出库称重（多部位提交）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitCutOut(PigCutOutBo bo) {
        Long userId = LoginHelper.getUserId();

        // Step 1：SELECT cut_record，校验 cut_status='picked' or 'cutting'
        PigCutRecord record = baseMapper.selectById(bo.getCutRecordId());
        if (record == null) {
            throw new ServiceException("分割单不存在：" + bo.getCutRecordId());
        }
        if (!CUT_STATUS_PICKED.equals(record.getCutStatus())
            && !CUT_STATUS_CUTTING.equals(record.getCutStatus())) {
            throw new ServiceException("分割单状态不符（当前：" + record.getCutStatus()
                + "，需 picked 或 cutting）");
        }

        // Step 1.5：超量校验（本次提交合计 + 该白条已分割产出 ≤ 领用白条重 pickup_weight）。
        // 口径与 fillRemainingWeight 一致：已分割量按 white_bar_id 聚合 cut_out_in 流水
        // （外购白条 ear_no=NULL 也能稳定关联，避免按 ear_no 校验对外购失效）。
        BigDecimal pickupWeight = record.getPickupWeight();
        if (pickupWeight != null) {
            BigDecimal requestedTotal = BigDecimal.ZERO;
            for (PigCutOutBo.PartItem part : bo.getPartItems()) {
                if (part.getProductWeight() != null) {
                    requestedTotal = requestedTotal.add(part.getProductWeight());
                }
            }
            BigDecimal alreadyCut = stockFlowMapper.sumCutOutByWhiteBarId(record.getWhiteBarId());
            BigDecimal afterTotal = requestedTotal.add(alreadyCut == null ? BigDecimal.ZERO : alreadyCut);
            if (afterTotal.compareTo(pickupWeight) > 0) {
                BigDecimal remaining = pickupWeight
                    .subtract(alreadyCut == null ? BigDecimal.ZERO : alreadyCut)
                    .max(BigDecimal.ZERO);
                throw new ServiceException("分割产品重量超出剩余可分割重量（本次 " + requestedTotal
                    + "kg，剩余 " + remaining + "kg）");
            }
        }

        // Step 2：首次提交 → cut_record picked → cutting + bar_info pending_cut → cutting
        Date now = new Date();
        if (CUT_STATUS_PICKED.equals(record.getCutStatus())) {
            int affected = baseMapper.updateStatusToCutting(record.getId(), now, userId);
            if (affected == 0) {
                throw new ServiceException("分割单状态已变更，请刷新重试");
            }
            // bar_info pending_cut → cutting（已是 cutting 则跳过，affectedRows==0 不抛）
            barInfoMapper.updateStatusToCutting(record.getWhiteBarId(), userId);
        }

        // P4 库位兜底：前端未传 locationId 时按所选分割产品配置的 store_location_id 解析（参 ProductProductionServiceImpl.resolveLocationId）
        Long effectiveLocationId = resolveCutLocationId(bo.getLocationId(), bo.getPartItems());

        // 校验 locationId 存在 + 是冻品库
        LocationInfo location = locationInfoMapper.selectById(effectiveLocationId);
        if (location == null) {
            throw new ServiceException("入冻品库位不存在：" + effectiveLocationId);
        }

        // 库位级业务锁（WMS-STOCK-001）：盘点进行中的冻品库禁出入库（后端双保险，写 location_stock 篮子前置）
        stockCheckService.assertLocationUnlocked(effectiveLocationId);

        // Step 3：for each part → INSERT location_stock 篮子(入冷库) + INSERT stock_flow IN(cut_out_in 不可变审计)
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (PigCutOutBo.PartItem part : bo.getPartItems()) {
            // 按具体产品对齐原型（Kevin 定）：productId 非空 → 直接用该分割成品落库；
            // 否则回落 cutPart 5 部位枚举解析标准 SKU（向后兼容 mp 旧端 + 成品主数据未 seed）。
            Long productId;
            String productName;
            String productUnit;
            if (part.getProductId() != null) {
                ProductInfo product = productInfoMapper.selectById(part.getProductId());
                if (product == null) {
                    throw new ServiceException("分割成品产品主数据不存在：" + part.getProductId());
                }
                productId = product.getId();
                productName = product.getProductName();
                productUnit = StringUtils.isNotBlank(product.getProductUnit()) ? product.getProductUnit() : "kg";
            } else if (StringUtils.isNotBlank(part.getCutPart())) {
                productId = resolveProductIdByCutPart(part.getCutPart());
                productName = productNameByCutPart(part.getCutPart());
                productUnit = "kg";
            } else {
                throw new ServiceException("分割明细须指定具体产品(productId) 或部位(cutPart) 之一");
            }

            // 分割部位入冷库 = 一个「篮子」（doc/14 §1：分割→入库，不直接产待打包 WIP）：
            // 每头猪每部位一行 location_stock，ear_no 作篮子标签（追溯键）。mp 物资领用按产品聚合展示这些篮子；
            // 据门店需求领用时按篮子 FIFO 扣减并产出 product_inhouse（带 ear_no）→ 肉品打包来源。
            LocationStock basket = new LocationStock();
            basket.setLocationId(effectiveLocationId);
            basket.setProductId(productId);
            basket.setEarNo(record.getEarNo());
            basket.setProductName(productName);
            basket.setProductUnit(productUnit);
            basket.setProductStock(part.getProductWeight());
            basket.setIsEnd(0);
            basket.setOperatorId(userId);
            locationStockMapper.insert(basket);

            // 分割品入冻品库流水
            StockFlow flowIn = new StockFlow();
            Map<String, Object> flowCtxIn = new HashMap<>(2);
            flowCtxIn.put("ioCode", INOUT_IN);
            flowIn.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, flowCtxIn));
            flowIn.setFlowDate(now);
            flowIn.setProductId(productId);
            flowIn.setWarehouseId(effectiveLocationId);
            flowIn.setInoutType(INOUT_IN);
            flowIn.setFlowType(FLOW_TYPE_CUT_OUT_IN);
            flowIn.setChangeNum(part.getProductWeight());
            flowIn.setChangeQuantity(part.getProductWeight());
            flowIn.setEarNo(record.getEarNo());
            // 分割产出按 white_bar_id 关联白条（外购无耳号也稳定可聚合，剩余重量/超量校验统一口径）
            flowIn.setWhiteBarId(record.getWhiteBarId());
            flowIn.setOperatorId(userId);
            flowIn.setRemark("分割产出入冻品库 cut_id=" + record.getCutId() + " part=" + part.getCutPart());
            stockFlowMapper.insert(flowIn);

            totalWeight = totalWeight.add(part.getProductWeight());
        }

        // Step 4：INSERT 白条总出库流水（合计 weight，关联白条 product_id）
        StockFlow flowOut = new StockFlow();
        Map<String, Object> flowCtxOut = new HashMap<>(2);
        flowCtxOut.put("ioCode", INOUT_OUT);
        flowOut.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, flowCtxOut));
        flowOut.setFlowDate(now);
        flowOut.setProductId(resolveWhiteBarProductId());
        flowOut.setWarehouseId(record.getLocationId());
        flowOut.setInoutType(INOUT_OUT);
        flowOut.setFlowType(FLOW_TYPE_CUT_OUT);
        // 白条出库去向固定为分割间（FIX-WMS-FLOWDICT-001，前端只读不可改）
        flowOut.setStockOutDest(STOCK_OUT_DEST_BAR_CUT);
        flowOut.setChangeNum(totalWeight);
        flowOut.setChangeQuantity(totalWeight);
        flowOut.setEarNo(record.getEarNo());
        flowOut.setOperatorId(userId);
        flowOut.setRemark("白条分割出库 cut_id=" + record.getCutId() + " 部位数=" + bo.getPartItems().size());
        stockFlowMapper.insert(flowOut);

        // Step 5：proof_oss_ids 增量更新（如有）
        if (StringUtils.isNotBlank(bo.getProofOssIds())) {
            PigCutRecord upd = new PigCutRecord();
            upd.setId(record.getId());
            upd.setProofOssIds(bo.getProofOssIds());
            baseMapper.updateById(upd);
        }
    }

    /**
     * 阶段 3：出库完成。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitCutDone(PigCutDoneBo bo) {
        Long userId = LoginHelper.getUserId();

        // Step 1：SELECT cut_record，校验 cut_status='cutting'
        PigCutRecord record = baseMapper.selectById(bo.getCutRecordId());
        if (record == null) {
            throw new ServiceException("分割单不存在：" + bo.getCutRecordId());
        }
        if (!CUT_STATUS_CUTTING.equals(record.getCutStatus())) {
            throw new ServiceException("分割单状态不符（当前：" + record.getCutStatus()
                + "，需 cutting）");
        }
        if (record.getPickupTime() == null) {
            throw new ServiceException("分割单缺 pickup_time，数据异常");
        }
        if (record.getPickupWeight() == null) {
            throw new ServiceException("分割单缺 pickup_weight，数据异常");
        }

        Date now = new Date();
        int acidMinutes = (int) Duration.between(
            record.getPickupTime().toInstant(), now.toInstant()).toMinutes();

        // 滴水损耗由系统自动计算（前端不再录入）：白条入库重量 − 白条出库重量。
        // 白条入库重量 = bar_info.in_weight（燎毛入库快照）；白条出库重量 = 领用称重 pickup_weight。
        // 入库重量缺失（外购 / 旧数据 in_weight=NULL）或差值为负（脏数据 pickup>in）时钳到 0（损耗不可为负）。
        BigDecimal pickupWeight = record.getPickupWeight();
        BarInfo bar = barInfoMapper.selectById(record.getWhiteBarId());
        BigDecimal inWeight = bar == null ? null : bar.getInWeight();
        BigDecimal dripLoss = inWeight == null
            ? BigDecimal.ZERO
            : inWeight.subtract(pickupWeight).max(BigDecimal.ZERO);
        // 白条出库重量 = 领用称重（滴水损耗已作为独立损耗项，不再从出库重量二次扣减）
        BigDecimal outWeight = pickupWeight;

        // Step 2：UPDATE cut_record cutting → done
        int affected = baseMapper.updateStatusToDone(
            record.getId(), now, dripLoss, acidMinutes,
            bo.getRemark(), bo.getProofOssIds(), userId);
        if (affected == 0) {
            throw new ServiceException("分割单状态已变更，请刷新重试");
        }

        // Step 3：UPDATE bar_info cutting → cut_done
        int barAffected = barInfoMapper.updateStatusToCutDone(
            record.getWhiteBarId(), now, outWeight, acidMinutes, dripLoss, userId);
        if (barAffected == 0) {
            throw new ServiceException("白条状态不符或已被并发更新，请刷新重试");
        }

        // TRC-CORE-001：屠宰分割 + 排酸追溯事件（分割完成同时记两条；按耳号反查 trace_code，
        // 猪肉链当前无生成入口 → warn 跳过，不拖垮分割事务）
        // 追溯时间轴每节点重量：分割 / 排酸节点重量 = 白条出库重量 outWeight（= pickupWeight）
        traceService.recordEventByEarNo(record.getEarNo(), TraceContentConst.SLAUGHTER, outWeight);
        traceService.recordEventByEarNo(record.getEarNo(), TraceContentConst.ACID, outWeight);

        // WMS-LOSS-001 行62：分割完成结算点统一损耗双写（在原 cut_record.drip_loss / bar_info.drip_loss
        // 之上追加 loss_flow 两条明细，作损耗总览数据源）。负值/0 由门面自动跳过。
        // 该判定点由 cutting→done 乐观锁保证只走一次，故只在此写一次，不重复。
        writeCutLossFlows(record, dripLoss, outWeight, userId);
    }

    /**
     * 分割完成结算双写损耗（WMS-LOSS-001 行62）。
     *
     * <ul>
     *   <li>预冷损耗 {@code precool_loss} = 白条入库重 − 出库重（= 已算好的 {@code dripLoss}，公式同义，不重算）；
     *       关联白条产品 id。</li>
     *   <li>分割损耗 {@code cut_loss} = 白条出库重 − 该白条所有分割产品重量之和
     *       （Σ {@code cut_out_in} 不可变流水 by white_bar_id，与 {@code fillRemainingWeight} 口径一致）。</li>
     * </ul>
     *
     * <p>负值/0 由 {@link ILossFlowService#record} 自动跳过，无需调用方判断。</p>
     */
    private void writeCutLossFlows(PigCutRecord record, BigDecimal dripLoss,
                                   BigDecimal outWeight, Long userId) {
        // ① 预冷损耗（= dripLoss，复用已算量，不重算）
        LossFlow precool = new LossFlow();
        precool.setLossType(LOSS_TYPE_PRECOOL);
        precool.setLossWeight(dripLoss);
        precool.setProductId(resolveWhiteBarProductId());
        precool.setEarNo(record.getEarNo());
        precool.setOperatorId(userId);
        precool.setSourceBizType(LOSS_SOURCE_BIZ_CUT);
        precool.setSourceBizId(record.getId());
        lossFlowService.record(precool);

        // ② 分割损耗 = 出库重 − 分割产品重量之和（Σ cut_out_in by white_bar_id）
        BigDecimal cutTotal = stockFlowMapper.sumCutOutByWhiteBarId(record.getWhiteBarId());
        BigDecimal cutLoss = outWeight.subtract(cutTotal == null ? BigDecimal.ZERO : cutTotal);
        LossFlow cut = new LossFlow();
        cut.setLossType(LOSS_TYPE_CUT);
        cut.setLossWeight(cutLoss);
        cut.setProductId(resolveWhiteBarProductId());
        cut.setEarNo(record.getEarNo());
        cut.setOperatorId(userId);
        cut.setSourceBizType(LOSS_SOURCE_BIZ_CUT);
        cut.setSourceBizId(record.getId());
        lossFlowService.record(cut);
    }

    @Override
    public TableDataInfo<PigCutRecordVo> queryPageList(PigCutRecordQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PigCutRecord> wrapper = buildQueryWrapper(query);
        Page<PigCutRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillLocationNames(page.getRecords());
        fillRemainingWeight(page.getRecords());
        fillOutsourceAndStatistics(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<PigCutRecordVo> queryList(PigCutRecordQuery query) {
        List<PigCutRecordVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillLocationNames(list);
        fillRemainingWeight(list);
        fillOutsourceAndStatistics(list);
        return list;
    }

    @Override
    public PigCutRecordVo queryById(Long id) {
        PigCutRecordVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillLocationNames(List.of(vo));
            fillRemainingWeight(List.of(vo));
            fillOutsourceAndStatistics(List.of(vo));
        }
        return vo;
    }

    @Override
    public List<BarInfoVo> queryAvailableBars() {
        List<BarInfo> bars = barInfoMapper.selectList(
            new LambdaQueryWrapper<BarInfo>()
                .eq(BarInfo::getStatus, BAR_STATUS_IN_STOCK)
                .orderByDesc(BarInfo::getInTime)
                .last("LIMIT 50"));
        // FIX-WMS-OUTSOURCE-001 行51：批量取各白条燎毛实际产出的分产品（半只/五花肉/整只 等），
        // 白条领用卡片据此展示「燎毛产出明细 + 各自重量」，取代仅显「白条(整只)+整猪重量」。
        Map<Long, List<BarInfoVo.BurnProduct>> burnProductMap = loadBurnProducts(bars);
        return bars.stream().map(b -> {
            BarInfoVo vo = new BarInfoVo();
            vo.setId(b.getId());
            vo.setBarId(b.getBarId());
            vo.setEarNo(b.getEarNo());
            vo.setMarketingWeight(b.getMarketingWeight());
            vo.setInWeight(b.getInWeight());
            vo.setInTime(b.getInTime());
            vo.setStatus(b.getStatus());
            vo.setBurnProducts(burnProductMap.getOrDefault(b.getId(), List.of()));
            return vo;
        }).toList();
    }

    @Override
    public List<BarPickupItemVo> queryPickupItems() {
        List<BarInfo> bars = barInfoMapper.selectList(
            new LambdaQueryWrapper<BarInfo>()
                .eq(BarInfo::getStatus, BAR_STATUS_IN_STOCK)
                .orderByDesc(BarInfo::getInTime)
                .last("LIMIT 50"));
        if (bars.isEmpty()) {
            return List.of();
        }
        List<Long> barIds = bars.stream().map(BarInfo::getId).filter(Objects::nonNull).toList();
        // 各白条「未领」燎毛产出行（pickup_status=0/NULL），按 id 升序保留燎毛入库顺序
        List<ProductInhouse> rows = barIds.isEmpty() ? List.of() : productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .in(ProductInhouse::getWhiteBarId, barIds)
                .and(w -> w.eq(ProductInhouse::getPickupStatus, 0).or().isNull(ProductInhouse::getPickupStatus))
                .orderByAsc(ProductInhouse::getId));
        Map<Long, List<ProductInhouse>> rowsByBar = new HashMap<>();
        for (ProductInhouse r : rows) {
            if (r.getWhiteBarId() != null) {
                rowsByBar.computeIfAbsent(r.getWhiteBarId(), k -> new ArrayList<>()).add(r);
            }
        }
        List<BarPickupItemVo> result = new ArrayList<>();
        for (BarInfo bar : bars) {
            List<ProductInhouse> rs = rowsByBar.get(bar.getId());
            if (rs != null && !rs.isEmpty()) {
                // 燎毛多产出行 → 每行一张可单独领用的卡（半只 / 半扇 各一张）
                for (ProductInhouse r : rs) {
                    result.add(toPickupItem(bar, r));
                }
            } else {
                // 燎毛无产出行 → 整只兜底卡（inhouseId=null，领用走整猪路径），向后兼容旧数据
                result.add(toPickupItem(bar, null));
            }
        }
        return result;
    }

    /** 组装单张白条领用卡（row 非空 = 按产出行；row 空 = 整只兜底）。 */
    private BarPickupItemVo toPickupItem(BarInfo bar, ProductInhouse row) {
        BarPickupItemVo vo = new BarPickupItemVo();
        vo.setBarInfoId(bar.getId());
        vo.setBarId(bar.getBarId());
        vo.setEarNo(bar.getEarNo());
        // 外购无耳号 → chip 显白条标识号 mark_id（与 fillOutsourceAndStatistics 同口径）
        vo.setMarkId(bar.getSupplierId() != null ? bar.getMarkId() : null);
        vo.setMarketingWeight(bar.getMarketingWeight());
        vo.setInWeight(bar.getInWeight());
        vo.setInTime(bar.getInTime());
        if (row != null) {
            vo.setInhouseId(row.getId());
            vo.setProductName(row.getProductName());
            vo.setProductWeight(row.getProductWeight());
            vo.setProductUnit(StringUtils.isNotBlank(row.getProductUnit()) ? row.getProductUnit() : "kg");
        } else {
            vo.setInhouseId(null);
            vo.setProductName("白条（整只）");
            vo.setProductWeight(bar.getMarketingWeight() != null ? bar.getMarketingWeight() : bar.getInWeight());
            vo.setProductUnit("kg");
        }
        return vo;
    }

    /**
     * 批量取白条燎毛产出分产品（{@code product_inhouse WHERE white_bar_id IN}，避免 N+1）。
     *
     * @return white_bar_id → 该白条燎毛产出分产品列表（产品名 + 重量 + 单位，按 id 升序保留入库顺序）
     */
    private Map<Long, List<BarInfoVo.BurnProduct>> loadBurnProducts(List<BarInfo> bars) {
        if (bars == null || bars.isEmpty()) {
            return Map.of();
        }
        List<Long> barIds = bars.stream().map(BarInfo::getId).filter(Objects::nonNull).toList();
        if (barIds.isEmpty()) {
            return Map.of();
        }
        List<ProductInhouse> inhouses = productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .select(ProductInhouse::getWhiteBarId, ProductInhouse::getProductName,
                    ProductInhouse::getProductWeight, ProductInhouse::getProductUnit, ProductInhouse::getId)
                .in(ProductInhouse::getWhiteBarId, barIds)
                .orderByAsc(ProductInhouse::getId));
        Map<Long, List<BarInfoVo.BurnProduct>> map = new HashMap<>();
        for (ProductInhouse ih : inhouses) {
            if (ih.getWhiteBarId() == null) {
                continue;
            }
            BarInfoVo.BurnProduct bp = new BarInfoVo.BurnProduct();
            bp.setProductName(ih.getProductName());
            bp.setProductWeight(ih.getProductWeight());
            bp.setProductUnit(StringUtils.isNotBlank(ih.getProductUnit()) ? ih.getProductUnit() : "kg");
            map.computeIfAbsent(ih.getWhiteBarId(), k -> new ArrayList<>()).add(bp);
        }
        return map;
    }

    @Override
    public List<CutProductTypeVo> queryCutProductTypes() {
        // 分割车间产出 = 原材料(product_attr=2)（doc/14 §3：分割产「原料」入 WIP，打包按 product_material
        // 反查原料聚合；故分割只能选原料，不选生产产品）。belong_type='pork' + workshop=2 + attr=2，
        // 排除生产产品(attr=1)/商品/测试数据，按业务码升序。
        List<ProductInfo> types = productInfoMapper.selectList(
            new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getProductWorkshop, PRODUCT_WORKSHOP_CUT)
                .eq(ProductInfo::getBelongType, CUT_PRODUCT_BELONG_TYPE)
                .eq(ProductInfo::getProductAttr, PRODUCT_ATTR_MATERIAL)
                .orderByAsc(ProductInfo::getProductId));

        // IMG-LIB-001：批量解析产品图，禁 N+1。L1 优先用户上传缩略图 product_thumb，退回自动匹配 image_oss_id，
        // 再 L2 pork 默认图 → L3 全局兜底（admin 产品表单唯一图片入口写 product_thumb，须优先取）。
        List<ImageUrlResolver.Item> items = types.stream()
            .map(p -> new ImageUrlResolver.Item(
                StringUtils.isNotBlank(p.getProductThumb()) ? p.getProductThumb() : p.getImageOssId(),
                CUT_PRODUCT_BELONG_TYPE))
            .toList();
        List<String> urls = imageUrlResolver.resolveList(items);
        boolean urlsAligned = urls.size() == types.size();

        List<CutProductTypeVo> result = new ArrayList<>(types.size());
        for (int i = 0; i < types.size(); i++) {
            ProductInfo p = types.get(i);
            CutProductTypeVo vo = new CutProductTypeVo();
            vo.setProductId(p.getId());
            vo.setProductCode(p.getProductId());
            vo.setProductName(p.getProductName());
            vo.setProductUnit(StringUtils.isNotBlank(p.getProductUnit()) ? p.getProductUnit() : "kg");
            vo.setImageUrl(urlsAligned ? urls.get(i) : null);
            result.add(vo);
        }
        return result;
    }

    /**
     * 生成 cut_id：{@code CUT+yyMMdd+4 位}。protected 方便单测 stub。
     *
     * <p>D9 closing Group B 迁入 {@link IBizCodeGenerator}（BizCodeType.CUT_NO，
     * seed 在 V202606071600）—— Redisson 分布式锁 + 序号表 UNIQUE 双保护，
     * 取代原 SELECT MAX inline 实现。</p>
     */
    protected String generateCutId() {
        return bizCodeGenerator.generate(BizCodeType.CUT_NO, Map.of());
    }

    /**
     * 按 cut_part 解析分割品 product_id（D8 #11 决策 a 模式）。
     */
    protected Long resolveProductIdByCutPart(String cutPart) {
        String code = CUT_PART_PRODUCT_CODE_PREFIX + cutPart.toUpperCase() + CUT_PART_PRODUCT_CODE_SUFFIX;
        ProductInfo product = productInfoMapper.selectOne(
            new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getProductId, code)
                .last("LIMIT 1"));
        if (product == null || product.getId() == null) {
            throw new ServiceException(
                "分割品产品主数据缺失（product_id=" + code + "），请确认 V202606071100 seed 已执行");
        }
        return product.getId();
    }

    /**
     * 解析白条 product_id（同 D8 PigBurnRecordServiceImpl 实现）。
     */
    protected Long resolveWhiteBarProductId() {
        ProductInfo whiteBar = productInfoMapper.selectOne(
            new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getProductId, WHITE_BAR_PRODUCT_BIZ_CODE)
                .last("LIMIT 1"));
        if (whiteBar == null || whiteBar.getId() == null) {
            throw new ServiceException(
                "白条产品主数据缺失（product_id=" + WHITE_BAR_PRODUCT_BIZ_CODE
                    + "），请确认 V202606061060 seed 已执行");
        }
        return whiteBar.getId();
    }

    private String productNameByCutPart(String cutPart) {
        return switch (cutPart) {
            case "lean" -> "猪肉·精瘦肉";
            case "part" -> "猪肉·部位肉";
            case "bone" -> "猪肉·骨类";
            case "skin" -> "猪肉·猪皮";
            case "scrap" -> "猪肉·碎料";
            default -> "猪肉·未知部位";
        };
    }

    private void fillLocationNames(List<PigCutRecordVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> locationIds = records.stream()
            .map(PigCutRecordVo::getLocationId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (locationIds.isEmpty()) {
            return;
        }
        List<LocationInfo> locations = locationInfoMapper.selectList(
            new LambdaQueryWrapper<LocationInfo>().in(LocationInfo::getId, locationIds));
        Map<Long, String> nameMap = locations.stream()
            .collect(Collectors.toMap(LocationInfo::getId, LocationInfo::getLocationName, (a, b) -> a));
        for (PigCutRecordVo vo : records) {
            if (vo.getLocationId() != null) {
                vo.setLocationName(nameMap.get(vo.getLocationId()));
            }
        }
    }

    /**
     * 回填剩余可分割重量：remainingWeight = pickupWeight − 该白条已入库分割产品重量合计。
     *
     * <p>每次分割出库称重（cutOut）入库一件产品后，剩余重量随之减少，前端分割单 chip 据此展示
     * 当前白条还可继续分割的余量（不为负）。</p>
     */
    private void fillRemainingWeight(List<PigCutRecordVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (PigCutRecordVo vo : records) {
            // 按 white_bar_id 聚合（不依赖 ear_no）：外购白条无耳号也能算剩余重量（修复外购恒 null 不显示）
            if (vo.getPickupWeight() == null || vo.getWhiteBarId() == null) {
                continue;
            }
            // 已分割重量 = Σ cut_out_in 流水（不可变，doc/14 §1）；剩余可分割 = 领用白条重 − 已分割
            BigDecimal used = stockFlowMapper.sumCutOutByWhiteBarId(vo.getWhiteBarId());
            BigDecimal remaining = vo.getPickupWeight().subtract(used == null ? BigDecimal.ZERO : used);
            vo.setRemainingWeight(remaining.max(BigDecimal.ZERO));
        }
    }

    /**
     * 统计/外购标注金额计算保留小数位。
     */
    private static final int STAT_SCALE = 2;

    /**
     * P5 外购标注 + P7 统计指标 compute-on-read 一体回填（按 white_bar_id 关联 bar_info 派生，无持久列）。
     *
     * <p>一次批量取齐所需上游数据，避免 N+1：</p>
     * <ol>
     *   <li>按 white_bar_id 批量查 bar_info（取 supplier_id / arrive_weight / marketing_weight /
     *       in_weight / out_weight / in_time / out_time）；</li>
     *   <li>按出现的 supplier_id 批量查 supplier 名（参 {@code OutsourcePigServiceImpl#fillSupplierName} 范式）；</li>
     *   <li>按 ear_no 批量查分割品总重（{@code StockFlowMapper#sumCutOutGroupByEarNo}，读不可变 cut_out_in 流水）。</li>
     * </ol>
     *
     * <p>外购判定：{@code bar.supplierId != null}（已核实判据，外购优先 supplier_id）。
     * 外购行 {@code isOutsource=true} + {@code supplierName}；自养行 {@code isOutsource=false}、{@code supplierName=null}。</p>
     */
    private void fillOutsourceAndStatistics(List<PigCutRecordVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> whiteBarIds = records.stream()
            .map(PigCutRecordVo::getWhiteBarId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (whiteBarIds.isEmpty()) {
            return;
        }

        // ① 批量查 bar_info（white_bar_id = bar_info.id）
        List<BarInfo> bars = barInfoMapper.selectBatchIds(whiteBarIds);
        Map<Long, BarInfo> barMap = new HashMap<>(bars.size());
        for (BarInfo bar : bars) {
            barMap.put(bar.getId(), bar);
        }

        // ② 批量查 supplier 名（仅外购 bar 的 supplier_id）
        Set<Long> supplierIds = bars.stream()
            .map(BarInfo::getSupplierId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, String> supplierNameMap = new HashMap<>(supplierIds.size());
        if (!supplierIds.isEmpty()) {
            List<Supplier> suppliers = supplierMapper.selectBatchIds(supplierIds);
            for (Supplier s : suppliers) {
                supplierNameMap.put(s.getId(), s.getSupplierName());
            }
        }

        // ③ 批量查分割品总重（doc/14 §1：改读不可变 cut_out_in 流水 by ear_no——总产出量恒定，
        //    不随下游领用/打包消耗变动；分割产出已入 location_stock 篮子、不再写 product_inhouse）
        List<String> earNos = records.stream()
            .map(PigCutRecordVo::getEarNo)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .toList();
        Map<String, BigDecimal> cutWeightMap = new HashMap<>(earNos.size());
        if (!earNos.isEmpty()) {
            List<Map<String, Object>> sums = stockFlowMapper.sumCutOutGroupByEarNo(earNos);
            if (sums != null) {
                for (Map<String, Object> row : sums) {
                    Object earObj = row.get("earNo");
                    Object weightObj = row.get("totalWeight");
                    if (earObj != null && weightObj != null) {
                        cutWeightMap.put(earObj.toString(), toBigDecimal(weightObj));
                    }
                }
            }
        }

        for (PigCutRecordVo vo : records) {
            BarInfo bar = vo.getWhiteBarId() == null ? null : barMap.get(vo.getWhiteBarId());
            if (bar == null) {
                continue;
            }
            // P5 外购标注
            boolean outsource = bar.getSupplierId() != null;
            vo.setIsOutsource(outsource);
            vo.setSupplierName(outsource ? supplierNameMap.get(bar.getSupplierId()) : null);
            // FIX-WMS-OUTSOURCE-001 行53：外购无耳号，回填白条标识号 mark_id（分割单 chip 显示用，
            // 前端优先级 earNo ?? markId ?? barId ?? cutId，让外购记录显外购白条标识而非 CUT 业务码）
            vo.setMarkId(outsource ? bar.getMarkId() : null);

            // P7 统计指标（compute-on-read，防除零）
            // 头皮肉出品率 = arriveWeight / marketingWeight
            vo.setHeadSkinYieldRate(safeDivide(bar.getArriveWeight(), bar.getMarketingWeight()));
            // 白条出品率 = inWeight / arriveWeight
            vo.setWhiteBarYieldRate(safeDivide(bar.getInWeight(), bar.getArriveWeight()));
            // 预冷损耗量 = inWeight - outWeight
            if (bar.getInWeight() != null && bar.getOutWeight() != null) {
                BigDecimal precoolLoss = bar.getInWeight().subtract(bar.getOutWeight());
                vo.setPrecoolLossWeight(precoolLoss);
                // 预冷损耗率 = (inWeight - outWeight) / inWeight
                vo.setPrecoolLossRate(safeDivide(precoolLoss, bar.getInWeight()));
            }
            // 冷库停留时长（分钟）= outTime - inTime
            if (bar.getInTime() != null && bar.getOutTime() != null) {
                vo.setColdStorageMinutes(Duration.between(
                    bar.getInTime().toInstant(), bar.getOutTime().toInstant()).toMinutes());
            }
            // 分割品总重 = Σ cut_out_in 流水 change_quantity（按 ear_no，doc/14 §1）
            BigDecimal cutTotal = cutWeightMap.get(vo.getEarNo());
            vo.setCutProductTotalWeight(cutTotal);
            // 分割损耗 = outWeight - 分割品总重
            if (bar.getOutWeight() != null && cutTotal != null) {
                vo.setCutLossWeight(bar.getOutWeight().subtract(cutTotal));
            }
        }
    }

    /**
     * 比率安全除法：分母为 null 或 0 返 null（前端显「—」）；保留 {@link #STAT_SCALE} 位小数 HALF_UP。
     */
    private BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null
            || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, STAT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * SQL 聚合标量（{@code SUM(...)}）→ BigDecimal 容错转换（MySQL 驱动可能返 BigDecimal / Double / Long）。
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    /**
     * P4 分割入库库位解析兜底（参 {@code ProductProductionServiceImpl#resolveLocationId} 范式）。
     *
     * <p>优先级：</p>
     * <ol>
     *   <li>前端显式传 {@code boLocationId} 非空 → 直接用（存在性校验由调用方 {@code submitCutOut} 统一做）；</li>
     *   <li>否则取所选分割产品配置的入库库位（{@code product_info.store_location_id} 逗号分隔，
     *       取首个能解析为数字且 location 存在的有效项）；</li>
     *   <li>仍无 → 抛 ServiceException 提示前端补库位（分割落库必须指定冻品/鲜品库位，不静默兜底任意库位）。</li>
     * </ol>
     *
     * <p>容错：CSV 中非数字 token / 不存在的库位 id 跳过；遍历各 part 的 productId 直到解析出有效库位。</p>
     *
     * @param boLocationId 前端传入库位 id（P4 自动填后通常非空；mp 旧端或未填时为空）
     * @param partItems    分割明细（取其 productId 关联 product_info.store_location_id 配置）
     * @return 解析出的有效库位 id（非空）
     */
    private Long resolveCutLocationId(Long boLocationId, List<PigCutOutBo.PartItem> partItems) {
        // ① 前端显式传了库位 → 直接用（存在 + 库类型校验由 submitCutOut 统一做）
        if (boLocationId != null) {
            return boLocationId;
        }
        // ② 按所选分割产品配置的 store_location_id 解析首个有效库位
        if (partItems != null) {
            for (PigCutOutBo.PartItem part : partItems) {
                if (part.getProductId() == null) {
                    continue;
                }
                ProductInfo product = productInfoMapper.selectById(part.getProductId());
                if (product == null || StringUtils.isBlank(product.getStoreLocationId())) {
                    continue;
                }
                for (String token : product.getStoreLocationId().split(",")) {
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
        }
        // ③ 全部落空 → 要求前端补库位（不静默兜底任意库位，避免分割产出入错冷库）
        throw new ServiceException("未指定入冻品库位，且所选分割产品未配置入库库位，请在录入页选择入库位置");
    }

    private LambdaQueryWrapper<PigCutRecord> buildQueryWrapper(PigCutRecordQuery query) {
        LambdaQueryWrapper<PigCutRecord> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(PigCutRecord::getId);
        }
        wrapper.eq(StringUtils.isNotBlank(query.getCutId()), PigCutRecord::getCutId, query.getCutId())
            .eq(StringUtils.isNotBlank(query.getBarId()), PigCutRecord::getBarId, query.getBarId())
            .eq(StringUtils.isNotBlank(query.getEarNo()), PigCutRecord::getEarNo, query.getEarNo())
            .eq(StringUtils.isNotBlank(query.getCutStatus()), PigCutRecord::getCutStatus, query.getCutStatus())
            .eq(query.getOperatorId() != null, PigCutRecord::getOperatorId, query.getOperatorId())
            .ge(query.getPickupTimeFrom() != null, PigCutRecord::getPickupTime, query.getPickupTimeFrom())
            .le(query.getPickupTimeTo() != null, PigCutRecord::getPickupTime, query.getPickupTimeTo())
            .orderByDesc(PigCutRecord::getId);
        return wrapper;
    }

}
