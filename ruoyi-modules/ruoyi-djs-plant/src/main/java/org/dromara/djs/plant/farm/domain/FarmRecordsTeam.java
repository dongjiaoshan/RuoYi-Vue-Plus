package org.dromara.djs.plant.farm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 农事记录·班组多选中间表实体（G1-TEAMS-MULTISELECT，row37）。
 *
 * <p>对应表 {@code t_farm_records_team}（V202608221300）：一行 {@code t_plant_farm_records}
 * 可关联多个处理班组。旧单列 {@code farm_by} 保留过渡兼容（存多选第一个，班组绩效等单值聚合口径不变）。</p>
 *
 * @author djs
 * @since G1-TEAMS-MULTISELECT
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_records_team")
public class FarmRecordsTeam extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** FK → {@code t_plant_farm_records.id}。 */
    private Long recordId;

    /** FK → {@code t_plant_work_team.id}。 */
    private Long teamId;

    @TableLogic
    private String delFlag;
}
