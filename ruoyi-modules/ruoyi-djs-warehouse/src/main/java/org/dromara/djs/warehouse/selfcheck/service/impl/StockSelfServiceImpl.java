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
import org.dromara.djs.common.supplier.domain.Supplier;
import org.dromara.djs.common.supplier.mapper.SupplierMapper;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
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

/**
 * 库存盘点自助子系统 Service 实现（SELFCHECK）。
 *
 * <h3>读端点</h3>
 * <p>聚合查询委托 {@link StockSelfMapper}（各库入口 / 库详情 / 待盘点 / 盘点记录 / 进出库流水）；
 * 白条库逐条走 {@link BarInfoMapper} 取在库白条（{@code status IN ('in_stock','pending_cut')}）
 * 后在 service 格式化排酸时长 / 入库时间。</p>
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

    private final StockSelfMapper stockSelfMapper;
    private final LocationStockMapper locationStockMapper;
    private final StockFlowMapper stockFlowMapper;
    private final ProductInfoMapper productInfoMapper;
    private final BarInfoMapper barInfoMapper;
    private final SupplierMapper supplierMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final IStockCheckService stockCheckService;

    public StockSelfServiceImpl(StockSelfMapper stockSelfMapper,
                                LocationStockMapper locationStockMapper,
                                StockFlowMapper stockFlowMapper,
                                ProductInfoMapper productInfoMapper,
                                BarInfoMapper barInfoMapper,
                                SupplierMapper supplierMapper,
                                IBizCodeGenerator bizCodeGenerator,
                                IStockCheckService stockCheckService) {
        this.stockSelfMapper = stockSelfMapper;
        this.locationStockMapper = locationStockMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.productInfoMapper = productInfoMapper;
        this.barInfoMapper = barInfoMapper;
        this.supplierMapper = supplierMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.stockCheckService = stockCheckService;
    }

    // ============================ 读端点 ============================

    @Override
    public List<StockStoreEntryVo> listStoreEntries() {
        return stockSelfMapper.selectStoreEntries();
    }

    @Override
    public List<StoreProductVo> listStoreProducts(String locationId, String keyword, String sort) {
        Long locId = requireLocationId(locationId);
        return stockSelfMapper.selectStoreProducts(locId, trimToNull(keyword), trimToNull(sort));
    }

    @Override
    public List<WhiteBarStockVo> listWhiteBarStocks(String locationId) {
        // locationId 不参与过滤（白条是逻辑库，bar_info 无 location_id 列）；接收参数仅保契约一致
        List<BarInfo> bars = barInfoMapper.selectList(
            new LambdaQueryWrapper<BarInfo>()
                .in(BarInfo::getStatus, List.of("in_stock", "pending_cut"))
                .eq(BarInfo::getDelFlag, "0")
                .orderByDesc(BarInfo::getInTime));
        SimpleDateFormat mdhm = new SimpleDateFormat("MM-dd HH:mm");
        return bars.stream().map(b -> {
            WhiteBarStockVo vo = new WhiteBarStockVo();
            vo.setId(b.getId());
            vo.setProductName(WHITE_BAR_PRODUCT_NAME);
            vo.setEarNo(b.getEarNo());
            vo.setInboundTime(b.getInTime() == null ? null : mdhm.format(b.getInTime()));
            vo.setAcidDischargeDuration(formatAcidDuration(b.getAcidRemoveTime()));
            // bar_info 无门店字段，固定 null
            vo.setDesignatedStore(null);
            vo.setInboundWeight(b.getInWeight());
            return vo;
        }).toList();
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
                                                     String flowDate, String keyword, PageQuery pageQuery) {
        Long locId = requireLocationId(locationId);
        String inoutType = "in".equalsIgnoreCase(direction) ? INOUT_IN : INOUT_OUT;
        IPage<InoutFlowVo> page = stockSelfMapper.selectInoutFlowsPage(
            pageQuery.build(), locId, inoutType, trimToNull(flowDate), trimToNull(keyword));
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

        // 1. 系统量 + 差异（实盘 - 系统）
        BigDecimal sysStock = currentStock(locId, productId);
        BigDecimal diff = bo.getCheckStock().subtract(sysStock);
        boolean surplus = diff.compareTo(BigDecimal.ZERO) >= 0;

        // 盘点结果 code（normal=1 / abnormal=2 / loss=3），用于拆盘亏 flow_type + 回写 location_stock
        int resultCode = mapCheckResultCode(bo.getCheckResult());

        // 2. INSERT 盘点流水留痕（方案 A：每次盘点都写 flow）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(surplus ? INOUT_IN : INOUT_OUT));
        flow.setFlowDate(parseDateOrNow(bo.getCheckDate()));
        flow.setProductId(productId);
        flow.setWarehouseId(locId);
        flow.setInoutType(surplus ? INOUT_IN : INOUT_OUT);
        // 盘盈 → check_in；盘亏按结果类型拆：异常(2) → check_abnormal_out，正常计损 → check_out（FIX-WMS-FLOWDICT-001）
        if (surplus) {
            flow.setFlowType(FLOW_CHECK_IN);
        } else {
            flow.setFlowType(resultCode == RESULT_ABNORMAL ? FLOW_CHECK_ABNORMAL_OUT : FLOW_CHECK_OUT);
            // 盘点计损 / 异常出库去向固定为盘点计损（前端只读不可改）
            flow.setStockOutDest(STOCK_OUT_DEST_CHECK_LOSS);
        }
        flow.setChangeNum(diff);
        // changeQuantity 存实盘量（与盘点记录 tab 的 stock 列口径一致）
        flow.setChangeQuantity(bo.getCheckStock());
        flow.setOperatorId(userId);
        flow.setRemark(bo.getDiffReason());
        stockFlowMapper.insert(flow);

        // 3. 回写 location_stock 至实盘绝对值 + check_result；无行 → 兜底 INSERT
        int affected = locationStockMapper.setStockAfterCheck(locId, productId, bo.getCheckStock(), resultCode, userId);
        if (affected == 0) {
            LocationStock stock = newStockRow(locId, productId, product, bo.getCheckStock(), userId);
            stock.setLatestCheckTime(new Date());
            stock.setCheckResult(resultCode);
            locationStockMapper.insert(stock);
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
     * 查系统现量（无库存行返 0）—— 与 {@code StockCheckServiceImpl.currentStock} 同口径。
     */
    private BigDecimal currentStock(Long locationId, Long productId) {
        LocationStock stock = locationStockMapper.selectOne(
            new LambdaQueryWrapper<LocationStock>()
                .eq(LocationStock::getLocationId, locationId)
                .eq(LocationStock::getProductId, productId)
                .last("LIMIT 1"));
        if (stock == null || stock.getProductStock() == null) {
            return BigDecimal.ZERO;
        }
        return stock.getProductStock();
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
