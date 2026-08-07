package org.dromara.djs.warehouse.burn.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.burn.domain.bo.BurnInhouseAdjustBo;
import org.dromara.djs.warehouse.burn.domain.query.BurnInhouseAdjustQuery;
import org.dromara.djs.warehouse.burn.domain.vo.BurnInhouseAdjustVo;

/**
 * 燎毛间产品重量调整 Service（V6-R43，admin 数据管理）。
 *
 * @author djs
 * @since V6-R43
 */
public interface IBurnInhouseAdjustService {

    /**
     * 分页查询燎毛间产品入库记录。
     *
     * @param query     筛选条件（入库日期区间 / 产品名模糊 / 是否调整）
     * @param pageQuery 分页
     * @return 分页列表
     */
    TableDataInfo<BurnInhouseAdjustVo> queryPageList(BurnInhouseAdjustQuery query, PageQuery pageQuery);

    /**
     * 调整某条燎毛间产品入库记录的入库重量，并把差额同步到全部下游台账。
     *
     * <p>语义是<b>改写历史</b>（甲方口径：「相当于产品是按修改完成后的重量入的库」），不是补冲正流水。
     * 仅在该猪只燎毛间<b>尚未点「处理完成」</b>时允许。</p>
     *
     * @param bo 入库行主键 + 调整后重量
     */
    void adjustWeight(BurnInhouseAdjustBo bo);

}
