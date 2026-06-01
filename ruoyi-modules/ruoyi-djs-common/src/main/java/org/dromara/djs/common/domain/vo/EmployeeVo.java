package org.dromara.djs.common.domain.vo;

import lombok.Data;

/**
 * 小程序员工选择器单条记录 VO（{@code GET /djs/applet/common/employee/list} 响应元素）。
 *
 * <p>面向 mp 端各人员录入字段的 EmployeePicker（阉割人员 / 后续引种人员等）。与 {@link ContactVo}
 * 区别：ContactVo 面向通讯录页（含手机 / 邮箱 / 角色名分组，userId 是 {@code Long}），EmployeeVo
 * 面向"选一个员工写进业务字段"场景，{@code userId} 走 {@code String} —— snowflake 全链路 string
 * 跨层契约（防 JSON 19 位精度截断），mp 选中后直接把 userId 作为人员字段值提交。</p>
 *
 * @author djs
 * @since BRD-FIX-EMPLOYEE-PICKER-001
 */
@Data
public class EmployeeVo {

    /**
     * sys_user.user_id。
     *
     * <p>{@code String} 而非 {@code Long}：snowflake ID 19 位 > {@code 2^53}，JSON 给 mp 后
     * {@code Number()} 会截末位。mp 选中后把该值写进业务人员字段（如 castrater）提交。</p>
     */
    private String userId;

    /** sys_user.nick_name（picker 主显示文案，工人按昵称识别）。 */
    private String nickName;

    /** sys_user.user_name（登录名，picker 副文案 / 同名昵称时区分用）。 */
    private String userName;

    /** sys_dept.dept_name（picker 列表里按部门归类展示，可空）。 */
    private String deptName;
}
