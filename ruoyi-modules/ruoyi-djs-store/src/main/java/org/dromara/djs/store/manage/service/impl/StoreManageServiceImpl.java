package org.dromara.djs.store.manage.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.store.domain.vo.StorePickerVo;
import org.dromara.djs.common.store.service.IStoreUserRelationService;
import org.dromara.djs.store.manage.domain.vo.StoreManageCategoryVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageMetricVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageMonthlyVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageProductCountRowVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageQtyRowVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageUnitRowVo;
import org.dromara.djs.store.manage.mapper.StoreManageMapper;
import org.dromara.djs.store.manage.service.IStoreManageService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 管理板块「门店管理」月度看板实现（MGMT-MP-STORE-MONTH-001，纯只读聚合）。
 *
 * <h3>甲方口径（逐条落这里）</h3>
 * <ol>
 *   <li>门店可选「全部」（storeId=null → 跨店合计）+ 单店；月份按月搜索，默认当月。</li>
 *   <li>全部指标以月为维度，区间左闭右开 {@code [当月1日, 次月1日)}。</li>
 *   <li>猪肉品类数 = 当月到店的 pork + white_bar 产品去重数；果蔬 = vegetable；
 *       其他 = egg + dry_good。「到店」= 门店日台账当月 {@code inbound_qty > 0}。</li>
 *   <li>需求量 = 门店下单量；销售量 = 盘点的 sale_qty + gift_qty；退回量 = 门店退回记录
 *       （方向 store_to_warehouse）的 return_quantity。四类卡：猪肉（pork+white_bar）/ 果蔬 /
 *       蛋类 / 干货。比率 = 环比（对上一自然月），上月无基数 → 0.00% 且 {@code hasBase=false}
 *       （前端据此渲染黑色）。</li>
 * </ol>
 *
 * <h3>单位分行</h3>
 * <p>同业态里 kg 与 份 不可相加，故按产品主数据 {@code product_unit} 分行。单位键统一小写归一
 * （库 collation 大小写不敏感、三条 SQL 各自可能返回 {@code kg} / {@code Kg} 两种写法，
 * 不归一会在同一张卡里裂成两行），展示值取第一次见到的原始写法。</p>
 *
 * <p>三指标 + 上月同口径 = 6 项全为 0 的单位行直接丢弃（台账里 sale/gift 全 0 的行会制造这种空行）。</p>
 *
 * @author djs
 * @since MGMT-MP-STORE-MONTH-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreManageServiceImpl implements IStoreManageService {

    /** 租户兜底（V1 单租户，TenantHelper 取不到时回退）。 */
    private static final String DEFAULT_TENANT = "1001";

    /** 业态卡 key（= 字典 djs_belong_type 的代表值，猪肉卡用 pork 代表 pork+white_bar）。 */
    private static final String CAT_PORK = "pork";
    private static final String CAT_VEG = "vegetable";
    private static final String CAT_EGG = "egg";
    private static final String CAT_DRY = "dry_good";

    /** 业态卡固定顺序 + 中文名（mp 硬编码中文，文案由后端给）。 */
    private static final Map<String, String> CATEGORY_NAMES = new LinkedHashMap<>();

    /** belong_type → 业态卡 key（pork / white_bar 并进同一张猪肉卡）。 */
    private static final Map<String, String> BELONG_TO_CATEGORY = new HashMap<>();

    /** SQL IN 白名单：只统计这 5 个 belong_type，礼盒 / 包材 / 饲料 / 种子 / other 不在四类卡里。 */
    private static final List<String> BELONG_TYPES =
        List.of("pork", "white_bar", "vegetable", "egg", "dry_good");

    /** 品类数分子：猪肉 = pork + white_bar。 */
    private static final List<String> PORK_BELONG_TYPES = List.of("pork", "white_bar");

    /** 品类数分子：果蔬。 */
    private static final List<String> VEG_BELONG_TYPES = List.of("vegetable");

    /** 品类数分子：其他 = 蛋类 + 干货（甲方原话「其他品类数包含蛋类产品和干货产品」）。 */
    private static final List<String> OTHER_BELONG_TYPES = List.of("egg", "dry_good");

    /** 产品主数据单位为空时的占位（product_unit 理论非空，防御性兜底）。 */
    private static final String UNIT_UNKNOWN = "未设单位";

    /** 环比百分比换算基数。 */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 无可比基数时的环比值（甲方口径：显示 0.00%，颜色由前端按 hasBase 降为黑色）。 */
    private static final BigDecimal ZERO_PCT = new BigDecimal("0.00");

    /** 指标值精度：与业务表 decimal(12,3) 对齐，不在后端截断有效位。 */
    private static final int QTY_SCALE = 3;

    static {
        CATEGORY_NAMES.put(CAT_PORK, "猪肉产品");
        CATEGORY_NAMES.put(CAT_VEG, "果蔬产品");
        CATEGORY_NAMES.put(CAT_EGG, "蛋类产品");
        CATEGORY_NAMES.put(CAT_DRY, "干货产品");

        BELONG_TO_CATEGORY.put("pork", CAT_PORK);
        BELONG_TO_CATEGORY.put("white_bar", CAT_PORK);
        BELONG_TO_CATEGORY.put("vegetable", CAT_VEG);
        BELONG_TO_CATEGORY.put("egg", CAT_EGG);
        BELONG_TO_CATEGORY.put("dry_good", CAT_DRY);
    }

    private final StoreManageMapper storeManageMapper;

    private final IStoreUserRelationService storeUserRelationService;

    @Override
    public List<StorePickerVo> listSelectableStores() {
        List<StorePickerVo> stores = storeUserRelationService.listMyStores(true);
        return stores == null ? List.of() : stores;
    }

    @Override
    public StoreManageMonthlyVo getMonthly(Long storeId, String month) {
        YearMonth ym = parseMonth(month);
        String tenantId = currentTenant();

        LocalDate curStart = ym.atDay(1);
        LocalDate curEnd = ym.plusMonths(1).atDay(1);
        LocalDate prevStart = ym.minusMonths(1).atDay(1);

        StoreManageMonthlyVo vo = new StoreManageMonthlyVo();
        vo.setMonth(ym.toString());
        vo.setStoreId(storeId);

        Map<String, Integer> arrived = countsByBelongType(
            storeManageMapper.countArrivedProducts(tenantId, storeId, curStart, curEnd, BELONG_TYPES));
        vo.setPorkProductCount(sumCounts(arrived, PORK_BELONG_TYPES));
        vo.setVegProductCount(sumCounts(arrived, VEG_BELONG_TYPES));
        vo.setOtherProductCount(sumCounts(arrived, OTHER_BELONG_TYPES));

        Map<String, String> unitLabels = new HashMap<>();
        Map<String, Map<String, BigDecimal>> curDemand = collect(
            storeManageMapper.sumDemandQty(tenantId, storeId, curStart, curEnd, BELONG_TYPES), unitLabels);
        Map<String, Map<String, BigDecimal>> curSale = collect(
            storeManageMapper.sumSaleQty(tenantId, storeId, curStart, curEnd, BELONG_TYPES), unitLabels);
        Map<String, Map<String, BigDecimal>> curReturn = collect(
            storeManageMapper.sumReturnQty(tenantId, storeId, curStart, curEnd, BELONG_TYPES), unitLabels);
        Map<String, Map<String, BigDecimal>> prevDemand = collect(
            storeManageMapper.sumDemandQty(tenantId, storeId, prevStart, curStart, BELONG_TYPES), unitLabels);
        Map<String, Map<String, BigDecimal>> prevSale = collect(
            storeManageMapper.sumSaleQty(tenantId, storeId, prevStart, curStart, BELONG_TYPES), unitLabels);
        Map<String, Map<String, BigDecimal>> prevReturn = collect(
            storeManageMapper.sumReturnQty(tenantId, storeId, prevStart, curStart, BELONG_TYPES), unitLabels);

        List<StoreManageCategoryVo> categories = new ArrayList<>(CATEGORY_NAMES.size());
        for (Map.Entry<String, String> cat : CATEGORY_NAMES.entrySet()) {
            StoreManageCategoryVo cvo = new StoreManageCategoryVo();
            cvo.setCategoryKey(cat.getKey());
            cvo.setCategoryName(cat.getValue());
            cvo.setRows(buildRows(cat.getKey(), unitLabels,
                curDemand, curSale, curReturn, prevDemand, prevSale, prevReturn));
            categories.add(cvo);
        }
        vo.setCategories(categories);
        return vo;
    }

    /**
     * 组一张业态卡的单位行。
     *
     * <p>单位全集 = 本月 ∪ 上月三指标出现过的单位——只取本月会让「上月有、本月归零」的品类
     * 整行消失，环比 -100% 这条最该被看见的信息反而看不到。</p>
     *
     * @param category   业态卡 key
     * @param unitLabels 单位归一键 → 展示原文
     * @return 单位行列表（按本月三指标合计倒序），全 0 的单位不出行
     */
    private List<StoreManageUnitRowVo> buildRows(String category,
                                                 Map<String, String> unitLabels,
                                                 Map<String, Map<String, BigDecimal>> curDemand,
                                                 Map<String, Map<String, BigDecimal>> curSale,
                                                 Map<String, Map<String, BigDecimal>> curReturn,
                                                 Map<String, Map<String, BigDecimal>> prevDemand,
                                                 Map<String, Map<String, BigDecimal>> prevSale,
                                                 Map<String, Map<String, BigDecimal>> prevReturn) {
        Set<String> unitKeys = new LinkedHashSet<>();
        unitKeys.addAll(unitsOf(curDemand, category));
        unitKeys.addAll(unitsOf(curSale, category));
        unitKeys.addAll(unitsOf(curReturn, category));
        unitKeys.addAll(unitsOf(prevDemand, category));
        unitKeys.addAll(unitsOf(prevSale, category));
        unitKeys.addAll(unitsOf(prevReturn, category));

        List<StoreManageUnitRowVo> rows = new ArrayList<>(unitKeys.size());
        for (String unitKey : unitKeys) {
            BigDecimal cd = valueOf(curDemand, category, unitKey);
            BigDecimal cs = valueOf(curSale, category, unitKey);
            BigDecimal cr = valueOf(curReturn, category, unitKey);
            BigDecimal pd = valueOf(prevDemand, category, unitKey);
            BigDecimal ps = valueOf(prevSale, category, unitKey);
            BigDecimal pr = valueOf(prevReturn, category, unitKey);
            if (isAllZero(cd, cs, cr, pd, ps, pr)) {
                continue;
            }
            StoreManageUnitRowVo row = new StoreManageUnitRowVo();
            row.setUnit(unitLabels.getOrDefault(unitKey, unitKey));
            row.setDemand(metric(cd, pd));
            row.setSale(metric(cs, ps));
            row.setReturned(metric(cr, pr));
            rows.add(row);
        }
        rows.sort(Comparator
            .comparing((StoreManageUnitRowVo r) -> r.getDemand().getValue()
                .add(r.getSale().getValue())
                .add(r.getReturned().getValue()))
            .reversed()
            .thenComparing(StoreManageUnitRowVo::getUnit));
        return rows;
    }

    /**
     * 单指标 + 环比。上月基数为 0 / 无记录 → {@code hasBase=false} 且环比固定 0.00。
     *
     * @param cur  本月值
     * @param prev 上月值
     * @return 指标 VO
     */
    private static StoreManageMetricVo metric(BigDecimal cur, BigDecimal prev) {
        BigDecimal c = nz(cur).setScale(QTY_SCALE, RoundingMode.HALF_UP);
        BigDecimal p = nz(prev);
        boolean hasBase = p.compareTo(BigDecimal.ZERO) != 0;
        StoreManageMetricVo vo = new StoreManageMetricVo();
        vo.setValue(c);
        vo.setHasBase(hasBase);
        vo.setMom(hasBase
            ? c.subtract(p).multiply(HUNDRED).divide(p, 2, RoundingMode.HALF_UP)
            : ZERO_PCT);
        return vo;
    }

    /**
     * mapper 原始行 → {业态 : {单位归一键 : 合计}}，同时登记单位展示原文。
     *
     * @param rows       mapper 行（可为 null）
     * @param unitLabels 单位归一键 → 展示原文（就地填充）
     * @return 业态桶
     */
    private static Map<String, Map<String, BigDecimal>> collect(List<StoreManageQtyRowVo> rows,
                                                                Map<String, String> unitLabels) {
        Map<String, Map<String, BigDecimal>> out = new HashMap<>();
        if (rows == null) {
            return out;
        }
        for (StoreManageQtyRowVo row : rows) {
            String category = BELONG_TO_CATEGORY.get(row.getBelongType());
            if (category == null) {
                continue;
            }
            String label = StringUtils.isBlank(row.getUnit()) ? UNIT_UNKNOWN : row.getUnit().trim();
            String key = label.toLowerCase(Locale.ROOT);
            unitLabels.putIfAbsent(key, label);
            out.computeIfAbsent(category, k -> new HashMap<>()).merge(key, nz(row.getQty()), BigDecimal::add);
        }
        return out;
    }

    /**
     * mapper 品类数行 → {belong_type : 去重产品数}。
     *
     * @param rows mapper 行（可为 null）
     * @return 业态 : 产品数
     */
    private static Map<String, Integer> countsByBelongType(List<StoreManageProductCountRowVo> rows) {
        Map<String, Integer> out = new HashMap<>();
        if (rows == null) {
            return out;
        }
        for (StoreManageProductCountRowVo row : rows) {
            if (row.getBelongType() == null) {
                continue;
            }
            out.merge(row.getBelongType(), row.getProductCount() == null ? 0 : row.getProductCount(), Integer::sum);
        }
        return out;
    }

    /**
     * 若干 belong_type 的去重产品数求和。
     *
     * <p>一个产品只有一个 belong_type，所以跨 belong_type 相加不会重复计数。</p>
     *
     * @param counts      业态 : 产品数
     * @param belongTypes 要合计的业态
     * @return 合计品类数
     */
    private static int sumCounts(Map<String, Integer> counts, List<String> belongTypes) {
        int total = 0;
        for (String bt : belongTypes) {
            total += counts.getOrDefault(bt, 0);
        }
        return total;
    }

    private static Set<String> unitsOf(Map<String, Map<String, BigDecimal>> bucket, String category) {
        return bucket.getOrDefault(category, Map.of()).keySet();
    }

    private static BigDecimal valueOf(Map<String, Map<String, BigDecimal>> bucket, String category, String unitKey) {
        return bucket.getOrDefault(category, Map.of()).getOrDefault(unitKey, BigDecimal.ZERO);
    }

    private static boolean isAllZero(BigDecimal... values) {
        for (BigDecimal v : values) {
            if (v != null && v.compareTo(BigDecimal.ZERO) != 0) {
                return false;
            }
        }
        return true;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 解析月份入参。空 → 当月；格式非法 → 400，不静默回退当月（否则用户以为在看 9 月、其实是当月）。
     *
     * @param month yyyy-MM
     * @return 年月
     */
    private static YearMonth parseMonth(String month) {
        if (StringUtils.isBlank(month)) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new ServiceException("月份格式不合法，应为 yyyy-MM：" + month, 400);
        }
    }

    private String currentTenant() {
        try {
            String t = TenantHelper.getTenantId();
            return StringUtils.isBlank(t) ? DEFAULT_TENANT : t;
        } catch (Exception e) {
            log.warn("[StoreManage] 获取租户失败，回退默认租户", e);
            return DEFAULT_TENANT;
        }
    }

}
