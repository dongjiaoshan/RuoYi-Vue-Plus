package org.dromara.djs.common.service;

import org.dromara.djs.common.domain.vo.ContactVo;
import org.dromara.djs.common.domain.vo.UserMeVo;

import java.util.List;

/**
 * 小程序"我的" + "通讯录" Service（MIN-INFRA-003）。
 *
 * <p>对应两个端点：</p>
 * <ul>
 *   <li>{@code GET /applet/user/me}：当前登录用户摘要</li>
 *   <li>{@code GET /applet/user/contacts}：通讯录（按部门 / 角色分组列出员工）</li>
 * </ul>
 *
 * <p>没有写端点。修改用户信息走 ruoyi 自带 {@code /system/user/profile/*}。</p>
 *
 * @author djs
 * @since MIN-INFRA-003
 */
public interface IAppletUserMeService {

    /**
     * 拿当前登录用户的"我的"页面摘要（{@code sys_user} 主信息 + 部门名 + 当前农场名 + 角色 keys）。
     *
     * @param userId 当前登录用户 id（由 controller 从 {@code LoginHelper.getUserId()} 读取，不允许从入参传）
     * @return UserMeVo；DB 中查不到用户（已删 / 禁用）时返 null，controller 转 404
     */
    UserMeVo queryMe(Long userId);

    /**
     * 通讯录查询。
     *
     * @param currentUserId 当前登录用户 id（用于排除自己 & V2 农场过滤）
     * @param keyword 可选搜索关键字（昵称 / 用户名 / 手机号模糊匹配；null 或空串则不过滤）
     * @return 通讯录列表（已按 dept_id, user_id 升序，前端按 deptName 分组渲染）
     */
    List<ContactVo> queryContacts(Long currentUserId, String keyword);

    /**
     * 解绑当前账号的微信（清空 sys_user.wx_openid）。
     *
     * <p>「我的」页微信绑定卡片「解绑」触发。解绑后该微信可重新走绑手机号流程绑到别的员工，
     * 本账号也可重新微信登录后重新绑定。幂等（未绑定时清空 0 行也算成功）。</p>
     *
     * @param userId 当前登录用户 id（controller 从 {@code LoginHelper.getUserId()} 读，不允许入参传）
     */
    void unbindWechat(Long userId);
}
