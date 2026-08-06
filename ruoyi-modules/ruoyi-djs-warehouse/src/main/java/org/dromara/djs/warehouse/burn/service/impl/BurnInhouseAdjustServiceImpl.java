package org.dromara.djs.warehouse.burn.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.warehouse.burn.domain.PigBurnRecord;
import org.dromara.djs.warehouse.burn.domain.bo.BurnInhouseAdjustBo;
import org.dromara.djs.warehouse.burn.domain.query.BurnInhouseAdjustQuery;
import org.dromara.djs.warehouse.burn.domain.vo.BurnInhouseAdjustVo;
import org.dromara.djs.warehouse.burn.mapper.BurnInhouseAdjustMapper;
import org.dromara.djs.warehouse.burn.mapper.PigBurnRecordMapper;
import org.dromara.djs.warehouse.burn.service.IBurnInhouseAdjustService;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.stat.service.IWarehouseStatService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;

/**
 * 燎毛间产品重量调整 Service 实现（V6-R43，admin 系统管理 → 数据管理）。
 *
 * <h3>为什么是「改写历史」而不是「补冲正流水」</h3>
 * <p>甲方原文：「逻辑上这里调整完成之后，相当于产品是按修改完成后的重量入的库」。
 * 所以对既有台账做 UPDATE，不新增反向流水 —— 事后看这头猪，就像当初就是按新重量入的库。</p>
 *
 * <h3>一次调整要动的下游（= 一次燎毛入库写过的全部数值位）</h3>
 * <table border="1">
 *   <caption>调整链路</caption>
 *   <tr><th>表</th><th>字段</th><th>与产品入库重量的关系</th><th>怎么改</th></tr>
 *   <tr><td>{@code t_warehouse_product_inhouse}</td><td>{@code product_weight}</td>
 *       <td>就是它本身</td><td><b>覆盖</b>为新重量 + 打 {@code is_adjusted/adjust_time/adjust_by} 留痕</td></tr>
 *   <tr><td>{@code t_warehouse_location_stock}</td><td>{@code product_stock}</td>
 *       <td>建账时 = 入库重量，之后是「该库存行当前余量」</td>
 *       <td><b>按差额</b>增减 —— 余量是活数，覆盖会抹掉此前对它的其它变动</td></tr>
 *   <tr><td>{@code t_warehouse_stock_flow}</td><td>{@code change_num / change_quantity}</td>
 *       <td>该次入库事件的量，只由这一次入库产生</td><td><b>覆盖</b>为新重量（改写这笔历史事件）</td></tr>
 *   <tr><td>{@code t_warehouse_pig_burn_record}</td><td>{@code burn_weight}</td>
 *       <td>本次提交<b>各产出行之和</b></td><td><b>按差额</b>增减（覆盖会抹掉同批其它产品）</td></tr>
 *   <tr><td>{@code t_warehouse_bar_info}</td><td>{@code in_weight}</td>
 *       <td>「处理完成」时才写 = 各产出行合计（整只口径）</td>
 *       <td>调整窗口内恒 NULL → 保持 NULL；非空时<b>按差额</b>增减</td></tr>
 * </table>
 *
 * <p>mp 燎毛间页面的「已称重重量」（{@code BarPendingVo.inboundedWeight}）是 compute-on-read
 * ——现算 {@code SUM(product_inhouse.product_weight)}，改完第一张表它就自动跟着变，无需单独处理。</p>
 *
 * <p><b>统计快照是例外，必须显式重算</b>：白条总重 / 平均白条重 / 白条出成率不是现算的，
 * {@code WarehouseStatJob} 每日按 {@code SUM(burn_weight) WHERE DATE(burn_time)=T-1} 算好落进
 * {@code t_warehouse_indicator_record}，月表 {@code t_warehouse_monthly_record} 再从日表回读。
 * 所以 Step 9 在燎毛日早于今天时调 {@link IWarehouseStatService#aggregate}（UPSERT，幂等）重算那一天。</p>
 *
 * <h3>不动的下游（各有确定原因，不是漏了）</h3>
 * <ul>
 *   <li>{@code t_warehouse_loss_flow} 燎毛损耗：由 {@code finishBurn}「处理完成」那一刻按
 *       {@code 接收重量 − 入库合计} 现算并写入。调整只发生在处理完成<b>之前</b>，此时损耗行还没产生，
 *       将来生成时自然用的就是调整后的合计。</li>
 *   <li>{@code t_warehouse_trace_event} 燎毛/白条入库事件：猪肉链追溯码要到打包出库才生成，
 *       燎毛时反查落空（{@code recordEventByEarNo} warn 跳过），此刻没有事件行可改；
 *       后续 {@code backfillEarNoEvents} 回填时只写时间戳不写重量。</li>
 *   <li>{@code t_warehouse_pig_cut_record.drip_loss}（预冷损耗，= 入库重 − 领用出库重）：
 *       白条领用发生在 {@code in_stock} 之后，即处理完成之后，调整窗口内不可能已存在。</li>
 * </ul>
 *
 * <h3>并发：调整 vs 处理完成</h3>
 * <p>两者都以 {@code bar_info} 那一行为闸门。调整事务把
 * {@link BarInfoMapper#adjustInWeightIfBurning}（带 {@code status IN ('pending_singe','singing')} 条件）
 * 放在<b>最后一步</b>：拿不到 = 有人已点「处理完成」→ 抛异常，前面几张表的写入随事务一起回滚。
 * 反向的窗口（finishBurn 先读了旧的产出行合计、调整随后提交）由 {@code finishBurn} 里那句
 * {@code FOR UPDATE} 锁住产出行来关闭。</p>
 *
 * @author djs
 * @since V6-R43
 */
