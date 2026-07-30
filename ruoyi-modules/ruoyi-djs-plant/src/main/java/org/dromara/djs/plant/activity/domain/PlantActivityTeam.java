package org.dromara.djs.plant.activity.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 采摘活动·班组多选中间表实体（G1-TEAMS-MULTISELECT，row40）。
 *
 * <p>对应表 {@code t_plant_activity_team}（V202608221300）：一行 {@code t_plant_plant_activity}
 * 可关联多个记录班组。旧单列 {@code activity_by}（DENGBO-R4 起已被 recorder_id 取代）保留过渡兼容。</p>
 *
 * @author djs
 * @since G1-TEAMS-MULTISELECT
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_activity_team")
public class PlantActivityTeam extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** FK → {@code t_plant_plant_activity.id}。 */
    private Long activityId;

    /** FK → {@code t_plant_work_team.id}。 */
    private Long teamId;

    @TableLogic
    private String delFlag;
}
