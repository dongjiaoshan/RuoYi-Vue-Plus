package org.dromara.djs.warehouse.vegout.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.common.core.service.UserService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.domain.bo.StockOutBo;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.stock.service.ILocationStockService;
import org.dromara.djs.warehouse.veg.domain.FeedLog;
import org.dromara.djs.warehouse.veg.domain.VegetableHandle;
import org.dromara.djs.warehouse.veg.domain.HandleRecord;
import org.dromara.djs.warehouse.veg.mapper.FeedLogMapper;
import org.dromara.djs.warehouse.veg.mapper.HandleRecordMapper;
import org.dromara.djs.warehouse.veg.mapper.VegetableHandleMapper;
import org.dromara.djs.warehouse.vegout.domain.bo.VegOutItemBo;
import org.dromara.djs.warehouse.vegout.domain.bo.VegOutSubmitBo;
import org.dromara.djs.warehouse.vegout.domain.query.VegOutQuery;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutBatchVo;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutCandidateVo;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutDetailVo;
import org.dromara.djs.warehouse.vegout.mapper.VegOutMapper;
import org.dromara.djs.warehouse.vegout.service.IVegOutService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 毛菜间出库 Service 实现（admin row185 + row187）。
 *
 * <p><b>货从哪来</b>：毛菜处理间「入库」去向把毛菜写进<b>毛菜鲜品库 L0006</b>
 * （{@code VegetableHandleServiceImpl.LOCATION_CODE_FRESH_VEG} 硬编码，不由工人选），
 * 本 service 出的就是这批货。</p>
 *
 * <p><b>去哪</b>（甲方 row185 col8 口径）：出库到果蔬月台 = 从毛菜间运蔬到果蔬月台，
 * 对毛菜鲜品库是一次出库；出库后货显示在 mp「果蔬月台」功能里，工人在月台收货后再进蔬菜保鲜库。
 * 月台待入库量口径 = {@code Σ vegetable_handle.send_platform_weight − 已收货 − 已完成损耗}
 * （见 {@code VegReceiveMapper.selectSelfPending}），故这里只需往对应 handle 行累加 send_platform_weight。</p>
 *
 * @author djs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VegOutServiceImpl implements IVegOutService {

    /** 毛菜鲜品库库位编码（与 VegetableHandleServiceImpl 同一常量，毛菜处理入库的固定落点）。 */
    private static final String LOCATION_CODE_FRESH_VEG = "L0006";

    /** 干货库库位编码（admin row194 第 4 点起也可从这里出库）。 */
    private static final String LOCATION_CODE_DRY_GOODS = "L0005";

    /** 蛋类库库位编码（同上）。 */
    private static final String LOCATION_CODE_EGG = "L0009";

    /**
     * 可出库的库位白名单。
     *
     * <p>⚠️ 按 {@code location_code} 而不是 {@code location_type} —— 线上 location_type 不可靠
     * （猪肉鲜品库/蛋类库被错归成 veg_fresh、干货库是 warehouse），mp matPack 早就因此改走库名匹配。</p>
     */
    private static final java.util.List<String> ALLOWED_LOCATION_CODES =
        java.util.List.of(LOCATION_CODE_FRESH_VEG, LOCATION_CODE_DRY_GOODS, LOCATION_CODE_EGG);

    /** 可出库的产品业态白名单（干货库里实测还有 other 业态的桶/罐/袋装品）。 */
    private static final java.util.List<String> ALLOWED_BELONG_TYPES =
        java.util.List.of("vegetable", "dry_good", "egg", "other");

    /** 果蔬业态（只有果蔬产品能走毛菜间出库）。 */
    private static final String BELONG_TYPE_VEGETABLE = "vegetable";

    /** 出库去向：果蔬月台（本次迁移新增字典值）。 */
    private static final String DEST_VEG_DOCK = "veg_dock";

    /** 出库去向：猪只饲料（前端「饲料饲喂」，字典既有值）。 */
    private static final String DEST_FEED = "feed";

    /** 饲喂来源：毛菜间（字典 djs_feed_type，有机饲喂记录里的「位置」）。 */
    private static final String FEED_TYPE_VEG_HANDLE = "veg_handle";

    /** 处理明细类型：处理录入（{@code t_warehouse_handle_record.record_type}）。 */
    private static final int RECORD_TYPE_HANDLE = 2;

    /** 处理去向：月台（{@code handle_target}；日统计「发往月台果蔬总重」按此筛）。 */
    private static final int HANDLE_TARGET_PLATFORM = 2;

    private final VegOutMapper vegOutMapper;
    private final LocationStockMapper locationStockMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final ProductInfoMapper productInfoMapper;
    private final StockFlowMapper stockFlowMapper;
    private final CropInfoMapper cropInfoMapper;
    private final VegetableHandleMapper vegetableHandleMapper;
    private final FeedLogMapper feedLogMapper;
    private final HandleRecordMapper handleRecordMapper;
    private final ILocationStockService locationStockService;
    private final org.dromara.djs.common.encoder.IBizCodeGenerator bizCodeGenerator;
    private final UserService userService;

    /**
     * 一张出库单最多几个产品 —— 与 admin 打印模板 {@code printSheet.ts} 的 `ROWS_PER_PAGE` 同一个数。
     * 改这里必须同步改那边，否则要么单据分页、要么表格印不下。
     */
    private static final int MAX_ITEMS_PER_SHEET = 10;

    @Override
    public List<VegOutCandidateVo> listCandidates(String productName) {
        return vegOutMapper.selectCandidates(ALLOWED_LOCATION_CODES, ALLOWED_BELONG_TYPES, productName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String submit(VegOutSubmitBo bo, boolean asBatch) {
        // 一单最多 10 个产品（甲方 V6 row26）：241×140mm 的三联单一页只印得下 10 行，超了就得打两张纸。
        // 前端抽屉已经把输入框禁掉了，这里是第二道门 —— 直接打接口 / 前端被绕过时不能照单全收。
        if (bo.getItems() != null && bo.getItems().size() > MAX_ITEMS_PER_SHEET) {
            throw new ServiceException("一张出库单最多 " + MAX_ITEMS_PER_SHEET + " 个产品，当前 "
                + bo.getItems().size() + " 个", 400);
        }
        // 三个可出库库位的 id（按 location_code 解析；缺哪个就少哪个，不阻断整单）
        java.util.Set<Long> allowedLocationIds = resolveAllowedLocationIds();
        String batchNo = asBatch ? generateBatchNo() : null;
        Long userId = LoginHelper.getUserId();

        for (VegOutItemBo item : bo.getItems()) {
            LocationStock stock = locationStockMapper.selectById(item.getStockId());
            if (stock == null) {
                throw new ServiceException("库存记录不存在或已删除：" + item.getStockId());
            }
            // 前置校验（防前端绕过 —— 入口按钮只在毛菜鲜品库的果蔬行显示）
            if (!allowedLocationIds.contains(stock.getLocationId())) {
                throw new ServiceException("只有毛菜鲜品库 / 干货库 / 蛋类库的库存可做毛菜间出库");
            }
            ProductInfo product = productInfoMapper.selectById(stock.getProductId());
            if (product == null) {
                throw new ServiceException("产品不存在或已删除：" + stock.getProductId());
            }
            if (!ALLOWED_BELONG_TYPES.contains(product.getBelongType())) {
                throw new ServiceException("该产品业态不支持毛菜间出库：" + product.getProductName());
            }
            // ⚠️ 非果蔬不能送「果蔬月台」：月台的待入库量只来自 vegetable_handle.send_platform_weight，
            // 而那张表是果蔬（毛菜处理）专属、干货/蛋类根本没有对应行。放行的话库存扣了、流水写了，
            // 月台侧却永远收不到这批货 = 凭空蒸发。故在此 fail-fast，不做「跳过累加」的静默放行。
            // （「猪只饲料」去向不受此限：饲喂台账 t_warehouse_feed_log 三类都能记，见下方分流。）
            if (DEST_VEG_DOCK.equals(bo.getOutDest())
                && !BELONG_TYPE_VEGETABLE.equals(product.getBelongType())) {
                throw new ServiceException("只有果蔬产品可以出库到果蔬月台：" + product.getProductName()
                    + "（干货 / 蛋类请选其他出库去向）");
            }

            // 扣库存 + 写 backstage_out 出库流水，全部复用产品出库既有口径
            // （含库位盘点锁校验、超扣 fail-fast、按行原子扣减）。跨 bean 调用，事务并入本方法。
            StockOutBo outBo = new StockOutBo();
            outBo.setId(stock.getId());
            outBo.setOutDate(bo.getOutDate());
            outBo.setQuantity(item.getQuantity());
            outBo.setStockOutDest(bo.getOutDest());
            outBo.setRemark(bo.getRemark());
            Long flowId = locationStockService.productOut(outBo);

            // 回写流水的地块、批次与出库日期：
            //   plot_id 来自库存行（月台/饲喂台账按地块×作物定位）；
            //   batch_no 让同一次提交的多条聚合成 row187 列表里的「一单」；
            //   flow_date 改记业务日期 —— productOut 默认写 new Date()（实际操作时刻），
            //   但甲方 row187 明确「可以选择当天和历史的日期」，补录历史日期时列表必须显示所选那天。
            //   沿用项目补录约定：选当天则保留真实时分秒，选历史日期则落该日 00:00:00。
            // ⚠️ third_phase 不在这里补 —— 已由 productOut 从被扣的库存行统一继承（V6 row92 唯一收口点）。
            StockFlow patch = new StockFlow();
            patch.setId(flowId);
            patch.setPlotId(stock.getPlotId());
            patch.setBatchNo(batchNo);
            patch.setFlowDate(resolveFlowDate(bo.getOutDate()));
            // row194：销售单价快照。前端默认带出产品 sale_price 但允许改，故必须按本次录入值落在流水行上，
            // 不能事后回读产品主数据 —— 否则改一次产品价格，历史出库单金额会整体漂移。
            patch.setOutUnitPrice(item.getOutUnitPrice() != null ? item.getOutUnitPrice() : product.getSalePrice());
            stockFlowMapper.updateById(patch);

            // 去向额外下游：果蔬月台 / 饲料饲喂 与毛菜处理间同口径
            // 去向额外下游（Kevin 2026-08-03 拍板 D3）：
            //   · 果蔬月台：走到这里必然是果蔬（上面已 fail-fast 拦掉非果蔬），照常累加毛菜处理送月台重量；
            //   · 猪只饲料：**三类业态都写**有机饲喂台账——干货/蛋类也可能真拿去喂猪，这笔账要记；
            //     但「毛菜处理的 feed_weight 累加」仍只对果蔬做（那是果蔬专属报表，混入会污染）。
            boolean isVegetable = BELONG_TYPE_VEGETABLE.equals(product.getBelongType());
            if (DEST_VEG_DOCK.equals(bo.getOutDest())) {
                Long handleId = accumulateHandleWeight(product, stock, item.getQuantity(), true);
                insertPlatformHandleRecord(handleId, stock, resolveCropIdByProduct(product.getId()),
                    item.getQuantity(), userId, resolveFlowDate(bo.getOutDate()));
            } else if (DEST_FEED.equals(bo.getOutDest())) {
                if (isVegetable) {
                    accumulateHandleWeight(product, stock, item.getQuantity(), false);
                }
                insertFeedLog(product, stock, item.getQuantity(), stock.getLocationId(), userId, bo.getOutDate());
            }
        }
        log.info("[VEG-OUT] dest={} items={} batchNo={}", bo.getOutDest(), bo.getItems().size(), batchNo);
        return batchNo;
    }

    @Override
    public TableDataInfo<VegOutBatchVo> queryBatchPage(VegOutQuery query, PageQuery pageQuery) {
        VegOutQuery q = query != null ? query : new VegOutQuery();
        IPage<VegOutBatchVo> page = vegOutMapper.selectBatchPage(
            pageQuery.build(), q.getBeginDate(), q.getEndDate(), q.getOutDest(), q.getOperatorId());
        fillOperatorNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<VegOutBatchVo> queryBatchList(VegOutQuery query) {
        VegOutQuery q = query != null ? query : new VegOutQuery();
        List<VegOutBatchVo> rows = vegOutMapper.selectBatchList(
            q.getBeginDate(), q.getEndDate(), q.getOutDest(), q.getOperatorId());
        fillOperatorNames(rows);
        return rows;
    }

    @Override
    public List<VegOutDetailVo> queryBatchDetail(String batchNo, String productName) {
        if (StringUtils.isBlank(batchNo)) {
            return List.of();
        }
        return vegOutMapper.selectBatchDetail(batchNo, productName);
    }

    @Override
    public List<VegOutDetailVo> queryBatchDetailForExport(String batchNo, String productName) {
        List<VegOutDetailVo> rows = queryBatchDetail(batchNo, productName);
        rows.forEach(r -> r.setOutQtyLabel(formatQtyLabel(r.getOutWeight(), r.getProductUnit())));
        return rows;
    }

    /**
     * 「出库量」带单位展示串 —— 与详情弹框 {@code fmtQty} 逐字对齐（甲方拿导出跟弹框对账）。
     *
     * <p>kg 或单位缺失 → 3 位小数 + {@code kg}（如 {@code 12.000kg}）；
     * 其余计件单位 → 去尾零的数值 + 空格 + 单位（如 {@code 3 袋}）。</p>
     */
    private static String formatQtyLabel(BigDecimal qty, String unit) {
        if (qty == null) {
            return "";
        }
        String u = unit != null ? unit.trim() : "";
        if (u.isEmpty() || "kg".equalsIgnoreCase(u)) {
            return qty.setScale(3, java.math.RoundingMode.HALF_UP).toPlainString() + "kg";
        }
        return qty.stripTrailingZeros().toPlainString() + " " + u;
    }

    /**
     * 往对应的毛菜处理汇总行累加「发往月台」或「饲料饲喂」重量。
     *
     * <p>定位链：库存行 product_id → 作物（{@code t_plant_crop_info.related_product} 反查，1:1）
     * + 库存行 plot_id → {@code t_warehouse_vegetable_handle}（该组合零重复）。</p>
     *
     * <p><b>为什么定位不到时要按需补建而不是直接跳过或直接拦死</b>：</p>
     * <ul>
     *   <li>跳过不行 —— {@code send_platform_weight} 是这批货到了月台的<b>唯一记录</b>
     *       （mp 月台待入库量 = Σ该字段 − 已收货 − 损耗）。累加不上就是库存被扣、流水也写了，
     *       但货在月台永远不出现、也永远收不了 —— 等于凭空蒸发。</li>
     *   <li>一律拦死也不行 —— L0006 的库存<b>有两条合法来源</b>：①毛菜处理间「入库」去向
     *       （会建 handle 行）；②mp 采摘录入把采摘去向选成「毛菜保鲜室」，走
     *       {@code VegetableHandleServiceImpl.insertPickStockIn}，该路径<b>只写库存不建 handle 行</b>
     *       （与同处的 {@code insertPickPlatform} 不对称）。②进来的货是完全合法的库存，
     *       拦死等于这批货永远出不去，而「毛菜间处理记录」页是只读的（只有 list/export 权限），
     *       用户根本没有补录入口。</li>
     * </ul>
     *
     * <p>故：能确定(作物, 地块)就<b>按需补建一条最小 handle 行</b>再累加（picked/handled/stockIn 记 0，
     * 表示这批货不是经毛菜处理流程进来的）；只有连归属都定不了（无地块 或 产品反查不到作物）才拦。</p>
     *
     * @param toPlatform true=发往月台（缺归属时拦） / false=饲料饲喂（缺归属时降级，feed_log 仍照写）
     */
    private Long accumulateHandleWeight(ProductInfo product, LocationStock stock, BigDecimal weight, boolean toPlatform) {
        Long cropId = resolveCropIdByProduct(product.getId());
        VegetableHandle handle = null;
        if (cropId != null && stock.getPlotId() != null) {
            handle = vegetableHandleMapper.selectOne(new LambdaQueryWrapper<VegetableHandle>()
                .eq(VegetableHandle::getCropId, cropId)
                .eq(VegetableHandle::getPlotId, stock.getPlotId())
                .orderByDesc(VegetableHandle::getId)
                .last("LIMIT 1"));
            if (handle == null) {
                handle = createMinimalHandle(cropId, stock.getPlotId(), product.getId());
            }
        }
        if (handle == null) {
            // 连(作物, 地块)都定不了：月台侧无处归集，拦；饲料侧 feed_log 是主记录，降级放行
            if (toPlatform) {
                throw new ServiceException("「" + product.getProductName() + "」"
                    + (stock.getPlotId() == null ? "该库存行未关联地块" : "未配置对应作物（作物管理的关联产品）")
                    + "，无法归集到果蔬月台。请先补齐后再操作。");
            }
            log.warn("[VEG-OUT] 定位不到(作物,地块) productId={} plotId={}，饲料饲喂只写有机饲喂记录、跳过汇总累加",
                product.getId(), stock.getPlotId());
            return null;
        }
        VegetableHandle delta = new VegetableHandle();
        delta.setId(handle.getId());
        if (toPlatform) {
            delta.setSendPlatformWeight(nullSafe(handle.getSendPlatformWeight()).add(weight));
        } else {
            delta.setFeedWeight(nullSafe(handle.getFeedWeight()).add(weight));
        }
        vegetableHandleMapper.updateById(delta);
        return handle.getId();
    }

    /**
     * 写一条毛菜处理明细（{@code handle_target=2} 月台）。
     *
     * <p><b>为什么必须写</b>：日统计「发往月台果蔬总重」（{@code WarehouseStatAggregateMapper.sumSendPlatformWeight}）
     * 读的是 {@code t_warehouse_handle_record} 而非 {@code vegetable_handle.send_platform_weight}
     * （历史上按后者算会错记归属日）。兄弟路径 {@code insertPickPlatform} 也是两张表都写，
     * 这里不写会让本功能出的货在该日统计上silently 少算。</p>
     */
    private void insertPlatformHandleRecord(Long handleId, LocationStock stock, Long cropId,
                                            BigDecimal weight, Long userId, Date outDate) {
        HandleRecord record = new HandleRecord();
        record.setHandleId(handleId);
        record.setPlotId(stock.getPlotId());
        record.setCropId(cropId);
        // row55：果蔬月台待入库量改为按产品聚合、数据源就是这张明细表。不落产品的话，
        // 从毛菜间出库到月台的量会全部挂到作物默认产品名下（红薯杆的货显在红薯卡里）。
        // 出的是哪个库存篮就是哪个产品，stock 上现成有。
        record.setProductId(stock.getProductId());
        record.setRecordType(RECORD_TYPE_HANDLE);
        record.setRecordWeight(weight);
        record.setHandleTarget(HANDLE_TARGET_PLATFORM);
        record.setIsFinish(2);
        record.setHandleUser(userId);
        record.setHandleTime(outDate);
        record.setRemark("毛菜间出库");
        handleRecordMapper.insert(record);
    }

    /**
     * 按需补建一条最小毛菜处理汇总行（用于 mp 采摘直送毛菜保鲜室进来的库存 —— 那条路径只写库存不建 handle 行）。
     *
     * <p>各重量列记 0：这批货没经过毛菜处理间的称重/处理流程，只是借这行做「作物×地块」的归集锚点，
     * 后续 send_platform_weight / feed_weight 由调用方累加。{@code handle_status} 记 processing、
     * {@code is_finish=2}（未完成），避免被当成已结算行参与损耗结算。</p>
     */
    private VegetableHandle createMinimalHandle(Long cropId, Long plotId, Long productId) {
        VegetableHandle h = new VegetableHandle();
        h.setPlotId(plotId);
        h.setCropId(cropId);
        h.setProductId(productId);
        h.setPickStartTime(new Date());
        h.setPickedWeight(BigDecimal.ZERO);
        h.setHandledWeight(BigDecimal.ZERO);
        h.setFeedWeight(BigDecimal.ZERO);
        h.setSendPlatformWeight(BigDecimal.ZERO);
        h.setStockInWeight(BigDecimal.ZERO);
        h.setLossWeight(BigDecimal.ZERO);
        h.setIsWeighed(1);
        h.setIsFinish(2);
        h.setHandleStatus("processing");
        h.setRemark("毛菜间出库按需补建（采摘直送毛菜保鲜室的库存无毛菜处理记录）");
        vegetableHandleMapper.insert(h);
        log.info("[VEG-OUT] 补建毛菜处理归集行 cropId={} plotId={} id={}", cropId, plotId, h.getId());
        return h;
    }

    /**
     * 写有机饲喂记录：{@code feed_type='veg_handle'} 即甲方要的「位置记录为毛菜间」。
     */
    private void insertFeedLog(ProductInfo product, LocationStock stock, BigDecimal weight,
                               Long locationId, Long userId, Date outDate) {
        Long cropId = resolveCropIdByProduct(product.getId());
        FeedLog feedLog = new FeedLog();
        feedLog.setFeedDate(outDate);
        feedLog.setCropId(cropId);
        feedLog.setCropName(cropId != null ? cropNameOf(cropId) : product.getProductName());
        feedLog.setFeedType(FEED_TYPE_VEG_HANDLE);
        feedLog.setProductId(product.getId());
        feedLog.setLocationId(locationId);
        feedLog.setOperatorId(userId);
        feedLog.setFeedWeight(weight);
        feedLogMapper.insert(feedLog);
    }

    /** 产品 → 作物反查（{@code crop_info.related_product}，全库 1:1）。查不到返 null。 */
    private Long resolveCropIdByProduct(Long productId) {
        if (productId == null) {
            return null;
        }
        CropInfo crop = cropInfoMapper.selectOne(new LambdaQueryWrapper<CropInfo>()
            .eq(CropInfo::getRelatedProduct, productId)
            .orderByAsc(CropInfo::getId)
            .last("LIMIT 1"));
        return crop != null ? crop.getId() : null;
    }

    private String cropNameOf(Long cropId) {
        CropInfo crop = cropInfoMapper.selectById(cropId);
        return crop != null ? crop.getCropName() : null;
    }

    /**
     * 出库流水时间戳：业务日期为准。
     *
     * <p>选当天 → 保留当前时分秒（真实操作时刻，出库记录看得出先后）；
     * 选历史日期 → 该日 00:00:00（那天几点录的不可知，与事件台账补录同款约定）。</p>
     */
    private Date resolveFlowDate(Date outDate) {
        if (outDate == null) {
            return new Date();
        }
        LocalDate picked = outDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return picked.isEqual(LocalDate.now()) ? new Date() : outDate;
    }

    /** 毛菜鲜品库库位 id；库位未维护时 fail-fast（与毛菜处理入库同款报错口径）。 */
    private Long requireFreshVegLocationId() {
        LocationInfo loc = locationInfoMapper.selectOne(new LambdaQueryWrapper<LocationInfo>()
            .eq(LocationInfo::getLocationCode, LOCATION_CODE_FRESH_VEG)
            .last("LIMIT 1"));
        if (loc == null) {
            throw new ServiceException("毛菜鲜品库（库位编码 " + LOCATION_CODE_FRESH_VEG + "）不存在，请先在库位管理维护");
        }
        return loc.getId();
    }

    /**
     * 三个可出库库位（毛菜鲜品库 / 干货库 / 蛋类库）的 id 集合。
     *
     * <p>按 {@code location_code} 解析。某个库位没维护就不进集合（该库的产品自然也不会出现在候选里），
     * 不 fail-fast 整单 —— 甲方可能只用其中一两个库。</p>
     */
    private java.util.Set<Long> resolveAllowedLocationIds() {
        List<LocationInfo> rows = locationInfoMapper.selectList(new LambdaQueryWrapper<LocationInfo>()
            .in(LocationInfo::getLocationCode, ALLOWED_LOCATION_CODES));
        if (rows.isEmpty()) {
            throw new ServiceException("毛菜鲜品库 / 干货库 / 蛋类库均未维护，请先在库位管理配置");
        }
        return rows.stream().map(LocationInfo::getId).collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 出库单号（row192）：7 位纯数字、终生递增，走统一编码生成器
     * （Redisson 锁 + 序号表唯一键保证并发不撞号，故不再需要原来的随机后缀）。
     */
    private String generateBatchNo() {
        return bizCodeGenerator.generate(BizCodeType.VEG_OUT_NO, java.util.Map.of());
    }

    /** 批量回填操作人姓名（一次查 sys_user，避免 N+1）。 */
    private void fillOperatorNames(List<VegOutBatchVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> ids = rows.stream().map(VegOutBatchVo::getOperatorId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = ids.stream().collect(Collectors.toMap(
            id -> id, id -> StringUtils.blankToDefault(userService.selectNicknameById(id), ""), (a, b) -> a));
        rows.forEach(r -> r.setOperatorName(nameMap.get(r.getOperatorId())));
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