@Slf4j
@Service
public class BurnInhouseAdjustServiceImpl implements IBurnInhouseAdjustService {

    /**
     * 白条在燎毛间的两个在制态：只有这两个态才允许调整入库重量（= 甲方说的「未点处理完成」）。
     */
    private static final String BAR_STATUS_PENDING_SINGE = "pending_singe";
    private static final String BAR_STATUS_SINGING = "singing";

    /**
     * 产出行来源（{@code t_warehouse_product_inhouse.source}）：warehouse=仓库工序产出 / store=门店拆单。
     */
    private static final String SOURCE_WAREHOUSE = "warehouse";

    private final BurnInhouseAdjustMapper adjustMapper;
    private final ProductInhouseMapper productInhouseMapper;
    private final BarInfoMapper barInfoMapper;
    private final LocationStockMapper locationStockMapper;
    private final StockFlowMapper stockFlowMapper;
    private final PigBurnRecordMapper pigBurnRecordMapper;
    private final IStockCheckService stockCheckService;
    private final IWarehouseStatService warehouseStatService;

    public BurnInhouseAdjustServiceImpl(BurnInhouseAdjustMapper adjustMapper,
                                        ProductInhouseMapper productInhouseMapper,
                                        BarInfoMapper barInfoMapper,
                                        LocationStockMapper locationStockMapper,
                                        StockFlowMapper stockFlowMapper,
                                        PigBurnRecordMapper pigBurnRecordMapper,
                                        IStockCheckService stockCheckService,
                                        IWarehouseStatService warehouseStatService) {
        this.adjustMapper = adjustMapper;
        this.productInhouseMapper = productInhouseMapper;
        this.barInfoMapper = barInfoMapper;
        this.locationStockMapper = locationStockMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.pigBurnRecordMapper = pigBurnRecordMapper;
        this.stockCheckService = stockCheckService;
        this.warehouseStatService = warehouseStatService;
    }

