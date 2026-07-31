package org.dromara.djs.plant.plan.domain.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.util.Date;
import java.util.List;

/**
 * 种植计划查询参数（PLT-PLAN-001 / FIX-PLT-AD-PLAN-001 对齐原型 4 项筛选）。
 *
 * <p>原型 4 项：计划日期范围 {@code beginPlanDate}~{@code endPlanDate}（按最早开始日期 earliestBegindate 搜索）/
 * 种植农作物 {@code cropId} / 计划更新时间 {@code queryUpdateTime} / 计划编制人 {@code queryCreateBy}。
 * planNo/planYear/planSeason/plantStatus 仍保留（导出 / 兼容），仅列表 UI 不挂。</p>
 *
 * <p>更新时间 / 编制人用独立 {@code queryUpdateTime} / {@code queryCreateBy} 字段，
 * 不复用 BaseEntity 的 {@code updateTime/createBy}（避免与持久化字段语义混淆）。</p>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlantPlanQuery extends BaseEntity {

    /** 业务码模糊。 */
    private String planNo;

    /** 计划年份精确。 */
    private Integer planYear;

    /** 季节字典 djs_planting_season。 */
    private String planSeason;

    /** 作物 id 精确（导出 / 兼容保留）。 */
    private Long cropId;

    /**
     * 农作物名称模糊（原型「种植农作物」筛选）。
     *
     * <p>cropName 非主表存储列（crop_name 在 t_plant_crop_info），故在 buildWrapper 用相关子查询
     * 按 {@code crop_id IN (SELECT id FROM t_plant_crop_info WHERE crop_name LIKE ...)} 过滤。</p>
     */
    private String cropName;

    /** 计划状态字典 djs_plant_plan_status。 */
    private String plantStatus;

    /**
     * 计划日期范围-起（原型「计划日期」筛选，按计划最早开始日期 earliestBegindate 搜索）。
     *
     * <p>earliestBegindate 是明细派生值 {@code MIN(DATE(CONCAT(plan_year,'-',LPAD(plant_month,2,'0'),'-',plant_period)))}，
     * 非主表存储列，故在 buildWrapper 用相关子查询 + HAVING 过滤，语义 = 该计划的最早开始日期落在 [begin,end] 内。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date beginPlanDate;

    /** 计划日期范围-止（含当日），同 {@link #beginPlanDate}。 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date endPlanDate;

    /**
     * 计划月份多选（原型「计划月份」筛选，1-12，可多选）：查该年份下含任一所选月份种植明细的计划。
     *
     * <p>plant_month 在 t_plant_plant_details，非主表列，故 buildWrapper 用
     * {@code EXISTS(SELECT 1 FROM t_plant_plant_details d WHERE d.plant_id=t_plant_plant_plan.id AND d.plant_month IN (...))}
     * 过滤；年份维度仍用主表 {@link #planYear} 等值。取代原 {@link #beginPlanDate}/{@link #endPlanDate} 日期范围筛选。</p>
     */
    private List<Integer> planMonths;

    /** 计划更新时间（按天精确，原型「计划更新时间」筛选）。 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date queryUpdateTime;

    /** 计划编制人 user_id 精确（对 create_by，导出 / 兼容保留）。 */
    private Long queryCreateBy;

    /**
     * 计划编制人姓名模糊（原型「计划编制人」筛选）。
     *
     * <p>编制人姓名 = create_by 对应的 sys_user.nick_name（与列表 createByName 同源），
     * 故在 buildWrapper 用相关子查询按
     * {@code create_by IN (SELECT user_id FROM sys_user WHERE nick_name LIKE ...)} 过滤。</p>
     */
    private String queryCreateByName;
}
