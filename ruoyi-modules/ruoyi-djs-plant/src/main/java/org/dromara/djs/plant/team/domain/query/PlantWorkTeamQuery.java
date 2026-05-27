package org.dromara.djs.plant.team.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 班组查询参数（PLT-MD-002）。
 *
 * @author djs
 * @since PLT-MD-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlantWorkTeamQuery extends BaseEntity {

    /**
     * 班组名称（模糊匹配）。
     */
    private String teamName;

    /**
     * 班组状态（1=启用 2=停用）。
     */
    private Integer teamStatus;

    /**
     * 负责人 user_id（精确匹配，可空）。
     */
    private Long leaderId;
}
