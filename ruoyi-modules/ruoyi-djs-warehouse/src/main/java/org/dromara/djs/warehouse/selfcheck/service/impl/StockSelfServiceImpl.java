package org.dromara.djs.warehouse.selfcheck.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.DateUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.common.supplier.domain.Supplier;
import org.dromara.djs.common.supplier.mapper.SupplierMapper;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.selfcheck.domain.bo.ProductInboundBo;
import org.dromara.djs.warehouse.selfcheck.domain.bo.ProductOutboundBo;
import org.dromara.djs.warehouse.selfcheck.domain.bo.StockCheckEntryBo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.CheckRecordVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.InoutFlowVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.PendingCheckVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.StockStoreEntryVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.StoreProductVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.WhiteBarStockVo;
import org.dromara.djs.warehouse.selfcheck.mapper.StockSelfMapper;
import org.dromara.djs.warehouse.selfcheck.service.IStockSelfService;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 库存盘点自助子系统 Service 实现（SELFCHECK）。
 *
 * <h3>读端点</h3>
 * <p>聚合查询委托 {@link StockSelfMapper}（各库入口 / 库详情 / 待盘点 / 盘点记录 / 进出库流水）；
 * 白条库逐条走 {@link BarInfoMapper} 取可领用在库白条（{@code status='in_stock'}，口径对齐分割白条领用）
 * 后在 service 计算排酸时长（(排酸完成 ?? now) − 入库）/ 格式化入库时间。</p>
 *
 * <h3>写端点（入库 / 出库 / 盘点）</h3>
 * <ul>
 *   <li>统一三步：parse string ID → requireProduct → {@code assertLocationUnlocked} → INSERT flow
 *       → UPDATE location_stock（复用 {@link LocationStockMapper} 既有 add/deduct/setStockAfterCheck）。</li>
 *   <li>{@code @Transactional(rollbackFor = Exception.class)}：库存更新失败抛异常整体回滚流水。</li>
 *   <li>flowNo 复用 {@link BizCodeType#STOCK_FLOW_NO}（ctx.ioCode = IN/OT）。</li>
 * </ul>
 *
 * @author djs
 * @since SELFCHECK
 */
@Slf4j
@Service
public class StockSelfServiceImpl implements IStockSelfService {

    /**
     * 出入库方向（DDL CHAR(3)）。
     */
    private static final String INOUT_IN = "IN";
    private static final String INOUT_OUT = "OT";

    /**
     * 盘点差异流水类型（{@code djs_flow_type}）。
     */
    private static final String FLOW_CHECK_IN = "check_in";
    private static final String FLOW_CHECK_OUT = "check_out";
    /** 盘点异常出库（FIX-WMS-FLOWDICT-001，盘亏且 check_result=异常(2)）。 */
    private static final String FLOW_CHECK_ABNORMAL_OUT = "check_abnormal_out";
    /** 出库去向：盘点计损（FIX-WMS-FLOWDICT-001，盘点计损 / 异常出库回填）。 */
    private static final String STOCK_OUT_DEST_CHECK_LOSS = "check_loss";
    /** 统一损耗台账类型：盘点损耗（{@code djs_loss_type} 已 seed「盘点损耗」）。 */
    private static final String LOSS_TYPE_CHECK = "check_loss";

    /**
     * 盘点结果字典 {@code djs_check_result}：1=正常 / 2=异常 / 3=计损。
     */
    private static final int RESULT_NORMAL = 1;
    private static final int RESULT_ABNORMAL = 2;
    private static final int RESULT_LOSS = 3;

    /**
     * 白条库逻辑产品名（bar_info 无产品主数据，固定展示名）。
     */
    private static final String WHITE_BAR_PRODUCT_NAME = "白条(整只)";

    /**
     * 产品缩略图 IMG-LIB-001 分类键（belongType 当前不参与 URL 解析，仅保留调用签名一致性）。
     */
    private static final String PRODUCT_BELONG_TYPE = "product";

    private final StockSelfMapper stockSelfMapper;
    private final LocationStockMapper locationStockMapper;
    private final StockFlowMapper stockFlowMapper;
    private final ProductInfoMapper productInfoMapper;
    private final BarInfoMapper barInfoMapper;
    private final SupplierMapper supplierMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final IStockCheckService stockCheckService;
    private final ILossFlowService lossFlowService;
    private final ImageUrlResolver imageUrlResolver;

    public StockSelfServiceImpl(StockSelfMapper stockSelfMapper,
                                LocationStockMapper locationStockMapper,
                                StockFlowMapper stockFlowMapper,
                                ProductInfoMapper productInfoMapper,
                                BarInfoMapper barInfoMapper,
                                SupplierMapper supplierMapper,
                                IBizCodeGenerator bizCodeGenerator,
                                IStockCheckService stockCheckService,
                                ILossFlowService lossFlowService,
                                ImageUrlResolver imageUrlResolver) {
        this.stockSelfMapper = stockSelfMapper;
        this.locationStockMapper = locationStockMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.productInfoMapper = productInfoMapper;
        this.barInfoMapper = barInfoMapper;
        this.supplierMapper = supplierMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.stockCheckService = stockCheckService;
        this.lossFlowService = lossFlowService;
        this.imageUrlResolver = imageUrlResolver;
    }

    // ============================ 读端点 ============================

    @Override
    public List<StockStoreEntryVo> listStoreEntries() {
        return stockSelfMapper.selectStoreEntries();
    }

    @Override
    public List<StoreProductVo> listStoreProducts(String locationId, String keyword, String sort) {
        Long locId = requireLocationId(locationId);
        List<StoreProductVo> vos = stockSelfMapper.selectStoreProducts(locId, trimToNull(keyword), trimToNull(sort));
        // 114.4：产品卡缩略图（mapper SELECT 不取图，thumbUrl 恒 null → 前端全灰框）。
        //   按 productId 批量取 ProductInfo，COALESCE(product_thumb, image_oss_id) → OSS URL 回填。
        fillProductThumb(vos);
        return vos;
    }

    /**
     * 批量回填产品缩略图 public URL（114.4）：{@code COALESCE(product_thumb, image_oss_id)} → OSS URL，禁 N+1。
     *
     * <p>一次 {@code selectByIds} 取产品图 ossId，再一次 {@link ImageUrlResolver#resolveList} 转 URL
     * （与 {@code PigBurnRecordServiceImpl} 产品图解析范式一致）。取不到图的位置留 null，前端占位兜底。</p>
     */
    private void fillProductThumb(List<StoreProductVo> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        List<Long> productIds = vos.stream().map(StoreProductVo::getProductId)
            .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (productIds.isEmpty()) {
            return;
        }
        Map<Long, String> ossIdMap = new HashMap<>();
        for (ProductInfo p : productInfoMapper.selectByIds(productIds)) {
            if (p.getId() != null) {
                ossIdMap.put(p.getId(), resolveProductImageOssId(p));
            }
        }
        List<ImageUrlResolver.Item> items = vos.stream()
            .map(v -> new ImageUrlResolver.Item(
                v.getProductId() == null ? null : ossIdMap.get(v.getProductId()), PRODUCT_BELONG_TYPE))
            .collect(Collectors.toList());
        List<String> urls = imageUrlResolver.resolveList(items);
        if (urls.size() != vos.size()) {
            return;
        }
        for (int i = 0; i < vos.size(); i++) {
            vos.get(i).setThumbUrl(urls.get(i));
        }
    }

    /**
     * 产品卡展示用 ossId 优先级：用户在 admin 上传的缩略图 {@code product_thumb} 优先，退回自动匹配的
     * {@code image_oss_id}（两者都空交 resolver 走默认图兜底）。与 {@code PigBurnRecordServiceImpl} 同口径。
     */
    private static String resolveProductImageOssId(ProductInfo p) {
        return StringUtils.isNotBlank(p.getProductThumb()) ? p.getProductThumb() : p.getImageOssId();
    }

    @Override
    public List<WhiteBarStockVo> listWhiteBarStocks(String locationId) {
        // 134.1：白条库列表 = 可领用白条库存，口径对齐分割白条领用（AppletPigCutController.availableBars →
        //   queryAvailableBars，status='in_stock'）。故只取 status='in_stock'（未领未分割）；pending_cut（已领用
        //   进分割流）不再计入白条库物理在库。上限不设（盘点/库存视图需展示全部在库，不像领用 picker 截 50）。
        // locationId 不参与过滤（白条是逻辑库，bar_info 无 location_id 列）；接收参数仅保契约一致。
        List<BarInfo> bars = barInfoMapper.selectList(
            new LambdaQueryWrapper<BarInfo>()
                .eq(BarInfo::getStatus, "in_stock")
                .orderByDesc(BarInfo::getInTime));
        SimpleDateFormat mdhm = new SimpleDateFormat("MM-dd HH:mm");
        return bars.stream().map(b -> {
            WhiteBarStockVo vo = new WhiteBarStockVo();
            vo.setId(b.getId());
            vo.setProductName(WHITE_BAR_PRODUCT_NAME);
            vo.setEarNo(b.getEarNo());
            vo.setInboundTime(b.getInTime() == null ? null : mdhm.format(b.getInTime()));
            vo.setAcidDischargeDuration(formatAcidDuration(resolveAcidMinutes(b)));
            // bar_info 无门店字段，固定 null
            vo.setDesignatedStore(null);
            vo.setInboundWeight(b.getInWeight());
            return vo;
        }).toList();
    }

    /**
     * 白条排酸时长分钟数（134.4）。口径 = (排酸完成时间 ?? now) − 入库时间：
     * <ul>
     *   <li>已结算排酸时长 {@code acid_remove_time}（分割领用时按 out−in 计算写入，分钟）非空 → 直接取；</li>
     *   <li>在库排酸进行中（{@code acid_remove_time} 空，白条库均为此态）→ now − {@code in_time}；</li>
     *   <li>{@code in_time} 缺失 → null（交 {@link #formatAcidDuration} 兜「-」，不瞎编）。</li>
     * </ul>
     * bar_info 无独立「排酸完成时间」列，故未结算态用 now − in_time；与分割记录
     * {@code PigCutRecordServiceImpl.computeAcidMinutes}（out−in）复用同一时间差算法。
     */
    private static Integer resolveAcidMinutes(BarInfo b) {
        if (b.getAcidRemoveTime() != null && b.getAcidRemoveTime() >= 0) {
            return b.getAcidRemoveTime();
        }
        if (b.getInTime() == null) {
            return null;
        }
        long mins = (System.currentTimeMillis() - b.getInTime().getTime()) / 60_000L;
        return (int) Math.max(0L, mins);
    }

    @Override
    public List<PendingCheckVo> listPendingChecks(String locationId, String keyword) {
        Long locId = requireLocationId(locationId);
        return stockSelfMapper.selectPendingChecks(locId, trimToNull(keyword));
    }

    @Override
    public TableDataInfo<CheckRecordVo> pageCheckRecords(String locationId, String checkDate,
                                                         String keyword, String checkResult, PageQuery pageQuery) {
        Long locId = requireLocationId(locationId);
        IPage<CheckRecordVo> page = stockSelfMapper.selectCheckRecordsPage(
            pageQuery.build(), locId, trimToNull(checkDate), trimToNull(keyword), trimToNull(checkResult));
        return TableDataInfo.build(page);
    }

    @Override
    public TableDataInfo<InoutFlowVo> pageInoutFlows(String direction, String locationId,
                                                     String startDate, String endDate, String keyword, PageQuery pageQuery) {
        Long locId = requireLocationId(locationId);
        String inoutType = "in".equalsIgnoreCase(direction) ? INOUT_IN : INOUT_OUT;
        IPage<InoutFlowVo> page = stockSelfMapper.selectInoutFlowsPage(
            pageQuery.build(), locId, inoutType, trimToNull(startDate), trimToNull(endDate), trimToNull(keyword));
        return TableDataInfo.build(page);
    }

    // ============================ 写端点 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long inbound(ProductInboundBo bo) {
        Long locId = parseId(bo.getLocationId(), "库位");
        Long productId = parseId(bo.getProductId(), "产品");
        ProductInfo product = requireProduct(productId);
        stockCheckService.assertLocationUnlocked(locId);
        Long userId = resolveOperatorId(bo.getOperatorId());

        // 1. INSERT stock_flow（IN）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_IN));
        flow.setFlowDate(parseDateOrNow(bo.getInboundDate()));
        flow.setProductId(productId);
        flow.setWarehouseId(locId);
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(mapInboundFlowType(bo.getInoutType()));
        flow.setChangeNum(bo.getQuantity());
        flow.setChangeQuantity(bo.getQuantity());
        flow.setSupplierId(resolveSupplierId(bo.getSupplierCode()));
        flow.setOperatorId(userId);
        flow.setRemark(bo.getSourceBill());
        stockFlowMapper.insert(flow);

        // 2. UPDATE location_stock 加库存；无对应行 → 兜底 INSERT 新库存行
        int affected = locationStockMapper.addByProductLocation(locId, productId, bo.getQuantity(), userId);
        if (affected == 0) {
            insertStockRow(locId, productId, product, bo.getQuantity(), userId);
        }
        return flow.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long outbound(ProductOutboundBo bo) {
        Long locId = parseId(bo.getLocationId(), "库位");
        Long productId = parseId(bo.getProductId(), "产品");
        ProductInfo product = requireProduct(productId);
        stockCheckService.assertLocationUnlocked(locId);
        Long userId = resolveOperatorId(bo.getOperatorId());

        // 1. INSERT stock_flow（OT）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_OUT));
        flow.setFlowDate(parseDateOrNow(bo.getOutboundDate()));
        flow.setProductId(productId);
        flow.setWarehouseId(locId);
        flow.setInoutType(INOUT_OUT);
        flow.setFlowType(mapOutboundFlowType(bo.getInoutType()));
        flow.setStockOutDest(bo.getStockOutDest());
        flow.setChangeNum(bo.getQuantity().negate());
        flow.setChangeQuantity(bo.getQuantity());
        flow.setOperatorId(userId);
        stockFlowMapper.insert(flow);

        // 2. UPDATE location_stock 行锁扣减 + 数量校验
        int affected = locationStockMapper.deductByProductLocation(locId, productId, bo.getQuantity(), userId);
        if (affected == 0) {
            // 抛异常 → @Transactional 回滚流水
            throw new ServiceException(
                "库存不足或库位/产品不匹配（product=" + product.getProductName()
                    + " / location=" + locId + " / 申请=" + bo.getQuantity() + product.getProductUnit() + "）");
        }
        return flow.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long checkSubmit(StockCheckEntryBo bo) {
        Long locId = parseId(bo.getLocationId(), "库位");
        Long productId = parseId(bo.getProductId(), "产品");
        ProductInfo product = requireProduct(productId);
        stockCheckService.assertLocationUnlocked(locId);
        Long userId = resolveOperatorId(bo.getOperatorId());

        // 盘点结果 code（normal=1 / abnormal=2 / loss=3），决定库存校准口径 + 拆 flow_type
        int resultCode = mapCheckResultCode(bo.getCheckResult());
        BigDecimal sysStock = currentStock(locId, productId);

        // ❓ 口径（R80/R81，按此实现）：计损 / 异常 = 真实库存损失。
        //   - 正常：以实盘量校准账面（targetStock = checkStock，diff = checkStock - sysStock，盘盈/盘亏均留痕）。
        //   - 计损 / 异常：以「计损量 / 异常量」(diffQuantity) 为权威损失量，从系统量扣减
        //     （targetStock = sysStock - lossQty），写出库流水 + 统一损耗台账。
        //   旧实现只用 checkStock - sysStock 算差异、丢弃 diffQuantity：用户保持实盘量默认值（=当前库存）
        //   单独填计损量时 diff=0 → 不扣库存、误记为正常（R80/R82 根因）。
        BigDecimal targetStock;
        BigDecimal diff;
        if (resultCode == RESULT_NORMAL) {
            targetStock = bo.getCheckStock();
            diff = bo.getCheckStock().subtract(sysStock);
        } else {
            BigDecimal lossQty = bo.getDiffQuantity();
            if (lossQty == null || lossQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ServiceException((resultCode == RESULT_ABNORMAL ? "异常量" : "计损量") + "必须大于 0");
            }
            targetStock = sysStock.subtract(lossQty);
            if (targetStock.compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException((resultCode == RESULT_ABNORMAL ? "异常量" : "计损量")
                    + "（" + lossQty + product.getProductUnit() + "）超过系统库存量（"
                    + sysStock + product.getProductUnit() + "）");
            }
            // 损失出库 → diff 取负（|diff| = 损失量，与盘点记录卡「计损/异常量」口径一致）
            diff = lossQty.negate();
        }
        boolean surplus = diff.compareTo(BigDecimal.ZERO) >= 0;

        // 1. INSERT 盘点流水留痕（方案 A：每次盘点都写 flow）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(surplus ? INOUT_IN : INOUT_OUT));
        flow.setFlowDate(parseDateOrNow(bo.getCheckDate()));
        flow.setProductId(productId);
        flow.setWarehouseId(locId);
        flow.setInoutType(surplus ? INOUT_IN : INOUT_OUT);
        // 盘盈 → check_in；盘亏按结果类型拆：异常(2) → check_abnormal_out，计损(3) → check_out（FIX-WMS-FLOWDICT-001）
        if (surplus) {
            flow.setFlowType(FLOW_CHECK_IN);
        } else {
            flow.setFlowType(resultCode == RESULT_ABNORMAL ? FLOW_CHECK_ABNORMAL_OUT : FLOW_CHECK_OUT);
            // 盘点计损 / 异常出库去向固定为盘点计损（前端只读不可改）
            flow.setStockOutDest(STOCK_OUT_DEST_CHECK_LOSS);
        }
        flow.setChangeNum(diff);
        // changeQuantity 存变动绝对值：正常盘点 = 实盘量；计损/异常 = 损失量（ABS(diff)）。
        // 盘点记录卡按结果取量（正常→stock=实盘量；计损/异常→diffQuantity=ABS(change_num)）。
        flow.setChangeQuantity(resultCode == RESULT_NORMAL ? bo.getCheckStock() : diff.abs());
        flow.setOperatorId(userId);
        flow.setRemark(bo.getDiffReason());
        stockFlowMapper.insert(flow);

        // 2. 回写 location_stock 非篮子行至「非篮目标量 = 目标组量 − 篮子行合计」+ check_result。
        //    系统量/目标量是组口径（含耳号/地块/白条篮子行），篮子行的账随各自业务链走、不参与盘点 SET，
        //    非篮子行只承接目标组量中超出篮子合计的部分 —— SET 后组合计 == 目标组量。
        BigDecimal basketStock = basketStock(locId, productId);
        BigDecimal nonBasketTarget = targetStock.subtract(basketStock);
        if (nonBasketTarget.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("盘点目标量低于篮子行合计，非篮子行按 0 校准（差额已由盘点流水留痕）："
                    + "location={} product={} 目标组量={} 篮子合计={}", locId, productId, targetStock, basketStock);
            nonBasketTarget = BigDecimal.ZERO;
        }
        int affected = locationStockMapper.setStockAfterCheck(locId, productId, nonBasketTarget, resultCode, userId);
        if (affected == 0 && nonBasketTarget.compareTo(BigDecimal.ZERO) > 0) {
            // 无非篮子行且有非篮余量（首次建账 / 组内仅篮子行但目标超出篮子合计）→ 按非篮目标量建行；
            // 按组量全量建行会与篮子行合计翻倍
            LocationStock stock = newStockRow(locId, productId, product, nonBasketTarget, userId);
            stock.setLatestCheckTime(new Date());
            stock.setCheckResult(resultCode);
            locationStockMapper.insert(stock);
        }

        // 3. 计损 / 异常（盘亏）→ 双写统一损耗台账：损耗量 = ABS(diff)，关联本盘点流水
        if (resultCode != RESULT_NORMAL) {
            lossFlowService.record(
                LOSS_TYPE_CHECK,
                productId,
                diff.abs(),
                locId,
                userId,
                "self_check",
                null,
                flow.getId());
        }
        return flow.getId();
    }

    // ============================ 内部辅助 ============================

    /**
     * 入库 flow_type 映射（{@code djs_flow_type}）；未知值默认 purchase_in。
     */
    private static String mapInboundFlowType(String inoutType) {
        if (inoutType == null) {
            return "purchase_in";
        }
        return switch (inoutType) {
            case "purchase" -> "purchase_in";
            case "receive" -> "veg_receive_in";
            case "white_bar_buy" -> "supplier_in";
            case "pack_return" -> "pack_in";
            // 销售退货入库归门店退回（FIX-WMS-FLOWDICT-001）
            case "sale_return" -> "store_return_in";
            default -> "purchase_in";
        };
    }

    /**
     * 出库 flow_type 映射（{@code djs_flow_type}）；未知值默认 pick_out。
     */
    private static String mapOutboundFlowType(String inoutType) {
        if (inoutType == null) {
            return "pick_out";
        }
        return switch (inoutType) {
            // 来源明确（admin 录入显式选「部门领用」）→ 拆 dept_pick_out（FIX-WMS-FLOWDICT-001）
            case "dept_pick" -> "dept_pick_out";
            case "split_out" -> "cut_out";
            case "sale_out" -> "ship_out";
            case "loss_out" -> "loss";
            default -> "pick_out";
        };
    }

    /**
     * 盘点结果 code 映射（{@code djs_check_result}）：normal→1 / abnormal→2 / loss→3。
     */
    private static int mapCheckResultCode(String checkResult) {
        if (checkResult == null) {
            return RESULT_NORMAL;
        }
        return switch (checkResult) {
            case "normal" -> RESULT_NORMAL;
            case "abnormal" -> RESULT_ABNORMAL;
            case "loss" -> RESULT_LOSS;
            default -> RESULT_NORMAL;
        };
    }

    /**
     * 排酸时长（分钟）转「X小时Y分钟」；null 兜「-」。
     */
    private static String formatAcidDuration(Integer minutes) {
        if (minutes == null || minutes < 0) {
            return "-";
        }
        int h = minutes / 60;
        int m = minutes % 60;
        if (h > 0 && m > 0) {
            return h + "小时" + m + "分钟";
        }
        if (h > 0) {
            return h + "小时";
        }
        return m + "分钟";
    }

    /**
     * 查系统现量（组内全部行 SUM；无库存行返 0）—— 与 {@code StockCheckServiceImpl.currentStock} 同口径。
     *
     * <p>同 (库位,产品) 可能存在多行（耳号/地块/白条篮子 + 非篮子行），系统现量取组合计，
     * 与工人实盘的物理总量同口径。</p>
     */
    private BigDecimal currentStock(Long locationId, Long productId) {
        BigDecimal sum = locationStockMapper.sumStockByProductLocation(locationId, productId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    /**
     * 组内篮子行库存合计（{@code plot_id / ear_no / white_bar_no} 任一非 NULL）；无篮子行返 0。
     */
    private BigDecimal basketStock(Long locationId, Long productId) {
        BigDecimal sum = locationStockMapper.sumBasketStockByProductLocation(locationId, productId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    /**
     * location_stock 无对应行时兜底 INSERT 新库存行（入库首次为该 product 建账）。
     */
    private void insertStockRow(Long locationId, Long productId, ProductInfo product,
                                BigDecimal stockNum, Long userId) {
        locationStockMapper.insert(newStockRow(locationId, productId, product, stockNum, userId));
    }

    private LocationStock newStockRow(Long locationId, Long productId, ProductInfo product,
                                      BigDecimal stockNum, Long userId) {
        LocationStock stock = new LocationStock();
        stock.setLocationId(locationId);
        stock.setProductId(productId);
        stock.setProductName(product.getProductName());
        stock.setProductStock(stockNum);
        stock.setProductUnit(product.getProductUnit());
        stock.setIsEnd(0);
        stock.setOperatorId(userId);
        return stock;
    }

    /**
     * 供应商业务码 → t_md_supplier.id；为空 / 找不到返 null（不阻塞入库）。
     */
    private Long resolveSupplierId(String supplierCode) {
        if (StringUtils.isBlank(supplierCode)) {
            return null;
        }
        Supplier supplier = supplierMapper.selectOne(
            new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getSupplierCode, supplierCode.trim())
                .eq(Supplier::getDelFlag, "0")
                .last("LIMIT 1"));
        return supplier == null ? null : supplier.getId();
    }

    /**
     * 操作人解析：bo 传了用 bo 的，否则取当前登录人。
     */
    private static Long resolveOperatorId(String operatorId) {
        if (StringUtils.isNotBlank(operatorId)) {
            try {
                return Long.valueOf(operatorId.trim());
            }
            catch (NumberFormatException e) {
                throw new ServiceException("操作人 ID 非法：" + operatorId);
            }
        }
        return LoginHelper.getUserId();
    }

    /**
     * 查产品，找不到抛异常。
     */
    private ProductInfo requireProduct(Long productId) {
        ProductInfo p = productInfoMapper.selectOne(
            new LambdaQueryWrapper<ProductInfo>().eq(ProductInfo::getId, productId).last("LIMIT 1"));
        if (p == null) {
            throw new ServiceException("产品不存在或已删除：" + productId);
        }
        return p;
    }

    /**
     * 库位 ID 必填解析（读端点除白条外都需要 locationId）。
     */
    private static Long requireLocationId(String locationId) {
        if (StringUtils.isBlank(locationId)) {
            throw new ServiceException("库位 ID 不能为空");
        }
        return parseId(locationId, "库位");
    }

    /**
     * snowflake string → Long（防 JS 大数截断，后端只在此显式 parse）。
     */
    private static Long parseId(String id, String label) {
        if (StringUtils.isBlank(id)) {
            throw new ServiceException(label + " ID 不能为空");
        }
        try {
            return Long.valueOf(id.trim());
        }
        catch (NumberFormatException e) {
            throw new ServiceException(label + " ID 非法：" + id);
        }
    }

    /**
     * 日期 yyyy-MM-dd 解析；空 / 非法 → 当前时间。
     */
    private static Date parseDateOrNow(String date) {
        if (StringUtils.isBlank(date)) {
            return new Date();
        }
        Date d = DateUtils.parseDate(date.trim());
        return d == null ? new Date() : d;
    }

    /**
     * 生成流水号（复用 SYS-INFRA-004 BizCodeService）。
     */
    private String generateFlowNo(String ioCode) {
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", ioCode);
        return bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx);
    }

    private static String trimToNull(String s) {
        return StringUtils.isBlank(s) ? null : s.trim();
    }

}
