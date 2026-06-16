package org.dromara.djs.store.demand.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.store.demand.domain.bo.StoreDemandBatchBo;
import org.dromara.djs.store.demand.service.IStoreDemandService;
import org.dromara.djs.warehouse.demand.core.enums.DemandStatus;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.demand.service.IDemandManageService;
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
 *   <li>{@link #createStoreDemand} 单产品「门店发起」= insertByBo（warehouse 侧已直接落 SUBMITTED，不再二次 transition）</li>
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

    private final ProductInfoMapper productInfoMapper;

    private final DemandManageMapper demandManageMapper;

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
    }
}
