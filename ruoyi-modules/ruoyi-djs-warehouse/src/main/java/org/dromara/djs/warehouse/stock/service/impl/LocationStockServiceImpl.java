package org.dromara.djs.warehouse.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.common.util.I18nMessages;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.djs.warehouse.common.QuantityUnitRule;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.domain.bo.LocationStockBo;
import org.dromara.djs.warehouse.stock.domain.bo.StockOutBo;
import org.dromara.djs.warehouse.stock.domain.bo.StockTransferBo;
import org.dromara.djs.warehouse.stock.domain.query.LocationStockQuery;
import org.dromara.djs.warehouse.stock.domain.vo.LocationStockVo;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.stock.service.ILocationStockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.dromara.djs.warehouse.stock.domain.PlotLabel;

/**
 * 库存明细 Service 实现（WMS-MD-001）。
 *
 * <p>查询时 service 层 JOIN {@code t_warehouse_location_info} 回填 {@code locationName}，
 * 避免 VO 走 ruoyi {@code @Translation} 注册位置类型（V1 简化路径）。</p>
 *
 * <p>{@code operatorId} 在 {@link #insertByBo(LocationStockBo)} 中走
 * {@link LoginHelper#getUserId()} 注入（ADR-0007 强制 — D6 #14 教训）。</p>
 *
 * @author djs
 * @since WMS-MD-001
 */
@Slf4j
@Service
public class LocationStockServiceImpl extends DjsBaseServiceImpl<LocationStockMapper, LocationStock> implements ILocationStockService {

    /**
     * 产品出库流水类型：{@code backstage_out}（后台手工出库）。
     *
     * <p>出库记录页 {@code flow/out} 直接按 {@code djs_flow_type} 字典渲染 flow_type，
     * 字典值 {@code backstage_out} = 「后台出库」（seed 102450019）。与商品详情业务流水
     * {@link ProductInfoMapper#selectFlowRecords} 把非 pick_out 的出库派生为 {@code backend_out}
     * 文案口径一致（均为「后台出库」语义）。</p>
     */
    private static final String FLOW_BACKSTAGE_OUT = "backstage_out";

    /**
     * 出库方向（DDL CHAR(3)）。
     */
    private static final String INOUT_OUT = "OT";

    /**
     * 入库方向（DDL CHAR(3)）。
     */
    private static final String INOUT_IN = "IN";

    /**
     * 转移出库流水类型：{@code transfer_out}（猪肉鲜品库转出侧，WS13 seed 102450027）。
     */
    private static final String FLOW_TRANSFER_OUT = "transfer_out";

    /**
     * 转移入库流水类型：{@code transfer_in}（冻品库转入侧，WS13 seed 102450028）。
     */
    private static final String FLOW_TRANSFER_IN = "transfer_in";

    /**
     * 猪肉转移源库位名称（唯一定位「猪肉鲜品库」；location_type=veg_fresh 被 4 个鲜品库共用，故按名精确判定）。
     */
    private static final String LOC_NAME_PORK_FRESH = "猪肉鲜品库";

    /**
     * 猪肉转移目标库位类型（{@code djs_location_type}=frozen，「冻品库」唯一）。
     */
    private static final String LOC_TYPE_FROZEN = "frozen";

    /**
     * 猪肉转移出库去向：{@code frozen_store}（{@code djs_stock_out_dest}=「冻品库」，
     * FIX-WMS-TRANSFER-DEST seed 1006411）。转移出库流水 stock_out_dest 记此值，
     * 出库记录页去向列据此渲染。
     */
    private static final String STOCK_OUT_DEST_FROZEN = "frozen_store";

    /**
     * 猪肉业态（{@code djs_belong_type}=pork），猪肉转移前置校验用。
     */
    private static final String BELONG_TYPE_PORK = "pork";

    private final LocationInfoMapper locationInfoMapper;
    private final PlotInfoMapper plotInfoMapper;
    private final ProductInfoMapper productInfoMapper;
    private final StockFlowMapper stockFlowMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final IStockCheckService stockCheckService;

