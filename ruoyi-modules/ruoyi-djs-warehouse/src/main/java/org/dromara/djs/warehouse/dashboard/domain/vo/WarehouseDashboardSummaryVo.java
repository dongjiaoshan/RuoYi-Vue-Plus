package org.dromara.djs.warehouse.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 仓库看板汇总 VO（DJS-FIX-ADMIN-W22-006 占位版）。
 *
 * <p>3 KPI 卡 + 库位概览，不含趋势折线 / 饼图（完整版推 V1.x WMS-DASH-001）。
 * 各字段聚合范式见 {@link org.dromara.djs.warehouse.dashboard.mapper.WarehouseDashboardMapper}。</p>
 *
 * <p>当日 / 当月无数据时所有计数字段返 0（service 用 {@code COALESCE} + null-safe 兜底），
 * 不抛错。</p>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-006
 */
@Data
public class WarehouseDashboardSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * KPI 卡 1：今日白条需求量（{@code SUM(demand_quantity)} WHERE product_type='white_bar' AND demand_date=CURDATE()）。
     *
     * <p>单位与产品单位一致（白条为 kg）。字段名保留 demand 语义；非"头数"——
     * demand_manage 表记录的是需求数量（DECIMAL），白条业态按 kg 计。</p>
     */
    private BigDecimal todayDemandQuantity;

    /**
     * KPI 卡 2：今日生产入库笔数（{@code COUNT(*)} stock_flow WHERE inout_type='in' AND DATE(flow_date)=CURDATE()）。
     */
    private Integer todayProductionCount;

    /**
     * KPI 卡 3-a：最近 1 次盘点正常数（diff_stock = 0）。
     */
    private Integer stockCheckNormal;

    /**
     * KPI 卡 3-b：最近 1 次盘点异常数（diff_stock != 0）。
     */
    private Integer stockCheckAbnormal;

    /**
     * KPI 卡 3-c：最近 1 次盘点计损数（diff_stock < 0，盘亏）。
     */
    private Integer stockCheckLoss;

    /**
     * 当月异常库位数（{@code COUNT(DISTINCT warehouse_id)} check_record WHERE diff_stock!=0 AND check_date>=当月1号）。
     */
    private Integer monthAbnormalLocationCount;

    /**
     * 库位概览列表（Top 20，按库位名排序）。
     */
    private List<LocationOverviewItemVo> locationOverview;

    /**
     * 全空兜底实例（无任何数据时返回，避免前端空指针）。
     *
     * @return 计数字段全 0、库位列表空的 VO
     */
    public static WarehouseDashboardSummaryVo empty() {
        WarehouseDashboardSummaryVo vo = new WarehouseDashboardSummaryVo();
        vo.setTodayDemandQuantity(BigDecimal.ZERO);
        vo.setTodayProductionCount(0);
        vo.setStockCheckNormal(0);
        vo.setStockCheckAbnormal(0);
        vo.setStockCheckLoss(0);
        vo.setMonthAbnormalLocationCount(0);
        vo.setLocationOverview(List.of());
        return vo;
    }

}
