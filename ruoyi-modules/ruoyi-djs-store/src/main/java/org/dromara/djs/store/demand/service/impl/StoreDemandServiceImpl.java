package org.dromara.djs.store.demand.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.store.demand.domain.bo.StoreDemandBatchBo;
import org.dromara.djs.store.demand.service.IStoreDemandService;
import org.dromara.djs.warehouse.demand.core.enums.DemandStatus;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.domain.query.DemandManageQuery;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.demand.service.IDemandManageService;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.pack.service.IProductProductionService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.trace.domain.TraceContentConst;
import org.dromara.djs.warehouse.trace.service.ITraceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 门店端需求服务实现（STR-DEMAND-001 + STORE-DEMAND-REALIGN-001，薄封装复用 WMS demand）。
 *
 * <p>不写状态机 / 编码 / 业态校验——全部复用 warehouse service。本类职责：</p>
 * <ul>
 *   <li>{@link #createStoreDemand} 单产品「门店发起」= insertByBo（warehouse 侧已直接落 SUBMITTED，不再二次 transition）</li>
 *   <li>{@link #batchCreate} 购物车整单：逐项补产品冗余字段后循环 createStoreDemand</li>
 *   <li>{@link #receive} 门店收货确认：patch received_time / received_by（不触碰仓库状态机）+ 写
 *       追溯链「到店」(arrival) 节点（按 demand_id 反查已发货产品 trace_code 逐条 recordEvent）</li>
 * </ul>
 *
 * @author djs
 * @since STR-DEMAND-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreDemandServiceImpl implements IStoreDemandService {

    /** 需求类型字典 djs_demand_mailing_type：门店。 */
    private static final String DEMAND_TYPE_STORE = "store";

    /** 需求类型字典 djs_demand_mailing_type：个人邮寄。 */
    private static final String DEMAND_TYPE_MAILING = "mailing";

    private final IDemandManageService demandManageService;

    private final ProductInfoMapper productInfoMapper;

    private final DemandManageMapper demandManageMapper;

    private final ProductProductionMapper productProductionMapper;

    private final IProductProductionService productProductionService;

    private final ITraceService traceService;

    /** 门店视角派生状态：已发货（{@code djs_store_demand_status} 之一）。 */
    private static final String STORE_STATUS_SHIPPED = "SHIPPED";

    /** 门店视角派生状态：确认到店（{@code djs_store_demand_status} 之一，SHIPPED 收货后）。 */
    private static final String STORE_STATUS_ARRIVED = "ARRIVED";

    /** 自产归属类型（djs_belong_type）：果蔬 / 猪肉 / 白条 —— 预计到店重量口径分流。 */
    private static final String BELONG_VEGETABLE = "vegetable";
    private static final String BELONG_PORK = "pork";
    private static final String BELONG_WHITE_BAR = "white_bar";

    @Override
    public TableDataInfo<DemandManageVo> queryStoreList(DemandManageQuery query, PageQuery pageQuery) {
        TableDataInfo<DemandManageVo> page = demandManageService.queryPageList(query, pageQuery);
        fillDamagedCount(page.getRows());
        fillExpectedWeight(page.getRows());
        return page;
    }

    /**
     * 回填「预计到店重量」{@code expectedWeight}（admin row5，kg，前端按 productType 换单位展示）。
     *
     * <p>按产品自产归属类型（belong_type，经 product_id JOIN 商品主数据取）分流：</p>
     * <ul>
     *   <li>果蔬(vegetable) / 猪肉(pork)：{@code 需求量 × 规格净重(kg)}（规格如 500g/包 → 0.5kg）；前端 ×1000 展示 g。</li>
     *   <li>白条(white_bar)：该需求已发货白条实际重量之和（kg，Kevin 2026-07-07 定 a 方案）；前端 kg 不换算。</li>
     *   <li>其余（鸡蛋 / 物资 / 礼盒 / 规格非重量）：不回填（{@code null}），前端展示 '—'。</li>
     * </ul>
     * compute-on-read：不持久化（白条已发货重随发货变、果蔬量随需求量算），与 {@link #fillDamagedCount} 同批。
     */
    private void fillExpectedWeight(List<DemandManageVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> productIds = rows.stream().map(DemandManageVo::getProductId)
            .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> belongByProduct = new HashMap<>();
        if (!productIds.isEmpty()) {
            List<ProductInfo> products = productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .in(ProductInfo::getId, productIds)
                .select(ProductInfo::getId, ProductInfo::getBelongType));
            for (ProductInfo p : products) {
                belongByProduct.put(p.getId(), p.getBelongType());
            }
        }
        for (DemandManageVo vo : rows) {
            if (vo.getExpectedWeight() != null || vo.getProductId() == null) {
                continue;
            }
            String belong = belongByProduct.get(vo.getProductId());
            if (BELONG_WHITE_BAR.equals(belong)) {
                BigDecimal w = productProductionMapper.sumShippedWhiteBarWeightByDemand(vo.getId());
                if (w != null && w.signum() > 0) {
                    vo.setExpectedWeight(w);
                }
            } else if (BELONG_VEGETABLE.equals(belong) || BELONG_PORK.equals(belong)) {
                // row49/51：果蔬 / 猪肉预计到店重量改按「已发货实际称重之和」（kg），非规格×需求量估算。
                // 刚下单待确认时无发货清点行 → 0 → 不回填（前端 '—'），发货后按实际称重回填。
                BigDecimal w = productProductionMapper.sumShippedVegPorkWeightByDemand(vo.getId());
                if (w != null && w.signum() > 0) {
                    vo.setExpectedWeight(w);
                }
            }
            // 其余 belong（egg / dry_good / gift_box / 外购商品 / null）→ 不回填，前端 '—'
        }
    }

    /**
     * 回填「损坏数量」{@code damagedCount}（row48 + admin row6）：对门店派生状态为「已发货」(SHIPPED)
     * 或「确认到店」(ARRIVED) 的行，调 warehouse {@link IProductProductionService#countDamagedByDemand}
     * 按 demand_id 统计损坏件数；其余行保持 {@code null}（前端展示 '—'）。
     *
     * <p>admin row6：确认到店后行由 SHIPPED 派生成 ARRIVED，损坏数量不因收货而清零（损坏件是产品生产明细
     * is_damaged=1 的固有属性，与收货无关），故收货后仍按同口径回填，不再漏算成 0。</p>
     *
     * <p>当前逐行查询（门店端单店分页数据量小，契约 c 为 COUNT 单点查询）。若后续单店需求行数显著增大，
     * 可在 warehouse 侧加批量 {@code countDamagedByDemandBatch(demandIds)} 一次聚合替代，去 N+1。</p>
     */
    private void fillDamagedCount(List<DemandManageVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (DemandManageVo vo : rows) {
            String s = vo.getStoreDemandStatus();
            if (vo.getId() != null && (STORE_STATUS_SHIPPED.equals(s) || STORE_STATUS_ARRIVED.equals(s))) {
                vo.setDamagedCount((int) productProductionService.countDamagedByDemand(vo.getId()));
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStoreDemand(DemandManageBo bo) {
        // 编辑路径不走本方法（门店端创建专用）；强制清空 id 避免误更新
        bo.setId(null);
        // warehouse insertByBo 已直接落 SUBMITTED（门店发起 = 提交需求给仓库，无独立存草稿环节，
        // 与 admin 仓库侧 add 同契约）+ 自动生成 demand_no + 业态字段校验，无需再 transition(SUBMIT)。
        return demandManageService.insertByBo(bo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchCreate(StoreDemandBatchBo bo) {
        LocalDate demandDate = bo.getDemandDate() == null ? LocalDate.now() : bo.getDemandDate();
        int created = 0;
        for (StoreDemandBatchBo.Item item : bo.getItems()) {
            ProductInfo product = productInfoMapper.selectById(item.getProductId());
            if (product == null) {
                throw new ServiceException("产品不存在或已删除：" + item.getProductId(), 404);
            }
            DemandManageBo demandBo = new DemandManageBo();
            demandBo.setDemandDate(demandDate);
            demandBo.setStoreId(bo.getStoreId());
            demandBo.setProductId(item.getProductId());
            // 冗余产品名 / 规格 / 单位（列表展示不再 JOIN）
            demandBo.setProductName(product.getProductName());
            demandBo.setProductSpec(product.getProductSpec());
            demandBo.setProductUnit(product.getProductUnit());
            demandBo.setProductType(item.getProductType());
            demandBo.setDemandQuantity(item.getDemandQuantity());
            demandBo.setExpectedArriveDate(bo.getExpectedArriveDate());
            demandBo.setDemandRemark(bo.getDemandRemark());
            // 个人邮寄标记 → demand_type
            demandBo.setDemandType(Boolean.TRUE.equals(item.getMailing()) ? DEMAND_TYPE_MAILING : DEMAND_TYPE_STORE);
            createStoreDemand(demandBo);
            created++;
        }
        log.info("[STORE-DEMAND-REALIGN-001] batchCreate store={} 行数={} → 逐条 SUBMITTED",
            bo.getStoreId(), created);
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long id) {
        DemandManage demand = demandManageMapper.selectById(id);
        if (demand == null) {
            throw new ServiceException("需求不存在或已删除：" + id, 404);
        }
        if (demand.getReceivedTime() != null) {
            throw new ServiceException("该需求已确认到店，请勿重复确认", 400);
        }
        // 原型 0613-04 点4：「确认收货」按钮仅在「已发货」行展示。
        // 门店视角「已发货」= 仓库侧 PARTIAL_SHIPPED / COMPLETED；点确认收货后 received_time 置位，
        // 门店派生状态算成「确认到店」(ARRIVED)。不触碰仓库状态机（薄封装铁律）。
        String status = demand.getDemandStatus();
        if (!DemandStatus.PARTIAL_SHIPPED.name().equals(status) && !DemandStatus.COMPLETED.name().equals(status)) {
            throw new ServiceException("仅「已发货」状态的需求可确认到店，当前状态：" + status, 400);
        }
        // 仅 patch 收货字段，不触碰仓库状态机 / 业务字段
        DemandManage patch = new DemandManage();
        patch.setId(id);
        patch.setReceivedTime(LocalDateTime.now());
        patch.setReceivedBy(LoginHelper.getUserId());
        demandManageMapper.updateById(patch);
        log.info("[STORE-DEMAND-REALIGN-001] receive demand id={} no={} by={}",
            id, demand.getDemandNo(), LoginHelper.getUserId());

        // 追溯链「到店」节点（7 节点闭环最后一节）：本次收货 demand 下已发货产品逐条写 arrival 事件。
        recordArrivalTrace(id);
    }

    /**
     * 给本次收货 demand 下已发货产品写 {@code arrival} 追溯事件（追溯链 7 节点最后一节）。
     *
     * <p>关联结构：{@code t_warehouse_product_production.demand_id} = 本次发货确认时由
     * {@link org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper#markDeliveryChecked} 绑定，
     * 故按 {@code demand_id} 反查该需求下 {@code trace_code} 非空的 production 即为到店产品集
     * （与 {@link org.dromara.djs.warehouse.trace.listener.ShipTraceEventListener} 反查 ship 节点同口径，
     * 仅事件类型从 ship 改 arrival）。</p>
     *
     * <p>追溯写失败不影响收货确认（收货事务已经 patch 完成）：整体 try-catch swallow + log，
     * 单条 produce_code 重量取 {@code productWeight}（同 ship 节点）。trace_code 为空
     * （如猪肉链当前无生成入口）的 production 跳过。</p>
     */
    private void recordArrivalTrace(Long demandId) {
        try {
            List<ProductProduction> productions = productProductionMapper.selectList(
                new LambdaQueryWrapper<ProductProduction>()
                    .eq(ProductProduction::getDemandId, demandId)
                    .isNotNull(ProductProduction::getTraceCode));
            int recorded = 0;
            for (ProductProduction p : productions) {
                if (StringUtils.isNotBlank(p.getTraceCode())) {
                    traceService.recordEvent(p.getTraceCode(), TraceContentConst.ARRIVAL, p.getProductWeight());
                    recorded++;
                }
            }
            log.info("[STORE-DEMAND-REALIGN-001] arrival trace events recorded demandId={} count={}",
                demandId, recorded);
        } catch (Exception e) {
            log.warn("[STORE-DEMAND-REALIGN-001] arrival trace event failed (skipped) demandId={}: {}",
                demandId, e.getMessage());
        }
    }
}