    public LocationStockServiceImpl(LocationStockMapper baseMapper,
                                    LocationInfoMapper locationInfoMapper,
                                    PlotInfoMapper plotInfoMapper,
                                    ProductInfoMapper productInfoMapper,
                                    StockFlowMapper stockFlowMapper,
                                    IBizCodeGenerator bizCodeGenerator,
                                    IStockCheckService stockCheckService) {
        super(baseMapper);
        this.locationInfoMapper = locationInfoMapper;
        this.plotInfoMapper = plotInfoMapper;
        this.productInfoMapper = productInfoMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.stockCheckService = stockCheckService;
    }

    @Override
    public TableDataInfo<LocationStockVo> queryPageList(LocationStockQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<LocationStock> wrapper = buildQueryWrapper(query);
        Page<LocationStockVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillLocationNames(page.getRecords());
        fillPlotFields(page.getRecords());
        fillProductCodes(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<LocationStockVo> queryList(LocationStockQuery query) {
        List<LocationStockVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillLocationNames(list);
        fillPlotFields(list);
        fillProductCodes(list);
        return list;
    }

    @Override
    public LocationStockVo queryById(Long id) {
        LocationStockVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillLocationNames(List.of(vo));
            fillPlotFields(List.of(vo));
            fillProductCodes(List.of(vo));
        }
        return vo;
    }

    @Override
    public int insertByBo(LocationStockBo bo) {
        validateDimensionExclusive(bo);
        LocationStock entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("库存入参转换失败");
        }
        if (entity.getIsEnd() == null) {
            entity.setIsEnd(0);
        }
        // ADR-0007：最后操作人显式注入（D6 #14 BRD-EVENT-002 教训：依赖 createBy 不够，需独立 operatorId 字段）
        entity.setOperatorId(LoginHelper.getUserId());
        return baseMapper.insert(entity);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        return softDelete(ids);
    }

    @Override
    public void assertManualOutQuantity(StockOutBo bo) {
        if (bo == null || bo.getQuantity() == null || bo.getId() == null) {
            return;   // 空值交给 @Valid / productOut 自己报，这里只管单位口径
        }
        LocationStock stock = baseMapper.selectById(bo.getId());
        if (stock == null || stock.getProductId() == null) {
            return;   // 库存行不存在 → 让 productOut 抛它那句更准确的错，不在这里抢着报
        }
        ProductInfo product = productInfoMapper.selectById(stock.getProductId());
        String unit = product == null ? null : product.getProductUnit();
        if (QuantityUnitRule.isNonIntegerForCountingUnit(bo.getQuantity(), unit)) {
            throw new ServiceException(
                I18nMessages.t("stock.out.quantity.integer_only", StringUtils.blankToDefault(unit, "-")), 400);
        }
    }

