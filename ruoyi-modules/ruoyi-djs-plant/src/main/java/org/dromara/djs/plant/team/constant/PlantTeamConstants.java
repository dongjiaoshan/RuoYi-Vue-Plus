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
     * 种植部门 dept_id（D04-CLOSING seed 固定 201）。
     *
     * <p>V1 单农场场景硬编码；V2 多农场启用时改读 {@code sys_config}（如 {@code djs.plant.dept_id}）
     * 或按 farm context 动态识别。详 {@code doc/_open-issues.md} 跨 ticket follow-up。</p>
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
