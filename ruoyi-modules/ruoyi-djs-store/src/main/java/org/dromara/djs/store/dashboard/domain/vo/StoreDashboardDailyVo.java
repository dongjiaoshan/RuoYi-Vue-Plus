package org.dromara.djs.store.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 门店每日情况一览 VO（STR-DASH-001，mp 门店店员视角）。
 *
 * <p>猪肉 / 果蔬两组当日速览 + 需求统计 Tab + 发货统计 Tab。
 * 业态过滤走 {@code t_warehouse_product_info.belong_type}（字典 djs_belong_type）：
 * 猪肉 = belong_type IN ('pork','white_bar')，果蔬 = belong_type='vegetable'。</p>
 *
 * <p><b>CR-20260519-01 收缩</b>：mp 数据明细从原型 6 Tab 收缩为 2 真 Tab（需求 / 发货）
 * + 4 占位 Tab（退货 / 损耗 / 车间效能 / 进出库，前端硬编码"V1 销售数据未对接，待 V2"，
 * 不调后端、不在本 VO 体现）。</p>
 *
 * <p>退回率 / 损耗率：上游 V1 无退货 / 损耗明细表 → service 置 null，前端显示"—"占位。</p>
 *
 * @author djs
 * @since STR-DASH-001
 */
@Data
public class StoreDashboardDailyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 猪肉当日速览（belong_type IN 'pork','white_bar'）。
     */
    private DailyCategoryVo pork;

    /**
     * 果蔬当日速览（belong_type='vegetable'）。
     */
    private DailyCategoryVo vegetable;

    /**
     * 需求统计 Tab（真）：本店 demand 按 demand_status 计数。
     */
    private List<StoreGroupCountVo> demandStat;

    /**
     * 发货统计 Tab（真）：demand_status IN PARTIAL_SHIPPED / COMPLETED 计数。
     */
    private List<StoreGroupCountVo> shipStat;

    /**
     * 单业态当日速览（猪肉 / 果蔬通用）。
     *
     * <p>退回率 / 损耗率上游 V1 无数据源 → 为 null（前端显示"—"）。</p>
     */
    @Data
    public static class DailyCategoryVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 当日销售总额（元）。
         */
        private BigDecimal salesAmount;

        /**
         * 退回率（上游 V1 无退货明细 → null，前端显示"—"）。
         */
        private BigDecimal returnRate;

        /**
         * 损耗率（上游 V1 无损耗明细 → null，前端显示"—"）。
         */
        private BigDecimal lossRate;

        /**
         * 客单价（销售额 / 订单数；订单数 0 时为 0）。
         */
        private BigDecimal avgOrderPrice;

        /**
         * 当日订单数。
         */
        private Integer orderCount;

        /**
         * 单业态空兜底（销售额 0 / 订单 0 / 退回率损耗率 null）。
         *
         * @return 空速览
         */
        public static DailyCategoryVo empty() {
            DailyCategoryVo vo = new DailyCategoryVo();
            vo.setSalesAmount(BigDecimal.ZERO);
            vo.setReturnRate(null);
            vo.setLossRate(null);
            vo.setAvgOrderPrice(BigDecimal.ZERO);
            vo.setOrderCount(0);
            return vo;
        }

    }

    /**
     * 全空兜底实例（无任何数据时返回，避免前端空指针）。
     *
     * @return 猪肉 / 果蔬空速览 + 空统计列表的 VO
     */
    public static StoreDashboardDailyVo empty() {
        StoreDashboardDailyVo vo = new StoreDashboardDailyVo();
        vo.setPork(DailyCategoryVo.empty());
        vo.setVegetable(DailyCategoryVo.empty());
        vo.setDemandStat(List.of());
        vo.setShipStat(List.of());
        return vo;
    }

}