    @Override
    public void assertManualTransferQuantity(StockTransferBo bo) {
        if (bo == null || bo.getQuantity() == null || bo.getId() == null) {
            return;
        }
        LocationStock stock = baseMapper.selectById(bo.getId());
        if (stock == null || stock.getProductId() == null) {
            return;   // 库存行不存在 → 让 pigTransfer 抛它那句更准确的错
        }
        ProductInfo product = productInfoMapper.selectById(stock.getProductId());
        String unit = product == null ? null : product.getProductUnit();
        if (QuantityUnitRule.isNonIntegerForCountingUnit(bo.getQuantity(), unit)) {
            throw new ServiceException(
                I18nMessages.t("stock.transfer.quantity.integer_only", StringUtils.blankToDefault(unit, "-")), 400);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long productOut(StockOutBo bo) {
        // 1. 取库存行，解析 locationId + productId（按行出库，避免前端透传可篡改的 location/product）
        LocationStock stock = baseMapper.selectById(bo.getId());
        if (stock == null) {
            throw new ServiceException("库存记录不存在或已删除：" + bo.getId());
        }
        Long productId = stock.getProductId();
        Long locationId = stock.getLocationId();
        if (productId == null || locationId == null) {
            throw new ServiceException("该库存行非产品库存（缺产品 / 库位），不支持产品出库");
        }
        ProductInfo product = productInfoMapper.selectById(productId);
        if (product == null) {
            throw new ServiceException("产品不存在或已删除：" + productId);
        }
        // 库位级业务锁（WMS-STOCK-001）：盘点进行中的库位禁出入库（后端双保险）
        stockCheckService.assertLocationUnlocked(locationId);
        // row186-BE：出库量不得超过当前库存（前端软拦 + 此处后端 fail-fast 硬拦，写流水前拦截防绕过/并发超扣；
        // 与 step3 的 deductStockById 行锁原子校验互为内外两道闸）。
        BigDecimal currentStock = stock.getProductStock() == null ? BigDecimal.ZERO : stock.getProductStock();
        if (bo.getQuantity().compareTo(currentStock) > 0) {
            throw new ServiceException(
                "出库量超过当前库存（product=" + product.getProductName()
                    + " / 当前库存=" + currentStock.stripTrailingZeros().toPlainString() + product.getProductUnit()
                    + " / 申请=" + bo.getQuantity().stripTrailingZeros().toPlainString() + product.getProductUnit() + "）");
        }
        Long userId = LoginHelper.getUserId();

        // 2. INSERT 出库流水（flow_type=backstage_out → 出库记录显示「后台出库」）
        StockFlow flow = new StockFlow();
        flow.setFlowNo(generateFlowNo(INOUT_OUT));
        // flow_date 记录实际操作时刻（含时分秒）；bo.outDate 仅是用户选的业务日期、为纯日期会落 00:00:00
        flow.setFlowDate(new Date());
        flow.setProductId(productId);
        flow.setWarehouseId(locationId);
        flow.setInoutType(INOUT_OUT);
        flow.setFlowType(FLOW_BACKSTAGE_OUT);
        flow.setStockOutDest(bo.getStockOutDest());
        flow.setChangeNum(bo.getQuantity().negate());
        flow.setChangeQuantity(bo.getQuantity());
        // 【三期】标识从被扣的那一行继承（V6 row92）。这里是「按行出库」的唯一收口点：
        // 库存查询页每行的「产品出库」按钮、毛菜间出库（VegOutServiceImpl 跨 bean 调本方法）
        // 都从这里出，标识在此一次性带上，调用方不需要各自记得回补 —— 靠调用方 patch 的写法
        // 已经漏过一次（只有毛菜间出库补了，库存查询页的产品出库没补，三期总出库直接少算）。
        flow.setThirdPhase(LocationStock.thirdPhaseOf(stock));
        // 出库操作人 + 备注：出库记录 / 毛菜间出库单列表的「出库操作人」列直接读 operator_id
        // （ADR-0007：不靠 create_by，那是框架审计字段）。remark 目前 admin 两个出库表单都没有输入框、
        // 恒传 undefined，落库为 null；这行是给直接调接口的调用方留的通路，也是回到本方法被误删前的原样。
        // ⚠️ 这两行与上面的 thirdPhase 一起被 LocationStockServiceImplTest 钉住，别再一起删掉。
        flow.setOperatorId(userId);
        flow.setRemark(bo.getRemark());
        stockFlowMapper.insert(flow);

        // 3. 按行 id 原子扣减（product_stock >= quantity 行锁 + 数量校验）——UI 按库存行出库，
        //    精确扣所选行/篮，避免同 (库位,产品) 多耳号/地块/白条篮串扣（与 pigTransfer 同范式）
        int affected = locationStockMapper().deductStockById(stock.getId(), bo.getQuantity(), userId);
        if (affected == 0) {
            // 抛异常 → @Transactional 回滚 step 2
            throw new ServiceException(
                "库存不足或已被并发占用，无法出库（product=" + product.getProductName()
                    + " / 当前库存=" + stock.getProductStock() + product.getProductUnit()
                    + " / 申请=" + bo.getQuantity() + product.getProductUnit() + "）");
        }
        return flow.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long pigTransfer(StockTransferBo bo) {
        // 1. 取源库存行（猪肉鲜品库某产品/耳号篮），解析 locationId + productId + 当前库存
        LocationStock stock = baseMapper.selectById(bo.getId());
        if (stock == null) {
            throw new ServiceException("库存记录不存在或已删除：" + bo.getId());
        }
        Long productId = stock.getProductId();
        Long srcLocationId = stock.getLocationId();
        if (productId == null || srcLocationId == null) {
            throw new ServiceException("该库存行非产品库存（缺产品 / 库位），不支持猪肉转移");
        }
        // 前置校验源库位必须为「猪肉鲜品库」（防前端绕过 —— 按钮只在该库位 + pork 行显示）
        LocationInfo srcLocation = locationInfoMapper.selectById(srcLocationId);
        if (srcLocation == null || !LOC_NAME_PORK_FRESH.equals(srcLocation.getLocationName())) {
            throw new ServiceException("猪肉转移仅支持从「" + LOC_NAME_PORK_FRESH + "」转出");
        }
        ProductInfo product = productInfoMapper.selectById(productId);
        if (product == null) {
            throw new ServiceException("产品不存在或已删除：" + productId);
        }
        // 前置校验产品业态必须为 pork（与按钮显示条件一致，双保险）
        if (!BELONG_TYPE_PORK.equals(product.getBelongType())) {
            throw new ServiceException("猪肉转移仅支持猪肉产品：" + product.getProductName());
        }
        // 源库位盘点锁定校验
        stockCheckService.assertLocationUnlocked(srcLocationId);

        // 2. 解析目标冻品库（location_type=frozen，唯一）
        LocationInfo frozen = locationInfoMapper.selectOne(
            new LambdaQueryWrapper<LocationInfo>()
                .eq(LocationInfo::getLocationType, LOC_TYPE_FROZEN)
                .orderByAsc(LocationInfo::getId)
                .last("LIMIT 1"));
        if (frozen == null) {
            throw new ServiceException("未配置冻品库（location_type=" + LOC_TYPE_FROZEN + "），无法转移");
        }
        Long frozenLocationId = frozen.getId();
        // 目标库位盘点锁定校验
        stockCheckService.assertLocationUnlocked(frozenLocationId);

        // 转移量不得超过源库存行当前库存（前端软拦 + 此处后端硬拦；与 step3 按行 id 行锁互为内外两道闸）
        BigDecimal currentStock = stock.getProductStock() == null ? BigDecimal.ZERO : stock.getProductStock();
        if (bo.getQuantity().compareTo(currentStock) > 0) {
            throw new ServiceException(
                "转移量超过当前库存（product=" + product.getProductName()
                    + " / 当前库存=" + currentStock.stripTrailingZeros().toPlainString() + product.getProductUnit()
                    + " / 申请=" + bo.getQuantity().stripTrailingZeros().toPlainString() + product.getProductUnit() + "）");
        }
        BigDecimal qty = bo.getQuantity();
        Long userId = LoginHelper.getUserId();
        // 【三期】标识从源库存行继承（V6 row92）：转移是「同一批货换个库位」，两侧流水与目标篮都得跟着它走，
        // 否则三期货一转移就掉标识、总出/总入两头对不上，目标库位还会把三期货并进普通篮混账。
        int srcThirdPhase = LocationStock.thirdPhaseOf(stock);

        // 3. 源侧（猪肉鲜品库）：按行 id 原子扣减（精确扣本篮，避免同产品多耳号篮串扣）
        int deducted = locationStockMapper().deductStockById(stock.getId(), qty, userId);
        if (deducted == 0) {
            throw new ServiceException(
                "库存不足或已被并发占用，无法转移（product=" + product.getProductName()
                    + " / 当前库存=" + currentStock.stripTrailingZeros().toPlainString() + product.getProductUnit()
                    + " / 申请=" + qty.stripTrailingZeros().toPlainString() + product.getProductUnit() + "）");
        }
        // INSERT 转移出库流水（flow_type=transfer_out，去向记冻品库）
        StockFlow outFlow = new StockFlow();
        outFlow.setFlowNo(generateFlowNo(INOUT_OUT));
        outFlow.setFlowDate(new Date());
        outFlow.setProductId(productId);
        outFlow.setWarehouseId(srcLocationId);
        outFlow.setInoutType(INOUT_OUT);
        outFlow.setFlowType(FLOW_TRANSFER_OUT);
        // 出库去向记冻品库（djs_stock_out_dest=frozen_store），出库记录页去向列据此渲染（与 remark 并存）
        outFlow.setStockOutDest(STOCK_OUT_DEST_FROZEN);
        outFlow.setChangeNum(qty.negate());
        outFlow.setChangeQuantity(qty);
        outFlow.setEarNo(stock.getEarNo());
        outFlow.setWhiteBarNo(stock.getWhiteBarNo());
        outFlow.setThirdPhase(srcThirdPhase);
        outFlow.setOperatorId(userId);
        outFlow.setRemark(buildTransferRemark("转移至 " + frozen.getLocationName(), bo.getRemark()));
        stockFlowMapper.insert(outFlow);

        // 4. 目标侧（冻品库）：同产品 UPSERT 加库存（product 维度；冻品库无耳号篮）
        //    三期货落三期篮、普通货落普通篮 —— 两个 UPSERT 各自只认自己那一半（见
        //    LocationStockMapper#addByProductLocationThirdPhase），混用会把转来的三期货并进普通篮。
        int added = srcThirdPhase == 1
            ? locationStockMapper().addByProductLocationThirdPhase(frozenLocationId, productId, qty, userId)
            : locationStockMapper().addByProductLocation(frozenLocationId, productId, qty, userId);
        if (added == 0) {
            LocationStock fresh = new LocationStock();
            fresh.setLocationId(frozenLocationId);
            fresh.setProductId(productId);
            fresh.setProductName(product.getProductName());
            fresh.setProductUnit(product.getProductUnit());
            fresh.setProductStock(qty);
            fresh.setIsEnd(0);
            fresh.setThirdPhase(srcThirdPhase);
            fresh.setOperatorId(userId);
            baseMapper.insert(fresh);
        }
        // INSERT 转移入库流水（flow_type=transfer_in，来源记猪肉鲜品库）
        StockFlow inFlow = new StockFlow();
        inFlow.setFlowNo(generateFlowNo(INOUT_IN));
        inFlow.setFlowDate(new Date());
        inFlow.setProductId(productId);
        inFlow.setWarehouseId(frozenLocationId);
        inFlow.setInoutType(INOUT_IN);
        inFlow.setFlowType(FLOW_TRANSFER_IN);
        inFlow.setChangeNum(qty);
        inFlow.setChangeQuantity(qty);
        inFlow.setEarNo(stock.getEarNo());
        inFlow.setWhiteBarNo(stock.getWhiteBarNo());
        inFlow.setThirdPhase(srcThirdPhase);
        inFlow.setOperatorId(userId);
        inFlow.setRemark(buildTransferRemark("转移自 " + srcLocation.getLocationName(), bo.getRemark()));
        stockFlowMapper.insert(inFlow);

        return outFlow.getId();
    }

    /**
     * 拼转移流水备注：方向前缀（如「转移至 冻品库」）+ 用户备注（有则追加）。
     */
    private String buildTransferRemark(String directionPrefix, String userRemark) {
        if (StringUtils.isBlank(userRemark)) {
            return directionPrefix;
        }
        return directionPrefix + "；" + userRemark;
    }

    @Override
    public List<String> listStockEarNos(Long locationId) {
        List<LocationStock> rows = baseMapper.selectList(
            new LambdaQueryWrapper<LocationStock>()
                .select(LocationStock::getEarNo)
                .eq(locationId != null, LocationStock::getLocationId, locationId)
                .isNotNull(LocationStock::getEarNo)
                .ne(LocationStock::getEarNo, ""));
        return rows.stream()
            .map(LocationStock::getEarNo)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .sorted()
            .toList();
    }

    /**
     * 取库存 Mapper（基类 {@code baseMapper} 即 {@link LocationStockMapper}，封装一层便于读）。
     */
    private LocationStockMapper locationStockMapper() {
        return baseMapper;
    }

    /**
     * 生成流水号（复用 SYS-INFRA-004 BizCodeService，{@code F+yyyyMMdd+ioCode2+seq4}）。
     */
    private String generateFlowNo(String ioCode) {
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", ioCode);
        return bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx);
    }

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus。
     *
     * <p>protected 方便 Mockito 单测覆盖（避免启 Spring 上下文）。</p>
     */
    protected LocationStock toEntity(LocationStockBo bo) {
        return MapstructUtils.convert(bo, LocationStock.class);
    }

    /**
     * 校验 productId / earNo / plotId / medicineId 四选一（非空字段恰好一个）。
     */
    private void validateDimensionExclusive(LocationStockBo bo) {
        int filled = 0;
        if (bo.getProductId() != null) filled++;
        if (StringUtils.isNotBlank(bo.getEarNo())) filled++;
        if (bo.getPlotId() != null) filled++;
        if (bo.getMedicineId() != null) filled++;
        if (filled != 1) {
            throw new ServiceException("stock.dimension.exclusive");
        }
    }

    /**
     * 批量回填 {@code locationName}（避免 N+1，单次 IN 查 location 表）。
     */
    private void fillLocationNames(List<LocationStockVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> locationIds = records.stream()
            .map(LocationStockVo::getLocationId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (locationIds.isEmpty()) {
            return;
        }
        List<LocationInfo> locations = locationInfoMapper.selectList(
            new LambdaQueryWrapper<LocationInfo>().in(LocationInfo::getId, locationIds));
        Map<Long, String> nameMap = locations.stream()
            .collect(Collectors.toMap(LocationInfo::getId, LocationInfo::getLocationName, (a, b) -> a));
        for (LocationStockVo vo : records) {
            if (vo.getLocationId() != null) {
                vo.setLocationName(nameMap.get(vo.getLocationId()));
            }
        }
    }

    /**
     * 批量回填 {@code blockNo}（地块编号 = {@code t_plant_plot_info.plot_code}）
     * 与 {@code plotName}（地块名 = {@code plot_name}）。
     *
     * <p>库存表只存 {@code plotId}，两者都在地块主数据表。单次 IN 查地块表回填，避免 N+1。
     * 库存行 {@code plotId} 为空（按产品 / 耳号入库的行、以及三期货）→ 两者都保持 null。</p>
     *
     * <p>「地块」列由前端按 {@code thirdPhase} → {@code plotName} → {@code -} 的顺序渲染，
     * 后端只负责把两个原料都给全，不在这里拼展示串。</p>
     */
    private void fillPlotFields(List<LocationStockVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> plotIds = records.stream()
            .map(LocationStockVo::getPlotId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        // ⚠️ 这里**不能**在 plotIds 为空时早退：三期篮 plot_id 恒为 NULL，
        // 一批全是三期行时早退会让下面的 plotLabel 一个都填不上 → 导出「地块」列整列空白。
        Map<Long, PlotInfo> plotMap = plotIds.isEmpty()
            ? Map.of()
            : plotInfoMapper.selectList(new LambdaQueryWrapper<PlotInfo>().in(PlotInfo::getId, plotIds))
                .stream()
                .collect(Collectors.toMap(PlotInfo::getId, p -> p, (a, b) -> a));
        for (LocationStockVo vo : records) {
            if (vo.getPlotId() != null) {
                PlotInfo plot = plotMap.get(vo.getPlotId());
                if (plot != null) {
                    vo.setBlockNo(plot.getPlotCode());
                    vo.setPlotName(plot.getPlotName());
                }
            }
            // 导出件的「地块」列（V6 row92）：与页面 formatPlotLabel 同一套规则。
            // 必须在 plotName 落完之后算 —— 三期行直接返「三期」，不看 plotName。
            vo.setPlotLabel(PlotLabel.of(vo.getThirdPhase(), vo.getPlotName()));
        }
    }

    /**
     * 批量回填 {@code productCode} + {@code belongType} + {@code productAttr} + {@code productSpec}
     * （均取自产品主数据 {@code t_warehouse_product_info}）。
     *
     * <p>库存表只存 {@code product_id} FK（Long），产品代码/业态/属性/规格在产品主数据表。
     * 单次 IN 查产品表回填，避免 N+1。库存行 {@code productId} 为空（按耳号 / 地块入库的行）→ 四者保持 null。
     * {@code belongType + productAttr} 供库存详情「饲料饲喂记录」tab 判定（果蔬原材料才显示）；
     * {@code productSpec} 供库存查询列表产品名称右侧的规格列（row183）。</p>
     */
    private void fillProductCodes(List<LocationStockVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> productIds = records.stream()
            .map(LocationStockVo::getProductId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (productIds.isEmpty()) {
            return;
        }
        Map<Long, ProductInfo> productMap = productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>().in(ProductInfo::getId, productIds))
            .stream()
            .collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));
        for (LocationStockVo vo : records) {
            if (vo.getProductId() != null) {
                ProductInfo product = productMap.get(vo.getProductId());
                if (product != null) {
                    vo.setProductCode(product.getProductId());
                    vo.setBelongType(product.getBelongType());
                    vo.setProductAttr(product.getProductAttr());
                    vo.setProductSpec(product.getProductSpec());
                }
            }
        }
    }

    /**
     * 按地块编号（模糊）解析匹配的 plotId 集合；无匹配返空 list（调用方据此让查询恒空）。
     */
    private List<Long> resolvePlotIdsByBlockNo(String blockNo) {
        List<PlotInfo> plots = plotInfoMapper.selectList(
            new LambdaQueryWrapper<PlotInfo>()
                .like(PlotInfo::getPlotCode, blockNo)
                .select(PlotInfo::getId));
        return plots.stream().map(PlotInfo::getId).toList();
    }

    /**
     * 按归属类型（{@code djs_belong_type} 字典值，精确）解析匹配的 productId 集合；无匹配返空 list
     * （调用方据此让查询恒空）。
     *
     * <p>{@code belong_type} 不在 {@code t_warehouse_location_stock} 表，仅存于产品主数据
     * {@code t_warehouse_product_info}，故先按 belong_type 查出 productId 再 IN 过滤库存
     * （参 {@link #fillProductCodes} 的 productInfoMapper 用法）。</p>
     */
    private List<Long> resolveProductIdsByBelongType(String belongType) {
        List<ProductInfo> products = productInfoMapper.selectList(
            new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getBelongType, belongType)
                .select(ProductInfo::getId));
        return products.stream().map(ProductInfo::getId).toList();
    }

    /**
     * 按归属类型多选（{@code djs_belong_type} 字典值集合，IN）解析匹配的 productId 集合；无匹配返空 list
     * （R70 产品品类多选，调用方据此让查询恒空）。
     */
    private List<Long> resolveProductIdsByBelongTypes(List<String> belongTypes) {
        List<ProductInfo> products = productInfoMapper.selectList(
            new LambdaQueryWrapper<ProductInfo>()
                .in(ProductInfo::getBelongType, belongTypes)
                .select(ProductInfo::getId));
        return products.stream().map(ProductInfo::getId).toList();
    }

    /**
     * 构造查询条件。
     */
    private LambdaQueryWrapper<LocationStock> buildQueryWrapper(LocationStockQuery query) {
        LambdaQueryWrapper<LocationStock> wrapper = new LambdaQueryWrapper<>();
        // admin rows84/110：正库存始终可见；零库存只在上海自然日内该“同一库存篮”
        // 有真实出入库流水时可见。不能用 update_time 代替流水，否则盘点/编辑会把历史零库存
        // 永久重新暴露。<=> 逐维 null-safe 关联，避免同产品不同耳号/白条/地块串篮。
        wrapper.apply("""
            (t_warehouse_location_stock.product_stock > 0 OR EXISTS (
                SELECT 1
                  FROM t_warehouse_stock_flow f
                 WHERE f.del_flag = '0'
                   AND f.tenant_id = t_warehouse_location_stock.tenant_id
                   AND f.warehouse_id <=> t_warehouse_location_stock.location_id
                   AND f.product_id <=> COALESCE(
                       t_warehouse_location_stock.product_id,
                       t_warehouse_location_stock.medicine_id)
                   AND f.ear_no <=> t_warehouse_location_stock.ear_no
                   AND f.white_bar_no <=> t_warehouse_location_stock.white_bar_no
                   AND f.plot_id <=> t_warehouse_location_stock.plot_id
                   AND DATE(f.flow_date) =
                       DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+08:00'))
            ))""");
        if (query == null) {
            return wrapper.orderByDesc(LocationStock::getId);
        }
        // 库位多选（R70）：locationIds 非空 → IN；否则 fallback 单值 locationId .eq（mp / 其他单值调用方）
        boolean hasLocationIds = query.getLocationIds() != null && !query.getLocationIds().isEmpty();
        wrapper.in(hasLocationIds, LocationStock::getLocationId, query.getLocationIds())
            .eq(!hasLocationIds && query.getLocationId() != null, LocationStock::getLocationId, query.getLocationId())
            .eq(query.getProductId() != null, LocationStock::getProductId, query.getProductId())
            .like(StringUtils.isNotBlank(query.getProductName()), LocationStock::getProductName, query.getProductName())
            .like(StringUtils.isNotBlank(query.getEarNo()), LocationStock::getEarNo, query.getEarNo())
            .eq(query.getPlotId() != null, LocationStock::getPlotId, query.getPlotId())
            .eq(query.getMedicineId() != null, LocationStock::getMedicineId, query.getMedicineId())
            .eq(query.getIsEnd() != null, LocationStock::getIsEnd, query.getIsEnd())
            // 三期过滤（V6 row92）：传 1 = 只看三期库存；不传 = 全部
            .eq(query.getThirdPhase() != null, LocationStock::getThirdPhase, query.getThirdPhase())
            .orderByDesc(LocationStock::getId);
        // 地块编号过滤：先解析匹配的 plotId 集合再 IN 过滤；无匹配则用不存在的 id 让结果恒空
        if (StringUtils.isNotBlank(query.getBlockNo())) {
            List<Long> plotIds = resolvePlotIdsByBlockNo(query.getBlockNo());
            if (plotIds.isEmpty()) {
                wrapper.eq(LocationStock::getId, -1L);
            } else {
                wrapper.in(LocationStock::getPlotId, plotIds);
            }
        }
        // 归属类型过滤：belong_type 不在库存表，先按产品主数据 belong_type 解析 productId 集合再 IN 过滤；无匹配则恒空。
        // R70 多选：belongTypes 非空 → 按集合 IN 反查；否则 fallback 单值 belongType（mp / 其他单值调用方）。
        boolean hasBelongTypes = query.getBelongTypes() != null && !query.getBelongTypes().isEmpty();
        if (hasBelongTypes) {
            List<Long> productIds = resolveProductIdsByBelongTypes(query.getBelongTypes());
            if (productIds.isEmpty()) {
                wrapper.eq(LocationStock::getId, -1L);
            } else {
                wrapper.in(LocationStock::getProductId, productIds);
            }
        } else if (StringUtils.isNotBlank(query.getBelongType())) {
            List<Long> productIds = resolveProductIdsByBelongType(query.getBelongType());
            if (productIds.isEmpty()) {
                wrapper.eq(LocationStock::getId, -1L);
            } else {
                wrapper.in(LocationStock::getProductId, productIds);
            }
        }
        return wrapper;
    }

}
