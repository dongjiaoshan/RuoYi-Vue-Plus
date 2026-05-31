package org.dromara.djs.breed.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 月度活动统计 VO（BRD-DASH-ACTIVITY-001，原型 21 种猪场活动统计）。
 *
 * <p>mp 端"种猪场活动统计"双冻结表数据源。{@link #days} 为该月日期列（MM-dd，自然日升序）；
 * {@link #rows} 为 11 指标行，每行含与 days 对齐的 {@code daily[]} 与后端汇总的 {@code total}。
 * 累计列由后端计算（FE 不再求和）。</p>
 *
 * @author djs
 * @since BRD-DASH-ACTIVITY-001
 */
@Data
public class MonthActivityVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 日期列（MM-dd 格式，按自然日升序，长度=当月天数） */
    private List<String> days = new ArrayList<>();

    /** 指标行（11 行） */
    private List<MonthRow> rows = new ArrayList<>();

    /**
     * 单指标行。
     */
    @Data
    public static class MonthRow implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 指标名称（固定中文文案） */
        private String metric;

        /** 每日值，长度与 days 一致 */
        private List<Integer> daily = new ArrayList<>();

        /** 本月累计（后端汇总，前端不再求和） */
        private Integer total;
    }
}
