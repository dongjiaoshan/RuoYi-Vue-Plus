package org.dromara.djs.store.manage.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.djs.common.store.domain.vo.StorePickerVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageDetailVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageMonthlyVo;

import java.util.List;

/**
 * 管理板块「门店管理」月度看板 Service（MGMT-MP-STORE-MONTH-001 / V6-R180）。
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
     * 月度看板：3 个品类数 + 业态卡（每卡按单位分行，每行 3 指标 + 环比）。
     *
     * <p>业态卡最多 5 张（猪肉 / 果蔬 / 蛋类 / 干货 / 其他），<b>当月无数据的卡不下发</b>（D-0045）。</p>
     *
     * @param storeId 门店 ID；null = 全部门店合计
     * @param month   月份 yyyy-MM；空 = 当月
     * @return 月度看板 VO
     */
    StoreManageMonthlyVo getMonthly(Long storeId, String month);

    /**
     * 业态卡「明细」下钻：该业态当月按<b>产品</b>拆的需求 / 销售 / 退回三个量 + 按单位的全量合计。
     *
     * @param storeId    门店 ID；null = 全部门店合计
     * @param month      月份 yyyy-MM；空 = 当月（格式非法 400）
     * @param belongType 业态卡 key：pork / vegetable / egg / dry_good / other（白名单外 400）
     * @param pageQuery  分页参数
     * @return 明细分页 + 合计（合计与业态卡同口径同数字）
     */
    StoreManageDetailVo getDetail(Long storeId, String month, String belongType, PageQuery pageQuery);

}
