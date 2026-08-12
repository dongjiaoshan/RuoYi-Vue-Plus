package org.dromara.djs.warehouse.thirdphase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.UserService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.util.LikeEscape;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.domain.CropProduct;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.crop.mapper.CropProductMapper;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.thirdphase.domain.ThirdPhaseIn;
import org.dromara.djs.warehouse.thirdphase.domain.bo.ThirdPhaseInBo;
import org.dromara.djs.warehouse.thirdphase.domain.query.ThirdPhaseInQuery;
import org.dromara.djs.warehouse.thirdphase.domain.vo.ThirdPhaseInVo;
import org.dromara.djs.warehouse.thirdphase.domain.vo.ThirdPhaseSummaryVo;
import org.dromara.djs.warehouse.thirdphase.mapper.ThirdPhaseInMapper;
import org.dromara.djs.warehouse.thirdphase.service.IThirdPhaseInService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 三期作物入库 Service 实现（V6 row88）。
 *
 * <h3>「三期」是什么</h3>
 * <p>甲方作物的<b>第三茬 / 第三期</b>，不是开发三期（Kevin 2026-08-12 拍板）。
 * 标识打在<b>地块</b>上（{@code t_plant_plot_info.third_phase}），不是作物、不是种植计划批次。</p>
 *
 * <h3>{@link #submit} 单事务顺序</h3>
 * <ol>
 *   <li>产品守门：必须是果蔬类原材料（{@code product_attr=2 且 belong_type='vegetable'}），
 *       口径与 {@code VegReceiveServiceImpl#resolveVegMaterialProductId} 同一份</li>
 *   <li>库位守门：必须是毛菜鲜品库（{@code location_code='L0006'}，按 code 查 id，不硬编码 id）</li>
 *   <li>入库量守门：&gt; 0 且最多 3 位小数（Bo 上的 Bean Validation 已挡一道，这里是接口直调的兜底）</li>
 *   <li>反查作物：产品 → 作物（先 {@code t_plant_crop_product} 配置表，回落 {@code crop.related_product}）；
 *       查不到 → 中文报错说清去哪配</li>
 *   <li>写库存：{@code stock_flow}（{@code flow_type=third_phase_in / inout_type=IN}）+
 *       {@code location_stock} —— 口径照抄 {@code VegetableHandleServiceImpl#insertPickStockIn}；
 *       两侧都打 {@code third_phase=1}，库存篮按 {@code third_phase} 与普通货分篮（见 {@link #insertStockIn}）</li>
 *   <li>给该作物「当前在种」的地块打 {@code third_phase=1}</li>
 *   <li>落一行 {@code t_warehouse_third_phase_in}（作物 / 产品 / 库位 / 地块 / 入库人快照）</li>
 * </ol>
 *
 * <h3>「当前在种的地块」口径</h3>
 * <p>直接复用本项目已有的<b>在种</b>口径（{@code PlantDashboardMapper#selectCropPlantStat}
 * 「实时种植物统计·在种地块数」与 {@code selectCurrentExpectedYield} 同一份）：
 * {@code t_plant_plant_details} 里 {@code plant_status='completed' AND harvest_status<>'completed'}
 * 的行，取 {@code DISTINCT plot_id}。不另发明第二套判定。</p>
 *
 * <p>一个作物可能同时在多块地上 → <b>多块都打</b>，记录行的 {@code plotNames} 逗号拼接。</p>
 *
 * <p><b>命中 0 块地时不阻断入库</b>：入库是仓库对实物的动作，货已经进了毛菜鲜品库；
 * 因为种植侧的计划明细状态没到位就拒收，会让库存与实物对不上（比标识没打上严重得多）。
 * 此时 {@code plot_ids} / {@code plot_names} 落空串 —— 列表 / 接口上直接看得出「一块地都没打上」，
 * 不是静默跳过；同时打 WARN 日志。</p>
 *
 * @author djs
 * @since V6-R88
 */
@Slf4j
@Service
public class ThirdPhaseInServiceImpl
    extends DjsBaseServiceImpl<ThirdPhaseInMapper, ThirdPhaseIn>
    implements IThirdPhaseInService {

    /** 毛菜鲜品室（= 甲方说的「毛菜保鲜室」）库位业务码。按 code 查 id，不硬编码 id。 */
    private static final String LOCATION_CODE_FRESH_VEG = "L0006";

    /** {@code stock_flow.flow_type}：三期作物入库（字典 {@code djs_flow_type}，V202608301700 seed）。 */
    private static final String FLOW_TYPE_THIRD_PHASE_IN = "third_phase_in";

    /** {@code stock_flow.inout_type} CHAR(3)：IN=入库。 */
    private static final String INOUT_IN = "IN";

    /** {@code stock_flow.inout_type} CHAR(3)：OT=出库。 */
    private static final String INOUT_OUT = "OT";

    /** {@code djs_product_attr}：2=原材料。 */
    private static final int PRODUCT_ATTR_MATERIAL = 2;

    /** {@code djs_belong_type}：果蔬。 */
    private static final String BELONG_TYPE_VEGETABLE = "vegetable";

    /** {@code djs_plant_plan_status}：种植完成（= 苗已下地，进入产出期）。 */
    private static final String PLANT_STATUS_COMPLETED = "completed";

    /** {@code djs_pick_status}：采摘完成（采完即不再算「在种」）。 */
    private static final String HARVEST_STATUS_COMPLETED = "completed";

    /** 【三期】标识：1=是。三处共用 —— 地块 / 入库流水 / 入库库存篮。 */
    private static final int THIRD_PHASE_YES = 1;

    /** 入库量小数位上限（甲方：支持三位小数）。 */
    private static final int STOCK_NUM_SCALE = 3;

    private final ProductInfoMapper productInfoMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final StockFlowMapper stockFlowMapper;
    private final LocationStockMapper locationStockMapper;
    private final CropInfoMapper cropInfoMapper;
    /** 作物-产品配置（V6 row16 起的权威映射，一个作物可配多个产品）。 */
    private final CropProductMapper cropProductMapper;
    private final PlantDetailsMapper plantDetailsMapper;
    private final PlotInfoMapper plotInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final UserService userService;

    public ThirdPhaseInServiceImpl(ThirdPhaseInMapper baseMapper,
                                   ProductInfoMapper productInfoMapper,
                                   LocationInfoMapper locationInfoMapper,
                                   StockFlowMapper stockFlowMapper,
                                   LocationStockMapper locationStockMapper,
                                   CropInfoMapper cropInfoMapper,
                                   CropProductMapper cropProductMapper,
                                   PlantDetailsMapper plantDetailsMapper,
                                   PlotInfoMapper plotInfoMapper,
                                   IBizCodeGenerator bizCodeGenerator,
                                   UserService userService) {
        super(baseMapper);
        this.productInfoMapper = productInfoMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.locationStockMapper = locationStockMapper;
        this.cropInfoMapper = cropInfoMapper;
        this.cropProductMapper = cropProductMapper;
        this.plantDetailsMapper = plantDetailsMapper;
        this.plotInfoMapper = plotInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.userService = userService;
    }

    // ==================================================================== 列表

    @Override
    public TableDataInfo<ThirdPhaseInVo> queryPage(ThirdPhaseInQuery query, PageQuery pageQuery) {
        IPage<ThirdPhaseInVo> page = baseMapper.selectVoPage(pageQuery.build(), buildWrapper(query));
        fillOperatorNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<ThirdPhaseInVo> queryList(ThirdPhaseInQuery query) {
        List<ThirdPhaseInVo> rows = baseMapper.selectVoList(buildWrapper(query));
        fillOperatorNames(rows);
        return rows;
    }

    /**
     * 列表 / 导出共用 wrapper：入库时间区间 + 作物名模糊，按入库时间倒序（同刻按 id 倒序稳定分页）。
     *
     * <p>{@code endTime} 是纯日期，补到当天 {@code 23:59:59} 再比 —— 直接 {@code <= '2026-08-12'}
     * 会被 MySQL 当成 {@code 2026-08-12 00:00:00}，把结束当天整天漏掉。</p>
     *
     * <p>{@code cropName} 进 LIKE 前过 {@link LikeEscape#escape}：不转义时用户输一个 {@code %}
     * 就变通配符（「搜什么都返回全部」），{@code _} 更隐蔽。</p>
     */
    private LambdaQueryWrapper<ThirdPhaseIn> buildWrapper(ThirdPhaseInQuery query) {
        ThirdPhaseInQuery q = query != null ? query : new ThirdPhaseInQuery();
        LambdaQueryWrapper<ThirdPhaseIn> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(q.getBeginTime())) {
            wrapper.ge(ThirdPhaseIn::getInTime, q.getBeginTime().trim() + " 00:00:00");
        }
        if (StringUtils.isNotBlank(q.getEndTime())) {
            wrapper.le(ThirdPhaseIn::getInTime, q.getEndTime().trim() + " 23:59:59");
        }
        if (StringUtils.isNotBlank(q.getCropName())) {
            wrapper.like(ThirdPhaseIn::getCropName, LikeEscape.escape(q.getCropName().trim()));
        }
        wrapper.orderByDesc(ThirdPhaseIn::getInTime).orderByDesc(ThirdPhaseIn::getId);
        return wrapper;
    }

    /** 批量回填入库人姓名（同 {@code VegOutServiceImpl#fillOperatorNames} 口径）。 */
    private void fillOperatorNames(List<ThirdPhaseInVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> ids = rows.stream()
            .map(ThirdPhaseInVo::getOperatorId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = new HashMap<>(ids.size());
        for (Long id : ids) {
            nameMap.put(id, StringUtils.blankToDefault(userService.selectNicknameById(id), ""));
        }
        rows.forEach(r -> r.setOperatorName(nameMap.get(r.getOperatorId())));
    }

    // ================================================================ 三期合计

    /**
     * 【三期】总入库 / 总出库合计。
     *
     * <p>判据是<b>流水行上的 {@code third_phase}</b>，不是地块 —— 三期没有真实地块（甲方口径），
     * 按地块统计会永远是 0。入库由本 service 打标，出库由 {@code VegOutServiceImpl} 从被出的
     * 库存篮把标识带下来，两头都落在同一张 {@code t_warehouse_stock_flow} 上，一次聚合即可。</p>
     *
     * <p>本模块自己的台账 {@code t_warehouse_third_phase_in} 只记入库、且是操作快照，
     * 不作合计口径 —— 用它算总入库会漏掉「入库后又被调整」的情形，也天然给不出总出库。</p>
     */
    @Override
    public ThirdPhaseSummaryVo querySummary() {
        ThirdPhaseSummaryVo vo = new ThirdPhaseSummaryVo();
        vo.setTotalIn(zeroIfNull(stockFlowMapper.sumThirdPhaseByInout(INOUT_IN)));
        vo.setTotalOut(zeroIfNull(stockFlowMapper.sumThirdPhaseByInout(INOUT_OUT)));
        return vo;
    }

    /** SQL 侧已 {@code COALESCE} 兜过 0，这里再兜一次防 mapper 换实现后前端拿到 null 直接崩。 */
    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // ================================================================ 新增入库

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(ThirdPhaseInBo bo) {
        if (bo == null) {
            throw new ServiceException("入库参数为空", 400);
        }
        BigDecimal stockNum = requireStockNum(bo.getStockNum());
        ProductInfo product = requireVegMaterialProduct(bo.getProductId());
        LocationInfo location = requireFreshVegLocation(bo.getLocationId());
        List<CropInfo> crops = requireCropsByProduct(product);

        Long userId = LoginHelper.getUserId();
        Date now = new Date();

        // 1. 库存：stock_flow + location_stock（口径照抄 VegetableHandleServiceImpl#insertPickStockIn）
        List<PlotInfo> plots = findPlantingPlots(crops);
        // 多块地时无法把这一笔货分摊到某一块（分摊比例是凭空发明的），退化成不带 plot 的产品级库存行 ——
        // 这正是 insertPickStockIn 在 plotId 为空时走的那条分支，不是新写法。
        Long flowPlotId = plots.size() == 1 ? plots.get(0).getId() : null;
        Long flowId = insertStockIn(product, location, stockNum, flowPlotId, crops, userId, now);

        // 2. 地块打【三期】标识
        markThirdPhase(plots, userId, now);

        // 3. 本模块操作记录（作物 / 产品 / 库位 / 地块全部快照）
        ThirdPhaseIn record = new ThirdPhaseIn();
        record.setInTime(now);
        record.setCropId(crops.get(0).getId());
        record.setCropName(crops.stream().map(CropInfo::getCropName).collect(Collectors.joining(",")));
        record.setProductId(product.getId());
        record.setProductName(product.getProductName());
        record.setStockNum(stockNum);
        record.setLocationId(location.getId());
        record.setLocationName(location.getLocationName());
        record.setPlotIds(plots.stream().map(p -> String.valueOf(p.getId())).collect(Collectors.joining(",")));
        record.setPlotNames(plots.stream().map(PlotInfo::getPlotName).collect(Collectors.joining(",")));
        record.setFlowId(flowId);
        record.setOperatorId(userId);
        record.setRemark(bo.getRemark());
        baseMapper.insert(record);

        log.info("[THIRD-PHASE-IN] product={} num={} location={} crops={} plots={} flowId={}",
            product.getId(), stockNum, location.getLocationCode(), record.getCropName(),
            record.getPlotNames(), flowId);
        return record.getId();
    }

    // ------------------------------------------------------------------ 守门

    /**
     * 入库量：必须 &gt; 0 且最多 3 位小数。
     *
     * <p>不做四舍五入 —— 静默把 {@code 1.2345} 改成 {@code 1.235} 会让称重记录与台账对不上，
     * 直接 400 让人重填。</p>
     */
    private BigDecimal requireStockNum(BigDecimal stockNum) {
        if (stockNum == null || stockNum.signum() <= 0) {
            throw new ServiceException("入库量必须大于 0", 400);
        }
        if (stockNum.stripTrailingZeros().scale() > STOCK_NUM_SCALE) {
            throw new ServiceException("入库量最多 3 位小数，当前值：" + stockNum.toPlainString(), 400);
        }
        return stockNum;
    }

    /**
     * 产品守门：必须是果蔬类原材料。
     *
     * <p>判定 = {@code product_attr=2}（原材料）+ {@code belong_type='vegetable'}（果蔬），
     * 与 {@code VegReceiveServiceImpl#resolveVegMaterialProductId} /
     * {@code ProductProductionServiceImpl#listSourceForVeg} 同一份规则，不复制第二套。</p>
     */
    private ProductInfo requireVegMaterialProduct(Long productId) {
        if (productId == null) {
            throw new ServiceException("请选择入库产品", 400);
        }
        ProductInfo product = productInfoMapper.selectById(productId);
        if (product == null) {
            throw new ServiceException("入库产品不存在或已删除：" + productId, 400);
        }
        boolean vegMaterial = product.getProductAttr() != null
            && product.getProductAttr() == PRODUCT_ATTR_MATERIAL
            && BELONG_TYPE_VEGETABLE.equals(product.getBelongType());
        if (!vegMaterial) {
            throw new ServiceException("产品「" + product.getProductName()
                + "」不是果蔬类原材料（需产品属性=原材料、归属类型=果蔬），不能走三期作物入库", 400);
        }
        return product;
    }

    /**
     * 库位守门：只能入毛菜保鲜室（毛菜鲜品库 {@code L0006}）。
     *
     * <p>按 {@code location_code} 查出真身再比 id，不在代码里写死库位主键。</p>
     */
    private LocationInfo requireFreshVegLocation(Long locationId) {
        LocationInfo freshVeg = locationInfoMapper.selectOne(
            new LambdaQueryWrapper<LocationInfo>()
                .eq(LocationInfo::getLocationCode, LOCATION_CODE_FRESH_VEG)
                .last("LIMIT 1"));
        if (freshVeg == null) {
            throw new ServiceException("毛菜保鲜室（库位编码 " + LOCATION_CODE_FRESH_VEG
                + "）不存在，请先在库位管理维护", 400);
        }
        if (locationId == null || !freshVeg.getId().equals(locationId)) {
            throw new ServiceException("三期作物入库只能入毛菜保鲜室（" + freshVeg.getLocationName()
                + "），不能入其他库位", 400);
        }
        return freshVeg;
    }

    /**
     * 产品 → 作物（反向解析）。
     *
     * <p>两个数据源，先后顺序有意为之：</p>
     * <ol>
     *   <li>{@code t_plant_crop_product}（V6 row16 起的作物-产品配置表）—— 现在的权威映射，
     *       一个作物可配多个产品，建表时已把存量 {@code related_product} 一对一迁进来</li>
     *   <li>回落 {@code t_plant_crop_info.related_product} —— 迁移之后新配的作物如果只改了老字段
     *       没进配置表，仍然认得出来</li>
     * </ol>
     *
     * <p>正常情况下命中一个作物。若同一个产品被配到多个作物上（数据允许，业务上罕见），
     * 全部返回：地块标识多块都打、作物名逗号拼接 —— 不在这里替甲方挑一个。</p>
     */
    private List<CropInfo> requireCropsByProduct(ProductInfo product) {
        Set<Long> cropIds = new LinkedHashSet<>();
        cropProductMapper.selectList(new LambdaQueryWrapper<CropProduct>()
                .eq(CropProduct::getProductId, product.getId()))
            .stream()
            .map(CropProduct::getCropId)
            .filter(Objects::nonNull)
            .forEach(cropIds::add);
        cropInfoMapper.selectList(new LambdaQueryWrapper<CropInfo>()
                .eq(CropInfo::getRelatedProduct, product.getId()))
            .stream()
            .map(CropInfo::getId)
            .forEach(cropIds::add);
        if (cropIds.isEmpty()) {
            throw new ServiceException("产品「" + product.getProductName()
                + "」没有关联任何作物，无法记录三期作物信息："
                + "请到 admin 作物管理 → 编辑作物 →「产品配置」页签把该产品加到对应作物下，再来入库", 400);
        }
        List<CropInfo> crops = cropInfoMapper.selectByIds(cropIds).stream()
            .sorted(Comparator.comparing(CropInfo::getId))
            .collect(Collectors.toList());
        if (crops.isEmpty()) {
            throw new ServiceException("产品「" + product.getProductName()
                + "」关联的作物已被删除，请到 admin 作物管理 →「产品配置」重新关联后再入库", 400);
        }
        return crops;
    }

    // -------------------------------------------------------------- 库存写入

    /**
     * 写入库台账：{@code stock_flow}（{@code third_phase_in} / IN）+ {@code location_stock} 余额，
     * 两侧都打 {@code third_phase=1}。
     *
     * <p>整段口径照抄 {@code VegetableHandleServiceImpl#insertPickStockIn}（采摘去向[毛菜保鲜室]入库）：
     * 有地块 → 按 plot 建篮；无地块 → 先按 (库位, 产品) 原子累加，累加不到再建产品级行。
     * 不另发明一套库存写法。</p>
     *
     * <p><b>无地块时的累加走三期专用方法</b>（{@link LocationStockMapper#addByProductLocationThirdPhase}）：
     * 三期货没有真实地块（甲方口径），{@code plot_id / ear_no / white_bar_no} 全 NULL，
     * 与「退货篮」/ 产品级普通篮是同一个形状。若沿用 {@code addByProductLocation}，
     * 这一笔会 UPDATE 到那条普通篮上 —— 普通货被染成三期、三期与普通货的量再也拆不开。
     * 故按 {@code third_phase} 分篮：三期入库只累加三期篮，累加不到就 INSERT 一条
     * {@code third_phase=1} 的新篮。</p>
     *
     * @return 新流水 id
     */
    private Long insertStockIn(ProductInfo product, LocationInfo location, BigDecimal weight,
                               Long plotId, List<CropInfo> crops, Long userId, Date now) {
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", INOUT_IN);

        StockFlow flow = new StockFlow();
        flow.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx));
        flow.setFlowDate(now);
        flow.setProductId(product.getId());
        flow.setWarehouseId(location.getId());
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(FLOW_TYPE_THIRD_PHASE_IN);
        flow.setChangeNum(weight);
        flow.setChangeQuantity(weight);
        flow.setPlotId(plotId);
        flow.setThirdPhase(THIRD_PHASE_YES);
        flow.setOperatorId(userId);
        flow.setRemark("三期作物入库 crop="
            + crops.stream().map(CropInfo::getCropName).collect(Collectors.joining(",")));
        stockFlowMapper.insert(flow);

        if (plotId != null) {
            LocationStock basket = new LocationStock();
            basket.setLocationId(location.getId());
            basket.setProductId(product.getId());
            basket.setPlotId(plotId);
            basket.setProductName(product.getProductName());
            basket.setProductUnit(product.getProductUnit());
            basket.setProductStock(weight);
            basket.setIsEnd(0);
            basket.setThirdPhase(THIRD_PHASE_YES);
            basket.setOperatorId(userId);
            locationStockMapper.insert(basket);
            return flow.getId();
        }
        int updated = locationStockMapper.addByProductLocationThirdPhase(
            location.getId(), product.getId(), weight, userId);
        if (updated == 0) {
            LocationStock fresh = new LocationStock();
            fresh.setLocationId(location.getId());
            fresh.setProductId(product.getId());
            fresh.setProductName(product.getProductName());
            fresh.setProductUnit(product.getProductUnit());
            fresh.setProductStock(weight);
            fresh.setIsEnd(0);
            fresh.setThirdPhase(THIRD_PHASE_YES);
            fresh.setOperatorId(userId);
            locationStockMapper.insert(fresh);
        }
        return flow.getId();
    }

    // ---------------------------------------------------------- 地块三期标识

    /**
     * 查这些作物「当前在种」的地块。
     *
     * <p>在种口径 = {@code t_plant_plant_details} 里 {@code plant_status='completed'}（苗已下地、
     * 进入产出期）且 {@code harvest_status<>'completed'}（还没采完）的明细，取 {@code DISTINCT plot_id}
     * —— 与 {@code PlantDashboardMapper#selectCropPlantStat}「实时种植物统计·在种地块数」
     * 完全同一份判定，不是本功能新造的。</p>
     *
     * <p>{@code harvest_status} DDL 是 {@code NOT NULL DEFAULT 'pending'}，
     * 所以 {@code <>} 不会因 NULL 三值逻辑漏行。</p>
     *
     * @return 按地块编码升序的在种地块（可能为空列表）
     */
    private List<PlotInfo> findPlantingPlots(List<CropInfo> crops) {
        List<Long> cropIds = crops.stream().map(CropInfo::getId).collect(Collectors.toList());
        List<PlantDetails> details = plantDetailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .in(PlantDetails::getCropId, cropIds)
                .eq(PlantDetails::getPlantStatus, PLANT_STATUS_COMPLETED)
                .ne(PlantDetails::getHarvestStatus, HARVEST_STATUS_COMPLETED));
        Set<Long> plotIds = details.stream()
            .map(PlantDetails::getPlotId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (plotIds.isEmpty()) {
            return new ArrayList<>();
        }
        return plotInfoMapper.selectByIds(plotIds).stream()
            .sorted(Comparator.comparing(PlotInfo::getPlotCode,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
    }

    /**
     * 给地块打【三期】标识（{@code third_phase=1}）。
     *
     * <p>纯 wrapper update（{@code entity=null}）+ 显式 set {@code update_by} / {@code update_time}：
     * entity 为 null 时 MP 的 {@code updateFill} 不会触发，审计列得自己写。写法与
     * {@link DjsBaseServiceImpl#softDelete} 一致 —— 用字符串列名而不是 lambda，
     * {@code LambdaUpdateWrapper.set} 会在构造期就去查 MP 的 lambda 列缓存，跨模块实体
     * （{@code PlotInfo} 属 plant 模块）在纯 mock 单测里缓存未初始化会直接抛。</p>
     *
     * <p>命中 0 块地不抛 —— 见类注释「命中 0 块地时不阻断入库」。</p>
     */
    private void markThirdPhase(List<PlotInfo> plots, Long userId, Date now) {
        if (plots.isEmpty()) {
            log.warn("[THIRD-PHASE-IN] 该产品对应作物当前没有在种地块（plant_status=completed"
                + " 且 harvest_status<>completed 的明细为 0），本次入库不打【三期】标识");
            return;
        }
        List<Long> plotIds = plots.stream().map(PlotInfo::getId).collect(Collectors.toList());
        UpdateWrapper<PlotInfo> wrapper = Wrappers.<PlotInfo>update()
            .in("id", plotIds)
            .eq("del_flag", "0")
            .set("third_phase", THIRD_PHASE_YES)
            .set("update_by", userId)
            .set("update_time", now);
        plotInfoMapper.update(null, wrapper);
    }
}
