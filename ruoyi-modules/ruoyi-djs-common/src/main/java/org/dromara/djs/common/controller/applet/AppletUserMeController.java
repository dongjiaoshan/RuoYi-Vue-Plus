package org.dromara.djs.common.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.domain.vo.ContactVo;
import org.dromara.djs.common.domain.vo.UserMeVo;
import org.dromara.djs.common.service.IAppletUserMeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序"我的" + "通讯录" Controller（MIN-INFRA-003）。
 *
 * <p>URL 前缀 {@code /applet/user/*}，与 {@link AppletAuthController}（{@code /applet/auth/*}）/
 * {@link AppletOssStsController}（{@code /djs/applet/oss/sts/*}）保持小程序统一前缀风格。</p>
 *
 * <h2>端点</h2>
 *
 * <ul>
 *   <li>{@code GET /applet/user/me}        — 当前登录用户摘要（个人中心首屏）</li>
 *   <li>{@code GET /applet/user/contacts}  — 通讯录（按部门 / 角色分组列出全部员工）</li>
 * </ul>
 *
 * <h2>鉴权策略</h2>
 *
 * <ul>
 *   <li>{@code /me}：仅 {@code @SaCheckLogin}（任何登录用户都能拿自己的信息，不需要额外菜单权限）</li>
 *   <li>{@code /contacts}：仅 {@code @SaCheckLogin}（V1 全员可见通讯录；V2 若要按角色限制可改加
 *       {@code @SaCheckPermission("djs:user:contacts:query")}）</li>
 * </ul>
 *
 * <p>{@code userId} 一律走 {@code LoginHelper.getUserId()} 不允许从入参读，遵守 D03 注入约束 §0.5.5。</p>
 *
 * @author djs
 * @since MIN-INFRA-003
 */
@Slf4j
@RestController
@RequestMapping("/applet/user")
@RequiredArgsConstructor
public class AppletUserMeController {

    private final IAppletUserMeService appletUserMeService;

    /**
     * 当前登录用户的"我的"页面摘要。
     *
     * <p>响应示例：</p>
     * <pre>
     * {
     *   "userId": 9001,
     *   "username": "dev",
     *   "nickname": "dev 员工",
     *   "phone": "13800000001",
     *   "email": "dev@djs.com",
     *   "avatarOssId": null,
     *   "sex": "0",
     *   "deptId": 100,
     *   "deptName": "东角山农场",
     *   "currentFarmId": "1001",
     *   "currentFarmName": "东角山主农场",
     *   "roles": ["breed_admin", "store_clerk"]
     * }
     * </pre>
     *
     * <p>错误码：</p>
     * <ul>
     *   <li>401 — 未登录（sa-token 自动处理）</li>
     *   <li>404 — DB 中查不到 user_id（已删 / 禁用）</li>
     * </ul>
     */
    @SaCheckLogin
    @GetMapping("/me")
    public R<UserMeVo> me() {
        Long userId = LoginHelper.getUserId();
        UserMeVo me = appletUserMeService.queryMe(userId);
        if (me == null) {
            log.warn("[applet-user-me] /me user_id={} 查询为空", userId);
            return R.fail(404, "用户信息不存在或已禁用");
        }
        return R.ok(me);
    }

    /**
     * 通讯录查询。
     *
     * <p>请求示例：</p>
     * <pre>
     * GET /applet/user/contacts            # 列全部员工（含自己除外）
     * GET /applet/user/contacts?keyword=张   # 按昵称/姓名/手机号模糊
     * </pre>
     *
     * <p>响应是已按 dept_id, user_id 升序的 {@code List<ContactVo>}；前端按 deptName 二次分组。
     * 单次查询硬上限 500 条（sys_user 在 V1 不会超过这个数；超出时前端搜索 / 按部门切片处理）。</p>
     */
    @SaCheckLogin
    @GetMapping("/contacts")
    public R<List<ContactVo>> contacts(@RequestParam(required = false) String keyword) {
        Long currentUserId = LoginHelper.getUserId();
        return R.ok(appletUserMeService.queryContacts(currentUserId, keyword));
    }
}
