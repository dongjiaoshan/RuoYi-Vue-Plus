package org.dromara.djs.warehouse.cut.service.impl;

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
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.cut.domain.PigCutRecord;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutDoneBo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutOutBo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutPickupBo;
import org.dromara.djs.warehouse.cut.domain.query.PigCutRecordQuery;
import org.dromara.djs.warehouse.cut.domain.vo.BarInfoVo;
import org.dromara.djs.warehouse.cut.domain.vo.PigCutRecordVo;
import org.dromara.djs.warehouse.cut.mapper.PigCutRecordMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.trace.domain.TraceContentConst;
import org.dromara.djs.warehouse.trace.service.ITraceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * stock_flow.flow_type 分割品入冻品库流水。
     */
    private static final String FLOW_TYPE_CUT_OUT_IN = "cut_out_in";

    /**
     * stock_flow.flow_type 白条分割出库流水。
     */
    private static final String FLOW_TYPE_CUT_OUT = "cut_out";

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
    private final ProductInhouseMapper productInhouseMapper;
    private final StockFlowMapper stockFlowMapper;
    private final ProductInfoMapper productInfoMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final ITraceService traceService;

    public PigCutRecordServiceImpl(PigCutRecordMapper baseMapper,
                                   BarInfoMapper barInfoMapper,
                                   ProductInhouseMapper productInhouseMapper,
                                   StockFlowMapper stockFlowMapper,
                                   ProductInfoMapper productInfoMapper,
                                   LocationInfoMapper locationInfoMapper,
                                   IBizCodeGenerator bizCodeGenerator,
                                   ITraceService traceService) {
        super(baseMapper);
        this.barInfoMapper = barInfoMapper;
        this.productInhouseMapper = productInhouseMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.productInfoMapper = productInfoMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.traceService = traceService;
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

        // 领用称重：现场录入优先，未录入回落 in_weight 快照（兼容 mp 旧端 / 不录重场景）
        BigDecimal pickupWeight = bo.getPickupWeight() != null ? bo.getPickupWeight() : bar.getInWeight();
        // 校验：领用称重不应大于该白条出栏重量（marketing_weight）
        if (pickupWeight != null && bar.getMarketingWeight() != null
            && pickupWeight.compareTo(bar.getMarketingWeight()) > 0) {
            throw new ServiceException("领用称重（" + pickupWeight + "kg）不应大于该白条出栏重量（"
                + bar.getMarketingWeight() + "kg）");
        }

        // Step 2：UPDATE bar_info status → pending_cut（乐观锁）
        int affected = barInfoMapper.updateStatusToPendingCut(bar.getId(), userId);
        if (affected == 0) {
            throw new ServiceException("白条已被并发领用，请刷新重试");
        }

        // Step 3：INSERT cut_record
        PigCutRecord record = new PigCutRecord();
        record.setCutId(generateCutId());
        record.setWhiteBarId(bar.getId());
        record.setBarId(bar.getBarId());
        record.setEarNo(bar.getEarNo());
        record.setPickupTime(new Date());
        record.setPickupWeight(pickupWeight);
        record.setOperatorId(userId);
        record.setLocationId(bo.getLocationId());
        record.setTargetStoreId(bo.getTargetStoreId());
        record.setTargetDemandId(bo.getTargetDemandId());
        record.setIsHalf(bo.getIsHalf() == null ? 2 : bo.getIsHalf());
        record.setCutStatus(CUT_STATUS_PICKED);
        record.setRemark(bo.getRemark());
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

        // 校验 locationId 存在 + 是冻品库
        LocationInfo location = locationInfoMapper.selectById(bo.getLocationId());
        if (location == null) {
            throw new ServiceException("入冻品库位不存在：" + bo.getLocationId());
        }

        // Step 3：for each part → INSERT product_inhouse + INSERT stock_flow IN
        BigDecimal totalWeight = BigDecimal.ZERO;
        LocalDate today = LocalDate.now();
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

            ProductInhouse inhouse = new ProductInhouse();
            inhouse.setProduceDate(java.sql.Date.valueOf(today));
            inhouse.setProductId(productId);
            inhouse.setProductName(productName);
            inhouse.setProductType(1); // 自产
            inhouse.setProductUnit(productUnit);
            inhouse.setProductSpec(part.getProductSpec());
            inhouse.setEarNo(record.getEarNo());
            inhouse.setProductWeight(part.getProductWeight());
            inhouse.setProduceTime(now);
            inhouse.setWhiteBarId(record.getWhiteBarId());
            inhouse.setCutPart(part.getCutPart());
            inhouse.setLocationId(bo.getLocationId());
            productInhouseMapper.insert(inhouse);

            // 分割品入冻品库流水
            StockFlow flowIn = new StockFlow();
            Map<String, Object> flowCtxIn = new HashMap<>(2);
            flowCtxIn.put("ioCode", INOUT_IN);
            flowIn.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, flowCtxIn));
            flowIn.setFlowDate(now);
            flowIn.setProductId(productId);
            flowIn.setWarehouseId(bo.getLocationId());
            flowIn.setInoutType(INOUT_IN);
            flowIn.setFlowType(FLOW_TYPE_CUT_OUT_IN);
            flowIn.setChangeNum(part.getProductWeight());
            flowIn.setChangeQuantity(part.getProductWeight());
            flowIn.setEarNo(record.getEarNo());
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

        // Step 2：UPDATE cut_record cutting → done
        int affected = baseMapper.updateStatusToDone(
            record.getId(), now, bo.getDripLoss(), acidMinutes,
            bo.getRemark(), bo.getProofOssIds(), userId);
        if (affected == 0) {
            throw new ServiceException("分割单状态已变更，请刷新重试");
        }

        // Step 3：UPDATE bar_info cutting → cut_done
        BigDecimal outWeight = record.getPickupWeight().subtract(bo.getDripLoss());
        int barAffected = barInfoMapper.updateStatusToCutDone(
            record.getWhiteBarId(), now, outWeight, acidMinutes, bo.getDripLoss(), userId);
        if (barAffected == 0) {
            throw new ServiceException("白条状态不符或已被并发更新，请刷新重试");
        }

        // TRC-CORE-001：屠宰分割 + 排酸追溯事件（分割完成同时记两条；按耳号反查 trace_code，
        // 猪肉链当前无生成入口 → warn 跳过，不拖垮分割事务）
        // 追溯时间轴每节点重量：分割 / 排酸节点重量 = 白条重量 outWeight（pickupWeight − dripLoss）
        traceService.recordEventByEarNo(record.getEarNo(), TraceContentConst.SLAUGHTER, outWeight);
        traceService.recordEventByEarNo(record.getEarNo(), TraceContentConst.ACID, outWeight);
    }

    @Override
    public TableDataInfo<PigCutRecordVo> queryPageList(PigCutRecordQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PigCutRecord> wrapper = buildQueryWrapper(query);
        Page<PigCutRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillLocationNames(page.getRecords());
        fillRemainingWeight(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<PigCutRecordVo> queryList(PigCutRecordQuery query) {
        List<PigCutRecordVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillLocationNames(list);
        fillRemainingWeight(list);
        return list;
    }

    @Override
    public PigCutRecordVo queryById(Long id) {
        PigCutRecordVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillLocationNames(List.of(vo));
            fillRemainingWeight(List.of(vo));
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
        return bars.stream().map(b -> {
            BarInfoVo vo = new BarInfoVo();
            vo.setId(b.getId());
            vo.setBarId(b.getBarId());
            vo.setEarNo(b.getEarNo());
            vo.setMarketingWeight(b.getMarketingWeight());
            vo.setInWeight(b.getInWeight());
            vo.setInTime(b.getInTime());
            vo.setStatus(b.getStatus());
            return vo;
        }).toList();
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
            if (vo.getPickupWeight() == null || vo.getWhiteBarId() == null) {
                continue;
            }
            BigDecimal used = productInhouseMapper.sumProductWeightByWhiteBarId(vo.getWhiteBarId());
            BigDecimal remaining = vo.getPickupWeight().subtract(used == null ? BigDecimal.ZERO : used);
            vo.setRemainingWeight(remaining.max(BigDecimal.ZERO));
        }
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
