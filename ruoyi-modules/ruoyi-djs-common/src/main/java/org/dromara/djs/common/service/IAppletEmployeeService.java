package org.dromara.djs.common.service;

import org.dromara.djs.common.domain.vo.EmployeeVo;

import java.util.List;

/**
 * 小程序员工选择器 Service（BRD-FIX-EMPLOYEE-PICKER-001）。
 *
 * <p>对应端点 {@code GET /djs/applet/common/employee/list}，供 mp EmployeePicker 选人写进
 * 业务人员字段（V1 阉割人员 castrater；V1.x 可扩引种 / 出栏等人员录入）。纯只读。</p>
 *
 * @author djs
 * @since BRD-FIX-EMPLOYEE-PICKER-001
 */
public interface IAppletEmployeeService {

    /**
     * 查询可选员工列表（启用 + 未删除）。
     *
     * @param keyword 可选关键字（按 nick_name / user_name 模糊；null 或空串不过滤）
     * @param role    可选 role_key（按角色过滤，如 {@code breed_worker}；null 或空串则全员可选）
     * @return 员工列表（userId 为 String，已按部门 / userId 升序）；无命中返空数组
     */
    List<EmployeeVo> queryEmployees(String keyword, String role);
}
