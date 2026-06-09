package org.dromara.djs.breed.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 养殖场日情况概览 15 格 VO（FIX-MGMT-MP-BRD-001，原型 4f113e00"种猪 tab·养殖场日情况概览"，4+4+4+3）。
 *
 * <p>给某一自然日（缺省今日）的 15 项养殖活动当日值。每项 = {@code metric}（中文文案，后端给）
 * + {@code value}（当日值）。前端按 15 格平铺，无需知道每格含义，文案完全由后端定。</p>
 *
 * <p>15 项顺序 / 文案严格对齐原型，与 by-month 活动统计 15 行同序同文案：分娩母猪数 / 配种母猪数 /
 * 断奶母猪数 / 返空流母猪数 / 引种母猪数 / 查情不配种数 / 死亡猪只数 / 淘汰猪只数 / 产仔数 / 活仔数 /
 * 仔猪打标数 / 断奶仔猪数 / 末行 3 格 = 生长记录数 / 阉割猪只数 / 用药猪只数。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-BRD-001
 */
@Data
public class DailyOverviewVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计日期（yyyy-MM-dd）。 */
    private String date;

    /** 16 项当日指标格（顺序固定，前端按序平铺 16 格）。 */
    private List<OverviewCell> cells = new ArrayList<>();

    public DailyOverviewVo() {
    }

    public DailyOverviewVo(LocalDate date) {
        this.date = date.toString();
    }

    /** 单格：中文指标名 + 当日数值。 */
    @Data
    public static class OverviewCell implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 指标名称（固定中文文案，后端给）。 */
        private String metric;

        /** 当日值（无数据 = 0，不返 null）。 */
        private Integer value;

        public OverviewCell() {
        }

        public OverviewCell(String metric, Integer value) {
            this.metric = metric;
            this.value = value == null ? 0 : value;
        }
    }
}
