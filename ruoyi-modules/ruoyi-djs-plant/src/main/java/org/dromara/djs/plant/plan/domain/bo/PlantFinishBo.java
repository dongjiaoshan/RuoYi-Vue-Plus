package org.dromara.djs.plant.plan.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * mp 播种「种植完成」一步落地入参 BO。
 *
 * <p>作物种植详情页地块「确认完成」：取消开工分步后，每块只录一次「种植完成日期 + 种植班组」即落地。</p>
 *
 * <p>语义：仅 {@code plant_status != 'completed'} 且 {@code end_actualdate IS NULL}（尚未完成）的明细可被完成。
 * 对其中 {@code begin_actualdate IS NULL}（待种植）的明细，一并补写
 * {@code begin_actualdate=endActualdate}（种植日期=完成日期，单日期口径）+ {@code plant_by=plantBy}，
 * 并按 begin 重算采摘窗口 + 关联地块 {@code plot_status=2}；最后批量回写
 * {@code plant_status='completed'} + {@code end_actualdate=endActualdate}。</p>
 *
 * @author djs
 */
@Data
public class PlantFinishBo {

    /** 待完成的计划明细 id 列表（{@code t_plant_plant_details.id}，前端从详情页计划地块 list 勾选）。 */
    @NotEmpty(message = "请至少选择一个计划地块完成")
    private List<Long> detailIds;

    /** 种植完成日期（单日期口径：同时作为种植日期 begin_actualdate 与完成日期 end_actualdate）。 */
    @NotNull(message = "请选择种植完成日期")
    private LocalDate endActualdate;

    /** 种植班组 id（{@code t_plant_work_team.id}，回写 plant_by；过渡兼容单值 = 多选第一个）。 */
    @NotNull(message = "请选择种植班组")
    private Long plantBy;

    /**
     * 种植班组全集（G1-TEAMS-MULTISELECT，row36）。非空时写中间表 role=plant，
     * 旧单列 {@code plantBy} 取本 list 第一个；为 null 时退化为单值 {@code plantBy}。
     */
    private List<Long> plantByIds;
}
