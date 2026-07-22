package org.dromara.djs.plant.plan.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 种植计划明细·班组多选中间表实体（G1-TEAMS-MULTISELECT，row36）。
 *
 * <p>对应表 {@code t_plant_details_team}（V202608221300）：一行 {@code t_plant_plant_details}
 * 可关联多个种植班组（role=plant）与多个采摘班组（role=harvest）。
 * 旧单列 {@code plant_by / harvest_by} 保留过渡兼容，存多选第一个。</p>
 *
 * @author djs
 * @since G1-TEAMS-MULTISELECT
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_details_team")
public class PlantDetailsTeam extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** FK → {@code t_plant_plant_details.id}。 */
    private Long detailId;

    /** FK → {@code t_plant_work_team.id}。 */
    private Long teamId;

    /** 班组角色：{@code plant}=种植班组 / {@code harvest}=采摘班组。 */
    private String role;

    @TableLogic
    private String delFlag;
}
