package org.dromara.djs.warehouse.flow.service;

import org.dromara.djs.warehouse.flow.domain.query.InoutSummaryQuery;
import org.dromara.djs.warehouse.flow.domain.vo.InoutMonthVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutSummaryInVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutSummaryOutVo;

import java.util.List;

/**
 * 出入库月汇总 Service（V6-R154 列表 / R155 入库汇总 / R156 出库汇总）。
 *
 * <p>compute-on-read：按月实时 GROUP BY 既有出入库流水，无汇总表、无跑批。</p>
 *
 * @author djs
 * @since V6-R154
 */
public interface IInoutMonthlyService {

    /**
     * 有出入库流水的月份列表（倒序）。
     *
     * @param statMonth 月份精确筛选 yyyy-MM（可空 = 全部）
     * @return 月份行
     */
    List<InoutMonthVo> queryMonths(String statMonth);

    /**
     * 当月入库汇总（产品 × 入库方式 × 供应商）。
     *
     * @param query 筛选条件（statMonth 必填）
     * @return 入库汇总行（字典 label 已翻译、空值已兜底）
     */
    List<InoutSummaryInVo> queryInSummary(InoutSummaryQuery query);

    /**
     * 当月出库汇总（产品 × 出库去向）。
     *
     * @param query 筛选条件（statMonth 必填）
     * @return 出库汇总行（字典 label 已翻译、空值已兜底）
     */
    List<InoutSummaryOutVo> queryOutSummary(InoutSummaryQuery query);
}
