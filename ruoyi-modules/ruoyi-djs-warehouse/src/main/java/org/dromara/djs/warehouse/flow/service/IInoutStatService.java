package org.dromara.djs.warehouse.flow.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.flow.domain.query.InoutStatQuery;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatInVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatOutVo;

import java.util.List;

/**
 * 出入库统计 Service（V6-R167 入库统计 / 出库统计两个 Tab）。
 *
 * <p>compute-on-read：按日期区间实时 GROUP BY 既有出入库流水，无汇总表、无跑批。</p>
 *
 * <p>每个 Tab 两个方法：列表分页 + 导出全量，都吃同一个 query，
 * 走 mapper 里同一份 SQL，保证「导出的表 = 页面上翻完的所有页」。</p>
 *
 * @author djs
 * @since V6-R167
 */
public interface IInoutStatService {

    /**
     * 入库统计分页（产品 × 入库方式 × 供应商）。
     *
     * @param query     筛选条件（可为 null，等价不带任何筛选）
     * @param pageQuery 分页参数
     * @return 入库统计分页（字典 label 已翻译、空值已兜底）
     */
    TableDataInfo<InoutStatInVo> queryInPage(InoutStatQuery query, PageQuery pageQuery);

    /**
     * 入库统计全量（导出用，筛选条件与列表一致）。
     *
     * @param query 筛选条件（可为 null）
     * @return 入库统计行
     */
    List<InoutStatInVo> queryInList(InoutStatQuery query);

    /**
     * 出库统计分页（产品 × 出库去向）。
     *
     * @param query     筛选条件（可为 null）
     * @param pageQuery 分页参数
     * @return 出库统计分页（字典 label 已翻译、空值已兜底）
     */
    TableDataInfo<InoutStatOutVo> queryOutPage(InoutStatQuery query, PageQuery pageQuery);

    /**
     * 出库统计全量（导出用）。
     *
     * @param query 筛选条件（可为 null）
     * @return 出库统计行
     */
    List<InoutStatOutVo> queryOutList(InoutStatQuery query);
}
