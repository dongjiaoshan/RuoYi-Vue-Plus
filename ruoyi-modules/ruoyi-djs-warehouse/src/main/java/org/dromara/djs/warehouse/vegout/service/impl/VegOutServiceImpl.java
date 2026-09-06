package org.dromara.djs.warehouse.vegout.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.common.core.service.UserService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.domain.bo.StockOutBo;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.stock.service.ILocationStockService;
import org.dromara.djs.warehouse.veg.domain.FeedLog;
import org.dromara.djs.warehouse.veg.domain.VegetableHandle;
import org.dromara.djs.warehouse.veg.domain.HandleRecord;
import org.dromara.djs.warehouse.veg.mapper.FeedLogMapper;
import org.dromara.djs.warehouse.veg.mapper.HandleRecordMapper;
import org.dromara.djs.warehouse.veg.mapper.VegetableHandleMapper;
import org.dromara.djs.warehouse.vegout.domain.bo.VegOutItemBo;
import org.dromara.djs.warehouse.vegout.domain.bo.VegOutSubmitBo;
import org.dromara.djs.warehouse.vegout.domain.query.VegOutQuery;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutBatchVo;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutCandidateVo;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutDetailVo;
import org.dromara.djs.warehouse.vegout.mapper.VegOutMapper;
import org.dromara.djs.warehouse.vegout.service.IVegOutService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 毛菜间出库 Service 实现（admin row185 + row187）。
 *
 * <p><b>货从哪来</b>：候选 = {@link #ALLOWED_LOCATION_CODES} 五个库位里 {@code product_attr=2}
 * （原材料）的库存篮，一行一个篮、不按产品合并（出库按 {@code stockId} 逐篮扣）。主力仍是毛菜处理间
 * 「入库」去向写进<b>毛菜鲜品库 L0006</b> 的那批毛菜
 * （{@code VegetableHandleServiceImpl.LOCATION_CODE_FRESH_VEG} 硬编码，不由工人选）；
 * 干货 / 蛋类 / 猪肉部位篮同样可从这里卖出去。范围为什么就是这五个库，见
 * {@link #ALLOWED_LOCATION_CODES} 的注释。</p>
 *
 * <p><b>去哪</b>（甲方 row185 col8 口径）：出库到果蔬月台 = 从毛菜间运蔬到果蔬月台，
 * 对毛菜鲜品库是一次出库；出库后货显示在 mp「果蔬月台」功能里，工人在月台收货后再进蔬菜保鲜库。
 * 月台待入库量口径 = {@code Σ handle_record(record_type=2, handle_target=2) − 已收货 − 已完成损耗}
 * （见 {@code VegReceiveMapper.selectSelfPending}，按产品拆所以只能读明细），
 * 故月台去向<b>必须写一条 handle_record</b>；不写这条货就在月台永远收不了。</p>
 *
 * <p><b>本 service 出的货<u>不</u>回写 {@code vegetable_handle.handled_weight}（甲方 2026-08-19 定）。</b></p>
 * <p>甲方原话：「果蔬处理重量」指的是<b>毛菜处理录入</b>那两个去向出去的量，而
 * 「loss 应该是 地块入库量 − 果蔬月台 − 有机饲喂 − 出库」—— 本 service 就是那个单列出来的「出库」，
 * 它与月台、饲喂并列，<b>不属于</b>果蔬处理重量。甲方同时点明「这里会有其他地方从毛菜间出库」。</p>
 * <ul>
 *   <li>本 service 扣的是 L0006 的实物库存，收口时 {@code settleRemainAsLoss} 按<b>剩余库存</b>结转损耗，
 *       所以这批 kg 自动从损耗里减掉了 —— 恒等式 {@code 入库 = 月台 + 饲喂 + 出库 + 损耗} 成立，
 *       且天然覆盖甲方说的「其他地方」的出库，不用逐个去列。</li>
 *   <li>反过来若回写 {@code handled_weight}，那批货既进「果蔬处理重量」又已从库存扣掉，
 *       该记录的 {@code picked = handled + loss} 当场不成立（实测出现过 mp 地块卡一行显示
 *       「采摘 20 / 处理 15 / 剩余 20」）。</li>
 * </ul>
 * <p>🔴 <b>不要再把回写加回来。</b>判据：这条出库路径在甲方的损耗公式里是被<b>单独列出</b>的一项，
 * 与「果蔬月台」「有机饲喂」并列 —— 并列项不能同时算进其中一项。
 * （commit {@code c572cd241} 当年以「已入库库存被二次扣减」为由去掉回写，理由虽已过时，结论一致。）</p>
 *
 * <p>本 service 只写<b>流水与台账明细</b>，一个汇总列都不碰
 * （{@code handled_weight} / {@code send_platform_weight} / {@code feed_weight} 全不写）：
 * 月台待入库、运输损耗、日统计、有机饲喂记录读的都是这里写的 {@code handle_record} / {@code feed_log} 明细，
 * 汇总列再加一次就是双重计数。</p>
 *
 * <p>{@link #resolveHandleId} 只服务于一件事：把月台明细挂到对的毛菜处理汇总行上
 * （① 篮子带 {@code source_biz_id} → 精确定位；② 定得出 {@code (作物, 地块)} → 按该组合定位、必要时补建；
 * 干货 / 蛋类反查不到作物、退货篮没地块 → 定不到，本表本就没有它们的汇总行）。</p>
 *
 * @author djs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VegOutServiceImpl implements IVegOutService {

    /** 毛菜鲜品库库位编码（与 VegetableHandleServiceImpl 同一常量，毛菜处理入库的固定落点）。 */
    private static final String LOCATION_CODE_FRESH_VEG = "L0006";

    /** 干货库库位编码（admin row194 第 4 点起也可从这里出库）。 */
    private static final String LOCATION_CODE_DRY_GOODS = "L0005";

    /** 蛋类库库位编码（同上）。 */
    private static final String LOCATION_CODE_EGG = "L0009";

    /** 猪肉鲜品库库位编码：分割间按「部位 × 耳号」建的篮子落在这里，猪肉 tab 的货源。 */
    private static final String LOCATION_CODE_PORK_FRESH = "L0007";

    /** 红白脏库库位编码：猪下水，业态同为 pork，与鲜品库一起构成猪肉 tab。 */
    private static final String LOCATION_CODE_OFFAL = "L0018";

    /**
     * 可出库的库位白名单。
     *
     * <p>⚠️ 按 {@code location_code} 而不是 {@code location_type} —— 线上 location_type 不可靠
     * （猪肉鲜品库/蛋类库被错归成 veg_fresh、干货库是 warehouse），mp matPack 早就因此改走库名匹配。</p>
     *
     * <p><b>为什么只放这五个</b>：这个窗口是「把农场的可售农产品直接卖出去」，所以库位既要有货、
     * 又不能是别的链路的中转池。被排除的都有各自的去处：</p>
     * <ul>
     *   <li>L0003 蔬菜保鲜库 / L0004 重口味蔬菜库 —— 有机链路月台收货的落点，正是
     *       果蔬打包 → 发货月台 → 门店的<b>供货池</b>；从这里卖掉，门店的货就凭空少了。</li>
     *   <li>L0001 白条库 —— 白条有独立的燎毛 / 分割链路（按半只、按耳号走），不按 kg 散卖。</li>
     *   <li>L0008 包材库 / L0010 种子库 / L0011 肥料库 / L0012 生物农药库 / L0013-L0017 —— 生产投入品，
     *       不是可售农产品。它们同样是 {@code product_attr=2}，所以<b>光靠原材料过滤挡不住</b>，
     *       只有库位白名单能把它们排除。</li>
     * </ul>
     */
    private static final java.util.List<String> ALLOWED_LOCATION_CODES = java.util.List.of(
        LOCATION_CODE_FRESH_VEG, LOCATION_CODE_DRY_GOODS, LOCATION_CODE_EGG,
        LOCATION_CODE_PORK_FRESH, LOCATION_CODE_OFFAL);

    /**
     * 可出库的产品业态白名单（干货库实测还有 other 业态的桶/罐/袋装品）。
     *
     * <p>前端按此分三个 tab：果蔬 = {@code vegetable}；猪肉 = {@code pork}；其他 = 剩下的全部业态。</p>
     */
    private static final java.util.List<String> ALLOWED_BELONG_TYPES =
        java.util.List.of("vegetable", "pork", "dry_good", "egg", "other");

    /** 果蔬业态（只有果蔬产品能出到果蔬月台）。 */
    private static final String BELONG_TYPE_VEGETABLE = "vegetable";

    /** 三期标识值（{@code location_stock.third_phase}）。 */
    private static final int THIRD_PHASE_YES = 1;

    /** 出库去向：果蔬月台（本次迁移新增字典值）。 */
    private static final String DEST_VEG_DOCK = "veg_dock";

    /** 出库去向：猪只饲料（前端「饲料饲喂」，字典既有值）。 */
    private static final String DEST_FEED = "feed";

    /** 饲喂来源：毛菜间（字典 djs_feed_type，有机饲喂记录里的「位置」）。 */
    private static final String FEED_TYPE_VEG_HANDLE = "veg_handle";

    /** 饲喂来源：仓库（字典 djs_feed_type 的另一个值）。非毛菜鲜品库出的货走这个。 */
    private static final String FEED_TYPE_WAREHOUSE = "warehouse";

    /** 处理明细类型：处理录入（{@code t_warehouse_handle_record.record_type}）。 */
    private static final int RECORD_TYPE_HANDLE = 2;

    /** 处理去向：月台（{@code handle_target}；日统计「发往月台果蔬总重」按此筛）。 */
    private static final int HANDLE_TARGET_PLATFORM = 2;

    private final VegOutMapper vegOutMapper;
    private final LocationStockMapper locationStockMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final ProductInfoMapper productInfoMapper;
    private final StockFlowMapper stockFlowMapper;
    private final CropInfoMapper cropInfoMapper;
    private final VegetableHandleMapper vegetableHandleMapper;
    private final FeedLogMapper feedLogMapper;
    private final HandleRecordMapper handleRecordMapper;
    private final ILocationStockService locationStockService;
    private final org.dromara.djs.common.encoder.IBizCodeGenerator bizCodeGenerator;
    private final UserService userService;

    /**
     * 一张出库单最多几个<b>产品</b> —— 与 admin 打印模板 {@code printSheet.ts} 的 `ROWS_PER_PAGE` 同一个数。
     * 改这里必须同步改那边，否则要么单据分页、要么表格印不下。
     *
     * <p>数的是产品不是明细条数（V6 row108）：同一产品的多个地块篮在打印单上合并成一行，
     * 12 条明细若只对应 8 个产品，单子就是 8 行、一页印得下。</p>
     */
    private static final int MAX_PRODUCTS_PER_SHEET = 10;

    @Override
    public List<VegOutCandidateVo> listCandidates(String productName) {
        return vegOutMapper.selectCandidates(ALLOWED_LOCATION_CODES, ALLOWED_BELONG_TYPES, productName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String submit(VegOutSubmitBo bo, boolean asBatch) {
        // 可出库库位的 id（按 location_code 解析；缺哪个就少哪个，不阻断整单）
        java.util.Set<Long> allowedLocationIds = resolveAllowedLocationIds();
        // 先把本单涉及的库存行取齐：产品数要在扣任何库存之前数得出来
        Map<Long, LocationStock> stocks = loadStocks(bo.getItems());
        // 一单最多 10 个产品（甲方 V6 row26）：241×140mm 的三联单一页只印得下 10 行，超了就得打两张纸。
        // 前端抽屉已经把输入框禁掉了，这里是第二道门 —— 直接打接口 / 前端被绕过时不能照单全收。
        // ⚠️ 数**产品**不数明细条数（V6 row108）：同一产品的不同地块篮各是一条明细，但打印单上合成一行，
        // 按条数拦会把「8 个产品 12 个篮」这种正常单子误拦在门外。
        long productCount = stocks.values().stream()
            .map(LocationStock::getProductId)
            .filter(Objects::nonNull)
            .distinct()
            .count();
        if (productCount > MAX_PRODUCTS_PER_SHEET) {
            throw new ServiceException("一张出库单最多 " + MAX_PRODUCTS_PER_SHEET + " 个产品，当前 "
                + productCount + " 个", 400);
        }
        String batchNo = asBatch ? generateBatchNo() : null;
        Long userId = LoginHelper.getUserId();

        for (VegOutItemBo item : bo.getItems()) {
            LocationStock stock = stocks.get(item.getStockId());
            // 前置校验（防前端绕过 —— 候选列表只列白名单库位里的原材料篮）
            if (!allowedLocationIds.contains(stock.getLocationId())) {
                throw new ServiceException("只有毛菜鲜品库 / 干货库 / 蛋类库 / 猪肉鲜品库 / 红白脏库的库存可做毛菜间出库");
            }
            ProductInfo product = productInfoMapper.selectById(stock.getProductId());
            if (product == null) {
                throw new ServiceException("产品不存在或已删除：" + stock.getProductId());
            }
            if (!ALLOWED_BELONG_TYPES.contains(product.getBelongType())) {
                throw new ServiceException("该产品业态不支持毛菜间出库：" + product.getProductName());
            }
            // ⚠️ 非果蔬不能送「果蔬月台」：月台的待入库量来自 handle_record(handle_target=2)，
            // 而那条明细必须挂在 vegetable_handle 上，那张表是果蔬（毛菜处理）专属、
            // 干货/蛋类根本定位不到 (作物,地块) 也就建不出归集行。放行的话库存扣了、流水写了，
            // 月台侧却永远收不到这批货 = 凭空蒸发。故在此 fail-fast，不做「跳过」的静默放行。
            // （「猪只饲料」去向不受此限：饲喂台账 t_warehouse_feed_log 三类都能记，见下方分流。）
            if (DEST_VEG_DOCK.equals(bo.getOutDest())
                && !BELONG_TYPE_VEGETABLE.equals(product.getBelongType())) {
                throw new ServiceException("只有果蔬产品可以出库到果蔬月台：" + product.getProductName()
                    + "（干货 / 蛋类请选其他出库去向）");
            }
            // ⚠️ 三期货一律不走果蔬月台（甲方邓博 2026-08-13 口径，见 doc/16 §0「三期是什么」）：
            // 三期地块不是有机地块、不纳入地块管理、货也不发到门店，系统只需记「进了多少 / 出了多少」。
            // 而月台是有机链路的中转站 —— 收货落 L0003 蔬菜保鲜库 / L0004 重口味蔬菜库，
            // 那正是 果蔬打包 → 发货月台 → 门店 的供货池；且收货侧 UPSERT 与建篮都只写普通篮
            // （VegReceiveMapper.addStockByPlotLocation 硬带 third_phase=0、insertPlotStockRow 不 set），
            // 三期货走进去会**当场洗掉三期标识**、变成门店可取的普通有机货。
            // 这道守卫按 third_phase 判、不按有没有地块判：多地块三期货本就因 plot_id 为空被下游拦下，
            // 但「恰好 1 块在种地块」的三期货带着真实 plot_id、整条链路走得通，正是那条会漏的暗门。
            if (DEST_VEG_DOCK.equals(bo.getOutDest()) && LocationStock.thirdPhaseOf(stock) == THIRD_PHASE_YES) {
                throw new ServiceException("「" + product.getProductName() + "」是三期货，不走果蔬月台"
                    + "（三期作物不按地块管理、也不发到门店）。出库请用「产品出库」或「毛菜间出库管理」直接记账。");
            }

            // 扣库存 + 写 backstage_out 出库流水，全部复用产品出库既有口径
            // （含库位盘点锁校验、超扣 fail-fast、按行原子扣减）。跨 bean 调用，事务并入本方法。
            StockOutBo outBo = new StockOutBo();
            outBo.setId(stock.getId());
            outBo.setOutDate(bo.getOutDate());
            outBo.setQuantity(item.getQuantity());
            outBo.setStockOutDest(bo.getOutDest());
            outBo.setRemark(bo.getRemark());
            Long flowId = locationStockService.productOut(outBo);

            // 回写流水的地块、批次与出库日期：
            //   plot_id 来自库存行（月台/饲喂台账按地块×作物定位）；
            //   batch_no 让同一次提交的多条聚合成 row187 列表里的「一单」；
            //   flow_date 改记业务日期 —— productOut 默认写 new Date()（实际操作时刻），
            //   但甲方 row187 明确「可以选择当天和历史的日期」，补录历史日期时列表必须显示所选那天。
            //   沿用项目补录约定：选当天则保留真实时分秒，选历史日期则落该日 00:00:00。
            // ⚠️ third_phase 不在这里补 —— 已由 productOut 从被扣的库存行统一继承（V6 row92 唯一收口点）。
            StockFlow patch = new StockFlow();
            patch.setId(flowId);
            patch.setPlotId(stock.getPlotId());
            patch.setBatchNo(batchNo);
            patch.setFlowDate(resolveFlowDate(bo.getOutDate()));
            // row194：销售单价快照。前端默认带出产品 sale_price 但允许改，故必须按本次录入值落在流水行上，
            // 不能事后回读产品主数据 —— 否则改一次产品价格，历史出库单金额会整体漂移。
            patch.setOutUnitPrice(item.getOutUnitPrice() != null ? item.getOutUnitPrice() : product.getSalePrice());
            stockFlowMapper.updateById(patch);

            // 这批货归属哪条毛菜处理汇总行 —— 唯一用途是让月台明细挂对行（见类头注与 resolveHandleId）。
            // 解析出来的行只做挂载点，一个重量列都不改。
            boolean toDock = DEST_VEG_DOCK.equals(bo.getOutDest());
            Long cropId = resolveCropIdByProduct(product.getId());
            Long handleId = resolveHandleId(product, stock, cropId, toDock);

            // 去向额外下游（业态范围 Kevin 2026-08-03 拍板 D3：**三类业态都写**有机饲喂台账
            // ——干货/蛋类也可能真拿去喂猪，这笔账要记）：
            //   · 果蔬月台 → handle_record(handle_target=2)：月台待入库量与日统计都读它；
            //   · 猪只饲料 → feed_log：有机饲喂的权威台账。
            //
            // ⚠️ 两条去向都**只写流水台账，不碰 send_platform_weight / feed_weight 两个汇总列**
            // （见类头注）——那两列的读取方读的正是这里写的 handle_record / feed_log 明细，
            // 汇总列再加一次就是双重计数。
            if (toDock) {
                insertPlatformHandleRecord(handleId, stock, cropId,
                    item.getQuantity(), userId, resolveFlowDate(bo.getOutDate()));
            } else if (DEST_FEED.equals(bo.getOutDest())) {
                insertFeedLog(product, cropId, item.getQuantity(), stock.getLocationId(),
                    userId, bo.getOutDate());
            }

        }
        log.info("[VEG-OUT] dest={} items={} products={} batchNo={}",
            bo.getOutDest(), bo.getItems().size(), productCount, batchNo);
        return batchNo;
    }

    /**
     * 取齐本单涉及的库存行（{@code stockId → 行}，按 items 顺序），缺行直接 fail-fast。
     *
     * <p>为什么先取齐再进主循环：一单几个产品只有拿到库存行才数得出来，而这道上限必须在
     * <b>扣任何库存、生成任何单号之前</b>判 —— 边扣边发现超限只能靠事务回滚，单号却已经消耗掉了
     * （出库单号终生递增，回滚不还号）。顺带让主循环不必二次查库。</p>
     */
    private Map<Long, LocationStock> loadStocks(List<VegOutItemBo> items) {
        Map<Long, LocationStock> stocks = new java.util.LinkedHashMap<>();
        for (VegOutItemBo item : items) {
            if (stocks.containsKey(item.getStockId())) {
                continue;
            }
            LocationStock stock = locationStockMapper.selectById(item.getStockId());
            if (stock == null) {
                throw new ServiceException("库存记录不存在或已删除：" + item.getStockId());
            }
            stocks.put(item.getStockId(), stock);
        }
        return stocks;
    }

    @Override
    public TableDataInfo<VegOutBatchVo> queryBatchPage(VegOutQuery query, PageQuery pageQuery) {
        VegOutQuery q = query != null ? query : new VegOutQuery();
        IPage<VegOutBatchVo> page = vegOutMapper.selectBatchPage(
            pageQuery.build(), q.getBeginDate(), q.getEndDate(), q.getOutDest(), q.getOperatorId());
        fillOperatorNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<VegOutBatchVo> queryBatchList(VegOutQuery query) {
        VegOutQuery q = query != null ? query : new VegOutQuery();
        List<VegOutBatchVo> rows = vegOutMapper.selectBatchList(
            q.getBeginDate(), q.getEndDate(), q.getOutDest(), q.getOperatorId());
        fillOperatorNames(rows);
        return rows;
    }

    @Override
    public List<VegOutDetailVo> queryBatchDetail(String batchNo, String productName) {
        if (StringUtils.isBlank(batchNo)) {
            return List.of();
        }
        return vegOutMapper.selectBatchDetail(batchNo, productName);
    }

    @Override
    public List<VegOutDetailVo> queryBatchDetailForExport(String batchNo, String productName) {
        List<VegOutDetailVo> rows = queryBatchDetail(batchNo, productName);
        rows.forEach(r -> r.setOutQtyLabel(formatQtyLabel(r.getOutWeight(), r.getProductUnit())));
        return rows;
    }

    /**
     * 「出库量」带单位展示串 —— 与详情弹框 {@code fmtQty} 逐字对齐（甲方拿导出跟弹框对账）。
     *
     * <p>kg 或单位缺失 → 3 位小数 + {@code kg}（如 {@code 12.000kg}）；
     * 其余计件单位 → 去尾零的数值 + 空格 + 单位（如 {@code 3 袋}）。</p>
     */
    private static String formatQtyLabel(BigDecimal qty, String unit) {
        if (qty == null) {
            return "";
        }
        String u = unit != null ? unit.trim() : "";
        if (u.isEmpty() || "kg".equalsIgnoreCase(u)) {
            return qty.setScale(3, java.math.RoundingMode.HALF_UP).toPlainString() + "kg";
        }
        return qty.stripTrailingZeros().toPlainString() + " " + u;
    }

    /**
     * 定位（必要时补建）该批货所属的毛菜处理汇总行，返回其 id。
     *
     * <p>用途只有一个：把月台明细（{@link #insertPlatformHandleRecord}）挂到对的毛菜处理汇总行上。
     * <b>不再用于回写「果蔬处理重量」</b> —— 甲方 2026-08-19 定：毛菜间出库不计入果蔬处理重量。
     * 两处各写一套解析是曾经的 bug 源：月台那套有 {@code (作物, 地块)} 兜底、回写那套只认
     * {@code source_biz_id}，于是采摘活动直送篮（无来源标识、有地块）出到月台时明细记了、汇总列没记，
     * 月台明细合计与「果蔬处理重量」当场对不上。</p>
     *
     * <p><b>只定位、不改任何重量列</b> —— {@code send_platform_weight / feed_weight} 的读取方读的是
     * handle_record / feed_log 明细（见类头注），汇总列一个都不动；{@code handled_weight} 同样不动，
     * 本 service 出的量是甲方损耗公式里与月台/饲喂并列的「出库」项，不属于「果蔬处理重量」。</p>
     *
     * <p>定位链：① 篮子的 {@code source_biz_id}（毛菜采摘录入建的地块篮）→ 精确定位到建它的那条种植记录
     * 的汇总行。这一步优先于 ② —— 同一 {@code (作物, 地块)} 可能有两条种植记录，
     * ② 的 {@code ORDER BY id DESC LIMIT 1} 会把账挂到最新那条上，而货其实是老那条采的。
     * ② 库存行 product_id → 作物（{@code t_plant_crop_info.related_product} 反查，1:1）+ 库存行 plot_id
     * → {@code t_warehouse_vegetable_handle}；查不到则按需补建一条最小行。</p>
     *
     * <p><b>为什么 ② 定位不到时要按需补建而不是直接跳过或直接拦死</b>：</p>
     * <ul>
     *   <li>跳过不行 —— handle 行是这批货到了月台的<b>挂载点</b>：mp 月台待入库量读的是
     *       {@code t_warehouse_handle_record}（按产品拆），而那条明细必须挂在某个 {@code handle_id} 上。
     *       定位不到就写不出明细，库存被扣、流水也写了，但货在月台永远不出现、也永远收不了
     *       —— 等于凭空蒸发。</li>
     *   <li>一律拦死也不行 —— L0006 的库存<b>有两条合法来源</b>：①毛菜处理间「入库」去向
     *       （会建 handle 行）；②mp 采摘录入把采摘去向选成「毛菜保鲜室」，走
     *       {@code VegetableHandleServiceImpl.insertPickStockIn}，该路径<b>只写库存不建 handle 行</b>
     *       （与同处的 {@code insertPickPlatform} 不对称）。②进来的货是完全合法的库存，
     *       拦死等于这批货永远出不去，而「毛菜间处理记录」页是只读的（只有 list/export 权限），
     *       用户根本没有补录入口。</li>
     * </ul>
     *
     * @param cropId   已解析好的作物 id（{@code product.related_product} 反查，可为 null）
     * @param required {@code true}（果蔬月台去向）= 定不出归属就抛，绝不放行；
     *                 {@code false}（饲料 / 后厨等去向）= 返 null，调用方跳过回写。
     *                 干货 / 蛋类反查不到作物，属正常情况 —— 它们在本表本就没有汇总行，
     *                 按月台那套硬拒会把合法的干货饲喂 / 后厨领用整单拦死。
     * @return 汇总行 id；{@code required=false} 且归属定不了时返 null
     */
    private Long resolveHandleId(ProductInfo product, LocationStock stock, Long cropId, boolean required) {
        VegetableHandle bySource = resolveHandleBySource(stock);
        if (bySource != null) {
            return bySource.getId();
        }
        if (cropId != null && stock.getPlotId() != null) {
            VegetableHandle handle = vegetableHandleMapper.selectOne(new LambdaQueryWrapper<VegetableHandle>()
                .eq(VegetableHandle::getCropId, cropId)
                .eq(VegetableHandle::getPlotId, stock.getPlotId())
                .orderByDesc(VegetableHandle::getId)
                .last("LIMIT 1"));
            if (handle == null) {
                handle = createMinimalHandle(cropId, stock.getPlotId(), product.getId());
            }
            return handle.getId();
        }
        if (required) {
            // 连(作物, 地块)都定不了 → 月台侧无处归集，拦（放过去就是扣了库存、货却永远到不了月台）
            throw new ServiceException("「" + product.getProductName() + "」"
                + (stock.getPlotId() == null ? "该库存行未关联地块" : "未配置对应作物（作物管理的关联产品）")
                + "，无法归集到果蔬月台。请先补齐后再操作。");
        }
        return null;
    }

    /**
     * 写一条毛菜处理明细（{@code handle_target=2} 月台）。
     *
     * <p><b>为什么必须写</b>：日统计「发往月台果蔬总重」（{@code WarehouseStatAggregateMapper.sumSendPlatformWeight}）
     * 读的是 {@code t_warehouse_handle_record} 而非 {@code vegetable_handle.send_platform_weight}
     * （历史上按后者算会错记归属日）。兄弟路径 {@code insertPickPlatform} 也是两张表都写，
     * 这里不写会让本功能出的货在该日统计上silently 少算。</p>
     */
    private void insertPlatformHandleRecord(Long handleId, LocationStock stock, Long cropId,
                                            BigDecimal weight, Long userId, Date outDate) {
        HandleRecord record = new HandleRecord();
        record.setHandleId(handleId);
        record.setPlotId(stock.getPlotId());
        record.setCropId(cropId);
        // row55：果蔬月台待入库量改为按产品聚合、数据源就是这张明细表。不落产品的话，
        // 从毛菜间出库到月台的量会全部挂到作物默认产品名下（红薯杆的货显在红薯卡里）。
        // 出的是哪个库存篮就是哪个产品，stock 上现成有。
        record.setProductId(stock.getProductId());
        record.setRecordType(RECORD_TYPE_HANDLE);
        record.setRecordWeight(weight);
        record.setHandleTarget(HANDLE_TARGET_PLATFORM);
        record.setIsFinish(2);
        record.setHandleUser(userId);
        record.setHandleTime(outDate);
        record.setRemark("毛菜间出库");
        handleRecordMapper.insert(record);
    }

    /**
     * 按需补建一条最小毛菜处理汇总行（用于 mp 采摘直送毛菜保鲜室进来的库存 —— 那条路径只写库存不建 handle 行）。
     *
     * <p>各重量列记 0 <b>且此后一直是 0</b>：这批货没经过毛菜处理间的称重/处理流程，
     * 这行只是借来做「作物×地块」的归集锚点，好让 handle_record 明细有 {@code handle_id} 可挂。
     * {@code handle_status} 记 processing、{@code is_finish=2}（未完成），避免被当成已结算行参与损耗结算。</p>
     */
    private VegetableHandle createMinimalHandle(Long cropId, Long plotId, Long productId) {
        VegetableHandle h = new VegetableHandle();
        h.setPlotId(plotId);
        h.setCropId(cropId);
        h.setProductId(productId);
        h.setPickStartTime(new Date());
        h.setPickedWeight(BigDecimal.ZERO);
        h.setHandledWeight(BigDecimal.ZERO);
        h.setFeedWeight(BigDecimal.ZERO);
        h.setSendPlatformWeight(BigDecimal.ZERO);
        h.setStockInWeight(BigDecimal.ZERO);
        h.setLossWeight(BigDecimal.ZERO);
        h.setIsWeighed(1);
        h.setIsFinish(2);
        h.setHandleStatus("processing");
        h.setRemark("毛菜间出库按需补建（采摘直送毛菜保鲜室的库存无毛菜处理记录）");
        vegetableHandleMapper.insert(h);
        log.info("[VEG-OUT] 补建毛菜处理归集行 cropId={} plotId={} id={}", cropId, plotId, h.getId());
        return h;
    }

    /**
     * 按库存篮的来源标识定位毛菜处理汇总行；篮子无来源标识（非毛菜地块篮）返 null。
     *
     * <p>{@code source_biz_id} 存的是建篮的 {@code t_warehouse_planting_record.id}，
     * 一条种植记录对应至多一行汇总，所以这条定位是精确的、不需要「取最新一条」这种猜。</p>
     *
     * <p>有来源标识却查不到汇总行 = 数据被破坏（篮子由 {@code submitHarvest} 建，那一步必然先有汇总行）。
     * 此时<b>硬拒 + 整单回滚</b>而不是 warn 后放行：放行意味着这批 kg 悄悄逃出「果蔬处理重量」，
     * 收口时被当成损耗，账错得无声无息 —— 与本 service 既有的 fail-fast 风格一致
     * （见 {@link #resolveHandleId} 定位不到就拦）。</p>
     *
     * <p>错误码取 400 而不是 {@code ServiceException} 默认的 500：当前没有任何 API 能软删汇总行，
     * 这条路径不可达、属防御性收敛；真触发时也是「这条库存数据需要人工核查」而非服务端故障，
     * 500 会把它冲进告警噪声里。拦截语义不变 —— 仍然整单回滚，绝不降级成 warn 跳过。</p>
     */
    private VegetableHandle resolveHandleBySource(LocationStock stock) {
        Long sourceBizId = stock.getSourceBizId();
        if (sourceBizId == null) {
            return null;
        }
        VegetableHandle handle = vegetableHandleMapper.selectByPlantingRecordId(sourceBizId);
        if (handle == null) {
            throw new ServiceException("库存行 " + stock.getId() + " 标记来自种植记录 " + sourceBizId
                + "，但该记录的毛菜处理汇总行不存在，无法记账。请联系管理员核查数据后再出库。", 400);
        }
        return handle;
    }

    /**
     * 写有机饲喂记录，{@code feed_type} 即有机饲喂记录里的「位置」。
     *
     * <p>位置按<b>货实际是从哪个库出的</b>定，不写死：毛菜鲜品库出的记「毛菜间」，
     * 猪肉鲜品库 / 红白脏库 / 干货库 / 蛋类库出的记「仓库」——
     * 这几个库本来就不在毛菜间，写死 veg_handle 会让饲喂台账的位置栏对不上实物来源。</p>
     *
     * <p>{@code cropId} 由调用方传入（与 handle 解析共用同一次反查，不重复查库）；
     * 干货 / 蛋类反查不到作物 → 传 null，作物名回落成产品名。</p>
     */
    private void insertFeedLog(ProductInfo product, Long cropId, BigDecimal weight,
                               Long locationId, Long userId, Date outDate) {
        FeedLog feedLog = new FeedLog();
        feedLog.setFeedDate(outDate);
        feedLog.setCropId(cropId);
        feedLog.setCropName(cropId != null ? cropNameOf(cropId) : product.getProductName());
        feedLog.setFeedType(isFreshVegLocation(locationId) ? FEED_TYPE_VEG_HANDLE : FEED_TYPE_WAREHOUSE);
        feedLog.setProductId(product.getId());
        feedLog.setLocationId(locationId);
        feedLog.setOperatorId(userId);
        feedLog.setFeedWeight(weight);
        feedLogMapper.insert(feedLog);
    }

    /** 该库位是不是毛菜鲜品库（L0006）——决定饲喂记录的「位置」记毛菜间还是仓库。 */
    private boolean isFreshVegLocation(Long locationId) {
        if (locationId == null) {
            return false;
        }
        LocationInfo loc = locationInfoMapper.selectById(locationId);
        return loc != null && LOCATION_CODE_FRESH_VEG.equals(loc.getLocationCode());
    }

    /** 产品 → 作物反查（{@code crop_info.related_product}，全库 1:1）。查不到返 null。 */
    private Long resolveCropIdByProduct(Long productId) {
        if (productId == null) {
            return null;
        }
        CropInfo crop = cropInfoMapper.selectOne(new LambdaQueryWrapper<CropInfo>()
            .eq(CropInfo::getRelatedProduct, productId)
            .orderByAsc(CropInfo::getId)
            .last("LIMIT 1"));
        return crop != null ? crop.getId() : null;
    }

    private String cropNameOf(Long cropId) {
        CropInfo crop = cropInfoMapper.selectById(cropId);
        return crop != null ? crop.getCropName() : null;
    }

    /**
     * 出库流水时间戳：业务日期为准。
     *
     * <p>选当天 → 保留当前时分秒（真实操作时刻，出库记录看得出先后）；
     * 选历史日期 → 该日 00:00:00（那天几点录的不可知，与事件台账补录同款约定）。</p>
     */
    private Date resolveFlowDate(Date outDate) {
        if (outDate == null) {
            return new Date();
        }
        LocalDate picked = outDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return picked.isEqual(LocalDate.now()) ? new Date() : outDate;
    }

    /** 毛菜鲜品库库位 id；库位未维护时 fail-fast（与毛菜处理入库同款报错口径）。 */
    private Long requireFreshVegLocationId() {
        LocationInfo loc = locationInfoMapper.selectOne(new LambdaQueryWrapper<LocationInfo>()
            .eq(LocationInfo::getLocationCode, LOCATION_CODE_FRESH_VEG)
            .last("LIMIT 1"));
        if (loc == null) {
            throw new ServiceException("毛菜鲜品库（库位编码 " + LOCATION_CODE_FRESH_VEG + "）不存在，请先在库位管理维护");
        }
        return loc.getId();
    }

    /**
     * 可出库库位（{@link #ALLOWED_LOCATION_CODES}）的 id 集合。
     *
     * <p>按 {@code location_code} 解析。某个库位没维护就不进集合（该库的产品自然也不会出现在候选里），
     * 不 fail-fast 整单 —— 甲方可能只用其中一两个库。</p>
     */
    private java.util.Set<Long> resolveAllowedLocationIds() {
        List<LocationInfo> rows = locationInfoMapper.selectList(new LambdaQueryWrapper<LocationInfo>()
            .in(LocationInfo::getLocationCode, ALLOWED_LOCATION_CODES));
        if (rows.isEmpty()) {
            throw new ServiceException("毛菜鲜品库 / 干货库 / 蛋类库 / 猪肉鲜品库 / 红白脏库均未维护，请先在库位管理配置");
        }
        return rows.stream().map(LocationInfo::getId).collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 出库单号（row192）：7 位纯数字、终生递增，走统一编码生成器
     * （Redisson 锁 + 序号表唯一键保证并发不撞号，故不再需要原来的随机后缀）。
     */
    private String generateBatchNo() {
        return bizCodeGenerator.generate(BizCodeType.VEG_OUT_NO, java.util.Map.of());
    }

    /** 批量回填操作人姓名（一次查 sys_user，避免 N+1）。 */
    private void fillOperatorNames(List<VegOutBatchVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> ids = rows.stream().map(VegOutBatchVo::getOperatorId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = ids.stream().collect(Collectors.toMap(
            id -> id, id -> StringUtils.blankToDefault(userService.selectNicknameById(id), ""), (a, b) -> a));
        rows.forEach(r -> r.setOperatorName(nameMap.get(r.getOperatorId())));
    }
}
