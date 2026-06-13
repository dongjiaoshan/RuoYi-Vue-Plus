package org.dromara.djs.plant.plan.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * mp 播种「种植完成」收工入参 BO。
 *
 * <p>原型作物种植详情页「种植完成」按钮：选实际结束日期，把所选若干已开工的计划地块
 * （{@code t_plant_plant_details}）标记种植完成。</p>
 *
 * <p>语义：仅 {@code plant_status='ongoing'} 且 {@code begin_actualdate IS NOT NULL} 且
 * {@code end_actualdate IS NULL}（已开工尚未完成）的明细可被完成；批量回写
 * {@code plant_status='completed'} + {@code end_actualdate=endActualdate}。
 * 不动 {@code t_plant_plot_info.plot_status}（保持 2 种植，待采摘开始才转 3）。</p>
 *
 * @author djs
 */
@Data
public class PlantFinishBo {

    /** 待完成的计划明细 id 列表（{@code t_plant_plant_details.id}，前端从详情页计划地块 list 勾选）。 */
    @NotEmpty(message = "请至少选择一个计划地块完成")
    private List<Long> detailIds;

    /** 实际结束种植日期。 */
    @NotNull(message = "请选择种植结束日期")
    private LocalDate endActualdate;
}
