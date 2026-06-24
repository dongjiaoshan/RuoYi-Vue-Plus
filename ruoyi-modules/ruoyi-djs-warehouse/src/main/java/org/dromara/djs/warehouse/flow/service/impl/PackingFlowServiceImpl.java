package org.dromara.djs.warehouse.flow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.domain.bo.PackCheckBo;
import org.dromara.djs.warehouse.flow.domain.bo.PackInBo;
import org.dromara.djs.warehouse.flow.domain.bo.PackOutBo;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.flow.service.IPackingFlowService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 包材库 per 项写操作 Service 实现（FIX-WMS-MP-MATISSUE-001，原型图54 per 项 4 动作）。
 *
 * <h3>跨表事务一致性</h3>
 * <ul>
 *   <li>每个公共 {@code @Transactional} 方法跨表写：INSERT stock_flow + UPDATE/INSERT location_stock；
 *       任一 RuntimeException / ServiceException 触发整体回滚。</li>
 *   <li>{@code packOut} 走 {@link LocationStockMapper#deductByProductLocation} —— SQL 内置
 *       {@code product_stock >= deductQty} 行锁 + 数量校验，并发只有一次 affectedRows > 0。</li>
 *   <li>{@code packIn} 走 {@link LocationStockMapper#addByProductLocation}；无库存行则 INSERT 建账。</li>
 *   <li>{@code packCheck} 走 {@link LocationStockMapper#setStockAfterCheck}（设实盘绝对值）+
 *       差异流水（盘盈 check_in / 盘亏 check_out），逻辑对齐 {@code StockCheckServiceImpl} 单 line。</li>
 *   <li>3 动作均先 {@link IStockCheckService#assertLocationUnlocked} 拒绝盘点锁定中的库位（后端双保险）。</li>
 * </ul>
 *
 * <h3>设计：独立 service 不改共享类</h3>
 * <p>读端点（home/list/detail）在 {@code StockFlowServiceImpl}（admin + mp 共享）；写端点单独成此
 * service，只调用 / 不修改 {@code MatFlowServiceImpl} / {@code StockCheckServiceImpl} / 各 mapper，
 * 避免跨卡耦合。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-MATISSUE-001
 */
@Slf4j
@Service
public class PackingFlowServiceImpl implements IPackingFlowService {

    /**
     * 出入库方向：IN=入 / OT=出（DDL CHAR(3)）。
     */
    private static final String INOUT_IN = "IN";
    private static final String INOUT_OUT = "OT";

    /**
     * djs_flow_type 字典 value（均已 seed，无需新 DDL）。
     *
     * <p>包材库 per 项出库 = 仓库生产消耗，归生产领用（FIX-WMS-FLOWDICT-003）：flow_type={@code prod_pick_out}、
     * 强制 dest={@code prod_pick}（包材库唯一来源是仓库，无歧义，硬编码不经 scene 推断）。</p>
     */
    private static final String FLOW_PURCHASE_IN = "purchase_in";
    private static final String FLOW_PROD_PICK_OUT = "prod_pick_out";
    private static final String DEST_PROD_PICK = "prod_pick";
    private static final String FLOW_CHECK_IN = "check_in";
    private static final String FLOW_CHECK_OUT = "check_out";

    /**
     * 盘点结果字典 djs_check_result：1=正常 / 2=异常。
     */
    private static final int RESULT_NORMAL = 1;
    private static final int RESULT_ABNORMAL = 2;

    private final StockFlowMapper stockFlowMapper;
    private final LocationStockMapper locationStockMapper;
    private final ProductInfoMapper productInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final IStockCheckService stockCheckService;

    public PackingFlowServiceImpl(StockFlowMapper stockFlowMapper,
                                  LocationStockMapper locationStockMapper,
                                  ProductInfoMapper productInfoMapper,
                                  IBizCodeGenerator bizCodeGenerator,
                                  IStockCheckService stockCheckService) {
        this.stockFlowMapper = stockFlowMapper;
        this.locationStockMapper = locationStockMapper;
        this.productInfoMapper = productInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.stockCheckService = stockCheckService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long packIn(PackInBo bo) {
        ProductInfo product = requireProduct(bo.getProductId());
        Long userId = LoginHelper.getUserId();
        // 库位解析：传了用传的；为空取默认库位（包材库 per 项动作工人不选库位）
        Long locId = resolveLocationForIn(bo.getLocationId(), bo.getProductId(), product);
        stockCheckService.assertLocationUnlocked(locId);

        // 1. INSERT stock_flow（purchase_in 入库）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_IN));
        flow.setFlowDate(new Date());
        flow.setProductId(bo.getProductId());
        flow.setWarehouseId(locId);
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(FLOW_PURCHASE_IN);
        flow.setChangeNum(bo.getQuantity());
        flow.setChangeQuantity(bo.getQuantity());
        flow.setOperatorId(userId);
        flow.setRemark(bo.getRemark());
        flow.setProofOssIds(bo.getProofOssIds());
        stockFlowMapper.insert(flow);

        // 2. UPDATE location_stock 加库存；无库存行则 INSERT 建账（包材首次入库）
        int affected = locationStockMapper.addByProductLocation(locId, bo.getProductId(), bo.getQuantity(), userId);
        if (affected == 0) {
            insertStockRow(locId, product, bo.getQuantity(), userId);
        }

        return flow.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long packOut(PackOutBo bo) {
        ProductInfo product = requireProduct(bo.getProductId());
        Long userId = LoginHelper.getUserId();
        Long locId = resolveLocationId(bo.getLocationId(), bo.getProductId(), "出库");
        stockCheckService.assertLocationUnlocked(locId);

        // 1. INSERT stock_flow（pick_out 出库）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_OUT));
        flow.setFlowDate(new Date());
        flow.setProductId(bo.getProductId());
        flow.setWarehouseId(locId);
        flow.setInoutType(INOUT_OUT);
        // 包材库出库 = 仓库生产消耗：强制生产领用，dest 不信 mp 传值（FIX-WMS-FLOWDICT-003）
        flow.setFlowType(FLOW_PROD_PICK_OUT);
        flow.setStockOutDest(DEST_PROD_PICK);
        flow.setChangeNum(bo.getQuantity().negate());
        flow.setChangeQuantity(bo.getQuantity());
        flow.setOperatorId(userId);
        flow.setRemark(bo.getRemark());
        flow.setProofOssIds(bo.getProofOssIds());
        stockFlowMapper.insert(flow);

        // 2. UPDATE location_stock 行锁扣减 + 数量校验
        int affected = locationStockMapper.deductByProductLocation(locId, bo.getProductId(), bo.getQuantity(), userId);
        if (affected == 0) {
            throw new ServiceException(
                "库存不足或库位/产品不匹配（product=" + product.getProductName()
                    + " / 申请=" + bo.getQuantity() + product.getProductUnit() + "）");
        }

        return flow.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long packCheck(PackCheckBo bo) {
        ProductInfo product = requireProduct(bo.getProductId());
        Long userId = LoginHelper.getUserId();
        Long locId = resolveLocationForIn(bo.getLocationId(), bo.getProductId(), product);
        stockCheckService.assertLocationUnlocked(locId);

        // 1. 系统现量 → 差异
        BigDecimal sysStock = currentStock(locId, bo.getProductId());
        BigDecimal diff = bo.getCheckStock().subtract(sysStock);
        Integer resultType = bo.getCheckResult() != null
            ? bo.getCheckResult()
            : (diff.compareTo(BigDecimal.ZERO) == 0 ? RESULT_NORMAL : RESULT_ABNORMAL);

        // 2. 差异 != 0 → 写盘盈 / 盘亏流水
        Long flowId = null;
        if (diff.compareTo(BigDecimal.ZERO) != 0) {
            boolean surplus = diff.compareTo(BigDecimal.ZERO) > 0;
            StockFlow flow = new StockFlow();
            flow.setFlowNo(generateFlowNo(surplus ? INOUT_IN : INOUT_OUT));
            flow.setFlowDate(new Date());
            flow.setProductId(bo.getProductId());
            flow.setWarehouseId(locId);
            flow.setInoutType(surplus ? INOUT_IN : INOUT_OUT);
            flow.setFlowType(surplus ? FLOW_CHECK_IN : FLOW_CHECK_OUT);
            flow.setChangeNum(diff);
            flow.setChangeQuantity(diff.abs());
            flow.setOperatorId(userId);
            flow.setRemark(bo.getRemark());
            flow.setProofOssIds(bo.getProofOssIds());
            stockFlowMapper.insert(flow);
            flowId = flow.getId();
        }

        // 3. 回写 location_stock 至实盘绝对值 + 刷新 latest_check_time / check_result；无行则建账
        int affected = locationStockMapper.setStockAfterCheck(
            locId, bo.getProductId(), bo.getCheckStock(), resultType, userId);
        if (affected == 0) {
            LocationStock stock = insertStockRow(locId, product, bo.getCheckStock(), userId);
            // 建账行补盘点字段
            stock.setLatestCheckTime(new Date());
            stock.setCheckResult(resultType);
            locationStockMapper.updateById(stock);
        }

        return flowId;
    }

    // ============================ 内部辅助 ============================

    /**
     * 查产品，找不到 / 非包材抛异常。
     *
     * <p>protected 便于单测覆盖。</p>
     */
    protected ProductInfo requireProduct(Long productId) {
        if (productId == null) {
            throw new ServiceException("产品 ID 不能为空");
        }
        ProductInfo p = productInfoMapper.selectOne(
            new LambdaQueryWrapper<ProductInfo>().eq(ProductInfo::getId, productId).last("LIMIT 1"));
        if (p == null) {
            throw new ServiceException("产品不存在或已删除：" + productId);
        }
        return p;
    }

    /**
     * 出库 / 通用：库位解析（传了用传的；为空取该产品库存最多的库位）。无库位行抛异常。
     *
     * <p>protected 便于单测 stub。</p>
     */
    protected Long resolveLocationId(Long locationId, Long productId, String action) {
        if (locationId != null) {
            return locationId;
        }
        Long resolved = locationStockMapper.selectDefaultLocationByProduct(productId);
        if (resolved == null) {
            throw new ServiceException("该产品暂无库位，无法" + action);
        }
        return resolved;
    }

    /**
     * 入库 / 盘点：库位解析（允许产品首次无库存行）。传了用传的；为空取默认库位；
     * 仍无（产品从未有库存行）→ 抛异常引导先指定库位（包材库 per 项动作无库位选择器，此为兜底提示）。
     *
     * <p>protected 便于单测 stub。</p>
     */
    protected Long resolveLocationForIn(Long locationId, Long productId, ProductInfo product) {
        if (locationId != null) {
            return locationId;
        }
        Long resolved = locationStockMapper.selectDefaultLocationByProduct(productId);
        if (resolved == null) {
            throw new ServiceException(
                "该包材（" + product.getProductName() + "）暂无库存记录，请先在库存管理建账后再操作");
        }
        return resolved;
    }

    /**
     * 查 location + product 的系统现量（无库存行返 0）。
     *
     * <p>protected 便于单测 stub。</p>
     */
    protected BigDecimal currentStock(Long locationId, Long productId) {
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
     * location_stock 无对应行时 INSERT 一条新库存行（包材首次入库 / 首次盘点建账）。
     *
     * @return 新建的 LocationStock（已带回 id，便于调用方续写盘点字段）
     */
    private LocationStock insertStockRow(Long locationId, ProductInfo product, BigDecimal stockQty, Long userId) {
        LocationStock stock = new LocationStock();
        stock.setLocationId(locationId);
        stock.setProductId(product.getId());
        stock.setProductName(product.getProductName());
        stock.setProductStock(stockQty);
        stock.setProductUnit(product.getProductUnit());
        stock.setIsEnd(0);
        stock.setOperatorId(userId);
        locationStockMapper.insert(stock);
        return stock;
    }

    /**
     * 生成流水号（复用 SYS-INFRA-004 BizCodeService）。
     *
     * <p>protected 便于单测固定返值。</p>
     */
    protected String generateFlowNo(String ioCode) {
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", ioCode);
        return bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx);
    }

}
