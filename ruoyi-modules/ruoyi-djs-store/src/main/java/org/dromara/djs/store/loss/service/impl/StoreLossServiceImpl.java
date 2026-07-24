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
import org.dromara.djs.store.ledger.domain.StoreDailyLedger;
import org.dromara.djs.store.ledger.mapper.StoreDailyLedgerMapper;
import org.dromara.djs.store.loss.domain.StoreLossRecord;
import org.dromara.djs.store.loss.domain.query.StoreLossQuery;
import org.dromara.djs.store.loss.domain.vo.StoreLossRecordVo;
import org.dromara.djs.store.loss.domain.vo.WhiteBarSplitLossVo;
import org.dromara.djs.store.loss.mapper.StoreLossRecordMapper;
import org.dromara.djs.store.loss.service.IStoreLossService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.shipment.domain.Shipment;
import org.dromara.djs.warehouse.shipment.mapper.ShipmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 门店损耗记录聚合 Service 实现（DENGBO-R15）。
 *
 * <p>每天凌晨定时任务把 T-1（昨日，已完结）两类损耗落盘到 {@code t_store_loss_record}
 * （幂等：先物理删该日旧行再重插；admin 手动重跑传显式日期）：</p>
 * <ol>
 *   <li><b>门店日损耗</b>（{@code store_daily_loss}）：读 {@code t_store_daily_ledger} 当日各行 {@code loss_qty}
 *       逐产品搬入（join {@code t_warehouse_product_info} 补 {@code product_unit}），
 *       <b>原样搬负值</b>（盘盈也搬，不钳 0）。</li>
 *   <li><b>白条分割损耗</b>（{@code white_bar_split_loss}）：按门店汇总一行（{@code product_id}=哨兵 0），
 *       {@code loss_qty} = max(0, 白条到店重 − 白条分割产品总重)；当天无白条到店（到店重=0）则不记录。
 *       另存 {@code white_bar_arrive_weight}（当日该店 {@code white_bar} 发货 {@code ship_quantity} 之和）
 *       与 {@code white_bar_split_weight}（白条在门店分割下来的产品总重 = 当日该店盘点台账中
 *       {@code djs_white_bar_return_product} 字典各白条部位的 {@code inbound_qty} 入库量之和）。</li>
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

    /** 业态字典值：白条（发货到店重量来源）。 */
    private static final String PRODUCT_TYPE_WHITE_BAR = "white_bar";

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
    private final ShipmentMapper shipmentMapper;
    private final ProductInfoMapper productInfoMapper;
    private final DictService dictService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void aggregate(LocalDate targetDate) {
        // 默认 T-1：定时任务凌晨 1:30 触发，聚合的是已完结的昨日（当日凌晨两数据源必然为空）。
        // admin 手动重跑走 DjsJobRegistry 传显式日期，不受此默认影响。
        LocalDate date = targetDate == null ? LocalDate.now(ZONE_SHANGHAI).minusDays(1) : targetDate;

        // 幂等：先物理删该日旧行再重插。唯一键 (tenant_id, store_id, product_id, loss_date, loss_type)
        // 不含 del_flag，走 @TableLogic 软删会残留旧行占键、重跑重插 Duplicate entry，故物理删该日彻底清空。
        String tenantId = TenantHelper.getTenantId();
        baseMapper.physicalDeleteByDate(tenantId, date);

        int dailyRows = aggregateStoreDailyLoss(date);
        int whiteBarRows = aggregateWhiteBarSplitLoss(date);
        log.info("[STORE-LOSS] aggregate date={} 门店日损耗={}行 白条分割损耗={}行", date, dailyRows, whiteBarRows);
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

        // 产品名称模糊：下推到 SQL 分页前——先按产品名 LIKE 预查产品雪花 id 集；关键字命中白条分割损耗
        // 中文占位（字典 label / 兜底文案）时把哨兵行（product_id=0）一并纳入，保证 total / 翻页语义正确。
        if (StringUtils.isNotBlank(query.getProductName())) {
            String kw = query.getProductName().trim();
            Set<Long> matchedIds = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                    .like(ProductInfo::getProductName, kw)
                    .select(ProductInfo::getId))
                .stream().map(ProductInfo::getId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
            if (resolveWhiteBarSplitLabel().contains(kw)) {
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
        // 到店重：当日该店 white_bar 发货 ship_quantity 之和。
        BigDecimal arrive = nz(sumWhiteBarArriveWeightByStore(d).get(storeId));
        vo.setArriveWeight(arrive);
        if (arrive.signum() <= 0) {
            return vo;   // 无白条到店：分割/损耗均 0，前端据 arriveWeight=0 隐藏
        }
        // 白条分割产品总重 = 白条在门店分割下来的产品总重 = 当日该店盘点各白条部位（白条退回字典产品）入库量之和。
        Set<Long> cutProductIds = resolveWhiteBarReturnProductIds();
        BigDecimal splitTotal = sumStoreSplitProductWeight(storeId, d, cutProductIds);
        vo.setSplitTotalWeight(splitTotal);
        // 白条分割损耗 = max(0, 白条到店重 − 白条分割产品总重)。
        BigDecimal loss = arrive.subtract(splitTotal);
        vo.setSplitLoss(loss.signum() < 0 ? BigDecimal.ZERO : loss);
        return vo;
    }

    /**
     * 回填 productName / productUnit：门店日损耗行 LEFT JOIN {@code t_warehouse_product_info}；
     * 白条分割损耗汇总行（productId=哨兵 0）productName 取损耗类型中文（{@code djs_store_loss_type}）占位。
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
                    .select(ProductInfo::getId, ProductInfo::getProductName, ProductInfo::getProductUnit))
                .stream().collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));
        String whiteBarSplitLabel = resolveWhiteBarSplitLabel();

        for (StoreLossRecordVo vo : list) {
            if (vo.getProductId() != null && vo.getProductId() != SENTINEL_PRODUCT_ID) {
                ProductInfo p = products.get(vo.getProductId());
                if (p != null) {
                    vo.setProductName(p.getProductName());
                    if (StringUtils.isBlank(vo.getProductUnit())) {
                        vo.setProductUnit(p.getProductUnit());
                    }
                }
            } else {
                // 白条分割损耗汇总行：无真实产品，用类型中文占位。
                vo.setProductName(whiteBarSplitLabel);
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
    private int aggregateStoreDailyLoss(LocalDate date) {
        List<StoreDailyLedger> ledgers = storeDailyLedgerMapper.selectList(
            new LambdaQueryWrapper<StoreDailyLedger>()
                .eq(StoreDailyLedger::getLedgerDate, date));
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

    /**
     * 白条分割损耗：按门店汇总一行。门店集 = 当日有白条发货（到店）的门店；
     * {@code 白条分割产品总重 = 当日该店盘点各白条部位入库量之和}（白条在门店分割下来的产品总重）；
     * {@code loss = max(0, 到店重 − 白条分割产品总重)}；到店重=0 的门店不记录。
     *
     * @return 落盘行数
     */
    private int aggregateWhiteBarSplitLoss(LocalDate date) {
        // 当日各店白条到店重量（white_bar 发货 ship_quantity 之和），门店集由此派生。
        Map<Long, BigDecimal> arriveByStore = sumWhiteBarArriveWeightByStore(date);
        if (arriveByStore.isEmpty()) {
            return 0;
        }
        // 白条部位产品集（白条退回字典 product_id 雪花）= 白条在门店分割下来的部位品；空则分割产品总重记 0。
        Set<Long> cutProductIds = resolveWhiteBarReturnProductIds();

        int rows = 0;
        for (Map.Entry<Long, BigDecimal> e : arriveByStore.entrySet()) {
            Long storeId = e.getKey();
            BigDecimal arrive = nz(e.getValue());
            if (arrive.signum() <= 0) {
                continue;   // 到店重 ≤ 0 视为无白条到店，不记录
            }
            // 白条分割产品总重 = 当日该店盘点各白条部位（白条退回字典产品）入库量之和；损耗 = max(0, 到店 − 分割产品总重)。
            BigDecimal splitTotal = sumStoreSplitProductWeight(storeId, date, cutProductIds);
            BigDecimal loss = arrive.subtract(splitTotal);
            if (loss.signum() < 0) {
                loss = BigDecimal.ZERO;   // 钳 0
            }
            StoreLossRecord record = new StoreLossRecord();
            record.setStoreId(storeId);
            record.setProductId(SENTINEL_PRODUCT_ID);
            record.setProductUnit(UNIT_KG);   // 哨兵行无真实产品可回填，单位固定 kg（重量口径）
            record.setLossQty(loss);
            record.setLossDate(date);
            record.setLossType(LOSS_TYPE_WHITE_BAR_SPLIT);
            record.setWhiteBarArriveWeight(arrive);
            record.setWhiteBarSplitWeight(splitTotal);
            baseMapper.insert(record);
            rows++;
        }
        return rows;
    }

    /**
     * 当日各门店白条到店重量：{@code t_warehouse_shipment} shipDate=date + productType=white_bar，
     * 按 storeId 聚合 {@code ship_quantity}（storeId 为空的发货跳过，无门店归属）。
     */
    private Map<Long, BigDecimal> sumWhiteBarArriveWeightByStore(LocalDate date) {
        List<Shipment> shipments = shipmentMapper.selectList(
            new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getShipDate, date)
                .eq(Shipment::getProductType, PRODUCT_TYPE_WHITE_BAR)
                .select(Shipment::getStoreId, Shipment::getShipQuantity));
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (Shipment s : shipments) {
            if (s.getStoreId() == null) {
                continue;
            }
            result.merge(s.getStoreId(), nz(s.getShipQuantity()), BigDecimal::add);
        }
        return result;
    }

    /**
     * 当日该店「白条分割产品总重」kg = 白条在门店分割下来的部位品总重：
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
    private Set<Long> resolveWhiteBarReturnProductIds() {
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
        return productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .in(ProductInfo::getProductId, codes).select(ProductInfo::getId))
            .stream().map(ProductInfo::getId).filter(Objects::nonNull)
            .collect(Collectors.toCollection(TreeSet::new));
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
