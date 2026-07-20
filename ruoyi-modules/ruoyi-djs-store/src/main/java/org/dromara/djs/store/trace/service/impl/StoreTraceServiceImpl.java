package org.dromara.djs.store.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.common.store.context.StoreContext;
import org.dromara.djs.common.store.service.IStoreService;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.vo.PigAvailableVo;
import org.dromara.djs.breed.core.service.IPigQueryService;
import org.dromara.djs.store.trace.domain.bo.StoreTraceOnsiteBo;
import org.dromara.djs.store.trace.domain.vo.StoreOnsiteCodeVo;
import org.dromara.djs.store.trace.domain.vo.StorePackProductVo;
import org.dromara.djs.store.trace.domain.vo.TraceablePigVo;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.store.trace.service.IStoreTraceService;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.trace.domain.TraceCode;
import org.dromara.djs.warehouse.trace.mapper.TraceCodeMapper;
import org.dromara.djs.warehouse.trace.domain.query.TraceCodeQuery;
import org.dromara.djs.warehouse.trace.domain.vo.TraceCodeDetailVo;
import org.dromara.djs.warehouse.trace.domain.vo.TraceCodeListVo;
import org.dromara.djs.warehouse.trace.service.ITraceCodeAdminService;
import org.dromara.djs.warehouse.trace.service.ITraceService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 门店现场生码服务实现（STORE-TRACE-ONSITE-001）。
 *
 * <p>纯 orchestration：picker 委托养殖 {@link IPigQueryService}，生码委托仓库 {@link ITraceService}，
 * 已生码列表 / 详情 / 补打取数委托仓库 {@link ITraceCodeAdminService}。
 * 不持有任何 trace / pig 表 mapper（trace 表归 warehouse，pig 表归 breed），门店模块只编排不直读。</p>
 *
 * @author djs
 * @since STORE-TRACE-ONSITE-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreTraceServiceImpl implements IStoreTraceService {

    /** 门店追溯码恒为猪肉业态（已生码列表 / 详情口径固定 pork）。 */
    private static final String CODE_TYPE_PORK = "pork";
    /** 产品属性=生产产品（字典 djs_product_attr：1=生产产品 / 2=原材料）。 */
    private static final Integer PRODUCT_ATTR_PRODUCE = 1;
    /** 猪肉业态。 */
    private static final String BELONG_TYPE_PORK = "pork";
    /** 白条业态（现场分割 picker 取白条 production 用）。 */
    private static final String BELONG_TYPE_WHITE_BAR = "white_bar";
    /** product_production.is_delivery_check=1：已发货清点。 */
    private static final Integer DELIVERY_CHECKED = 1;
    /** TraceCodeListVo.source 值：门店现场生码（与 warehouse TraceCodeAdminServiceImpl 口径一致）。 */
    private static final String SOURCE_STORE = "store";
    /** 门店现场生码 remark 前缀（已打包重量按耳号合计时筛此前缀的 pork 码）。 */
    private static final String ONSITE_REMARK_PREFIX = "现场生码";
    /** 「今天」时区（与发货月台一致，避免非 UTC+8 实例跨日偏移）。 */
    private static final ZoneId TODAY_ZONE = ZoneId.of("Asia/Shanghai");

    private final IPigQueryService pigQueryService;
    private final ITraceService traceService;
    private final ITraceCodeAdminService traceCodeAdminService;
    private final BarInfoMapper barInfoMapper;
    private final ProductInfoMapper productInfoMapper;
    private final DemandManageMapper demandManageMapper;
    private final ProductProductionMapper productProductionMapper;
    private final TraceCodeMapper traceCodeMapper;
    private final IStoreService storeService;

    /**
     * 可追溯 picker = 当天入库白条（FIX-STORE-TRACE-BAR-001 测试问题 158）。
     *
     * <p>口径：「{@code t_warehouse_bar_info.status='in_stock'} 且 {@code DATE(in_time)=CURDATE()}」
     * 的白条（含外购）：先按白条过滤（warehouse {@link BarInfoMapper}），再按白条耳号 enrich 猪只
     * 性别 / 品种品系 / 日龄（breed {@link IPigQueryService#listPigInfoByEarNos}，additive 只读方法，
     * <b>不</b>改 breed 共享分页选猪 mapper，避免跨域污染）。</p>
     *
     * <p><b>「今日发货到门店白条」口径待补（S-C c1 链路评估结论）</b>：方案理想口径是
     * 「shipment⋈bar（product 粒度，shipDate=今日 + storeId 不空）」，但当前 schema 下 shipment 到 bar
     * 的唯一桥接是 {@code t_warehouse_product_production.whiteBarId/earNo}（冗余 FK），且该链路仅在
     * <b>分割打包</b>路径回填——白条<b>整只</b>发货（{@code submitWhiteBarOut → updateStatusToShipOut}）
     * 不保证产生带 earNo 的 product_production 行，现网 earNo/whiteBarId 多为 NULL。强行改成
     * {@code bar ⋈ product_production ⋈ shipment} 的 INNER JOIN 会因 FK 大面积 NULL 而 picker 取空 /
     * 漏白条，比现状「当天 in_stock 白条」更差。故 c1 轻量方案<b>保留当天入库白条口径</b>，
     * shipment⋈bar 干净链路待 product_production.earNo/whiteBarId 回填完善后再切（见 blockers「链路待补」）。</p>
     *
     * <p>外购白条无耳号或耳号无猪档案时，{@code pigSex/pigBreedLabel/ageDays} 留 null；
     * chip 主显值 {@code earNo} 为空时回退用 {@code barId}（保证选择器有可点项）。
     * 当天无白条入库 → 空结果，属正常态（前端显示「暂无当天可追溯白条」），非 bug。</p>
     */
    @Override
    public TableDataInfo<TraceablePigVo> listTraceablePigs(PageQuery pageQuery) {
        PageQuery pq = pageQuery != null ? pageQuery : new PageQuery(1, 10);
        // 口径 = 门店「当天确认收货」的白条（邓博：白条领到门店后现场分割）。链路：
        //   demand.received_time=今天 → 其已发货(is_delivery_check=1)的 white_bar 业态 production → ear_no。
        // 不再用「仓库当天在库白条(in_stock)」——那是仓库视角，与门店确认收货无关（旧 bug：确认收货后白条不显示）。
        LocalDate today = LocalDate.now(TODAY_ZONE);
        // row41：按当前所选门店过滤（StoreContext 头，与现场生码 currentStoreId 同源）——门店猪肉打包 picker 应
        // 只看本店当天确认收货的白条；storeId 为空（超管未选店）→ 不加店过滤，兜底看全部（不阻断）。
        Long storeId = currentStoreId();
        LambdaQueryWrapper<DemandManage> demandWrapper = new LambdaQueryWrapper<DemandManage>()
            .ge(DemandManage::getReceivedTime, today.atStartOfDay())
            .lt(DemandManage::getReceivedTime, today.plusDays(1).atStartOfDay())
            .select(DemandManage::getId);
        if (storeId != null) {
            demandWrapper.eq(DemandManage::getStoreId, storeId);
        }
        List<Long> receivedDemandIds = demandManageMapper.selectList(demandWrapper)
            .stream().map(DemandManage::getId).filter(Objects::nonNull).distinct().toList();
        if (receivedDemandIds.isEmpty()) {
            return emptyPigPage();
        }
        List<Long> whiteBarProductIds = productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>()
                    .eq(ProductInfo::getBelongType, BELONG_TYPE_WHITE_BAR)
                    .select(ProductInfo::getId))
            .stream().map(ProductInfo::getId).filter(Objects::nonNull).toList();
        if (whiteBarProductIds.isEmpty()) {
            return emptyPigPage();
        }
        // 已发货到门店的白条 production（取耳号/白条号 + 到货重量；按 white_bar_no（半只）或耳号去重保序累加）。
        // row41：不再强求 ear_no 非空——整只白条发货 production 常无耳号、只有 white_bar_no（如 BAR2607080012），
        // 若过滤 ear_no IS NOT NULL 会把这些门店已收货白条全漏掉（门店猪肉打包显空）。
        List<ProductProduction> barProds = productProductionMapper.selectList(
            new LambdaQueryWrapper<ProductProduction>()
                .in(ProductProduction::getDemandId, receivedDemandIds)
                .in(ProductProduction::getProductId, whiteBarProductIds)
                .eq(ProductProduction::getIsDeliveryCheck, DELIVERY_CHECKED)
                .orderByDesc(ProductProduction::getId)
                .select(ProductProduction::getEarNo, ProductProduction::getWhiteBarNo, ProductProduction::getProduceQuantity));
        // 门店到货白条按【半只】一条（邓博 row13：white_bar_no 区分同一耳号的两个半只，门店按半只识别 / 现场分割）。
        // key = white_bar_no；为空（旧发货数据）回落 "EAR:<耳号>" 按整猪聚合，兼容历史。
        List<String> keyOrder = new ArrayList<>();
        Map<String, BigDecimal> arrivedByKey = new LinkedHashMap<>();
        Map<String, String> keyToEar = new LinkedHashMap<>();
        Map<String, String> keyToWhiteBarNo = new LinkedHashMap<>();
        Map<String, Integer> barCountByEar = new LinkedHashMap<>();
        for (ProductProduction p : barProds) {
            String ear = p.getEarNo();
            String wbNo = p.getWhiteBarNo();
            // row41：门店白条以 white_bar_no（半只）为主键，ear_no 可空（整只白条发货 production 常无耳号）；
            // 仅当耳号与白条号都为空才无从识别、跳过。key 优先 white_bar_no，回落 EAR:<耳号>。
            if (StringUtils.isBlank(ear) && StringUtils.isBlank(wbNo)) {
                continue;
            }
            String key = StringUtils.isNotBlank(wbNo) ? wbNo : ("EAR:" + ear);
            if (!arrivedByKey.containsKey(key)) {
                keyOrder.add(key);
                keyToEar.put(key, ear);
                keyToWhiteBarNo.put(key, StringUtils.isNotBlank(wbNo) ? wbNo : null);
                if (StringUtils.isNotBlank(ear)) {
                    barCountByEar.merge(ear, 1, Integer::sum);
                }
            }
            arrivedByKey.merge(key, p.getProduceQuantity() == null ? BigDecimal.ZERO : p.getProduceQuantity(), BigDecimal::add);
        }
        if (keyOrder.isEmpty()) {
            return emptyPigPage();
        }

        // 已现场打包重量按耳号合计（门店 pork 追溯码 remark「重量=Ykg」，追溯码按耳号、暂无半只维度）。
        // row41：过滤空耳号（整只白条无耳号行靠 white_bar_no 识别，不参与按耳号的猪只信息 enrich / 已用量合计）。
        List<String> earNoList = keyToEar.values().stream().filter(StringUtils::isNotBlank).distinct().toList();
        Map<String, BigDecimal> usedByEar = sumOnsiteUsedWeightByEarNo(earNoList);

        // 按耳号批量 enrich 猪只信息（earNo → PigAvailableVo），additive 跨域只读
        Set<String> earNos = new LinkedHashSet<>(earNoList);
        Map<String, PigAvailableVo> pigByEarNo = pigQueryService.listPigInfoByEarNos(earNos).stream()
            .filter(p -> StringUtils.isNotBlank(p.getEarNo()))
            .collect(Collectors.toMap(PigAvailableVo::getEarNo, Function.identity(), (a, b) -> a));

        List<TraceablePigVo> all = keyOrder.stream().map(key -> {
            String earNo = keyToEar.get(key);
            TraceablePigVo v = new TraceablePigVo();
            v.setEarNo(earNo);
            v.setWhiteBarNo(keyToWhiteBarNo.get(key));
            PigAvailableVo pig = pigByEarNo.get(earNo);
            if (pig != null) {
                v.setPigSex(pig.getPigSex());
                v.setPigBreedLabel(pig.getPigBreedLabel());
                v.setAgeDays(pig.getAgeDays());
            }
            BigDecimal arrived = arrivedByKey.getOrDefault(key, BigDecimal.ZERO);
            // 已打包扣减：该耳号仅一条白条（整只）时按耳号 used 精确扣；一耳多半只时追溯码无半只维度，
            // 不把整猪 used 逐半只重复扣（防双扣），剩余 = 到货（半只级已打包扣减待门店生码带 white_bar_no）。
            BigDecimal used = barCountByEar.getOrDefault(earNo, 1) <= 1
                ? usedByEar.getOrDefault(earNo, BigDecimal.ZERO) : BigDecimal.ZERO;
            v.setArrivedWeight(arrived);
            v.setRemainingWeight(arrived.subtract(used));
            return v;
        }).toList();

        // 内存分页（门店当天收货白条量级小）
        int pageNum = pq.getPageNum() == null ? 1 : pq.getPageNum();
        int pageSize = pq.getPageSize() == null ? 10 : pq.getPageSize();
        int from = Math.min(Math.max(pageNum - 1, 0) * pageSize, all.size());
        int to = (int) Math.min((long) from + pageSize, all.size());
        TableDataInfo<TraceablePigVo> out = TableDataInfo.build();
        out.setRows(new ArrayList<>(all.subList(from, to)));
        out.setTotal((long) all.size());
        return out;
    }

    /** 空 picker 分页（无当天确认收货白条 → 前端显「暂无当天确认收货白条」）。 */
    private TableDataInfo<TraceablePigVo> emptyPigPage() {
        TableDataInfo<TraceablePigVo> empty = TableDataInfo.build();
        empty.setRows(List.of());
        empty.setTotal(0L);
        return empty;
    }

    /**
     * 按耳号合计「已现场打包重量」：门店 pork 码（{@code code_type=pork} + remark「现场生码」前缀）的
     * remark「重量=Ykg」之和。用于 picker 算每根白条的剩余可打包重量（到货 − 已打包）。
     */
    private Map<String, BigDecimal> sumOnsiteUsedWeightByEarNo(List<String> earNos) {
        Map<String, BigDecimal> used = new LinkedHashMap<>();
        if (earNos == null || earNos.isEmpty()) {
            return used;
        }
        List<TraceCode> codes = traceCodeMapper.selectList(
            new LambdaQueryWrapper<TraceCode>()
                .eq(TraceCode::getCodeType, CODE_TYPE_PORK)
                .in(TraceCode::getPigEarNo, earNos)
                .likeRight(TraceCode::getRemark, ONSITE_REMARK_PREFIX)
                .select(TraceCode::getPigEarNo, TraceCode::getRemark));
        for (TraceCode c : codes) {
            BigDecimal w = parseWeightFromRemark(c.getRemark());
            if (w != null && StringUtils.isNotBlank(c.getPigEarNo())) {
                used.merge(c.getPigEarNo(), w, BigDecimal::add);
            }
        }
        return used;
    }

    /** 从现场生码 remark「…重量=Ykg」解析重量（剥非数字字符）；无则 null。 */
    private BigDecimal parseWeightFromRemark(String remark) {
        if (remark == null) {
            return null;
        }
        int wi = remark.indexOf("重量=");
        if (wi < 0) {
            return null;
        }
        String num = remark.substring(wi + 3).replaceAll("[^0-9.]", "");
        if (num.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(num);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 门店现场按需生码（S-C c1 方案：部位字典驱动，<b>不</b>扣白条库存）。
     *
     * <p>部位卡口径：{@code bo.cutLabel} 来自字典 {@code djs_pork_cut_product}（5 部位），生码委托仓库域
     * {@link ITraceService#genPorkOnsiteCode}，与门店打包间（{@code djs_product_workshop=5}）口径一致——
     * 门店打包间属猪肉处理点，现场对当天入库白条按部位生追溯码，<b>纯生码不联动库存出入</b>。</p>
     *
     * <p><b>c2 不在本轮（保留现状）</b>：客户若要「打包扣白条库存 / 部位升级为 product 驱动」（即把 5 部位
     * seed 成 product、PorkTracePanel 改产品驱动 + 生码同步扣 {@code t_warehouse_bar_info} 库存）才上 c2，
     * 本轮仅保留 djs_pork_cut_product 字典驱动 + {@code genPorkOnsiteCode} 现状，不改 product 驱动。</p>
     */
    /**
     * 门店猪肉打包可选产品：产品属性=生产产品（{@code product_attr=1}）且业态=猪肉（{@code belong_type=pork}）。
     *
     * <p>无匹配产品 → 空 List（前端 PorkTracePanel 回退用部位字典 {@code djs_pork_cut_product}）。</p>
     */
    @Override
    public List<StorePackProductVo> listPackProducts() {
        return productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getProductAttr, PRODUCT_ATTR_PRODUCE)
                .eq(ProductInfo::getBelongType, BELONG_TYPE_PORK))
            .stream().map(p -> {
                StorePackProductVo vo = new StorePackProductVo();
                vo.setProductId(p.getId());
                vo.setProductCode(p.getProductId());
                vo.setProductName(p.getProductName());
                vo.setProductSpec(p.getProductSpec());
                vo.setProductThumb(p.getProductThumb());
                vo.setImageOssId(p.getImageOssId());
                return vo;
            }).toList();
    }

    @Override
    public StoreOnsiteCodeVo genOnsiteCode(StoreTraceOnsiteBo bo) {
        // row201：现场码归属当前门店（StoreContext 由 Current-Store-Id 头注入；未选门店/超管无上下文 → null 容错）
        Long storeId = currentStoreId();
        // 已终止合作门店禁止现场生码（口径同门店域其余业务写路径；storeId=null 放行，由下方生产编码生成兜底报错）
        storeService.assertStoreActive(storeId);
        // row84：生产编码 = <门店生产标识码>YYMMDD####（门店级每日流水），标签「生产编码」展示用。
        //   必须先生成——门店未配生产标识码时抛异常，避免生了追溯码却无生产编码的半成品。
        String productionCode = storeService.generateStoreProduceCode(storeId);
        // 追溯码（T{yyyyMMdd}PG{seq6}）仍按原规则生成，二维码 encode 用（C 端 /trace/pork/{code} 查得到）。
        // row84 follow-up：把上面生成的 productionCode 传下去落 trace_code.production_code 列（补打列表「生产编号」读此），
        //   不在 warehouse 侧重算流水，避免生成两个生产编码。
        String produceCode = traceService.genPorkOnsiteCode(bo.getEarNo(), bo.getCutLabel(), bo.getWeight(), storeId, productionCode);
        log.info("[STORE-TRACE-ONSITE-001] store onsite gen produceCode={} productionCode={} earNo={} cut={} storeId={}",
            produceCode, productionCode, bo.getEarNo(), bo.getCutLabel(), storeId);
        StoreOnsiteCodeVo vo = new StoreOnsiteCodeVo();
        vo.setProduceCode(produceCode);
        vo.setProductionCode(productionCode);
        return vo;
    }

    /** 当前所选门店 id（StoreContext 头值；空 / 非数字 → null，不阻断生码）。 */
    private Long currentStoreId() {
        String storeId = StoreContext.getStoreId();
        if (StringUtils.isBlank(storeId)) {
            return null;
        }
        try {
            return Long.valueOf(storeId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public TableDataInfo<TraceCodeListVo> listPorkTrace(TraceCodeQuery query, PageQuery pageQuery) {
        TraceCodeQuery q = query == null ? new TraceCodeQuery() : query;
        // 门店端恒 pork，忽略前端可能传入的其它 codeType
        q.setCodeType(CODE_TYPE_PORK);
        TableDataInfo<TraceCodeListVo> page = traceCodeAdminService.queryPage(q, pageQuery);
        fillStoreArrivalDate(page.getRows());
        return page;
    }

    /**
     * 门店现场生码行（{@code source=store}）「到店日期」回填 = 该白条耳号对应需求的门店确认收货时间。
     *
     * <p>门店猪肉打包用的白条是「门店确认收货」的，故现场生码的到店日期 = 白条所在需求 {@code received_time}。
     * 链路：{@code trace_code.pig_ear_no → white_bar 业态 production(is_delivery_check=1) → demand_id → received_time}。
     * 仓库发货流的「到店事件」对店内现做码取不到，故按耳号反查收货时间填（best-effort，查不到留空）。</p>
     */
    private void fillStoreArrivalDate(List<TraceCodeListVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // 1. 缺到店日期、有耳号的门店行
        List<String> earNos = rows.stream()
            .filter(r -> SOURCE_STORE.equals(r.getSource()) && r.getArrivalDate() == null && StringUtils.isNotBlank(r.getPigEarNo()))
            .map(TraceCodeListVo::getPigEarNo).distinct().toList();
        if (earNos.isEmpty()) {
            return;
        }
        // 2. white_bar 产品 id
        List<Long> whiteBarProductIds = productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>()
                    .eq(ProductInfo::getBelongType, BELONG_TYPE_WHITE_BAR).select(ProductInfo::getId))
            .stream().map(ProductInfo::getId).filter(Objects::nonNull).toList();
        if (whiteBarProductIds.isEmpty()) {
            return;
        }
        // 3. 耳号 → demand_id（已发货 white_bar production，最新 id 优先）
        Map<String, Long> earToDemand = new java.util.LinkedHashMap<>();
        productProductionMapper.selectList(
                new LambdaQueryWrapper<ProductProduction>()
                    .in(ProductProduction::getEarNo, earNos)
                    .in(ProductProduction::getProductId, whiteBarProductIds)
                    .eq(ProductProduction::getIsDeliveryCheck, DELIVERY_CHECKED)
                    .isNotNull(ProductProduction::getDemandId)
                    .orderByDesc(ProductProduction::getId)
                    .select(ProductProduction::getEarNo, ProductProduction::getDemandId))
            .forEach(p -> {
                if (StringUtils.isNotBlank(p.getEarNo())) {
                    earToDemand.putIfAbsent(p.getEarNo(), p.getDemandId());
                }
            });
        if (earToDemand.isEmpty()) {
            return;
        }
        // 4. demand → received_time
        List<Long> demandIds = earToDemand.values().stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, java.time.LocalDateTime> demandRecv = demandManageMapper.selectList(
                new LambdaQueryWrapper<DemandManage>()
                    .in(DemandManage::getId, demandIds).select(DemandManage::getId, DemandManage::getReceivedTime))
            .stream().filter(d -> d.getReceivedTime() != null)
            .collect(Collectors.toMap(DemandManage::getId, DemandManage::getReceivedTime, (a, b) -> a));
        // 5. 回填到店日期 = received_time 当天
        for (TraceCodeListVo r : rows) {
            if (!SOURCE_STORE.equals(r.getSource()) || r.getArrivalDate() != null || StringUtils.isBlank(r.getPigEarNo())) {
                continue;
            }
            Long demandId = earToDemand.get(r.getPigEarNo());
            java.time.LocalDateTime recv = demandId == null ? null : demandRecv.get(demandId);
            if (recv != null) {
                r.setArrivalDate(recv.toLocalDate());
            }
        }
    }

    @Override
    public TraceCodeDetailVo getPorkTraceDetail(Long id) {
        return traceCodeAdminService.getDetail(id);
    }

    @Override
    public List<TraceCodeDetailVo> batchPorkTraceDetail(List<Long> ids) {
        return traceCodeAdminService.batchDetail(ids);
    }
}
