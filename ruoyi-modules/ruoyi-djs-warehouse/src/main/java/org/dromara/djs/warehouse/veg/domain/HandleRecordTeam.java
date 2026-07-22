package org.dromara.djs.warehouse.veg.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 采摘重量录入·采摘班组多选中间表实体（ROW64）。
 *
 * <p>对应表 {@code t_warehouse_handle_record_team}（V202608221700）：一条 record_type=1 的
 * {@code t_warehouse_handle_record}（采收行）可关联多个采摘班组。旧单列
 * {@code t_warehouse_handle_record.team_id} 写多选第一个作过渡兼容，row39 班组绩效聚合口径不变。</p>
 *
 * @author djs
 * @since ROW64
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_handle_record_team")
public class HandleRecordTeam extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** FK → {@code t_warehouse_handle_record.id}（record_type=1 采收行）。 */
    private Long recordId;

    /** FK → {@code t_plant_work_team.id}。 */
    private Long teamId;

    @TableLogic
    private String delFlag;

    public HandleRecordTeam() {
    }

    public HandleRecordTeam(Long recordId, Long teamId) {
        this.recordId = recordId;
        this.teamId = teamId;
    }
}
