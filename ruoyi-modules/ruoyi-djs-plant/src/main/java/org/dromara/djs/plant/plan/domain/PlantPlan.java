package org.dromara.djs.plant.plan.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;
import org.dromara.djs.common.base.DjsBaseServiceImpl;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 种植计划主表实体（PLT-PLAN-001）。
 *
 * <p>对应表 {@code t_plant_plant_plan}（V202606071400）：</p>
 * <ul>
 *   <li>{@code planNo} 业务码 {@code PLAN-yyyy-NNN}，service 层 inline 生成（{@link org.dromara.djs.plant.plan.service.impl.PlantPlanServiceImpl#nextPlanNo}）</li>
 *   <li>{@code earliestHarvestdate} / {@code lastHarvestdate} / {@code totalArea} / {@code totalPlot} 4 个聚合字段
 *       由 {@code PlantPlanMapper.recalcAggregates} 从明细表聚合后写回</li>
 *   <li>状态机 {@code plantStatus}：pending → ongoing → completed / delayed，{@code djs_plant_plan_status}</li>
 *   <li>下游：{@code t_plant_plant_details} (plant_id FK) + {@code t_plant_farm_records}（D10 PLT-WORK-001）</li>
 * </ul>
 *
 * <p>软删走 {@link DjsBaseServiceImpl#softDelete}。</p>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_plant_plan")
public class PlantPlan extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 业务码 {@code PLAN-yyyy-NNN}（UNIQUE(tenant_id, plan_no, del_unique)）。 */
    private String planNo;

    /** 计划年份（例 2026）。 */
    private Integer planYear;

    /** 作物 FK → {@code t_plant_crop_info.id}。 */
    private Long cropId;

    /** 计划种植时间（自由文本 例 "4月上旬"，xlsx R8 兼容字段）。 */
    private String plantDate;

    /** 计划季节（字典 {@code djs_planting_season}：spring/summer/autumn/winter）。 */
    private String planSeason;

    /** 由明细表聚合 MIN(details.earliest_harvestdate)。 */
    private LocalDate earliestHarvestdate;

    /** 由明细表聚合 MAX(details.last_harvestdate)。 */
    private LocalDate lastHarvestdate;

    /** 总面积（亩），SUM(details.plot_area)。 */
    private BigDecimal totalArea;

    /** 地块数 COUNT(details)。 */
    private Integer totalPlot;

    /** 计划状态（字典 {@code djs_plant_plan_status}）。 */
    private String plantStatus;

    @TableLogic
    private String delFlag;

    private Long delUnique;
}
