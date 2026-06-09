package org.dromara.djs.plant.farm.domain;

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
 * 农事记录实体（PLT-WORK-001）。
 *
 * <p>对应表 {@code t_plant_farm_records}（V202606101300，doc/11 §1.9）。
 * 一张表覆盖 12 类农事，{@code farmType} 字段区分：</p>
 *
 * <ul>
 *   <li>空地阶段 3 类：tillage_break / tillage_prepare / fertilize（整地额外 tillage_type/tillage_method）</li>
 *   <li>生长阶段 7 类：transplant / water_fertilize / irrigation / weed / pest_control / pruning / rotation</li>
 *   <li>其他 2 类：disaster / harvest_activity</li>
 * </ul>
 *
 * <p>独立画布：</p>
 * <ul>
 *   <li>移栽（farm_type=transplant）：transplant_plot + transplant_percent（≤60）</li>
 *   <li>灾害（farm_type=disaster）：disaster_type + loss_rate + loss_yield；触发 plant_details.loss_yield 累加</li>
 *   <li>退茬（farm_type=rotation）：触发 plot_info.plot_status=1（空闲）+ plant_details.plant_status=completed</li>
 * </ul>
 *
 * @author djs
 * @since PLT-WORK-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_farm_records")
public class FarmRecords extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 业务码 FR+yyyyMMdd+4 位（inline mapper.selectMaxByDate）。 */
    private String recordNo;

    /** FK → {@code t_plant_plant_plan.id}；空地处理时为空。 */
    private Long plantId;

    /** FK → {@code t_plant_plot_info.id}。 */
    private Long plotId;

    /** 地块类型快照（字典 {@code djs_plot_status}：1=空闲 / 2=种植 / 3=采摘）。 */
    private Integer plotType;

    /** FK → {@code t_plant_crop_info.id}；空地时为空。 */
    private Long cropId;

    /** 冗余作物名。 */
    private String cropName;

    /** 农事类型（字典 {@code djs_farm_work_type}，12 类）。 */
    private String farmType;

    /** 农事日期。 */
    private LocalDate farmDate;

    /** 处理班组 FK → {@code t_plant_work_team.id}。 */
    private Long farmBy;

    /** 操作人（采摘人员）FK → {@code sys_user.user_id}（FIX-PLT-MP-PICK-001；采收 / 采摘状态调整时落班组成员）。 */
    private Long operatorUserId;

    /** 整地类型（字典 {@code djs_tillage_type}；仅 farm_type=tillage_prepare）。 */
    private String tillageType;

    /** 整地方式（字典 {@code djs_tillage_way}；仅 farm_type=tillage_prepare）。 */
    private String tillageMethod;

    /** 转移地块 FK → {@code t_plant_plot_info.id}；仅 farm_type=transplant。 */
    private Long transplantPlot;

    /** 移栽百分比 0-100（业务规则 ≤60）；仅 farm_type=transplant。 */
    private Integer transplantPercent;

    /** 灾害类型（字典 {@code djs_disaster_type}）；仅 farm_type=disaster。 */
    private String disasterType;

    /** 损失率 0-100；仅 farm_type=disaster。 */
    private BigDecimal lossRate;

    /** 损失产量；仅 farm_type=disaster。触发 plant_details.loss_yield 累加。 */
    private BigDecimal lossYield;

    /** 预警标记（字典 {@code djs_yes_no}：1=是 / 2=否；loss_rate≥30 时置 1）。 */
    private Integer isWarning;

    /** OSS bizType=plant_farm_proof 凭证图，逗号分隔 ossIds。 */
    private String proofOssIds;

    /** 业务备注（DDL t_plant_farm_records.remark；与 ruoyi BaseEntity 不重叠，单独声明）。 */
    private String remark;

    @TableLogic
    private String delFlag;

    private Long delUnique;
}