    @Override
    public TableDataInfo<BurnInhouseAdjustVo> queryPageList(BurnInhouseAdjustQuery query, PageQuery pageQuery) {
        String from = query == null ? null : query.getInboundDateFrom();
        String to = query == null ? null : query.getInboundDateTo();
        IPage<BurnInhouseAdjustVo> page = adjustMapper.selectAdjustPage(
            pageQuery.build(),
            toDayStart(from),
            toNextDayStart(to),
            query == null ? null : query.getProductName(),
            query == null ? null : query.getIsAdjusted());
        fillPendingWeight(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustWeight(BurnInhouseAdjustBo bo) {
        // ---------- Step 1：取产出行 + 结构校验（必须真的是燎毛间入库产出行）----------
        ProductInhouse row = productInhouseMapper.selectById(bo.getId());
        if (row == null) {
            throw new ServiceException("入库记录不存在或已删除：" + bo.getId());
        }
        if (row.getWhiteBarId() == null || row.getMaterialId() != null
            || !SOURCE_WAREHOUSE.equals(row.getSource())) {
            throw new ServiceException("该记录不是燎毛间入库记录，不支持调整入库重量");
        }
        if (StringUtils.isBlank(row.getWhiteBarNo())) {
            // 白条流水号是产出行 ↔ 库存行 ↔ 入库流水的唯一对应键；缺了就无法保证三处同步，
            // 宁可拒绝也不做「只改一半」的调整。
            throw new ServiceException("该入库记录缺少白条流水号，无法同步库存与流水，不允许调整");
        }
        BigDecimal oldWeight = row.getProductWeight();
        if (oldWeight == null) {
            throw new ServiceException("该入库记录没有入库重量，无法调整");
        }
        BigDecimal newWeight = bo.getProductWeight();
        BigDecimal delta = newWeight.subtract(oldWeight);

        // ---------- Step 2：可调整窗口（业务前置条件，后端必须拦，不能只靠前端隐藏按钮）----------
        // 排在「重量没变就早退」之前：已处理完成的行无论提交什么值都该被拒，
        // 提交一个恰好相同的重量而拿到 200，是把该拒的请求答成了 ok。
        BarInfo bar = barInfoMapper.selectById(row.getWhiteBarId());
        if (bar == null) {
            throw new ServiceException("入库记录关联的白条不存在：" + row.getWhiteBarId());
        }
        if (!BAR_STATUS_PENDING_SINGE.equals(bar.getStatus()) && !BAR_STATUS_SINGING.equals(bar.getStatus())) {
            throw new ServiceException("该猪只燎毛间已处理完成，不能再调整入库重量");
        }
        // 库位级业务锁（WMS-STOCK-001）：盘点进行中的库位禁出入库 —— 调整会动库存，同一道闸
        if (row.getLocationId() != null) {
            stockCheckService.assertLocationUnlocked(row.getLocationId());
        }

        if (delta.signum() == 0) {
            // 重量没变 = 什么都不用做。不打「已调整」标记：那个标记要回答的是「这条数据被人改过没有」，
            // 提交一个一模一样的值并没有改动过数据。
            return;
        }

        // ---------- Step 3：上限校验（与燎毛入库同口径：全部产出行合计不得超过猪只接收重量）----------
        BigDecimal arriveWeight = bar.getArriveWeight();
        if (arriveWeight != null) {
            BigDecimal otherRows = sumOtherRowsWeight(row.getWhiteBarId(), row.getId());
            BigDecimal maxAllowed = arriveWeight.subtract(otherRows).max(BigDecimal.ZERO);
            if (newWeight.compareTo(maxAllowed) > 0) {
                throw new ServiceException("调整后入库重量不能超过 " + maxAllowed.stripTrailingZeros().toPlainString()
                    + "kg（猪只接收重量 " + arriveWeight.stripTrailingZeros().toPlainString()
                    + "kg − 其它产品已入库 " + otherRows.stripTrailingZeros().toPlainString() + "kg）");
            }
        }

        Long userId = LoginHelper.getUserId();

        // ---------- Step 4：产出行（覆盖新重量 + 调整留痕；带旧值乐观锁防并发算错差额）----------
        if (adjustMapper.applyAdjust(row.getId(), oldWeight, newWeight, userId) == 0) {
            throw new ServiceException("该入库记录已被他人修改，请刷新后重试");
        }

        // ---------- Step 5：白条库存行（按差额增减）----------
        // 必须恰好命中 1 行：WHERE 只有 (white_bar_no, product_id)，而 location_stock 上 idx_white_bar_no
        // 是非唯一索引 —— 将来出现第二行（移库 / 盘点拆行）就会一次改两行、差额双倍生效。
        // 只判 ==0 的话这种情况会静默通过，所以判 !=1。
        int stockRows = locationStockMapper.adjustStockByWhiteBarNo(
            row.getWhiteBarNo(), row.getProductId(), delta, userId);
        if (stockRows != 1) {
            throw new ServiceException(stockRows == 0
                ? "白条库存行不存在或调整后库存为负，无法调整入库重量"
                : "该白条同一产品存在 " + stockRows + " 条库存行，无法确定调整目标，请先核对库存");
        }

        // ---------- Step 6：燎毛入库流水（覆盖为新重量）----------
        if (stockFlowMapper.updateBurnInFlowWeight(
            row.getWhiteBarNo(), row.getProductId(), newWeight, userId) == 0) {
            throw new ServiceException("燎毛入库流水不存在，无法调整入库重量");
        }

        // ---------- Step 7：燎毛记录合计重量（按差额增减）----------
        Long burnRecordId = resolveBurnRecordId(row);
        if (pigBurnRecordMapper.adjustBurnWeight(burnRecordId, delta, userId) == 0) {
            throw new ServiceException("燎毛记录不存在，无法同步燎毛间已称重重量");
        }

        // ---------- Step 8：白条（窗口乐观锁 + in_weight 差额）—— 放最后当提交闸 ----------
        if (barInfoMapper.adjustInWeightIfBurning(bar.getId(), delta, userId) == 0) {
            throw new ServiceException("该猪只燎毛间已处理完成，不能再调整入库重量");
        }

        // ---------- Step 9：统计快照重算 ----------
        // 前 8 步改的都是 as-built 明细，但白条总重 / 平均白条重 / 白条出成率是 **compute-then-store**：
        // WarehouseStatJob 每日 0:00 按 SUM(burn_weight) WHERE DATE(burn_time)=T-1 算好落进
        // t_warehouse_indicator_record，月表再从日表回读。只改明细不重算，那天的快照就永久偏差。
        // 触发很日常：白条跨零点仍停在 singing（工人没点处理完成），次日调整即命中；burn_time 由客户端传，
        // 倒填日期当天也会命中。甲方原文是「在所有对应的记录里都要将重量调整为用户录入的重量」，日报月报也算。
        // 只在燎毛日 < 今天时重算：当天的那份快照今晚跑批本来就会算。aggregate 是 UPSERT，重复执行幂等。
        LocalDate burnDate = burnDateOf(burnRecordId);
        if (burnDate != null && burnDate.isBefore(LocalDate.now())) {
            // 不 catch：统计没跟上就让整笔调整回滚重来，好过留下「明细对、报表错」且没人知道的状态。
            warehouseStatService.aggregate(burnDate);
        }

        log.info("[V6-R43] 燎毛入库重量调整 inhouseId={} whiteBarNo={} {} → {} (delta={}) operator={} statRecalc={}",
            row.getId(), row.getWhiteBarNo(), oldWeight, newWeight, delta, userId,
            burnDate != null && burnDate.isBefore(LocalDate.now()) ? burnDate : "none");
    }

    /**
     * 该燎毛记录的业务日（{@code burn_time} 的日期）——统计跑批就是按这一天分桶的。
     *
     * @return 记录不存在或 burn_time 为空时返回 null（此时不触发重算，不猜日期）
     */
    private LocalDate burnDateOf(Long burnRecordId) {
        if (burnRecordId == null) {
            return null;
        }
        PigBurnRecord record = pigBurnRecordMapper.selectById(burnRecordId);
        if (record == null || record.getBurnTime() == null) {
            return null;
        }
        return record.getBurnTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * 同一白条<b>其它</b>产出行的已入库重量之和（不含本行），用于算本行的可调上限。
     */
    private BigDecimal sumOtherRowsWeight(Long whiteBarId, Long selfId) {
        List<ProductInhouse> rows = productInhouseMapper.selectList(
            new LambdaQueryWrapper<ProductInhouse>()
                .select(ProductInhouse::getProductWeight)
                .eq(ProductInhouse::getWhiteBarId, whiteBarId)
                .ne(ProductInhouse::getId, selfId));
        BigDecimal sum = BigDecimal.ZERO;
        for (ProductInhouse r : rows) {
            if (r.getProductWeight() != null) {
                sum = sum.add(r.getProductWeight());
            }
        }
        return sum;
    }

    /**
     * 解析产出行所属燎毛记录 id：优先 {@code burn_record_id}（V202608300500 起 submitBurnRecord 直接写），
     * 历史行退回按 (耳号, 燎毛时间) 反查（该迁移的回填口径）。两条都拿不到 → 抛异常，
     * 不静默跳过 —— 跳过会让「燎毛记录」的合计重量与产出行长期对不上。
     */
    private Long resolveBurnRecordId(ProductInhouse row) {
        if (row.getBurnRecordId() != null) {
            return row.getBurnRecordId();
        }
        Long fallback = pigBurnRecordMapper.selectIdByEarNoAndBurnTime(row.getEarNo(), row.getProduceTime());
        if (fallback == null) {
            throw new ServiceException("找不到该产品对应的燎毛记录，无法同步燎毛间已称重重量");
        }
        return fallback;
    }

    /**
     * 待入库重量 = 猪只接收重量 − 该白条已入库产品重量合计（含本行），钳 0；未称重则留空。
     */
    private void fillPendingWeight(List<BurnInhouseAdjustVo> rows) {
        if (rows == null) {
            return;
        }
        for (BurnInhouseAdjustVo vo : rows) {
            BigDecimal arrive = vo.getArriveWeight();
            if (arrive == null) {
                continue;
            }
            BigDecimal inbounded = vo.getInboundedWeight() == null ? BigDecimal.ZERO : vo.getInboundedWeight();
            vo.setPendingWeight(arrive.subtract(inbounded).max(BigDecimal.ZERO));
        }
    }

    /**
     * {@code yyyy-MM-dd} → 当日 00:00:00；空 / 非法格式返 null（该条件不生效）。
     */
    private static Date toDayStart(String date) {
        LocalDate d = parseDate(date);
        return d == null ? null : Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /**
     * {@code yyyy-MM-dd} → 次日 00:00:00（SQL 用 {@code < 次日零点} 表达「含当天」的闭区间，
     * 避免 {@code <= 当天 00:00:00} 把当天有时分秒的记录全漏掉）。
     */
    private static Date toNextDayStart(String date) {
        LocalDate d = parseDate(date);
        return d == null ? null : Date.from(d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDate parseDate(String date) {
        if (StringUtils.isBlank(date)) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw new ServiceException("日期格式不正确（应为 yyyy-MM-dd）：" + date);
        }
    }

}
