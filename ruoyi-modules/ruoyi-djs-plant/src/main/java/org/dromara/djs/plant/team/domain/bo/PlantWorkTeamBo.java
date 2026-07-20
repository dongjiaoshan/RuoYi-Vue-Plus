package org.dromara.djs.plant.team.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.plant.team.domain.PlantWorkTeam;

/**
 * 班组新增/修改入参 BO（PLT-MD-002）。
 *
 * <p>{@code leaderId} 不在新增 BO 直接设置——由"成员管理弹窗"中"设为负责人"动作统一切换
 * （service 层双写 {@link org.dromara.djs.plant.team.domain.PlantWorkPeople#getIsLeader()}）。</p>
 *
 * @author djs
 * @since PLT-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PlantWorkTeam.class, reverseConvertGenerate = false)
public class PlantWorkTeamBo extends BaseEntity {

    /**
     * 班组 ID（编辑时必填）。
     */
    private Long id;

    /**
     * 班组名称。
     */
    @NotBlank(message = "{plant.workTeam.name.required}")
    @Size(max = 64, message = "{plant.workTeam.name.size}")
    private String teamName;

    /**
     * 班组状态（1=启用 2=停用）。
     */
    private Integer teamStatus;

    /**
     * 备注。
     */
    @Size(max = 500, message = "{plant.workTeam.remark.size}")
    private String remark;
}
