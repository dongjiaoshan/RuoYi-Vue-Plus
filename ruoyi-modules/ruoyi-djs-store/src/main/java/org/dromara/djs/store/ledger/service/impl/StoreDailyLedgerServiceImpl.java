package org.dromara.djs.store.ledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
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
import org.dromara.djs.warehouse.demand.core.enums.DemandStatus;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
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
 * 门店经营流水盘点台账 Service 实现（STORE-LEDGER-001）。
 *
 * <h3>口径</h3>
 * <ul>
 *   <li>盘点候选 = 两类产品并集（仅这两类，不再全 SKU）：<br>
 *       ① 昨日（{@code ledgerDate-1}）该门店盘点结存 {@code closingQty>0} 的产品；<br>
 *       ② 该门店已进入「确认收货」状态的需求产品（{@code t_warehouse_demand_manage}
 *          {@code demand_status='CONFIRMED' AND received_time IS NOT NULL}）。<br>
 *       两类都没有则返回空表（门店当日确无可盘产品）。</li>
 *   <li>预填 saleQty（{@code t_store_sale_record} 当日聚合）/ returnQty（{@code t_store_return}
 *       customer_to_store 当日聚合）/ whReturnQty（{@code t_store_return} store_to_warehouse 当日聚合）。
 *       inboundQty V1 不自动预填 0（{@code t_warehouse_shipment} 为 demand/业态粒度，非产品 SKU 粒度，
 *       无法干净 join 到产品；门店端手填，见 _open-issues）。</li>
 *   <li>期末库存落库时算：closing = opening + inbound − sale − gift − return − whReturn − loss（量列缺省 0）。</li>
 * </ul>
 *
 * @author djs
 * @since STORE-LEDGER-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreDailyLedgerServiceImpl implements IStoreDailyLedgerService {

    private final StoreDailyLedgerMapper baseMapper;
    private final StoreMapper storeMapper;
    private final ProductInfoMapper productInfoMapper;
    private final StoreSaleRecordMapper saleRecordMapper;
    private final StoreReturnMapper storeReturnMapper;
    private final DemandManageMapper demandManageMapper;

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

        // 1. 候选产品 = 两类并集（仅这两类，不再门店关联全 SKU）
        //    ① 昨日盘点结存 closingQty>0 的产品；② 已确认收货的需求产品。
        Set<Long> productIdSet = new LinkedHashSet<>();
        productIdSet.addAll(yesterdayPositiveClosingProductIds(storeId, date));
        productIdSet.addAll(confirmedReceivedDemandProductIds(storeId));
        List<Long> productIds = new ArrayList<>(productIdSet);
        if (productIds.isEmpty()) {
            return List.of();
        }

        Map<Long, ProductInfo> productMap = productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>().in(ProductInfo::getId, productIds))
            .stream().collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));

        // 2. 预填：销售量（当日 sale_record 聚合）+ 退货量 / 退回量（当日 store_return 按方向聚合）
        Map<Long, BigDecimal> saleMap = sumSaleByProduct(storeId, date, productIds);
        Map<Long, BigDecimal> returnMap = sumReturnByProduct(storeId, date, productIds, "customer_to_store");
        Map<Long, BigDecimal> whReturnMap = sumReturnByProduct(storeId, date, productIds, "store_to_warehouse");

        List<StoreDailyLedgerCandidateVo> result = new ArrayList<>();
        for (Long pid : productIds) {
            ProductInfo p = productMap.get(pid);
            if (p == null) {
                continue;
            }
            StoreDailyLedgerCandidateVo vo = new StoreDailyLedgerCandidateVo();
            vo.setProductId(pid);
            vo.setProductName(p.getProductName());
            vo.setProductUnit(p.getProductUnit());
            vo.setProductSpec(p.getProductSpec());
            vo.setSaleQty(saleMap.getOrDefault(pid, BigDecimal.ZERO));
            vo.setReturnQty(returnMap.getOrDefault(pid, BigDecimal.ZERO));
            vo.setWhReturnQty(whReturnMap.getOrDefault(pid, BigDecimal.ZERO));
            vo.setInboundQty(BigDecimal.ZERO);
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

        // 已存在的同门店同日行（产品 → entity），用于 UPSERT（重盘覆盖）
        Map<Long, StoreDailyLedger> existingByProduct = baseMapper.selectList(
                new LambdaQueryWrapper<StoreDailyLedger>()
                    .eq(StoreDailyLedger::getStoreId, bo.getStoreId())
                    .eq(StoreDailyLedger::getLedgerDate, date))
            .stream().collect(Collectors.toMap(StoreDailyLedger::getProductId, e -> e, (a, b) -> a));

        int saved = 0;
        for (StoreDailyLedgerBatchBo.Item item : bo.getItems()) {
            if (productInfoMapper.selectById(item.getProductId()) == null) {
                throw new ServiceException("产品不存在或已删除：" + item.getProductId(), 404);
            }
            BigDecimal opening = nz(item.getOpeningQty());
            BigDecimal inbound = nz(item.getInboundQty());
            BigDecimal sale = nz(item.getSaleQty());
            BigDecimal gift = nz(item.getGiftQty());
            BigDecimal ret = nz(item.getReturnQty());
            BigDecimal whRet = nz(item.getWhReturnQty());
            BigDecimal loss = nz(item.getLossQty());
            BigDecimal closing = opening.add(inbound)
                .subtract(sale).subtract(gift).subtract(ret).subtract(whRet).subtract(loss);

            StoreDailyLedger existing = existingByProduct.get(item.getProductId());
            StoreDailyLedger entity = new StoreDailyLedger();
            entity.setStoreId(bo.getStoreId());
            entity.setProductId(item.getProductId());
            entity.setLedgerDate(date);
            entity.setOpeningQty(opening);
            entity.setInboundQty(inbound);
            entity.setSaleQty(sale);
            entity.setGiftQty(gift);
            entity.setReturnQty(ret);
            entity.setWhReturnQty(whRet);
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
     * 候选维度①：昨日（{@code date-1}）该门店盘点结存 {@code closingQty>0} 的产品 ID。
     */
    private List<Long> yesterdayPositiveClosingProductIds(Long storeId, LocalDate date) {
        LocalDate yesterday = date.minusDays(1);
        return baseMapper.selectList(
                new LambdaQueryWrapper<StoreDailyLedger>()
                    .eq(StoreDailyLedger::getStoreId, storeId)
                    .eq(StoreDailyLedger::getLedgerDate, yesterday)
                    .gt(StoreDailyLedger::getClosingQty, BigDecimal.ZERO)
                    .select(StoreDailyLedger::getProductId))
            .stream().map(StoreDailyLedger::getProductId).filter(Objects::nonNull).distinct().toList();
    }

    /**
     * 候选维度②：该门店已进入「确认收货」状态的需求对应产品 ID
     * （{@code demand_status='CONFIRMED' AND received_time IS NOT NULL}）。
     */
    private List<Long> confirmedReceivedDemandProductIds(Long storeId) {
        return demandManageMapper.selectList(
                new LambdaQueryWrapper<DemandManage>()
                    .eq(DemandManage::getStoreId, storeId)
                    .eq(DemandManage::getDemandStatus, DemandStatus.CONFIRMED.name())
                    .isNotNull(DemandManage::getReceivedTime)
                    .select(DemandManage::getProductId))
            .stream().map(DemandManage::getProductId).filter(Objects::nonNull).distinct().toList();
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
        for (StoreDailyLedgerVo vo : list) {
            if (vo.getStoreId() != null) {
                vo.setStoreName(storeNames.get(vo.getStoreId()));
            }
            ProductInfo p = vo.getProductId() == null ? null : products.get(vo.getProductId());
            if (p != null) {
                vo.setProductName(p.getProductName());
                vo.setProductUnit(p.getProductUnit());
            }
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
