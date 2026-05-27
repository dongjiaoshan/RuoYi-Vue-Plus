package org.dromara.djs.plant.team.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;
import org.dromara.djs.common.base.DjsBaseServiceImpl;

import java.io.Serial;

/**
 * 种植班组实体（PLT-MD-002）。
 *
 * <p>对应表 {@code t_plant_work_team}（V202606060900）。</p>
 *
 * <p><b>leader 字段双轨</b>：本实体 {@code leaderId} 与 {@link PlantWorkPeople#getIsLeader()}
 * 同时表达"谁是负责人"。service 层负责双写一致性（设置负责人时同步 update 两处）。
 * 双轨保留出于 doc/11 §1.11/§1.12 字段权威；V2 客户需求清晰后再考虑合并。</p>
 *
 * <p>软删走 {@link DjsBaseServiceImpl#softDelete}（{@code del_flag} + {@code del_unique}）。</p>
 *
 * @author djs
 * @since PLT-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_work_team")
public class PlantWorkTeam extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（snowflake）。
     */
    @TableId
    private Long id;

    /**
     * 班组名称（UNIQUE(tenant_id, team_name, del_unique)）。
     */
    private String teamName;

    /**
     * 班组负责人 sys_user.user_id（可空；与 {@code PlantWorkPeople.isLeader} 双轨）。
     */
    private Long leaderId;

    /**
     * 班组状态（字典 {@code djs_common_status}：1=启用 2=停用）。
     */
    private Integer teamStatus;

    /**
     * 软删标记（{@code 0}=正常 / {@code 1}=已删；走 MP {@code @TableLogic}）。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列（活动行=0；软删行=id）。
     */
    private Long delUnique;
}
