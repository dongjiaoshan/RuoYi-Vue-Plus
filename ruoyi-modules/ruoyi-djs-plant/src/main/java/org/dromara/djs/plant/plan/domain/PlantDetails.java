package org.dromara.djs.plant.plan.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 种植计划明细实体（PLT-PLAN-001）。
 *
 * <p>对应表 {@code t_plant_plant_details}（V202606071400，30 字段权威 doc/11 §1.8）：</p>
 * <ul>
 *   <li>4 时间字段：{@code beginActualdate} / {@code endActualdate}（种植起止）+ {@code beginHarvestdate} / {@code endHarvestdate}（采摘起止）
 *       由 D10 PLT-WORK-001（mp 农事录入）+ D12 PLT-PICK-001（mp 采摘）触发更新</li>
 *   <li>{@code earliestHarvestdate} / {@code lastHarvestdate}：创建时按 {@code plant_month + plant_period + crop.min/max_cycle} 算</li>
 *   <li>{@code expectedYield}：创建时 {@code plot_area × crop.predicted_per}</li>
 *   <li>{@code actualYield}：D14 CROSS-FLOW-002 采摘累计写入</li>
 *   <li>{@code isPick}：游客采摘活动 flag（PLT-PLAN-002 D10 admin "设为采摘活动" 改 1）</li>
 *   <li>UNIQUE(tenant_id, plant_id, plot_id, plant_month, plant_period, del_unique)：同计划同地块同月同旬不重复</li>
 * </ul>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_plant_details")
public class PlantDetails extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** FK → {@code t_plant_plant_plan.id}。 */
    private Long plantId;

    /** FK → {@code t_plant_plot_info.id}。 */
    private Long plotId;

    /** FK → {@code t_plant_crop_info.id}（冗余，与主表 crop_id 一致）。 */
    private Long cropId;

    /** 计划月份 1-12（xlsx plant_money typo 清理）。 */
    private Integer plantMonth;

    /** 计划阶段 CHAR(2)（字典 {@code djs_plant_period}：05=上旬 / 15=中旬 / 25=下旬）。 */
    private String plantPeriod;

    /** 实际开始种植日期（D10 mp "开始种植"按钮触发）。 */
    private LocalDate beginActualdate;

    /** 实际结束种植日期。 */
    private LocalDate endActualdate;

    /** 实际开始采摘日期（D12 mp 采摘触发）。 */
    private LocalDate beginHarvestdate;

    /** 实际结束采摘日期。 */
    private LocalDate endHarvestdate;

    /** 计划最早采摘日期 = plant_start + crop.min_cycle。 */
    private LocalDate earliestHarvestdate;

    /** 计划最晚采摘日期 = plant_start + crop.max_cycle。 */
    private LocalDate lastHarvestdate;

    /** 种植状态（字典 {@code djs_plant_plan_status}）。 */
    private String plantStatus;

    /** 采摘状态（字典 {@code djs_pick_status}）。 */
    private String harvestStatus;

    /** 地块面积（亩，冗余 from plot.plot_area，方便查询）。 */
    private BigDecimal plotArea;

    /** 预计产量 = plot_area × crop.predicted_per。 */
    private BigDecimal expectedYield;

    /** 预计损失产量（灾害填写）。 */
    private BigDecimal lossYield;

    /** 实际产量（D14 CROSS-FLOW-002 采摘累计）。 */
    private BigDecimal actualYield;

    /** 平均亩产 = actual / area（完成时）。 */
    private BigDecimal averageYield;

    /** 种植班组 FK → {@code t_plant_work_team.id}。 */
    private Long plantBy;

    /** 采摘班组 FK → {@code t_plant_work_team.id}。 */
    private Long harvestBy;

    /** 是否游客采摘活动（字典 {@code djs_yes_no}：1=是 / 2=否）。 */
    private Integer isPick;

    /** DENGBO-R21/R24 采摘活动销售分摊结算轮次：0=未结算(当前批次)，&gt;0=已随第 N 次录入完成参与分摊。 */
    private Integer pickSettleRound;

    /**
     * 是否移栽调整（PLT-TRANSPLANT-REDO-001）：0=否 / 1=是。
     * 1 = 该明细由育苗移栽累计 100%（或手动结束移栽）自动落地生成，{@code plotId}=目标地块。
     * 配合 {@code t_plant_farm_records} 移栽记录（源地块→目标地块+比例）可追溯来源。
     */
    private Integer transplantAdjusted;

    @TableLogic
    private String delFlag;

    private Long delUnique;
}
