package org.dromara.djs.warehouse.vegout.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.vegout.domain.bo.VegOutSubmitBo;
import org.dromara.djs.warehouse.vegout.domain.query.VegOutQuery;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutBatchVo;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutCandidateVo;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutDetailVo;

import java.util.List;

/**
 * 毛菜间出库 Service（admin row185 单条内部处理 + row187 批量出库管理）。
 *
 * <p>两个入口写入口径完全一致，都走 {@link #submit}：扣毛菜鲜品库（L0006）库存 +
 * 写一条 {@code flow_type=backstage_out} 的出库流水；去向为「果蔬月台」或「猪只饲料」时
 * 再按毛菜处理间同款口径写下游台账。</p>
 *
 * @author djs
 */
public interface IVegOutService {

    /**
     * 新增抽屉左侧可选产品：毛菜鲜品库里 belong_type='vegetable' 且库存 &gt; 0 的行。
     *
     * @param productName 产品名称（模糊，可空）
     */
    List<VegOutCandidateVo> listCandidates(String productName);

    /**
     * 提交出库。
     *
     * @param bo      出库单（row185 传 1 条明细、去向限果蔬月台/饲料饲喂；row187 传多条、去向为字典全项）
     * @param asBatch 是否作为「出库单」记账：true → 生成 batch_no 并进 row187 列表；
     *                false → batch_no 留空（row185 单条内部处理，只进出库记录）
     * @return 批量出库单号（asBatch=false 时返回 null）
     */
    String submit(VegOutSubmitBo bo, boolean asBatch);

    /**
     * 出库单列表（row187，按 batch_no 聚合）。
     */
    TableDataInfo<VegOutBatchVo> queryBatchPage(VegOutQuery query, PageQuery pageQuery);

    /**
     * 出库单全量列表（V6 row31 导出）：与 {@link #queryBatchPage} 同筛选同口径，不分页。
     *
     * @param query 查询条件（出库日期区间 / 出库去向 / 操作人，与页面搜索框一致）
     */
    List<VegOutBatchVo> queryBatchList(VegOutQuery query);

    /**
     * 出库单明细（row187 详情弹框）。
     *
     * @param batchNo     出库单号
     * @param productName 产品名称（模糊，可空）
     */
    List<VegOutDetailVo> queryBatchDetail(String batchNo, String productName);

    /**
     * 出库单明细导出行（V6 row30）：同 {@link #queryBatchDetail}，额外派生带单位的「出库量」展示列。
     *
     * <p>单开一个方法而不是在 {@code queryBatchDetail} 里顺手填：明细接口是详情弹框在跑的线上路径，
     * 不为导出改它的响应内容。</p>
     *
     * @param batchNo     出库单号
     * @param productName 产品名称（模糊，可空；与弹框内搜索框一致）
     */
    List<VegOutDetailVo> queryBatchDetailForExport(String batchNo, String productName);

}
