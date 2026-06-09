package org.dromara.djs.common.domain.vo;

import lombok.Data;

import java.util.Set;

/**
 * 小程序"我的"页面 VO（{@code GET /applet/user/me} 响应）。
 *
 * <p>聚合 {@code sys_user} 主信息 + 当前农场名 + 当前用户角色 key 集合，前端用于渲染头像 / 昵称 /
 * 角色徽章 / 农场胶囊。所有字段都是只读快照，前端不应据此回写。</p>
 *
 * @author djs
 * @since MIN-INFRA-003
 */
@Data
public class UserMeVo {

    /** sys_user.user_id */
    private Long userId;

    /** sys_user.user_name（登录名） */
    private String username;

    /** sys_user.nick_name（昵称，UI 优先展示） */
    private String nickname;

    /** sys_user.phonenumber */
    private String phone;

    /** sys_user.email */
    private String email;

    /** sys_user.avatar — 走 ruoyi sys_oss.oss_id；前端按需调 /system/oss/getInfo 拿真实 URL */
    private Long avatarOssId;

    /** sys_user.sex — '0' 男 / '1' 女 / '2' 未知（字典 sys_user_sex） */
    private String sex;

    /** sys_user.dept_id */
    private Long deptId;

    /** sys_dept.dept_name */
    private String deptName;

    /** 当前农场 ID（sys_user.current_farm_id；V1 固定 '1001'） */
    private String currentFarmId;

    /** 当前农场名（V1 固定 '东角山主农场'） */
    private String currentFarmName;

    /**
     * 工作岗位中文名（来源 {@code sys_post.post_name}，经 {@code sys_user_post} 关联）。
     * <p>多岗位时按 {@code post_sort} 升序用「、」拼接（如「场长、兽医」）；无岗位返 null，
     * 前端展示「未设置岗位」。这是展示用快照，不参与权限（权限走 {@code roles}）。</p>
     * <p>对应 ADR-0007 sys_post 5 农场岗位：场长 / 饲养员 / 兽医 / 仓库管理员 / 技术员。</p>
     */
    private String postName;

    /**
     * 当前用户的 role_key 集合（来自 sa-token LoginUser.rolePermission）。
     * <p>用于前端按角色显示不同入口，如 admin / boss / breed_admin 等。</p>
     */
    private Set<String> roles;
}
