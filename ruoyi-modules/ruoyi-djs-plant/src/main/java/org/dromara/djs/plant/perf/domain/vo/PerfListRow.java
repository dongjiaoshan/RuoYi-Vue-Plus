package org.dromara.djs.plant.perf.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 班组绩效列表行（班组 × 月聚合，PLT-PERF-001 rework 134/135）。
 *
 * <p>主列表展示粒度 = 班组 × 月（不再逐作物平铺）：一行 = 某班组某月的绩效汇总。
 * 由 {@code mapper.selectTeamMonthPage}（GROUP BY stat_month, team_id）产出聚合金额 /
 * 采摘总量 / 作物种类数；{@code teamName} 由 service enrich（反查 t_plant_work_team.team_name）；
 * {@code farmCount} 由 service 批量 count t_plant_farm_records 补（按 team × month）。</p>
 *
 * <p>逐作物明细（采摘量 / 单价 / 该作物绩效额）走详情抽屉 {@code /crop-rows?teamId=&statMonth=}，
 * 返 {@link PlantWorkPerformanceVo} list。</p>
 *
 * @author djs
 * @since PLT-PERF-001
 */
@Data
@ExcelIgnoreUnannotated
public class PerfListRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 统计月份（yyyy-MM）。
     */
    @ExcelProperty(value = "月份")
    private String statMonth;

    /**
     * 班组 ID。
     */
    private Long teamId;

    /**
     * 班组名称（冗余，service 反查 t_plant_work_team.team_name）。
     */
    @ExcelProperty(value = "班组")
    private String teamName;

    /**
     * 班组人数（= 该 team_id 下 t_plant_work_people 活动成员数，service 批量补）。
     *
     * <p>当前快照成员数（成员表无历史快照），同班组各月行显示同一人数。</p>
     */
    @ExcelProperty(value = "班组人数")
    private Integer teamMemberCount;

    /**
     * 采摘总量（斤，= SUM(pick_weight)）。
     */
    @ExcelProperty(value = "采摘总量(斤)")
    private BigDecimal totalPickWeight;

    /**
     * 该班组该月绩效总额（元，= SUM(performance_amount)）。
     */
    @ExcelProperty(value = "绩效总额(元)")
    private BigDecimal teamMonthAmount;

    /**
     * 作物种类数（= COUNT(DISTINCT crop_id)）。
     */
    @ExcelProperty(value = "作物种类数")
    private Integer cropCount;

    /**
     * 农事次数（该班组该月 t_plant_farm_records 全部 farm_type 计数，service 批量补）。
     */
    @ExcelProperty(value = "农事次数")
    private Integer farmCount;
}
