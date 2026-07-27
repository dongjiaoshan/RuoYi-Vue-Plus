package org.dromara.djs.warehouse.demand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.domain.DemandPig;
import org.dromara.djs.warehouse.demand.domain.vo.DispatchHomeVo;
import org.dromara.djs.warehouse.demand.domain.vo.DispatchListItemVo;
import org.dromara.djs.warehouse.demand.domain.vo.DispatchStatsVo;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.demand.mapper.DemandPigMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@link org.dromara.djs.warehouse.demand.service.IAppletDemandService} 默认实现（WMS-DEMAND-002）。
 *
 * <p>纯只读聚合：直接走 {@link DemandManageMapper} / {@link StoreMapper} / {@link DemandPigMapper}
 * count + enrich。状态推进/指定猪只复用 admin service（在 controller 层注入，不进本类）。</p>
 *
 * @author djs
 * @since WMS-DEMAND-002
 */
@Service
@RequiredArgsConstructor
public class AppletDemandServiceImpl implements org.dromara.djs.warehouse.demand.service.IAppletDemandService {

    /** 已确认（含）后续态——白条/蔬菜「已确认」入口卡 + 「待发货」口径基。 */
    private static final List<String> CONFIRMED_PLUS =
        List.of("CONFIRMED", "IN_PRODUCTION", "PARTIAL_SHIPPED", "COMPLETED");

    /** 待发货态（已确认但未完成）。 */
    private static final List<String> TO_SHIP =
        List.of("CONFIRMED", "IN_PRODUCTION", "PARTIAL_SHIPPED");

    /** 列表可调度态（草稿不调度、已完成/取消不展示）。 */
    private static final List<String> DISPATCHABLE =
        List.of("SUBMITTED", "CONFIRMED", "IN_PRODUCTION", "PARTIAL_SHIPPED");

    private final DemandManageMapper demandManageMapper;

    private final DemandPigMapper demandPigMapper;

    private final StoreMapper storeMapper;

    @Override
    public DispatchHomeVo dispatchHome() {
        LocalDate today = LocalDate.now();
        DispatchHomeVo vo = new DispatchHomeVo();
        vo.setWhiteBarPending((int) countByDateTypeStatus(today, "white_bar", List.of("SUBMITTED")));
        vo.setWhiteBarConfirmed((int) countByDateTypeStatus(today, "white_bar", CONFIRMED_PLUS));
        vo.setVegetablePending((int) countByDateTypeStatus(today, "vegetable", List.of("SUBMITTED")));
        vo.setVegetableConfirmed((int) countByDateTypeStatus(today, "vegetable", CONFIRMED_PLUS));
        vo.setConfirmedCount((int) countByDateTypeStatus(today, null, CONFIRMED_PLUS));
        vo.setPendingCount((int) countByDateTypeStatus(today, null, List.of("SUBMITTED")));
        vo.setToShipCount((int) countByDateTypeStatus(today, null, TO_SHIP));
        return vo;
    }

