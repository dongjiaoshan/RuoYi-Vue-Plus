package org.dromara.djs.plant.plan.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * mp 播种「开始种植」开工入参 BO（FIX-PLT-MP-SEED-001，#5）。
 *
 * <p>原型作物种植详情页底部「种植记录」表单 + 「开始种植」按钮：选开始日期 + 班组，
 * 把所选若干计划地块（{@code t_plant_plant_details}）标记实际开工。</p>
 *
 * <p>语义：仅 {@code begin_actualdate IS NULL}（尚未开工）的明细可被开工；批量回写
 * {@code begin_actualdate=beginActualdate} + {@code plant_by=plantBy} +
 * {@code plant_status='ongoing'}，并同步关联地块 {@code t_plant_plot_info.plot_status=2}（种植中）。</p>
 *
 * @author djs
 * @since FIX-PLT-MP-SEED-001
 */
@Data
public class PlantStartBo {

    /** 待开工的计划明细 id 列表（{@code t_plant_plant_details.id}，前端从详情页计划地块 list 勾选）。 */
    @NotEmpty(message = "请至少选择一个计划地块开工")
    private List<Long> detailIds;

    /** 实际开始种植日期。 */
    @NotNull(message = "请选择种植开始日期")
    private LocalDate beginActualdate;

    /** 种植班组 id（{@code t_plant_work_team.id}，回写 plant_by；过渡兼容单值 = 多选第一个）。 */
    @NotNull(message = "请选择种植班组")
    private Long plantBy;

    /**
     * 种植班组全集（G1-TEAMS-MULTISELECT，row36）。非空时写中间表 role=plant，
     * 旧单列 {@code plantBy} 取本 list 第一个；为 null 时退化为单值 {@code plantBy}。
     */
    private List<Long> plantByIds;
}
