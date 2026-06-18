package org.dromara.djs.warehouse.flow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.domain.bo.MatLossBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatPickBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatReturnBo;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueItemVo;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueLocationVo;
import org.dromara.djs.warehouse.flow.domain.vo.MatTodaySummaryVo;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.flow.service.IMatFlowService;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
    private final CropInfoMapper cropInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final IStockCheckService stockCheckService;
    private final ImageUrlResolver imageUrlResolver;

    public MatFlowServiceImpl(StockFlowMapper stockFlowMapper,
                              LocationStockMapper locationStockMapper,
                              ProductInfoMapper productInfoMapper,
                              CropInfoMapper cropInfoMapper,
                              IBizCodeGenerator bizCodeGenerator,
                              IStockCheckService stockCheckService,
                              ImageUrlResolver imageUrlResolver) {
        this.stockFlowMapper = stockFlowMapper;
        this.locationStockMapper = locationStockMapper;
        this.productInfoMapper = productInfoMapper;
        this.cropInfoMapper = cropInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.stockCheckService = stockCheckService;
        this.imageUrlResolver = imageUrlResolver;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long pick(MatPickBo bo) {
        // 自产果蔬「按地块维度」领用（步11 偏差修复 · 决策 a）：plotId 非空时走 plot 维度扣减分支。
        // 自产果蔬入库时库存按 (plot_id, location) 建账（无 product_id），故领用也按 plot 扣减；
        // pick_out 流水带 plot_id，且 product_id 走 crop.related_product 解析（与 admin 打包统计契约）。
        if (bo.getPlotId() != null) {
            return pickSelfVeg(bo);
        }
        return pickByProduct(bo);
    }

    /**
     * 外购 / 包材等 product 维度领用（现有路径，plotId 为空时走此分支，行为不变）。
     */
    private Long pickByProduct(MatPickBo bo) {
        if (bo.getProductId() == null) {
            throw new ServiceException("产品 ID 不能为空（外购果蔬 / 包材按产品维度领用必填）");
        }
        ProductInfo product = requireProduct(bo.getProductId());
        // 库位可空（饲料子页不让工人选库位）：为空时按 productId 解析默认库位
        Long locId = resolveLocationId(bo.getLocationId(), bo.getProductId(), "领用");
        // 库位级业务锁（WMS-STOCK-001）：盘点进行中的库位禁出入库（后端双保险）
        stockCheckService.assertLocationUnlocked(locId);
        Long userId = LoginHelper.getUserId();

        // 1. INSERT stock_flow（pick_out 出库）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_OUT));
        flow.setFlowDate(new Date());
        flow.setProductId(bo.getProductId());
        flow.setWarehouseId(locId);
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
            locId, bo.getProductId(), bo.getQuantity(), userId);
        if (affected == 0) {
            // 抛异常 → @Transactional 回滚 step 1
            throw new ServiceException(
                "库存不足或库位/产品不匹配（product=" + product.getProductName()
                    + " / location=" + locId + " / 申请=" + bo.getQuantity()
                    + product.getProductUnit() + "）");
        }

        return flow.getId();
    }

    /**
     * 自产果蔬「按地块维度」领用（步11 偏差修复 · 决策 a）。
     *
     * <p>三步同事务：</p>
     * <ol>
     *   <li>解析库位（bo 传了用 bo 的；为空按 plot 取库存最多库位）+ 盘点锁校验；</li>
     *   <li>INSERT pick_out 流水：<b>setPlotId(plotId)</b>（贯穿地块编号）+ product_id 走
     *       plot→crop→{@code crop.related_product} 解析（让 admin 打包 {@code belong_type='vegetable'}
     *       的 join 能计入；解析不到兜底 0 + warn，不阻塞领用）；</li>
     *   <li>UPDATE location_stock 按 (plot, location) 行锁扣减 + 数量校验。</li>
     * </ol>
     */
    private Long pickSelfVeg(MatPickBo bo) {
        Long plotId = bo.getPlotId();
        // 库位：bo 传了用 bo 的；为空按 plot 取库存最多库位（自产果蔬常落 L0003/L0004/L0006）
        Long locId = bo.getLocationId();
        if (locId == null) {
            locId = locationStockMapper.selectDefaultLocationByPlot(plotId);
            if (locId == null) {
                throw new ServiceException("该地块暂无可领用的自产果蔬库存，无法领用（plotId=" + plotId + "）");
            }
        }
        // 库位级业务锁（WMS-STOCK-001）：盘点进行中的库位禁出入库（后端双保险）
        stockCheckService.assertLocationUnlocked(locId);
        Long userId = LoginHelper.getUserId();

        // product_id：plot→crop→crop.related_product 解析果蔬成品（与 admin 打包统计 join 契约）；
        // 解析不到兜底 0 + warn（作物未配 related_product，与步5/步6 数据治理同源，不阻塞领用）
        Long resolvedProductId = resolveVegProductId(plotId);

        // 1. INSERT stock_flow（pick_out 出库，带 plot_id + 解析出的果蔬成品 product_id）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_OUT));
        flow.setFlowDate(new Date());
        flow.setProductId(resolvedProductId);
        flow.setPlotId(plotId);
        flow.setWarehouseId(locId);
        flow.setInoutType(INOUT_OUT);
        flow.setFlowType(FLOW_PICK_OUT);
        flow.setStockOutDest(bo.getStockOutDest());
        flow.setChangeNum(bo.getQuantity().negate());
        flow.setChangeQuantity(bo.getQuantity());
        flow.setOperatorId(userId);
        flow.setRemark(bo.getRemark());
        flow.setProofOssIds(bo.getProofOssIds());
        stockFlowMapper.insert(flow);

        // 2. UPDATE location_stock 按 (plot, location) 行锁扣减 + 数量校验
        int affected = locationStockMapper.deductByPlotLocation(
            locId, plotId, bo.getQuantity(), userId);
        if (affected == 0) {
            // 抛异常 → @Transactional 回滚 step 1
            throw new ServiceException(
                "自产果蔬库存不足或地块/库位不匹配（plotId=" + plotId
                    + " / location=" + locId + " / 申请=" + bo.getQuantity() + "kg）");
        }

        return flow.getId();
    }

    /**
     * 自产果蔬 plot→crop→{@code crop.related_product} 解析果蔬成品 product_id。
     *
     * <p>自产果蔬 plot 维度库存行不存 crop_id，先按 plot 反查最近一条自产收货的 crop_id，再取
     * {@code crop.related_product}（作物↔果蔬成品映射，与 {@code VegetableHandleServiceImpl.resolveProductIdByCrop}
     * 同规则）。任一环节缺失（无收货记录 / 作物已删 / 未配 related_product）→ 返 0 + warn，不阻塞领用
     * （与步5/步6 数据治理同源；product_id=0 不影响领用本身，只是 admin 打包 vegetable 统计 join 不命中）。</p>
     *
     * <p>protected 便于单测 stub。</p>
     *
     * @param plotId 地块 ID
     * @return 解析出的果蔬成品 product_id；无法解析返 0
     */
    protected Long resolveVegProductId(Long plotId) {
        Long cropId = locationStockMapper.selectCropIdByPlot(plotId);
        if (cropId == null) {
            log.warn("自产果蔬领用：plot 无自产收货记录，无法反查作物，product_id 兜底 0 — plotId={}", plotId);
            return 0L;
        }
        CropInfo crop = cropInfoMapper.selectById(cropId);
        if (crop == null || crop.getRelatedProduct() == null) {
            log.warn("自产果蔬领用：作物 related_product 未配置，product_id 兜底 0 — plotId={} cropId={}"
                + "（请在 admin 作物录入页填写「关联产品」建立作物↔果蔬成品映射）", plotId, cropId);
            return 0L;
        }
        return crop.getRelatedProduct();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long returnBack(MatReturnBo bo) {
        // 自产果蔬按地块领用（pick_out product_id 兜底 0）暂不支持退回（库存按 plot 维度建账、无 product_id 行）。
        // V1 显式拦截给清晰提示，避免走 product 维度 addByProductLocation(productId=0) 命中 0 行后报"库存记录不存在"。
        if (bo.getProductId() != null && bo.getProductId() == 0L) {
            throw new ServiceException("自产果蔬（按地块领用）V1 暂不支持退回，如需请联系管理员");
        }
        ProductInfo product = requireProduct(bo.getProductId());
        // 库位可空（饲料子页不让工人选库位）：为空时按 productId 解析默认库位
        Long locId = resolveLocationId(bo.getLocationId(), bo.getProductId(), "退回");
        // 库位级业务锁（WMS-STOCK-001）：盘点进行中的库位禁出入库（后端双保险）
        stockCheckService.assertLocationUnlocked(locId);
        Long userId = LoginHelper.getUserId();

        // 1. 校验今日额度：已领 ≥ 已退 + 已损 + 当次退回量
        ensureTodayCapacity(userId, bo.getProductId(), bo.getQuantity(), product.getProductName(), product.getProductUnit());

        // 2. INSERT stock_flow（return_in 入库）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_IN));
        flow.setFlowDate(new Date());
        flow.setProductId(bo.getProductId());
        flow.setWarehouseId(locId);
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
            locId, bo.getProductId(), bo.getQuantity(), userId);
        if (affected == 0) {
            // 退回的库位 / 产品记录不存在（领用走的是同 location_id+product_id，正常不会发生；
            // 防御性兜底：库位 / 产品已被删 → 回滚）
            throw new ServiceException(
                "库存记录不存在，无法退回（product=" + product.getProductName()
                    + " / location=" + locId + "）");
        }

        return flow.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long loss(MatLossBo bo) {
        // 自产果蔬按地块领用（pick_out product_id 兜底 0）暂不支持损耗登记（库存按 plot 维度建账、无 product_id 行）。
        if (bo.getProductId() != null && bo.getProductId() == 0L) {
            throw new ServiceException("自产果蔬（按地块领用）V1 暂不支持损耗登记，如需请联系管理员");
        }
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

    @Override
    public List<MatIssueLocationVo> issueLocations(String belongType) {
        return locationStockMapper.selectMatIssueLocations(parseBelongTypes(belongType));
    }

    @Override
    public List<MatIssueItemVo> selfVegIssueItems(String locationId) {
        Long locId = parseLocationId(locationId);
        List<MatIssueItemVo> items = locationStockMapper.selectSelfVegIssueItems(locId);
        // IMG-LIB-001：productThumb 走 4 层 resolver（L1 crop.image_oss_id → L2 belong_type 默认图 → L3 全局），批量禁 N+1
        if (items != null && !items.isEmpty()) {
            List<ImageUrlResolver.Item> resolveItems = items.stream()
                .map(v -> new ImageUrlResolver.Item(v.getProductThumb(), v.getBelongType()))
                .toList();
            List<String> urls = imageUrlResolver.resolveList(resolveItems);
            if (urls.size() == items.size()) {
                for (int i = 0; i < items.size(); i++) {
                    items.get(i).setProductThumb(urls.get(i));
                }
            }
        }
        return items;
    }

    @Override
    public List<MatIssueItemVo> issueItems(String belongType, String locationId) {
        List<String> belongTypes = parseBelongTypes(belongType);
        Long locId = parseLocationId(locationId);
        Long userId = LoginHelper.getUserId();
        List<MatIssueItemVo> items = locationStockMapper.selectMatIssueItems(belongTypes, locId, userId);
        // IMG-LIB-001：productThumb 走 4 层 resolver（L1 image_oss_id → L2 belong_type 默认图 → L3 全局），批量禁 N+1
        if (items != null && !items.isEmpty()) {
            List<ImageUrlResolver.Item> resolveItems = items.stream()
                .map(v -> new ImageUrlResolver.Item(v.getProductThumb(), v.getBelongType()))
                .toList();
            List<String> urls = imageUrlResolver.resolveList(resolveItems);
            if (urls.size() == items.size()) {
                for (int i = 0; i < items.size(); i++) {
                    items.get(i).setProductThumb(urls.get(i));
                }
            }
        }
        return items;
    }

    /**
     * 业态参数解析：逗号分隔字符串 → 去空白 / 去空项的 List。
     *
     * <p>mp 物资领用列表 / chip 必须带业态（5 tab 各锁一或多种），不允许空（空会扫全表产品，无意义）。
     * 「猪肉产品」tab 传 {@code "pork,white_bar"}（项目猪肉链口径，参 {@code TraceServiceImpl.PORK_BELONG_TYPES}），
     * 其余 tab 单值。解析后为空 → 抛 ServiceException。</p>
     */
    private static List<String> parseBelongTypes(String belongType) {
        if (belongType == null || belongType.isBlank()) {
            throw new ServiceException("业态类型不能为空");
        }
        List<String> list = Arrays.stream(belongType.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
        if (list.isEmpty()) {
            throw new ServiceException("业态类型不能为空");
        }
        return list;
    }

    /**
     * 库位 ID 字符串 → Long（snowflake 防截断：前端按 string 传，后端只在此显式 parse）。
     *
     * <p>为空 / 空白 → 返 null（chip 未选中态，列表跨库位聚合）。非法字符串 → 抛 ServiceException。</p>
     */
    private static Long parseLocationId(String locationId) {
        if (locationId == null || locationId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(locationId.trim());
        }
        catch (NumberFormatException e) {
            throw new ServiceException("库位 ID 非法：" + locationId);
        }
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
     * 解析库位 ID：bo 传了用 bo 的；为空（饲料子页不选库位）则按 productId 取默认库位（库存最多）。
     *
     * <p>投喂语义本无库位，工人不选 —— 兜底取该产品库存最多的库位。解析后仍为空（产品无任何
     * location_stock 行）→ 抛 ServiceException。不修改 bo，调用方用返回的局部变量（不污染入参）。</p>
     *
     * <p>protected 方便单测 stub。</p>
     *
     * @param locationId bo 传入的库位 ID（可空）
     * @param productId  产品 ID
     * @param action     操作名（"领用" / "退回"），用于异常文案
     * @return 解析后的库位 ID（非空）
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
