package org.dromara.djs.warehouse.flow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.domain.bo.MatLossBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatPickBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatReturnBo;
import org.dromara.djs.warehouse.flow.domain.vo.MatTodaySummaryVo;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.flow.service.IMatFlowService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 物资领用 / 退回 / 损耗 Service 实现（WMS-MAT-001）。
 *
 * <h3>跨表事务一致性（本 ticket 核心风险）</h3>
 * <ul>
 *   <li>每个公共 {@code @Transactional} 方法跨 3 步：校验 → INSERT stock_flow → UPDATE location_stock；
 *       任一 RuntimeException / ServiceException 触发整体回滚。</li>
 *   <li>{@code pick} / {@code loss} 走 {@link LocationStockMapper#deductByProductLocation}
 *       —— SQL 内置 {@code product_stock >= deductQty} 行锁 + 数量校验，并发提交只有一次 affectedRows > 0。</li>
 *   <li>{@code returnBack} 走 {@link LocationStockMapper#addByProductLocation} —— 退回累加无上限。</li>
 *   <li>{@code return} / {@code loss} 额外校验"今日额度"避免工人超退 / 超损（已领 ≥ 已退 + 已损 + 当次量）。</li>
 * </ul>
 *
 * <h3>flow_no 生成</h3>
 * <p>复用 {@link BizCodeType#STOCK_FLOW_NO}（{@code F+yyyyMMdd+ioCode2+seq4}）。</p>
 *
 * @author djs
 * @since WMS-MAT-001
 */
@Slf4j
@Service
public class MatFlowServiceImpl implements IMatFlowService {

    /**
     * 出入库：IN=入 / OT=出（DDL CHAR(3)）。
     */
    private static final String INOUT_IN = "IN";
    private static final String INOUT_OUT = "OT";

    /**
     * djs_flow_type 字典 value。
     */
    private static final String FLOW_PICK_OUT = "pick_out";
    private static final String FLOW_RETURN_IN = "return_in";
    private static final String FLOW_LOSS = "loss";

    private final StockFlowMapper stockFlowMapper;
    private final LocationStockMapper locationStockMapper;
    private final ProductInfoMapper productInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final IStockCheckService stockCheckService;

    public MatFlowServiceImpl(StockFlowMapper stockFlowMapper,
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
    public Long pick(MatPickBo bo) {
        ProductInfo product = requireProduct(bo.getProductId());
        // 库位级业务锁（WMS-STOCK-001）：盘点进行中的库位禁出入库（后端双保险）
        stockCheckService.assertLocationUnlocked(bo.getLocationId());
        Long userId = LoginHelper.getUserId();

        // 1. INSERT stock_flow（pick_out 出库）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_OUT));
        flow.setFlowDate(new Date());
        flow.setProductId(bo.getProductId());
        flow.setWarehouseId(bo.getLocationId());
        flow.setInoutType(INOUT_OUT);
        flow.setFlowType(FLOW_PICK_OUT);
        flow.setStockOutDest(bo.getStockOutDest());
        flow.setChangeNum(bo.getQuantity().negate());
        flow.setChangeQuantity(bo.getQuantity());
        flow.setOperatorId(userId);
        flow.setRemark(bo.getRemark());
        flow.setProofOssIds(bo.getProofOssIds());
        stockFlowMapper.insert(flow);

        // 2. UPDATE location_stock 行锁扣减 + 数量校验
        int affected = locationStockMapper.deductByProductLocation(
            bo.getLocationId(), bo.getProductId(), bo.getQuantity(), userId);
        if (affected == 0) {
            // 抛异常 → @Transactional 回滚 step 1
            throw new ServiceException(
                "库存不足或库位/产品不匹配（product=" + product.getProductName()
                    + " / location=" + bo.getLocationId() + " / 申请=" + bo.getQuantity()
                    + product.getProductUnit() + "）");
        }

        return flow.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long returnBack(MatReturnBo bo) {
        ProductInfo product = requireProduct(bo.getProductId());
        // 库位级业务锁（WMS-STOCK-001）：盘点进行中的库位禁出入库（后端双保险）
        stockCheckService.assertLocationUnlocked(bo.getLocationId());
        Long userId = LoginHelper.getUserId();

        // 1. 校验今日额度：已领 ≥ 已退 + 已损 + 当次退回量
        ensureTodayCapacity(userId, bo.getProductId(), bo.getQuantity(), product.getProductName(), product.getProductUnit());

        // 2. INSERT stock_flow（return_in 入库）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_IN));
        flow.setFlowDate(new Date());
        flow.setProductId(bo.getProductId());
        flow.setWarehouseId(bo.getLocationId());
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(FLOW_RETURN_IN);
        flow.setChangeNum(bo.getQuantity());
        flow.setChangeQuantity(bo.getQuantity());
        flow.setOperatorId(userId);
        flow.setRemark(bo.getRemark());
        flow.setProofOssIds(bo.getProofOssIds());
        stockFlowMapper.insert(flow);

        // 3. UPDATE location_stock 加回库存
        int affected = locationStockMapper.addByProductLocation(
            bo.getLocationId(), bo.getProductId(), bo.getQuantity(), userId);
        if (affected == 0) {
            // 退回的库位 / 产品记录不存在（领用走的是同 location_id+product_id，正常不会发生；
            // 防御性兜底：库位 / 产品已被删 → 回滚）
            throw new ServiceException(
                "库存记录不存在，无法退回（product=" + product.getProductName()
                    + " / location=" + bo.getLocationId() + "）");
        }

        return flow.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long loss(MatLossBo bo) {
        ProductInfo product = requireProduct(bo.getProductId());
        // 库位级业务锁（WMS-STOCK-001）：盘点进行中的库位禁出入库（后端双保险）
        stockCheckService.assertLocationUnlocked(bo.getLocationId());
        Long userId = LoginHelper.getUserId();

        // 1. 校验今日额度（同退回）
        ensureTodayCapacity(userId, bo.getProductId(), bo.getQuantity(), product.getProductName(), product.getProductUnit());

        // 2. INSERT stock_flow（loss 出库）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_OUT));
        flow.setFlowDate(new Date());
        flow.setProductId(bo.getProductId());
        flow.setWarehouseId(bo.getLocationId());
        flow.setInoutType(INOUT_OUT);
        flow.setFlowType(FLOW_LOSS);
        flow.setChangeNum(bo.getQuantity().negate());
        flow.setChangeQuantity(bo.getQuantity());
        flow.setOperatorId(userId);
        flow.setRemark(bo.getRemark());
        flow.setProofOssIds(bo.getProofOssIds());
        stockFlowMapper.insert(flow);

        // 3. 损耗"扣减"库存（注：损耗 = 不可逆消耗，从账面剥离；与"退回"语义相反）。
        //    若工人领用后已经把物理物品消耗完才补登损耗，则库存可能已扣过 0 —— 这种情况下损耗只
        //    在流水留痕，不再走 update。affectedRows==0 时不抛异常（流水仍记录管理者审计用），
        //    打 warn 让 admin 流水查询页能看到这种"账实倒挂"明细。
        int affected = locationStockMapper.deductByProductLocation(
            bo.getLocationId(), bo.getProductId(), bo.getQuantity(), userId);
        if (affected == 0) {
            log.warn("WMS-MAT-001 loss 流水已记，但 location_stock 扣减失败（账面已不足）："
                    + "user={}, product={}, location={}, qty={}",
                userId, bo.getProductId(), bo.getLocationId(), bo.getQuantity());
        }

        return flow.getId();
    }

    @Override
    public MatTodaySummaryVo todaySummary(String matType) {
        return todaySummary(matType, null);
    }

    @Override
    public MatTodaySummaryVo todaySummary(String matType, String productId) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            return MatTodaySummaryVo.empty();
        }
        MatTodaySummaryVo vo = new MatTodaySummaryVo();
        // 单产品维度优先（BRD-FIX-MP-FEED-IA-001 MPV-FEED-09）：精确到选中产品
        if (productId != null && !productId.isBlank()) {
            Long pid;
            try {
                pid = Long.valueOf(productId.trim());
            }
            catch (NumberFormatException e) {
                throw new ServiceException("产品 ID 非法：" + productId);
            }
            vo.setPickedQuantity(safe(stockFlowMapper.sumTodayByUserProductType(userId, pid, FLOW_PICK_OUT)));
            vo.setReturnedQuantity(safe(stockFlowMapper.sumTodayByUserProductType(userId, pid, FLOW_RETURN_IN)));
            vo.setLossQuantity(safe(stockFlowMapper.sumTodayByUserProductType(userId, pid, FLOW_LOSS)));
        } else if (matType == null || matType.isBlank()) {
            vo.setPickedQuantity(safe(stockFlowMapper.sumTodayByUserType(userId, FLOW_PICK_OUT)));
            vo.setReturnedQuantity(safe(stockFlowMapper.sumTodayByUserType(userId, FLOW_RETURN_IN)));
            vo.setLossQuantity(safe(stockFlowMapper.sumTodayByUserType(userId, FLOW_LOSS)));
        } else {
            // matType（djs_mat_type）与 belong_type 取值同名映射（package/feed/seed/white_bar 等同）
            vo.setPickedQuantity(safe(stockFlowMapper.sumTodayByUserMatType(userId, FLOW_PICK_OUT, matType)));
            vo.setReturnedQuantity(safe(stockFlowMapper.sumTodayByUserMatType(userId, FLOW_RETURN_IN, matType)));
            vo.setLossQuantity(safe(stockFlowMapper.sumTodayByUserMatType(userId, FLOW_LOSS, matType)));
        }
        return vo;
    }

    /**
     * 查产品，找不到抛异常。
     *
     * <p>protected 方便单测覆盖。</p>
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
     * 校验今日额度：已领 ≥ 已退 + 已损 + 当次申请量。
     *
     * <p>protected 方便单测 stub。</p>
     */
    protected void ensureTodayCapacity(Long userId, Long productId, BigDecimal applying,
                                       String productName, String productUnit) {
        BigDecimal picked = safe(stockFlowMapper.sumTodayByUserProductType(userId, productId, FLOW_PICK_OUT));
        BigDecimal returned = safe(stockFlowMapper.sumTodayByUserProductType(userId, productId, FLOW_RETURN_IN));
        BigDecimal lost = safe(stockFlowMapper.sumTodayByUserProductType(userId, productId, FLOW_LOSS));
        BigDecimal remaining = picked.subtract(returned).subtract(lost);
        if (remaining.compareTo(applying) < 0) {
            throw new ServiceException(
                "今日额度不足（product=" + productName + " / 今日已领=" + picked + productUnit
                    + " / 已退=" + returned + " / 已损=" + lost
                    + " / 剩余可操作=" + remaining + " / 当次申请=" + applying + "）");
        }
    }

    /**
     * 生成流水号（复用 SYS-INFRA-004 BizCodeService）。
     *
     * <p>protected 方便单测固定返值。</p>
     */
    protected String generateFlowNo(String ioCode) {
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", ioCode);
        return bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx);
    }

    private static BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

}
