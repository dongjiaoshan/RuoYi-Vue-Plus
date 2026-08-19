package org.dromara.djs.warehouse.veg.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.plant.activity.domain.bo.PickActivityRecordBo;
import org.dromara.djs.plant.activity.service.IPlantActivityService;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.domain.vo.CropProductVo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.crop.service.ICropProductService;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.mapper.PlantDetailsMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.loss.domain.LossFlow;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.domain.vo.PlotProductStockRow;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.veg.domain.FeedLog;
import org.dromara.djs.warehouse.veg.domain.HandleRecord;
import org.dromara.djs.warehouse.veg.domain.HandleRecordTeam;
import org.dromara.djs.warehouse.veg.domain.PlantingRecord;
import org.dromara.djs.warehouse.veg.domain.VegetableHandle;
import org.dromara.djs.warehouse.veg.domain.bo.HandleRecordSubmitBo;
import org.dromara.djs.warehouse.veg.domain.bo.HarvestSubmitBo;
import org.dromara.djs.warehouse.veg.domain.bo.PickActivitySubmitBo;
import org.dromara.djs.warehouse.veg.domain.bo.PickDestSubmitBo;
import org.dromara.djs.warehouse.veg.domain.bo.ProcessSubmitBo;
import org.dromara.djs.warehouse.veg.domain.query.PickDetailQuery;
import org.dromara.djs.warehouse.veg.domain.query.VegHandleQuery;
import org.dromara.djs.warehouse.veg.domain.vo.HandleRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.PendingPlantingRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.PickDetailVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegCropVo;
import org.dromara.djs.warehouse.veg.domain.vo.HandleProductNetRow;
import org.dromara.djs.warehouse.veg.domain.vo.VegPlotDetailVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegPlotProductVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegetableHandleVo;
import org.dromara.djs.warehouse.veg.mapper.FeedLogMapper;
import org.dromara.djs.warehouse.veg.mapper.HandleRecordMapper;
import org.dromara.djs.warehouse.veg.mapper.HandleRecordTeamMapper;
import org.dromara.djs.warehouse.veg.mapper.PlantingRecordMapper;
import org.dromara.djs.warehouse.veg.mapper.VegetableHandleMapper;
import org.dromara.djs.warehouse.veg.service.IVegetableHandleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 毛菜处理 Service 实现（WMS-VEG-001）。
 *
 * <h3>并发安全</h3>
 * <p>同一 planting_record 并发写入：MySQL InnoDB REPEATABLE_READ + 事务内 SELECT + 后续 UPDATE 同行
 * 通过行锁串行化。UNIQUE 维度由业务上"一个 planting_record 对应一个 handle 汇总"约束，
 * 由 {@link VegetableHandleMapper#selectByPlantingRecordId} + 事务隔离保证。</p>
 *
 * <h3>V6 row102 毛菜处理间链路（唯一现行口径，入口 {@link #submitHarvest} / {@link #submitProcess}）</h3>
 * <ol>
 *   <li><b>采摘录入即入库</b>：作物在称重的一刻就变成产品，默认入毛菜保鲜库 L0006，
 *       建一个「地块篮」+ 一条 {@code veg_stock_in} 入库流水。篮子带
 *       {@code source_biz_id = planting_record.id}，标明「这篮是哪条种植记录采下来的」。
 *       汇总列 {@code stock_in_weight} 语义 = 采摘累计入毛菜间的量。</li>
 *   <li><b>处理录入 = 从毛菜间出库</b>：去向只剩「果蔬月台 / 有机饲喂」（毛菜鲜品库去向已取消 ——
 *       货已经在库里，再入一次就是同一批货入两次账）。两个去向都按 FIFO 跨篮扣 L0006 库存
 *       + 写一条 {@code veg_stock_out} 出库流水（{@code stock_out_dest} 分别 veg_dock / feed）。
 *       出库上限 = <b>本条种植记录名下</b>篮子的实际库存，不再按采摘累计封顶。</li>
 *   <li><b>地块处理完成</b>：把<b>本条种植记录名下</b>在毛菜间的剩余库存全部结转损耗 ——
 *       篮子扣到 0 + 每个产品写一条 {@code loss_flow}（与既有毛菜间损耗同类型，故每日损耗汇总自动含它）。
 *       {@code loss_weight} 列 = 本次结转量。结算时机不变，仅 {@code is_finish=1} 时结。</li>
 *   <li><b>两个重量</b>：果蔬处理重量 = {@code handled_weight}（= 月台 + 饲喂 = 从毛菜间出库总量，
 *       毛菜间出库管理出的量由 {@code VegOutServiceImpl} 回写进来）；
 *       剩余重量 = 本条种植记录在毛菜间的实时库存（读侧 SQL 直接查库存，不做减法）。</li>
 * </ol>
 *
 * <h3>为什么一切都按 {@code source_biz_id} 收窄（第二轮修复的核心）</h3>
 * <p>第一版把篮子的定位键定为 {@code (库位, 产品, 地块)}，等于假设「一个地块只有一条业务流」。
 * 真实数据里同一 {@code (地块, 产品)} 会同时存在：同地块同作物的两条 planting_record（两季 / 补录）、
 * 采摘活动 {@code pick_dest=veg_fresh} 直送进来的货、两个作物共享同一产品时各自的货。于是
 * 「FIFO 扣减」「收口结转损耗」「剩余重量」三处全部串到别人的账上 —— 实测关闭 A 记录把 B 记录的
 * 25kg 结成了 A 的损耗（{@code loss > picked}），也能让采摘 60kg 的记录出库 68kg。
 * 三处一律加 {@code source_biz_id} 条件之后，串货的物理可能性消失，
 * 「{@code handled} 不可能超过自己那条记录的 {@code picked}」也就自动成立、无需再单设封顶。</p>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Slf4j
@Service
public class VegetableHandleServiceImpl
    extends DjsBaseServiceImpl<VegetableHandleMapper, VegetableHandle>
    implements IVegetableHandleService {

    /**
     * djs_veg_handle_status 取值。
     */
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_DONE = "done";

    /**
     * djs_record_type 取值。
     */
    private static final int RECORD_TYPE_PICK = 1;
    private static final int RECORD_TYPE_HANDLE = 2;

    /**
     * djs_pick_status「已完成」value（种植端采摘完成信号；{@code t_plant_plant_details.harvest_status}）。
     */
    private static final String PICK_STATUS_COMPLETED = "completed";

    /**
     * djs_handle_target 取值。
     */
    private static final int HANDLE_TARGET_STOCK_IN = 1;
    private static final int HANDLE_TARGET_PLATFORM = 2;
    private static final int HANDLE_TARGET_FEED = 3;

    /**
     * stock_flow.flow_type 蔬菜入库。
     */
    private static final String FLOW_TYPE_VEG_STOCK_IN = "veg_stock_in";

    /**
     * stock_flow.flow_type 毛菜间出库（V6 row102：处理录入去向从毛菜保鲜库出库）。
     * 字典 {@code djs_flow_type} seed 见 V202608310400。
     */
    private static final String FLOW_TYPE_VEG_STOCK_OUT = "veg_stock_out";

    /**
     * 毛菜鲜品库库位业务码（V6 row102 起：采摘录入的默认入库库位 + 处理录入的唯一出库库位）。
     * 按 location_code 查 id，不硬编码 id。
     */
    private static final String LOCATION_CODE_FRESH_VEG = "L0006";

    /**
     * stock_flow.inout_type CHAR(3) IN=入库。
     */
    private static final String INOUT_IN = "IN";

    /**
     * stock_flow.inout_type CHAR(3) OT=出库。
     */
    private static final String INOUT_OUT = "OT";

    /**
     * stock_flow.stock_out_dest 果蔬月台（字典 {@code djs_stock_out_dest}，与毛菜间出库管理同值）。
     */
    private static final String OUT_DEST_VEG_DOCK = "veg_dock";

    /**
     * stock_flow.stock_out_dest 有机饲喂（字典 {@code djs_stock_out_dest} 的 {@code feed}=投喂）。
     */
    private static final String OUT_DEST_FEED = "feed";

    /**
     * 统一损耗台账的毛菜处理损耗类型（字典 {@code djs_loss_type}）。
     */
    private static final String LOSS_TYPE_VEG_HANDLE = "veg_handle_loss";

    /**
     * djs_pick_dest 采摘去向（DENGBO-R4 决策 A，非销售去向映射到毛菜处理写入机制）。
     * sale（销售）不写仓库、不进本类，故无常量。
     */
    private static final String PICK_DEST_VEG_FRESH = "veg_fresh";
    private static final String PICK_DEST_PLATFORM = "platform";
    private static final String PICK_DEST_LOSS = "loss";
    private static final String PICK_DEST_FEED = "feed";

    private final HandleRecordMapper handleRecordMapper;
    /** 采摘班组多选中间表 mapper（ROW64）：采收行同步全集，旧单列 team_id 仍写首值过渡。 */
    private final HandleRecordTeamMapper handleRecordTeamMapper;
    private final PlantingRecordMapper plantingRecordMapper;
    private final StockFlowMapper stockFlowMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final ImageUrlResolver imageUrlResolver;
    private final CropInfoMapper cropInfoMapper;
    private final FeedLogMapper feedLogMapper;
    /** 种植明细 mapper：毛菜处理称重回写 actual_yield，让种植「采摘详情·已摘」反映仓库真实称重。 */
    private final PlantDetailsMapper plantDetailsMapper;
    /** 库位库存 mapper：毛菜处理入库回写 location_stock 余额（物资领用读余额表）。 */
    private final LocationStockMapper locationStockMapper;
    /** 产品 mapper：入库前校验解析出的 product_id 真实存在（不存在则跳过余额、不建孤儿行）。 */
    private final ProductInfoMapper productInfoMapper;
    /** 统一损耗门面：处理完成结算损耗时在原 loss_weight 列之上双写一条 t_warehouse_loss_flow 明细。 */
    private final ILossFlowService lossFlowService;
    /** 采摘活动 service（DENGBO-R4 采摘去向编排：先写 plant activity 行 + 产量分摊，再写仓库台账）。 */
    private final IPlantActivityService plantActivityService;
    /** 作物产品配置 service（V6 row16-18：一个作物多个产品，采摘 / 处理按所选产品记账）。 */
    private final ICropProductService cropProductService;

    /**
     * 作物图 L2 兜底分类键（作物无 belong_type，统一走"蔬菜默认图"）。
     */
    private static final String CROP_BELONG_TYPE = "vegetable";

    public VegetableHandleServiceImpl(VegetableHandleMapper baseMapper,
                                      HandleRecordMapper handleRecordMapper,
                                      HandleRecordTeamMapper handleRecordTeamMapper,
                                      PlantingRecordMapper plantingRecordMapper,
                                      StockFlowMapper stockFlowMapper,
                                      LocationInfoMapper locationInfoMapper,
                                      IBizCodeGenerator bizCodeGenerator,
                                      ImageUrlResolver imageUrlResolver,
                                      CropInfoMapper cropInfoMapper,
                                      FeedLogMapper feedLogMapper,
                                      PlantDetailsMapper plantDetailsMapper,
                                      LocationStockMapper locationStockMapper,
                                      ProductInfoMapper productInfoMapper,
                                      ILossFlowService lossFlowService,
                                      IPlantActivityService plantActivityService,
                                      ICropProductService cropProductService) {
        super(baseMapper);
        this.handleRecordMapper = handleRecordMapper;
        this.handleRecordTeamMapper = handleRecordTeamMapper;
        this.plantingRecordMapper = plantingRecordMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.imageUrlResolver = imageUrlResolver;
        this.cropInfoMapper = cropInfoMapper;
        this.feedLogMapper = feedLogMapper;
        this.plantDetailsMapper = plantDetailsMapper;
        this.locationStockMapper = locationStockMapper;
        this.productInfoMapper = productInfoMapper;
        this.lossFlowService = lossFlowService;
        this.plantActivityService = plantActivityService;
        this.cropProductService = cropProductService;
    }

    /**
     * 解析本次采摘 / 处理流水记到哪个产品头上（V6 row17/row18）。
     *
     * <p>用户传了就用用户传的，但必须真属于该作物的产品配置 —— 否则一次改前端 payload 就能把重量记到
     * 别的作物的产品上，库存跟着串。传空（作物只配了一个产品，mp 只展示不回传）取配置里的第一个；
     * 作物一个产品都没配时回落旧口径 {@code crop.related_product}，保持改造前行为。</p>
     */
    private Long resolveRecordProductId(Long cropId, Long requested) {
        List<CropProductVo> configured = cropProductService.listByCrop(cropId);
        if (!configured.isEmpty()) {
            if (requested != null) {
                boolean belongs = configured.stream().anyMatch(c -> requested.equals(c.getProductId()));
                if (!belongs) {
                    throw new ServiceException("所选产品不在该作物的产品配置中，请刷新后重试", 400);
                }
                return requested;
            }
            return configured.get(0).getProductId();
        }
        return resolveProductIdByCrop(cropId, null);
    }

    /**
     * 按作物 {@code crop.related_product}（FK → t_warehouse_product_info.id）解析果蔬成品 product_id。
     *
     * <p>甲方《果疏产品全流程处理.docx》规则：作物→产品转换走 {@code t_plant_crop_info.related_product}，
     * 毛菜处理产出的 product_id 取自该映射（重量不变）。</p>
     *
     * <p><b>优雅降级</b>：客户未在 admin 作物录入页填 related_product 时（现网全 NULL），返回
     * {@code fallback} 并 {@code log.warn}，不抛、不阻塞采摘/处理流程。fallback 通常是
     * {@code planting.getProductId()}（旧来源，多为 null）；写 stock_flow 的调用方对 null 结果
     * 显式失败（product_id=0 兜底已废除，防库存总览无名幽灵行）。</p>
     *
     * @param cropId   作物 id（planting_record.crop_id）
     * @param fallback related_product 为空时的兜底 product_id（可为 null）
     * @return 解析出的果蔬成品 product_id；无映射时返 fallback
     */
    private Long resolveProductIdByCrop(Long cropId, Long fallback) {
        if (cropId == null) {
            return fallback;
        }
        CropInfo crop = cropInfoMapper.selectById(cropId);
        if (crop == null || crop.getRelatedProduct() == null) {
            log.warn("作物未配置产品，product_id 降级为 {} — cropId={}（请在 admin 作物管理 → 编辑作物 →"
                + "「产品配置」页签为该作物添加产出产品）", fallback, cropId);
            return fallback;
        }
        return crop.getRelatedProduct();
    }

    /**
     * 遗留通用录入入口 {@code POST /applet/warehouse/vegHandle/submit} —— <b>已停用，一律拒绝</b>。
     *
     * <p>它是 V6 row102 改造之前的老口径，改造后三个分支各自都会把账做坏：</p>
     * <ul>
     *   <li>{@code recordType=1}（采收）只累加 {@code picked_weight}，<b>既不建库存篮也不写入库流水</b> ——
     *       这批货对新链路完全隐形：处理录入报库存不足、地块收口时静默蒸发。</li>
     *   <li>{@code recordType=2 + handleTarget=1}（毛菜鲜品库）仍能建篮，而 {@link #submitProcess}
     *       对同一个值是硬拒的（货在采摘时已经入过库，再入一次就是同一批货入两次账）。</li>
     *   <li>{@code recordType=2 + handleTarget=2/3}（月台 / 饲料）只加 {@code handled_weight}
     *       <b>而不扣库存</b> —— 同一批 kg 收口时会被 {@link #settleRemainAsLoss} 再认领成一次损耗。</li>
     * </ul>
     *
     * <p>不做「部分保留」：能走的两个去向要保留就必须复刻 {@link #submitProcess} 整套扣库存逻辑，
     * 而它没有任何 mp 入口在用（对应页面 {@code pages/warehouse/vegHandle/handle} 已随本次修复删除，
     * 全仓无 navigateTo 指向它），复刻一份等于凭空多养一条会漂移的并行链路。整条端点拒掉，
     * 报错文案直接指向新入口。</p>
     */
    @Override
    @Deprecated(since = "V6-R102")
    public Long submitHandleRecord(HandleRecordSubmitBo bo) {
        throw new ServiceException("该录入入口已停用（V6 row102 毛菜链路改造）："
            + "采摘请用「采摘录入」POST /applet/warehouse/vegHandle/harvest，"
            + "处理请用「处理录入」POST /applet/warehouse/vegHandle/process。"
            + "旧入口不建库存篮 / 不扣库存，继续使用会让毛菜间的账对不上。", 410);
    }

    /**
     * 毛菜保鲜库入库的<b>唯一写入口</b>：一条 {@code veg_stock_in} 入库流水 + 一个「地块篮」库存行。
     *
     * <p>两条入库路径（采摘录入即入库 / 采摘活动直送毛菜保鲜室）共用本方法，差别只在
     * 「产品怎么解析出来、解析不到时抛还是降级」以及<b>篮子带不带来源标识</b>，那部分留给各自的调用方。
     * 抽出来是因为两处必须写出<b>形状完全一致</b>的库存篮 —— 篮的形状一旦分叉，
     * 下游的 FIFO 出库就会漏掉某一路进来的货。</p>
     *
     * <p><b>{@code sourceBizId} 决定这篮归谁管</b>：传种植记录 id 的篮子才会被那条记录的处理录入 /
     * 收口损耗看见；传 {@code null}（采摘活动直送）的篮子对毛菜处理链路不可见，只能走
     * 「毛菜间出库管理」按行 id 出。这正是要的效果 —— 活动直送的货不该被某条种植记录吃掉
     * （实测第一版会把它整篮结成别人的损耗）。</p>
     *
     * <p>余额回写对齐采购/打包入库的范式：不写余额则物资领用·蔬菜 tab（以 product_info 为主表
     * JOIN location_stock）看不到可领用库存。{@code productId} 必须真实存在于 {@code product_info}；
     * 不存在时跳过余额 + warn，不建挂空 product 的孤儿余额行、也不阻断流水。</p>
     *
     * @param sourceBizId 来源业务 id（种植记录 id；采摘活动直送传 null）
     * @return {@code true} = 库存篮已建（这批货真的进了毛菜间）；
     *         {@code false} = 产品主数据缺失，只写了流水、余额跳过（调用方不应把它计进 stock_in_weight）
     */
    private boolean writeFreshVegStockIn(Long locationId, Long productId, Long plotId, Long sourceBizId,
                                         BigDecimal weight, Long userId, Date now, String remark) {
        StockFlow flow = new StockFlow();
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", INOUT_IN);
        flow.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx));
        flow.setFlowDate(now);
        flow.setProductId(productId);
        flow.setWarehouseId(locationId);
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(FLOW_TYPE_VEG_STOCK_IN);
        flow.setChangeNum(weight);
        flow.setChangeQuantity(weight);
        flow.setPlotId(plotId);
        flow.setThirdPhase(0);
        flow.setOperatorId(userId);
        flow.setRemark(remark);
        stockFlowMapper.insert(flow);

        ProductInfo product = productInfoMapper.selectById(productId);
        if (product == null) {
            log.warn("毛菜保鲜库入库余额未回写：product_id={} 在 product_info 不存在（作物未正确关联果蔬成品产品）"
                + " — plotId={} remark={}", productId, plotId, remark);
            return false;
        }
        if (plotId != null) {
            // 自产果蔬原料按「地块篮子」入冷库（plot_id = 篮子标签，对齐猪肉分割 ear_no 篮子，doc/14 §1）：
            // 每次入库建一篮（带 plot_id）。领用按篮 FIFO 把 plot 带到 product_inhouse → 果蔬打包
            // 右台显「对应地块」（而非领用记录）。同 plot 多次入库 = 多篮，打包页 plotToggle 按 plot 去重。
            LocationStock basket = new LocationStock();
            basket.setLocationId(locationId);
            basket.setProductId(productId);
            basket.setPlotId(plotId);            // 篮子标签 = 地块 → 打包追溯键
            // 来源标识 = 这篮是谁建的（种植记录 id）。地块只回答「货在哪块地上」，回答不了
            // 「同一块地同时跑着两条业务流时这篮算谁的」——毛菜处理的出库 / 收口全靠这一列定范围。
            basket.setSourceBizId(sourceBizId);
            basket.setProductName(product.getProductName());
            basket.setProductUnit(product.getProductUnit());
            basket.setProductStock(weight);
            basket.setIsEnd(0);
            basket.setThirdPhase(0);
            basket.setOperatorId(userId);
            locationStockMapper.insert(basket);
            return true;
        }
        // 兜底（毛菜处理来源 planting 理论必有 plot）：无地块 → product 维度 UPSERT（旧行为）
        int updated = locationStockMapper.addByProductLocation(locationId, productId, weight, userId);
        if (updated == 0) {
            LocationStock fresh = new LocationStock();
            fresh.setLocationId(locationId);
            fresh.setProductId(productId);
            fresh.setProductName(product.getProductName());
            fresh.setProductUnit(product.getProductUnit());
            fresh.setProductStock(weight);
            fresh.setIsEnd(0);
            fresh.setThirdPhase(0);
            fresh.setOperatorId(userId);
            locationStockMapper.insert(fresh);
        }
        return true;
    }

    /**
     * 毛菜鲜品库（L0006）库位 id；未维护时 fail-fast。
     *
     * <p>V6 row102 起它同时是采摘录入的入库落点与处理录入的出库来源，缺了整条毛菜链路都跑不通，
     * 报错文案直接指向可操作的修复动作（库位管理维护 L0006），不做静默降级 ——
     * 静默跳过会让采摘看似成功、随后处理录入统统报「库存不足」，更难排查。</p>
     */
    private Long requireFreshVegLocationId() {
        LocationInfo loc = locationInfoMapper.selectOne(
            new LambdaQueryWrapper<LocationInfo>()
                .eq(LocationInfo::getLocationCode, LOCATION_CODE_FRESH_VEG)
                .last("LIMIT 1"));
        if (loc == null) {
            throw new ServiceException("毛菜鲜品库（库位编码 " + LOCATION_CODE_FRESH_VEG + "）不存在，请先在库位管理维护");
        }
        return loc.getId();
    }

    /**
     * 毛菜鲜品库库位 id；未维护时返 null（只读路径用 —— 查列表不该因为库位没配就整页报错）。
     */
    private Long freshVegLocationIdOrNull() {
        LocationInfo loc = locationInfoMapper.selectOne(
            new LambdaQueryWrapper<LocationInfo>()
                .eq(LocationInfo::getLocationCode, LOCATION_CODE_FRESH_VEG)
                .last("LIMIT 1"));
        return loc != null ? loc.getId() : null;
    }

    /**
     * 重量的可读串（去尾零，如 {@code 12.500} → {@code 12.5}），只用于报错文案。
     */
    private static String plain(BigDecimal v) {
        return nullSafe(v).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 首次录入时建 vegetable_handle 汇总行（picked/handled/feed/sendPlatform/stockIn/loss 全 0，
     * is_weighed=2 / is_finish=2 / handle_status=processing）。tenant_id 走 MP 自动 fill。
     */
    private VegetableHandle createHandleRow(PlantingRecord planting, Date now) {
        VegetableHandle handle = new VegetableHandle();
        handle.setPlantingRecordId(planting.getId());
        handle.setPlotId(planting.getPlotId());
        handle.setCropId(planting.getCropId());
        // product_id 优先按作物 related_product（作物↔果蔬成品映射）解析；未配置则降级回旧来源 planting.product_id
        handle.setProductId(resolveProductIdByCrop(planting.getCropId(), planting.getProductId()));
        handle.setPickStartTime(now);
        handle.setPickedWeight(BigDecimal.ZERO);
        handle.setHandledWeight(BigDecimal.ZERO);
        handle.setFeedWeight(BigDecimal.ZERO);
        handle.setSendPlatformWeight(BigDecimal.ZERO);
        handle.setStockInWeight(BigDecimal.ZERO);
        handle.setLossWeight(BigDecimal.ZERO);
        handle.setIsWeighed(2);
        handle.setIsFinish(2);
        handle.setHandleStatus(STATUS_PROCESSING);
        baseMapper.insert(handle);
        return handle;
    }

    /**
     * 毛菜处理称重回写种植产量：仓库称重 = 实际采摘产量，把累计 picked 写回 {@code t_plant_plant_details.actual_yield}，
     * 让种植「采摘详情·已摘」反映仓库真实称重（#3=a 后采收 tab 不录重量，actual_yield 在种植侧无来源，唯一来源在此）。
     *
     * <p>定位：{@code planting_record} 不存 detail_id，按 (plot_id, crop_id) 匹配，优先已完成采摘（{@code harvest_status='completed'}）
     * 的明细（V1 plot+crop 基本 1:1）。方向为 warehouse→plant 写，与本类已有 {@link CropInfoMapper} 依赖同向、不成环
     * （plant 不反向依赖 warehouse）。</p>
     *
     * @param plotId      地块 id
     * @param cropId      作物 id
     * @param pickedTotal 该地块该作物累计采摘重量（vegetable_handle.picked_weight）
     */
    private void syncActualYieldToPlant(Long plotId, Long cropId, BigDecimal pickedTotal) {
        if (plotId == null || cropId == null || pickedTotal == null) {
            return;
        }
        List<PlantDetails> matches = plantDetailsMapper.selectList(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getPlotId, plotId)
                .eq(PlantDetails::getCropId, cropId)
                .orderByDesc(PlantDetails::getId));
        if (matches == null || matches.isEmpty()) {
            log.warn("毛菜处理回写采摘产量：未找到 plant_details plotId={} cropId={}，跳过", plotId, cropId);
            return;
        }
        PlantDetails target = matches.stream()
            .filter(d -> "completed".equals(d.getHarvestStatus()))
            .findFirst().orElse(matches.get(0));
        plantDetailsMapper.update(null,
            new LambdaUpdateWrapper<PlantDetails>()
                .eq(PlantDetails::getId, target.getId())
                .set(PlantDetails::getActualYield, pickedTotal)
                .set(PlantDetails::getUpdateBy, LoginHelper.getUserId()));
    }

    /**
     * 采摘「地块称重完成」前置门：校验该地块该作物的种植采摘已完成
     * （{@code t_plant_plant_details.harvest_status='completed'}，dict {@code djs_pick_status}「已完成」）。
     *
     * <p>口径：{@code planting_record} 不存 detail_id，按 (plot_id, crop_id) 匹配 plant_details（V1 plot+crop
     * 基本 1:1，与 {@link #syncActualYieldToPlant} 同定位规则）；只要存在一条 {@code harvest_status='completed'}
     * 即视为该地块采摘完成，放行。无任何匹配明细 / 全部未完成 → 抛 {@link ServiceException}「请先完成地块采收操作」。</p>
     *
     * <p>方向 warehouse→plant 只读查询，与本类已有 plant 依赖同向不成环。</p>
     *
     * @param plotId 地块 id
     * @param cropId 作物 id
     */
    private void requirePlantHarvestCompleted(Long plotId, Long cropId) {
        if (plotId == null || cropId == null) {
            throw new ServiceException("请先完成地块采收操作");
        }
        Long completedCount = plantDetailsMapper.selectCount(
            new LambdaQueryWrapper<PlantDetails>()
                .eq(PlantDetails::getPlotId, plotId)
                .eq(PlantDetails::getCropId, cropId)
                .eq(PlantDetails::getHarvestStatus, PICK_STATUS_COMPLETED));
        if (completedCount == null || completedCount == 0L) {
            throw new ServiceException("请先完成地块采收操作");
        }
    }

    @Override
    public List<PendingPlantingRecordVo> listPending() {
        return plantingRecordMapper.selectPendingList();
    }

    @Override
    public List<VegCropVo> listCrops() {
        List<VegCropVo> list = baseMapper.selectCropAggList();
        // IMG-LIB-001：thumbUrl 走 4 层 resolver（L1 作物 image_oss_id → L2 蔬菜默认图 → L3 全局），禁 N+1
        if (!list.isEmpty()) {
            List<ImageUrlResolver.Item> items = list.stream()
                .map(v -> new ImageUrlResolver.Item(v.getImageOssId(), CROP_BELONG_TYPE))
                .toList();
            List<String> urls = imageUrlResolver.resolveList(items);
            if (urls.size() == list.size()) {
                for (int i = 0; i < list.size(); i++) {
                    list.get(i).setThumbUrl(urls.get(i));
                }
            }
        }
        return list;
    }

    @Override
    public List<VegPlotDetailVo> listPlotsByCrop(Long cropId) {
        if (cropId == null) {
            throw new ServiceException("缺少作物 ID");
        }
        List<VegPlotDetailVo> plots = plantingRecordMapper.selectPlotDetailByCrop(cropId);
        fillPlotProducts(cropId, plots);
        return plots;
    }

    /**
     * 出库前置封顶：本次出库量不得超过<b>本条种植记录</b>在毛菜保鲜库该产品名下的实际库存（V6 row102）。
     *
     * <p>先按合计拦一道再进 {@link #deductFreshVegFifo}，是为了在<b>写任何一行之前</b>给出可读的错误
     * （逐篮扣到一半才发现不够只能靠回滚，报错也说不清差多少）。两处 WHERE 完全同源，
     * 不会出现「校验说够、真扣时不够」。</p>
     *
     * <p><b>{@code sourceBizId} 必须一起传进去</b>：只按 {@code (产品, 地块)} 求和会把同地块另一条
     * 种植记录的货、采摘活动直送的货一起算成本条记录的可出量 —— 实测能让一条采摘 60kg 的记录
     * 成功出库 68kg，{@code handled_weight} 直接超过 {@code picked_weight}。收窄之后
     * 「出库总量 ≤ 本记录采摘总量」由库存本身保证，不需要另设一道按采摘累计的封顶。</p>
     *
     * <p>产品 / 地块解析不出来时直接拒：没有这两个键就定位不到任何篮子，放行等于扣了个空、
     * 把重量凭空记进 handled，账再也对不回来。</p>
     */
    private void assertFreshStockEnough(Long locationId, Long productId, Long plotId,
                                        Long sourceBizId, BigDecimal weight) {
        if (productId == null || plotId == null) {
            throw new ServiceException("该地块尚未在毛菜保鲜库建账（作物未配置产出产品或地块信息缺失），"
                + "无法出库处理：请先在 admin 作物管理 → 编辑作物 →「产品配置」补齐产出产品", 400);
        }
        BigDecimal available = nullSafe(
            locationStockMapper.sumPlotProductStock(locationId, productId, plotId, sourceBizId));
        if (available.compareTo(weight) < 0) {
            throw new ServiceException("毛菜保鲜库该地块该产品仅剩 " + plain(available)
                + " kg，无法出库 " + plain(weight) + " kg", 400);
        }
    }

    /**
     * 从<b>本条种植记录</b>在毛菜保鲜库该产品名下的篮子里按 FIFO 跨篮扣减（V6 row102）。
     *
     * <p><b>为什么要跨篮</b>：每次采摘录入建一篮，出库量常常跨越多篮。</p>
     *
     * <p><b>为什么必须带 {@code sourceBizId}</b>：见 {@link #assertFreshStockEnough}。
     * 这里与上限校验用的是同一组键，两处一旦分家就会出现「校验说够、真扣时不够」的事务中途 409，
     * 或者更糟 —— 扣到别人的篮子上。</p>
     *
     * <p><b>并发安全</b>：每篮的扣减走 {@link LocationStockMapper#deductStockById}，
     * 那条 UPDATE 的 WHERE 自带 {@code product_stock >= 扣减量} —— MySQL 行锁与余量校验同一步发生，
     * 两个工人同时提交时只有一个能扣成功。{@code affectedRows = 0} 说明这一篮在我们读到它之后
     * 被别的事务扣走了，此时<b>整笔失败回滚</b>（不静默跳到下一篮：跳过就可能出现「总量看着够、
     * 实际拼不出来」的半扣状态）。前置的 {@link #assertFreshStockEnough} 已挡掉绝大多数不足场景，
     * 走到这里的冲突是真并发，让工人重提一次是正确代价。</p>
     */
    private void deductFreshVegFifo(Long locationId, Long productId, Long plotId, Long sourceBizId,
                                    BigDecimal weight, Long userId) {
        BigDecimal remaining = weight;
        for (LocationStock basket
            : locationStockMapper.selectPlotProductBaskets(locationId, productId, plotId, sourceBizId)) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal take = nullSafe(basket.getProductStock()).min(remaining);
            if (take.signum() <= 0) {
                continue;
            }
            if (locationStockMapper.deductStockById(basket.getId(), take, userId) == 0) {
                throw new ServiceException("毛菜保鲜库该批库存正被其他操作占用，请刷新后重试", 409);
            }
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            throw new ServiceException("毛菜保鲜库该地块该产品库存不足，还差 " + plain(remaining)
                + " kg，请刷新后重试", 409);
        }
    }

    /**
     * 写一条毛菜保鲜库出库流水（{@code veg_stock_out} / {@code OT}；V6 row102）。
     *
     * <p>去向落 {@code stock_out_dest}：果蔬月台 {@code veg_dock} / 有机饲喂 {@code feed}，
     * 与毛菜间出库管理（{@code VegOutServiceImpl}）用同一套字典值，两条链路的出库能并在一起对账。</p>
     */
    private void insertVegStockOutFlow(Long locationId, Long productId, Long plotId, BigDecimal weight,
                                       String outDest, Long userId, Date now, String remark) {
        StockFlow flow = new StockFlow();
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", INOUT_OUT);
        flow.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx));
        flow.setFlowDate(now);
        flow.setProductId(productId);
        flow.setWarehouseId(locationId);
        flow.setInoutType(INOUT_OUT);
        flow.setFlowType(FLOW_TYPE_VEG_STOCK_OUT);
        flow.setStockOutDest(outDest);
        flow.setChangeNum(weight);
        flow.setChangeQuantity(weight);
        flow.setPlotId(plotId);
        // 扣的是 third_phase=0 的普通篮（见 selectPlotProductBaskets），流水照实标 0
        flow.setThirdPhase(0);
        flow.setOperatorId(userId);
        flow.setRemark(remark);
        stockFlowMapper.insert(flow);
    }

    /**
     * 地块处理完成 → 把毛菜保鲜库里<b>本条种植记录</b>名下的剩余库存全部结转损耗（V6 row102 第 2 条）。
     *
     * <p><b>迭代的是本记录的实际篮子，不是任何一份产品清单</b>。甲方口径是「点击地块处理完成时，
     * 剩下的库存记录为损耗数据」——「剩下的库存」的唯一权威就是 {@code source_biz_id = 本记录 id}
     * 的那些篮，{@link LocationStockMapper#selectBasketsBySource} 一次取全。每篮自带 {@code product_id}，
     * 损耗按它归集，不需要反解产品。</p>
     *
     * <p>🔴 <b>为什么不能按产品清单枚举</b>（第二轮的写法：作物产品配置 ∪ {@code crop.related_product}
     * ∪ {@code handle.product_id}，逐个产品取篮）：那份清单与读侧范围不是同一个东西。读侧
     * （{@link LocationStockMapper#selectPlotProductStocks} / {@code PlantingRecordMapper#selectPlotDetailByCrop}）
     * 只按 {@code source_biz_id} 查，产品被移出作物配置后照样看得见它的存量；而清单里它已经没了
     * —— {@code handle.product_id} 也兜不住，那是建汇总行时写死的单值（{@code = crop.related_product}），
     * 不是实际采收过的产品集合。于是「读得到、结不掉」：收口后 {@code picked ≠ handled + loss}，
     * 那批货扣不掉又出不去（记录已 done，处理录入直接被拒），在 mp 地块卡上永久显示成僵尸剩余。
     * 实测：作物配 P1/P2 各采 30/20kg，删掉 P2 的作物产品配置后收口 → loss 只有 P1 的 30，
     * P2 的 20kg 一分没结没扣。改成按篮迭代之后，读侧与结算侧同一个源，范围不可能再分家。</p>
     *
     * <p>🔴 <b>{@code source_biz_id} 仍是范围的关键</b>：只按 {@code (产品, 地块)} 取篮的话，
     * 「关掉 A 记录」会把同地块 B 记录的货、采摘活动直送的货、共享同一产品的另一作物的货
     * 一并扣光并全部记成 A 的损耗（实测 A 采摘 40kg 却结出 55kg，{@code loss > picked}）。
     * 收窄到本条记录之后，结转量在数学上不可能超过本记录的 {@code picked}。</p>
     *
     * <p><b>扣库存与写损耗必须成对</b>：只写 loss_flow 不扣篮，库存会永远挂着已经算作损耗的量，
     * 之后毛菜间出库还能把它出出去 = 凭空多出一批货。</p>
     *
     * <p>损耗明细字段与既有毛菜处理损耗完全一致（{@code loss_type=veg_handle_loss} /
     * {@code source_biz_type=veg_handle} / plotId / productId / belongType / operatorId），
     * 每日损耗汇总读 {@code loss_flow} 自然把它统计进去，无需改汇总侧。同一产品的多篮合并成一条
     * 损耗流水（先扣完所有篮再逐产品写），台账行数与产品数一致、不按篮碎片化。</p>
     *
     * @return 本次结转的损耗合计（kg）；无剩余则 0
     */
    private BigDecimal settleRemainAsLoss(VegetableHandle handle, PlantingRecord planting,
                                          Long locationId, Long userId) {
        // 产品 → 本次结转量；LinkedHashMap 保证损耗流水的写入顺序 = 篮子的 FIFO 顺序（便于对账）
        Map<Long, BigDecimal> settledByProduct = new LinkedHashMap<>();
        // 产品 → 地块（取自篮子本身；planting.plotId 作兜底，正常两者相同）
        Map<Long, Long> plotByProduct = new HashMap<>();
        for (LocationStock basket : locationStockMapper.selectBasketsBySource(locationId, planting.getId())) {
            BigDecimal qty = nullSafe(basket.getProductStock());
            if (qty.signum() <= 0 || basket.getProductId() == null) {
                continue;
            }
            if (locationStockMapper.deductStockById(basket.getId(), qty, userId) == 0) {
                throw new ServiceException("毛菜保鲜库该批库存正被其他操作占用，请刷新后重试", 409);
            }
            settledByProduct.merge(basket.getProductId(), qty, BigDecimal::add);
            plotByProduct.putIfAbsent(basket.getProductId(),
                basket.getPlotId() != null ? basket.getPlotId() : planting.getPlotId());
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> e : settledByProduct.entrySet()) {
            LossFlow lossFlow = new LossFlow();
            lossFlow.setLossType(LOSS_TYPE_VEG_HANDLE);
            lossFlow.setLossWeight(e.getValue());
            lossFlow.setProductId(e.getKey());
            lossFlow.setPlotId(plotByProduct.get(e.getKey()));
            lossFlow.setBelongType(CROP_BELONG_TYPE);
            lossFlow.setSourceBizType("veg_handle");
            lossFlow.setSourceBizId(handle.getId());
            lossFlow.setOperatorId(userId);
            lossFlowService.record(lossFlow);
            total = total.add(e.getValue());
        }
        return total.setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * 给每个地块行补「产品 + 各自剩余重量」（V6 row17/row18；剩余口径 row102 改为实时库存）。
     *
     * <p><b>剩余 = 该 {@code (种植记录, 产品)} 在毛菜保鲜库 L0006 的实时库存</b>，与地块卡的
     * remainWeight 同源、与处理录入的出库上限同源（三处都带 {@code source_biz_id}）。
     * 三处同源是硬要求：mp 拿这个数决定「哪个产品还能选来处理」，一旦读侧算法与服务端校验分家，
     * 就会出现「页面显示还有 30kg、提交却说库存不足」；而按地块而不按记录聚合，
     * 同地块两条种植记录会各读到同一份库存、mp 头卡 {@code Σ remainWeight} 直接翻倍。</p>
     *
     * <p><b>行集合 = 作物当前配置的产品 ∪ 地里还有流水的产品</b>。后者不能省：作物产品配置支持删除，
     * 一旦某产品被移出配置、而它名下还有没处理完的库存，只按配置渲染会让那部分货在分产品视图里
     * 凭空消失。已不在配置里的产品照常列出、只是不能再被选来录入，直到它的存量处理干净。
     * 圈定行集合仍走 handle_record 流水（而不是「该地块所有库存篮」）—— 按篮反推会把同一地块上
     * 别的作物的产品也拉进来串味。</p>
     */
    private void fillPlotProducts(Long cropId, List<VegPlotDetailVo> plots) {
        if (plots == null || plots.isEmpty()) {
            return;
        }
        List<CropProductVo> configured = cropProductService.listByCrop(cropId);
        List<Long> handleIds = plots.stream().map(VegPlotDetailVo::getHandleId)
            .filter(Objects::nonNull).distinct().toList();
        // 作物一个产品都没配、且历史也没有按产品记过账 → 保持改造前形态（mp 不显示产品行、提交不带 productId）
        if (configured.isEmpty() && handleIds.isEmpty()) {
            plots.forEach(p -> p.setProducts(List.of()));
            return;
        }
        Set<Long> configuredIds = configured.stream().map(CropProductVo::getProductId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        // 已被移出配置、但这批汇总行下还有流水的产品：单独反查名字补进展示行
        Set<Long> orphanProductIds = new LinkedHashSet<>();
        if (!handleIds.isEmpty()) {
            for (HandleProductNetRow row : handleRecordMapper.selectProductNetByHandleIds(handleIds)) {
                Long pid = row.getProductId();
                if (pid != null && !configuredIds.contains(pid)) {
                    orphanProductIds.add(pid);
                }
            }
        }
        Map<Long, String> orphanNames = new LinkedHashMap<>();
        if (!orphanProductIds.isEmpty()) {
            for (ProductInfo p : productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .select(ProductInfo::getId, ProductInfo::getProductName)
                .in(ProductInfo::getId, orphanProductIds))) {
                orphanNames.put(p.getId(), p.getProductName());
            }
        }
        // plantingRecordId → (productId → 毛菜保鲜库库存)
        Map<Long, Map<Long, BigDecimal>> stockByRecord = loadFreshStockByRecord(plots);
        for (VegPlotDetailVo plot : plots) {
            Map<Long, BigDecimal> stock = plot.getPlantingRecordId() == null ? Map.of()
                : stockByRecord.getOrDefault(plot.getPlantingRecordId(), Map.of());
            List<VegPlotProductVo> rows = new ArrayList<>(configured.size() + orphanNames.size());
            for (CropProductVo cp : configured) {
                rows.add(productRow(cp.getProductId(), cp.getProductName(),
                    stock.getOrDefault(cp.getProductId(), BigDecimal.ZERO), true));
            }
            for (Map.Entry<Long, String> e : orphanNames.entrySet()) {
                BigDecimal remain = stock.get(e.getKey());
                // 该地块下这个已下架产品已经没货了就不占一行（别给每块地都挂一行 0）
                if (remain == null || remain.signum() == 0) {
                    continue;
                }
                rows.add(productRow(e.getKey(), e.getValue(), remain, false));
            }
            plot.setProducts(rows);
        }
    }

    /**
     * 一次查回这批种植记录在毛菜保鲜库的「产品 × 记录」库存（V6 row102 分产品剩余重量数据源）。
     *
     * <p>键是 {@code plantingRecordId} 而不是 {@code plotId}：同一地块可能同时挂两条种植记录，
     * 按地块归并会让两条各显示一份同样的库存。</p>
     *
     * <p>库位没维护时返空 map + warn，剩余按 0 展示 —— 这是只读列表接口，不该因为库位配置缺失整页报错
     * （写入路径另有 {@link #requireFreshVegLocationId} 的 fail-fast 把关）。</p>
     */
    private Map<Long, Map<Long, BigDecimal>> loadFreshStockByRecord(List<VegPlotDetailVo> plots) {
        List<Long> recordIds = plots.stream().map(VegPlotDetailVo::getPlantingRecordId)
            .filter(Objects::nonNull).distinct().toList();
        if (recordIds.isEmpty()) {
            return Map.of();
        }
        Long locationId = freshVegLocationIdOrNull();
        if (locationId == null) {
            log.warn("毛菜鲜品库（库位编码 {}）未维护，地块分产品剩余重量按 0 展示", LOCATION_CODE_FRESH_VEG);
            return Map.of();
        }
        Map<Long, Map<Long, BigDecimal>> result = new HashMap<>();
        for (PlotProductStockRow row : locationStockMapper.selectPlotProductStocks(locationId, recordIds)) {
            if (row.getSourceBizId() == null || row.getProductId() == null) {
                continue;
            }
            result.computeIfAbsent(row.getSourceBizId(), k -> new HashMap<>())
                .merge(row.getProductId(), nullSafe(row.getStockWeight()), BigDecimal::add);
        }
        return result;
    }

    private static VegPlotProductVo productRow(Long productId, String productName,
                                               BigDecimal remain, boolean selectable) {
        VegPlotProductVo vo = new VegPlotProductVo();
        vo.setProductId(productId);
        vo.setProductName(productName);
        vo.setRemainWeight(remain);
        vo.setSelectable(selectable);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitHarvest(HarvestSubmitBo bo) {
        Date now = new Date();
        Long userId = bo.getWeighUserId();

        // Step 1：校验 planting_record 存在 + 未完成
        PlantingRecord planting = plantingRecordMapper.selectById(bo.getPlantingRecordId());
        if (planting == null) {
            throw new ServiceException("种植记录不存在：" + bo.getPlantingRecordId());
        }
        if (STATUS_DONE.equals(planting.getHandleStatus())) {
            throw new ServiceException("该种植记录已处理完成，不能再录入");
        }

        BigDecimal weight = bo.getHarvestWeight();
        boolean weighDone = bo.getWeighFinish() != null && bo.getWeighFinish() == 1;
        if (weight == null || weight.signum() < 0) {
            throw new ServiceException("采摘重量不能为空或负数", 400);
        }

        // Step 1.0a V6 row28：允许 0 kg 收口 —— 解决「人已经称完、但没人去点完成」导致地块卡在称重中。
        // 代价是 0 kg 必须真的把「地块是否称重完成」打开：一条既不带重量、又不收口的记录没有任何业务含义。
        if (weight.signum() == 0 && !weighDone) {
            throw new ServiceException("采摘重量为 0 时，必须打开「地块是否称重完成」才能提交", 400);
        }

        // Step 1.0b V6 row28（row64 班组多选的条件化）：去重去空后，>0 的真实称重仍必须选班组
        // （否则这次重量进不了 row39 班组绩效聚合）；0 kg 收口记录不要求选组 —— 绩效 SQL 本就按
        // record_weight > 0 过滤，0 摊给谁都是 0，强制选组只会让工人为了收口乱点一个组、污染绩效归属。
        List<Long> teamIds = bo.getTeamIds() == null ? List.of()
            : new ArrayList<>(new LinkedHashSet<>(
                bo.getTeamIds().stream().filter(Objects::nonNull).toList()));
        if (weight.signum() > 0 && teamIds.isEmpty()) {
            throw new ServiceException("请选择采摘班组", 400);
        }

        // Step 1.1：勾「地块称重完成」(weighFinish=1) 才能提交的前置门——该地块对应种植采摘必须已完成
        // （种植端 t_plant_plant_details.harvest_status='completed'，dict djs_pick_status「已完成」）。
        // 未完成不允许在仓库侧标记称重完成（避免采摘未结束就锁死地块）。未勾完成（仅追加重量）不校验。
        if (weighDone) {
            requirePlantHarvestCompleted(planting.getPlotId(), planting.getCropId());
        }

        // Step 2：找到 / 创建 vegetable_handle 汇总
        VegetableHandle handle = baseMapper.selectByPlantingRecordId(planting.getId());
        if (handle == null) {
            handle = createHandleRow(planting, now);
        }

        // Step 2.1：spec 步4「地块称重完成后不再允许新增采摘录入」后端强约束
        // （createHandleRow 把新行 isWeighed=2，不误伤本次新建的首录）
        if (handle.getIsWeighed() != null && handle.getIsWeighed() == 1) {
            throw new ServiceException("该地块已称重完成，不能再录入采摘重量");
        }

        // Step 3：INSERT handle_record（采收）
        // row64：teamIds 已在 Step 1.0b 去重去空，旧单列 team_id 写首值作过渡（row39 班组绩效按 team_id
        // GROUP BY 口径不变），全集写入 t_warehouse_handle_record_team 中间表。
        HandleRecord record = new HandleRecord();
        record.setHandleId(handle.getId());
        record.setPlotId(planting.getPlotId());
        record.setCropId(planting.getCropId());
        // row17：本次过磅算作哪个产品（作物多产品时由录入人选，单产品时服务端补首个）
        record.setProductId(resolveRecordProductId(planting.getCropId(), bo.getProductId()));
        // row64：旧单列写多选第一个，作为 row39 班组绩效按组采收总重量的统计维度（口径不变）
        record.setTeamId(teamIds.isEmpty() ? null : teamIds.get(0));
        record.setRecordType(RECORD_TYPE_PICK);
        record.setRecordWeight(weight);
        // row105：绩效百分比与称重记录同表存；不传按 100（全额计绩效，与改造前口径一致），范围由 BO 注解拦
        record.setPerfPercent(bo.getPerfPercent() != null ? bo.getPerfPercent() : 100);
        record.setRemark(bo.getRemark());
        record.setIsWeighed(weighDone ? 1 : 2);
        record.setIsFinish(2);
        record.setHandleTarget(null);
        record.setLocationId(null);
        record.setHandleUser(userId);
        record.setHandleTime(now);
        handleRecordMapper.insert(record);

        // Step 3.1：同步采摘班组多选中间表（先物理删旧关联再逐条插；采收行 INSERT-only，删为幂等无害）
        handleRecordTeamMapper.physicalDeleteByRecordId(record.getId());
        for (Long teamId : teamIds) {
            handleRecordTeamMapper.insert(new HandleRecordTeam(record.getId(), teamId));
        }

        // Step 3.2 V6 row102：采摘录入即入库 —— 称重的这一刻作物就变成产品，默认进毛菜保鲜库 L0006，
        // 按 (产品, 地块) 建一个地块篮 + 写一条入库流水。后面的「处理录入」出的就是这批货。
        BigDecimal stockedIn = stockInHarvestToFreshVeg(planting, weight, record.getProductId(), userId, now);

        // Step 4：聚合 UPDATE vegetable_handle（picked_weight += weight，stock_in_weight += 实际入库量）
        // 序号9-Req1：采摘阶段 is_finish 恒为 2（未处理完成）→ 损耗恒置 0，不在采摘时结算损耗（客户 2026-06-20）
        // row102：stock_in_weight 语义改为「采摘累计入毛菜间的量」，在此累加（原来由处理录入去向①累加）。
        BigDecimal picked = nullSafe(handle.getPickedWeight()).add(weight);
        BigDecimal stockIn = nullSafe(handle.getStockInWeight()).add(stockedIn);

        VegetableHandle delta = new VegetableHandle();
        delta.setId(handle.getId());
        delta.setPickedWeight(picked);
        delta.setStockInWeight(stockIn);
        delta.setLossWeight(BigDecimal.ZERO);
        if (STATUS_PENDING.equals(handle.getHandleStatus())) {
            delta.setHandleStatus(STATUS_PROCESSING);
        }
        // 采摘录入只动 is_weighed，不动 is_finish，不推 done
        if (weighDone) {
            delta.setIsWeighed(1);
        }
        baseMapper.updateById(delta);

        // Step 4.1：回写种植 actual_yield（仓库称重 = 实际采摘产量），让种植「采摘详情·已摘」反映真实称重
        syncActualYieldToPlant(planting.getPlotId(), planting.getCropId(), picked);

        // Step 5：同步 planting_record.handle_status pending → processing
        if (STATUS_PENDING.equals(planting.getHandleStatus())) {
            plantingRecordMapper.advanceHandleStatus(
                planting.getId(), STATUS_PENDING, STATUS_PROCESSING, userId);
        }

        return handle.getId();
    }

    /**
     * 采摘录入即入毛菜保鲜库（V6 row102 第 1 条：采摘录入后系统直接把作物化为产品，默认进毛菜保鲜库）。
     *
     * <p><b>产品解析不到时降级、不抛</b>：既有入库路径（{@link #insertVegStockInFlow} /
     * {@link #insertPickStockIn}）对「作物没配产出产品」是 fail-fast 的，那在处理/活动录入里说得通
     * —— 工人在屋里、可以喊人去后台补配置。但采摘录入是工人在地头对着秤按的，把它挡住等于当场停工，
     * 而重量本身是真实发生的事实、必须先记下来。故此处 {@code log.warn} + 跳过库存写入，
     * 采摘记录照常落库；这批货后续要么补齐产品配置后由盘点/毛菜间出库补账，要么在地块收口时算进损耗。
     * 代价是这类地块的「剩余重量」会显 0、处理录入会被库存上限挡住 —— 这是刻意的：
     * 没有产品就没有可出库的实体，宁可显 0 也不能凭空造一笔出不掉的账。</p>
     *
     * @param productId 本次采摘算作哪个产品（{@code handle_record.product_id}，可能为 null）
     * @return 实际入毛菜间的重量（0 = 未入库：0kg 收口记录 / 产品未配置 / 产品主数据缺失）
     */
    private BigDecimal stockInHarvestToFreshVeg(PlantingRecord planting, BigDecimal weight,
                                                Long productId, Long userId, Date now) {
        // 0 kg 收口记录不写库存、不写流水（写了只会造出空篮子进果蔬打包的 FIFO 领用列表，纯噪声）
        if (weight == null || weight.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (productId == null) {
            log.warn("采摘录入未入毛菜保鲜库：作物「{}」(cropId={}) 未配置产出产品，本次 {} kg 只记采摘不建库存篮"
                    + " — plantingRecordId={}。请在 admin 作物管理 → 编辑作物 →「产品配置」补齐后，"
                    + "用盘点 / 毛菜间出库为这批货补账。",
                planting.getCropName(), planting.getCropId(), weight, planting.getId());
            return BigDecimal.ZERO;
        }
        boolean stocked = writeFreshVegStockIn(requireFreshVegLocationId(), productId, planting.getPlotId(),
            planting.getId(), weight, userId, now,
            "采摘录入入毛菜保鲜库 plantingRecordId=" + planting.getId() + " crop=" + planting.getCropName());
        return stocked ? weight : BigDecimal.ZERO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitProcess(ProcessSubmitBo bo) {
        Date now = new Date();
        Long userId = bo.getProcessUserId();

        // Step 1：校验 planting_record 存在 + 未完成
        PlantingRecord planting = plantingRecordMapper.selectById(bo.getPlantingRecordId());
        if (planting == null) {
            throw new ServiceException("种植记录不存在：" + bo.getPlantingRecordId());
        }
        if (STATUS_DONE.equals(planting.getHandleStatus())) {
            throw new ServiceException("该种植记录已处理完成，不能再录入");
        }

        BigDecimal weight = bo.getProcessWeight();
        boolean processDone = bo.getProcessFinish() != null && bo.getProcessFinish() == 1;
        if (weight == null || weight.signum() < 0) {
            throw new ServiceException("处理重量不能为空或负数", 400);
        }

        // Step 2.1 V6 row29：允许 0 kg 收口（口径同 row28 采摘侧）—— 解决「已经处理完、但没人去点完成」。
        // 同样要求 0 kg 必须真的把「地块是否处理完成」打开，否则这条记录不产生任何业务效果。
        if (weight.signum() == 0 && !processDone) {
            throw new ServiceException("处理重量为 0 时，必须打开「地块是否处理完成」才能提交", 400);
        }

        // Step 2.2 校验去向。
        // V6 row102：【毛菜鲜品库】去向已取消 —— 货在采摘录入那一刻就进了毛菜保鲜库，
        // 这里再入一次就是同一批货入两次账（库存翻倍、损耗结算跟着虚高）。不做「两边兼容」，直接拒。
        // row41：有货（weight > 0）必须说清楚货去哪了；0 kg 收口记录不必填 —— 一分货都没走，
        // 两个去向桶加 0 完全等价，强制选一个只会让工人瞎点、给流水留一个查不出所以然的假去向。
        Integer handleTarget = bo.getHandleTarget();
        if (handleTarget != null && handleTarget == HANDLE_TARGET_STOCK_IN) {
            throw new ServiceException("毛菜鲜品库去向已取消：采摘录入时已自动入毛菜保鲜库，"
                + "本页只需选择果蔬月台或有机饲喂", 400);
        }
        if (handleTarget != null
            && handleTarget != HANDLE_TARGET_PLATFORM
            && handleTarget != HANDLE_TARGET_FEED) {
            throw new ServiceException("处理目标非法（必须 2=月台 / 3=饲料）：" + handleTarget);
        }
        if (handleTarget == null && weight.signum() > 0) {
            throw new ServiceException("请选择去向（2=月台 / 3=饲料）", 400);
        }
        // 下面全程用这两个 boolean 判去向，不再直接拆箱 handleTarget（null 收口记录会 NPE）
        boolean toPlatform = handleTarget != null && handleTarget == HANDLE_TARGET_PLATFORM;
        boolean toFeed = handleTarget != null && handleTarget == HANDLE_TARGET_FEED;

        // Step 3：找汇总行（必须先有采摘）
        VegetableHandle handle = baseMapper.selectByPlantingRecordId(planting.getId());
        if (handle == null) {
            throw new ServiceException("请先录入采摘重量");
        }

        // row18：本次处理算作哪个产品（作物多产品时由录入人选，单产品时服务端补首个）
        Long selectedProductId = resolveRecordProductId(planting.getCropId(), bo.getProductId());
        Long freshLocationId = requireFreshVegLocationId();

        // Step 3.0a V6 row102：出库上限 = 该「产品 × 地块」在毛菜保鲜库的实际库存。
        // 替代原来两道基于采摘累计的封顶（地块级 projectedHandled > picked + 产品级 assertWithinProductRemain）：
        //   ① 货已在采摘时入库，库存才是唯一真相 —— 毛菜间出库 / 盘点也会动它，减法口径必然与实物漂移；
        //   ② 新口径下 handled 已含 feed，旧式 handled + feed + 本次 会把饲喂量算两遍、必然误拦。
        // row29：0 kg 收口记录跳过封顶（它一分不出，封顶恒成立；照跑只会让存量脏行永远收不了口）。
        if (weight.signum() > 0) {
            assertFreshStockEnough(freshLocationId, selectedProductId, planting.getPlotId(),
                planting.getId(), weight);
        }

        // Step 3.0b 序号9-Req2：未「称重完成」(is_weighed=1) 不得标记「处理完成」（客户 2026-06-20）
        if (processDone && (handle.getIsWeighed() == null || handle.getIsWeighed() != 1)) {
            throw new ServiceException("请先完成地块称重，再标记处理完成");
        }

        // Step 4：INSERT handle_record（处理）
        // location_id 恒为 null：改造后本页不再有「入哪个库」的语义，出库来源库位固定 L0006 且已记在出库流水上。
        HandleRecord record = new HandleRecord();
        record.setHandleId(handle.getId());
        // 两个去向 handle_record 均记 plot_id（t_warehouse_handle_record.plot_id NOT NULL）。
        // spec 步8「饲料饲喂不记地块」由专用台账表 t_warehouse_feed_log（无 plot_id 列）满足；
        // handle_record 是毛菜处理事件日志（非饲料专用表），保留 plot_id 作处理来源上下文。
        record.setPlotId(planting.getPlotId());
        record.setCropId(planting.getCropId());
        // row18 第 3 点：按所选产品做后续处理 —— 流水与下面的出库流水用的是同一个 productId
        record.setProductId(selectedProductId);
        record.setRecordType(RECORD_TYPE_HANDLE);
        record.setRecordWeight(weight);
        record.setHandleTarget(handleTarget);
        record.setLocationId(null);
        record.setIsFinish(processDone ? 1 : 2);
        record.setHandleUser(userId);
        record.setHandleTime(now);
        handleRecordMapper.insert(record);

        // Step 5 V6 row102：两个去向都是「从毛菜保鲜库出库」——
        //   按 FIFO 跨篮扣 L0006 该 (产品, 地块) 的库存 + 写一条 veg_stock_out 出库流水；
        //   去向③饲料再加一条饲料台账（按日 × 作物品类，不记地块，spec 步8）。
        // row29：0 kg 收口记录不写下游台账 —— 写了会造出 0 kg 的出库流水和 0 kg 饲喂行，纯噪声。
        // handle_record 本身照记（谁在什么时候把这块地收口的，要有痕迹）。
        if (weight.signum() > 0 && (toPlatform || toFeed)) {
            String destLabel = toPlatform ? "果蔬月台" : "有机饲喂";
            deductFreshVegFifo(freshLocationId, selectedProductId, planting.getPlotId(),
                planting.getId(), weight, userId);
            insertVegStockOutFlow(freshLocationId, selectedProductId, planting.getPlotId(), weight,
                toPlatform ? OUT_DEST_VEG_DOCK : OUT_DEST_FEED, userId, now,
                "毛菜处理出库[" + destLabel + "] plantingRecordId=" + planting.getId()
                    + " crop=" + planting.getCropName());
            if (toFeed) {
                // row54：把工人选的处理产品传下去（与出库流水同源），别在台账里按作物反解回去
                insertFeedLog(planting, weight, userId, now, selectedProductId);
            }
        }

        // Step 5.1 V6 row102：地块处理完成 → 毛菜保鲜库里【本条种植记录名下】的剩余库存全部结转损耗
        //（按 source_biz_id 取全部篮 → 扣到 0 → 逐产品写 loss_flow）。结算时机不变：仅 is_finish=1 时结。
        // 范围与读侧（剩余重量 / 分产品剩余）同一个源，不按产品清单枚举 —— 详见 settleRemainAsLoss。
        BigDecimal loss = processDone
            ? settleRemainAsLoss(handle, planting, freshLocationId, userId)
            : BigDecimal.ZERO;

        // Step 6：聚合 UPDATE vegetable_handle（按 target 分流）
        BigDecimal handled = nullSafe(handle.getHandledWeight());
        BigDecimal feed = nullSafe(handle.getFeedWeight());
        BigDecimal sendPlatform = nullSafe(handle.getSendPlatformWeight());
        BigDecimal stockIn = nullSafe(handle.getStockInWeight());

        if (toPlatform) {
            sendPlatform = sendPlatform.add(weight);
            handled = handled.add(weight);
        } else if (toFeed) {
            // V6 row102 口径变更：饲喂也是「从毛菜间出库」，计入 handled
            // （甲方定义「果蔬处理重量 = 从毛菜间出库的总重量」）。改造前饲喂只加 feed 不进 handled。
            feed = feed.add(weight);
            handled = handled.add(weight);
        }
        // row41：0 kg 收口未选去向（handleTarget=null）→ 两个桶一个都不动。
        // null 只可能出现在 weight=0（Step 2.2 已硬校验），跳过分流不会漏账；
        // 上面的剩余转损耗照常跑，收口该结的账一分不少。

        VegetableHandle delta = new VegetableHandle();
        delta.setId(handle.getId());
        delta.setHandledWeight(handled);
        delta.setFeedWeight(feed);
        delta.setSendPlatformWeight(sendPlatform);
        delta.setStockInWeight(stockIn);
        delta.setLossWeight(loss);
        delta.setHandleStatus(STATUS_PROCESSING);
        if (processDone) {
            delta.setIsFinish(1);
            delta.setHandleStatus(STATUS_DONE);
            delta.setPickEndTime(now);
        }
        baseMapper.updateById(delta);

        // Step 7：同步 planting_record.handle_status
        if (STATUS_PENDING.equals(planting.getHandleStatus())) {
            plantingRecordMapper.advanceHandleStatus(
                planting.getId(), STATUS_PENDING, STATUS_PROCESSING, userId);
        }
        if (processDone) {
            plantingRecordMapper.advanceHandleStatus(
                planting.getId(), STATUS_PROCESSING, STATUS_DONE, userId);
        }

        return handle.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitPickActivity(PickActivitySubmitBo bo) {
        if (bo == null) {
            throw new ServiceException("采摘去向录入参数为空");
        }
        boolean sale = "sale".equals(bo.getPickDest());

        // 1. plant 侧：写 activity per-event 行 + 非销售去向累加所选地块产量（plant 自管 plant_details）
        PickActivityRecordBo plantBo = new PickActivityRecordBo();
        plantBo.setCropId(bo.getCropId());
        plantBo.setActivityDate(bo.getActivityDate());
        plantBo.setPickWeight(bo.getPickWeight());
        plantBo.setPickDest(bo.getPickDest());
        plantBo.setPlotId(bo.getPlotId());
        plantBo.setRecorderId(bo.getRecorderId());
        plantBo.setFinishFlag(bo.getFinishFlag()); // DENGBO-R24 录入完成标志透传
        plantBo.setTeamIds(bo.getTeamIds());       // row129 绩效班组多选透传（plant 侧落 junction）
        Long activityId = plantActivityService.recordPickActivity(plantBo);

        // 2. 非销售去向：写仓库台账（销售不写仓库库存、只进产量分摊，已在 step1 plant 侧完成行写入）
        //    DENGBO-R24：结算-only（仅录入完成、无本次重量）→ activityId 为 null，不写任何去向台账。
        if (activityId != null && !sale) {
            String cropName = bo.getCropName();
            if (cropName == null || cropName.isBlank()) {
                CropInfo crop = cropInfoMapper.selectById(bo.getCropId());
                cropName = crop != null ? crop.getCropName() : null;
            }
            PickDestSubmitBo destBo = new PickDestSubmitBo();
            destBo.setCropId(bo.getCropId());
            destBo.setCropName(cropName);
            destBo.setPlotId(bo.getPlotId());
            destBo.setProductId(resolveProductIdByCrop(bo.getCropId(), null));
            destBo.setPickDest(bo.getPickDest());
            destBo.setWeight(bo.getPickWeight());
            destBo.setRecorderId(bo.getRecorderId());
            recordPickDestination(destBo);
        }
        return activityId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordPickDestination(PickDestSubmitBo bo) {
        if (bo == null) {
            throw new ServiceException("采摘去向入账参数为空");
        }
        String dest = bo.getPickDest();
        BigDecimal weight = bo.getWeight();
        if (dest == null || dest.isBlank()) {
            throw new ServiceException("采摘去向不能为空");
        }
        if (weight == null || weight.signum() <= 0) {
            throw new ServiceException("采摘重量必须大于 0");
        }
        Long userId = bo.getRecorderId() != null ? bo.getRecorderId() : LoginHelper.getUserId();
        Date now = new Date();

        switch (dest) {
            case PICK_DEST_VEG_FRESH ->
                // 毛菜保鲜室 = 复用毛菜处理入库（stock_flow veg_stock_in + location_stock 按 plot 篮，落 L0006）
                insertPickStockIn(bo, weight, userId, now);
            case PICK_DEST_PLATFORM ->
                // DENGBO-R22：果蔬月台 = 写一行 t_warehouse_vegetable_handle 承载「发往月台重量」，
                // 使采摘活动直送月台的果蔬出现在「自产产品收货」待入库列表、可入库
                //（row55 起 selectSelfPending 读的是下面同写的 handle_record 明细，不再读 send_platform_weight）。
                insertPickPlatform(bo, weight, userId, now);
            case PICK_DEST_LOSS ->
                // 损耗 = 复用统一损耗台账 loss_flow（loss_type=veg_handle_loss）
                insertPickLoss(bo, weight, userId);
            case PICK_DEST_FEED ->
                // 饲料饲喂 = 复用饲料台账 feed_log（feed_type=veg_handle）
                insertPickFeed(bo, weight, userId, now);
            default -> throw new ServiceException("非法/不入仓库的采摘去向：" + dest
                + "（销售去向不应调用本入账方法）");
        }
    }

    /**
     * DENGBO-R22 采摘去向[果蔬月台]：写一行 {@code t_warehouse_vegetable_handle} 承载 send_platform_weight，
     * 使采摘活动直送月台的果蔬进入「自产产品收货」待入库列表（{@code selectSelfPending} 按 send_platform_weight 聚合）、可入库；
     * 其余处理量字段置 0，复用现有月台待收货/入库/损耗全链路。
     *
     * <p>Kevin 2026-07-16 定 A：同写一行 {@code t_warehouse_handle_record}(handle_target=2 发往月台，按 handle_time)，
     * 使采摘直送月台的量计入「发往月台果蔬总重」日统计（{@code WarehouseStatAggregateMapper.sumSendPlatformWeight}
     * 读 handle_record）+ 作物维发往口径，与入库后计入的「月台接收」（veg_receive）对称、不再漏计。
     * handle_record 按 handle_id/handle_user 归属本行，不污染毛菜处理列表。
     * <b>row55 起待入库量改读 handle_record 明细</b>（要按产品拆），所以这条明细行必须落 product_id，见下。</p>
     */
    private void insertPickPlatform(PickDestSubmitBo bo, BigDecimal weight, Long userId, Date now) {
        VegetableHandle handle = new VegetableHandle();
        handle.setPlotId(bo.getPlotId());
        handle.setCropId(bo.getCropId());
        handle.setProductId(pickProductId(bo));
        handle.setPickStartTime(now);
        handle.setPickedWeight(BigDecimal.ZERO);
        handle.setHandledWeight(BigDecimal.ZERO);
        handle.setFeedWeight(BigDecimal.ZERO);
        handle.setSendPlatformWeight(weight);
        handle.setStockInWeight(BigDecimal.ZERO);
        handle.setLossWeight(BigDecimal.ZERO);
        handle.setIsWeighed(2);
        handle.setIsFinish(2);
        handle.setHandleStatus(STATUS_PROCESSING);
        baseMapper.insert(handle);

        HandleRecord record = new HandleRecord();
        record.setHandleId(handle.getId());
        record.setPlotId(bo.getPlotId());
        record.setCropId(bo.getCropId());
        // row55：月台待入库量现在按产品聚合、数据源就是这张明细表。**必须落产品** ——
        // 不落的话采摘直送月台的量会全部挂到作物默认产品名下（红薯杆的货显在红薯卡里）。
        record.setProductId(handle.getProductId());
        record.setRecordType(RECORD_TYPE_HANDLE);
        record.setRecordWeight(weight);
        record.setHandleTarget(HANDLE_TARGET_PLATFORM);
        record.setLocationId(null);
        record.setIsFinish(2);
        record.setHandleUser(userId);
        record.setHandleTime(now);
        handleRecordMapper.insert(record);
    }

    /**
     * 采摘去向[毛菜保鲜室]入库：落毛菜鲜品库 L0006，写 stock_flow(veg_stock_in) + location_stock(按 plot 篮)。
     * 复用 {@link #writeFreshVegStockIn} 的库存写入口径，但不依赖 PlantingRecord/VegetableHandle 上下文。
     *
     * <p><b>{@code sourceBizId} 传 null</b>：采摘活动不挂在任何一条种植记录上（它是按作物 × 日期录的，
     * 地块只是分摊维度）。因此这篮货对毛菜处理链路不可见 —— 同地块种植记录的处理录入出不了它、
     * 收口也不会把它结成损耗。它的出口是「毛菜间出库管理」（按行 id 出），
     * {@code VegOutServiceImpl.resolveHandleId} 为这条来源留了按 {@code (作物, 地块)} 定位 / 按需补建
     * 归集行的分支 —— 出库量因此照样计进那一行的「果蔬处理重量」（它有地块标识，符合甲方口径）。</p>
     */
    private void insertPickStockIn(PickDestSubmitBo bo, BigDecimal weight, Long userId, Date now) {
        Long productId = resolveProductIdByCrop(bo.getCropId(), bo.getProductId());
        if (productId == null) {
            throw new ServiceException("作物「" + bo.getCropName() + "」未关联果蔬成品，无法入库："
                + "请先在 admin 作物管理 → 编辑作物 →「产品配置」页签为该作物添加产出产品后再提交");
        }
        writeFreshVegStockIn(requireFreshVegLocationId(), productId, bo.getPlotId(), null,
            weight, userId, now, "采摘去向[毛菜保鲜室]入库 crop=" + bo.getCropName());
    }

    /**
     * 采摘去向[损耗]：写统一损耗台账 loss_flow（loss_type=veg_handle_loss，与毛菜处理损耗同类型）。
     */
    private void insertPickLoss(PickDestSubmitBo bo, BigDecimal weight, Long userId) {
        LossFlow lossFlow = new LossFlow();
        lossFlow.setLossType("veg_handle_loss");
        lossFlow.setLossWeight(weight);
        lossFlow.setProductId(resolveProductIdByCrop(bo.getCropId(), bo.getProductId()));
        lossFlow.setPlotId(bo.getPlotId());
        lossFlow.setBelongType(CROP_BELONG_TYPE);
        lossFlow.setSourceBizType("pick_dest");
        lossFlow.setOperatorId(userId);
        lossFlowService.record(lossFlow);
    }

    /**
     * 采摘去向[饲料饲喂]：写饲料台账 feed_log（feed_type=veg_handle，不记地块）。
     *
     * <p>row54：产品取工人选的那个（{@link #pickProductId}），不按作物反解——同 {@link #insertFeedLog}。</p>
     */
    private void insertPickFeed(PickDestSubmitBo bo, BigDecimal weight, Long userId, Date now) {
        FeedLog feedLog = new FeedLog();
        feedLog.setFeedDate(now);
        feedLog.setCropId(bo.getCropId());
        feedLog.setCropName(bo.getCropName());
        feedLog.setFeedType("veg_handle");
        feedLog.setProductId(pickProductId(bo));
        feedLog.setOperatorId(userId);
        feedLog.setFeedWeight(weight);
        feedLogMapper.insert(feedLog);
    }

    /**
     * 采摘活动各去向的产品口径：<b>工人选了哪个就是哪个</b>，没选才按作物的 {@code related_product} 兜底。
     *
     * <p>原来写的是 {@code resolveProductIdByCrop(cropId, bo.getProductId())} —— 那个方法把入参当
     * 「兜底值」，只要作物配了 {@code related_product} 就直接返回它、把工人选的产品丢掉。</p>
     *
     * <p>⚠️ <b>这一支目前是「备而未用」</b>：唯一调用链 {@code submitPickActivity} 在构造 BO 时就写死了
     * {@code destBo.setProductId(resolveProductIdByCrop(cropId, null))}，而 {@code PickActivitySubmitBo}
     * 根本没有 productId 字段、mp 采摘活动录入页也没有产品选择器 —— 所以 {@code bo.getProductId()}
     * 恒等于作物默认产品，新旧行为完全一致。也就是说：<b>采摘活动直送饲料 / 直送月台这两条路，
     * 记的仍然是作物默认产品</b>，多产品作物在有机饲喂记录（row54）和果蔬月台分卡（row55）上依旧会
     * 退化成作物名。要真正打通得三处一起改：BO 加字段 + mp 加产品选择 + 去掉 submitPickActivity 里的
     * 反解覆盖 —— 那是新增能力，不在本轮范围，已留痕给甲方决定。现网 0 行受影响
     * （现有饲喂/月台记录全部来自毛菜处理 {@code submitProcess} 那条链路，产品是对的）。</p>
     *
     * <p>🔴 <b>真把 productId 打通时，必须同时在这里加「产品属于该作物配置」的守门</b>
     * （照 {@link #resolveRecordProductId} 那道，它会对不属于的选择抛 400）。理由：这条链路写出的
     * {@code handle_record} 就是果蔬月台卡的数据源，而收货侧对不属于该作物配置的产品是硬拒的 ——
     * 上游不把门就会造出一张点进去收不了的卡，货永久卡在月台。
     * 现在<b>没有</b>加这道门是有意的：{@code bo.getProductId()} 眼下并非客户端输入、而是调用方自己
     * 预解析出来的作物默认产品，对它做「必须在配置里」的校验防不到任何真实入口，只会在
     * {@code related_product} 与产品配置不一致时制造误拒（该不变量无 DB 约束、纯靠数据凑巧成立）。</p>
     */
    private Long pickProductId(PickDestSubmitBo bo) {
        return bo.getProductId() != null ? bo.getProductId() : resolveProductIdByCrop(bo.getCropId(), null);
    }

    /**
     * 去向③饲料饲喂 → 插入饲料台账（{@code t_warehouse_feed_log}，spec 步8）。
     *
     * <p>按自然日 × 作物品类记录重量，{@code 不记录地块编号}（无 plot_id）。tenant_id 走 MP 自动 fill。</p>
     *
     * <p>行64 来源①「毛菜间」：feed_type=veg_handle；operatorId = 当前处理操作人。</p>
     *
     * <p><b>row54</b>：{@code productId} 用调用方传进来的 {@code selectedProductId}
     * （= 工人在处理录入里选的那个处理产品，已过 {@link #resolveRecordProductId} 的作物-产品配置校验），
     * <b>不再</b>在这里用 {@code resolveProductIdByCrop} 按作物二次反解 —— 那个方法只要作物配了
     * {@code related_product} 就一律返回它、无视传入值，于是红薯杆的饲喂会被记成红薯。
     * 一个作物可以有多个产品（红薯 / 红薯杆），有机饲喂记录新增的「产品名称」列正是要区分它们，
     * 反解会让整列退化成作物名、等于没加。入库那一路（{@code insertVegStockInFlow}）本来就传的是它。</p>
     */
    private void insertFeedLog(PlantingRecord planting, BigDecimal weight, Long operatorId, Date now,
                              Long selectedProductId) {
        FeedLog feedLog = new FeedLog();
        feedLog.setFeedDate(now);
        feedLog.setCropId(planting.getCropId());
        feedLog.setCropName(planting.getCropName());
        feedLog.setFeedType("veg_handle");
        feedLog.setProductId(selectedProductId);
        feedLog.setOperatorId(operatorId);
        feedLog.setFeedWeight(weight);
        feedLogMapper.insert(feedLog);
    }

    @Override
    public TableDataInfo<VegetableHandleVo> queryPageList(VegHandleQuery query, PageQuery pageQuery) {
        Page<VegetableHandleVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(query));
        fillPlantingNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<VegetableHandleVo> queryList(VegHandleQuery query) {
        List<VegetableHandleVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillPlantingNames(list);
        return list;
    }

    @Override
    public VegetableHandleVo queryById(Long id) {
        VegetableHandleVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillPlantingNames(List.of(vo));
        }
        return vo;
    }

    @Override
    public List<HandleRecordVo> listRecords(Long handleId) {
        return handleRecordMapper.selectVoList(
            new LambdaQueryWrapper<HandleRecord>()
                .eq(HandleRecord::getHandleId, handleId)
                .orderByAsc(HandleRecord::getHandleTime));
    }

    @Override
    public TableDataInfo<HandleRecordVo> myRecords(PageQuery pageQuery) {
        Long userId = LoginHelper.getUserId();
        Page<HandleRecordVo> page = handleRecordMapper.selectVoPage(pageQuery.build(),
            new LambdaQueryWrapper<HandleRecord>()
                .eq(HandleRecord::getHandleUser, userId)
                .orderByDesc(HandleRecord::getHandleTime));
        return TableDataInfo.build(page);
    }

    /**
     * 用 planting_record 冗余 plot_name / crop_name 回填 VO（避免跨模块依赖 plant）。
     */
    private void fillPlantingNames(List<VegetableHandleVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> plantingIds = records.stream()
            .map(VegetableHandleVo::getPlantingRecordId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (plantingIds.isEmpty()) {
            return;
        }
        List<PlantingRecord> plantings = plantingRecordMapper.selectList(
            new LambdaQueryWrapper<PlantingRecord>().in(PlantingRecord::getId, plantingIds));
        Map<Long, PlantingRecord> map = plantings.stream()
            .collect(Collectors.toMap(PlantingRecord::getId, p -> p, (a, b) -> a));
        for (VegetableHandleVo vo : records) {
            if (vo.getPlantingRecordId() != null) {
                PlantingRecord p = map.get(vo.getPlantingRecordId());
                if (p != null) {
                    vo.setPlotName(p.getPlotName());
                    vo.setCropName(p.getCropName());
                }
            }
        }
    }

    private LambdaQueryWrapper<VegetableHandle> buildQueryWrapper(VegHandleQuery query) {
        LambdaQueryWrapper<VegetableHandle> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(VegetableHandle::getId);
        }
        boolean hasHandleStatuses = query.getHandleStatuses() != null && !query.getHandleStatuses().isEmpty();
        wrapper.eq(query.getPlotId() != null, VegetableHandle::getPlotId, query.getPlotId())
            .eq(query.getCropId() != null, VegetableHandle::getCropId, query.getCropId())
            .eq(query.getPlantingRecordId() != null, VegetableHandle::getPlantingRecordId, query.getPlantingRecordId())
            .in(hasHandleStatuses, VegetableHandle::getHandleStatus, query.getHandleStatuses())
            .eq(!hasHandleStatuses && query.getHandleStatus() != null && !query.getHandleStatus().isBlank(),
                VegetableHandle::getHandleStatus, query.getHandleStatus())
            .ge(query.getPickStartTimeFrom() != null, VegetableHandle::getPickStartTime, query.getPickStartTimeFrom())
            .le(query.getPickStartTimeTo() != null, VegetableHandle::getPickStartTime, query.getPickStartTimeTo())
            .orderByDesc(VegetableHandle::getId);
        return wrapper;
    }

    @Override
    public TableDataInfo<PickDetailVo> queryPickDetailPage(PickDetailQuery query, PageQuery pageQuery) {
        PickDetailQuery q = query == null ? new PickDetailQuery() : query;
        Page<PickDetailVo> page = handleRecordMapper.selectPickDetailPage(pageQuery.build(), q);
        return TableDataInfo.build(page);
    }

    @Override
    public void exportPickDetail(PickDetailQuery query, HttpServletResponse response) {
        PickDetailQuery q = query == null ? new PickDetailQuery() : query;
        List<PickDetailVo> list = handleRecordMapper.selectPickDetailList(q);
        ExcelUtil.exportExcel(list, "采摘明细", PickDetailVo.class, response);
    }

}
