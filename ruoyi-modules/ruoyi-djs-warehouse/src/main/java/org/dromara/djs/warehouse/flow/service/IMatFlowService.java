package org.dromara.djs.warehouse.flow.service;

import org.dromara.djs.warehouse.flow.domain.bo.MatLossBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatPickBo;
import org.dromara.djs.warehouse.flow.domain.bo.MatReturnBo;
import org.dromara.djs.warehouse.flow.domain.vo.MatTodaySummaryVo;

/**
 * 物资领用 / 退回 / 损耗 Service（WMS-MAT-001）。
 *
 * <p>3 个核心方法（mp 工人 3 Tab 通用模板），每个均同事务跨表（stock_flow + location_stock）。
 * V1 仅"包材"实例化使用，V1.5 由白条 / 蔬菜 / 鸡蛋 / 干货 / 果蔬 / 采食 / 药品 / 种子 8 类通过
 * matType 参数复用。</p>
 *
 * @author djs
 * @since WMS-MAT-001
 */
public interface IMatFlowService {

    /**
     * 物资领用（出库）。
     *
     * @return 新增 stock_flow 主键 id
     */
    Long pick(MatPickBo bo);

    /**
     * 物资退回（入库）。
     *
     * @return 新增 stock_flow 主键 id
     */
    Long returnBack(MatReturnBo bo);

    /**
     * 物资损耗（出库）。
     *
     * @return 新增 stock_flow 主键 id
     */
    Long loss(MatLossBo bo);

    /**
     * 当前登录人今日已领 / 已退 / 已损汇总。
     *
     * @param matType 可选物资类型（djs_mat_type 字典 value，service 内部映射 belong_type）；null = 全部
     * @return 三项 SUM
     */
    MatTodaySummaryVo todaySummary(String matType);

    /**
     * 当前登录人今日已领 / 已退 / 已损汇总（单产品维度）。
     *
     * <p>BRD-FIX-MP-FEED-IA-001：饲料领用子页顶卡需「当前选中单产品」的今日已领 / 退回，
     * 而非全产品汇总。productId 入参 String（snowflake 防截断），内部转 Long 查询；
     * 为空 / 空白时退化为 {@link #todaySummary(String)} 全产品（或 matType 维度）。</p>
     *
     * @param matType   可选物资类型；productId 非空时本参不参与过滤
     * @param productId 产品 ID（snowflake String），可空 → 退化为 matType 维度
     * @return 三项 SUM
     */
    MatTodaySummaryVo todaySummary(String matType, String productId);

}
