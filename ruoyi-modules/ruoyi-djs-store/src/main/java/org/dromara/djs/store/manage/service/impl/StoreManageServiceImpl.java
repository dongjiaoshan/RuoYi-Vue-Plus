package org.dromara.djs.store.manage.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.store.domain.vo.StorePickerVo;
import org.dromara.djs.common.store.service.IStoreUserRelationService;
import org.dromara.djs.store.manage.domain.vo.StoreManageCategoryVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageDetailRowVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageDetailTotalVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageDetailVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageMetricVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageMonthlyVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageProductCountRowVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageProductQtyRowVo;
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
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

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
 *       （方向 store_to_warehouse）的 return_quantity。五类卡：猪肉（pork+white_bar）/ 果蔬 /
 *       蛋类 / 干货 / 其他（belong_type=other）。比率 = 环比（对上一自然月），上月无基数 →
 *       0.00% 且 {@code hasBase=false}（前端据此渲染黑色）。</li>
 *   <li><b>当月无数据不显示</b>（D-0045，甲方 2026-09-08），两级同规则：<b>卡级</b>——本卡当月三个
 *       指标在所有单位上都为 0 → 该卡不进 {@code categories}，前端连卡壳都不渲染，五张卡全被剔掉
 *       时下发空列表、前端出整页空态；<b>行级</b>——某单位当月三指标全 0 → 该行不出，上月有数也不救
 *       （代价是那一行的 -100% 环比看不到，甲方明确要的）。</li>
 * </ol>
 *
 * <h3>「其他产品」卡与顶部「其他品类数」不是一回事（D-0046）</h3>
 * <p>顶部小卡「其他品类数」= 到店的 <b>egg + dry_good</b> 去重产品数（甲方原话「其他品类数包含蛋类
 * 产品和干货产品」，见 {@link #OTHER_BELONG_TYPES}）；新增的<b>业态卡</b>「其他产品」= 产品档案
 * {@code belong_type='other'} 的三项统计。两者名字都带「其他」但口径不同，各按各的甲方口径走，
 * 不互相对齐。</p>
 *
 * <h3>单位分行</h3>
 * <p>同业态里 kg 与 份 不可相加，故按产品主数据 {@code product_unit} 分行。单位键统一小写归一
 * （库 collation 大小写不敏感、三条 SQL 各自可能返回 {@code kg} / {@code Kg} 两种写法，
 * 不归一会在同一张卡里裂成两行），展示值取第一次见到的原始写法。</p>
 *
 * <p>本月三指标全为 0 的单位行直接丢弃（台账里 sale/gift 全 0 的行会制造这种空行），
 * 与卡级同一把尺子，见 {@link #buildRows}。</p>
 *
 * <h3>「明细」下钻为什么和卡片必然对得上（V6-R180）</h3>
 * <p>明细页顶部的合计 {@code totals} <b>不另写 SQL</b>，直接调业态卡那三个聚合方法
 * （{@code sumDemandQty} / {@code sumSaleQty} / {@code sumReturnQty}），只是把业态白名单收窄到
 * 点开的那一张卡；明细行则与聚合共用 mapper 里的 {@code *_FROM} / {@code *_WHERE} 片段。
 * 两条路径的筛选条件是同一份，改一处两边同时生效。</p>
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
    private static final String CAT_OTHER = "other";

    /** 业态卡固定顺序 + 中文名（mp 硬编码中文，文案由后端给）。 */
    private static final Map<String, String> CATEGORY_NAMES = new LinkedHashMap<>();

    /** belong_type → 业态卡 key（pork / white_bar 并进同一张猪肉卡）。 */
    private static final Map<String, String> BELONG_TO_CATEGORY = new HashMap<>();

    /**
     * 业态卡 key → 该卡涵盖的 belong_type（猪肉卡合并 pork + white_bar：白条是猪肉的过程形态）。
     *
     * <p>卡片聚合传全集、「明细」下钻传单卡子集，两处都从这里取，不各写一份。</p>
     */
    private static final Map<String, List<String>> CATEGORY_BELONG_TYPES = new LinkedHashMap<>();

    /** SQL IN 白名单：只统计业态卡涉及的 belong_type，礼盒 / 包材 / 饲料 / 种子 不在内。 */
    private static final List<String> BELONG_TYPES;

    /** 品类数分子：猪肉 = pork + white_bar。 */
    private static final List<String> PORK_BELONG_TYPES;

    /** 品类数分子：果蔬。 */
    private static final List<String> VEG_BELONG_TYPES;

    /**
     * 顶部小卡「其他品类数」的分子 = 蛋类 + 干货（甲方原话「其他品类数包含蛋类产品和干货产品」）。
     *
     * <p>⚠️ 与业态卡 {@link #CAT_OTHER}「其他产品」（belong_type = other）不是一回事，别互相套用。</p>
     */
    private static final List<String> OTHER_BELONG_TYPES = List.of("egg", "dry_good");

    /** 产品主数据单位为空时的占位（product_unit 理论非空，防御性兜底）。 */
    private static final String UNIT_UNKNOWN = "未设单位";

    /** 明细行里可空文本的占位（规格）。 */
    private static final String EMPTY_TEXT = "—";

    /** 明细分页缺省页大小（PageQuery 默认是查全部，一次全拉会拖死 mp 页面）。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

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
        CATEGORY_NAMES.put(CAT_OTHER, "其他产品");

        CATEGORY_BELONG_TYPES.put(CAT_PORK, List.of("pork", "white_bar"));
        CATEGORY_BELONG_TYPES.put(CAT_VEG, List.of("vegetable"));
        CATEGORY_BELONG_TYPES.put(CAT_EGG, List.of("egg"));
        CATEGORY_BELONG_TYPES.put(CAT_DRY, List.of("dry_good"));
        CATEGORY_BELONG_TYPES.put(CAT_OTHER, List.of("other"));

        CATEGORY_BELONG_TYPES.forEach((cat, types) -> types.forEach(t -> BELONG_TO_CATEGORY.put(t, cat)));

        BELONG_TYPES = CATEGORY_BELONG_TYPES.values().stream().flatMap(List::stream).toList();
        PORK_BELONG_TYPES = CATEGORY_BELONG_TYPES.get(CAT_PORK);
        VEG_BELONG_TYPES = CATEGORY_BELONG_TYPES.get(CAT_VEG);
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

        // 单位展示原文：category → (合并键 → 原文)，**按业态隔离**（见 putLabel）
        Map<String, Map<String, String>> unitLabels = new HashMap<>();
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
            // D-0045：本月三个指标在所有单位上都无数据 → 整卡不下发（不是下发空卡让前端出空态）
            if (!hasCurrentData(cat.getKey(), curDemand, curSale, curReturn)) {
                continue;
            }
            StoreManageCategoryVo cvo = new StoreManageCategoryVo();
            cvo.setCategoryKey(cat.getKey());
            cvo.setCategoryName(cat.getValue());
            cvo.setRows(buildRows(cat.getKey(), unitLabels.getOrDefault(cat.getKey(), Map.of()),
                curDemand, curSale, curReturn, prevDemand, prevSale, prevReturn));
            categories.add(cvo);
        }
        vo.setCategories(categories);
        return vo;
    }

    /**
     * 该业态本月是否有任何数据（三个指标 × 所有单位里存在非 0 值）。
     *
     * <p>只看本月，不看上月：上月有、本月归零的卡按甲方口径 D-0045 一样不显示
     * ——「没有数据时不显示对应的内容」指的是当月，环比 -100% 不构成显示理由。</p>
     *
     * @param category 业态卡 key
     * @param buckets  本月三个指标的桶
     * @return 有任一非 0 值即 true
     */
    @SafeVarargs
    private static boolean hasCurrentData(String category, Map<String, Map<String, BigDecimal>>... buckets) {
        for (Map<String, Map<String, BigDecimal>> bucket : buckets) {
            for (BigDecimal v : bucket.getOrDefault(category, Map.of()).values()) {
                if (v != null && v.compareTo(BigDecimal.ZERO) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public StoreManageDetailVo getDetail(Long storeId, String month, String belongType, PageQuery pageQuery) {
        YearMonth ym = parseMonth(month);
        String tenantId = currentTenant();
        List<String> catBelongTypes = requireCategory(belongType);
        String category = belongType.trim();

        LocalDate curStart = ym.atDay(1);
        LocalDate curEnd = ym.plusMonths(1).atDay(1);
        LocalDate prevStart = ym.minusMonths(1).atDay(1);

        IPage<StoreManageDetailRowVo> page = storeManageMapper.selectProductDetailPage(
            safePage(pageQuery), tenantId, storeId, curStart, curEnd, catBelongTypes);
        List<StoreManageDetailRowVo> rows = page.getRecords() == null ? List.of() : page.getRecords();
        Map<Long, StoreManageProductQtyRowVo> prev =
            prevMonthByProduct(tenantId, storeId, prevStart, curStart, catBelongTypes, rows);
        for (StoreManageDetailRowVo row : rows) {
            row.setUnit(StringUtils.isBlank(row.getUnit()) ? UNIT_UNKNOWN : row.getUnit().trim());
            row.setProductSpec(StringUtils.isBlank(row.getProductSpec()) ? EMPTY_TEXT : row.getProductSpec().trim());
            row.setDemandQty(scaled(row.getDemandQty()));
            row.setSaleQty(scaled(row.getSaleQty()));
            row.setReturnQty(scaled(row.getReturnQty()));
            applyMom(row, prev.get(row.getProductId()));
        }

        StoreManageDetailVo vo = new StoreManageDetailVo();
        vo.setMonth(ym.toString());
        vo.setBelongType(category);
        vo.setCategoryName(CATEGORY_NAMES.get(category));
        vo.setStoreId(storeId);
        vo.setTotal(page.getTotal());
        vo.setRows(rows);
        vo.setTotals(buildDetailTotals(tenantId, storeId, curStart, curEnd, catBelongTypes));
        return vo;
    }

    /**
     * 取当前页这几个产品的<b>上月</b>三个量（V6-R209 逐产品环比的基数）。
     *
     * <p>只查当前页的产品：明细是分页的，把整月产品全拉回来算环比是白花的 IO。
     * 页里一条都没有时直接短路——空 {@code IN ()} 会拼出非法 SQL。</p>
     *
     * @param tenantId       租户
     * @param storeId        门店 ID（可空）
     * @param prevStart      上月首日（含）
     * @param curStart       本月首日（不含）
     * @param catBelongTypes 本卡涵盖的 belong_type（与本月同一份，环比分子分母才同口径）
     * @param rows           当前页明细行
     * @return productId : 上月三个量；上月无记录的产品不在 map 里（调用方按 0 处理）
     */
    private Map<Long, StoreManageProductQtyRowVo> prevMonthByProduct(String tenantId, Long storeId,
                                                                     LocalDate prevStart, LocalDate curStart,
                                                                     List<String> catBelongTypes,
                                                                     List<StoreManageDetailRowVo> rows) {
        List<Long> productIds = rows.stream()
            .map(StoreManageDetailRowVo::getProductId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }
        List<StoreManageProductQtyRowVo> prevRows = storeManageMapper.selectProductMonthSums(
            tenantId, storeId, prevStart, curStart, catBelongTypes, productIds);
        Map<Long, StoreManageProductQtyRowVo> out = new HashMap<>();
        if (prevRows == null) {
            return out;
        }
        for (StoreManageProductQtyRowVo r : prevRows) {
            if (r.getProductId() != null) {
                out.put(r.getProductId(), r);
            }
        }
        return out;
    }

    /**
     * 把上月基数换算成明细行的三组环比（算法与业态卡 {@link #metric} 逐字相同）。
     *
     * @param row  明细行（就地写入）
     * @param prev 上月同产品同口径的三个量；null = 上月该产品无任何记录
     */
    private static void applyMom(StoreManageDetailRowVo row, StoreManageProductQtyRowVo prev) {
        BigDecimal pd = prev == null ? BigDecimal.ZERO : nz(prev.getDemandQty());
        BigDecimal ps = prev == null ? BigDecimal.ZERO : nz(prev.getSaleQty());
        BigDecimal pr = prev == null ? BigDecimal.ZERO : nz(prev.getReturnQty());
        row.setDemandMom(momOf(row.getDemandQty(), pd));
        row.setDemandHasBase(hasBase(pd));
        row.setSaleMom(momOf(row.getSaleQty(), ps));
        row.setSaleHasBase(hasBase(ps));
        row.setReturnMom(momOf(row.getReturnQty(), pr));
        row.setReturnHasBase(hasBase(pr));
    }

    /**
     * 明细页顶部合计：直接调业态卡那三条聚合 SQL，只把业态白名单收窄到本卡。
     *
     * <p>不自己再 SUM 一遍明细行 —— 明细是分页的，按页求和只会得到「这一页的合计」；
     * 而复用卡片聚合则连口径漂移的可能都没有。</p>
     *
     * @param tenantId        租户
     * @param storeId         门店 ID（可空）
     * @param curStart        月首日（含）
     * @param curEnd          次月首日（不含）
     * @param catBelongTypes  本卡涵盖的 belong_type
     * @return 按单位的三项合计（单位名升序，与业态卡行序一致）
     */
    private List<StoreManageDetailTotalVo> buildDetailTotals(String tenantId, Long storeId,
                                                             LocalDate curStart, LocalDate curEnd,
                                                             List<String> catBelongTypes) {
        Map<String, String> unitLabels = new HashMap<>();
        Map<String, BigDecimal> demand = sumByUnit(
            storeManageMapper.sumDemandQty(tenantId, storeId, curStart, curEnd, catBelongTypes), unitLabels);
        Map<String, BigDecimal> sale = sumByUnit(
            storeManageMapper.sumSaleQty(tenantId, storeId, curStart, curEnd, catBelongTypes), unitLabels);
        Map<String, BigDecimal> returned = sumByUnit(
            storeManageMapper.sumReturnQty(tenantId, storeId, curStart, curEnd, catBelongTypes), unitLabels);

        // TreeMap 只为拿确定的单位序；三源的单位取并集，否则「只有退回量」的单位整行消失
        Set<String> unitKeys = new TreeSet<>();
        unitKeys.addAll(demand.keySet());
        unitKeys.addAll(sale.keySet());
        unitKeys.addAll(returned.keySet());

        List<StoreManageDetailTotalVo> totals = new ArrayList<>(unitKeys.size());
        for (String key : unitKeys) {
            StoreManageDetailTotalVo t = new StoreManageDetailTotalVo();
            t.setUnit(unitLabels.getOrDefault(key, key));
            t.setDemandQty(scaled(demand.get(key)));
            t.setSaleQty(scaled(sale.get(key)));
            t.setReturnQty(scaled(returned.get(key)));
            totals.add(t);
        }
        return totals;
    }

    /**
     * 聚合行 → {单位归一键 : 合计}（本卡内跨 belong_type 的同单位相加，与 {@link #collect} 同规则）。
     *
     * @param rows       mapper 行（可为 null）
     * @param unitLabels 单位归一键 → 展示原文（就地填充）
     * @return 单位 : 合计
     */
    private static Map<String, BigDecimal> sumByUnit(List<StoreManageQtyRowVo> rows,
                                                     Map<String, String> unitLabels) {
        Map<String, BigDecimal> out = new HashMap<>();
        if (rows == null) {
            return out;
        }
        for (StoreManageQtyRowVo row : rows) {
            String label = StringUtils.isBlank(row.getUnit()) ? UNIT_UNKNOWN : row.getUnit().trim();
            String key = label.toLowerCase(Locale.ROOT);
            // 入参恒是单张卡的聚合结果 → 这份 map 天然按业态隔离；取字面走同一条确定性规则
            putLabel(unitLabels, key, label);
            out.merge(key, nz(row.getQty()), BigDecimal::add);
        }
        return out;
    }

    /**
     * 业态白名单校验：只认业态卡的 key（pork / vegetable / egg / dry_good / other）。
     *
     * <p>非法值直接 400 而不是返空列表 —— 返空会让人以为「这个月这个业态真没数据」，
     * 实际是链接拼错了，这种错静默下去没人能发现。</p>
     *
     * @param belongType 入参业态键
     * @return 该卡涵盖的 belong_type（猪肉卡 = pork + white_bar）
     */
    private static List<String> requireCategory(String belongType) {
        List<String> types = belongType == null ? null : CATEGORY_BELONG_TYPES.get(belongType.trim());
        if (types == null) {
            throw new ServiceException(
                "业态不合法，只接受 " + CATEGORY_BELONG_TYPES.keySet() + "：" + belongType, 400);
        }
        return types;
    }

    /**
     * 分页参数兜底。
     *
     * <p>没传 pageSize 时给 {@value #DEFAULT_PAGE_SIZE} 条：{@code PageQuery} 的默认值是
     * {@code Integer.MAX_VALUE}（查全部）。只取 pageNum / pageSize，<b>不透传前端排序</b>——
     * 需求量降序是与卡片对读的前提，不能被 URL 参数改掉。</p>
     *
     * @param pageQuery 前端分页参数（可空）
     * @return MyBatis-Plus 分页对象
     */
    private static IPage<StoreManageDetailRowVo> safePage(PageQuery pageQuery) {
        Integer size = pageQuery == null || pageQuery.getPageSize() == null
            ? DEFAULT_PAGE_SIZE : pageQuery.getPageSize();
        Integer num = pageQuery == null || pageQuery.getPageNum() == null
            ? 1 : pageQuery.getPageNum();
        return new PageQuery(size, num).build();
    }

    /** 指标值统一到 decimal(12,3) 精度，避免同一个量在卡片与明细两边显示位数不一致。 */
    private static BigDecimal scaled(BigDecimal v) {
        return nz(v).setScale(QTY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 组一张业态卡的单位行。
     *
     * <h3>单位全集只取本月（D-0045，与卡级同一把尺子）</h3>
     * <p>甲方口径「没有数据时不显示对应的内容」落在两个层级上：卡级（本卡当月三指标全 0 → 整卡不下发）
     * 与行级（本单位当月三指标全 0 → 该行不出）。两级必须同规则——否则同一张卡里会同时出现
     * 「整卡因当月无数据而消失」和「某行因上月有数而留下一排 0 与 -100%」，自相矛盾。</p>
     *
     * <p><b>放弃的东西写在这里</b>：上月有数、本月归零的单位行连同它的 -100% 环比一起看不到了。
     * 这是甲方明确要的（row201 +「需求量、销售量、退回量都为空的数据不显示」），不是疏漏。</p>
     *
     * @param category   业态卡 key
     * @param unitLabels 单位归一键 → 展示原文
     * @return 单位行列表（按本月三指标合计倒序），本月三指标全 0 的单位不出行
     */
    private List<StoreManageUnitRowVo> buildRows(String category,
                                                 Map<String, String> unitLabels,
                                                 Map<String, Map<String, BigDecimal>> curDemand,
                                                 Map<String, Map<String, BigDecimal>> curSale,
                                                 Map<String, Map<String, BigDecimal>> curReturn,
                                                 Map<String, Map<String, BigDecimal>> prevDemand,
                                                 Map<String, Map<String, BigDecimal>> prevSale,
                                                 Map<String, Map<String, BigDecimal>> prevReturn) {
        // 只取本月出现过的单位：只在上月出现的单位本月三项必为 0，下面那道过滤也会把它剔掉，
        // 这里不并进来省一轮空转，同时让「行级只看当月」在代码上一眼可见
        Set<String> unitKeys = new LinkedHashSet<>();
        unitKeys.addAll(unitsOf(curDemand, category));
        unitKeys.addAll(unitsOf(curSale, category));
        unitKeys.addAll(unitsOf(curReturn, category));

        List<StoreManageUnitRowVo> rows = new ArrayList<>(unitKeys.size());
        for (String unitKey : unitKeys) {
            BigDecimal cd = valueOf(curDemand, category, unitKey);
            BigDecimal cs = valueOf(curSale, category, unitKey);
            BigDecimal cr = valueOf(curReturn, category, unitKey);
            BigDecimal pd = valueOf(prevDemand, category, unitKey);
            BigDecimal ps = valueOf(prevSale, category, unitKey);
            BigDecimal pr = valueOf(prevReturn, category, unitKey);
            // D-0045 行级：只看当月三项，上月有数不构成显示理由（丢掉的是这一行的 -100% 环比）
            if (isAllZero(cd, cs, cr)) {
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
        StoreManageMetricVo vo = new StoreManageMetricVo();
        vo.setValue(scaled(cur));
        vo.setHasBase(hasBase(prev));
        vo.setMom(momOf(cur, prev));
        return vo;
    }

    /**
     * 环比百分比。上月基数为 0 / 无记录 → 固定 {@link #ZERO_PCT}（配 {@link #hasBase} 的 false 由前端渲染黑色）。
     *
     * @param cur  本月值
     * @param prev 上月值
     * @return 环比（scale=2）
     */
    private static BigDecimal momOf(BigDecimal cur, BigDecimal prev) {
        BigDecimal p = nz(prev);
        if (p.compareTo(BigDecimal.ZERO) == 0) {
            return ZERO_PCT;
        }
        return scaled(cur).subtract(p).multiply(HUNDRED).divide(p, 2, RoundingMode.HALF_UP);
    }

    /**
     * 上月是否有可比基数。
     *
     * <p>「上月无基数的 0.00%」与「上月有基数、持平的 0.00%」必须分得开，前者前端渲染中性黑。</p>
     *
     * @param prev 上月值
     * @return 上月非 0 即 true
     */
    private static boolean hasBase(BigDecimal prev) {
        return nz(prev).compareTo(BigDecimal.ZERO) != 0;
    }

    /**
     * mapper 原始行 → {业态 : {单位归一键 : 合计}}，同时登记单位展示原文。
     *
     * @param rows       mapper 行（可为 null）
     * @param unitLabels 单位归一键 → 展示原文（就地填充）
     * @return 业态桶
     */
    private static Map<String, Map<String, BigDecimal>> collect(List<StoreManageQtyRowVo> rows,
                                                                Map<String, Map<String, String>> unitLabels) {
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
            putLabel(unitLabels.computeIfAbsent(category, k -> new HashMap<>()), key, label);
            out.computeIfAbsent(category, k -> new HashMap<>()).merge(key, nz(row.getQty()), BigDecimal::add);
        }
        return out;
    }

    /**
     * 记录某个单位合并键的展示原文。
     *
     * <h3>两条要求，缺一个业态卡与明细页就会显示不同的字面</h3>
     * <ol>
     *   <li><b>按业态隔离</b>（调用方保证：传进来的 map 是某一张卡专属的）。共享一份会串味 ——
     *       产品档案里同一个单位大小写混录（实测 {@code Kg} 只有 other 品类的 2 个产品在用、
     *       其余 165 个都是小写 {@code kg}），共享时 {@code labels["kg"]} 被先到的业态占成小写，
     *       而明细页的 {@link #sumByUnit} 只吃这张卡自己的行 —— 同一个数两处字面不一样。</li>
     * </ol>
     *
     * <p><b>取值规则（确定性）</b>：同一个键有多种字面时，<b>优先取全小写那个</b>（即字面 == 合并键），
     * 都不是小写则取自然序最小的。<b>不是「先到先得」</b> —— 先到先得依赖 SQL 返回行序，
     * 而卡片走全品类那条聚合、明细走收窄品类的同一条聚合，两次行序 MySQL 不保证一致，
     * 那就又会两处显示不同的字面。</p>
     *
     * <p>与仓库侧 {@code WarehouseBoardStatServiceImpl#putLabel} 同规则 —— 同一个坑不留两套写法。</p>
     *
     * @param labels 该业态的「合并键 → 展示原文」（就地填充）
     * @param key    合并键（小写）
     * @param label  本行的展示原文
     */
    private static void putLabel(Map<String, String> labels, String key, String label) {
        labels.merge(key, label, (a, b) -> {
            if (a.equals(key) || b.equals(key)) {
                return a.equals(key) ? a : b;
            }
            return a.compareTo(b) <= 0 ? a : b;
        });
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
