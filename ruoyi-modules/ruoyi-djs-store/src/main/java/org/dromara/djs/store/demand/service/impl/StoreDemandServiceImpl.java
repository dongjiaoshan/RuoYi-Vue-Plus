package org.dromara.djs.store.demand.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.store.demand.domain.bo.StoreDemandBatchBo;
import org.dromara.djs.store.demand.service.IStoreDemandService;
import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.core.enums.DemandStatus;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.demand.service.IDemandManageService;
import org.dromara.djs.warehouse.demand.service.IDemandStatusService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门店端需求服务实现（STR-DEMAND-001 + STORE-DEMAND-REALIGN-001，薄封装复用 WMS demand）。
 *
 * <p>不写状态机 / 编码 / 业态校验——全部复用 warehouse service。本类职责：</p>
 * <ul>
 *   <li>{@link #createStoreDemand} 单产品「门店发起」收口为 insertByBo + transition(SUBMIT) 原子事务（落 SUBMITTED）</li>
 *   <li>{@link #batchCreate} 购物车整单：逐项补产品冗余字段后循环 createStoreDemand</li>
 *   <li>{@link #receive} 门店收货确认：patch received_time / received_by（不触碰仓库状态机）</li>
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

    private final IDemandStatusService demandStatusService;

    private final ProductInfoMapper productInfoMapper;

    private final DemandManageMapper demandManageMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStoreDemand(DemandManageBo bo) {
        // 编辑路径不走本方法（门店端创建专用）；强制清空 id 避免误更新
        bo.setId(null);
        // 第 1 步：warehouse 落库（初始 DRAFT + 自动生成 demand_no + 业态字段校验）
        Long id = demandManageService.insertByBo(bo);
        // 第 2 步：立即提交，推到 SUBMITTED（门店发起跳过 DRAFT，doc/10 §4.F-STR-01）
        demandStatusService.transition(id, DemandEvent.SUBMIT, LoginHelper.getUserId(), "门店发起");
        return id;
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
            throw new ServiceException("该需求已确认收货，请勿重复确认", 400);
        }
        // 原型「确认收货」按钮仅在「已确认」行显示：仅 CONFIRMED 态可门店收货确认
        if (!DemandStatus.CONFIRMED.name().equals(demand.getDemandStatus())) {
            throw new ServiceException("仅「已确认」状态的需求可确认收货，当前状态：" + demand.getDemandStatus(), 400);
        }
        // 仅 patch 收货字段，不触碰仓库状态机 / 业务字段
        DemandManage patch = new DemandManage();
        patch.setId(id);
        patch.setReceivedTime(LocalDateTime.now());
        patch.setReceivedBy(LoginHelper.getUserId());
        demandManageMapper.updateById(patch);
        log.info("[STORE-DEMAND-REALIGN-001] receive demand id={} no={} by={}",
            id, demand.getDemandNo(), LoginHelper.getUserId());
    }
}
