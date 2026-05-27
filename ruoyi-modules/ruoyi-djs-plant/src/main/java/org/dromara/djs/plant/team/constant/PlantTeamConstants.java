package org.dromara.djs.plant.team.constant;

/**
 * 种植班组常量（PLT-MD-002）。
 *
 * @author djs
 * @since PLT-MD-002
 */
public final class PlantTeamConstants {

    private PlantTeamConstants() {
    }

    /**
     * 种植部门 dept_id。
     *
     * <p>V1 单农场硬编码（D04-CLOSING seed 固定 201）；V2 多农场启用时改 {@code sys_config} key
     * {@code djs.plant.dept_id} 默认 201。</p>
     */
    public static final Long PLANT_DEPT_ID = 201L;

    /**
     * 班组状态 - 启用。
     */
    public static final Integer TEAM_STATUS_ACTIVE = 1;

    /**
     * 班组状态 - 停用。
     */
    public static final Integer TEAM_STATUS_INACTIVE = 2;

    /**
     * 成员 is_leader - 是。
     */
    public static final Integer IS_LEADER_YES = 1;

    /**
     * 成员 is_leader - 否。
     */
    public static final Integer IS_LEADER_NO = 2;
}
