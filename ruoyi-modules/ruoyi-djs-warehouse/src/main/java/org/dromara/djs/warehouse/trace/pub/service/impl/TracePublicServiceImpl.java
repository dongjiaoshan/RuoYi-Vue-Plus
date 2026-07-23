package org.dromara.djs.warehouse.trace.pub.service.impl;

import cn.hutool.core.lang.Dict;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.service.OssService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.vo.PedigreeVo;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.ISowDetailService;
import org.dromara.djs.breed.event.growth.domain.PigGrowth;
import org.dromara.djs.breed.event.growth.mapper.PigGrowthMapper;
import org.dromara.djs.breed.event.slaughter.domain.PigMarketing;
import org.dromara.djs.breed.event.slaughter.mapper.PigMarketingMapper;
import org.dromara.djs.breed.med.domain.Medicine;
import org.dromara.djs.breed.med.mapper.MedicineMapper;
import org.dromara.djs.breed.med.record.domain.MedRecord;
import org.dromara.djs.breed.med.record.mapper.MedRecordMapper;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.warehouse.cut.domain.PigCutRecord;
import org.dromara.djs.warehouse.cut.mapper.PigCutRecordMapper;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.farm.domain.FarmRecords;
import org.dromara.djs.plant.farm.mapper.FarmRecordsMapper;
import org.dromara.djs.plant.organic.domain.CropOrganic;
import org.dromara.djs.plant.organic.domain.PlotOrganic;
import org.dromara.djs.plant.organic.mapper.CropOrganicMapper;
import org.dromara.djs.plant.organic.mapper.PlotOrganicMapper;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.plant.zone.domain.PlotZone;
import org.dromara.djs.plant.zone.mapper.PlotZoneMapper;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.mapper.VegDisplayNameMapper;
import org.dromara.djs.warehouse.trace.domain.TraceCode;
import org.dromara.djs.warehouse.trace.domain.TraceCodeTypeConst;
import org.dromara.djs.warehouse.trace.domain.TraceContentConst;
import org.dromara.djs.warehouse.trace.domain.TraceEvent;
import org.dromara.djs.warehouse.trace.mapper.TraceCodeMapper;
import org.dromara.djs.warehouse.trace.mapper.TraceEventMapper;
import org.dromara.djs.warehouse.trace.mapper.TraceFarmNameMapper;
import org.dromara.djs.warehouse.trace.pub.domain.vo.PublicTraceVo;
import org.dromara.djs.warehouse.trace.pub.mapper.TraceUserNameMapper;
import org.dromara.djs.warehouse.trace.pub.service.ITracePublicService;
import org.dromara.djs.warehouse.veg.domain.PlantingRecord;
import org.dromara.djs.warehouse.veg.mapper.PlantingRecordMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 公开追溯聚合 Service 实现（TRC-PUB-API-001）。
 *
 * <h3>聚合路径（路径 b：自建主表 + 事件 + 跨域 mapper）</h3>
 * <p>不依赖 admin service（admin {@code getDetail} 按雪花 id，公开端只有 produce_code，
 * 且 admin 详情聚合的 product/store/plot/farm fill 逻辑独立于本端裁剪 VO）。本实现走
 * {@code extends DjsBaseServiceImpl}（{@code baseMapper} = TraceCodeMapper），按 produce_code
 * 查主表 + 事件链 + 按 codeType 跨域分支填充。<b>只读，不调 softDelete</b>。</p>
 *
 * <h3>跨模块只读取数</h3>
 * <p>warehouse pom 已依赖 breed + plant，直接 import 跨域 mapper 内存 JOIN（无跨模块原生 JOIN SQL，
 * 对齐 {@code TraceCodeAdminServiceImpl} 内存 fill 范式）。V1 单条追溯码数据量小，内存聚合足够。</p>
 *
 * <h3>@SaIgnore 无登录态兼容</h3>
 * <ul>
 *   <li>所有查询用 {@link TenantHelper#ignore} 包裹（无登录 → 无 tenant 上下文，
 *       否则 MP 租户拦截器查空；V1 全 '1001'）。</li>
 *   <li>不调 {@code LoginHelper}（无登录上下文）。operatorId → 姓名直查 sys_user.nick_name
 *       （不走翻译注解，避免 @SaIgnore 端翻译上下文缺失）。</li>
 *   <li>图片 ossId → URL 走 {@link OssService#selectUrlByIds}（C 端无登录无法自行反查 oss）。</li>
 * </ul>
 *
 * @author djs
 * @since TRC-PUB-API-001
 */
@Slf4j
@Service
public class TracePublicServiceImpl
    extends org.dromara.djs.common.base.DjsBaseServiceImpl<TraceCodeMapper, TraceCode>
    implements ITracePublicService {

    /** 默认租户（V1 单租户；@SaIgnore 无登录上下文兜底）。 */
    private static final String DEFAULT_TENANT = "1001";

    /** Redis 缓存 key 前缀。 */
    private static final String CACHE_PREFIX = "trace:public:";

    /** 缓存 TTL（~10min）。 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    /**
     * 猪肉时间轴不展示的事件（DENGBO row61）：白条入库 / 屠宰 / 排酸——不在客户要求的 7 节点集，聚合时剔除。
     */
    private static final Set<String> PORK_TIMELINE_EXCLUDED = Set.of(
        TraceContentConst.WHITE_BAR_IN, TraceContentConst.SLAUGHTER, TraceContentConst.ACID);

    /**
     * 果蔬农事记录不展示的类型（DENGBO row63）：采摘 / 采摘活动——追溯页只显真实农事工序，采摘另有节点。
     */
    private static final Set<String> VEG_PLOT_RECORD_EXCLUDED = Set.of("harvest", "harvest_activity");

    /** 白条领用表出库类型：分割车间（{@code djs_pig_cut_out_type}），白条分割节点时刻只取 cut。 */
    private static final String PIG_CUT_OUT_TYPE_CUT = "cut";

    private final TraceEventMapper traceEventMapper;
    private final ProductInfoMapper productInfoMapper;
    private final StoreMapper storeMapper;
    private final TraceFarmNameMapper traceFarmNameMapper;
    private final TraceUserNameMapper traceUserNameMapper;
    private final OssService ossService;
    // breed
    private final PigMapper pigMapper;
    private final PigGrowthMapper pigGrowthMapper;
    private final PigMarketingMapper pigMarketingMapper;
    private final MedRecordMapper medRecordMapper;
    private final MedicineMapper medicineMapper;
    /** 谱系复用 breed 既有反查逻辑（earNo→Pig→queryPedigree），不重写。 */
    private final ISowDetailService sowDetailService;
    // plant
    private final PlotInfoMapper plotInfoMapper;
    private final PlotZoneMapper plotZoneMapper;
    private final FarmRecordsMapper farmRecordsMapper;
    private final CropOrganicMapper cropOrganicMapper;
    private final PlotOrganicMapper plotOrganicMapper;
    /** 地块作物史：按 plot_id 查种植明细 + 作物名/图。 */
    private final PlantDetailsMapper plantDetailsMapper;
    private final CropInfoMapper cropInfoMapper;
    // veg 工序时间轴源（同 warehouse 模块内）
    /** 仓库视角种植记录：种植 / 采摘节点时间源。 */
    private final PlantingRecordMapper plantingRecordMapper;
    /** 发货产品生产记录：产品生产（打包）节点时间 + 果蔬成品实际称重（按 trace_code 关联）。 */
    private final ProductProductionMapper productProductionMapper;
    private final VegDisplayNameMapper vegDisplayNameMapper;
    /** 白条领用 / 分割记录：猪肉追溯「白条分割」节点时间源（row61，cut_start_time）。 */
    private final PigCutRecordMapper pigCutRecordMapper;

    public TracePublicServiceImpl(TraceCodeMapper baseMapper,
                                  TraceEventMapper traceEventMapper,
                                  ProductInfoMapper productInfoMapper,
                                  StoreMapper storeMapper,
                                  TraceFarmNameMapper traceFarmNameMapper,
                                  TraceUserNameMapper traceUserNameMapper,
                                  OssService ossService,
                                  PigMapper pigMapper,
                                  PigGrowthMapper pigGrowthMapper,
                                  PigMarketingMapper pigMarketingMapper,
                                  MedRecordMapper medRecordMapper,
                                  MedicineMapper medicineMapper,
                                  ISowDetailService sowDetailService,
                                  PlotInfoMapper plotInfoMapper,
                                  PlotZoneMapper plotZoneMapper,
                                  FarmRecordsMapper farmRecordsMapper,
                                  CropOrganicMapper cropOrganicMapper,
                                  PlotOrganicMapper plotOrganicMapper,
                                  PlantDetailsMapper plantDetailsMapper,
                                  CropInfoMapper cropInfoMapper,
                                  PlantingRecordMapper plantingRecordMapper,
                                  ProductProductionMapper productProductionMapper,
                                  VegDisplayNameMapper vegDisplayNameMapper,
                                  PigCutRecordMapper pigCutRecordMapper) {
        super(baseMapper);
        this.traceEventMapper = traceEventMapper;
        this.productInfoMapper = productInfoMapper;
        this.storeMapper = storeMapper;
        this.traceFarmNameMapper = traceFarmNameMapper;
        this.traceUserNameMapper = traceUserNameMapper;
        this.ossService = ossService;
        this.pigMapper = pigMapper;
        this.pigGrowthMapper = pigGrowthMapper;
        this.pigMarketingMapper = pigMarketingMapper;
        this.medRecordMapper = medRecordMapper;
        this.medicineMapper = medicineMapper;
        this.sowDetailService = sowDetailService;
        this.plotInfoMapper = plotInfoMapper;
        this.plotZoneMapper = plotZoneMapper;
        this.farmRecordsMapper = farmRecordsMapper;
        this.cropOrganicMapper = cropOrganicMapper;
        this.plotOrganicMapper = plotOrganicMapper;
        this.plantDetailsMapper = plantDetailsMapper;
        this.cropInfoMapper = cropInfoMapper;
        this.plantingRecordMapper = plantingRecordMapper;
        this.productProductionMapper = productProductionMapper;
        this.vegDisplayNameMapper = vegDisplayNameMapper;
        this.pigCutRecordMapper = pigCutRecordMapper;
    }

    @Override
    public PublicTraceVo getByProduceCode(String produceCode) {
        if (StringUtils.isBlank(produceCode)) {
            return null;
        }
        String key = CACHE_PREFIX + produceCode;
        PublicTraceVo cached = readCache(key);
        if (cached != null) {
            return cached;
        }
        // @SaIgnore 无登录 → 无 tenant 上下文，全程 ignore 租户拦截（V1 单租户）
        PublicTraceVo vo = TenantHelper.ignore(() -> aggregate(produceCode));
        if (vo != null) {
            writeCache(key, vo);
        }
        return vo;
    }

    /**
     * 读 Redis 缓存（protected：{@code RedisUtils} 静态字段在单测无 Spring 环境会 clinit 失败，
     * 抽出便于单测覆盖；生产走 {@link RedisUtils#getCacheObject}）。
     */
    protected PublicTraceVo readCache(String key) {
        return RedisUtils.getCacheObject(key);
    }

    /**
     * 写 Redis 缓存 ~10min（protected，同 {@link #readCache} 缘由）。
     */
    protected void writeCache(String key, PublicTraceVo vo) {
        RedisUtils.setCacheObject(key, vo, CACHE_TTL);
    }

    // ============================ 聚合主流程 ============================

    private PublicTraceVo aggregate(String produceCode) {
        TraceCode code = baseMapper.selectOne(
            new LambdaQueryWrapper<TraceCode>().eq(TraceCode::getProduceCode, produceCode));
        if (code == null) {
            return null;
        }
        PublicTraceVo vo = new PublicTraceVo();
        vo.setCodeType(code.getCodeType());
        vo.setProduct(buildProduct(code));

        List<PublicTraceVo.TimelineNode> timeline = buildTimeline(produceCode);
        vo.setTimeline(timeline);

        String type = code.getCodeType();
        if (TraceCodeTypeConst.PORK.equals(type)) {
            fillPork(vo, code, timeline);
        } else if (TraceCodeTypeConst.VEG.equals(type)) {
            fillVeg(vo, code, timeline);
        } else if (TraceCodeTypeConst.GIFT.equals(type)) {
            fillGift(vo, code);
        }
        return vo;
    }

    // ============================ 通用块 ============================

    private PublicTraceVo.ProductBlock buildProduct(TraceCode code) {
        PublicTraceVo.ProductBlock block = new PublicTraceVo.ProductBlock();
        block.setProduceCode(code.getProduceCode());
        // 打包日期 V1 无独立列，用追溯码生成时间兜底（推断字段，见 _open-issues）
        block.setPackDate(code.getCreateTime());
        if (code.getProductId() != null) {
            ProductInfo p = productInfoMapper.selectById(code.getProductId());
            if (p != null) {
                // 展示名用生码时定格的 trace_display_name（DENGBO-R16：果蔬按有机证书取名/别名，与 admin 追溯列表一致）；
                // 旧码无定格值时回落产品当前名，绝不显空。
                block.setName(StringUtils.isNotBlank(code.getTraceDisplayName())
                    ? code.getTraceDisplayName() : p.getProductName());
                block.setSpec(p.getProductSpec());
                // 净重默认用规格兜底；veg 分支会用打包实际称重覆盖（见 fillVeg）
                block.setWeight(p.getProductSpec());
                block.setDescription(p.getProductDesc());
                // 图优先产品配置缩略图 product_thumb，缺失才用原图 product_img（参「产品双图片字段坑」）
                String imgOssId = StringUtils.isNotBlank(p.getProductThumb())
                    ? p.getProductThumb() : p.getProductImg();
                block.setImageUrl(resolveOssUrl(imgOssId));
            }
        }
        return block;
    }

    private List<PublicTraceVo.TimelineNode> buildTimeline(String produceCode) {
        List<TraceEvent> events = traceEventMapper.selectList(
            new LambdaQueryWrapper<TraceEvent>()
                .eq(TraceEvent::getProduceCode, produceCode)
                .orderByDesc(TraceEvent::getTraceTime)
                .orderByDesc(TraceEvent::getId));
        if (events.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, String> nameMap = resolveOperatorNames(
            events.stream().map(TraceEvent::getOperatorId).filter(Objects::nonNull).distinct().toList());
        List<PublicTraceVo.TimelineNode> nodes = new ArrayList<>(events.size());
        for (TraceEvent e : events) {
            PublicTraceVo.TimelineNode node = new PublicTraceVo.TimelineNode();
            node.setTraceContent(e.getTraceContent());
            node.setTraceTime(e.getTraceTime());
            node.setOperatorName(e.getOperatorId() == null ? null : nameMap.get(e.getOperatorId()));
            node.setWeight(parseEventWeight(e.getEventData()));
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * 解析 {@code trace_event.event_data} JSON 里的 {@code weight}（追溯时间轴每节点重量）。
     * event_data 为空 / 非合法 JSON / 无 weight → null（容错不抛，缺重量时节点照常展示）。
     */
    private String parseEventWeight(String eventData) {
        if (StringUtils.isBlank(eventData)) {
            return null;
        }
        try {
            Dict map = JsonUtils.parseMap(eventData);
            if (map == null) {
                return null;
            }
            String weight = map.getStr("weight");
            return StringUtils.isBlank(weight) ? null : weight;
        } catch (Exception ex) {
            log.warn("[trace-public] event_data 解析重量失败 eventData={}: {}", eventData, ex.getMessage());
            return null;
        }
    }

    // ============================ pork 分支 ============================

    private void fillPork(PublicTraceVo vo, TraceCode code, List<PublicTraceVo.TimelineNode> timeline) {
        // row48：重量改取肉品打包实际称重（product_production.product_weight，kg）按克展示、去掉规格「/份」
        //（如规格 700g/份、实际打包 0.500kg → 展示「重量：500g」）；无打包记录则保留 buildProduct 的规格兜底。
        ProductProduction pack = findPackProduction(code.getProduceCode());
        if (pack != null && pack.getProductWeight() != null && vo.getProduct() != null) {
            vo.getProduct().setWeight(toGramStr(pack.getProductWeight()));
        }

        String earNo = code.getPigEarNo();
        // 农场名（trace_code.farm_id 走 sys_farm，复用 admin farm-name mapper）
        String farmName = resolveFarmName(code.getFarmId());

        PublicTraceVo.PigBlock pigBlock = new PublicTraceVo.PigBlock();
        pigBlock.setEarNo(earNo);
        pigBlock.setFarmName(farmName);
        // 出栏日期：Pig 无列，用 timeline 的 marketing 节点时间兜底（推断字段）
        pigBlock.setMarketDate(findEventTime(timeline, TraceContentConst.MARKETING));

        LocalDate birthDate = null;
        if (StringUtils.isNotBlank(earNo)) {
            Pig pig = pigMapper.selectOne(
                new LambdaQueryWrapper<Pig>().eq(Pig::getEarNo, earNo).last("limit 1"));
            if (pig != null) {
                birthDate = pig.getBirthDate();
                pigBlock.setSex(pig.getPigSex());
                pigBlock.setBreed(pig.getPigBreedCode());
                pigBlock.setBirthDate(birthDate);
                // 出生重：Pig.birth_weight；出栏重：t_farm_pig_marketing.out_weight（按 pig_id 取最近一条）
                pigBlock.setBirthWeight(toStr(pig.getBirthWeight()));
                PigMarketing marketing = pigMarketingMapper.selectOne(
                    new LambdaQueryWrapper<PigMarketing>()
                        .eq(PigMarketing::getPigId, pig.getId())
                        .orderByDesc(PigMarketing::getMarketingDate)
                        .orderByDesc(PigMarketing::getId)
                        .last("limit 1"));
                if (marketing != null) {
                    pigBlock.setMarketWeight(toStr(marketing.getOutWeight()));
                }
                // 生长记录倒序（最新在前）+ 栋舍名（barnName 用生长记录冗余兜底）
                List<PigGrowth> growths = pigGrowthMapper.selectList(
                    new LambdaQueryWrapper<PigGrowth>()
                        .eq(PigGrowth::getPigId, pig.getId())
                        .orderByDesc(PigGrowth::getMeasureDate)
                        .orderByDesc(PigGrowth::getId));
                vo.setGrowthRecords(toGrowthRows(growths, birthDate));
                growths.stream()
                    .map(PigGrowth::getBarnName).filter(StringUtils::isNotBlank)
                    .findFirst().ifPresent(pigBlock::setBarnName);
                // 体重 / 照片：取最新一条生长记录（倒序后首条）的 weight / 首图（Pig 实体无体重列）
                if (!growths.isEmpty()) {
                    PigGrowth latest = growths.get(0);
                    pigBlock.setWeight(toStr(latest.getWeight()));
                    pigBlock.setPhotoUrl(resolveOssUrl(latest.getPhotoOssIds()));
                }
                // 谱系：复用 breed queryPedigree（本猪 father_ear/mother_ear 反查父母 Pig）
                vo.setPedigree(buildPedigree(pig.getId()));
            } else {
                vo.setGrowthRecords(new ArrayList<>());
            }
            // 用药 / 疫苗保健（按耳号，MedRecord drug_type=1/3 是按猪只）
            vo.setMedications(toMedicationRows(earNo, birthDate));
        } else {
            vo.setGrowthRecords(new ArrayList<>());
            vo.setMedications(new ArrayList<>());
        }
        // 日龄：出栏日（marketing 事件）− 出生日；任一缺 → null
        LocalDateTime marketDt = pigBlock.getMarketDate();
        pigBlock.setAgeDays(calcAgeDays(birthDate, marketDt == null ? null : marketDt.toLocalDate()));
        vo.setPig(pigBlock);

        // 检疫信息：V1 数据源缺口（无检疫表/字段），返 null（见 _open-issues）
        vo.setQuarantine(null);

        // row61：时间轴重构成 7 节点（出栏/屠宰完成/白条领用/白条分割/产品生产/冷链发货/到店）
        remodelPorkTimeline(timeline, earNo);

        // 门店（pork/veg 共用 fillStore）
        fillStore(vo, code);
    }

    /**
     * 猪肉追溯时间轴重构（DENGBO row61）：只保留客户要求的 7 节点，补「白条分割」合成节点。
     *
     * <p><b>保留</b>：{@code marketing}(出栏) / {@code singe}(屠宰完成) / {@code white_bar_pick}(白条领用) /
     * {@code white_bar_cut}(白条分割·合成) / {@code in_stock}(产品生产·打包完成) / {@code ship}(冷链发货) /
     * {@code arrival}(到店)。<b>去掉</b>：{@code white_bar_in}(白条入库) / {@code slaughter}(屠宰) /
     * {@code acid}(排酸)——不在客户要求节点集。</p>
     *
     * <p>「白条分割」时刻取 {@code t_warehouse_pig_cut_record.cut_start_time}（out_type='cut' 首次分割开始）；
     * 无（外购白条 / 未分割）→ 不补该节点。按 {@code trace_time} 倒序（最新在上，对齐原型「由下至上：出栏→到店」）。</p>
     */
    private void remodelPorkTimeline(List<PublicTraceVo.TimelineNode> timeline, String earNo) {
        if (timeline == null) {
            return;
        }
        timeline.removeIf(n -> PORK_TIMELINE_EXCLUDED.contains(n.getTraceContent()));
        LocalDateTime cutTime = findWhiteBarCutTime(earNo);
        addProcessNode(timeline, TraceContentConst.WHITE_BAR_CUT, cutTime);
        timeline.sort(Comparator.comparing(PublicTraceVo.TimelineNode::getTraceTime,
            Comparator.nullsLast(Comparator.reverseOrder())));
    }

    /**
     * 白条分割时刻：按耳号查 {@code cut_record}（{@code out_type='cut'}）最早一次分割开始时间
     * {@code cut_start_time}；无（外购 / 未分割 / 时间未落）→ null（不补节点）。
     */
    private LocalDateTime findWhiteBarCutTime(String earNo) {
        if (StringUtils.isBlank(earNo)) {
            return null;
        }
        PigCutRecord cut = pigCutRecordMapper.selectOne(
            new LambdaQueryWrapper<PigCutRecord>()
                .select(PigCutRecord::getCutStartTime)
                .eq(PigCutRecord::getEarNo, earNo)
                .eq(PigCutRecord::getOutType, PIG_CUT_OUT_TYPE_CUT)
                .isNotNull(PigCutRecord::getCutStartTime)
                .orderByAsc(PigCutRecord::getCutStartTime)
                .last("limit 1"));
        return cut == null ? null : toLocalDateTime(cut.getCutStartTime());
    }

    private List<PublicTraceVo.GrowthRow> toGrowthRows(List<PigGrowth> growths, LocalDate birthDate) {
        if (growths.isEmpty()) {
            return new ArrayList<>();
        }
        // 操作人 id → 姓名（批量，复用 timeline 同款直查；@SaIgnore 不走翻译注解）
        Map<Long, String> nameMap = resolveOperatorNames(
            growths.stream().map(PigGrowth::getOperatorId).filter(Objects::nonNull).distinct().toList());
        List<PublicTraceVo.GrowthRow> rows = new ArrayList<>(growths.size());
        for (PigGrowth g : growths) {
            PublicTraceVo.GrowthRow row = new PublicTraceVo.GrowthRow();
            row.setDate(g.getMeasureDate());
            row.setAgeDays(calcAgeDays(birthDate, g.getMeasureDate()));
            row.setWeight(toStr(g.getWeight()));
            row.setBackfat(toStr(g.getBackfatThickness()));
            row.setOperatorName(g.getOperatorId() == null ? null : nameMap.get(g.getOperatorId()));
            // 照片 ossId → 可访问 URL（首图；C 端无登录无法自行反查 oss）
            row.setPhotoUrl(resolveOssUrl(g.getPhotoOssIds()));
            rows.add(row);
        }
        return rows;
    }

    private List<PublicTraceVo.MedicationRow> toMedicationRows(String earNo, LocalDate birthDate) {
        List<MedRecord> records = medRecordMapper.selectList(
            new LambdaQueryWrapper<MedRecord>()
                .eq(MedRecord::getEarNo, earNo)
                .orderByAsc(MedRecord::getUseDate));
        if (records.isEmpty()) {
            return new ArrayList<>();
        }
        // 药品名优先用冗余列，缺失才反查 Medicine
        List<Long> needIds = records.stream()
            .filter(r -> StringUtils.isBlank(r.getMedicineName()) && r.getMedicineId() != null)
            .map(MedRecord::getMedicineId).distinct().toList();
        Map<Long, String> medNames = needIds.isEmpty() ? Collections.emptyMap()
            : medicineMapper.selectByIds(needIds).stream()
                .collect(Collectors.toMap(Medicine::getId, Medicine::getMedicineName, (a, b) -> a));
        // 操作人姓名优先用 MedRecord 冗余列，缺失才按 operatorId 直查
        List<Long> opIds = records.stream()
            .filter(r -> StringUtils.isBlank(r.getOperatorName()) && r.getOperatorId() != null)
            .map(MedRecord::getOperatorId).distinct().toList();
        Map<Long, String> opNames = opIds.isEmpty() ? Collections.emptyMap()
            : resolveOperatorNames(opIds);
        List<PublicTraceVo.MedicationRow> rows = new ArrayList<>(records.size());
        for (MedRecord r : records) {
            PublicTraceVo.MedicationRow row = new PublicTraceVo.MedicationRow();
            row.setDate(r.getUseDate());
            row.setAgeDays(calcAgeDays(birthDate, r.getUseDate() == null ? null : r.getUseDate().toLocalDate()));
            String name = StringUtils.isNotBlank(r.getMedicineName())
                ? r.getMedicineName()
                : (r.getMedicineId() == null ? null : medNames.get(r.getMedicineId()));
            row.setName(name);
            row.setType(r.getMedicineType());
            row.setReason(r.getMedicineReason());
            row.setOperatorName(StringUtils.isNotBlank(r.getOperatorName())
                ? r.getOperatorName()
                : (r.getOperatorId() == null ? null : opNames.get(r.getOperatorId())));
            rows.add(row);
        }
        return rows;
    }

    /**
     * 谱系块：复用 breed {@code ISowDetailService#queryPedigree}（本猪 id 反查父母 Pig 的品种/日龄/胎次）。
     * 父母耳号全空（PedigreeVo 关键字段全 null）→ 返 null，前端整块隐藏，不显空卡。
     */
    private PublicTraceVo.PedigreeBlock buildPedigree(Long pigId) {
        PedigreeVo p = sowDetailService.queryPedigree(pigId);
        if (p == null
            || (StringUtils.isBlank(p.getSireEarNo()) && StringUtils.isBlank(p.getDamEarNo()))) {
            return null;
        }
        PublicTraceVo.PedigreeBlock block = new PublicTraceVo.PedigreeBlock();
        block.setSireEarNo(p.getSireEarNo());
        block.setSireBreed(p.getSireBreed());
        block.setSireAgeDays(p.getSireAgeDays());
        block.setDamEarNo(p.getDamEarNo());
        block.setDamBreed(p.getDamBreed());
        block.setDamAgeDays(p.getDamAgeDays());
        block.setDamParity(p.getDamParity());
        return block;
    }

    // ============================ veg 分支 ============================

    private void fillVeg(PublicTraceVo vo, TraceCode code, List<PublicTraceVo.TimelineNode> timeline) {
        // 作物：trace_code 无 crop_id，name 用产品名兜底；variety 无关联返 null
        PublicTraceVo.CropBlock cropBlock = new PublicTraceVo.CropBlock();
        if (vo.getProduct() != null) {
            cropBlock.setName(vo.getProduct().getName());
        }
        vo.setCrop(cropBlock);

        // 产品块补 果蔬专属字段：生长天数 / 采摘日期（trace_code 直接有；地块名见下方 plot 分支）
        if (vo.getProduct() != null) {
            vo.getProduct().setGrowthDays(code.getPlantDays());
            vo.getProduct().setHarvestDate(code.getHarvestDate());
        }

        // ② 重量改取打包实际称重：本追溯码对应的发货生产记录（trace_code 关联）product_weight；缺则保留规格兜底
        ProductProduction pack = findPackProduction(code.getProduceCode());
        if (pack != null && pack.getProductWeight() != null && vo.getProduct() != null) {
            vo.getProduct().setWeight(toStr(pack.getProductWeight()));
        }

        // 地块 + 片区
        if (code.getPlotId() != null) {
            PlotInfo plot = plotInfoMapper.selectById(code.getPlotId());
            if (plot != null) {
                PublicTraceVo.PlotBlock plotBlock = new PublicTraceVo.PlotBlock();
                plotBlock.setPlotName(plot.getPlotCode());
                plotBlock.setArea(toStr(plot.getPlotArea()));
                if (plot.getZoneId() != null) {
                    PlotZone zone = plotZoneMapper.selectById(plot.getZoneId());
                    if (zone != null) {
                        plotBlock.setZoneName(zone.getZoneName());
                        // ③ 所属大区（片区 zone_belong）
                        plotBlock.setZoneBelong(zone.getZoneBelong());
                    }
                }
                vo.setPlot(plotBlock);
                // 产品块冗余地块名（原型产品信息块展示地块编号/名）
                if (vo.getProduct() != null) {
                    vo.getProduct().setPlotName(plot.getPlotCode());
                }
            }
            // 地块农事（row63：过滤掉采摘 / 采摘活动，追溯页只显真实农事工序）
            List<FarmRecords> records = farmRecordsMapper.selectList(
                new LambdaQueryWrapper<FarmRecords>()
                    .eq(FarmRecords::getPlotId, code.getPlotId())
                    .notIn(FarmRecords::getFarmType, VEG_PLOT_RECORD_EXCLUDED)
                    .orderByAsc(FarmRecords::getFarmDate));
            vo.setPlotRecords(toPlotRecordRows(records));
            // 该地块历史种植作物（与农事记录并存）
            vo.setPlotCropHistory(buildPlotCropHistory(code.getPlotId()));
        } else {
            vo.setPlotRecords(new ArrayList<>());
            vo.setPlotCropHistory(new ArrayList<>());
        }

        // ④ 时间轴重构成 5 节点：种植 / 采摘 / 产品生产 / 冷链发货 / 到店（row62）
        appendVegProcessNodes(timeline, code, pack);

        // 有机证书（作物认证 + 地块认证，图 ossId 解析 url）
        vo.setOrganicCerts(buildOrganicCerts(code));

        // 销售门店（veg 也挂 store_id；与 pork 共用 fillStore，原型果蔬主页含「销售信息」块）
        fillStore(vo, code);
    }

    /**
     * 本追溯码对应的发货生产记录（{@code t_warehouse_product_production.trace_code = produceCode}）。
     * 打包入库时 {@code TraceService.genCode} 回填 trace_code，故一条 veg 追溯码唯一对应一条生产记录。
     * 提供打包工序的实际称重（{@code product_weight}）与打包时间（{@code produce_time/produce_date}）。
     * 无（历史数据或未打包）→ null，调用方各自兜底。
     */
    private ProductProduction findPackProduction(String produceCode) {
        if (StringUtils.isBlank(produceCode)) {
            return null;
        }
        return productProductionMapper.selectOne(
            new LambdaQueryWrapper<ProductProduction>()
                .eq(ProductProduction::getTraceCode, produceCode)
                .orderByDesc(ProductProduction::getId)
                .last("limit 1"));
    }

    /**
     * 果蔬工序时间轴重构成 5 节点（DENGBO row62）：种植 / 采摘 / 产品生产 / 冷链发货 / 到店。
     *
     * <p>来源（按节点）：</p>
     * <ul>
     *   <li>种植（sowing）：种植明细 {@code plant_details.create_time}（录入时刻，DATETIME 有时分秒）近似种植完成时间；
     *       无明细则退化种植日 {@code planting_record.plant_date}（DATE）0 点，plant_date 缺则 {@code 采摘日 − 生长天数} 反推。</li>
     *   <li>采摘（harvest）：仓库种植记录 {@code planting_record.data_date}（DATETIME 有时分秒，「一般同采摘时间」）；
     *       无则退化采摘日（{@code trace_code.havest_date} / 种植记录 {@code harvest_date}，DATE）0 点。</li>
     *   <li>产品生产（pack）：发货生产记录 {@code product_production.produce_time}（无则 {@code produce_date}），
     *       即果蔬在仓库打包的时间。</li>
     *   <li>冷链发货（ship）/ 到店（arrival）：{@code trace_event} 基础节点（发货月台确认发货 / 门店确认到店）。</li>
     * </ul>
     *
     * <p><b>去掉</b>：{@code in_stock}(入库)——产品生产改用打包节点，不再另显入库；不再补 {@code veg_handle}(毛菜处理)。
     * 某节点时间无源 → 不造该节点（不显空节点）。合成节点 operatorName 为 null（上游表无统一记录人映射）。
     * 按 {@code trace_time} 倒序（最新在上，对齐原型「由下至上：种植→到店」）。</p>
     */
    private void appendVegProcessNodes(List<PublicTraceVo.TimelineNode> timeline,
                                       TraceCode code, ProductProduction pack) {
        if (timeline == null) {
            return;
        }
        // 去掉基础 in_stock 事件节点：产品生产改用下方打包节点，不再另显入库
        timeline.removeIf(n -> TraceContentConst.IN_STOCK.equals(n.getTraceContent()));

        // 仓库种植记录（种植 / 采摘兜底时间源）：按 plot_id + product_id 取一条
        PlantingRecord planting = findPlantingRecord(code.getPlotId(), code.getProductId());

        // 邓博 row19：种植 / 采摘节点写班组名（非人员名）；取仓库种植记录的 team_name，无则 null。
        String teamName = planting != null ? planting.getTeamName() : null;

        // 采摘（采摘开始时间）：优先取种植记录 data_date（DATETIME「数据生成时间，一般同采摘时间」，
        // 有真实时分秒），无则退化采摘日 0 点（trace_code.havest_date / planting.harvest_date 均 DATE，无时分秒）。
        LocalDate harvestDate = code.getHarvestDate() != null
            ? code.getHarvestDate()
            : (planting != null ? toLocalDate(planting.getHarvestDate()) : null);
        LocalDateTime harvestTime = planting != null ? toLocalDateTime(planting.getDataDate()) : null;
        if (harvestTime == null) {
            harvestTime = atDayStart(harvestDate);
        }

        // 种植（种植完成时间）：plant_date 为 DATE 无时分秒，取该地块种植明细录入时刻
        // plant_details.create_time（DATETIME，有真实时分秒）近似；无明细或晚于采摘时间则退化种植日 0 点
        // （保证时间轴顺序 种植 ≤ 采摘 ≤ 产品生产，不倒挂）。
        LocalDate sowDate = planting != null ? toLocalDate(planting.getPlantDate()) : null;
        if (sowDate == null && code.getHarvestDate() != null && code.getPlantDays() != null) {
            sowDate = code.getHarvestDate().minusDays(code.getPlantDays());
        }
        LocalDateTime sowTime = resolvePlantRecordTime(code.getPlotId());
        if (sowTime == null || (harvestTime != null && sowTime.isAfter(harvestTime))) {
            sowTime = atDayStart(sowDate);
        }

        addProcessNode(timeline, TraceContentConst.SOWING, sowTime, teamName);
        addProcessNode(timeline, TraceContentConst.HARVEST, harvestTime, teamName);

        // 产品生产（打包）
        if (pack != null) {
            Date packTime = pack.getProduceTime() != null
                ? pack.getProduceTime() : pack.getProduceDate();
            addProcessNode(timeline, TraceContentConst.PACK, toLocalDateTime(packTime));
        }

        // 按时间倒序（null 时间排末尾，最新在上，对齐「由下至上：种植→到店」）
        timeline.sort(Comparator.comparing(PublicTraceVo.TimelineNode::getTraceTime,
            Comparator.nullsLast(Comparator.reverseOrder())));
    }

    /**
     * 仓库种植记录：优先按 plot_id + product_id 取最近一条；exact 命中不到（种植记录 product_id 常为 NULL、
     * 与追溯码 product_id 对不上）则退化为仅按 plot_id 取该地块最近一条——否则果蔬追溯的种植/采摘节点会因 join key
     * 对不上被整体跳过（row74）。
     */
    private PlantingRecord findPlantingRecord(Long plotId, Long productId) {
        if (plotId == null) {
            return null;
        }
        if (productId != null) {
            PlantingRecord exact = plantingRecordMapper.selectOne(
                new LambdaQueryWrapper<PlantingRecord>()
                    .eq(PlantingRecord::getPlotId, plotId)
                    .eq(PlantingRecord::getProductId, productId)
                    .orderByDesc(PlantingRecord::getHarvestDate)
                    .orderByDesc(PlantingRecord::getId)
                    .last("limit 1"));
            if (exact != null) {
                return exact;
            }
        }
        return plantingRecordMapper.selectOne(
            new LambdaQueryWrapper<PlantingRecord>()
                .eq(PlantingRecord::getPlotId, plotId)
                .orderByDesc(PlantingRecord::getHarvestDate)
                .orderByDesc(PlantingRecord::getId)
                .last("limit 1"));
    }

    /**
     * 种植节点时分秒源：按 plot 取最近一条种植明细的 {@code create_time}（DATETIME，录入时刻，有时分秒）。
     *
     * <p>种植日 {@code plant_date} 是 DATE 无时分秒，故用种植明细录入时刻近似「种植完成时间」（与产品生产节点
     * 取 produce_time 录入时刻同理）；无种植明细返 null（调用方退化种植日 0 点）。</p>
     */
    private LocalDateTime resolvePlantRecordTime(Long plotId) {
        if (plotId == null) {
            return null;
        }
        PlantDetails detail = plantDetailsMapper.selectOne(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getPlotId, plotId)
                .orderByDesc(PlantDetails::getBeginActualdate)
                .orderByDesc(PlantDetails::getId)
                .last("limit 1"));
        return detail != null ? toLocalDateTime(detail.getCreateTime()) : null;
    }

    /** 时间非空才补节点（避免显示无意义空时间工序行）。 */
    private void addProcessNode(List<PublicTraceVo.TimelineNode> timeline,
                                String traceContent, LocalDateTime time) {
        addProcessNode(timeline, traceContent, time, null);
    }

    /** 时间非空才补节点；operatorName 可空（种植/采摘传班组名，邓博 row19）。 */
    private void addProcessNode(List<PublicTraceVo.TimelineNode> timeline,
                                String traceContent, LocalDateTime time, String operatorName) {
        if (time == null) {
            return;
        }
        PublicTraceVo.TimelineNode node = new PublicTraceVo.TimelineNode();
        node.setTraceContent(traceContent);
        node.setTraceTime(time);
        node.setOperatorName(operatorName);
        timeline.add(node);
    }

    /** {@code java.util.Date} / {@code java.sql.Date} → LocalDate（sql.Date.toInstant 会抛 UOE，单独走 toLocalDate）。 */
    private LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        if (date instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** {@code java.util.Date} / {@code java.sql.Date} → LocalDateTime（sql.Date 无时分秒，取当日 0 点）。 */
    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        if (date instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().atStartOfDay();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private LocalDateTime atDayStart(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    /** 门店信息块（pork/veg 共用）：trace_code.store_id → Store name/address；无 store_id → 不 set（前端隐藏）。 */
    private void fillStore(PublicTraceVo vo, TraceCode code) {
        if (code.getStoreId() == null) {
            return;
        }
        Store s = storeMapper.selectById(code.getStoreId());
        if (s != null) {
            PublicTraceVo.StoreBlock storeBlock = new PublicTraceVo.StoreBlock();
            storeBlock.setName(s.getStoreName());
            storeBlock.setAddress(s.getAddress());
            // 门店配图（row146）：image_oss_id 解析 URL，无图为 null（前端默认图兜底）
            storeBlock.setImageUrl(s.getImageOssId() == null
                ? null : resolveOssUrl(String.valueOf(s.getImageOssId())));
            vo.setStore(storeBlock);
        }
    }

    private List<PublicTraceVo.PlotRecordRow> toPlotRecordRows(List<FarmRecords> records) {
        // 记录人 id → 姓名（批量，避免 N+1；@SaIgnore 不走翻译注解）
        Map<Long, String> nameMap = resolveOperatorNames(
            records.stream().map(FarmRecords::getOperatorUserId).filter(Objects::nonNull).distinct().toList());
        List<PublicTraceVo.PlotRecordRow> rows = new ArrayList<>(records.size());
        for (FarmRecords r : records) {
            PublicTraceVo.PlotRecordRow row = new PublicTraceVo.PlotRecordRow();
            row.setDate(r.getFarmDate());
            row.setWorkType(r.getFarmType());
            row.setDetail(r.getRemark());
            row.setOperatorName(r.getOperatorUserId() == null ? null : nameMap.get(r.getOperatorUserId()));
            rows.add(row);
        }
        return rows;
    }

    /**
     * 该地块历史种植作物：按 plot_id 查 {@code t_plant_plant_details}（种植起始日倒序），
     * 关联作物名/图。无种植记录 → 空 list（前端整块隐藏）。
     * 生长天数 = 采摘日（实际 begin_harvestdate 缺则计划 earliest_harvestdate）− 种植日（begin_actualdate）；任一缺 → null。
     */
    private List<PublicTraceVo.PlotCropHistoryRow> buildPlotCropHistory(Long plotId) {
        List<PlantDetails> details = plantDetailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getPlotId, plotId)
                .orderByDesc(PlantDetails::getBeginActualdate)
                .orderByDesc(PlantDetails::getId));
        if (details.isEmpty()) {
            return new ArrayList<>();
        }
        // 批量取作物名 + 图（一次查，避免 N+1）
        List<Long> cropIds = details.stream()
            .map(PlantDetails::getCropId).filter(Objects::nonNull).distinct().toList();
        Map<Long, CropInfo> cropMap = cropIds.isEmpty() ? Collections.emptyMap()
            : cropInfoMapper.selectByIds(cropIds).stream()
                .collect(Collectors.toMap(CropInfo::getId, c -> c, (a, b) -> a));
        List<PublicTraceVo.PlotCropHistoryRow> rows = new ArrayList<>(details.size());
        for (PlantDetails d : details) {
            PublicTraceVo.PlotCropHistoryRow row = new PublicTraceVo.PlotCropHistoryRow();
            CropInfo crop = d.getCropId() == null ? null : cropMap.get(d.getCropId());
            if (crop != null) {
                row.setCropName(crop.getCropName());
                row.setImageUrl(resolveOssUrl(crop.getCropImagePreview()));
            }
            LocalDate start = d.getBeginActualdate();
            LocalDate end = d.getBeginHarvestdate() != null
                ? d.getBeginHarvestdate() : d.getEarliestHarvestdate();
            row.setStartDate(start);
            row.setEndDate(end);
            row.setGrowthDays(calcGrowthDays(start, end));
            rows.add(row);
        }
        return rows;
    }

    /** 生长天数 = end − start（天）；任一缺或负 → null。 */
    private Integer calcGrowthDays(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(start, end);
        return days >= 0 ? (int) days : null;
    }

    /** 日龄 = 目标日 − 出生日（天）；任一缺或负 → null（与生长天数同款日差逻辑）。 */
    private Integer calcAgeDays(LocalDate birthDate, LocalDate targetDate) {
        return calcGrowthDays(birthDate, targetDate);
    }

    private List<PublicTraceVo.OrganicCertRow> buildOrganicCerts(TraceCode code) {
        List<PublicTraceVo.OrganicCertRow> certs = new ArrayList<>();
        Set<Long> seenCertIds = new HashSet<>();
        // 作物有机证书（row149）：追溯码 crop_cert_id 在「一证多作物」重构后废弃恒 NULL，改按
        // 产品 → 作物（related_product）→ 关联表 t_plant_crop_organic_rel 解析当前有效证书（可多张）。
        if (code.getProductId() != null) {
            for (Long certId : vegDisplayNameMapper.selectVegOrganicCertIds(code.getProductId())) {
                CropOrganic c = certId == null ? null : cropOrganicMapper.selectById(certId);
                if (c != null && seenCertIds.add(certId)) {
                    PublicTraceVo.OrganicCertRow row = new PublicTraceVo.OrganicCertRow();
                    row.setCertType("crop");
                    row.setImageUrl(resolveCertImage(c.getCropImagePreview(), c.getCropImageUrl()));
                    row.setImageUrls(resolveCertImages(c.getCropImagePreview(), c.getCropImageUrl()));
                    row.setIssuer(c.getCropCertCompany());
                    row.setCertNo(c.getCropCertNo());
                    row.setValidTo(c.getCropCertValid());
                    certs.add(row);
                }
            }
        }
        // 兼容旧单值 crop_cert_id（一证多作物重构前生成的码；当前恒 NULL，保留避免历史码有机证书回归）
        if (code.getCropCertId() != null && seenCertIds.add(code.getCropCertId())) {
            CropOrganic c = cropOrganicMapper.selectById(code.getCropCertId());
            if (c != null) {
                PublicTraceVo.OrganicCertRow row = new PublicTraceVo.OrganicCertRow();
                row.setCertType("crop");
                row.setImageUrl(resolveCertImage(c.getCropImagePreview(), c.getCropImageUrl()));
                row.setImageUrls(resolveCertImages(c.getCropImagePreview(), c.getCropImageUrl()));
                row.setIssuer(c.getCropCertCompany());
                row.setCertNo(c.getCropCertNo());
                row.setValidTo(c.getCropCertValid());
                certs.add(row);
            }
        }
        if (code.getPlotCertId() != null) {
            PlotOrganic p = plotOrganicMapper.selectById(code.getPlotCertId());
            if (p != null) {
                PublicTraceVo.OrganicCertRow row = new PublicTraceVo.OrganicCertRow();
                row.setCertType("plot");
                row.setImageUrl(resolveCertImage(p.getOrganicImagePreview(), p.getOrganicImageUrl()));
                row.setImageUrls(resolveCertImages(p.getOrganicImagePreview(), p.getOrganicImageUrl()));
                row.setIssuer(p.getOrganicCompany());
                row.setCertNo(p.getOrganicNo());
                row.setValidTo(p.getOrganicValid());
                certs.add(row);
            }
        }
        return certs;
    }

    // ============================ gift 分支 ============================

    private void fillGift(PublicTraceVo vo, TraceCode code) {
        List<PublicTraceVo.ComponentRow> components = new ArrayList<>();
        String json = code.getGiftComponents();
        // V1 gift_components 写 NULL（子码 V3），解析为空 list；
        // 有数据时按 [{subType,subTraceCode,productName}] JSON 数组解析（Jackson，对齐 ruoyi 序列化）
        if (StringUtils.isNotBlank(json)) {
            try {
                List<Dict> arr = JsonUtils.parseArrayMap(json);
                if (arr != null) {
                    for (Dict jo : arr) {
                        PublicTraceVo.ComponentRow row = new PublicTraceVo.ComponentRow();
                        row.setSubType(strOf(jo.get("subType")));
                        row.setSubTraceCode(strOf(jo.get("subTraceCode")));
                        row.setProductName(strOf(jo.get("productName")));
                        components.add(row);
                    }
                }
            } catch (Exception e) {
                // gift_components 非标准 JSON 数组（V1 预留场景）→ 返空 list，不阻塞主链路
                log.warn("[trace-public] gift_components 解析失败 produceCode={}: {}",
                    code.getProduceCode(), e.getMessage());
            }
        }
        vo.setComponents(components);
    }

    // ============================ 内部辅助 ============================

    /** ossId（单个或逗号分隔，取首个 url）→ 可访问 URL；无 oss 返 null。 */
    private String resolveOssUrl(String ossIds) {
        if (StringUtils.isBlank(ossIds)) {
            return null;
        }
        String urls = ossService.selectUrlByIds(ossIds);
        if (StringUtils.isBlank(urls)) {
            return null;
        }
        // 多图取首张展示
        int comma = urls.indexOf(',');
        return comma >= 0 ? urls.substring(0, comma) : urls;
    }

    /**
     * 有机证书图 URL（row157）：缩略图 ossId 优先，为空则回落原图 ossIds 首张。
     * <p>admin 有机证书配置表单（cropOrganic / plotOrganic）只上传到原图列
     * （{@code crop_image_url} / {@code organic_image_url}），缩略图列
     * （{@code crop_image_preview} / {@code organic_image_preview}）恒空——只读缩略图列会导致追溯页有机认证无图。</p>
     */
    private String resolveCertImage(String previewOssId, String originOssIds) {
        return resolveOssUrl(StringUtils.isNotBlank(previewOssId) ? previewOssId : originOssIds);
    }

    /**
     * 有机证书全部图 URL（row162）：缩略图 ossId 优先、为空回落原图 ossIds；CSV 多图全解析
     * （一证多图，追溯页自适应网格全部展示，不再只取首张）。无图返空 list。
     */
    private java.util.List<String> resolveCertImages(String previewOssId, String originOssIds) {
        String ossIds = StringUtils.isNotBlank(previewOssId) ? previewOssId : originOssIds;
        if (StringUtils.isBlank(ossIds)) {
            return java.util.Collections.emptyList();
        }
        String urls = ossService.selectUrlByIds(ossIds);
        if (StringUtils.isBlank(urls)) {
            return java.util.Collections.emptyList();
        }
        return java.util.Arrays.stream(urls.split(","))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .toList();
    }

    /** 农场名（sys_farm，无 djs 实体，走 admin farm-name mapper 自定义 SQL）。 */
    private String resolveFarmName(Long farmId) {
        if (farmId == null) {
            return null;
        }
        List<Map<String, Object>> rows = traceFarmNameMapper.selectFarmNames(List.of(farmId), DEFAULT_TENANT);
        return rows.stream().findFirst()
            .map(m -> m.get("farmName")).map(String::valueOf).orElse(null);
    }

    /** operatorId → nick_name（直查 sys_user，不走翻译注解：@SaIgnore 端翻译上下文缺失）。 */
    private Map<Long, String> resolveOperatorNames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return traceUserNameMapper.selectUserNames(userIds).stream()
            .filter(m -> m.get("userId") != null && m.get("nickName") != null)
            .collect(Collectors.toMap(
                m -> ((Number) m.get("userId")).longValue(),
                m -> String.valueOf(m.get("nickName")),
                (a, b) -> a));
    }

    private LocalDateTime findEventTime(List<PublicTraceVo.TimelineNode> timeline, String traceContent) {
        if (timeline == null) {
            return null;
        }
        return timeline.stream()
            .filter(n -> traceContent.equals(n.getTraceContent()))
            .map(PublicTraceVo.TimelineNode::getTraceTime)
            .findFirst().orElse(null);
    }

    private String toStr(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    /** 千克 → 克整数字符串（肉品打包 product_weight 存 kg，猪肉追溯页按克展示，如 0.500kg → "500g"）。 */
    private String toGramStr(BigDecimal kg) {
        if (kg == null) {
            return null;
        }
        return kg.multiply(new BigDecimal("1000")).stripTrailingZeros().toPlainString() + "g";
    }

    private String strOf(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
