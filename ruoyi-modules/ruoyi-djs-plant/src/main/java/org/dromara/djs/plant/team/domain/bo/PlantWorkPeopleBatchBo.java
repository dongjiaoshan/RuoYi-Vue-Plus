package org.dromara.djs.plant.team.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 班组批量加入成员入参（PLT-MD-002）。
 *
 * <p>service 在 INSERT 前会逐个校验：(1) team 存在且未删除；(2) user 在 sys_user 中存在且 dept_id=种植部；
 * (3) user 不在任何 active 班组。命中冲突抛 {@code plant.workTeam.alreadyInOtherTeam}。</p>
 *
 * @author djs
 * @since PLT-MD-002
 */
@Data
public class PlantWorkPeopleBatchBo {

    /**
     * 班组 ID。
     */
    @NotNull(message = "{plant.workTeam.teamId.required}")
    private Long teamId;

    /**
     * 待加入的 sys_user.user_id 列表。
     */
    @NotEmpty(message = "{plant.workTeam.userIds.required}")
    private List<Long> userIds;
}
