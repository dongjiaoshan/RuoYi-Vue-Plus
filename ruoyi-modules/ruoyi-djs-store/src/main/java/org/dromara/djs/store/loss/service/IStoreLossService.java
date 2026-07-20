package org.dromara.djs.store.loss.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.store.loss.domain.query.StoreLossQuery;
import org.dromara.djs.store.loss.domain.vo.StoreLossRecordVo;
import org.dromara.djs.store.loss.domain.vo.WhiteBarSplitLossVo;

import java.time.LocalDate;

/**
 * 门店损耗记录聚合 Service（DENGBO-R15）。
 *
 * <p>每晚定时任务把两类损耗落盘到 {@code t_store_loss_record}：门店日损耗（读盘点台账 loss_qty 原样搬）
 * + 白条分割损耗（到店重−退回入库重 钳 0，按门店汇总一行）。DENGBO-R30 加列表查询端点 + 单店白条到店重查询。</p>
 *
 * @author djs
 * @since DENGBO-R15
 */
public interface IStoreLossService {

    /**
     * 聚合指定日的门店损耗到 {@code t_store_loss_record}（幂等：先删该日旧行再重插）。
     *
     * @param targetDate 目标日；{@code null} 取昨天（T-1，{@code Asia/Shanghai}）——定时任务凌晨触发，
     *                   聚合的是已完结的昨日；admin 手动重跑传显式日期不受此默认影响
     */
    void aggregate(LocalDate targetDate);

    /**
     * 门店损耗记录分页列表（DENGBO-R30，门店管理 > 门店损耗记录）。
     * 门店维度由 {@code StoreLineHandler} 行级注入（{@code Current-Store-Id} 头）；
     * 产品名称 / 单位 LEFT JOIN {@code t_warehouse_product_info} 内存回填（白条分割损耗汇总行 productName 用类型中文占位）。
     *
     * @param query     筛选（损耗日期范围 / 产品名称模糊 / 损耗类型）
     * @param pageQuery 分页
     * @return 分页结果
     */
    TableDataInfo<StoreLossRecordVo> queryPageList(StoreLossQuery query, PageQuery pageQuery);

    /**
     * 当日某门店白条分割损耗（DENGBO-R30，门店盘点抽屉「当日白条分割损耗」展示，口径同定时任务）。
     * {@code splitLoss = max(0, 白条到店重 − 白条退回产品入库重)}；退回入库重来自门店退货入库流水
     * {@code t_store_return}（门店退回仓库、已入库、白条退回产品字典），非盘点台账退回列。
     * {@code arriveWeight}=0（当日无白条到店）时前端不显示该块。
     *
     * @param storeId 门店 id
     * @param date    业务日；{@code null} 取今天（{@code Asia/Shanghai}）
     * @return 到店重 / 退回入库重 / 分割损耗（无白条到店三者均 0）
     */
    WhiteBarSplitLossVo getWhiteBarSplitLoss(Long storeId, LocalDate date);
}
