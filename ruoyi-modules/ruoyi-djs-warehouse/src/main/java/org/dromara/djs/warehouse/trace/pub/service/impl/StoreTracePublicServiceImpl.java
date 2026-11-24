package org.dromara.djs.warehouse.trace.pub.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.service.OssService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.trace.domain.TraceCode;
import org.dromara.djs.warehouse.trace.domain.TracePageConfig;
import org.dromara.djs.warehouse.trace.domain.TraceCodeTypeConst;
import org.dromara.djs.warehouse.trace.mapper.TraceCodeMapper;
import org.dromara.djs.warehouse.trace.mapper.TracePageConfigMapper;
import org.dromara.djs.warehouse.trace.pub.domain.vo.PublicTraceVo;
import org.dromara.djs.warehouse.trace.pub.service.IStoreTracePublicService;
import org.dromara.djs.warehouse.trace.pub.service.ITracePublicService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
 * 门店级猪肉动态追溯（V7 row40）。
 *
 * <h3>选猪规则（甲方原文落到 as-built 数据）</h3>
 * <ul>
 *   <li><b>门店「当天」的猪肉货品</b> = 本门店需求单 {@code t_warehouse_demand_manage.received_time}
 *       落在今天（Asia/Shanghai）—— 即门店今天确认收货 —— 的已发货清点产出行
 *       {@code t_warehouse_product_production}（{@code is_delivery_check=1}、未标损 {@code is_damaged=0}、
 *       {@code ear_no} 非空）。与门店猪肉打包选猪 {@code StoreTraceServiceImpl#listTraceablePigs} 同一口径。</li>
 *   <li><b>白条 / 猪肉产品</b> 按产品档案 {@code t_warehouse_product_info.belong_type}：{@code white_bar} / {@code pork}。</li>
 *   <li><b>重量</b> = 产出行 {@code product_weight}（kg，出库/打包实际称重，也就是到店重量）。
 *       白条按耳号（一头猪）合计——同一头猪分两个半只到店时算这头猪的白条总重；
 *       猪肉产品按单件比较（「所有猪肉产品里重量最大的那个」）。</li>
 *   <li>有白条 → 取白条总重最大的那头猪；没有白条 → 取单件最重的猪肉产品的耳号。
 *       同重优先最近到店（D-0190：白条取同一耳号有效半扇的最晚确认收货时间），再按耳号字典序取小者。</li>
 *   <li>选中耳号后的展示数据：优先用当天这批货自己的追溯码（产出行 {@code trace_code}），
 *       缺码的历史行回落该耳号最新的猪肉追溯码；都没有就顺延下一名（白条都取不到码时顺延到猪肉产品）。</li>
 * </ul>
 *
 * <h3>与按码追溯的差异（只在本入口生效）</h3>
 * <ul>
 *   <li>父系 / 母系任一方耳号缺失 → 整个 {@code pedigree} 置 null（前端整块不显示）；</li>
 *   <li>{@code store} 恒为二维码所属门店；{@code product} 只保留追溯码（H5 下钻子页用），
 *       不展示某一件货的品名 / 重量。</li>
 * </ul>
 *
 * <h3>缓存</h3>
 * <p>只缓存「今天选中了哪个追溯码」（key {@code trace:public-store:<storeId>:<yyyyMMdd>}，3 分钟，
 * RedisUtils：匿名公开请求写入的键不带租户前缀，带登录态访问时会多写一份 `1001:` 前缀的键，两份互不影响），追溯详情本身仍走 {@link ITracePublicService#getByProduceCode} 自己的缓存与运行期配置回填。
 * 日期入键：跨天自动换新；TTL 短：门店当天新收一批货后几分钟内二维码就换成新的最重那头猪。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreTracePublicServiceImpl implements IStoreTracePublicService {

    /** 选中追溯码缓存前缀（与按码缓存 {@code trace:public:<code>} 分开，避免与追溯码撞键）。 */
    private static final String CACHE_PREFIX = "trace:public-store:arrival-v2:";

    /** 选中结果缓存 TTL。 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(3);

    /** 「当天没有可追溯的猪」的缓存占位（追溯码不会是这个值）。 */
    static final String NONE = "-";

    /** 「今天」时区（与门店打包选猪 / 发货月台一致）。 */
    private static final ZoneId TODAY_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.BASIC_ISO_DATE;

    static final String BELONG_TYPE_WHITE_BAR = "white_bar";
    static final String BELONG_TYPE_PORK = "pork";

    /** product_production.is_delivery_check=1：已发货清点。 */
    private static final Integer DELIVERY_CHECKED = 1;
    /** product_production.is_damaged=0：未被门店标损。 */
    private static final Integer NOT_DAMAGED = 0;

    private final StoreMapper storeMapper;
    private final DemandManageMapper demandManageMapper;
    private final ProductProductionMapper productProductionMapper;
    private final ProductInfoMapper productInfoMapper;
    private final TraceCodeMapper traceCodeMapper;
    private final ITracePublicService tracePublicService;
    private final OssService ossService;
    private final TracePageConfigMapper tracePageConfigMapper;

    @Override
    public PublicTraceVo getByStoreKey(String storeKey) {
        if (StringUtils.isBlank(storeKey)) {
            return null;
        }
        String code = storeKey.trim();
        // @SaIgnore 无登录 → 无租户上下文，查库一律 ignore（V1 单租户 '1001'）
        Store store = TenantHelper.ignore(() -> storeMapper.selectOne(
            new LambdaQueryWrapper<Store>().eq(Store::getTenantId, "1001").eq(Store::getStoreCode, code).last("limit 1")));
        if (store == null) {
            return null;
        }
        LocalDate today = today();
        String cacheKey = CACHE_PREFIX + store.getId() + ":" + today.format(DAY_KEY);
        String produceCode = readCache(cacheKey);
        if (produceCode == null) {
            String picked = TenantHelper.ignore(() -> pickProduceCode(store.getId(), today));
            produceCode = picked == null ? NONE : picked;
            writeCache(cacheKey, produceCode);
        }
        PublicTraceVo vo = NONE.equals(produceCode) ? null : tracePublicService.getByProduceCode(produceCode);
        if (vo == null || !TraceCodeTypeConst.PORK.equals(vo.getCodeType()) || vo.getPig() == null) {
            vo = emptyVo();
        } else {
            adaptForStore(vo, produceCode);
        }
        vo.setStore(buildStoreBlock(store));
        if (vo.getPig() == null) {
            TracePageConfig config = TenantHelper.ignore(() -> tracePageConfigMapper.selectOne(
                new LambdaQueryWrapper<TracePageConfig>()
                    .eq(TracePageConfig::getTenantId, "1001")
                    .eq(TracePageConfig::getCodeType, TraceCodeTypeConst.PORK).last("limit 1")));
            if (config != null && StringUtils.isNotBlank(config.getBaseIntroImageOssId())) {
                vo.setBaseIntroImageUrl(firstOssUrl(config.getBaseIntroImageOssId()));
            }
        }
        return vo;
    }

    /** 今天（Asia/Shanghai）。protected：单测固定日期。 */
    protected LocalDate today() {
        return LocalDate.now(TODAY_ZONE);
    }

    /** 读选中结果缓存（protected：RedisUtils 静态初始化在无 Spring 单测环境不可用）。 */
    protected String readCache(String key) {
        return RedisUtils.getCacheObject(key);
    }

    /** 写选中结果缓存。 */
    protected void writeCache(String key, String produceCode) {
        RedisUtils.setCacheObject(key, produceCode, CACHE_TTL);
    }

    // ============================ 选猪 ============================

    /**
     * 选出门店当天最重那头猪的追溯码；当天没有可追溯的猪肉货品返 null。
     */
    String pickProduceCode(Long storeId, LocalDate day) {
        List<DemandManage> demands = demandManageMapper.selectList(
                new LambdaQueryWrapper<DemandManage>()
                    .eq(DemandManage::getTenantId, "1001")
                    .eq(DemandManage::getStoreId, storeId)
                    .ge(DemandManage::getReceivedTime, day.atStartOfDay())
                    .lt(DemandManage::getReceivedTime, day.plusDays(1).atStartOfDay())
                    .select(DemandManage::getId, DemandManage::getReceivedTime));
        List<Long> demandIds = demands.stream().map(DemandManage::getId).filter(Objects::nonNull).distinct().toList();
        if (demandIds.isEmpty()) {
            return null;
        }
        Map<Long, LocalDateTime> receivedByDemand = demands.stream()
            .filter(d -> d.getId() != null && d.getReceivedTime() != null)
            .collect(Collectors.toMap(DemandManage::getId, DemandManage::getReceivedTime, (a, b) -> a.isAfter(b) ? a : b));
        List<ProductProduction> rows = productProductionMapper.selectList(
            new LambdaQueryWrapper<ProductProduction>()
                .eq(ProductProduction::getTenantId, "1001")
                .eq(ProductProduction::getStoreId, storeId)
                .in(ProductProduction::getDemandId, demandIds)
                .eq(ProductProduction::getIsDeliveryCheck, DELIVERY_CHECKED)
                .eq(ProductProduction::getIsDamaged, NOT_DAMAGED)
                .isNotNull(ProductProduction::getEarNo)
                .ne(ProductProduction::getEarNo, "")
                .select(ProductProduction::getId, ProductProduction::getDemandId,
                    ProductProduction::getProductId, ProductProduction::getEarNo,
                    ProductProduction::getProductWeight, ProductProduction::getTraceCode));
        if (rows.isEmpty()) {
            return null;
        }
        List<Long> productIds = rows.stream().map(ProductProduction::getProductId)
            .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> belongById = productIds.isEmpty() ? Map.of()
            : productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                    .eq(ProductInfo::getTenantId, "1001")
                    .in(ProductInfo::getId, productIds)
                    .select(ProductInfo::getId, ProductInfo::getBelongType, ProductInfo::getProductName))
                .stream().filter(p -> p.getId() != null && p.getBelongType() != null)
                .collect(Collectors.toMap(ProductInfo::getId, StoreTracePublicServiceImpl::selectionClass, (a, b) -> a));

        for (Candidate c : rank(rows, belongById, receivedByDemand)) {
            String code = resolvePorkCode(c.traceCode(), c.earNo(), storeId);
            if (StringUtils.isNotBlank(code)) {
                log.debug("[trace-store] store={} day={} 选中 {} 耳号={} 重量={} code={}",
                    storeId, day, c.source(), c.earNo(), c.weight(), code);
                return code;
            }
        }
        return null;
    }

    /**
     * 选猪时的品类：产品档案里「白条·猪头 / 白条·猪蹄」的 belong_type 也是 white_bar，但甲方说的「一头白条」
     * 指整头猪的半扇；猪头、猪蹄按猪肉产品和别的货比重量，否则门店只收到一个 6kg 猪头就会压过 99kg 的猪肉产品。
     * 因此 white_bar 里名称含「猪头 / 猪蹄 / 猪脚」的归入 pork。
     */
    static String selectionClass(ProductInfo p) {
        String belong = p.getBelongType();
        if (BELONG_TYPE_WHITE_BAR.equals(belong)) {
            String name = StringUtils.defaultString(p.getProductName());
            if (name.contains("猪头") || name.contains("猪蹄") || name.contains("猪脚")) {
                return BELONG_TYPE_PORK;
            }
        }
        return belong;
    }

    /**
     * 候选排序（纯函数，单测直接覆盖）：白条候选（按耳号合计重量）在前，猪肉产品候选（单件重量）在后；
     * 组内重量降序 → 确认收货时间降序 → 耳号升序。同一耳号只出现一次（白条组优先）。
     */
    static List<Candidate> rank(List<ProductProduction> rows, Map<Long, String> belongById,
                                Map<Long, LocalDateTime> receivedByDemand) {
        Map<String, BigDecimal> barWeightByEar = new LinkedHashMap<>();
        Map<String, LocalDateTime> barReceivedByEar = new LinkedHashMap<>();
        Map<String, ProductProduction> barCodeRowByEar = new LinkedHashMap<>();
        List<ProductProduction> porkRows = new ArrayList<>();
        for (ProductProduction r : rows) {
            if (StringUtils.isBlank(r.getEarNo())) {
                continue;
            }
            String belong = belongById.get(r.getProductId());
            if (BELONG_TYPE_WHITE_BAR.equals(belong)) {
                String ear = r.getEarNo().trim();
                barWeightByEar.merge(ear, weightOf(r), BigDecimal::add);
                LocalDateTime received = receivedByDemand.get(r.getDemandId());
                if (received != null) {
                    barReceivedByEar.merge(ear, received, (a, b) -> a.isAfter(b) ? a : b);
                }
                barCodeRowByEar.merge(ear, r, StoreTracePublicServiceImpl::preferCodeRow);
            } else if (BELONG_TYPE_PORK.equals(belong)) {
                porkRows.add(r);
            }
        }
        List<Candidate> bars = barWeightByEar.entrySet().stream()
            .map(e -> new Candidate(BELONG_TYPE_WHITE_BAR, e.getKey(), e.getValue(),
                traceCodeOf(barCodeRowByEar.get(e.getKey())), barReceivedByEar.get(e.getKey())))
            .sorted(CANDIDATE_ORDER)
            .toList();
        List<Candidate> porks = porkRows.stream()
            .sorted(Comparator.comparing(StoreTracePublicServiceImpl::weightOf, Comparator.reverseOrder())
                .thenComparing(r -> receivedByDemand.get(r.getDemandId()), RECEIVED_ORDER)
                .thenComparing(r -> r.getEarNo().trim())
                .thenComparing(ProductProduction::getId, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(r -> new Candidate(BELONG_TYPE_PORK, r.getEarNo().trim(), weightOf(r), traceCodeOf(r),
                receivedByDemand.get(r.getDemandId())))
            .toList();
        List<Candidate> out = new ArrayList<>(bars.size() + porks.size());
        Set<String> seenEars = new LinkedHashSet<>();
        for (Candidate c : bars) {
            if (seenEars.add(c.earNo())) {
                out.add(c);
            }
        }
        for (Candidate c : porks) {
            if (seenEars.add(c.earNo())) {
                out.add(c);
            } else {
                // 同一耳号的猪肉产品排在后面时，若前面那条（白条组 / 更重的那件）缺码，用这件的码补上
                fillMissingCode(out, c);
            }
        }
        return out;
    }

    private static final Comparator<LocalDateTime> RECEIVED_ORDER = Comparator.nullsLast(Comparator.reverseOrder());

    private static final Comparator<Candidate> CANDIDATE_ORDER =
        Comparator.comparing(Candidate::weight, Comparator.reverseOrder())
            .thenComparing(Candidate::receivedAt, RECEIVED_ORDER).thenComparing(Candidate::earNo);

    private static void fillMissingCode(List<Candidate> out, Candidate extra) {
        if (StringUtils.isBlank(extra.traceCode())) {
            return;
        }
        for (int i = 0; i < out.size(); i++) {
            Candidate c = out.get(i);
            if (c.earNo().equals(extra.earNo()) && StringUtils.isBlank(c.traceCode())) {
                out.set(i, new Candidate(c.source(), c.earNo(), c.weight(), extra.traceCode(), c.receivedAt()));
                return;
            }
        }
    }

    /** 同一头猪的多条白条行里取哪条的追溯码：有码优先，其次重量大，再次 id 大（最新）。 */
    private static ProductProduction preferCodeRow(ProductProduction a, ProductProduction b) {
        boolean aHas = StringUtils.isNotBlank(a.getTraceCode());
        boolean bHas = StringUtils.isNotBlank(b.getTraceCode());
        if (aHas != bHas) {
            return aHas ? a : b;
        }
        int byWeight = weightOf(a).compareTo(weightOf(b));
        if (byWeight != 0) {
            return byWeight > 0 ? a : b;
        }
        long aId = a.getId() == null ? Long.MIN_VALUE : a.getId();
        long bId = b.getId() == null ? Long.MIN_VALUE : b.getId();
        return aId >= bId ? a : b;
    }

    private static BigDecimal weightOf(ProductProduction r) {
        return r.getProductWeight() == null ? BigDecimal.ZERO : r.getProductWeight();
    }

    private static String traceCodeOf(ProductProduction r) {
        return r == null || StringUtils.isBlank(r.getTraceCode()) ? null : r.getTraceCode().trim();
    }

    /** 仅接受同租户、同耳号、本店或未绑店的仓库基础码；坏关联不能把其他店的追溯带入本店。 */
    private String resolvePorkCode(String directCode, String earNo, Long storeId) {
        if (StringUtils.isNotBlank(directCode)) {
            TraceCode direct = traceCodeMapper.selectOne(porkCodeScope(earNo, storeId)
                .eq(TraceCode::getProduceCode, directCode.trim()).last("limit 1"));
            if (direct != null) {
                return direct.getProduceCode();
            }
        }
        TraceCode latest = traceCodeMapper.selectOne(porkCodeScope(earNo, storeId)
            .orderByDesc(TraceCode::getId).last("limit 1"));
        return latest == null ? null : latest.getProduceCode();
    }

    private LambdaQueryWrapper<TraceCode> porkCodeScope(String earNo, Long storeId) {
        return new LambdaQueryWrapper<TraceCode>()
            .eq(TraceCode::getTenantId, "1001")
            .eq(TraceCode::getPigEarNo, earNo)
            .eq(TraceCode::getCodeType, TraceCodeTypeConst.PORK)
            .and(q -> q.eq(TraceCode::getStoreId, storeId).or().isNull(TraceCode::getStoreId))
            .select(TraceCode::getProduceCode);
    }

    /** 选猪候选：来源（white_bar / pork）、耳号、比较用重量 kg、可用追溯码（可空）。 */
    record Candidate(String source, String earNo, BigDecimal weight, String traceCode, LocalDateTime receivedAt) {
    }

    // ============================ 组装 ============================

    /**
     * 门店入口的展示裁剪：父母系缺一方 → 整块隐藏；产品块只留追溯码（H5 下钻生长记录 / 基地页用）。
     * 只改本次返回的对象，不回写按码追溯的缓存。
     */
    private void adaptForStore(PublicTraceVo vo, String produceCode) {
        PublicTraceVo.PedigreeBlock p = vo.getPedigree();
        if (p == null || StringUtils.isBlank(p.getSireEarNo()) || StringUtils.isBlank(p.getDamEarNo())) {
            vo.setPedigree(null);
        }
        PublicTraceVo.ProductBlock product = new PublicTraceVo.ProductBlock();
        product.setProduceCode(produceCode);
        vo.setProduct(product);
    }

    /** 当天没有可追溯的猪：pork 空态；门店和基地配置在返回前回填。 */
    private PublicTraceVo emptyVo() {
        PublicTraceVo vo = new PublicTraceVo();
        vo.setCodeType(TraceCodeTypeConst.PORK);
        vo.setTimeline(new ArrayList<>());
        vo.setGrowthRecords(new ArrayList<>());
        vo.setMedications(new ArrayList<>());
        return vo;
    }

    /** 销售门店块 = 二维码所属门店（字段口径同按码追溯的门店卡）。 */
    private PublicTraceVo.StoreBlock buildStoreBlock(Store s) {
        PublicTraceVo.StoreBlock block = new PublicTraceVo.StoreBlock();
        block.setName(s.getStoreName());
        block.setAddress(s.getAddress());
        block.setManagerPhone(s.getManagerPhone());
        block.setImageUrl(firstOssUrl(s.getImageOssId()));
        block.setManagerWechatUrl(firstOssUrl(s.getManagerWechatOssId()));
        return block;
    }

    private String firstOssUrl(Long ossId) {
        return ossId == null ? null : firstOssUrl(String.valueOf(ossId));
    }

    private String firstOssUrl(String ossIds) {
        String urls = ossService.selectUrlByIds(ossIds);
        if (StringUtils.isBlank(urls)) {
            return null;
        }
        int comma = urls.indexOf(',');
        return comma >= 0 ? urls.substring(0, comma) : urls;
    }
}
