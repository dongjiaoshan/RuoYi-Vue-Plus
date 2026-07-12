package org.dromara.djs.store.ledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.store.inventory.domain.StoreInventory;
import org.dromara.djs.store.inventory.mapper.StoreInventoryMapper;
import org.dromara.djs.store.ledger.domain.StoreDailyLedger;
import org.dromara.djs.store.ledger.domain.bo.StoreDailyLedgerBatchBo;
import org.dromara.djs.store.ledger.domain.query.StoreDailyLedgerQuery;
import org.dromara.djs.store.ledger.domain.vo.StoreDailyLedgerCandidateVo;
import org.dromara.djs.store.ledger.domain.vo.StoreDailyLedgerHeaderVo;
import org.dromara.djs.store.ledger.domain.vo.StoreDailyLedgerVo;
import org.dromara.djs.store.ledger.mapper.StoreDailyLedgerMapper;
import org.dromara.djs.store.ledger.service.IStoreDailyLedgerService;
import org.dromara.djs.store.operation.domain.StoreSaleRecord;
import org.dromara.djs.store.operation.mapper.StoreSaleRecordMapper;
import org.dromara.djs.store.returns.domain.StoreReturn;
import org.dromara.djs.store.returns.mapper.StoreReturnMapper;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.shipment.domain.Shipment;
import org.dromara.djs.warehouse.shipment.mapper.ShipmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 门店经营流水盘点台账 Service 实现（STORE-LEDGER-001 / WSA 阶段1 重构）。
 *
 * <h3>盘点候选（{@link #listCandidates}）= 三类产品并集</h3>
 * <ol>
 *   <li><b>猪肉</b>：字典 {@code djs_pork_return_product} 的 value（业务码）resolve 出的产品
 *       （{@link #resolvePorkReturnProductIds}）。入库量手动可编辑，上限 = 当日白条发货重量。</li>
 *   <li><b>新到货</b>：当日发货到该门店的产品（{@code t_warehouse_shipment} ⋈ {@code t_warehouse_demand_manage}
 *       取 productId，排除 white_bar），{@code inboundQty}=发货量、{@code inboundReadonly}=true。</li>
 *   <li><b>昨日库存</b>：{@code t_store_inventory.stock_qty>0} 的产品，{@code openingQty}=结存。</li>
 * </ol>
 * 并集去重；同一产品同时命中「新到货」与「库存」时合并一行（category=stock，保留 inbound 的 inboundQty）。
 * 各行预填 {@code openingQty}（库存表结存，只读）、{@code returnWhQty}（退回模块当日聚合，只读）、
 * {@code saleQty}/{@code returnSaleQty}（流水当日聚合）。
 *
 * <h3>盘点提交（{@link #batchSave}）口径（按 docx 字面）</h3>
 * <ul>
 *   <li>期末 {@code closingQty} 手动入参（实盘录入）；</li>
 *   <li>损耗 service 计算：{@code loss = opening + inbound − sale − gift + returnSale − returnWh − closing}；</li>
 *   <li>猪肉行校验：{@code inboundQty ≤ } 当日白条发货重量（{@link #sumTodayWhiteBarShipWeight}）；</li>
 *   <li>每行 {@code closingQty} UPSERT 进 {@code t_store_inventory.stock_qty}（期末回写、下次期初读）。</li>
 * </ul>
 *
 * <h3>优雅降级</h3>
 * 现网 {@code t_plant_crop_info.related_product} 与果蔬成品 {@code product_material} 多为 NULL（客户未录），
 * 涉及取数处一律空回退 + {@code log.warn}，不抛异常。
 *
 * @author djs
 * @since STORE-LEDGER-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreDailyLedgerServiceImpl implements IStoreDailyLedgerService {

    /** 猪肉产品退回字典：label=产品名 / value=产品业务码（{@code t_warehouse_product_info.product_id} VARCHAR）。 */
    private static final String DICT_PORK_RETURN_PRODUCT = "djs_pork_return_product";
    /** 业态字典值：白条（新到货候选排除，白条作为猪肉入库上限的来源）。 */
    private static final String PRODUCT_TYPE_WHITE_BAR = "white_bar";
    /** 退回方向字典值。 */
    private static final String DIRECTION_CUSTOMER_TO_STORE = "customer_to_store";
    private static final String DIRECTION_STORE_TO_WAREHOUSE = "store_to_warehouse";

    private static final String CATEGORY_PORK = "pork";
    private static final String CATEGORY_INBOUND = "inbound";
    private static final String CATEGORY_STOCK = "stock";

    /** 产品品类页签（DENGBO-R10）：猪肉 / 果蔬 / 其他。 */
    private static final String TAB_PORK = "pork";
    private static final String TAB_VEG = "veg";
    private static final String TAB_OTHER = "other";
    /** {@code t_warehouse_product_info.belong_type} 字典值。 */
    private static final String BELONG_PORK = "pork";
    private static final String BELONG_VEGETABLE = "vegetable";

    private final StoreDailyLedgerMapper baseMapper;
    private final StoreMapper storeMapper;
    private final ProductInfoMapper productInfoMapper;
    private final StoreSaleRecordMapper saleRecordMapper;
    private final StoreReturnMapper storeReturnMapper;
    private final ShipmentMapper shipmentMapper;
    private final DemandManageMapper demandManageMapper;
    private final StoreInventoryMapper storeInventoryMapper;
    private final DictService dictService;

    @Override
    public TableDataInfo<StoreDailyLedgerHeaderVo> queryHeaderPage(StoreDailyLedgerQuery query, PageQuery pageQuery) {
        // 台账行级查询后内存按 (storeId, ledgerDate) 分组成盘点单。盘点单量级小（门店数 × 盘点日），全量取再假分页。
        List<StoreDailyLedgerVo> rows = baseMapper.selectVoList(buildQueryWrapper(query));
        List<StoreDailyLedgerHeaderVo> headers = groupToHeaders(rows);
        fillHeaderNames(headers);

        int total = headers.size();
        int pageNum = pageQuery.getPageNum() == null ? PageQuery.DEFAULT_PAGE_NUM : pageQuery.getPageNum();
        int pageSize = pageQuery.getPageSize() == null ? PageQuery.DEFAULT_PAGE_SIZE : pageQuery.getPageSize();
        int from = Math.min(Math.max(pageNum - 1, 0) * pageSize, total);
        int to = (int) Math.min((long) from + pageSize, total);
        List<StoreDailyLedgerHeaderVo> pageRows = new ArrayList<>(headers.subList(from, to));
        return new TableDataInfo<>(pageRows, total);
    }

    @Override
    public List<StoreDailyLedgerCandidateVo> listCandidates(Long storeId, LocalDate ledgerDate) {
        if (storeId == null) {
            throw new ServiceException("门店不能为空", 400);
        }
        if (storeMapper.selectById(storeId) == null) {
            throw new ServiceException("门店不存在或已删除：" + storeId, 404);
        }
        LocalDate date = ledgerDate == null ? LocalDate.now() : ledgerDate;

        // 三类候选并集（保留首次命中类别；inbound 与 stock 合并时 category=stock，inbound 量后面补）。
        List<Long> porkIds = resolvePorkReturnProductIds();
        Set<Long> porkIdSetForTab = new LinkedHashSet<>(porkIds);
        Map<Long, BigDecimal> inboundMap = selectStoreShippedProducts(storeId, date);    // 新到货：productId → 当日到店份数（需求量 demand_quantity，排 white_bar）
        Map<Long, BigDecimal> stockMap = selectPositiveStockByProduct(storeId);          // 昨日库存：productId → 结存（>0）

        // 类别归属：优先级 pork > stock > inbound（库存优先于新到货以保留期初）。
        Map<Long, String> categoryByProduct = new LinkedHashMap<>();
        for (Long pid : porkIds) {
            categoryByProduct.put(pid, CATEGORY_PORK);
        }
        for (Long pid : stockMap.keySet()) {
            categoryByProduct.putIfAbsent(pid, CATEGORY_STOCK);
        }
        for (Long pid : inboundMap.keySet()) {
            categoryByProduct.putIfAbsent(pid, CATEGORY_INBOUND);
        }

        Set<Long> productIdSet = new LinkedHashSet<>(categoryByProduct.keySet());
        List<Long> productIds = new ArrayList<>(productIdSet);
        if (productIds.isEmpty()) {
            return List.of();
        }

        Map<Long, ProductInfo> productMap = productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>().in(ProductInfo::getId, productIds))
            .stream().collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));

        // 预填：销售量 / 退货量(顾客) / 退回量(门店退仓库) 当日聚合 + 期初(库存结存)。
        Map<Long, BigDecimal> saleMap = sumSaleByProduct(storeId, date, productIds);
        Map<Long, BigDecimal> returnSaleMap = sumReturnByProduct(storeId, date, productIds, DIRECTION_CUSTOMER_TO_STORE);
        Map<Long, BigDecimal> returnWhMap = sumReturnByProduct(storeId, date, productIds, DIRECTION_STORE_TO_WAREHOUSE);

        List<StoreDailyLedgerCandidateVo> result = new ArrayList<>();
        for (Long pid : productIds) {
            ProductInfo p = productMap.get(pid);
            if (p == null) {
                // 字典/库存里有但产品已删：优雅降级，跳过该行（不抛）。
                log.warn("[STORE-LEDGER] 盘点候选产品已删除或不存在，跳过 productId={}", pid);
                continue;
            }
            String category = categoryByProduct.getOrDefault(pid, CATEGORY_STOCK);
            boolean pork = CATEGORY_PORK.equals(category);
            BigDecimal inbound = nz(inboundMap.get(pid));   // 新到货发货量（pork/stock 行也可能恰有发货，预填为参考）

            StoreDailyLedgerCandidateVo vo = new StoreDailyLedgerCandidateVo();
            vo.setProductId(pid);
            vo.setProductName(p.getProductName());
            vo.setProductUnit(p.getProductUnit());
            vo.setProductSpec(p.getProductSpec());
            vo.setCategory(category);
            vo.setBelongTab(resolveBelongTab(p, porkIdSetForTab));
            vo.setOpeningQty(nz(stockMap.get(pid)));
            vo.setInboundQty(inbound);
            // 猪肉行入库手动可编辑（有上限）；其余（新到货/库存）入库为发货量只读。
            vo.setInboundReadonly(!pork);
            vo.setSaleQty(saleMap.getOrDefault(pid, BigDecimal.ZERO));
            vo.setReturnSaleQty(returnSaleMap.getOrDefault(pid, BigDecimal.ZERO));
            vo.setReturnWhQty(returnWhMap.getOrDefault(pid, BigDecimal.ZERO));
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchSave(StoreDailyLedgerBatchBo bo) {
        if (storeMapper.selectById(bo.getStoreId()) == null) {
            throw new ServiceException("门店不存在或已删除：" + bo.getStoreId(), 404);
        }
        LocalDate date = bo.getLedgerDate() == null ? LocalDate.now() : bo.getLedgerDate();
        Long operatorId = LoginHelper.getUserId();

        // 已存在的同门店同日行（产品 → entity），用于 UPSERT（重盘覆盖）。
        Map<Long, StoreDailyLedger> existingByProduct = baseMapper.selectList(
                new LambdaQueryWrapper<StoreDailyLedger>()
                    .eq(StoreDailyLedger::getStoreId, bo.getStoreId())
                    .eq(StoreDailyLedger::getLedgerDate, date))
            .stream().collect(Collectors.toMap(StoreDailyLedger::getProductId, e -> e, (a, b) -> a));

        // 猪肉入库上限 = 当日白条发货重量（一次取，所有猪肉行共用同一上限）。
        // 按本批所有猪肉行【累计】校验：多个猪肉成品行各自的 inbound 之和不得超白条总重，
        // 否则逐行各比完整上限可被多行绕过（5 行各录满 limit → 累计 5×limit 全过）。
        Set<Long> porkProductIdSet = new LinkedHashSet<>(resolvePorkReturnProductIds());
        BigDecimal whiteBarLimit = sumTodayWhiteBarShipWeight(bo.getStoreId(), date);
        BigDecimal porkInboundSum = BigDecimal.ZERO;

        int saved = 0;
        for (StoreDailyLedgerBatchBo.Item item : bo.getItems()) {
            if (productInfoMapper.selectById(item.getProductId()) == null) {
                throw new ServiceException("产品不存在或已删除：" + item.getProductId(), 404);
            }
            BigDecimal opening = nz(item.getOpeningQty());
            BigDecimal inbound = nz(item.getInboundQty());
            BigDecimal sale = nz(item.getSaleQty());
            BigDecimal gift = nz(item.getGiftQty());
            BigDecimal returnSale = nz(item.getReturnSaleQty());
            BigDecimal returnWh = nz(item.getReturnWhQty());
            BigDecimal closing = nz(item.getClosingQty());

            // 猪肉行入库上限校验（累计本批所有猪肉行，不逐行各比完整上限）。
            if (porkProductIdSet.contains(item.getProductId())) {
                porkInboundSum = porkInboundSum.add(inbound);
                if (porkInboundSum.compareTo(whiteBarLimit) > 0) {
                    throw new ServiceException(
                        "本批猪肉到货合计(" + porkInboundSum.toPlainString() + ")不能超过当日白条发货重量("
                            + whiteBarLimit.toPlainString() + ")", 400);
                }
            }

            // 损耗按 docx 字面反算：loss = 期初 + 入库 − 销售 − 赠送 + 退货 − 退回 − 期末。
            BigDecimal loss = opening.add(inbound)
                .subtract(sale).subtract(gift)
                .add(returnSale).subtract(returnWh)
                .subtract(closing);

            StoreDailyLedger existing = existingByProduct.get(item.getProductId());
            StoreDailyLedger entity = new StoreDailyLedger();
            entity.setStoreId(bo.getStoreId());
            entity.setProductId(item.getProductId());
            entity.setLedgerDate(date);
            entity.setOpeningQty(opening);
            entity.setInboundQty(inbound);
            entity.setSaleQty(sale);
            entity.setGiftQty(gift);
            entity.setReturnQty(returnSale);
            entity.setWhReturnQty(returnWh);
            entity.setLossQty(loss);
            entity.setClosingQty(closing);
            entity.setOperatorId(operatorId);
            entity.setRemark(bo.getRemark());
            if (existing == null) {
                baseMapper.insert(entity);
            } else {
                entity.setId(existing.getId());
                baseMapper.updateById(entity);
            }

            // 盘点完成：期末结存 UPSERT 进门店独立库存（下次盘点期初读它）。
            upsertStoreInventory(bo.getStoreId(), item.getProductId(), closing);
            saved++;
        }
        log.info("[STORE-LEDGER-001] batchSave store={} date={} 行数={}", bo.getStoreId(), date, saved);
        return saved;
    }

    @Override
    public List<StoreDailyLedgerVo> queryDetail(Long storeId, LocalDate ledgerDate) {
        if (storeId == null || ledgerDate == null) {
            throw new ServiceException("门店和盘点日期不能为空", 400);
        }
        List<StoreDailyLedgerVo> list = baseMapper.selectVoList(
            new LambdaQueryWrapper<StoreDailyLedger>()
                .eq(StoreDailyLedger::getStoreId, storeId)
                .eq(StoreDailyLedger::getLedgerDate, ledgerDate)
                .orderByAsc(StoreDailyLedger::getProductId));
        fillLineNames(list);
        return list;
    }

    @Override
    public List<StoreDailyLedgerVo> queryHistoryByProduct(StoreDailyLedgerQuery query) {
        if (query == null || query.getProductId() == null) {
            throw new ServiceException("产品不能为空", 400);
        }
        LambdaQueryWrapper<StoreDailyLedger> w = new LambdaQueryWrapper<StoreDailyLedger>()
            .eq(StoreDailyLedger::getProductId, query.getProductId())
            .eq(query.getStoreId() != null, StoreDailyLedger::getStoreId, query.getStoreId())
            .ge(query.getLedgerDateFrom() != null, StoreDailyLedger::getLedgerDate, query.getLedgerDateFrom())
            .le(query.getLedgerDateTo() != null, StoreDailyLedger::getLedgerDate, query.getLedgerDateTo())
            .orderByDesc(StoreDailyLedger::getLedgerDate)
            .orderByDesc(StoreDailyLedger::getId);
        List<StoreDailyLedgerVo> list = baseMapper.selectVoList(w);
        fillLineNames(list);
        return list;
    }

    // ---------- private helpers ----------

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 猪肉候选：字典 {@code djs_pork_return_product} 的 value（产品业务码 VARCHAR）→ 雪花主键。
     *
     * <p>字典 value = {@code t_warehouse_product_info.product_id}（业务码）。空字典 / 无匹配产品 → 空回退 + warn，不抛。</p>
     */
    private List<Long> resolvePorkReturnProductIds() {
        Map<String, String> dict = dictService.getAllDictByDictType(DICT_PORK_RETURN_PRODUCT);
        if (dict == null || dict.isEmpty()) {
            log.warn("[STORE-LEDGER] 字典 {} 为空，猪肉盘点候选回退为空", DICT_PORK_RETURN_PRODUCT);
            return List.of();
        }
        // key=dictValue=产品业务码；过滤空值。
        List<String> codes = dict.keySet().stream()
            .filter(StringUtils::isNotBlank)
            .distinct()
            .toList();
        if (codes.isEmpty()) {
            return List.of();
        }
        List<Long> ids = productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>()
                    .in(ProductInfo::getProductId, codes)
                    .select(ProductInfo::getId))
            .stream().map(ProductInfo::getId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            log.warn("[STORE-LEDGER] 字典 {} 的业务码 {} 未匹配到任何产品，猪肉候选回退为空",
                DICT_PORK_RETURN_PRODUCT, codes);
        }
        return ids;
    }

    /**
     * 新到货候选：当日发货到该门店的产品（{@code t_warehouse_shipment} ⋈ {@code t_warehouse_demand_manage}
     * 取 productId，排除 white_bar），按产品聚合<b>到店份数</b>。
     *
     * <p>DENGBO-R10：盘点按产品单位（份）显示、不用重量。发货 {@code ship_quantity} 存的是重量
     * （果蔬 3 份=0.905kg，且 {@code ship_unit='份'} 与值自相矛盾），需求 {@code demand_quantity} 才是份数。
     * 故到店量取<b>需求订购份数</b>（Kevin 2026-07-12 拍板）：按已发货的 distinct demandId 累加其
     * {@code demand_quantity}，按 productId 归组（一需求一发货、订购=到货，多发货同一需求只计一次避免重复）。</p>
     *
     * <p>shipment 先按 storeId + shipDate + 非 white_bar 过滤；productId / demand_quantity 经 demandId
     * join demand 拿。demand 缺失（脏数据）→ 跳过该 demand + warn，不抛。</p>
     *
     * @return productId(雪花) → 当日到店份数合计（demand_quantity，产品单位）
     */
    private Map<Long, BigDecimal> selectStoreShippedProducts(Long storeId, LocalDate date) {
        List<Shipment> shipments = shipmentMapper.selectList(
            new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getStoreId, storeId)
                .eq(Shipment::getShipDate, date)
                .ne(Shipment::getProductType, PRODUCT_TYPE_WHITE_BAR)
                .select(Shipment::getDemandId, Shipment::getShipmentNo));
        if (shipments.isEmpty()) {
            return Map.of();
        }
        List<Long> demandIds = shipments.stream()
            .map(Shipment::getDemandId).filter(Objects::nonNull).distinct().toList();
        if (demandIds.isEmpty()) {
            log.warn("[STORE-LEDGER] 门店 {} {} 有发货但均无 demandId，新到货候选回退为空", storeId, date);
            return Map.of();
        }
        // 已发货的 distinct 需求：id → (productId, demandQuantity)。
        Map<Long, DemandManage> demandById = demandManageMapper.selectList(
                new LambdaQueryWrapper<DemandManage>()
                    .in(DemandManage::getId, demandIds)
                    .select(DemandManage::getId, DemandManage::getProductId, DemandManage::getDemandQuantity))
            .stream()
            .filter(d -> d.getProductId() != null)
            .collect(Collectors.toMap(DemandManage::getId, d -> d, (a, b) -> a));
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (Long did : demandIds) {   // distinct demandId：订购=到货，同一需求多次发货只计一次份数
            DemandManage d = demandById.get(did);
            if (d == null) {
                log.warn("[STORE-LEDGER] 已发货需求 {} 缺产品或已删，跳过新到货预填", did);
                continue;
            }
            result.merge(d.getProductId(), nz(d.getDemandQuantity()), BigDecimal::add);
        }
        return result;
    }

    /**
     * 产品品类页签（DENGBO-R10）：产品在 {@code djs_pork_return_product} 字典或 {@code belong_type='pork'} → 猪肉；
     * {@code belong_type='vegetable'} → 果蔬；其余（含外购 belong_type=NULL / egg / dry_good / other / gift_box）→ 其他。
     */
    private String resolveBelongTab(ProductInfo p, Set<Long> porkIds) {
        if (p == null) {
            return TAB_OTHER;
        }
        if (porkIds.contains(p.getId()) || BELONG_PORK.equals(p.getBelongType())) {
            return TAB_PORK;
        }
        if (BELONG_VEGETABLE.equals(p.getBelongType())) {
            return TAB_VEG;
        }
        return TAB_OTHER;
    }

    /**
     * 当日白条发货重量合计（猪肉入库上限）：{@code t_warehouse_shipment} storeId + shipDate + white_bar 的 shipQuantity 之和。
     */
    private BigDecimal sumTodayWhiteBarShipWeight(Long storeId, LocalDate date) {
        List<Shipment> shipments = shipmentMapper.selectList(
            new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getStoreId, storeId)
                .eq(Shipment::getShipDate, date)
                .eq(Shipment::getProductType, PRODUCT_TYPE_WHITE_BAR)
                .select(Shipment::getShipQuantity));
        return shipments.stream().map(s -> nz(s.getShipQuantity())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 昨日库存候选：门店独立库存 {@code stock_qty>0} 的产品。 */
    private Map<Long, BigDecimal> selectPositiveStockByProduct(Long storeId) {
        return storeInventoryMapper.selectList(
                new LambdaQueryWrapper<StoreInventory>()
                    .eq(StoreInventory::getStoreId, storeId)
                    .gt(StoreInventory::getStockQty, BigDecimal.ZERO))
            .stream()
            .filter(inv -> inv.getProductId() != null)
            .collect(Collectors.toMap(
                StoreInventory::getProductId,
                inv -> nz(inv.getStockQty()),
                BigDecimal::add,
                LinkedHashMap::new));
    }

    /** 期末结存 UPSERT 进门店独立库存（按 storeId + productId 找现有行；无则插入）。 */
    private void upsertStoreInventory(Long storeId, Long productId, BigDecimal closingQty) {
        StoreInventory existing = storeInventoryMapper.selectOne(
            new LambdaQueryWrapper<StoreInventory>()
                .eq(StoreInventory::getStoreId, storeId)
                .eq(StoreInventory::getProductId, productId)
                .last("LIMIT 1"));
        if (existing == null) {
            StoreInventory inv = new StoreInventory();
            inv.setStoreId(storeId);
            inv.setProductId(productId);
            inv.setStockQty(nz(closingQty));
            storeInventoryMapper.insert(inv);
        } else {
            existing.setStockQty(nz(closingQty));
            storeInventoryMapper.updateById(existing);
        }
    }

    private LambdaQueryWrapper<StoreDailyLedger> buildQueryWrapper(StoreDailyLedgerQuery q) {
        LambdaQueryWrapper<StoreDailyLedger> w = new LambdaQueryWrapper<>();
        if (q != null) {
            w.eq(q.getStoreId() != null, StoreDailyLedger::getStoreId, q.getStoreId())
                .eq(q.getProductId() != null, StoreDailyLedger::getProductId, q.getProductId())
                .eq(q.getLedgerDate() != null, StoreDailyLedger::getLedgerDate, q.getLedgerDate())
                .ge(q.getLedgerDateFrom() != null, StoreDailyLedger::getLedgerDate, q.getLedgerDateFrom())
                .le(q.getLedgerDateTo() != null, StoreDailyLedger::getLedgerDate, q.getLedgerDateTo());
        }
        w.orderByDesc(StoreDailyLedger::getLedgerDate).orderByDesc(StoreDailyLedger::getId);
        return w;
    }

    /** 台账行按 (storeId, ledgerDate) 分组成盘点单表头（行数 / 最早创建时间 / 盘点人）。 */
    private List<StoreDailyLedgerHeaderVo> groupToHeaders(List<StoreDailyLedgerVo> rows) {
        Map<String, StoreDailyLedgerHeaderVo> grouped = new LinkedHashMap<>();
        for (StoreDailyLedgerVo r : rows) {
            String key = r.getStoreId() + "_" + r.getLedgerDate();
            StoreDailyLedgerHeaderVo h = grouped.computeIfAbsent(key, k -> {
                StoreDailyLedgerHeaderVo nh = new StoreDailyLedgerHeaderVo();
                nh.setStoreId(r.getStoreId());
                nh.setLedgerDate(r.getLedgerDate());
                nh.setLineCount(0);
                nh.setOperatorId(r.getOperatorId());
                nh.setCheckTime(r.getCreateTime());
                return nh;
            });
            h.setLineCount(h.getLineCount() + 1);
            // 盘点时间取该组最早创建时间
            if (r.getCreateTime() != null
                && (h.getCheckTime() == null || r.getCreateTime().isBefore(h.getCheckTime()))) {
                h.setCheckTime(r.getCreateTime());
            }
            if (h.getOperatorId() == null) {
                h.setOperatorId(r.getOperatorId());
            }
        }
        return grouped.values().stream()
            .sorted(Comparator.comparing(StoreDailyLedgerHeaderVo::getLedgerDate,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());
    }

    private void fillHeaderNames(List<StoreDailyLedgerHeaderVo> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        Map<Long, String> storeNames = storeNameMap(headers.stream()
            .map(StoreDailyLedgerHeaderVo::getStoreId).filter(Objects::nonNull).distinct().toList());
        for (StoreDailyLedgerHeaderVo h : headers) {
            if (h.getStoreId() != null) {
                h.setStoreName(storeNames.get(h.getStoreId()));
            }
            // operatorName 由 @Translation(USER_ID_TO_NICKNAME) 序列化时回填
        }
    }

    private void fillLineNames(List<StoreDailyLedgerVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<Long, String> storeNames = storeNameMap(list.stream()
            .map(StoreDailyLedgerVo::getStoreId).filter(Objects::nonNull).distinct().toList());
        Map<Long, ProductInfo> products = productMap(list.stream()
            .map(StoreDailyLedgerVo::getProductId).filter(Objects::nonNull).distinct().toList());
        // 详情按品类分 TAB（DENGBO-R10）：同候选口径（猪肉字典 ∪ belong_type=pork / vegetable / 其他）。
        Set<Long> porkIdSetForTab = new LinkedHashSet<>(resolvePorkReturnProductIds());
        for (StoreDailyLedgerVo vo : list) {
            if (vo.getStoreId() != null) {
                vo.setStoreName(storeNames.get(vo.getStoreId()));
            }
            ProductInfo p = vo.getProductId() == null ? null : products.get(vo.getProductId());
            if (p != null) {
                vo.setProductName(p.getProductName());
                vo.setProductUnit(p.getProductUnit());
            }
            vo.setBelongTab(resolveBelongTab(p, porkIdSetForTab));
        }
    }

    private Map<Long, String> storeNameMap(List<Long> storeIds) {
        if (storeIds.isEmpty()) {
            return Map.of();
        }
        return storeMapper.selectList(new LambdaQueryWrapper<Store>().in(Store::getId, storeIds))
            .stream().collect(Collectors.toMap(Store::getId, Store::getStoreName, (a, b) -> a));
    }

    private Map<Long, ProductInfo> productMap(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>().in(ProductInfo::getId, productIds))
            .stream().collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));
    }

    private Map<Long, BigDecimal> sumSaleByProduct(Long storeId, LocalDate date, List<Long> productIds) {
        // sale_date 为 java.util.Date（到天）。当日聚合：[date, date+1)。
        List<StoreSaleRecord> records = saleRecordMapper.selectList(
            new LambdaQueryWrapper<StoreSaleRecord>()
                .eq(StoreSaleRecord::getStoreId, storeId)
                .in(StoreSaleRecord::getProductId, productIds)
                .apply("sale_date >= {0} AND sale_date < {1}", date.toString(), date.plusDays(1).toString()));
        return records.stream().collect(Collectors.toMap(
            StoreSaleRecord::getProductId,
            r -> nz(r.getSaleQty()),
            BigDecimal::add));
    }

    /**
     * 按退回方向当日聚合退回数量（按产品）。
     *
     * @param direction {@code djs_return_direction} 字典值：customer_to_store=退货量 / store_to_warehouse=退回量
     */
    private Map<Long, BigDecimal> sumReturnByProduct(Long storeId, LocalDate date, List<Long> productIds, String direction) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        List<StoreReturn> records = storeReturnMapper.selectList(
            new LambdaQueryWrapper<StoreReturn>()
                .eq(StoreReturn::getStoreId, storeId)
                .eq(StoreReturn::getReturnDirection, direction)
                .in(StoreReturn::getProductId, productIds)
                .ge(StoreReturn::getReturnDate, from)
                .lt(StoreReturn::getReturnDate, to));
        return records.stream().collect(Collectors.toMap(
            StoreReturn::getProductId,
            r -> nz(r.getReturnQuantity()),
            BigDecimal::add));
    }
}
