package org.dromara.djs.store.manage.service;

import org.dromara.djs.common.store.domain.vo.StorePickerVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageMonthlyVo;

import java.util.List;

/**
 * 管理板块「门店管理」月度看板 Service（MGMT-MP-STORE-MONTH-001）。
 *
 * @author djs
 * @since MGMT-MP-STORE-MONTH-001
 */
public interface IStoreManageService {

    /**
     * 门店下拉候选（管理者视角，「全部」由前端在列表首位补，后端不返伪门店）。
     *
     * @return 门店精简列表，无门店返空列表
     */
    List<StorePickerVo> listSelectableStores();

    /**
     * 月度看板：3 个品类数 + 4 张业态卡（每卡按单位分行，每行 3 指标 + 环比）。
     *
     * @param storeId 门店 ID；null = 全部门店合计
     * @param month   月份 yyyy-MM；空 = 当月
     * @return 月度看板 VO
     */
    StoreManageMonthlyVo getMonthly(Long storeId, String month);

}
