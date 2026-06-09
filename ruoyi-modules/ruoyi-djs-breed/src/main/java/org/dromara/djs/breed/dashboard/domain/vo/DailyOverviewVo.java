package org.dromara.djs.breed.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 养殖场日情况概览 16 格 VO（FIX-MGMT-MP-BRD-001，原型"种猪 tab·养殖场日情况概览 16 格"）。
 *
 * <p>给某一自然日（缺省今日）的 16 项养殖活动当日值。每项 = {@code metric}（中文文案，后端给）
 * + {@code value}（当日值）。前端按 16 格平铺，无需知道每格含义，文案完全由后端定。</p>
 *
 * <p>16 项 = by-month 13 项的"当日"快照（分娩/配种/断奶/返空流/死亡/淘汰母猪数 + 引种母猪数 +
 * 查情不配种数 + 产仔数 + 活仔数 + 仔猪打标数 + 生长记录数 + 阉割猪只数）
 * + 断奶仔猪数 + 用药猪只数（第 16 格，#7.7）。</p>
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
