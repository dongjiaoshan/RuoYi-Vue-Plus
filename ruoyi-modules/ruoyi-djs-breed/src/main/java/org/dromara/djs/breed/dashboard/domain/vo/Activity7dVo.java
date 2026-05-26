package org.dromara.djs.breed.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 近 7 天活动统计 VO（BRD-DASH-001）。
 *
 * <p>mp 端"种猪场活动统计"双冻结表数据源；行=事件类型（11 种），列=近 7 天 + 累计。</p>
 *
 * <p>本 ticket 范围内 sow_record 已聚合 8 列（sow_total/pregnant/farrow/weaning/idle/culling/death + piglet_total），
 * 端点直接返聚合表的"近 7 天 list"。fe 接到 list 后按需 pivot 成 11 行 × 7 列 + 1 累计列。</p>
 *
 * @author djs
 * @since BRD-DASH-001
 */
@Data
public class Activity7dVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<DailyRow> rows = new ArrayList<>();

    /**
     * 单日聚合记录（直接对应 t_farm_sow_record 一行）。
     */
    @Data
    public static class DailyRow implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private LocalDate statDate;
        private Integer sowTotal;
        private Integer sowPregnant;
        private Integer sowFarrow;
        private Integer sowWeaning;
        private Integer sowIdle;
        private Integer sowCullingCount;
        private Integer sowDeathCount;
        private Integer pigletTotal;
    }
}
