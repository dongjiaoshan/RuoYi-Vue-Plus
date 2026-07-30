package org.dromara.djs.plant.pick.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * mp 工人采收录入入参 BO（PLT-PICK-001 / FIX-PLT-MP-PICK-001 #3=a）。
 *
 * <p>工人从待采列表 / 详情进入，{@code detailId} 由路由 query 带入（不手输 ID）。
 * 按原型采收 tab 只走"开始/完成采摘"流程（采摘人员 + 日期），<b>不录重量</b>；首次回填
 * {@code begin_harvestdate} + 流转 {@code harvest_status}；{@code finish=true} 时额外置
 * {@code end_actualdate=NOW} + {@code harvest_status='completed'}，并 INSERT 一行
 * {@code t_plant_farm_records}（{@code farm_type='harvest_activity'}，{@code farm_by} 落所选采收班组，
 * 采收按班组记录、{@code operator_user_id} 留空）。采摘重量由农事「采摘活动管理」{@code submitHarvestWeight} 累加 {@code actual_yield}。</p>
 *
 * @author djs
 * @since PLT-PICK-001
 */
@Data
public class PickSubmitBo {

    /** 采摘明细 id（{@code t_plant_plant_details.id}）。 */
    @NotNull(message = "采摘明细 id 必填")
    private Long detailId;

    /** 采收日期（必填）。 */
    @NotNull(message = "采收日期必填")
    private LocalDate harvestDate;

    /** 是否完成采收（true 置 completed + end_actualdate；默认 false 继续采）。 */
    private Boolean finish = false;

    /** 采收班组 t_plant_work_team.id（必填；落 t_plant_farm_records.farm_by，采收按班组记录；多选时 = 第一个）。 */
    @NotNull(message = "请选择采收班组")
    private Long teamId;

    /**
     * 采收班组全集（G1-TEAMS-MULTISELECT，row40）。非空时写中间表，
     * 旧单列 {@code teamId} 取本 list 第一个；为 null 时退化为单值 {@code teamId}。
     */
    private List<Long> teamIds;

    /** 凭证图 OSS id（可选；bizType=plant_farm_proof）。 */
    private List<Long> proofOssIds;

    /** 备注（可选）。 */
    @Size(max = 500, message = "备注不超过 500 字")
    private String remark;
}
