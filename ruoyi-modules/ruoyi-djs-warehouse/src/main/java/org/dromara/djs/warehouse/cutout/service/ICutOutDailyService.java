package org.dromara.djs.warehouse.cutout.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.cutout.domain.query.CutOutDailyDetailQuery;
import org.dromara.djs.warehouse.cutout.domain.query.CutOutDailyQuery;
import org.dromara.djs.warehouse.cutout.domain.vo.CutOutDailyDetailVo;
import org.dromara.djs.warehouse.cutout.domain.vo.CutOutDailyVo;

import java.util.List;

/**
 * 猪肉日分割出库统计 Service（V7-R6）。
 *
 * <p>compute-on-read：按天实时聚合白条分割出库流水与门店需求产出记录，无汇总表、无跑批。</p>
 *
 * @author djs
 * @since V7-R6
 */
public interface ICutOutDailyService {

    /**
     * 列表分页：仓库出库按 日 × 出库去向、门店需求按 日 × 门店，日期倒序。
     *
     * @param query     筛选（统计日期区间 / 出库去向 / 出库门店）
     * @param pageQuery 分页参数
     * @return 列表行（去向 label、门店名已回填）
     */
    TableDataInfo<CutOutDailyVo> queryPage(CutOutDailyQuery query, PageQuery pageQuery);

    /**
     * 「查看」明细：被点击那一行的逐条出库记录（抽屉列表与导出共用）。
     *
     * @param query statDate + destType + outDest / storeId
     * @return 明细行（出库时间倒序）
     */
    List<CutOutDailyDetailVo> queryDetail(CutOutDailyDetailQuery query);
}
