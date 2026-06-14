package org.dromara.djs.store.dashboard.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.store.dashboard.domain.vo.StoreDashboardDailyVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreDashboardMemberStatVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreDashboardSummaryVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreGroupCountVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreMemberGrowthPointVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreProductRankItemVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreSaleCategoryVo;
import org.dromara.djs.store.dashboard.domain.vo.StoreTrendPointVo;
import org.dromara.djs.store.dashboard.mapper.StoreDashboardMapper;
import org.dromara.djs.store.dashboard.mapper.StoreDashboardMapper.DailyCategoryRow;
import org.dromara.djs.store.dashboard.service.IStoreDashboardService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门店看板聚合实现（STR-DASH-001，纯只读聚合，不 extends DjsBaseServiceImpl）。
 *
 * <p>每个 KPI / 列表 1 个聚合 query，无 view / 无写事务。所有 null 用 0 / 空列表兜底。
 * 退回率 / 损耗率上游 V1 无明细表 → 置 null（前端显示"—"）。</p>
 *
 * @author djs
 * @since STR-DASH-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreDashboardServiceImpl implements IStoreDashboardService {

    private static final String DEFAULT_TENANT = "1001";

    /**
     * 猪肉业态（belong_type，字典 djs_belong_type）：自养猪肉 + 白条。
     */
    private static final List<String> PORK_TYPES = List.of("pork", "white_bar");

    /**
     * 果蔬业态（belong_type，字典 djs_belong_type）。
     */
    private static final List<String> VEG_TYPES = List.of("vegetable");

    /**
     * 4 业态 key（对齐 {@code djs_demand_product_type} 字典：white_bar=猪肉 / vegetable=蔬菜
     * / gift_box=礼盒 / other=其他）。固定顺序，前端固定 4 行。
     */
    private static final String CAT_PORK = "white_bar";
    private static final String CAT_VEG = "vegetable";
    private static final String CAT_GIFT = "gift_box";
    private static final String CAT_OTHER = "other";

    /**
     * 4 业态 key → 中文名（mp 硬编码中文，后端给文案）。LinkedHashMap 保固定顺序：猪肉/蔬菜/礼盒/其他。
     */
    private static final Map<String, String> CATEGORY_NAMES = new LinkedHashMap<>();

    static {
        CATEGORY_NAMES.put(CAT_PORK, "猪肉");
        CATEGORY_NAMES.put(CAT_VEG, "蔬菜");
        CATEGORY_NAMES.put(CAT_GIFT, "礼盒");
        CATEGORY_NAMES.put(CAT_OTHER, "其他");
    }

    private final StoreDashboardMapper dashboardMapper;

    @Override
    public StoreDashboardSummaryVo getSummary(Long storeId) {
        String tenantId = currentTenant();

        StoreDashboardSummaryVo vo = new StoreDashboardSummaryVo();
        vo.setTodaySaleAmount(nzBd(dashboardMapper.sumTodaySaleAmount(tenantId, storeId)));
        vo.setMonthSaleAmount(nzBd(dashboardMapper.sumMonthSaleAmount(tenantId, storeId)));
        vo.setTodayOrderCount(nz(dashboardMapper.countTodayOrders(tenantId, storeId)));
        vo.setMonthOrderCount(nz(dashboardMapper.countMonthOrders(tenantId, storeId)));
        vo.setPendingShipCount(nz(dashboardMapper.countPendingShip(tenantId, storeId)));
        vo.setPendingPurchaseCount(nz(dashboardMapper.countPendingPurchase(tenantId, storeId)));

        // 会员 KPI（原型「会员信息组」：今日新增 / 会员总数 / 老客复购 / 本月客单价）
        vo.setTotalMembers(nzLong(dashboardMapper.countTotalMembers(tenantId, storeId)));
        vo.setTodayNewMembers(nzLong(dashboardMapper.countTodayNewMembers(tenantId, storeId)));
        vo.setRepeatCustomer(nzLong(dashboardMapper.countRepeatCustomers(tenantId, storeId)));
        vo.setMonthAvgPrice(avg(vo.getMonthSaleAmount(), vo.getMonthOrderCount()));

        vo.setProductStructure(nzList(dashboardMapper.selectProductStructure(tenantId, storeId)));
        vo.setMonthProductStructure(nzList(dashboardMapper.selectMonthProductStructure(tenantId, storeId)));
        vo.setTop10Products(nzList(dashboardMapper.selectTop10Products(tenantId, storeId)));
        vo.setMonthTop10ByOrder(nzList(dashboardMapper.selectMonthTop10ByOrder(tenantId, storeId)));

        // 近 10 日趋势：销售 trend 为基，按日期 union merge 退货量（无销售但有退货的日补点），算客单价
        vo.setTrend10Days(buildTrend10Days(tenantId, storeId));

        // 近 10 日新增会员趋势（原型「近十日订单数与新会员趋势」竖柱）
        vo.setMemberGrowth10Days(nzList(dashboardMapper.selectMemberGrowth10Days(tenantId, storeId)));

        // 本月逐日趋势（原型「销售额与客单价趋势」竖柱=销售额 + 折线=客单价）
        vo.setMonthDailyTrend(buildMonthDailyTrend(tenantId, storeId));

        // 4 业态当日销售分布（猪肉 / 蔬菜 / 礼盒 / 其他，固定 4 行）
        vo.setSaleByCategory(buildSaleByCategory(tenantId, storeId));

        // 当日 4 KPI 同比（vs 上月同期）+ 今日客单价
        fillTodayYoy(vo, tenantId, storeId);

        // 充值额无数据源 → null 占位（前端显示"—"，V2 接充值流水）
        vo.setTodayRechargeAmount(null);
        vo.setTodayRechargeAmountYoy(null);

        return vo;
    }

    @Override
    public List<StoreMemberGrowthPointVo> getMemberGrowth10Days(Long storeId) {
        String tenantId = currentTenant();
        return nzList(dashboardMapper.selectMemberGrowth10Days(tenantId, storeId));
    }

    @Override
    public StoreDashboardMemberStatVo getMemberStat(Long storeId) {
        String tenantId = currentTenant();
        StoreDashboardMemberStatVo vo = new StoreDashboardMemberStatVo();
        long todayNew = nzLong(dashboardMapper.countTodayNewMembers(tenantId, storeId));
        vo.setTotalCount(nzLong(dashboardMapper.countTotalMembers(tenantId, storeId)));
        vo.setTodayNew(todayNew);
        // V1 无渠道 / 推荐人字段 → 今日发展口径等同今日新增（见 assumptions）
        vo.setTodayDeveloped(todayNew);
        vo.setMonthNew(nzLong(dashboardMapper.countMonthNewMembers(tenantId, storeId)));
        return vo;
    }

    @Override
    public StoreDashboardDailyVo getDaily(Long storeId) {
        String tenantId = currentTenant();

        StoreDashboardDailyVo vo = new StoreDashboardDailyVo();
        vo.setPork(buildCategory(dashboardMapper.selectDailyByBelongTypes(tenantId, storeId, PORK_TYPES)));
        vo.setVegetable(buildCategory(dashboardMapper.selectDailyByBelongTypes(tenantId, storeId, VEG_TYPES)));
        vo.setDemandStat(nzList(dashboardMapper.countDemandByStatus(tenantId, storeId)));
        vo.setShipStat(nzList(dashboardMapper.countShipByStatus(tenantId, storeId)));
        return vo;
    }

    /**
     * 组装近 10 日趋势：销售 trend 为基，按日期 union merge 退货量，算客单价。
     *
     * <p>销售 trend 仅出「当日有销售」的日期；退货 trend 出「当日有退货」的日期。两份按 date
     * union（无销售但有退货的日补点 saleAmount=0/orderCount=0/saleQty=0），按日期升序，
     * 无退货日 returnQty 补 0。前端「销售量与退货量趋势」双柱 + 「销售额折线」共用本序列。</p>
     *
     * @param tenantId 租户
     * @param storeId  门店 ID（可空）
     * @return 近 10 日趋势点（按日期升序，每点含订单数/销售额/销售量/退货量/客单价）
     */
    private List<StoreTrendPointVo> buildTrend10Days(String tenantId, Long storeId) {
        Map<String, StoreTrendPointVo> byDate = new LinkedHashMap<>();
        for (StoreTrendPointVo p : nzList(dashboardMapper.selectTrend10Days(tenantId, storeId))) {
            p.setSaleQty(nzBd(p.getSaleQty()));
            byDate.put(p.getDate(), p);
        }
        for (StoreTrendPointVo r : nzList(dashboardMapper.selectReturnTrend10Days(tenantId, storeId))) {
            StoreTrendPointVo p = byDate.get(r.getDate());
            if (p == null) {
                p = new StoreTrendPointVo();
                p.setDate(r.getDate());
                p.setOrderCount(0);
                p.setSaleAmount(BigDecimal.ZERO);
                p.setSaleQty(BigDecimal.ZERO);
                byDate.put(r.getDate(), p);
            }
            p.setReturnQty(nzBd(r.getReturnQty()));
        }
        List<StoreTrendPointVo> merged = new ArrayList<>(byDate.values());
        merged.sort(Comparator.comparing(StoreTrendPointVo::getDate));
        merged.forEach(p -> {
            p.setReturnQty(nzBd(p.getReturnQty()));
            p.setAvgPrice(avg(p.getSaleAmount(), p.getOrderCount()));
        });
        return merged;
    }

    /**
     * 组装本月逐日趋势：每日订单数 + 销售额（mapper 出有销售的日），逐点算客单价。
     *
     * <p>原型「销售额与客单价趋势」竖柱=销售额 + 折线=客单价，横轴本月每天。mapper 仅出
     * 有销售的日期，前端按本月天数（横轴）补 0；后端只回填客单价 = 销售额 / 订单数。</p>
     *
     * @param tenantId 租户
     * @param storeId  门店 ID（可空）
     * @return 本月逐日趋势点（按日期升序，每点含订单数/销售额/客单价）
     */
    private List<StoreTrendPointVo> buildMonthDailyTrend(String tenantId, Long storeId) {
        List<StoreTrendPointVo> list = new ArrayList<>(nzList(dashboardMapper.selectMonthDailyTrend(tenantId, storeId)));
        list.forEach(p -> {
            p.setSaleAmount(nzBd(p.getSaleAmount()));
            p.setAvgPrice(avg(p.getSaleAmount(), p.getOrderCount()));
        });
        return list;
    }

    /**
     * 单业态速览组装：销售额 + 订单数 + 客单价；退回率 / 损耗率置 null（上游 V1 无数据）。
     *
     * @param row mapper 原始行（可空）
     * @return 速览 VO（row 空时返 empty）
     */
    private StoreDashboardDailyVo.DailyCategoryVo buildCategory(DailyCategoryRow row) {
        if (row == null) {
            return StoreDashboardDailyVo.DailyCategoryVo.empty();
        }
        StoreDashboardDailyVo.DailyCategoryVo vo = new StoreDashboardDailyVo.DailyCategoryVo();
        BigDecimal amount = nzBd(row.getValue());
        int orders = nz(row.getOrderCount());
        vo.setSalesAmount(amount);
        vo.setOrderCount(orders);
        vo.setAvgOrderPrice(avg(amount, orders));
        // 退回率 / 损耗率上游 V1 无退货 / 损耗明细表 → null，前端显示"—"占位
        vo.setReturnRate(null);
        vo.setLossRate(null);
        return vo;
    }

    /**
     * 组装 4 业态当日销售分布：mapper 出原始 belong_type 行 → 归并 4 业态 → 固定补齐 4 桶。
     *
     * <p>当日行（{@code selectSaleByBelongType}）出销售额 + 订单数 + 该业态当月累计；当日无单
     * 但当月有累计的业态由 {@code selectMonthAccumByBelongType} 补累计。两份按 4 业态 key 归并，
     * 最终固定返 4 行（无数据业态全 0）。</p>
     *
     * @param tenantId 租户
     * @param storeId  门店 ID（可空）
     * @return 固定 4 行业态分布
     */
    private List<StoreSaleCategoryVo> buildSaleByCategory(String tenantId, Long storeId) {
        // 4 业态聚合桶（LinkedHashMap 固定顺序：猪肉/蔬菜/礼盒/其他）
        Map<String, StoreSaleCategoryVo> buckets = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : CATEGORY_NAMES.entrySet()) {
            StoreSaleCategoryVo vo = new StoreSaleCategoryVo();
            vo.setBelongType(e.getKey());
            vo.setCategoryName(e.getValue());
            vo.setSaleAmount(BigDecimal.ZERO);
            vo.setOrderCount(0);
            vo.setMonthAccum(BigDecimal.ZERO);
            buckets.put(e.getKey(), vo);
        }

        // 当日行：累加销售额 + 订单数（当月累计先以当日行带的为主，下方再以月累计专查覆盖）
        for (StoreSaleCategoryVo row : nzList(dashboardMapper.selectSaleByBelongType(tenantId, storeId))) {
            StoreSaleCategoryVo b = buckets.get(mapToCategory(row.getBelongType()));
            b.setSaleAmount(b.getSaleAmount().add(nzBd(row.getSaleAmount())));
            b.setOrderCount(b.getOrderCount() + nz(row.getOrderCount()));
        }

        // 当月累计：以专查为权威覆盖（当日无单但当月有累计的业态也覆盖到）
        for (StoreSaleCategoryVo row : nzList(dashboardMapper.selectMonthAccumByBelongType(tenantId, storeId))) {
            StoreSaleCategoryVo b = buckets.get(mapToCategory(row.getBelongType()));
            b.setMonthAccum(b.getMonthAccum().add(nzBd(row.getMonthAccum())));
        }

        return new ArrayList<>(buckets.values());
    }

    /**
     * 产品 belong_type → 4 业态 key 归并（对齐 {@code djs_demand_product_type}）。
     *
     * <p>{@code pork}/{@code white_bar} → 猪肉(white_bar)；{@code vegetable} → 蔬菜；
     * {@code gift_box} → 礼盒；{@code dry_good}/{@code egg}/null/其他 → 其他(other)。</p>
     *
     * @param belongType 产品 belong_type（可空）
     * @return 4 业态 key
     */
    private String mapToCategory(String belongType) {
        if (belongType == null) {
            return CAT_OTHER;
        }
        return switch (belongType) {
            case "pork", "white_bar" -> CAT_PORK;
            case "vegetable" -> CAT_VEG;
            case "gift_box" -> CAT_GIFT;
            default -> CAT_OTHER;
        };
    }

    /**
     * 填充当日 4 KPI 同比（营业额 / 订单数 / 客单价）+ 今日客单价。
     *
     * <p>同比基准 = 上月同期；上期为 0 时同比 null（前端显示"—"，避免除零误导）。
     * 客单价 = 销售额 / 订单数（service 计算）。</p>
     *
     * @param vo       汇总 VO（todaySaleAmount / todayOrderCount 须已填）
     * @param tenantId 租户
     * @param storeId  门店 ID（可空）
     */
    private void fillTodayYoy(StoreDashboardSummaryVo vo, String tenantId, Long storeId) {
        BigDecimal todayAmount = nzBd(vo.getTodaySaleAmount());
        int todayOrders = nz(vo.getTodayOrderCount());
        BigDecimal todayAvg = avg(todayAmount, todayOrders);
        vo.setAvgPriceToday(todayAvg);

        BigDecimal lastAmount = nzBd(dashboardMapper.sumLastMonthSameDaySaleAmount(tenantId, storeId));
        int lastOrders = nz(dashboardMapper.countLastMonthSameDayOrders(tenantId, storeId));
        BigDecimal lastAvg = avg(lastAmount, lastOrders);

        vo.setTodaySaleAmountYoy(yoyPercent(todayAmount, lastAmount));
        vo.setTodayOrderCountYoy(yoyPercent(BigDecimal.valueOf(todayOrders), BigDecimal.valueOf(lastOrders)));
        vo.setAvgPriceYoy(yoyPercent(todayAvg, lastAvg));
    }

    /**
     * 同比百分比 = (本期 − 上期) / 上期 × 100，保留 2 位；上期 ≤ 0 时返 null（前端显示"—"）。
     *
     * @param current 本期值（可空，按 0 处理）
     * @param base    上期值（可空，按 0 处理）
     * @return 同比百分比（如 12.50 表示 +12.5%；负数表示下降），上期 0 时 null
     */
    private BigDecimal yoyPercent(BigDecimal current, BigDecimal base) {
        BigDecimal b = nzBd(base);
        if (b.signum() <= 0) {
            return null;
        }
        return nzBd(current).subtract(b)
            .multiply(BigDecimal.valueOf(100))
            .divide(b, 2, RoundingMode.HALF_UP);
    }

    /**
     * 客单价 = 销售额 / 订单数（订单数 0 时返 0，避免除零），保留 2 位小数。
     *
     * @param amount 销售额（可空）
     * @param orders 订单数
     * @return 客单价
     */
    private BigDecimal avg(BigDecimal amount, Integer orders) {
        int o = nz(orders);
        if (o == 0) {
            return BigDecimal.ZERO;
        }
        return nzBd(amount).divide(BigDecimal.valueOf(o), 2, RoundingMode.HALF_UP);
    }

    /**
     * null → 0。
     *
     * @param v 可空整数
     * @return 非空整数（null 视作 0）
     */
    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * null → 0L。
     *
     * @param v 可空 Long
     * @return 非空 long（null 视作 0）
     */
    private long nzLong(Long v) {
        return v == null ? 0L : v;
    }

    /**
     * null → BigDecimal.ZERO。
     *
     * @param v 可空 BigDecimal
     * @return 非空 BigDecimal（null 视作 ZERO）
     */
    private BigDecimal nzBd(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * null → 可变空列表（forEach 回写客单价需可变）。
     *
     * @param list 可空列表
     * @param <T>  元素类型
     * @return 非空列表
     */
    private <T> List<T> nzList(List<T> list) {
        return list == null ? List.of() : list;
    }

    /**
     * 取当前租户；V1 单租户场景或异常时回退 {@value #DEFAULT_TENANT}。
     *
     * @return 当前租户 ID
     */
    private String currentTenant() {
        try {
            String t = TenantHelper.getTenantId();
            return t == null || t.isEmpty() ? DEFAULT_TENANT : t;
        } catch (Exception e) {
            log.warn("[StoreDashboard] 获取租户失败，回退默认租户", e);
            return DEFAULT_TENANT;
        }
    }

}
