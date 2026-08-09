package org.dromara.djs.store.demand.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.common.store.service.IStoreUserRelationService;
import org.dromara.djs.plant.cropstat.domain.vo.CropPlotStatVo;
import org.dromara.djs.plant.cropstat.service.ICropPlotStatService;
import org.dromara.djs.store.demand.domain.bo.StoreDemandBatchBo;
import org.dromara.djs.store.demand.domain.bo.StoreDemandQuantityBo;
import org.dromara.djs.store.demand.domain.vo.StoreDemandCatalogVo;
import org.dromara.djs.store.demand.domain.vo.StoreDemandDayVo;
import org.dromara.djs.store.demand.service.IStoreDemandAppletService;
import org.dromara.djs.store.demand.service.IStoreDemandService;
import org.dromara.djs.warehouse.demand.core.StoreDemandStatusMapping;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.demand.domain.vo.StoreDemandDayAggVo;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.demand.service.IDemandManageService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.service.IProductDisplayNameResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 门店小程序需求服务实现（V6 row62-70）。
 *
 * <p>四件事：按天聚合卡（row66）/ 某天明细（row70）/ 下单目录（row68）/ 改量删行（row70）。
 * 门店态口径全部走 {@link StoreDemandStatusMapping}（读与筛同一套规则），删行走
 * {@link IDemandManageService#deleteWithValidByIds}（不写第二套删除）。</p>
 *
 * @author djs
 * @since STORE-MP-BOARD-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreDemandAppletServiceImpl implements IStoreDemandAppletService {

    /** 当天整体状态：已到店（S ⊆ {ARRIVED}）。 */
    static final String DAY_STATUS_ARRIVED = "ARRIVED";
    /** 当天整体状态：已发货（S ⊆ {SHIPPED, ARRIVED}）。 */
    static final String DAY_STATUS_SHIPPED = "SHIPPED";
    /** 当天整体状态：需求生产中（S ⊆ {CONFIRMED, SHIPPED, ARRIVED}）。 */
    static final String DAY_STATUS_IN_PRODUCTION = "IN_PRODUCTION";
    /** 当天整体状态：需求确认中（其余，即 S 含 SUBMITTED / 其他未知态）。 */
    static final String DAY_STATUS_CONFIRMING = "CONFIRMING";

    /** 确认率小数位（与 admin {@code DemandGroupVo.confirmRate} 一致）。 */
    private static final int CONFIRM_RATE_SCALE = 4;

    /** 目录返回上限（一次返全量、不分页，超限截断并告警）。 */
    private static final int CATALOG_LIMIT = 1000;

    /** 产品类型 {@code djs_product_type}：1=自产（含礼盒）—— 门店唯一可下单的产品类型。 */
    private static final int PRODUCT_TYPE_SELF_PRODUCED = 1;
    /** 产品属性 {@code djs_product_attr}：1=生产产品（成品）。 */
    private static final int PRODUCT_ATTR_FINISHED = 1;
    /** 产品状态 {@code product_status}：0=启用。 */
    private static final int PRODUCT_STATUS_ENABLED = 0;

    /** 归属类型（{@code djs_belong_type}）：白条。 */
    private static final String BELONG_WHITE_BAR = "white_bar";
    /** 归属类型：果蔬。 */
    private static final String BELONG_VEGETABLE = "vegetable";
    /** 归属类型：礼盒。 */
    private static final String BELONG_GIFT_BOX = "gift_box";
    /** 落库业态兜底值（猪肉 / 干货 / 鸡蛋 / 其他全部映射到它）。 */
    private static final String DEMAND_TYPE_OTHER = "other";

    /**
     * 目录展示的品类顺序（与 admin 购物车 7 tab 次序一致：白条 / 猪肉 / 果蔬 / 干货 / 鸡蛋 / 礼盒 / 其他）。
     * 不在表里的 belongType（含空）排最后 = 「其他」桶。
     */
    private static final List<String> BELONG_TYPE_ORDER =
        List.of(BELONG_WHITE_BAR, "pork", BELONG_VEGETABLE, "dry_good", "egg", BELONG_GIFT_BOX);

    private final DemandManageMapper demandManageMapper;

    private final IDemandManageService demandManageService;

    private final ProductInfoMapper productInfoMapper;

    private final IProductDisplayNameResolver displayNameResolver;

    private final ImageUrlResolver imageUrlResolver;

    private final ICropPlotStatService cropPlotStatService;

    private final IStoreUserRelationService storeUserRelationService;

    /** 整单落库仍复用 admin/mp 共用的那条路径（本类只在它之上加 mp 侧闸，不复写业务逻辑）。 */
    private final IStoreDemandService storeDemandService;

    // ---------------- row66 按天聚合 ----------------

    @Override
    public TableDataInfo<StoreDemandDayVo> queryDayList(Long storeId, LocalDate beginDate, LocalDate endDate,
                                                        PageQuery pageQuery) {
        PageQuery pq = pageQuery != null ? pageQuery : new PageQuery(1, 10);
        Page<StoreDemandDayAggVo> page =
            demandManageMapper.selectStoreDemandDayPage(pq.build(), storeId, beginDate, endDate);
        List<StoreDemandDayVo> rows = new ArrayList<>(page.getRecords().size());
        for (StoreDemandDayAggVo agg : page.getRecords()) {
            rows.add(toDayVo(agg));
        }
        TableDataInfo<StoreDemandDayVo> rsp = new TableDataInfo<>();
        rsp.setRows(rows);
        rsp.setTotal(page.getTotal());
        rsp.setCode(200);
        rsp.setMsg("查询成功");
        return rsp;
    }

    /** 聚合行 → 卡片 VO（派生 dayStatus + confirmRate）。 */
    private StoreDemandDayVo toDayVo(StoreDemandDayAggVo agg) {
        int total = nz(agg.getTotalCount());
        int arrived = nz(agg.getArrivedCount());
        int shipped = nz(agg.getShippedCount());
        int confirmed = nz(agg.getConfirmedCount());
        StoreDemandDayVo vo = new StoreDemandDayVo();
        vo.setDemandDate(agg.getDemandDate());
        vo.setStoreId(agg.getStoreId());
        vo.setOrdererName(agg.getOrdererName());
        vo.setCategoryCount(nz(agg.getCategoryCount()));
        vo.setDamagedCount(nz(agg.getDamagedCount()));
        vo.setLastOrderTime(agg.getLastOrderTime());
        vo.setDayStatus(dayStatus(total, arrived, shipped, confirmed));
        vo.setConfirmRate(confirmRate(total, arrived + shipped + confirmed));
        return vo;
    }

    /**
     * 当天整体状态阶梯（V6 row66）。入参为当天<b>非 DELETED</b> 行按门店态分桶的计数。
     *
     * <p>「其余」桶 = {@code total - arrived - shipped - confirmed}，装的是 SUBMITTED 以及理论上不该出现在
     * 门店端的态（DRAFT 等）——它们一律把当天压回「需求确认中」，即最保守的一档。</p>
     *
     * <p>甲方原文「需求生产中 = 需求全部完成确认，且有产品已开始生产完成」：系统里
     * {@code DemandStatus.IN_PRODUCTION} 已废弃、存量回迁 CONFIRMED，没有可读的「生产中」信号，
     * 「全部确认了但还没生产」在原文里是空档，故按「全部确认即生产中」收口（生产是确认后的必然下一步），
     * <b>已登记为待甲方复核的假设</b>。</p>
     *
     * @param total     当天需求单总数
     * @param arrived   门店态 ARRIVED 的单数
     * @param shipped   门店态 SHIPPED 的单数
     * @param confirmed 门店态 CONFIRMED 的单数
     * @return ARRIVED / SHIPPED / IN_PRODUCTION / CONFIRMING
     */
    static String dayStatus(int total, int arrived, int shipped, int confirmed) {
        int others = total - arrived - shipped - confirmed;
        if (total <= 0 || others > 0) {
            // 没有行（理论不出现，SQL 已排除空组）或含 SUBMITTED / 未知态 → 最保守一档
            return DAY_STATUS_CONFIRMING;
        }
        if (shipped == 0 && confirmed == 0) {
            return DAY_STATUS_ARRIVED;
        }
        if (confirmed == 0) {
            return DAY_STATUS_SHIPPED;
        }
        return DAY_STATUS_IN_PRODUCTION;
    }

    /**
     * 需求确认率 = 已确认需求单数 / 需求单总数（4 位小数，HALF_UP），与 admin
     * {@code DemandGroupVo.confirmRate} 同口径（按<b>需求单</b>不是按门店）。
     *
     * @param total     需求单总数（分母）
     * @param confirmed 已确认需求单数（门店态 ∈ {CONFIRMED, SHIPPED, ARRIVED}）
     * @return 0~1；分母 ≤ 0 返 0
     */
    static BigDecimal confirmRate(int total, int confirmed) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(confirmed)
            .divide(BigDecimal.valueOf(total), CONFIRM_RATE_SCALE, RoundingMode.HALF_UP);
    }

    // ---------------- row70 某天明细 ----------------

    @Override
    public List<DemandManageVo> queryDayDetail(Long storeId, LocalDate demandDate, String productName,
                                               String storeStatuses) {
        if (storeId == null) {
            throw new ServiceException("门店不能为空", 400);
        }
        if (demandDate == null) {
            throw new ServiceException("需求日期不能为空", 400);
        }
        // 门店态多选 → SQL 片段（未知态 / DELETED 由 mapping 直接拒，不做静默丢弃）
        String storeStatusSql = StoreDemandStatusMapping.sqlPredicateAny(splitCsv(storeStatuses));
        LambdaQueryWrapper<DemandManage> wrapper = new LambdaQueryWrapper<DemandManage>()
            .eq(DemandManage::getStoreId, storeId)
            .eq(DemandManage::getDemandDate, demandDate)
            .like(StringUtils.isNotBlank(productName), DemandManage::getProductName, productName)
            // 门店端永不返回门店态 DELETED 的行（软删行另由 del_flag 自动排除）
            .notIn(DemandManage::getDemandStatus, "DELETED", "CANCELLED")
            .orderByAsc(DemandManage::getId);
        if (storeStatusSql != null) {
            // 片段是 mapping 产出的常量字符串，不含任何用户输入，无注入面
            wrapper.apply(storeStatusSql);
        }
        List<DemandManageVo> rows = demandManageMapper.selectVoList(wrapper);
        for (DemandManageVo vo : rows) {
            vo.setStoreDemandStatus(
                StoreDemandStatusMapping.derive(vo.getDemandStatus(), vo.getReceivedTime() != null));
        }
        fillProductPresentation(rows);
        return rows;
    }

    /** 批量回填 {@code belongType} + {@code imageUrl}（详情卡要显品类和产品图；禁 N+1）。 */
    private void fillProductPresentation(List<DemandManageVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> productIds = rows.stream()
            .map(DemandManageVo::getProductId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (productIds.isEmpty()) {
            return;
        }
        List<ProductInfo> products = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
            .in(ProductInfo::getId, productIds));
        Map<Long, ProductInfo> byId = new HashMap<>(products.size());
        for (ProductInfo p : products) {
            byId.put(p.getId(), p);
        }
        // 一次批量解析所有 ossId（与 admin 产品列表同口径：product_thumb 优先，缺失回落 image_oss_id）
        List<ImageUrlResolver.Item> items = new ArrayList<>(products.size());
        for (ProductInfo p : products) {
            items.add(new ImageUrlResolver.Item(pickImageOssId(p), p.getBelongType()));
        }
        List<String> urls = imageUrlResolver.resolveList(items);
        Map<Long, String> urlById = new HashMap<>(products.size());
        if (urls.size() == products.size()) {
            for (int i = 0; i < products.size(); i++) {
                urlById.put(products.get(i).getId(), urls.get(i));
            }
        }
        for (DemandManageVo vo : rows) {
            ProductInfo p = vo.getProductId() == null ? null : byId.get(vo.getProductId());
            if (p == null) {
                continue;
            }
            vo.setBelongType(p.getBelongType());
            vo.setImageUrl(urlById.get(p.getId()));
        }
    }

    // ---------------- row68 下单目录 ----------------

    @Override
    public List<StoreDemandCatalogVo> queryCatalog(Long storeId, String keyword) {
        if (storeId == null) {
            throw new ServiceException("门店不能为空", 400);
        }
        List<ProductInfo> products = productInfoMapper.selectList(buildCatalogWrapper(keyword));
        if (products.isEmpty()) {
            return List.of();
        }
        if (products.size() >= CATALOG_LIMIT) {
            log.warn("[STORE-MP-BOARD-001] 下单目录命中上限 {} 条（keyword={}），可能有产品未返回", CATALOG_LIMIT, keyword);
        }
        List<Long> productIds = products.stream().map(ProductInfo::getId).filter(Objects::nonNull).distinct().toList();

        // 展示名（果蔬按有机证书取产品名 / 别名，与下单定格同源）
        Map<Long, String> displayNames = displayNameResolver.resolveDisplayNames(productIds);
        // 产品图（product_thumb 优先，缺失回落 image_oss_id）
        List<ImageUrlResolver.Item> items = new ArrayList<>(products.size());
        for (ProductInfo p : products) {
            items.add(new ImageUrlResolver.Item(pickImageOssId(p), p.getBelongType()));
        }
        List<String> urls = imageUrlResolver.resolveList(items);
        // 果蔬最早采摘日：作物 → 产品是反向关联（crop.related_product），同一产品可能被多条作物指向，取 min
        Map<Long, String> earliestPickByProduct = buildEarliestPickDateIndex();
        // 本店最近一次下单该产品的时间
        Map<Long, String> lastOrderByProduct = new HashMap<>();
        for (Map<String, Object> row : demandManageMapper.selectLastOrderTimeByStore(storeId, productIds)) {
            Object pid = row.get("productId");
            Object time = row.get("lastOrderTime");
            if (pid != null && time != null) {
                lastOrderByProduct.put(((Number) pid).longValue(), time.toString());
            }
        }

        List<StoreDemandCatalogVo> list = new ArrayList<>(products.size());
        for (int i = 0; i < products.size(); i++) {
            ProductInfo p = products.get(i);
            StoreDemandCatalogVo vo = new StoreDemandCatalogVo();
            vo.setProductId(p.getId());
            String display = displayNames.get(p.getId());
            vo.setProductName(StringUtils.isNotBlank(display) ? display : p.getProductName());
            vo.setProductSpec(p.getProductSpec());
            vo.setProductUnit(p.getProductUnit());
            vo.setBelongType(p.getBelongType());
            vo.setImageUrl(urls.size() == products.size() ? urls.get(i) : null);
            vo.setDemandProductType(toDemandProductType(p.getBelongType()));
            if (BELONG_VEGETABLE.equals(p.getBelongType())) {
                // 加工果蔬成品挂原材料基础菜（productMaterial），基础果蔬自身即原材料 —— 与 admin
                // cropStatOf(product) 的 `relatedProduct == (productMaterial ?? id)` 逐字一致
                Long cropKey = p.getProductMaterial() != null ? p.getProductMaterial() : p.getId();
                vo.setEarliestPickDate(earliestPickByProduct.get(cropKey));
            }
            vo.setLastOrderTime(lastOrderByProduct.get(p.getId()));
            list.add(vo);
        }
        list.sort(catalogComparator());
        return list.size() > CATALOG_LIMIT ? list.subList(0, CATALOG_LIMIT) : list;
    }

    /**
     * 目录候选产品过滤（逐字沿用 admin 购物车 {@code DemandCartDrawer.vue} 的口径）：
     * 启用（{@code product_status=0}）+ 自产（{@code product_type=1}）+ 除白条 / 礼盒外只取成品（{@code product_attr=1}）。
     *
     * <p>两条豁免的理由（原样沿用 admin）：白条整只/半只在仓库域建模为原材料（attr=2），但门店订白条→现场分割，
     * 可订；礼盒的 product_attr 在商品配置表单非必填，历史礼盒可能为空，按白名单会被误伤。</p>
     *
     * <p>keyword 同时匹配 {@code product_name} 与 {@code product_alias} —— 果蔬无证时展示的是别名，
     * 只匹配产品名会出现「屏幕上明明叫这个名字却搜不到」。</p>
     */
    private LambdaQueryWrapper<ProductInfo> buildCatalogWrapper(String keyword) {
        return new LambdaQueryWrapper<ProductInfo>()
            .eq(ProductInfo::getProductStatus, PRODUCT_STATUS_ENABLED)
            .eq(ProductInfo::getProductType, PRODUCT_TYPE_SELF_PRODUCED)
            .and(w -> w.in(ProductInfo::getBelongType, BELONG_WHITE_BAR, BELONG_GIFT_BOX)
                .or().eq(ProductInfo::getProductAttr, PRODUCT_ATTR_FINISHED))
            .and(StringUtils.isNotBlank(keyword), w -> w
                .like(ProductInfo::getProductName, keyword)
                .or().like(ProductInfo::getProductAlias, keyword))
            .orderByDesc(ProductInfo::getId)
            .last("LIMIT " + CATALOG_LIMIT);
    }

    /**
     * 目录排序（V6 row68）。
     *
     * <ol>
     *   <li>先按品类分组（{@link #BELONG_TYPE_ORDER}，与 admin 购物车 7 tab 次序一致）——
     *       mp 左侧品类栏按 belongType 切片，组内有序才有意义；</li>
     *   <li>果蔬组：{@code earliestPickDate} 升序、无日期沉底（先能采的排前面）；</li>
     *   <li>其余组：{@code lastOrderTime} 倒序、无记录沉底（甲方原文「按历史下单进行基础排序」口径未定，
     *       此处按「本店最近下单时间倒序」实现，<b>待甲方复核</b>）；</li>
     *   <li>最后一律 {@code productId} 倒序收口 —— 没有这一层，两个排序键都为空时（新库 0 条需求 /
     *       果蔬没在种计划）顺序退化成 DB 返回序，翻页和重复请求可能不同序。</li>
     * </ol>
     */
    private Comparator<StoreDemandCatalogVo> catalogComparator() {
        return (a, b) -> {
            int byGroup = Integer.compare(belongRank(a.getBelongType()), belongRank(b.getBelongType()));
            if (byGroup != 0) {
                return byGroup;
            }
            boolean veg = BELONG_VEGETABLE.equals(a.getBelongType());
            int byKey = veg
                // 日期是定长 yyyy-MM-dd，字典序 == 时间序
                ? nullsLastAsc(a.getEarliestPickDate(), b.getEarliestPickDate())
                : nullsLastDesc(a.getLastOrderTime(), b.getLastOrderTime());
            if (byKey != 0) {
                return byKey;
            }
            return Long.compare(nzLong(b.getProductId()), nzLong(a.getProductId()));
        };
    }

    /** 品类展示序；未知 / 空 belongType 落「其他」桶排最后。 */
    private static int belongRank(String belongType) {
        int idx = belongType == null ? -1 : BELONG_TYPE_ORDER.indexOf(belongType);
        return idx < 0 ? BELONG_TYPE_ORDER.size() : idx;
    }

    /** 升序比较，null 沉底。 */
    private static int nullsLastAsc(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return a.compareTo(b);
    }

    /** 倒序比较，null 沉底。 */
    private static int nullsLastDesc(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return b.compareTo(a);
    }

    /**
     * 「基础果蔬产品 → 最早可采摘日」索引。
     *
     * <p>同一基础产品可能被多条作物指向（如「甘蓝」挂两条作物），日期取 min ——
     * 直接按 relatedProduct 建 Map 会互相覆盖（与 admin {@code buildCropPlotStatByProduct} 同口径）。</p>
     */
    private Map<Long, String> buildEarliestPickDateIndex() {
        Map<Long, String> map = new HashMap<>();
        List<CropPlotStatVo> stats = cropPlotStatService.listPlotStat();
        if (stats == null) {
            return map;
        }
        for (CropPlotStatVo row : stats) {
            Long key = row.getRelatedProduct();
            String date = row.getEarliestPickDate();
            if (key == null || StringUtils.isBlank(date)) {
                continue;
            }
            String exists = map.get(key);
            if (exists == null || date.compareTo(exists) < 0) {
                map.put(key, date);
            }
        }
        return map;
    }

    /**
     * 产品归属 → 下单落库业态（仓库域 4 值）。
     *
     * <p>逐字沿用 admin 购物车 {@code DemandCartDrawer.vue} TABS 的映射：白条 / 果蔬 / 礼盒各自成业态，
     * 猪肉 / 干货 / 鸡蛋 / 其他全部落 {@code other}。<b>不要「改进」成后端白名单的 7 值</b>——
     * 需求单号的 bizCode 段与下游分拣发货的业态筛选都吃这个值，两端口径会分叉。</p>
     */
    static String toDemandProductType(String belongType) {
        if (BELONG_WHITE_BAR.equals(belongType) || BELONG_VEGETABLE.equals(belongType)
            || BELONG_GIFT_BOX.equals(belongType)) {
            return belongType;
        }
        return DEMAND_TYPE_OTHER;
    }

    // ---------------- row69 整单下单（mp 侧三道闸） ----------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchCreate(StoreDemandBatchBo bo) {
        // ① 需求日期不得早于今天（甲方 row69「最早只能是当天」）。
        //    前端 picker 的 min-date 与提交前钳制都基于**设备本地时间**，手机时钟错了就跟着错；
        //    直连接口更是零阻力（独立验收实测：传昨天日期照样 200 落库）。
        LocalDate demandDate = bo.getDemandDate();
        if (demandDate != null && demandDate.isBefore(LocalDate.now())) {
            throw new ServiceException("需求日期不能早于今天：" + demandDate, 400);
        }

        List<StoreDemandBatchBo.Item> items = bo.getItems();
        if (items == null || items.isEmpty()) {
            throw new ServiceException("需求产品不能为空", 400);
        }

        // ②③ 逐行按产品主数据收口：业态服务端推导 + 原材料一律拒
        List<Long> productIds = items.stream().map(StoreDemandBatchBo.Item::getProductId)
            .filter(Objects::nonNull).distinct().toList();
        Map<Long, ProductInfo> productMap = productIds.isEmpty()
            ? Map.of()
            : productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .in(ProductInfo::getId, productIds))
            .stream().collect(HashMap::new, (m, p) -> m.put(p.getId(), p), HashMap::putAll);

        for (StoreDemandBatchBo.Item item : items) {
            ProductInfo product = productMap.get(item.getProductId());
            if (product == null) {
                throw new ServiceException("产品不存在或已删除：" + item.getProductId(), 404);
            }
            assertNotRawMaterialForApplet(product);
            // ② 业态一律由服务端按 belong_type 推导，不信客户端传值 —— 这个字段决定需求单号的
            //    bizCode 段与下游分拣发货的业态筛选，客户端把果蔬声明成 gift_box 就能拿到礼盒段单号。
            item.setProductType(toDemandProductType(product.getBelongType()));
        }

        // ④ 同门店 + 同需求日 + 同产品 + 仍「待确认」→ **并进已有那一行**（累加），不新建第二条。
        //    甲方 row69「确认后订单整合到对应日期的需求内容」+「一天只显示一条数据」：只靠列表层按天聚合
        //    不够——详情页会出现「产品品数 1、底下两张同名卡、删一张品数还是 1」的自相矛盾。
        //    只并「待确认」的行：已确认/已发货的需求仓库那边已经在排产，回头改它的量等于篡改已受理单据。
        int merged = 0;
        List<StoreDemandBatchBo.Item> toCreate = new ArrayList<>(items.size());
        LocalDate targetDate = demandDate != null ? demandDate : LocalDate.now();
        for (StoreDemandBatchBo.Item item : items) {
            DemandManage exist = findMergeableRow(bo.getStoreId(), targetDate, item.getProductId());
            if (exist == null) {
                toCreate.add(item);
                continue;
            }
            BigDecimal sum = nzq(exist.getDemandQuantity()).add(item.getDemandQuantity());
            DemandManageBo patch = new DemandManageBo();
            patch.setId(exist.getId());
            patch.setDemandQuantity(sum);
            demandManageService.updateByBo(patch);
            merged++;
            log.info("[STORE-MP-BOARD-001] 同日同产品并单 id={} no={} {} + {} = {}",
                exist.getId(), exist.getDemandNo(), exist.getDemandQuantity(), item.getDemandQuantity(), sum);
        }
        if (toCreate.isEmpty()) {
            return merged;
        }
        bo.setItems(toCreate);
        return storeDemandService.batchCreate(bo) + merged;
    }

    /**
     * 找同门店 + 同需求日 + 同产品 + 门店态「待确认」的可并行（没有则 null）。
     *
     * <p>只认 {@code demand_status='SUBMITTED'}：这正是门店态 SUBMITTED 的充要条件
     * （见 {@link StoreDemandStatusMapping}，SUBMITTED 不受 received_time 影响），
     * 也与 row70 改量闸「仅待确认可改」逐条一致 —— 用户能自己改的行，才是能被并单的行。</p>
     *
     * <p>理论上同一天同产品最多只该有一条待确认行（并单本身保证了这点）；历史数据里若有多条，
     * 取 id 最小的那条并进去，剩下的留着不动（不替用户做合并决策）。</p>
     */
    private DemandManage findMergeableRow(Long storeId, LocalDate demandDate, Long productId) {
        if (storeId == null || demandDate == null || productId == null) {
            return null;
        }
        return demandManageMapper.selectOne(new LambdaQueryWrapper<DemandManage>()
            .eq(DemandManage::getStoreId, storeId)
            .eq(DemandManage::getDemandDate, demandDate)
            .eq(DemandManage::getProductId, productId)
            .eq(DemandManage::getDemandStatus, StoreDemandStatusMapping.SUBMITTED)
            .orderByAsc(DemandManage::getId)
            .last("LIMIT 1"));
    }

    /** BigDecimal null → 0（并单累加时旧行数量理论非空，兜底防 NPE）。 */
    private static BigDecimal nzq(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * mp 侧原材料闸：{@code product_attr=2} 一律拒，白条 / 礼盒豁免。
     *
     * <p>与 {@link #buildCatalogWrapper} 的候选谓词<b>逐字相同</b> —— 目录里选不到的东西，
     * 直连接口也不能下单。共用的 {@code DemandManageServiceImpl} 那道黑名单只覆盖
     * egg/dry_good/other/vegetable，漏了 pork，导致「猪肉原料在目录里没有、直连接口却能下单」。
     * 本轮只收口 mp 这一侧，admin 既有行为不动（它有自己的操作员与审核链路）。</p>
     */
    private void assertNotRawMaterialForApplet(ProductInfo product) {
        String belongType = product.getBelongType();
        if (BELONG_WHITE_BAR.equals(belongType) || BELONG_GIFT_BOX.equals(belongType)) {
            return;
        }
        Integer attr = product.getProductAttr();
        if (attr != null && attr != PRODUCT_ATTR_FINISHED) {
            throw new ServiceException(
                "产品【" + product.getProductName() + "】是原材料，不能被门店下单", 400);
        }
    }

    // ---------------- row70 改量 / 删行 ----------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(StoreDemandQuantityBo bo) {
        DemandManage demand = demandManageMapper.selectById(bo.getId());
        if (demand == null) {
            throw new ServiceException("需求不存在或已删除：" + bo.getId(), 404);
        }
        if (!storeUserRelationService.isStoreAccessible(currentUserIdSafe(), demand.getStoreId())) {
            throw new ServiceException("无权操作该门店的需求", 403);
        }
        String storeStatus = StoreDemandStatusMapping.derive(
            demand.getDemandStatus(), demand.getReceivedTime() != null);
        if (!StoreDemandStatusMapping.SUBMITTED.equals(storeStatus)) {
            throw new ServiceException("仅「待确认」的需求可修改数量或删除，当前状态："
                + StoreDemandStatusMapping.labelOf(storeStatus), 400);
        }
        if (bo.getDemandQuantity().signum() == 0) {
            // 删除走仓库域既有路径（置 DELETED 终态 + 软删 + 级联软删指定猪只），不写第二套
            demandManageService.deleteWithValidByIds(List.of(bo.getId()));
            log.info("[STORE-MP-BOARD-001] 门店删除需求 id={} no={}", bo.getId(), demand.getDemandNo());
            return;
        }
        DemandManageBo patch = new DemandManageBo();
        patch.setId(bo.getId());
        patch.setDemandQuantity(bo.getDemandQuantity());
        demandManageService.updateByBo(patch);
        log.info("[STORE-MP-BOARD-001] 门店改需求量 id={} no={} qty={}",
            bo.getId(), demand.getDemandNo(), bo.getDemandQuantity());
    }

    // ---------------- 内部辅助 ----------------

    /**
     * 当前登录人 ID；无登录上下文（mp mock token 等）时返 null。
     *
     * <p>只喂给 {@link IStoreUserRelationService#isStoreAccessible}——关墙时它不看 userId 直接放行，
     * 开墙后 null 会被判成「无绑定」而拒绝，是安全侧的失败方向。</p>
     */
    private Long currentUserIdSafe() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            log.debug("[STORE-MP-BOARD-001] 取当前登录人失败（无登录上下文），按未绑定处理: {}", e.getMessage());
            return null;
        }
    }

    /** 逗号分隔串 → 去空白列表；空串返 null（= 不加该筛选条件）。 */
    private static List<String> splitCsv(String csv) {
        if (StringUtils.isBlank(csv)) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String s : Arrays.asList(csv.split(","))) {
            if (StrUtil.isNotBlank(s)) {
                parts.add(s.trim());
            }
        }
        return parts.isEmpty() ? null : parts;
    }

    /** 取图 ossId：商品配置缩略图优先，缺失回落历史主图（与 admin 产品列表口径一致）。 */
    private static String pickImageOssId(ProductInfo p) {
        return StrUtil.isNotBlank(p.getProductThumb()) ? p.getProductThumb() : p.getImageOssId();
    }

    /** null → 0。 */
    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /** null → 0L。 */
    private static long nzLong(Long v) {
        return v == null ? 0L : v;
    }
}