    @Override
    public List<DispatchListItemVo> dispatchList(String productType) {
        List<DemandManage> rows = demandManageMapper.selectList(
            new LambdaQueryWrapper<DemandManage>()
                .eq(DemandManage::getProductType, productType)
                .in(DemandManage::getDemandStatus, DISPATCHABLE)
                .orderByDesc(DemandManage::getDemandDate)
                .orderByAsc(DemandManage::getStoreId)
                .last("LIMIT 200"));
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        // 批量 enrich storeName（避免 N+1）
        List<Long> storeIds = rows.stream()
            .map(DemandManage::getStoreId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, String> storeNameMap = storeIds.isEmpty()
            ? Collections.emptyMap()
            : storeMapper.selectList(new LambdaQueryWrapper<Store>().in(Store::getId, storeIds))
                .stream()
                .collect(Collectors.toMap(Store::getId, Store::getStoreName, (a, b) -> a));
        // 白条业态批量 enrich 已指定猪只数
        boolean whiteBar = "white_bar".equals(productType);
        Map<Long, Integer> assignedCountMap = whiteBar
            ? countAssignedPigByDemand(rows.stream().map(DemandManage::getId).collect(Collectors.toList()))
            : Collections.emptyMap();
        return rows.stream().map(d -> {
            DispatchListItemVo item = new DispatchListItemVo();
            item.setId(d.getId());
            item.setDemandNo(d.getDemandNo());
            item.setDemandDate(d.getDemandDate());
            item.setStoreId(d.getStoreId());
            item.setStoreName(d.getStoreId() == null ? null : storeNameMap.get(d.getStoreId()));
            item.setProductName(d.getProductName());
            item.setProductSpec(d.getProductSpec());
            item.setProductType(d.getProductType());
            item.setDemandQuantity(d.getDemandQuantity());
            item.setProductUnit(d.getProductUnit());
            item.setWhiteBarHeadFactor(whiteBar ? resolveWhiteBarHeadFactor(d) : null);
            item.setDemandExplain(d.getDemandExplain());
            item.setDemandStatus(d.getDemandStatus());
            item.setExpectedArriveDate(d.getExpectedArriveDate());
            item.setAssignedPigCount(whiteBar ? assignedCountMap.getOrDefault(d.getId(), 0) : 0);
            return item;
        }).collect(Collectors.toList());
    }

    /**
     * 白条订单保存的是用户输入的“份数”，这里仅给调度端返回每份的折算因子。
     * 产品历史数据可能把“半扇”写在名称或规格中，也兼容英文 half、1/2 与 ½。
     */
    static BigDecimal resolveWhiteBarHeadFactor(DemandManage demand) {
        String descriptor = (Objects.toString(demand.getProductName(), "") + " "
            + Objects.toString(demand.getProductSpec(), "")).toLowerCase(Locale.ROOT);
        boolean half = descriptor.contains("半扇")
            || descriptor.contains("半片")
            || descriptor.contains("半只")
            || descriptor.contains("half")
            || descriptor.contains("½")
            || descriptor.matches(".*(^|\\D)1\\s*/\\s*2(\\D|$).*");
        return half ? new BigDecimal("0.5") : BigDecimal.ONE;
    }

    @Override
    public DispatchStatsVo dispatchStats(LocalDate date) {
        LocalDate statDate = date != null ? date : LocalDate.now();
        DispatchStatsVo vo = new DispatchStatsVo();
        vo.setStatDate(statDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        // dashboard 1 车间效能：当日已确认/完成单数
        vo.setConfirmedDemands((int) countByDateTypeStatus(statDate, null, List.of("CONFIRMED")));
        vo.setCompletedDemands((int) countByDateTypeStatus(statDate, null, List.of("COMPLETED")));
        // dashboard 2 进出库：当日 demand 累计发货/确认量 SUM
        List<DemandManage> dayRows = demandManageMapper.selectList(
            new LambdaQueryWrapper<DemandManage>()
                .eq(DemandManage::getDemandDate, statDate));
        BigDecimal shipped = dayRows.stream()
            .map(DemandManage::getShippedCount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal confirmed = dayRows.stream()
            .map(DemandManage::getConfirmedCount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setShippedQuantity(shipped);
        vo.setConfirmedQuantity(confirmed);
        // dashboard 3 损耗 / dashboard 4 退货：V1 占位 0（D14 CROSS-FLOW-003 + return 模块联动回填）
        vo.setLossQuantity(BigDecimal.ZERO);
        vo.setReturnCount(0);
        // dashboard 5 发货：当日待发货单数
        vo.setToShipDemands((int) countByDateTypeStatus(statDate, null, TO_SHIP));
        return vo;
    }

    /**
     * 按日期 + 业态（可选）+ 状态白名单 count demand。
     *
     * @param date        demand_date 精确匹配
     * @param productType 业态；null 时不限业态
     * @param statuses    状态白名单（IN）
     */
    private long countByDateTypeStatus(LocalDate date, String productType, List<String> statuses) {
        return demandManageMapper.selectCount(
            new LambdaQueryWrapper<DemandManage>()
                .eq(DemandManage::getDemandDate, date)
                .eq(productType != null, DemandManage::getProductType, productType)
                .in(DemandManage::getDemandStatus, statuses));
    }

    /**
     * 批量统计每个 demand 的已指定猪只数（白条业态）。
     *
     * @param demandIds 需求 ID 列表
     * @return demandId → 猪只数
     */
    private Map<Long, Integer> countAssignedPigByDemand(List<Long> demandIds) {
        if (demandIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DemandPig> pigs = demandPigMapper.selectList(
            new LambdaQueryWrapper<DemandPig>()
                .select(DemandPig::getDemandId)
                .in(DemandPig::getDemandId, demandIds));
        return pigs.stream()
            .filter(p -> p.getDemandId() != null)
            .collect(Collectors.groupingBy(
                DemandPig::getDemandId,
                Collectors.summingInt(p -> 1)));
    }
}
