package org.dromara.djs.warehouse.flow.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.flow.domain.query.InoutStatDetailQuery;
import org.dromara.djs.warehouse.flow.domain.query.InoutStatQuery;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatInDetailVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatInVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatOutDetailVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatOutVo;

import java.util.List;

/**
 * 出入库统计 Service（V6-R167 入库统计 / 出库统计两个 Tab）。
 *
 * <p>compute-on-read：按日期区间实时 GROUP BY 既有出入库流水，无汇总表、无跑批。</p>
 *
 * <p>每个 Tab 四个方法：列表分页 + 导出全量 + 行内明细分页 + 明细导出全量。
 * 前两个吃同一个 query 走 mapper 同一份 SQL，保证「导出的表 = 页面上翻完的所有页」；
 * 后两个共用汇总的 FROM / WHERE 再叠分组键，保证「明细逐条求和 = 汇总行的量」（V6-R186）。</p>
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

    /**
     * 入库明细分页（V6-R186 入库统计行「查看详情」）。
     *
     * @param query     分组键（产品编码 / 入库方式 / 供应商）+ 列表原筛选 + 明细自己的日期区间与记录人
     * @param pageQuery 分页参数
     * @return 入库明细分页（量已拼单位、空值已兜底）
     */
    TableDataInfo<InoutStatInDetailVo> queryInDetailPage(InoutStatDetailQuery query, PageQuery pageQuery);

    /**
     * 入库明细全量（明细弹窗导出，筛选与弹窗表格一致）。
     *
     * @param query 分组键 + 筛选条件
     * @return 入库明细行
     */
    List<InoutStatInDetailVo> queryInDetailList(InoutStatDetailQuery query);

    /**
     * 出库明细分页（V6-R186 出库统计行「查看详情」）。
     *
     * @param query     分组键（产品编码 / 出库去向）+ 列表原筛选 + 明细自己的日期区间与记录人
     * @param pageQuery 分页参数
     * @return 出库明细分页
     */
    TableDataInfo<InoutStatOutDetailVo> queryOutDetailPage(InoutStatDetailQuery query, PageQuery pageQuery);

    /**
     * 出库明细全量（明细弹窗导出）。
     *
     * @param query 分组键 + 筛选条件
     * @return 出库明细行
     */
    List<InoutStatOutDetailVo> queryOutDetailList(InoutStatDetailQuery query);
}
