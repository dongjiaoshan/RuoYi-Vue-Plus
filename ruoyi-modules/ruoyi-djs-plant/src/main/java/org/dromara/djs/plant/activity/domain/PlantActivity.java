package org.dromara.djs.plant.activity.domain;

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
 * 采摘活动实体（FIX-PLT-HARVEST-ACTIVITY-001）。
 *
 * <p>对应表 {@code t_plant_plant_activity}（V202606291500，邓博权威 spec 新增表）：
 * 采摘重量录入流水表，每次 mp 采摘重量录入一行，
 * 报表 {@code SUM(daily_weight) GROUP BY crop_id, activity_date}。</p>
 *
 * <p>UNIQUE(tenant_id, crop_id, activity_date, activity_by, del_unique)：
 * 同作物同日同班组唯一，{@code recordDailyWeight} 按此幂等累加。</p>
 *
 * @author djs
 * @since FIX-PLT-HARVEST-ACTIVITY-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_plant_activity")
public class PlantActivity extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 采摘日期。 */
    private LocalDate activityDate;

    /** FK → {@code t_plant_crop_info.id}。 */
    private Long cropId;

    /** 当日采摘重量（kg，同 crop+date+班组累加）。 */
    private BigDecimal dailyWeight;

    /** 记录班组 FK → {@code t_plant_work_team.id}。 */
    private Long activityBy;

    @TableLogic
    private String delFlag;

    private Long delUnique;
}
