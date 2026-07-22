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
    /** 损耗出库 flow_type（邓博 row17：白条领用残量清零流水，与物资损耗共用字典码）。 */
    private static final String FLOW_TYPE_LOSS = "loss";
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
     * cut_record.out_type：白条领用表 3 个出库位置。仅 {@code cut} 后续进分割 + 计入分割统计；
     * {@code ship/warehouse} 领用即终态、不计入分割白条数/总重/率与 trace 领用时点。
     */
    private static final String OUT_TYPE_CUT = "cut";

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

        // Step 1：SELECT bar_info。邓博 row14 按半只领用：一头猪可分多次领（领一个半只后 bar 已转
        // pending_cut/cutting），故允许从 in_stock / pending_cut / cutting 继续领剩余产出行；
        // 仅拒已收尾（cut_done/ship_out）或未入库（pending_singe/singing）。
        BarInfo bar = barInfoMapper.selectById(bo.getBarInfoId());
        if (bar == null) {
            throw new ServiceException("白条不存在：" + bo.getBarInfoId());
        }
        if (!BAR_STATUS_IN_STOCK.equals(bar.getStatus())
            && !BAR_STATUS_PENDING_CUT.equals(bar.getStatus())
            && !BAR_STATUS_CUTTING.equals(bar.getStatus())) {
            throw new ServiceException("白条状态不符（当前：" + bar.getStatus() + "，需在库/待分割/分割中）");
        }

        // TRC 白条出库（领用）事件（邓博 row19 拆出独立事件）：白条被领用出库进入分割的时刻按耳号写追溯流水。
        // 重量取领用称重（未录回落白条入库重 in_weight）。recordEventByEarNo 全程容错（无生码白条 warn 跳过）。
        traceService.recordEventByEarNo(bar.getEarNo(), TraceContentConst.WHITE_BAR_PICK,
            bo.getPickupWeight() != null ? bo.getPickupWeight() : bar.getInWeight());

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
        // P3.c'（邓博 row13）：整只 / mp 白条领用 = 白条出白条库。逐白条产出行扣半只库存(by white_bar_no) +
        // 写白条出库流水（去向=白条分割）。修「白条库出库记录缺失」；结算仍按 white_bar_id(整猪) 不变。
        // 只取未领行（pickup_status=0/NULL）：整只路径消费产出行同样置位，已领行不再二次扣篮子/写流水。
        List<ProductInhouse> barRows = productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .eq(ProductInhouse::getWhiteBarId, bar.getId())
                .isNotNull(ProductInhouse::getWhiteBarNo)
                .gt(ProductInhouse::getProductWeight, BigDecimal.ZERO)
                .and(w -> w.eq(ProductInhouse::getPickupStatus, 0).or().isNull(ProductInhouse::getPickupStatus)));
        for (ProductInhouse r : barRows) {
            // 整只路径消费产出行 = 该行已领：置 pickup_status=1 + pickup_weight（与 pickupByInhouseRow 同置位口径；
            // 整只领用无逐行过磅，领用重取该行燎毛入库重 product_weight）。乐观锁 WHERE pickup_status=0/NULL：
            // 并发已领行置位不中 → 跳过该行，不重复扣篮子、不重复写出库流水。置位后 admin 半只卡不再对已消费行
            // 重复出卡、countCutBar/sumCutBarWeight 不双算、submitCutDone pendingRows 可归零整猪收口。
            int marked = productInhouseMapper.update(null,
                new LambdaUpdateWrapper<ProductInhouse>()
                    .eq(ProductInhouse::getId, r.getId())
                    .and(w -> w.eq(ProductInhouse::getPickupStatus, 0).or().isNull(ProductInhouse::getPickupStatus))
                    .set(ProductInhouse::getPickupStatus, 1)
                    .set(ProductInhouse::getPickupWeight, r.getProductWeight()));
            if (marked == 0) {
                continue;
            }
            writeBarCutOutFlow(r, r.getProductWeight(), userId, true);
        }
        // mp 整只兜底路径：无逐产出行拆分，建整猪 cut_record（whiteBarNo=null → 剩余/超量回落 white_bar_id）。
        // 预冷按整只：inWeight = bar.in_weight（整猪入库重）；产品维度走通用白条产品 id（无逐行产品）。
        return insertCutRecord(bar, pickupWeight, bar.getInWeight(), bo.getLocationId(), bo.getTargetStoreId(),
            bo.getTargetDemandId(), bo.getIsHalf() == null ? 2 : bo.getIsHalf(), bo.getRemark(), userId, null,
            resolveWhiteBarProductId());
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
        // 半只维度贯穿（邓博 row13/row14）：外购 / 旧数据行 white_bar_no 空 → 领用时补生成一个 BAR_NO 落到该产出行，
        // 使这半只在 库存/流水/分割/剩余/超量 全链有稳定半只键（cut_out_in 按 white_bar_no 聚合，互不串扣）。
        String whiteBarNo = row.getWhiteBarNo();
        boolean barNoGenerated = StringUtils.isBlank(whiteBarNo);
        if (barNoGenerated) {
            whiteBarNo = bizCodeGenerator.generate(BizCodeType.BAR_NO, Map.of());
            productInhouseMapper.update(null, new LambdaUpdateWrapper<ProductInhouse>()
                .eq(ProductInhouse::getId, row.getId())
                .set(ProductInhouse::getWhiteBarNo, whiteBarNo));
            row.setWhiteBarNo(whiteBarNo);
        }
        // 白条领用到分割间 = 白条出白条库：扣该半只库存行(by white_bar_no) + 写白条出库流水（去向=白条分割）。
        // 本次补号的行（外购/旧数据）白条库本就无该篮子 → stockRequired=false 走降级；原生带号的现代燎毛行必须命中篮子。
        writeBarCutOutFlow(row, rowWeight, userId, !barNoGenerated);
        // 邓博 row14 修复：按半只 surface —— 每领一个产出行即建独立 cut_record（picked）+ 推 bar pending_cut，
        // 立即在白条分割车间可见可分割，不再等整头猪所有产出行领完才建 cut_record（原 finalize 逻辑=「领半只后分割车间看不到」根因）。
        // bar → pending_cut 幂等（已 pending_cut/cutting → affected=0 不抛）；剩余未领行仍可继续领（picker 含 pending_cut/cutting）。
        barInfoMapper.updateStatusToPendingCut(bar.getId(), userId);
        int isHalf = bo.getIsHalf() != null ? bo.getIsHalf()
            : (row.getProductName() != null && row.getProductName().contains("半") ? 1 : 2);
        // 结算/超量/剩余/损耗仍按整猪 white_bar_id 汇总（dashboards 读 by white_bar_id 不变，Kevin 定）。
        // 预冷按白条：inWeight = 该燎毛产出行半只入库重（row.product_weight）减本行领用重 rowWeight；产品维度=该产出行产品。
        return insertCutRecord(bar, rowWeight, row.getProductWeight(), bo.getLocationId(), bo.getTargetStoreId(),
            bo.getTargetDemandId(), isHalf, bo.getRemark(), userId, whiteBarNo, row.getProductId());
    }

    /**
     * 白条领用到分割间 = 白条出白条库：扣该半只白条库存行（P2 燎毛按 white_bar_no 建）+ 写「白条出库」流水（去向=白条分割）。
     *
     * <p>邓博 row13：白条去分割车间时从白条库正常出库（修出库记录缺失致库存不准）。库存扣减口径：
     * {@code stockRequired=true}（现代燎毛行，white_bar_no 燎毛入库时建、白条库必有对应篮子）时，
     * 篮子查不到（已被领光 / 盘点清零 / 重复领用）或扣减 affected=0（余量不足 / 并发抢占）→ 抛异常回滚，
     * 防出库流水与货架库存单边分叉；{@code stockRequired=false}（white_bar_no 空，或外购 / 旧数据行
     * 领用时现补生成 BAR_NO、白条库本就无该篮子）→ 跳过库存行扣减、流水仍照写（优雅降级，不阻断领用）。
     * 篮子查询按 id 升序取最早一行，消除 LIMIT 1 非确定性。流水库位取 inhouse.location_id，
     * 空则回落白条库存行 location_id（doc/11 warehouse_id 必填）。分割结算/超量/剩余/损耗仍按
     * white_bar_id(整猪)，cut_out 流水不参与结算（结算用 cut_out_in），故此处补写不影响分割口径。
     * 邓博 row17：每个半只篮只能领用一次（pickup_status 乐观锁），领用扣重后篮内残量（入库重 −
     * 领用重）无后续业务消费 → 同事务清零并写 loss 流水（{@link #drainBarResidualToLossFlow}）。</p>
     */
    private void writeBarCutOutFlow(ProductInhouse row, BigDecimal weight, Long userId, boolean stockRequired) {
        Long warehouseId = row.getLocationId();
        Long drainedBasketId = null;
        if (StringUtils.isNotBlank(row.getWhiteBarNo())) {
            LocationStock barStock = locationStockMapper.selectOne(
                new LambdaQueryWrapper<LocationStock>()
                    .eq(LocationStock::getProductId, row.getProductId())
                    .eq(LocationStock::getWhiteBarNo, row.getWhiteBarNo())
                    .gt(LocationStock::getProductStock, BigDecimal.ZERO)
                    .orderByAsc(LocationStock::getId)
                    .last("LIMIT 1"));
            if (barStock == null && stockRequired) {
                throw new ServiceException("该半只白条库存不存在或已出库：white_bar_no=" + row.getWhiteBarNo());
            }
            if (barStock != null) {
                int affected = locationStockMapper.deductStockById(barStock.getId(), weight, userId);
                if (affected == 0) {
                    throw new ServiceException("白条库存扣减失败（余量不足或已被并发领用）：white_bar_no="
                        + row.getWhiteBarNo() + " 本次领用 " + weight.stripTrailingZeros().toPlainString() + "kg");
                }
                if (warehouseId == null) {
                    warehouseId = barStock.getLocationId();
                }
                drainedBasketId = barStock.getId();
            }
        }
        if (warehouseId == null) {
            log.warn("白条出库流水缺库位（inhouse 与白条库存行均无 location_id）— inhouseId={} whiteBarNo={} earNo={}",
                row.getId(), row.getWhiteBarNo(), row.getEarNo());
        }
        StockFlow out = new StockFlow();
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", INOUT_OUT);
        out.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx));
        out.setFlowDate(new Date());
        out.setProductId(row.getProductId());
        out.setWarehouseId(warehouseId);
        out.setInoutType(INOUT_OUT);
        out.setFlowType(FLOW_TYPE_CUT_OUT);
        out.setStockOutDest(STOCK_OUT_DEST_BAR_CUT);
        out.setChangeNum(weight.negate());
        out.setChangeQuantity(weight);
        out.setEarNo(row.getEarNo());
        out.setWhiteBarNo(row.getWhiteBarNo());
        out.setWhiteBarId(row.getWhiteBarId());
        out.setOperatorId(userId);
        stockFlowMapper.insert(out);
        if (drainedBasketId != null) {
            drainBarResidualToLossFlow(drainedBasketId, row.getProductId(), warehouseId,
                row.getEarNo(), row.getWhiteBarNo(), row.getWhiteBarId(), userId);
        }
    }

    /**
     * 白条篮残量转损耗出库（邓博 row17）：领用扣重后复读该篮余量（= 入库重 − 领用重），残量 > 0 →
     * 同事务二次扣减清零 + 写 {@code flow_type=loss} 出库流水留痕（防死残量永久躺在白条库存）。
     *
     * <p>仅白条篮（white_bar_no 命中的行）走本清零；耳号分割产出篮不适用。预冷损耗账
     * {@code t_warehouse_loss_flow} 由领用链路单独记（writePickupPrecoolLoss），
     * 此处绝不写 loss_flow —— 再写 = 损耗总览双算。</p>
     */
    private void drainBarResidualToLossFlow(Long barStockId, Long productId, Long warehouseId,
                                            String earNo, String whiteBarNo, Long whiteBarId, Long userId) {
        LocationStock refreshed = locationStockMapper.selectById(barStockId);
        if (refreshed == null || refreshed.getProductStock() == null
            || refreshed.getProductStock().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal residual = refreshed.getProductStock();
        int drained = locationStockMapper.deductStockById(barStockId, residual, userId);
        if (drained == 0) {
            // 本事务已持该篮行锁，理论不可达；防御性跳过（残量留待盘点），不阻断领用主链
            log.warn("白条篮残量清零失败 — stockId={} whiteBarNo={} residual={}", barStockId, whiteBarNo, residual);
            return;
        }
        StockFlow loss = new StockFlow();
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", INOUT_OUT);
        loss.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx));
        loss.setFlowDate(new Date());
        loss.setProductId(productId);
        loss.setWarehouseId(warehouseId);
        loss.setInoutType(INOUT_OUT);
        loss.setFlowType(FLOW_TYPE_LOSS);
        loss.setChangeNum(residual.negate());
        loss.setChangeQuantity(residual);
        loss.setEarNo(earNo);
        loss.setWhiteBarNo(whiteBarNo);
        loss.setWhiteBarId(whiteBarId);
        loss.setOperatorId(userId);
        loss.setRemark("白条领用残量转损耗出库（入库重-领用重）");
        stockFlowMapper.insert(loss);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long finalizeBarPickupIfComplete(Long barInfoId, Long userId) {
        // 邓博 row14 按半只 surface 后：cut_record 已在 pickupByInhouseRow 按产出行建好、bar 已 pending_cut。
        // 本方法保留给发货路径（ProductProductionServiceImpl.submitWhiteBarOut）幂等收口用：不再建整猪
        // cut_record（避免与领用时的按半只 cut_record 重复），仅在有分割领用行时确保 bar 处于 pending_cut，
        // 返非空信号（调用方仅日志用，据自身 cutRows 分支决定不转 ship_out）。
        BarInfo bar = barInfoMapper.selectById(barInfoId);
        if (bar == null) {
            return null;
        }
        Long cutRows = productInhouseMapper.selectCount(
            new LambdaQueryWrapper<ProductInhouse>()
                .eq(ProductInhouse::getWhiteBarId, barInfoId)
                .eq(ProductInhouse::getPickupStatus, 1));
        if (cutRows == null || cutRows == 0) {
            return null;  // 无分割领用行 → 全发货 → 调用方转 ship_out
        }
        if (BAR_STATUS_IN_STOCK.equals(bar.getStatus())) {
            barInfoMapper.updateStatusToPendingCut(barInfoId, userId);
        }
        return barInfoId;  // 非空信号（分割路径接管此 bar，调用方不转 ship_out）
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

    /**
     * 建 cut_record（cut_status=picked）。
     * whiteBarNo 非空 = 按半只（admin 逐产出行领用，邓博 row13/row14）；空 = 整猪（mp 整只兜底路径）。
     */
    private Long insertCutRecord(BarInfo bar, BigDecimal pickupWeight, BigDecimal inWeight, Long locationId,
                                 Long targetStoreId, Long targetDemandId, int isHalf,
                                 String remark, Long userId, String whiteBarNo, Long productId) {
        PigCutRecord record = new PigCutRecord();
        record.setCutId(generateCutId());
        record.setWhiteBarId(bar.getId());
        record.setWhiteBarNo(whiteBarNo);
        record.setBarId(bar.getBarId());
        record.setEarNo(bar.getEarNo());
        Date pickupTime = new Date();
        record.setPickupTime(pickupTime);
        record.setPickupWeight(pickupWeight);
        // 白条领用表：分割车间领用 out_type=cut；排酸时长 = 领用出库时间 − 白条入库时间（row204，领用时一次写定，
        // 分割完成不再覆盖）。bar.in_time 缺失 → acid 留 null 不瞎编。
        record.setOutType(OUT_TYPE_CUT);
        record.setAcidRemoveMinutes(computeAcidMinutes(bar.getInTime(), pickupTime));
        // r148：领用即按「白条产品」记录本行预冷损耗 = 该白条入库重 − 本次领用重（钳 0）。半只(whiteBarNo 非空)走
        //   燎毛产出行半只入库重、整只走 bar.in_weight。预冷按白条不按猪——一头猪分割成 N 张白条 = N 笔预冷。
        //   入库重/领用重任一缺失 → 不写（保持 NULL，不瞎编）。
        if (inWeight != null && pickupWeight != null) {
            record.setDripLoss(inWeight.subtract(pickupWeight).max(BigDecimal.ZERO));
        }
        record.setOperatorId(userId);
        record.setLocationId(locationId);
        record.setTargetStoreId(targetStoreId);
        record.setTargetDemandId(targetDemandId);
        record.setIsHalf(isHalf);
        record.setCutStatus(CUT_STATUS_PICKED);
        record.setRemark(remark);
        baseMapper.insert(record);

        // r148：预冷损耗按白条即时结算 loss_flow（loss_date=领用当天），与分割损耗 writeHalfCutLossFlow 对称。
        // 整猪收口不再写聚合，避免「混合出库双算 / 跨天错配 / 未收口漏计」。为 0 / 缺入库重时 record() 自动跳过。
        writePickupPrecoolLoss(record, productId, userId);
        return record.getId();
    }

    /**
     * r148：白条领用时按白条产品即时写「预冷损耗」loss_flow。
     *
     * <p>预冷物理上发生在「入白条库 → 领用出库」之间，故领用即结算（不再推迟到整猪分割收口一次性聚合）。
     * 粒度 = 白条产品（{@code productId} = 该燎毛产出行产品；mp 整只兜底走通用白条产品 id），
     * {@code loss_date} = 该白条领用时刻，与 {@link #writeHalfCutLossFlow} 分割损耗完全对称。
     * {@code lossWeight<=0}（无预冷 / 缺入库重）由 {@link ILossFlowService#record} 自动跳过。</p>
     */
    private void writePickupPrecoolLoss(PigCutRecord record, Long productId, Long userId) {
        LossFlow precool = new LossFlow();
        precool.setLossType(LOSS_TYPE_PRECOOL);
        precool.setLossWeight(record.getDripLoss());
        precool.setLossDate(record.getPickupTime());
        precool.setProductId(productId);
        precool.setEarNo(record.getEarNo());
        precool.setOperatorId(userId);
        precool.setSourceBizType(LOSS_SOURCE_BIZ_CUT);
        precool.setSourceBizId(record.getId());
        lossFlowService.record(precool);
    }

    /**
     * 排酸时长（分钟）= 领用出库时间 − 白条入库时间（in_time）。任一缺失返 {@code null}（不瞎编）；负值钳 0。
     */
    private static Integer computeAcidMinutes(Date inTime, Date pickupTime) {
        if (inTime == null || pickupTime == null) {
            return null;
        }
        long mins = Duration.between(inTime.toInstant(), pickupTime.toInstant()).toMinutes();
        return (int) Math.max(0L, mins);
    }

    /**
     * 白条领用表：发货月台 / 仓库出库分支领用即终态记录（row205，邓博 2026-07-05）。
     *
     * <p>白条领用页 3 个出库位置都写一条 cut_record 统一台账。ship/warehouse 领用即终态
     * （{@code cut_status='done'}，无后续分割），记 {@code out_type} + 预冷损耗（drip_loss 列）+ 排酸时长
     * （= 领用出库 − 入库）+（发货月台）目标门店/需求。分割统计只算 {@code out_type='cut'}，本表 ship/warehouse 行不计入。</p>
     *
     * <p>⚠️ 不写 loss_flow：预冷损耗 loss_flow 已由 {@code ProductProductionServiceImpl.writePrecoolLossOnBarOut}
     * 记，此处只写本表 drip_loss 列供展示，避免双算。分割损耗同理不写（出库无分割产出）。</p>
     *
     * @param outType        出库类型（ship / warehouse）
     * @param bar            白条实体（可空；取 in_time 算排酸、bar_id）
     * @param src            来源燎毛产出行（取 white_bar_no / ear_no / location_id / 半只入库重算预冷损耗）
     * @param outWeight      本次出库重（= pickup_weight）
     * @param targetStoreId  目标门店（发货月台有，仓库出库为 null）
     * @param targetDemandId 目标需求（发货月台可选，仓库出库为 null）
     * @param userId         操作人
     * @param outDest        出库去向码值（admin row2，字典 djs_stock_out_dest；仅 warehouse 仓库出库有值，ship 传 null）
     * @return 新 cut_record id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertOutRecord(String outType, BarInfo bar, ProductInhouse src, BigDecimal outWeight,
                                Long targetStoreId, Long targetDemandId, Long userId, String outDest) {
        PigCutRecord record = new PigCutRecord();
        record.setCutId(generateCutId());
        record.setOutType(outType);
        // admin row2：仓库出库记录出库去向码值（cut/ship 传 null）
        record.setOutDest(outDest);
        Date pickupTime = new Date();
        record.setPickupTime(pickupTime);
        record.setPickupWeight(outWeight);
        if (bar != null) {
            record.setWhiteBarId(bar.getId());
            record.setBarId(bar.getBarId());
            record.setAcidRemoveMinutes(computeAcidMinutes(bar.getInTime(), pickupTime));
        }
        if (src != null) {
            record.setWhiteBarNo(src.getWhiteBarNo());
            record.setEarNo(src.getEarNo());
            record.setLocationId(src.getLocationId());
            // 预冷损耗 = 该半只燎毛入库重(product_weight) − 本次出库重（钳 0），与 writePrecoolLossOnBarOut 同口径
            BigDecimal inWeight = src.getProductWeight();
            if (inWeight != null && outWeight != null) {
                record.setDripLoss(inWeight.subtract(outWeight).max(BigDecimal.ZERO));
            }
            record.setIsHalf(src.getProductName() != null && src.getProductName().contains("半") ? 1 : 2);
        }
        record.setTargetStoreId(targetStoreId);
        record.setTargetDemandId(targetDemandId);
        record.setOperatorId(userId);
        record.setCutStatus(CUT_STATUS_DONE);
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
            // 按半只（white_bar_no）聚合已分割量与 record.pickupWeight（半只领用重）对齐；空回落整猪 white_bar_id（旧记录）
            BigDecimal alreadyCut = StringUtils.isNotBlank(record.getWhiteBarNo())
                ? stockFlowMapper.sumCutOutByWhiteBarNo(record.getWhiteBarNo())
                : stockFlowMapper.sumCutOutByWhiteBarId(record.getWhiteBarId());
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
            // row157（撤销 SPLIT-001 拆分）：同一耳号整猪两半只产出同一部位合并到一行篮子——先按
            // (location, product, ear_no, white_bar_no IS NULL, is_end=0) UPSERT 累加，命中 0 行才 insert 新篮，
            // 避免库存查询同耳号同产品出现两行。外购无耳号（earNo 空）→ 不合并（避免跨猪串账），保持一行一次。
            boolean merged = false;
            if (StringUtils.isNotBlank(record.getEarNo())) {
                merged = locationStockMapper.addByProductLocationEarNo(
                    effectiveLocationId, productId, record.getEarNo(),
                    part.getProductWeight(), userId) > 0;
            }
            if (!merged) {
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
            }

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
            // 分割产出按 white_bar_id 关联白条（外购无耳号也稳定可聚合）+ 按 white_bar_no 关联半只
            // （邓博 row14：剩余可分割/超量校验按半只聚合，一头猪多半只互不串扣；旧记录 white_bar_no 空回落整猪）
            flowIn.setWhiteBarId(record.getWhiteBarId());
            flowIn.setWhiteBarNo(record.getWhiteBarNo());
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
        flowOut.setChangeNum(totalWeight.negate());
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

        // 邓博 row14 按半只 surface：把本 cut_record（本半只）置 done；分割损耗 + bar 转 cut_done 在整猪收口时结算。
        // r148：本 cut_record.drip_loss 保留领用时已按白条写的预冷值（不覆盖）；预冷 loss_flow 已在领用时按白条写、
        // 收口不再写。整猪滴水损耗仍回写 bar_info（整只级 bar 字段，供列表 compute-on-read），不进 loss_flow。
        // row204：acid_remove_minutes（排酸时长）已在领用时写定（= 领用出库 − 入库），本处不再覆盖。
        int affected = baseMapper.updateStatusToDone(
            record.getId(), now, record.getDripLoss(),
            bo.getRemark(), bo.getProofOssIds(), userId);
        if (affected == 0) {
            throw new ServiceException("分割单状态已变更，请刷新重试");
        }

        // row150（Kevin 定）：分割损耗按半只即时结算——本 cut_record（本半只）done 时立即按该半只写一条
        // cut_loss loss_flow（loss_date=本半只完成时刻 now）。跨天完成时前半只当日损耗即当日记账，
        // 不再等整猪所有半只领完/切完才一次性算。cut_loss(半只) = 本半只领用重 pickup_weight −
        // 本半只分割产出 Σcut_out_in（按 white_bar_no 聚合；旧记录 white_bar_no 空回落整猪 white_bar_id）。
        // 整猪收口不再写 cut_loss，避免双算；各半只 loss_flow 之和 = 整猪分割损耗（分区互不重叠）。
        writeHalfCutLossFlow(record, now, userId);

        // 该白条是否全部半只都分割完（无 cut_status!=done 的 cut_record）且无未领产出行 → 才整猪收口
        Long pendingCuts = baseMapper.selectCount(
            new LambdaQueryWrapper<PigCutRecord>()
                .eq(PigCutRecord::getWhiteBarId, record.getWhiteBarId())
                .ne(PigCutRecord::getCutStatus, CUT_STATUS_DONE));
        Long pendingRows = productInhouseMapper.selectCount(
            new LambdaQueryWrapper<ProductInhouse>()
                .eq(ProductInhouse::getWhiteBarId, record.getWhiteBarId())
                .and(w -> w.eq(ProductInhouse::getPickupStatus, 0).or().isNull(ProductInhouse::getPickupStatus)));
        if ((pendingCuts != null && pendingCuts > 0) || (pendingRows != null && pendingRows > 0)) {
            return;  // 还有半只未分割完 / 未领产出行 → 不整猪收口，bar 留 cutting
        }

        // 整猪收口：滴水损耗 = 白条入库重 − 领用总重（Σ pickup_weight = 各半只之和）；结算按整猪 white_bar_id。
        // 入库重缺失（外购/旧数据 in_weight=NULL）或差值为负钳到 0（损耗不可为负）。
        BarInfo bar = barInfoMapper.selectById(record.getWhiteBarId());
        BigDecimal inWeight = bar == null ? null : bar.getInWeight();
        BigDecimal totalPicked = sumPickedRowWeight(record.getWhiteBarId());
        BigDecimal dripLoss = inWeight == null
            ? BigDecimal.ZERO
            : inWeight.subtract(totalPicked).max(BigDecimal.ZERO);
        BigDecimal outWeight = totalPicked;

        // bar_info cutting → cut_done（整猪出库重 / 滴水损耗回写）。并发已收口 → affected=0 幂等跳过，不抛。
        // row204：bar_info 排酸时长与 cut_record 同口径（领用出库 − 入库），取本收口半只领用时已写定的 acid。
        int barAffected = barInfoMapper.updateStatusToCutDone(
            record.getWhiteBarId(), now, outWeight, record.getAcidRemoveMinutes(), dripLoss, userId);
        if (barAffected == 0) {
            return;  // bar 已被并发的另一半只收口，幂等
        }

        // TRC-CORE-001：屠宰分割 + 排酸追溯事件（整猪出库重；按耳号反查 trace_code，无生码 → warn 跳过）
        traceService.recordEventByEarNo(record.getEarNo(), TraceContentConst.SLAUGHTER, outWeight);
        traceService.recordEventByEarNo(record.getEarNo(), TraceContentConst.ACID, outWeight);

        // WMS-LOSS-001：整猪收口只做分割结果 rollup 到白条表（cut_product_weight / cut_loss 按整猪聚合）。
        // 预冷损耗 loss_flow 已在各白条领用时按白条即时写（writePickupPrecoolLoss，r148）、分割损耗已在各半只
        // done 时即时写（writeHalfCutLossFlow，row150），此处都不再写以免双算 / 跨天错配。
        // 由 bar cutting→cut_done 乐观锁保证整猪收口只走一次，不重复。
        writeCutRollup(record, outWeight, userId);
    }

    /**
     * row150（Kevin 定）：本半只 done 时按半只即时结算分割损耗 {@code cut_loss} loss_flow。
     *
     * <ul>
     *   <li>{@code cut_loss(半只)} = 本半只领用重 {@code record.pickupWeight} − 本半只分割产出
     *       Σ {@code cut_out_in}（按 {@code white_bar_no} 聚合，半只维度；旧记录 white_bar_no 空回落整猪
     *       white_bar_id，向后兼容 mp 整只兜底路径）。</li>
     *   <li>{@code loss_date} = 本半只完成时刻（跨天完成时前半只的当日损耗即当日记账，本 fix 目的）。</li>
     * </ul>
     *
     * <p>负值/0 由 {@link ILossFlowService#record} 自动跳过。各半只 loss_flow 之和 = 整猪分割损耗
     * （white_bar_no 分区互不重叠），整猪收口不再另写 cut_loss loss_flow。</p>
     */
    private void writeHalfCutLossFlow(PigCutRecord record, Date lossDate, Long userId) {
        if (record.getPickupWeight() == null) {
            return;
        }
        // 本半只分割产出 = Σ cut_out_in（按半只 white_bar_no；空回落整猪 white_bar_id，与 submitCutOut/
        // fillRemainingWeight 超量口径一致）
        BigDecimal cutTotal = StringUtils.isNotBlank(record.getWhiteBarNo())
            ? stockFlowMapper.sumCutOutByWhiteBarNo(record.getWhiteBarNo())
            : stockFlowMapper.sumCutOutByWhiteBarId(record.getWhiteBarId());
        BigDecimal cutProductWeight = cutTotal == null ? BigDecimal.ZERO : cutTotal;
        BigDecimal cutLoss = record.getPickupWeight().subtract(cutProductWeight);
        LossFlow cut = new LossFlow();
        cut.setLossType(LOSS_TYPE_CUT);
        cut.setLossWeight(cutLoss);
        cut.setLossDate(lossDate);
        cut.setProductId(resolveWhiteBarProductId());
        cut.setEarNo(record.getEarNo());
        cut.setOperatorId(userId);
        cut.setSourceBizType(LOSS_SOURCE_BIZ_CUT);
        cut.setSourceBizId(record.getId());
        lossFlowService.record(cut);
    }

    /**
     * 整猪收口：分割结果 rollup 到白条表（WMS-LOSS-001 行62 / 邓博 row8）。
     *
     * <p>白条表 {@code cut_product_weight} = Σ {@code cut_out_in} by white_bar_id（整猪分割产品重量之和）；
     * {@code cut_loss} = 出库重 − 分割产品重量（整猪聚合口径，落库供列表 compute-on-read 对齐）。</p>
     *
     * <p>预冷损耗 loss_flow 已在各白条领用时按白条即时写（{@link #writePickupPrecoolLoss}，r148）、分割损耗已在
     * 各半只 done 时即时写（{@link #writeHalfCutLossFlow}，row150），此处都不再写，避免双算 / 跨天错配。</p>
     */
    private void writeCutRollup(PigCutRecord record, BigDecimal outWeight, Long userId) {
        BigDecimal cutTotal = stockFlowMapper.sumCutOutByWhiteBarId(record.getWhiteBarId());
        BigDecimal cutProductWeight = cutTotal == null ? BigDecimal.ZERO : cutTotal;
        BigDecimal cutLoss = outWeight.subtract(cutProductWeight);
        barInfoMapper.updateCutResult(record.getWhiteBarId(), cutProductWeight, cutLoss, userId);
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
        // 按入库时间升序 = 先进先出（row93：默认选最早进分割库的白条优先处理）；LIMIT 50 截断时留下的是最老积压。
        List<BarInfo> bars = barInfoMapper.selectList(
            new LambdaQueryWrapper<BarInfo>()
                .eq(BarInfo::getStatus, BAR_STATUS_IN_STOCK)
                .orderByAsc(BarInfo::getInTime)
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
        // 邓博 row14 按半只 surface：一头猪部分半只已领(bar 转 pending_cut/cutting) 后，剩余未领半只仍要能继续领——
        // 故 picker 含 in_stock/pending_cut/cutting 三态（仅展示各 bar 的未领产出行；无未领行的不出卡）。
        // 按入库时间升序 = 先进先出（row93：默认选最早进分割库的白条优先处理，前端默认选中第一张卡即最早）；
        // LIMIT 50 截断时留下的是最老积压，不再把排酸超时的老白条挤出列表。
        List<BarInfo> bars = barInfoMapper.selectList(
            new LambdaQueryWrapper<BarInfo>()
                .in(BarInfo::getStatus, BAR_STATUS_IN_STOCK, BAR_STATUS_PENDING_CUT, BAR_STATUS_CUTTING)
                .orderByAsc(BarInfo::getInTime)
                .last("LIMIT 50"));
        if (bars.isEmpty()) {
            return List.of();
        }
        List<Long> barIds = bars.stream().map(BarInfo::getId).filter(Objects::nonNull).toList();
        // 各白条「未领」燎毛产出行（pickup_status=0/NULL），按 id 升序保留燎毛入库顺序（含副产，用于兜底判空）。
        List<ProductInhouse> rows = barIds.isEmpty() ? List.of() : productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .in(ProductInhouse::getWhiteBarId, barIds)
                .and(w -> w.eq(ProductInhouse::getPickupStatus, 0).or().isNull(ProductInhouse::getPickupStatus))
                .orderByAsc(ProductInhouse::getId));
        // row187：分割白条领用只领「白条库的白条」（整只/半只，belong_type='white_bar'）。燎毛副产猪头/猪蹄
        // （belong_type='pork'）虽同样写 product_inhouse 带 white_bar_id，但不入白条库、不参与白条分割 →
        // 只展示白条产出行卡；但「该 bar 有无未领产出行」仍按全量 rows 判定，避免仅有副产行的 bar 误落整只兜底卡。
        Set<Long> whiteBarProductIds = rows.isEmpty() ? Set.of() : productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>()
                    .select(ProductInfo::getId)
                    .eq(ProductInfo::getBelongType, "white_bar"))
            .stream().map(ProductInfo::getId).filter(Objects::nonNull).collect(Collectors.toSet());
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
                // 燎毛多产出行 → 每白条产出行一张可单独领用的卡（半只 / 半扇 各一张）；跳过副产（猪头/猪蹄）。
                for (ProductInhouse r : rs) {
                    if (whiteBarProductIds.contains(r.getProductId())) {
                        result.add(toPickupItem(bar, r));
                    }
                }
                // 有未领产出行但全是副产 → 不出白条卡、也不落整只兜底（bar 是现代燎毛数据，仅无可领白条行）。
            } else if (BAR_STATUS_IN_STOCK.equals(bar.getStatus())) {
                // 真·无任何未领产出行的旧数据白条 + in_stock → 整只兜底卡（inhouseId=null，领用走整猪路径），向后兼容。
                // pending_cut/cutting 且无未领行 = 已全部领完 → 不再出卡（避免全领 bar 冒出空整只卡）。
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
        if (row != null) {
            vo.setInhouseId(row.getId());
            vo.setWhiteBarNo(row.getWhiteBarNo());
            vo.setProductName(row.getProductName());
            vo.setProductWeight(row.getProductWeight());
            vo.setProductUnit(StringUtils.isNotBlank(row.getProductUnit()) ? row.getProductUnit() : "kg");
            // row144：入库时间按半只产出行各自的入库(称重)时间 produce_time 显示——同一头猪左右两半是燎毛间分别
            // 称重入库的，时间本就有间隔；bar.in_time 是「处理完成」按钮的统一时刻(两半相同)，不能当各半只入库时间。
            // 排酸时长(前端 now − inTime)随之按半只各自算。produce_time 缺失→回落 bar.in_time 不瞎编。
            vo.setInTime(row.getProduceTime() != null ? row.getProduceTime() : bar.getInTime());
        } else {
            vo.setInhouseId(null);
            vo.setProductName("白条（整只）");
            vo.setProductWeight(bar.getMarketingWeight() != null ? bar.getMarketingWeight() : bar.getInWeight());
            vo.setProductUnit("kg");
            // 整只兜底(旧数据无产出行)：用白条处理完成入库时间
            vo.setInTime(bar.getInTime());
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
            // 已分割重量 = Σ cut_out_in 流水；剩余可分割 = 领用白条重 − 已分割。
            // 按半只（white_bar_no）聚合与 pickupWeight（半只领用重）对齐；空回落整猪 white_bar_id（旧记录）。
            BigDecimal used = StringUtils.isNotBlank(vo.getWhiteBarNo())
                ? stockFlowMapper.sumCutOutByWhiteBarNo(vo.getWhiteBarNo())
                : stockFlowMapper.sumCutOutByWhiteBarId(vo.getWhiteBarId());
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
        boolean hasCutStatuses = query.getCutStatuses() != null && !query.getCutStatuses().isEmpty();
        wrapper.eq(StringUtils.isNotBlank(query.getCutId()), PigCutRecord::getCutId, query.getCutId())
            .eq(StringUtils.isNotBlank(query.getBarId()), PigCutRecord::getBarId, query.getBarId())
            .eq(StringUtils.isNotBlank(query.getEarNo()), PigCutRecord::getEarNo, query.getEarNo())
            .in(hasCutStatuses, PigCutRecord::getCutStatus, query.getCutStatuses())
            .eq(!hasCutStatuses && StringUtils.isNotBlank(query.getCutStatus()), PigCutRecord::getCutStatus, query.getCutStatus())
            .eq(query.getOperatorId() != null, PigCutRecord::getOperatorId, query.getOperatorId())
            .ge(query.getPickupTimeFrom() != null, PigCutRecord::getPickupTime, query.getPickupTimeFrom())
            .le(query.getPickupTimeTo() != null, PigCutRecord::getPickupTime, query.getPickupTimeTo())
            .orderByDesc(PigCutRecord::getId);
        return wrapper;
    }

}
