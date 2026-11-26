package org.dromara.djs.store.loss.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.job.DjsJobRunner;
import org.dromara.djs.common.store.context.StoreContext;
import org.dromara.djs.store.ledger.domain.StoreDailyLedger;
import org.dromara.djs.store.ledger.mapper.StoreDailyLedgerMapper;
import org.dromara.djs.store.loss.domain.StoreLossRecord;
import org.dromara.djs.store.loss.domain.query.StoreLossQuery;
import org.dromara.djs.store.loss.domain.vo.StoreLossRecordVo;
import org.dromara.djs.store.loss.domain.vo.WhiteBarSplitLossVo;
import org.dromara.djs.store.loss.mapper.StoreLossRecordMapper;
import org.dromara.djs.store.loss.service.IStoreLossService;
import org.dromara.djs.store.stat.domain.StoreOrderCandidate;
import org.dromara.djs.store.stat.domain.StoreProductionIndicatorDaily;
import org.dromara.djs.store.stat.domain.StoreProductionIndicatorVo;
import org.dromara.djs.store.stat.mapper.StoreProductionIndicatorMapper;
import org.dromara.djs.store.stat.mapper.StoreProductionIndicatorMapper.DamageTotals;
import org.dromara.djs.store.stat.mapper.StoreProductionIndicatorMapper.ReturnTotals;
import org.dromara.djs.store.stat.mapper.StoreProductionIndicatorMapper.StoreQuantity;
import org.dromara.djs.store.stat.mapper.StoreProductionIndicatorMapper.VegetableReceiptTotals;
import org.dromara.djs.warehouse.demand.core.enums.DemandStatus;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 门店损耗和生产指标的逐日聚合（DENGBO-R15、STR-STAT-V11）。
 *
 * <p>定时任务回刷最近 31 个已完结日，两类损耗落盘到 {@code t_store_loss_record}
 * （幂等：先物理删该日旧行再重插；admin 手动重跑传显式日期）：</p>
 * <ol>
 *   <li><b>门店日损耗</b>（{@code store_daily_loss}）：读 {@code t_store_daily_ledger} 当日各行 {@code loss_qty}
 *       逐产品搬入（join {@code t_warehouse_product_info} 补 {@code product_unit}），
 *       <b>原样搬负值</b>（盘盈也搬，不钳 0）。</li>
 *   <li><b>白条分割损耗</b>（{@code white_bar_split_loss}）：按门店汇总一行（{@code product_id}=哨兵 0），
 *       {@code loss_qty} = max(0, 白条到店重 − 白条分割产品总重)；当天无白条到店（到店重=0）则不记录。
 *       另存 {@code white_bar_arrive_weight}（按 D-0210 / D-0214 归日的白条重量）
 *       与 {@code white_bar_split_weight}（白条在门店分割下来的产品总重 = max(0, 当日该店盘点台账中
 *       {@code djs_white_bar_return_product} 字典各白条部位的 {@code inbound_qty} 入库量之和
 *       − 材料外售同原材料成品当日到店重)）。</li>
 * </ol>
 *
 * @author djs
 * @since DENGBO-R15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreLossServiceImpl implements IStoreLossService {

    /** 门店日损耗类型码。 */
    private static final String LOSS_TYPE_STORE_DAILY = "store_daily_loss";
    /** 白条分割损耗类型码。 */
    private static final String LOSS_TYPE_WHITE_BAR_SPLIT = "white_bar_split_loss";

    /** 白条分割损耗汇总行哨兵产品 id（无真实产品，让唯一键生效）。 */
    private static final long SENTINEL_PRODUCT_ID = 0L;

    /** 业态字典值（{@code djs_belong_type}）：白条 —— 发货到店重量来源 + 白条分割损耗汇总行的产品类型。 */
    private static final String PRODUCT_TYPE_WHITE_BAR = "white_bar";

    /** 业态字典值（{@code djs_belong_type}）：其他 —— 外购商品 {@code belong_type} 恒空，按此值归类。 */
    private static final String BELONG_TYPE_OTHER = "other";

    /** 白条部位字典（label=部位名 / value=产品业务码 product_id）：白条在门店分割下来的部位品集合。 */
    private static final String DICT_WHITE_BAR_RETURN_PRODUCT = "djs_white_bar_return_product";

    /** 损耗类型字典（label=中文 / value=类型码）：列表展示 + 白条分割损耗汇总行 productName 占位来源。 */
    private static final String DICT_STORE_LOSS_TYPE = "djs_store_loss_type";

    /** 白条分割损耗汇总行 productName 占位兜底文案（字典缺失时用）。 */
    private static final String WHITE_BAR_SPLIT_FALLBACK_LABEL = "白条分割损耗";

    /** 白条分割损耗汇总行单位：本质是 kg 重量（到店重 − 白条分割产品总重）。 */
    private static final String UNIT_KG = "kg";

    /** 业务日时区（与项目其余「今日」口径一致，避免 DB CURDATE() 时区雷）。 */
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final StoreLossRecordMapper baseMapper;
    private final StoreDailyLedgerMapper storeDailyLedgerMapper;
    private final StoreProductionIndicatorMapper indicatorMapper;
    private final ProductInfoMapper productInfoMapper;
    private final DictService dictService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void aggregate(LocalDate targetDate) {
        LocalDate date = targetDate == null ? LocalDate.now(ZONE_SHANGHAI).minusDays(1) : targetDate;
        StoreContext.ignore(() -> rebuildDay(date));
    }

    private void rebuildDay(LocalDate date) {
        String tenantId = currentTenant();
        Set<Long> stores = new TreeSet<>();
        indicatorMapper.lockStores(tenantId).stream()
            .filter(store -> "0".equals(store.delFlag()))
            .forEach(store -> stores.add(store.id()));

        List<StoreDailyLedger> ledgers = storeDailyLedgerMapper.selectList(
            new LambdaQueryWrapper<StoreDailyLedger>().eq(StoreDailyLedger::getLedgerDate, date));
        Map<Long, BigDecimal> arrivals = quantities(indicatorMapper.selectWhiteBarReceipts(tenantId, date, null));
        Map<Long, VegetableReceiptTotals> vegetableReceipts = indicatorMapper.selectVegetableReceipts(tenantId, date)
            .stream().collect(Collectors.toMap(VegetableReceiptTotals::storeId, row -> row));
        Map<Long, ReturnTotals> returns = indicatorMapper.selectReturns(tenantId, date).stream()
            .collect(Collectors.toMap(ReturnTotals::storeId, row -> row));
        Map<Long, BigDecimal> porkFinishes = quantities(indicatorMapper.selectPorkFinishReceipts(tenantId, date));
        Map<Long, DamageTotals> damages = indicatorMapper.selectDamageTotals(tenantId, date).stream()
            .collect(Collectors.toMap(DamageTotals::storeId, row -> row));
        Set<Long> cutProductIds = resolveWhiteBarReturnProductIds(tenantId);
        Map<Long, BigDecimal> materialSold = arrivals.isEmpty() ? Map.of()
            : materialSoldReceipts(date, null, cutProductIds);

        Map<Long, LocalDateTime> firstOrders = new HashMap<>();
        Set<Long> ordersPlaced = new HashSet<>();
        for (StoreOrderCandidate order : indicatorMapper.selectOrderCandidates(tenantId, date)) {
            LocalDateTime submitted = order.firstSubmittedAt();
            if (submitted == null) {
                continue;
            }
            // 需求日只判未取消的单，年指标仍按实际首次下单日起算。
            if (date.equals(order.demandDate()) && !DemandStatus.CANCELLED.name().equals(order.demandStatus())) {
                firstOrders.merge(order.storeId(), submitted, (a, b) -> a.isBefore(b) ? a : b);
            }
            if (date.equals(submitted.toLocalDate())) {
                ordersPlaced.add(order.storeId());
            }
        }

        stores.addAll(arrivals.keySet());
        stores.addAll(vegetableReceipts.keySet());
        stores.addAll(returns.keySet());
        stores.addAll(porkFinishes.keySet());
        stores.addAll(damages.keySet());
        stores.addAll(firstOrders.keySet());
        stores.addAll(ordersPlaced);
        for (StoreDailyLedger ledger : ledgers) {
            if (ledger.getStoreId() != null && cutProductIds.contains(ledger.getProductId())) {
                stores.add(ledger.getStoreId());
            }
        }

        // 同一事务清理陈旧标记和归属日，逐店日快照与两类损耗同时提交。
        indicatorMapper.deleteDay(tenantId, date);
        baseMapper.physicalDeleteByDate(tenantId, date);
        int dailyRows = aggregateStoreDailyLoss(date, ledgers);
        LocalDateTime deadline = date.minusDays(1).atTime(16, 0);
        for (Long storeId : stores) {
            BigDecimal arrive = nz(arrivals.get(storeId));
            BigDecimal split = arrive.signum() > 0
                ? sumWhiteBarSplitWeight(storeId, date, cutProductIds, nz(materialSold.get(storeId))) : BigDecimal.ZERO;
            BigDecimal loss = splitLoss(arrive, split);
            LocalDateTime orderTime = firstOrders.get(storeId);
            StoreProductionIndicatorDaily row = new StoreProductionIndicatorDaily();
            row.setStoreId(storeId);
            row.setStatDate(date);
            row.setOrderTimely(orderTime == null ? null : !orderTime.isAfter(deadline));
            row.setOrderPlaced(ordersPlaced.contains(storeId));
            row.setPorkReceivedWeightKg(arrive.add(nz(porkFinishes.get(storeId))));
            row.setWhiteBarReceivedWeight(arrive);
            row.setSplitProductWeight(split);
            row.setSplitLossWeight(loss);
            row.setSplitYieldRate(StoreProductionIndicatorVo.splitRate(split, arrive));
            row.setSplitLossRate(StoreProductionIndicatorVo.splitRate(loss, arrive));
            setVegetableAndReturnTotals(row, vegetableReceipts.get(storeId), damages.get(storeId), returns.get(storeId));
            indicatorMapper.insert(row);
            if (arrive.signum() > 0) {
                writeWhiteBarSplitLoss(date, storeId, arrive, split, loss);
            }
        }
        log.info("[STORE-LOSS] aggregate date={} 门店日损耗={}行 指标={}店", date, dailyRows, stores.size());
    }

    private static void setVegetableAndReturnTotals(StoreProductionIndicatorDaily row, VegetableReceiptTotals vegetables,
                                                     DamageTotals damage, ReturnTotals returns) {
        row.setVegReceivedPortionCount(vegetables == null ? BigDecimal.ZERO : nz(vegetables.portions()));
        row.setVegReceiveKgWeight(vegetables == null ? BigDecimal.ZERO : nz(vegetables.kgWeight()));
        row.setVegDamagePortionCount(damage == null ? BigDecimal.ZERO : damage.registeredPortions());
        row.setVegDamageReceivedPortionCount(damage == null ? BigDecimal.ZERO : damage.receivedPortions());
        row.setPorkReturnWeightKg(returns == null ? BigDecimal.ZERO : nz(returns.porkWeight()));
        row.setVegReturnPortionCount(returns == null ? BigDecimal.ZERO : nz(returns.vegPortions()));
        row.setVegReturnKgWeight(returns == null ? BigDecimal.ZERO : nz(returns.vegKgWeight()));
    }

    private static Map<Long, BigDecimal> quantities(List<StoreQuantity> rows) {
        return rows.stream().collect(Collectors.toMap(StoreQuantity::storeId, row -> nz(row.quantity())));
    }

    private static String currentTenant() {
        String tenantId = TenantHelper.getTenantId();
        return tenantId == null ? DjsJobRunner.DEFAULT_TENANT : tenantId;
    }

    @Override
    public TableDataInfo<StoreLossRecordVo> queryPageList(StoreLossQuery query, PageQuery pageQuery) {
        // 门店维度由 StoreLineHandler 行级注入（Current-Store-Id 头），不在此显式过滤。
        // 损耗为 0 的历史行不显示（写入端已不落盘新 0 行，此处隐藏历史已落盘的 0 行）；盘盈负值仍展示。
        LambdaQueryWrapper<StoreLossRecord> w = new LambdaQueryWrapper<StoreLossRecord>()
            .ne(StoreLossRecord::getLossQty, BigDecimal.ZERO)
            .eq(StringUtils.isNotBlank(query.getLossType()), StoreLossRecord::getLossType, query.getLossType())
            .ge(query.getLossDateFrom() != null, StoreLossRecord::getLossDate, query.getLossDateFrom())
            .le(query.getLossDateTo() != null, StoreLossRecord::getLossDate, query.getLossDateTo());

        // 产品名称模糊 + 产品类型多选：两者都不在损耗表上，一次产品主数据查询预查出命中的产品雪花 id 集，
        // 下推到 SQL 分页前，保证 total / 翻页语义正确。
        String kw = StringUtils.trimToNull(query.getProductName());
        List<String> belongTypes = query.getBelongTypes() == null ? List.of()
            : query.getBelongTypes().stream().filter(StringUtils::isNotBlank).distinct().toList();
        if (kw != null || !belongTypes.isEmpty()) {
            LambdaQueryWrapper<ProductInfo> pw = new LambdaQueryWrapper<ProductInfo>()
                .like(kw != null, ProductInfo::getProductName, kw)
                .select(ProductInfo::getId);
            if (!belongTypes.isEmpty()) {
                if (belongTypes.contains(BELONG_TYPE_OTHER)) {
                    // 「其他」一并命中 belong_type IS NULL（外购商品业态恒空），与 fillNames 的回落口径一致
                    pw.and(q -> q.in(ProductInfo::getBelongType, belongTypes).or().isNull(ProductInfo::getBelongType));
                } else {
                    pw.in(ProductInfo::getBelongType, belongTypes);
                }
            }
            Set<Long> matchedIds = productInfoMapper.selectList(pw)
                .stream().map(ProductInfo::getId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
            // 白条分割损耗哨兵行（product_id=0，无真实产品）：名称门（关键字命中中文占位：字典 label / 兜底文案）
            // 与类型门（选中「白条产品」）都过才纳入。
            boolean nameGate = kw == null || resolveWhiteBarSplitLabel().contains(kw);
            boolean typeGate = belongTypes.isEmpty() || belongTypes.contains(PRODUCT_TYPE_WHITE_BAR);
            if (nameGate && typeGate) {
                matchedIds.add(SENTINEL_PRODUCT_ID);
            }
            if (matchedIds.isEmpty()) {
                // 无命中产品：恒假条件返回空页（不生成空 IN ()）
                w.eq(StoreLossRecord::getProductId, -1L);
            } else {
                w.in(StoreLossRecord::getProductId, matchedIds);
            }
        }
        w.orderByDesc(StoreLossRecord::getLossDate)
            .orderByDesc(StoreLossRecord::getId);

        IPage<StoreLossRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), w);
        fillNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public WhiteBarSplitLossVo getWhiteBarSplitLoss(Long storeId, LocalDate date) {
        WhiteBarSplitLossVo vo = new WhiteBarSplitLossVo();
        vo.setArriveWeight(BigDecimal.ZERO);
        vo.setSplitTotalWeight(BigDecimal.ZERO);
        vo.setSplitLoss(BigDecimal.ZERO);
        if (storeId == null) {
            return vo;
        }
        LocalDate d = date == null ? LocalDate.now(ZONE_SHANGHAI) : date;
        BigDecimal arrive = nz(quantities(indicatorMapper.selectWhiteBarReceipts(currentTenant(), d, storeId))
            .get(storeId));
        vo.setArriveWeight(arrive);
        if (arrive.signum() <= 0) {
            return vo;   // 无白条到店：分割/损耗均 0，前端据 arriveWeight=0 隐藏
        }
        // 白条分割产品总重 = 白条在门店分割下来的产品总重（各白条部位入库量之和 − 材料外售同原材料成品到店重）。
        Set<Long> cutProductIds = resolveWhiteBarReturnProductIds(currentTenant());
        BigDecimal materialSold = nz(materialSoldReceipts(d, storeId, cutProductIds).get(storeId));
        BigDecimal splitTotal = sumWhiteBarSplitWeight(storeId, d, cutProductIds, materialSold);
        vo.setSplitTotalWeight(splitTotal);
        // 白条分割损耗 = max(0, 白条到店重 − 白条分割产品总重)。
        vo.setSplitLoss(splitLoss(arrive, splitTotal));
        return vo;
    }

    /**
     * 回填 productName / productUnit / belongType：门店日损耗行 LEFT JOIN {@code t_warehouse_product_info}；
     * 白条分割损耗汇总行（productId=哨兵 0）productName 取损耗类型中文（{@code djs_store_loss_type}）占位、
     * belongType 固定 {@code white_bar}（该行本就是白条口径）。
     */
    private void fillNames(List<StoreLossRecordVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> realProductIds = list.stream()
            .map(StoreLossRecordVo::getProductId)
            .filter(id -> id != null && id != SENTINEL_PRODUCT_ID)
            .distinct().toList();
        Map<Long, ProductInfo> products = realProductIds.isEmpty()
            ? Map.of()
            : productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                    .in(ProductInfo::getId, realProductIds)
                    .select(ProductInfo::getId, ProductInfo::getProductName, ProductInfo::getProductUnit,
                        ProductInfo::getBelongType))
                .stream().collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));
        String whiteBarSplitLabel = resolveWhiteBarSplitLabel();

        for (StoreLossRecordVo vo : list) {
            if (vo.getProductId() != null && vo.getProductId() != SENTINEL_PRODUCT_ID) {
                ProductInfo p = products.get(vo.getProductId());
                if (p != null) {
                    vo.setProductName(p.getProductName());
                    // 外购商品 belong_type 恒空 → 回落「其他」，与筛选口径一致
                    vo.setBelongType(StringUtils.isNotBlank(p.getBelongType()) ? p.getBelongType() : BELONG_TYPE_OTHER);
                    if (StringUtils.isBlank(vo.getProductUnit())) {
                        vo.setProductUnit(p.getProductUnit());
                    }
                }
            } else {
                // 白条分割损耗汇总行：无真实产品，用类型中文占位。
                vo.setProductName(whiteBarSplitLabel);
                vo.setBelongType(PRODUCT_TYPE_WHITE_BAR);
            }
        }
    }

    /** 白条分割损耗汇总行 productName 占位：损耗类型字典 {@code djs_store_loss_type} 中文 label，字典缺失兜底固定文案。 */
    private String resolveWhiteBarSplitLabel() {
        Map<String, String> lossTypeDict = dictService.getAllDictByDictType(DICT_STORE_LOSS_TYPE);
        String label = lossTypeDict == null ? null : lossTypeDict.get(LOSS_TYPE_WHITE_BAR_SPLIT);
        return StringUtils.isNotBlank(label) ? label : WHITE_BAR_SPLIT_FALLBACK_LABEL;
    }

    /**
     * 门店日损耗：读 {@code t_store_daily_ledger} 当日各行 {@code loss_qty} 逐产品搬入（原样含负）。
     * 产品单位 join {@code t_warehouse_product_info}；产品已删则 {@code product_unit} 留空、损耗照搬。
     *
     * @return 落盘行数
     */
    private int aggregateStoreDailyLoss(LocalDate date, List<StoreDailyLedger> ledgers) {
        if (ledgers.isEmpty()) {
            return 0;
        }
        Map<Long, String> unitByProduct = resolveProductUnits(ledgers.stream()
            .map(StoreDailyLedger::getProductId).filter(Objects::nonNull).distinct().toList());
        int rows = 0;
        for (StoreDailyLedger ledger : ledgers) {
            BigDecimal loss = nz(ledger.getLossQty());
            if (loss.signum() == 0) {
                continue;   // 损耗为 0 不落盘（不记录不显示）；盘盈负值仍原样搬
            }
            StoreLossRecord record = new StoreLossRecord();
            record.setStoreId(ledger.getStoreId());
            record.setProductId(ledger.getProductId());
            record.setProductUnit(ledger.getProductId() == null ? null : unitByProduct.get(ledger.getProductId()));
            record.setLossQty(loss);   // 原样搬，含负（盘盈不钳 0）
            record.setLossDate(date);
            record.setLossType(LOSS_TYPE_STORE_DAILY);
            baseMapper.insert(record);
            rows++;
        }
        return rows;
    }

    private void writeWhiteBarSplitLoss(LocalDate date, Long storeId, BigDecimal arrive,
                                        BigDecimal split, BigDecimal loss) {
        StoreLossRecord record = new StoreLossRecord();
        record.setStoreId(storeId);
        record.setProductId(SENTINEL_PRODUCT_ID);
        record.setProductUnit(UNIT_KG);
        record.setLossQty(loss);
        record.setLossDate(date);
        record.setLossType(LOSS_TYPE_WHITE_BAR_SPLIT);
        record.setWhiteBarArriveWeight(arrive);
        record.setWhiteBarSplitWeight(split);
        baseMapper.insert(record);
    }

    private static BigDecimal splitLoss(BigDecimal arrive, BigDecimal splitWeight) {
        return arrive.subtract(splitWeight).max(BigDecimal.ZERO);
    }

    /**
     * 指标 7：当日该店「白条分割产品总重」kg = max(0, 各白条部位当日入库量之和 − 材料外售同原材料成品当日到店重)。
     *
     * <p>材料外售成品由白条分割成原材料后再外售，其到店重已随成品记在盘点入库里，属于原材料的外卖去向、
     * 不是白条在门店分割留下的产品，故从入库项中剔除（客户口径：白条到店重 + 外卖产品重 − Σ原材料产品重 = 损耗）。
     * 钳 0 避免材料外售到店重大于入库项时列表出负数。</p>
     */
    private BigDecimal sumWhiteBarSplitWeight(Long storeId, LocalDate date, Set<Long> cutProductIds,
                                               BigDecimal materialSoldWeight) {
        return sumStoreSplitProductWeight(storeId, date, cutProductIds)
            .subtract(materialSoldWeight).max(BigDecimal.ZERO);
    }

    private Map<Long, BigDecimal> materialSoldReceipts(LocalDate date, Long storeId, Set<Long> cutProductIds) {
        return cutProductIds.isEmpty() ? Map.of()
            : quantities(indicatorMapper.selectMaterialSoldReceipts(currentTenant(), date, storeId, cutProductIds));
    }

    /**
     * 当日该店各白条部位入库量之和 kg（白条分割产品总重的入库项）：
     * 取当日该店盘点台账（{@code t_store_daily_ledger}）中 productId ∈ 白条部位字典产品集的行，
     * 累加其当日入库量（{@code inbound_qty}，门店对白条分割出的各部位实称入库）。
     * 无门店 / 空字典 → 0，不抛。
     */
    private BigDecimal sumStoreSplitProductWeight(Long storeId, LocalDate date, Set<Long> cutProductIds) {
        if (storeId == null || cutProductIds == null || cutProductIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<StoreDailyLedger> ledgers = storeDailyLedgerMapper.selectList(
            new LambdaQueryWrapper<StoreDailyLedger>()
                .eq(StoreDailyLedger::getStoreId, storeId)
                .eq(StoreDailyLedger::getLedgerDate, date)
                .in(StoreDailyLedger::getProductId, cutProductIds)
                .select(StoreDailyLedger::getInboundQty));
        BigDecimal sum = BigDecimal.ZERO;
        for (StoreDailyLedger l : ledgers) {
            sum = sum.add(nz(l.getInboundQty()));
        }
        return sum;
    }

    /**
     * 白条部位字典 {@code djs_white_bar_return_product} 的 value（产品业务码 product_id VARCHAR）→ 雪花主键。
     * 空字典 / 无匹配产品 → 空（白条分割产品总重记 0），不抛。
     */
    private Set<Long> resolveWhiteBarReturnProductIds(String tenantId) {
        Map<String, String> dict = dictService.getAllDictByDictType(DICT_WHITE_BAR_RETURN_PRODUCT);
        if (dict == null || dict.isEmpty()) {
            log.warn("[STORE-LOSS] 字典 {} 为空，白条分割产品总重记 0", DICT_WHITE_BAR_RETURN_PRODUCT);
            return Set.of();
        }
        List<String> codes = dict.keySet().stream()
            .filter(StringUtils::isNotBlank).distinct().toList();
        if (codes.isEmpty()) {
            return Set.of();
        }
        return new TreeSet<>(indicatorMapper.selectWhiteBarCutProductIds(tenantId, codes));
    }

    /** productId(雪花) → 产品单位（product_unit）；产品已删或无单位则该 key 缺省。 */
    private Map<Long, String> resolveProductUnits(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .in(ProductInfo::getId, productIds)
                .select(ProductInfo::getId, ProductInfo::getProductUnit))
            .forEach(p -> {
                if (p.getId() != null && p.getProductUnit() != null) {
                    result.put(p.getId(), p.getProductUnit());
                }
            });
        return result;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
