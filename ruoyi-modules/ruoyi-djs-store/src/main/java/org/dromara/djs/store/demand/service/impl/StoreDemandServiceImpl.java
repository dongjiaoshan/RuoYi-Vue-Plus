package org.dromara.djs.store.demand.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.store.demand.service.IStoreDemandService;
import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.service.IDemandManageService;
import org.dromara.djs.warehouse.demand.service.IDemandStatusService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 门店端需求服务实现（STR-DEMAND-001，薄封装复用 WMS demand）。
 *
 * <p>不写状态机 / 编码 / 业态校验——全部复用 warehouse service。本类唯一职责：
 * 把「门店发起需求」收口为「insertByBo + transition(SUBMIT)」一个原子事务，让门店端创建的
 * 需求直接落到 {@code SUBMITTED} 态（跳过 DRAFT）。</p>
 *
 * @author djs
 * @since STR-DEMAND-001
 */
@Service
@RequiredArgsConstructor
public class StoreDemandServiceImpl implements IStoreDemandService {

    private final IDemandManageService demandManageService;

    private final IDemandStatusService demandStatusService;

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
}
