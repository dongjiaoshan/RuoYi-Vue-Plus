package org.dromara.djs.warehouse.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 仓库「生产管理统计」VO（mp 原型图 26）。
 *
 * <p>对应原型 3 tab（猪肉处理 / 果蔬处理 / 发货产品 生产管理）。同一结构按 {@code tab} 复用：</p>
 * <ul>
 *   <li>{@code annualGroups}：年度指标分组，每组一个标题（如「年度屠宰指标统计」）+ 若干 label/value 卡。
 *       猪肉 tab 含 2 组（屠宰 / 分割）；果蔬、发货 tab 各 1 组。</li>
 *   <li>{@code trend}：月度效能趋势（当年 1 月～当前月），柱+3 折线，前端 V1 单折线渲染 barValue。</li>
 * </ul>
 *
 * <p>无数据时各 group value 为 0、trend 各点为 0，不抛错。</p>
 *
 * @author djs
 */
@Data
public class WarehouseProductionStatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前 tab：pig=猪肉处理 / veg=果蔬处理 / ship=发货产品。
     */
    private String tab;

    /**
     * 年度指标分组（猪肉 2 组、果蔬/发货 1 组）。
     */
    private List<Group> annualGroups;

    /**
     * 月度效能趋势（当年至今逐月，缺月补 0）。
     */
    private List<ProductionTrendPointVo> trend;

    /**
     * 指标分组（一个标题 + 该组下的指标卡列表）。
     */
    @Data
    public static class Group implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 分组标题（如「年度屠宰指标统计」）。
         */
        private String title;

        /**
         * 该组指标卡。
         */
        private List<ProductionStatItemVo> items;

        public Group() {
        }

        public Group(String title, List<ProductionStatItemVo> items) {
            this.title = title;
            this.items = items;
        }

    }

}
