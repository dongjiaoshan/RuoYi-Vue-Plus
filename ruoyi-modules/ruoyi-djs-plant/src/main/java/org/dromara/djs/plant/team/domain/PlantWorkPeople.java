package org.dromara.djs.plant.team.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.util.Date;

/**
 * 种植班组成员实体（PLT-MD-002）。
 *
 * <p>对应表 {@code t_plant_work_people}（V202606060900）。{@code userId} 关联
 * {@code sys_user.user_id}（ADR-0007：员工统一走 sys_user，不新建 t_md_person）。</p>
 *
 * <p>业务约束：一人只能属于一个班组（service INSERT 前校验 + UNIQUE(tenant_id, user_id, del_unique) 兜底）。</p>
 *
 * @author djs
 * @since PLT-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_work_people")
public class PlantWorkPeople extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（snowflake）。
     */
    @TableId
    private Long id;

    /**
     * FK {@code t_plant_work_team.id}。
     */
    private Long teamId;

    /**
     * FK {@code sys_user.user_id}（ADR-0007）。
     */
    private Long userId;

    /**
     * 是否班组负责人（1=是 2=否；与 {@link PlantWorkTeam#getLeaderId()} 双轨）。
     */
    private Integer isLeader;

    /**
     * 加入日期。
     */
    private Date joinDate;

    /**
     * 软删标记。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列。
     */
    private Long delUnique;
}
