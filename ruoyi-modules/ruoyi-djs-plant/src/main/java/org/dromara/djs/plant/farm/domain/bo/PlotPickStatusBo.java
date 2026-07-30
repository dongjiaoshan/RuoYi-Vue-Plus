package org.dromara.djs.plant.farm.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 土地采摘状态调整入参 BO（FIX-PLT-MP-HARVEST-001 #3，采摘活动管理富页）。
 *
 * <p>原型「土地状态调整」弹窗：状态调整日期 + 采摘状态（采摘中 ↔ 采摘完成）+ 记录班组。
 * 只切状态、不录重量（重量录入权威源为底栏「采收」tab，见 #3 code-resolved 决策）。</p>
 *
 * @author djs
 * @since FIX-PLT-MP-HARVEST-001
 */
@Data
public class PlotPickStatusBo {

    /** 地块 id（{@code t_plant_plot_info.id}）。 */
    @NotNull(message = "请选择地块")
    private Long plotId;

    /** 作物 id（{@code t_plant_crop_info.id}，定位该地块下对应作物的明细）。 */
    @NotNull(message = "请选择作物")
    private Long cropId;

    /**
     * 目标采摘状态（字典 {@code djs_pick_status} 值：{@code picking}=采摘中 / {@code completed}=采摘完成）。
     */
    @NotBlank(message = "请选择采摘状态")
    @Size(max = 32)
    private String pickStatus;

    /** 状态调整日期。 */
    @NotNull(message = "请选择状态调整日期")
    private LocalDate adjustDate;

    /** 采摘班组 id（{@code t_plant_work_team.id}，写留痕农事记录的 farm_by）。单选兼容口径；多选走 {@link #teamIds} 首值。 */
    @NotNull(message = "请选择采摘班组")
    private Long teamId;

    /**
     * 采摘班组全集 id（row152 多选，可空兼容旧单选）：picking 首次录入时复用
     * {@code PlantTeamLinkService.syncDetailTeams(role=harvest)} 落 {@code t_plant_details_team}，
     * {@code plant_details.harvest_by} 与留痕 {@code farm_by} 写首值。
     */
    private List<Long> teamIds;

    /** 采摘人员 user_id（{@code sys_user.user_id}，可空）：采摘活动土地状态只记班组、不记人员，前端不传，落 operator_user_id=NULL。 */
    private Long operatorUserId;
}
