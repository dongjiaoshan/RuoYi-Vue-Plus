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
import org.dromara.djs.common.store.service.IStoreService;
import org.dromara.djs.store.demand.core.StoreDemandViewEnricher;
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
 *   <li>{@link #createStoreDemand} 单产品「门店发起」= 门店合作状态闸 + 自产产品闸（{@link #assertSellableProduct}）
 *       + insertByBo（warehouse 侧已直接落 SUBMITTED，不再二次 transition）</li>
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

    /** 产品类型字典 djs_product_type：自产（含礼盒）——门店唯一可下单的产品类型。 */
    private static final Integer PRODUCT_TYPE_SELF_PRODUCED = 1;

    private final IDemandManageService demandManageService;

    private final ProductInfoMapper productInfoMapper;

    private final DemandManageMapper demandManageMapper;

    private final ProductProductionMapper productProductionMapper;

    private final IProductProductionService productProductionService;

    private final ITraceService traceService;

    private final IStoreService storeService;

    private final StoreDemandViewEnricher viewEnricher;

    @Override
    public TableDataInfo<DemandManageVo> queryStoreList(DemandManageQuery query, PageQuery pageQuery) {
        TableDataInfo<DemandManageVo> page = demandManageService.queryPageList(query, pageQuery);
        // 预计到店量 + 计损量：与 mp 按天明细（queryDayDetail）共用同一份口径，见 StoreDemandViewEnricher
        viewEnricher.enrich(page.getRows());
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStoreDemand(DemandManageBo bo) {
        // 已终止合作门店禁止下单（覆盖单条 + batchCreate 逐条路径）
        storeService.assertStoreActive(bo.getStoreId());
        // 只放行自产产品（覆盖 admin 单条 / admin 购物车整单 / mp 三个创建入口）
        assertSellableProduct(bo.getProductId());
        // 编辑路径不走本方法（门店端创建专用）；强制清空 id 避免误更新
        bo.setId(null);
        // warehouse insertByBo 已直接落 SUBMITTED（门店发起 = 提交需求给仓库，无独立存草稿环节，
        // 与 admin 仓库侧 add 同契约）+ 自动生成 demand_no + 业态字段校验，无需再 transition(SUBMIT)。
        return demandManageService.insertByBo(bo);
    }

    /**
     * 门店可下单产品闸：只放行自产产品（{@code product_type=1}，含礼盒）。
     *
     * <p>外购商品（{@code product_type=2}）是生产投入品——种子 / 药品 / 农药 / 肥料 / 饲料 / 包材 / 设备，
     * 走采购入库 → 物资领用消耗，不是门店可售商品（doc/14 §5）。前端候选已按 {@code productTypes=[1]} 收口，
     * 本闸兜底挡住直接打接口、以及产品属性未配置的历史脏数据绕过下单。</p>
     *
     * @param productId 产品主键（{@code t_warehouse_product_info.id}）；为空时交由 BO 必填校验报错
     */
    private void assertSellableProduct(Long productId) {
        if (productId == null) {
            return;
        }
        ProductInfo product = productInfoMapper.selectById(productId);
        if (product == null) {
            throw new ServiceException("产品不存在或已删除：" + productId, 404);
        }
        if (!PRODUCT_TYPE_SELF_PRODUCED.equals(product.getProductType())) {
            throw new ServiceException("「" + product.getProductName() + "」是外购商品，不可被门店下单", 400);
        }
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
