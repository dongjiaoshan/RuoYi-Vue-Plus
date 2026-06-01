package org.dromara.djs.common.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.djs.common.domain.vo.EmployeeVo;
import org.dromara.djs.common.service.IAppletEmployeeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序员工选择器 Controller（BRD-FIX-EMPLOYEE-PICKER-001）。
 *
 * <p>path 走 ADR-0009 新范式 {@code /djs/applet/<module>/<biz>}：员工属通用主数据，module=common，
 * biz=employee。供 mp EmployeePicker 选人写进业务人员字段（V1 阉割人员；V1.x 可扩其他人员录入）。</p>
 *
 * <h2>端点</h2>
 *
 * <ul>
 *   <li>{@code GET /djs/applet/common/employee/list} — 列可选员工（启用 + 未删除）</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 *
 * <p>{@code @SaCheckLogin}：登录后才点 picker，任何登录工人都能查同租户可选员工（V1 单农场全员可见）。
 * 不用 {@code @SaIgnore}（这不是 onLaunch 前置端点）。</p>
 *
 * @author djs
 * @since BRD-FIX-EMPLOYEE-PICKER-001
 */
@Slf4j
@RestController
@RequestMapping("/djs/applet/common/employee")
@RequiredArgsConstructor
public class EmployeeAppletController {

    private final IAppletEmployeeService appletEmployeeService;

    /**
     * 员工列表查询。
     *
     * <p>请求示例：</p>
     * <pre>
     * GET /djs/applet/common/employee/list                  # 列全部可选员工
     * GET /djs/applet/common/employee/list?keyword=张        # 按昵称 / 登录名模糊
     * GET /djs/applet/common/employee/list?role=breed_worker # 仅养殖工人
     * </pre>
     *
     * <p>响应是已按 dept_id, user_id 升序的 {@code List<EmployeeVo>}，每项 {@code userId} 为
     * String（snowflake 防截断）。mp 选中后把 userId 作为人员字段值提交。</p>
     *
     * @param keyword 可选关键字（按 nick_name / user_name 模糊）
     * @param role    可选 role_key（按角色过滤，如 {@code breed_worker}）
     */
    @SaCheckLogin
    @GetMapping("/list")
    public R<List<EmployeeVo>> list(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String role) {
        return R.ok(appletEmployeeService.queryEmployees(keyword, role));
    }
}
