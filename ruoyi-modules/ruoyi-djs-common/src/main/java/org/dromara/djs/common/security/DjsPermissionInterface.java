package org.dromara.djs.common.security;

import cn.dev33.satoken.stp.StpInterface;
import org.dromara.common.satoken.core.service.SaPermissionImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * djs 权限接口：在 ruoyi {@link SaPermissionImpl} 之上加「API 鉴权全局开关」。
 *
 * <p>V1 权限模型（Kevin 2026-07-08 拍板）：权限靠 admin 菜单 / 小程序 tab 的「显示 / 隐藏」控制，
 * 后端 API 不做细粒度 {@code @SaCheckPermission} 校验。开关 {@code djs.security.api-permission-check}：
 * <ul>
 *   <li>{@code false}（默认）→ {@link #getPermissionList} 返回 {@code *:*:*}，所有
 *       {@code @SaCheckPermission} 放行（仍需登录，只是不再逐权限校验）</li>
 *   <li>{@code true} → 委托 ruoyi 原生逻辑，按菜单权限精确校验（保留可随时回退）</li>
 * </ul>
 *
 * <p>只放行「权限」({@code getPermissionList})，「角色」({@code getRoleList}) 仍返回真实角色，
 * {@code @SaCheckRole} 照常生效。
 *
 * <p>{@code @Primary} 覆盖 {@link org.dromara.common.satoken.config.SaTokenConfig#stpInterface()}
 * 注册的默认实现，不修改 ruoyi 核心模块。同时因继承 {@link SaPermissionImpl}，开关打开时行为与原生完全一致。
 *
 * <p><b>注意</b>：本类只影响服务端 sa-token 的 {@code @SaCheckPermission} 拦截，不影响登录时
 * {@code getInfo} 下发给前端的 {@code permissions} 集合（那来自 {@code PermissionService.getMenuPermission}），
 * 因此小程序 tab / admin 按钮的「按权限显隐」不受本开关影响。
 */
@Primary
@Component
public class DjsPermissionInterface extends SaPermissionImpl {

    @Value("${djs.security.api-permission-check:false}")
    private boolean apiPermissionCheck;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (!apiPermissionCheck) {
            // 全局放行：等价超管 *:*:*，所有 @SaCheckPermission 通过
            List<String> allPass = new ArrayList<>(1);
            allPass.add("*:*:*");
            return allPass;
        }
        return super.getPermissionList(loginId, loginType);
    }
}
