package org.dromara.djs.warehouse.flow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.cross.domain.vo.TodayBarVo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.domain.bo.MatLossBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatPickBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatReturnBo;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueBasketVo;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueItemVo;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueLocationVo;
import org.dromara.djs.warehouse.flow.domain.vo.MatTodaySummaryVo;
import org.dromara.djs.warehouse.flow.domain.vo.MatWhiteBarBatchVo;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.flow.service.IMatFlowService;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * 可打包食品原料业态（统一目标模型 2026-06-20）：领用其 attr=2 原料时产 product_inhouse 当打包来源。
     *
     * <p>pork / white_bar 走各自篮子路径（ear_no）已产 inhouse、不经 {@link #bridgeMaterialInhouse}；
     * package / feed / seed / medicine = 物资不产 inhouse；gift_box（礼盒）= 组合品无原料概念，不在此列。</p>
     */
    private static final Set<String> PACKABLE_FOOD_BELONG_TYPES =
        Set.of("vegetable", "egg", "dry_good", "other");

    /**
     * 「白条·整只」canonical SKU id（bar_info 无 product_id FK，本 service 回填给领用 BO 用）。
     */
    private static final String WHITE_BAR_WHOLE_PRODUCT_ID = "100000000000000001";

    /**
     * 「白条·整只」产品名称（mp 白条批次卡主标题）。
     */
    private static final String WHITE_BAR_WHOLE_PRODUCT_NAME = "白条·整只";

    /**
     * 白条计数单位（按头计）。
     */
    private static final String WHITE_BAR_WHOLE_PRODUCT_UNIT = "头";

    /**
     * 入库时间展示格式 {@code MM-dd HH:mm}（mp 白条批次卡）。
     */
    private static final DateTimeFormatter INBOUND_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final StockFlowMapper stockFlowMapper;
    private final LocationStockMapper locationStockMapper;
    private final ProductInfoMapper productInfoMapper;
    private final ProductInhouseMapper productInhouseMapper;
    private final CropInfoMapper cropInfoMapper;
    private final BarInfoMapper barInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final IStockCheckService stockCheckService;
    private final ImageUrlResolver imageUrlResolver;

    public MatFlowServiceImpl(StockFlowMapper stockFlowMapper,
                              LocationStockMapper locationStockMapper,
                              ProductInfoMapper productInfoMapper,
                              ProductInhouseMapper productInhouseMapper,
                              CropInfoMapper cropInfoMapper,
                              BarInfoMapper barInfoMapper,
                              IBizCodeGenerator bizCodeGenerator,
                              IStockCheckService stockCheckService,
                              ImageUrlResolver imageUrlResolver) {
        this.stockFlowMapper = stockFlowMapper;
        this.locationStockMapper = locationStockMapper;
        this.productInfoMapper = productInfoMapper;
        this.productInhouseMapper = productInhouseMapper;
        this.cropInfoMapper = cropInfoMapper;
        this.barInfoMapper = barInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.stockCheckService = stockCheckService;
        this.imageUrlResolver = imageUrlResolver;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long pick(MatPickBo bo) {
        // 「按源手选」领用（对齐客户最新原型「仓库>分拣发货>物资领用」）：batchId 非空 = 用户在源篮子列表
        // 里手选了某一篮（猪肉耳号篮 / 自产果蔬地块篮），按该篮 id 行锁扣减 + 产带源标签的 inhouse。
        // 优先于 plot / product 维度兜底（兜底保留给无 batchId 的旧调用）。
        if (bo.getBatchId() != null) {
            return pickByBatch(bo);
        }
        // 自产果蔬「按地块维度」领用（步11 偏差修复 · 决策 a）：plotId 非空时走 plot 维度扣减分支。
        // 自产果蔬入库时库存按 (plot_id, location) 建账（无 product_id），故领用也按 plot 扣减；
        // pick_out 流水带 plot_id，且 product_id 走 crop.related_product 解析（与 admin 打包统计契约）。
        if (bo.getPlotId() != null) {
            return pickSelfVeg(bo);
        }
        return pickByProduct(bo);
    }

    /**
     * 不要求「有对应生产产品」的业态（领用后不打包成产品，故无需也不该有对应成品）：
     * 包材=打包耗材 / 白条=领用去分割（间接成品，非 product_material 直连）/ 饲料=投喂 / 种子=播种。
     * 除这几类外，mp 物资领用本质是「打包前工序」，所有原材料都必须先有对应生产产品领用才有意义（Kevin 2026-06-21）。
     */
    private static final Set<String> NON_PACK_BELONG_TYPES = Set.of("package", "white_bar", "feed", "seed");

    @Override
    public boolean canIssueMaterial(String productId, String plotId) {
        Long materialId = resolveMaterialId(productId, plotId);
        // 解析不到原材料 id（productId/plotId 都空，或 plot 查不到 crop/related_product）→ 不拦，允许领用。
        if (materialId == null) {
            return true;
        }
        ProductInfo material = productInfoMapper.selectById(materialId);
        // 查不到 / 非原材料(attr!=2) / 非打包消耗类业态（包材·白条·饲料·种子）→ 不在本约束内，允许领用。
        if (material == null
            || !Integer.valueOf(2).equals(material.getProductAttr())
            || NON_PACK_BELONG_TYPES.contains(material.getBelongType())) {
            return true;
        }
        // 其余原材料（果蔬/猪肉/鸡蛋/干货/其他…）：必须存在对应成品(attr=1 且 product_material 指向它)，
        // 否则禁止领用（前端弹「原材料没有对应的生产产品，请先创建」）。
        return productInfoMapper.existsFinishedProductByMaterial(materialId);
    }

    /**
     * 解析本次领用的原材料 product_id（mp 软校验用）。
     *
     * <p>productId 非空 → 直接 parse；否则 plotId 非空 → 复用 {@link #resolveVegProductId}（plot→crop→
     * {@code crop.related_product}）。解析不到（皆空 / 非数字 / plot 查不到 crop 或 related_product，
     * {@code resolveVegProductId} 兜底返 0）→ 返 {@code null}（调用方据此返 true 不打扰）。</p>
     */
    private Long resolveMaterialId(String productId, String plotId) {
        if (productId != null && !productId.isBlank()) {
            try {
                return Long.parseLong(productId.trim());
            }
            catch (NumberFormatException e) {
                // productId 非数字（异常入参）→ 视为解析不到，不打扰（返 null → 调用方返 true）
                log.warn("canIssueMaterial: productId 非数字，跳过校验 — productId={}", productId);
                return null;
            }
        }
        if (plotId != null && !plotId.isBlank()) {
            Long parsedPlotId;
            try {
                parsedPlotId = Long.parseLong(plotId.trim());
            }
            catch (NumberFormatException e) {
                // plotId 非数字（异常入参）→ 视为解析不到，不打扰
                log.warn("canIssueMaterial: plotId 非数字，跳过校验 — plotId={}", plotId);
                return null;
            }
            // resolveVegProductId 兜底返 0L（plot 无收货 / 作物未配 related_product）→ 归一成 null 表示「解析不到」
            Long resolved = resolveVegProductId(parsedPlotId);
            return resolved != null && resolved != 0L ? resolved : null;
        }
        return null;
    }

    /**
     * 「按源手选」领用（对齐客户最新原型「仓库&gt;分拣发货&gt;物资领用」）：用户在源篮子列表里手选某一篮
     * （{@code batchId = location_stock.id}），按该篮 id 行锁扣减 + 产一行 {@code product_inhouse}
     * 带篮的 {@code ear_no} / {@code plot_id} / {@code product_id} 源标签 → 打包链照常显示对应耳号/地块。
     *
     * <p>三步同事务：</p>
     * <ol>
     *   <li>查选中篮 {@code location_stock[id=batchId]}（防御：未软删 + 库存&gt;0；productId 一致性校验）；</li>
     *   <li>{@code deductStockById(batchId, qty)}（行锁 {@code stock>=qty}）—— affected==0 抛
     *       {@link ServiceException}（库存不足 / 篮子被并发占用）→ {@code @Transactional} 回滚 step3 流水；</li>
     *   <li>产一行 {@code product_inhouse}（带篮源标签、{@code produce_date=今天}）→ 打包来源；</li>
     *   <li>写 pick_out 流水：{@code productId=篮.product_id} + {@code plotId=篮.plot_id} +
     *       {@code earNo=篮.ear_no}。</li>
     * </ol>
     */
    private Long pickByBatch(MatPickBo bo) {
        LocationStock basket = locationStockMapper.selectById(bo.getBatchId());
        if (basket == null || !"0".equals(basket.getDelFlag())) {
            throw new ServiceException("选中的篮子不存在或已删除（batchId=" + bo.getBatchId() + "）");
        }
        if (basket.getProductStock() == null || basket.getProductStock().signum() <= 0) {
            throw new ServiceException("选中的篮子已无库存，无法领用（batchId=" + bo.getBatchId() + "）");
        }
        // 一致性校验：BO 带的 productId 与篮子 product_id 须一致（前端从源列表选篮，两者应同源）
        if (bo.getProductId() != null && basket.getProductId() != null
            && !bo.getProductId().equals(basket.getProductId())) {
            throw new ServiceException("选中篮子与产品不匹配（batch.productId=" + basket.getProductId()
                + " / bo.productId=" + bo.getProductId() + "）");
        }
        Long locId = basket.getLocationId();
        // 库位级业务锁（WMS-STOCK-001）：盘点进行中的库位禁出入库（后端双保险）
        stockCheckService.assertLocationUnlocked(locId);
        Long userId = LoginHelper.getUserId();

        Long productId = basket.getProductId();
        // product 主数据可空（篮子 product_id 缺失或 product 已删时，名称 / 单位回退篮子冗余字段）
        ProductInfo product = productId == null ? null : productInfoMapper.selectById(productId);

        // 1. INSERT stock_flow（pick_out 出库，带篮的 product_id + plot_id + ear_no 源标签）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_OUT));
        flow.setFlowDate(new Date());
        flow.setProductId(productId);
        flow.setPlotId(basket.getPlotId());
        flow.setEarNo(basket.getEarNo());
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

        // 2. 行锁扣减选中篮（WHERE stock>=qty）；并发被占用 / 库存不足 → affected==0 → 抛 → 回滚 step1
        int affected = locationStockMapper.deductStockById(basket.getId(), bo.getQuantity(), userId);
        if (affected == 0) {
            throw new ServiceException(
                "库存不足或篮子被并发占用（batchId=" + basket.getId()
                    + " / 申请=" + bo.getQuantity().stripTrailingZeros().toPlainString()
                    + (basket.getProductUnit() == null ? "" : basket.getProductUnit()) + "）");
        }

        // 3. 产一行 product_inhouse（带篮源标签 → 打包来源；produce_date=今天）
        ProductInhouse inhouse = new ProductInhouse();
        inhouse.setProduceDate(java.sql.Date.valueOf(LocalDate.now()));
        inhouse.setProduceTime(new Date());
        inhouse.setProductId(productId);
        inhouse.setProductName(product != null ? product.getProductName() : basket.getProductName());
        inhouse.setProductType(product != null && product.getProductType() != null ? product.getProductType() : 1);
        inhouse.setProductUnit(product != null ? product.getProductUnit() : basket.getProductUnit());
        inhouse.setProductWeight(bo.getQuantity());
        inhouse.setEarNo(basket.getEarNo());     // 猪肉篮 = 耳号源标签
        inhouse.setPlotId(basket.getPlotId());   // 自产果蔬篮 = 地块源标签
        inhouse.setLocationId(locId);
        inhouse.setMaterialId(productId);         // 原材料 = 自身（成品打包侧经 product_material 反查共享池）
        inhouse.setMaterialConsume(bo.getQuantity());
        productInhouseMapper.insert(inhouse);

        return flow.getId();
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

        // 2/3. 按业态扣库存 + 桥接打包来源（doc/14 §3 领用 = location_stock − ⇄ product_inhouse +）：
        if ("pork".equals(product.getBelongType())) {
            // 猪肉原材料按「篮子(ear_no)」FIFO 领用：分割入冷库的篮子逐个扣，每扣一篮产一行 product_inhouse
            //（带该篮 ear_no、produce_date=今天）→ 肉品打包来源。耳号随篮子自动带到打包，追溯链路不断。
            consumePorkBaskets(bo.getProductId(), bo.getQuantity(), locId, product, userId);
        } else if (isVegSelfMaterial(product)) {
            // 自产果蔬原料按「地块篮子(plot_id)」FIFO 领用（对齐猪肉 ear_no 篮子，doc/14 §1/§3）：毛菜处理入冷库的
            // 篮子（带 plot_id）逐个扣，每扣一篮产一行 product_inhouse（带该篮 plot_id、produce_date=今天）→
            // 果蔬打包右台显「对应地块」。地块随篮子自动带到打包；申请量可跨多地块篮子拼够。
            consumeVegBaskets(bo.getProductId(), bo.getQuantity(), locId, product, userId);
        } else {
            // 其他可打包食品原料（egg / dry_good / other / 外购 vegetable）+ 包材/饲料/种子等物资：
            // product 维度行锁扣减 + 数量校验
            int affected = locationStockMapper.deductByProductLocation(
                locId, bo.getProductId(), bo.getQuantity(), userId);
            if (affected == 0) {
                // 抛异常 → @Transactional 回滚 step 1
                throw new ServiceException(
                    "库存不足或库位/产品不匹配（product=" + product.getProductName()
                        + " / location=" + locId + " / 申请=" + bo.getQuantity()
                        + product.getProductUnit() + "）");
            }
            // 桥接打包来源（其他业态食品原料）：写一条 product_inhouse（produce_date=当天），admin 打包来源
            // （listSourceForDry / listSourceForVeg 等按 produce_date=今天过滤）读它。仅可打包食品原料
            // （attr=2 且 belong_type ∈ {vegetable, egg, dry_good, other}）触发，包材/饲料/种子不写。
            bridgeMaterialInhouse(bo.getProductId(), bo.getQuantity(), bo.getPlotId(), locId);
        }

        return flow.getId();
    }

    /**
     * 猪肉原材料按「篮子(ear_no)」FIFO 领用（doc/14 §3）。
     *
     * <p>分割入冷库时每头猪每部位 = 一篮 {@code location_stock}（带 ear_no 标签）。据门店需求领用某原材料
     * N kg 时，按 id 升序（先进先出）逐篮扣减 location_stock，每扣一篮产一行 {@code product_inhouse}
     * （带该篮 ear_no、{@code produce_date=今天}）→ 肉品打包来源。耳号随篮子自动带到打包，追溯链路
     * （出栏→燎毛→分割→打包）不断；申请量可跨多头猪的篮子拼够。</p>
     *
     * @throws ServiceException 篮子总量不足申请量（{@code @Transactional} 回滚领用流水）
     */
    private void consumePorkBaskets(Long productId, BigDecimal quantity, Long locId, ProductInfo product, Long userId) {
        List<LocationStock> baskets = locationStockMapper.selectList(
            new LambdaQueryWrapper<LocationStock>()
                .eq(LocationStock::getProductId, productId)
                .eq(LocationStock::getLocationId, locId)
                .isNotNull(LocationStock::getEarNo)
                .gt(LocationStock::getProductStock, BigDecimal.ZERO)
                .orderByAsc(LocationStock::getId));
        BigDecimal remaining = quantity;
        LocalDate today = LocalDate.now();
        Date now = new Date();
        for (LocationStock basket : baskets) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal take = basket.getProductStock().min(remaining);
            int affected = locationStockMapper.deductStockById(basket.getId(), take, userId);
            if (affected == 0) {
                // 并发抢占：该篮已被他人领走，跳过（不计入已领）
                continue;
            }
            ProductInhouse inhouse = new ProductInhouse();
            inhouse.setProduceDate(java.sql.Date.valueOf(today));
            inhouse.setProduceTime(now);
            inhouse.setProductId(productId);
            inhouse.setProductName(product.getProductName());
            inhouse.setProductType(product.getProductType() == null ? 1 : product.getProductType());
            inhouse.setProductUnit(product.getProductUnit());
            inhouse.setProductWeight(take);
            inhouse.setEarNo(basket.getEarNo());     // 篮子标签 = 猪只耳号 → 打包追溯键
            inhouse.setLocationId(locId);
            inhouse.setMaterialId(productId);         // 原材料 = 自身（成品打包侧经 product_material 反查共享池）
            inhouse.setMaterialConsume(take);
            productInhouseMapper.insert(inhouse);
            remaining = remaining.subtract(take);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new ServiceException(
                "库存不足：" + product.getProductName() + " 可领 "
                    + quantity.subtract(remaining).stripTrailingZeros().toPlainString()
                    + product.getProductUnit() + "，申请 "
                    + quantity.stripTrailingZeros().toPlainString() + product.getProductUnit());
        }
    }

    /**
     * 是否「自产果蔬原料」（type=1 自产 + belong_type=vegetable + attr=2 原料）。
     *
     * <p>仅此类走「地块篮子」FIFO 领用（毛菜处理入冷库时已按 plot 建篮）；外购果蔬(type=2)/包材/果蔬成品
     * 走 product 维度旧路径。type 为 null 视为自产（历史 seed 未显式置 type=1 的果蔬原料兼容）。</p>
     */
    private boolean isVegSelfMaterial(ProductInfo product) {
        return "vegetable".equals(product.getBelongType())
            && product.getProductAttr() != null && product.getProductAttr() == 2
            && (product.getProductType() == null || product.getProductType() == 1);
    }

    /**
     * 自产果蔬原料按「{@code product_id} 篮 FIFO」领用（doc/14 §1/§3，对齐 {@link #consumePorkBaskets}）。
     *
     * <p><b>统一目标模型（2026-06-20）下自产果蔬的唯一主路径</b>：两条入库路径（直接入库 / 月台中转）入的都是
     * {@code product_id+plot_id 双键篮}，本方法按 {@code product_id} 维度（不按 plot 过滤）FIFO 逐篮扣，
     * {@code plot_id} 只作篮标签随篮带到 {@code product_inhouse}（地块追溯）。这样月台中转篮（曾只带 plot_id、
     * product_id=NULL 的旧单键篮已由入库侧补 product_id 统一为双键篮）与直接入库篮汇入同一池，两池合一（G2/G3）。</p>
     *
     * <p>毛菜处理入冷库时每次入库 = 一篮 {@code location_stock}（带 plot_id 标签）。据门店需求领用某果蔬原料
     * N kg 时，按 id 升序（先进先出）逐篮扣减 location_stock，每扣一篮产一行 {@code product_inhouse}
     * （带该篮 plot_id、{@code produce_date=今天}）→ 果蔬打包来源。地块随篮子自动带到打包（右台显「对应地块」）；
     * 申请量可跨多地块篮子拼够。历史无 plot 的 product 维度行（plot_id=null）也一并 FIFO 消耗（inhouse plot 为 null
     * → 打包页显「无地块信息」，与旧数据兼容）。统一模型下此 plot 标签语义即正确（G7），无需改动。</p>
     *
     * @throws ServiceException 篮子总量不足申请量（{@code @Transactional} 回滚领用流水）
     */
    private void consumeVegBaskets(Long productId, BigDecimal quantity, Long locId, ProductInfo product, Long userId) {
        List<LocationStock> baskets = locationStockMapper.selectList(
            new LambdaQueryWrapper<LocationStock>()
                .eq(LocationStock::getProductId, productId)
                .eq(LocationStock::getLocationId, locId)
                .gt(LocationStock::getProductStock, BigDecimal.ZERO)
                .orderByAsc(LocationStock::getId));
        BigDecimal remaining = quantity;
        LocalDate today = LocalDate.now();
        Date now = new Date();
        for (LocationStock basket : baskets) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal take = basket.getProductStock().min(remaining);
            int affected = locationStockMapper.deductStockById(basket.getId(), take, userId);
            if (affected == 0) {
                // 并发抢占：该篮已被他人领走，跳过（不计入已领）
                continue;
            }
            ProductInhouse inhouse = new ProductInhouse();
            inhouse.setProduceDate(java.sql.Date.valueOf(today));
            inhouse.setProduceTime(now);
            inhouse.setProductId(productId);
            inhouse.setProductName(product.getProductName());
            inhouse.setProductType(product.getProductType() == null ? 1 : product.getProductType());
            inhouse.setProductUnit(product.getProductUnit());
            inhouse.setProductWeight(take);
            inhouse.setPlotId(basket.getPlotId());   // 篮子标签 = 地块 → 打包追溯键（历史 product 维度篮为 null）
            inhouse.setLocationId(locId);
            inhouse.setMaterialId(productId);          // 原材料 = 自身（成品打包侧经 product_material 反查共享池）
            inhouse.setMaterialConsume(take);
            productInhouseMapper.insert(inhouse);
            remaining = remaining.subtract(take);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new ServiceException(
                "库存不足：" + product.getProductName() + " 可领 "
                    + quantity.subtract(remaining).stripTrailingZeros().toPlainString()
                    + product.getProductUnit() + "，申请 "
                    + quantity.stripTrailingZeros().toPlainString() + product.getProductUnit());
        }
    }

    /**
     * 自产果蔬「按地块维度」领用 —— <b>LEGACY 兼容路径</b>（统一模型 2026-06-20 后非自产果蔬主路径）。
     *
     * <p>统一目标模型下自产果蔬主路径 = {@code pickByProduct} 经 {@link #isVegSelfMaterial} 判定后走
     * {@link #consumeVegBaskets}（product_id 维度 FIFO，篮带 plot_id 标签到 inhouse）。两条入库路径
     * （直接入库 / 月台中转）都入 {@code product_id+plot_id 双键篮}，故 product 维度领用足够覆盖。
     * 本方法仅在 mp 显式传 {@code plotId}（旧地块维度入口、无 product_id）时兜底命中，按 plot 维度扣账，
     * 保留与历史 plot-only 库存行兼容；新代码不应再走此入口。</p>
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

        // 3. 桥接果蔬打包来源（带 plot_id，自产果蔬地块维度）：写一条 product_inhouse，admin 果蔬打包来源读它
        bridgeMaterialInhouse(resolvedProductId, bo.getQuantity(), plotId, locId);

        return flow.getId();
    }

    /**
     * 可打包食品原料桥接 inhouse（统一目标模型 2026-06-20，泛化自旧 {@code bridgeVegInhouse}）：物资领用
     * 「可打包食品原料」成功后写一条 {@code t_warehouse_product_inhouse}（待打包 WIP），让 admin 打包来源
     * （{@code listSourceForVeg} / {@code listSourceForDry}，按 {@code produce_date=今天} 过滤）显示「当天领用的原料」。
     * 对齐猪肉分割 {@code PigCutRecordServiceImpl} 产 inhouse 的范式（product 维度，plot_id 选填冗余、非过滤主键）。
     *
     * <p>门槛（统一模型）：{@code product_attr=2}（原材料，字典 djs_product_attr）且
     * {@code belong_type ∈ {vegetable, egg, dry_good, other}}（可打包食品原料）才桥接——pork / white_bar 走各自
     * 篮子路径（ear_no）已产 inhouse、不经此；package / feed / seed / medicine = 物资不产 inhouse；产品（attr=1）
     * 不写（避免成品自身当来源的循环）。product 不存在 / 非可打包食品原料 → 跳过（不阻断领用，stock_flow 已写）。
     * 同事务，随 pick 一起提交 / 回滚。</p>
     */
    private void bridgeMaterialInhouse(Long productId, BigDecimal weight, Long plotId, Long locId) {
        if (productId == null || weight == null || weight.signum() <= 0) {
            return;
        }
        ProductInfo product = productInfoMapper.selectById(productId);
        if (product == null
            || product.getProductAttr() == null
            || product.getProductAttr() != 2
            || !PACKABLE_FOOD_BELONG_TYPES.contains(product.getBelongType())) {
            return;
        }
        ProductInhouse inhouse = new ProductInhouse();
        inhouse.setProduceDate(java.sql.Date.valueOf(LocalDate.now()));
        inhouse.setProduceTime(new Date());
        inhouse.setProductId(productId);
        inhouse.setProductName(product.getProductName());
        inhouse.setProductType(product.getProductType() == null ? 1 : product.getProductType());
        inhouse.setProductUnit(product.getProductUnit());
        inhouse.setProductWeight(weight);
        inhouse.setPlotId(plotId);          // product 维度领用为 null；自产果蔬带 plot
        inhouse.setLocationId(locId);
        inhouse.setMaterialId(productId);    // 冗余追溯：领用的原材料 = 自身
        inhouse.setMaterialConsume(weight);
        // source 走 DDL DEFAULT 'warehouse'；ear_no/white_bar_id/cut_part NULL（非猪肉链）
        productInhouseMapper.insert(inhouse);
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
        // 「按源手选」退回（batchId 非空）：把退回量回补到用户选中的那一篮（猪肉耳号篮 / 自产果蔬地块篮）。
        if (bo.getBatchId() != null) {
            return returnByBatch(bo);
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

        // 3. 退回 = 减「今天待打包」product_inhouse（让打包页不再显示已退量）+ 回补冷库 location_stock（doc/14 §6）。
        returnToStock(product, bo.getQuantity(), locId, userId);

        return flow.getId();
    }

    /**
     * 「按源手选」退回（{@code batchId} 非空）：把退回量回补到用户选中的那一篮（猪肉耳号篮 / 自产果蔬地块篮）。
     *
     * <p>四步同事务：</p>
     * <ol>
     *   <li>查选中篮 {@code location_stock[id=batchId]}（防御：未软删；product 主数据取名称 / 单位）；</li>
     *   <li>校验今日额度（仍按 {@code (user, product)} 统计，与现状一致）：已领 ≥ 已退 + 已损 + 当次量；</li>
     *   <li>INSERT {@code return_in} 流水（带篮的 {@code product_id} / {@code plot_id} / {@code ear_no} 源标签）；</li>
     *   <li>{@code addStockById(batchId, qty)} 回补选中篮 + LIFO 减今天该篮 {@code product_inhouse}
     *       （按 {@code product_id + ear_no/plot_id} 匹配，复用 {@link #reduceTodayInhouseForBasket}）。</li>
     * </ol>
     */
    private Long returnByBatch(MatReturnBo bo) {
        LocationStock basket = locationStockMapper.selectById(bo.getBatchId());
        if (basket == null || !"0".equals(basket.getDelFlag())) {
            throw new ServiceException("选中的篮子不存在或已删除（batchId=" + bo.getBatchId() + "）");
        }
        Long locId = basket.getLocationId();
        // 库位级业务锁（WMS-STOCK-001）：盘点进行中的库位禁出入库（后端双保险）
        stockCheckService.assertLocationUnlocked(locId);
        Long userId = LoginHelper.getUserId();

        Long productId = basket.getProductId();
        ProductInfo product = productId == null ? null : productInfoMapper.selectById(productId);
        String productName = product != null ? product.getProductName() : basket.getProductName();
        String productUnit = product != null ? product.getProductUnit() : basket.getProductUnit();

        // 1. 校验今日额度（仍按 user+product 统计，与现状 ensureTodayCapacity 一致）；productId 缺失（理论不该
        //    出现，源篮子均带 product_id）→ 跳过额度校验，仅回补（不阻塞）
        if (productId != null) {
            ensureTodayCapacity(userId, productId, bo.getQuantity(), productName, productUnit);
        }

        // 2. INSERT stock_flow（return_in 入库，带篮的 product_id + plot_id + ear_no 源标签）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_IN));
        flow.setFlowDate(new Date());
        flow.setProductId(productId);
        flow.setPlotId(basket.getPlotId());
        flow.setEarNo(basket.getEarNo());
        flow.setWarehouseId(locId);
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(FLOW_RETURN_IN);
        flow.setChangeNum(bo.getQuantity());
        flow.setChangeQuantity(bo.getQuantity());
        flow.setOperatorId(userId);
        flow.setRemark(bo.getRemark());
        flow.setProofOssIds(bo.getProofOssIds());
        stockFlowMapper.insert(flow);

        // 3. 回补选中篮 + 减今天该篮的待打包 inhouse（让打包页不再显示已退量）
        locationStockMapper.addStockById(basket.getId(), bo.getQuantity(), userId);
        reduceTodayInhouseForBasket(productId, basket.getEarNo(), basket.getPlotId(), bo.getQuantity());

        return flow.getId();
    }

    /**
     * 「按源手选」退回时减今天该篮的待打包 {@code product_inhouse}（LIFO 退最近领用的）。
     *
     * <p>按 {@code product_id + ear_no/plot_id} 匹配该篮今天产生的 inhouse 行（领用 {@code pickByBatch}
     * 写的源标签），倒序逐行扣减待打包重量。退回量超今天待打包量（差额已打包）→ 打 warn 不阻断（库存已
     * 回补、流水已记）。inhouse 匹配键与篮子标签一致：猪肉按 ear_no、自产果蔬按 plot_id。</p>
     */
    private void reduceTodayInhouseForBasket(Long productId, String earNo, Long plotId, BigDecimal quantity) {
        if (productId == null) {
            return;
        }
        LambdaQueryWrapper<ProductInhouse> w = new LambdaQueryWrapper<ProductInhouse>()
            .eq(ProductInhouse::getProductId, productId)
            .gt(ProductInhouse::getProductWeight, BigDecimal.ZERO)
            .apply("DATE(produce_date) = CURDATE()");
        if (earNo != null) {
            w.eq(ProductInhouse::getEarNo, earNo);
        } else if (plotId != null) {
            w.eq(ProductInhouse::getPlotId, plotId);
        }
        w.orderByDesc(ProductInhouse::getId);
        List<ProductInhouse> wips = productInhouseMapper.selectList(w);
        BigDecimal remaining = quantity;
        for (ProductInhouse wip : wips) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal back = wip.getProductWeight().min(remaining);
            if (productInhouseMapper.deductWeightById(wip.getId(), back) == 0) {
                // 被并发占用 / 已扣空 → 跳过该行
                continue;
            }
            remaining = remaining.subtract(back);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("按篮退回量超今天待打包量：productId={} earNo={} plotId={} 申请退 {} 实退 {}（差额已打包）",
                productId, earNo, plotId, quantity, quantity.subtract(remaining));
        }
    }

    /**
     * 退回回补（doc/14 §6）：减「今天待打包」{@code product_inhouse}（让打包页不再显示已退量）+ 回补冷库
     * {@code location_stock}。
     *
     * <p>按产品的今天活动 inhouse 倒序（LIFO，退最近领用的）逐行扣减待打包重量，扣多少回补多少到冷库：
     * 猪肉（{@code ear_no} 非空）按篮子（{@code product_id+ear_no+location} 匹配原篮）回补、找不到则 product
     * 维度兜底；果蔬 / 包材按 product 维度回补。退回量超今天待打包量（差额已打包、无可退）→ 流水已记，打 warn 不阻断。</p>
     */
    private void returnToStock(ProductInfo product, BigDecimal quantity, Long fallbackLocId, Long userId) {
        // 篮子型库存（猪肉 ear_no 篮 / 自产果蔬 plot_id 篮）：退回时减待打包 inhouse + 按篮回补原冷库篮。
        boolean isPork = "pork".equals(product.getBelongType());
        boolean isBasket = isPork || isVegSelfMaterial(product);
        // (a) 减「今天待打包」inhouse（让打包页不再显示已退量），LIFO 退最近领用的。
        //     篮子型按原篮（product_id + ear_no/plot_id + location 匹配）回补对应冷库篮；其余的冷库回补在 (b) 统一做。
        List<ProductInhouse> wips = productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .eq(ProductInhouse::getProductId, product.getId())
                .gt(ProductInhouse::getProductWeight, BigDecimal.ZERO)
                .apply("DATE(produce_date) = CURDATE()")
                .orderByDesc(ProductInhouse::getId));
        BigDecimal remaining = quantity;
        for (ProductInhouse wip : wips) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal back = wip.getProductWeight().min(remaining);
            // 减待打包 inhouse（行锁，余量足才扣；被并发占用则跳过该行）
            if (productInhouseMapper.deductWeightById(wip.getId(), back) == 0) {
                continue;
            }
            if (isBasket) {
                Long locId = wip.getLocationId() != null ? wip.getLocationId() : fallbackLocId;
                LambdaQueryWrapper<LocationStock> bw = new LambdaQueryWrapper<LocationStock>()
                    .eq(LocationStock::getProductId, product.getId())
                    .eq(LocationStock::getLocationId, locId);
                if (isPork) {
                    bw.eq(LocationStock::getEarNo, wip.getEarNo());
                } else if (wip.getPlotId() != null) {
                    bw.eq(LocationStock::getPlotId, wip.getPlotId());   // 自产果蔬按地块篮回补
                } else {
                    bw.isNull(LocationStock::getPlotId);                // 历史无地块 inhouse → product 维度篮
                }
                LocationStock basket = locationStockMapper.selectOne(bw.last("LIMIT 1"));
                if (basket != null) {
                    locationStockMapper.addStockById(basket.getId(), back, userId);
                } else {
                    locationStockMapper.addByProductLocation(locId, product.getId(), back, userId);
                }
            }
            remaining = remaining.subtract(back);
        }
        if (isBasket) {
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                // 退回量超今天待打包量（差额已打包、无待打包可减）——流水已记录，打 warn 不阻断
                log.warn("退回量超今天待打包量：product={} 申请退 {} 实退 {}（差额已打包）",
                    product.getProductName(), quantity, quantity.subtract(remaining));
            }
            return;
        }
        // (b) 非篮子型：product 维度把全部退回量加回冷库（package/外购 veg 无 inhouse，仅此一步；
        //     与原 returnBack 行为一致：命中 0 行 → 抛，防御库位/产品已删）。
        int affected = locationStockMapper.addByProductLocation(fallbackLocId, product.getId(), quantity, userId);
        if (affected == 0) {
            throw new ServiceException(
                "库存记录不存在，无法退回（product=" + product.getProductName()
                    + " / location=" + fallbackLocId + "）");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long loss(MatLossBo bo) {
        // 「按源手选」损耗（batchId 非空）：从用户选中的那一篮（猪肉耳号篮 / 自产果蔬地块篮）扣损耗量。
        if (bo.getBatchId() != null) {
            return lossByBatch(bo);
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

    /**
     * 「按源手选」损耗（{@code batchId} 非空）：从用户选中的那一篮（猪肉耳号篮 / 自产果蔬地块篮）扣损耗量。
     *
     * <p>三步同事务：</p>
     * <ol>
     *   <li>查选中篮 {@code location_stock[id=batchId]}（防御：未软删；product 主数据取名称 / 单位）；</li>
     *   <li>校验今日额度（仍按 {@code (user, product)} 统计，与现状一致）；</li>
     *   <li>INSERT {@code loss} 流水（带篮源标签）+ {@code deductStockById(batchId, qty)} 扣选中篮——
     *       与现状 loss 语义一致，{@code affected==0} 打 warn 不抛（损耗 = 不可逆消耗，账实倒挂留痕审计）。</li>
     * </ol>
     */
    private Long lossByBatch(MatLossBo bo) {
        LocationStock basket = locationStockMapper.selectById(bo.getBatchId());
        if (basket == null || !"0".equals(basket.getDelFlag())) {
            throw new ServiceException("选中的篮子不存在或已删除（batchId=" + bo.getBatchId() + "）");
        }
        Long locId = basket.getLocationId();
        // 库位级业务锁（WMS-STOCK-001）：盘点进行中的库位禁出入库（后端双保险）
        stockCheckService.assertLocationUnlocked(locId);
        Long userId = LoginHelper.getUserId();

        Long productId = basket.getProductId();
        ProductInfo product = productId == null ? null : productInfoMapper.selectById(productId);
        String productName = product != null ? product.getProductName() : basket.getProductName();
        String productUnit = product != null ? product.getProductUnit() : basket.getProductUnit();

        // 1. 校验今日额度（仍按 user+product 统计，与现状一致）；productId 缺失 → 跳过（不阻塞）
        if (productId != null) {
            ensureTodayCapacity(userId, productId, bo.getQuantity(), productName, productUnit);
        }

        // 2. INSERT stock_flow（loss 出库，带篮的 product_id + plot_id + ear_no 源标签）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_OUT));
        flow.setFlowDate(new Date());
        flow.setProductId(productId);
        flow.setPlotId(basket.getPlotId());
        flow.setEarNo(basket.getEarNo());
        flow.setWarehouseId(locId);
        flow.setInoutType(INOUT_OUT);
        flow.setFlowType(FLOW_LOSS);
        flow.setChangeNum(bo.getQuantity().negate());
        flow.setChangeQuantity(bo.getQuantity());
        flow.setOperatorId(userId);
        flow.setRemark(bo.getRemark());
        flow.setProofOssIds(bo.getProofOssIds());
        stockFlowMapper.insert(flow);

        // 3. 扣选中篮（与现状 loss 语义一致：affected==0 打 warn 不抛，账实倒挂留痕）
        int affected = locationStockMapper.deductStockById(basket.getId(), bo.getQuantity(), userId);
        if (affected == 0) {
            log.warn("按篮 loss 流水已记，但选中篮扣减失败（账面已不足）：user={}, batchId={}, qty={}",
                userId, basket.getId(), bo.getQuantity());
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

    @Override
    public List<MatWhiteBarBatchVo> issueWhiteBarBatches(String belongType, String locationId) {
        // 权威源 = bar_info status='in_stock'（一行 = 一条实物白条整只），不走 location_stock（白条 SKU 无库存行）。
        // belongType / locationId 仅为与 issueItems / selfVegIssueItems 端点签名对称接收，bar_info 不据此过滤。
        List<TodayBarVo> bars = barInfoMapper.selectInStockBars();
        List<MatWhiteBarBatchVo> result = new ArrayList<>();
        if (bars == null || bars.isEmpty()) {
            return result;
        }
        for (TodayBarVo bar : bars) {
            MatWhiteBarBatchVo vo = new MatWhiteBarBatchVo();
            // batchId：白条主键转 String 防 jackson Long 截断（snowflake 19 位）。
            vo.setBatchId(bar.getId() == null ? null : String.valueOf(bar.getId()));
            // batchCode：业务码 bar_id 优先，为空回退 ear_no（外购白条可能无 bar_id）。
            String code = bar.getBarId();
            if (code == null || code.isBlank()) {
                code = bar.getEarNo();
            }
            vo.setBatchCode(code);
            // bar_info 无 product_id FK：回填「白条·整只」canonical SKU，给 mp 领用 BO 一个 productId。
            vo.setProductId(WHITE_BAR_WHOLE_PRODUCT_ID);
            vo.setProductName(WHITE_BAR_WHOLE_PRODUCT_NAME);
            vo.setProductUnit(WHITE_BAR_WHOLE_PRODUCT_UNIT);
            vo.setInboundTime(formatInboundTime(bar.getInTime()));
            // in_stock 白条尚未绑定门店（门店在领用时选）→ storeName 恒 null。
            vo.setStoreName(null);
            // 预冷时长按 in_time 实时计算（acid_remove_time 在 cut_done 前恒 NULL，不读）。
            vo.setPrecoolDuration(computePrecoolDuration(bar.getInTime()));
            vo.setInboundWeight(bar.getInWeight());
            // 本端点只返 in_stock 行 → 恒可领。
            vo.setBatchStatus("pickable");
            // 白条 SKU 无库存行 / 库位绑定 → 恒 null。
            vo.setLocationId(null);
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<MatIssueBasketVo> issuePorkBatches(String productId, String locationId) {
        Long pid = requireProductId(productId);
        Long locId = parseLocationId(locationId);
        return locationStockMapper.selectPorkIssueByEar(pid, locId);
    }

    @Override
    public List<MatIssueBasketVo> issueVegBatches(String productId, String locationId) {
        Long pid = requireProductId(productId);
        Long locId = parseLocationId(locationId);
        return locationStockMapper.selectVegIssueByPlot(pid, locId);
    }

    /**
     * 产品 ID 字符串 → Long（snowflake 防截断；必填，为空 / 非法抛 ServiceException）。
     */
    private static Long requireProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new ServiceException("产品 ID 不能为空");
        }
        try {
            return Long.valueOf(productId.trim());
        }
        catch (NumberFormatException e) {
            throw new ServiceException("产品 ID 非法：" + productId);
        }
    }

    /**
     * 入库时间格式化为 {@code MM-dd HH:mm}（mp 白条批次卡）；为空 → 空串。
     */
    private static String formatInboundTime(Date inTime) {
        if (inTime == null) {
            return "";
        }
        return INBOUND_TIME_FORMATTER.format(inTime.toInstant().atZone(ZoneId.systemDefault()));
    }

    /**
     * 预冷时长 = {@code now - in_time} 向下取整到分钟，文案 {@code {h}小时{m}分钟}。
     *
     * <p>实时计算（不读 acid_remove_time，该字段在 cut_done 前恒 NULL）；in_time 为空 → 空串。
     * 负值（in_time 在未来，理论不应出现）按 0 处理。</p>
     */
    private static String computePrecoolDuration(Date inTime) {
        if (inTime == null) {
            return "";
        }
        long totalMinutes = Duration.between(inTime.toInstant(), Instant.now()).toMinutes();
        if (totalMinutes < 0) {
            totalMinutes = 0;
        }
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours + "小时" + minutes + "分钟";
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
