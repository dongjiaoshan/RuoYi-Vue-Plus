package org.dromara.djs.common.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 小程序通讯录单条记录 VO（{@code GET /applet/user/contacts} 响应元素）。
 *
 * <p>面向小程序通讯录页（按部门 / 角色分组显示）。{@code roles} 是 role_name 中文集合，
 * 前端展示用，不参与权限判断。</p>
 *
 * @author djs
 * @since MIN-INFRA-003
 */
@Data
public class ContactVo {

    /** sys_user.user_id（前端点击拨号 / 复制邮箱时定位） */
    private Long userId;

    /** sys_user.user_name */
    private String username;

    /** sys_user.nick_name */
    private String nickname;

    /** sys_user.phonenumber（可空，未填手机的员工不显示拨号按钮） */
    private String phone;

    /** sys_user.email（可空） */
    private String email;

    /** sys_user.avatar oss_id（前端通用占位） */
    private Long avatarOssId;

    /** sys_user.dept_id */
    private Long deptId;

    /** sys_dept.dept_name（分组 key，UI 上按此分组渲染） */
    private String deptName;

    /** 当前用户的角色名集合（role_name 中文，逗号拼接前由 mapper 拆成数组） */
    private List<String> roles;
}
