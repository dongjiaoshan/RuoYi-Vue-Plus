package org.dromara.djs.common.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.djs.common.mapper.DjsUserExtMapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Admin 端 sys_user 微信绑定管理 Controller。
 *
 * <p>给 plus-ui「系统/用户」列表提供微信绑定状态列 + 解绑操作。员工微信登录走手机号匹配
 * （{@link org.dromara.djs.common.service.IWechatLoginService#bindPhone}）后回写 wx_openid，
 * 本 controller 让管理员看到谁已绑、换人时一键解绑。</p>
 *
 * <p>独立于 ruoyi 自带 {@code SysUserController}（强约束 #1 不动 ruoyi 自带模块源码），
 * 复用 ruoyi 的 {@code system:user:*} 权限串保持一致。</p>
 *
 * @author djs
 */
@Slf4j
@RestController
@RequestMapping("/djs/system/wx-bind")
@RequiredArgsConstructor
public class DjsWxBindController {

    private final DjsUserExtMapper djsUserExtMapper;

    /**
     * 批量查询用户微信绑定状态（列表渲染用）。
     *
     * <p>入参 user_id 数组，返回每个用户的 {@code bound} + 脱敏 openid 尾号；空入参返空列表。</p>
     */
    @SaCheckPermission("system:user:list")
    @PostMapping("/status")
    public R<List<WxBindStatusVo>> status(@RequestBody Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return R.ok(List.of());
        }
        List<Map<String, Object>> rows = djsUserExtMapper.selectWxOpenidByIds(userIds);
        List<WxBindStatusVo> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Long userId = ((Number) row.get("userId")).longValue();
            String openid = row.get("wxOpenid") == null ? null : row.get("wxOpenid").toString();
            boolean bound = StrUtil.isNotBlank(openid);
            result.add(new WxBindStatusVo(userId, bound, bound ? maskOpenid(openid) : null));
        }
        return R.ok(result);
    }

    /**
     * 解绑指定用户的微信（清空 wx_openid）。换人 / 误绑时用。
     */
    @SaCheckPermission("system:user:edit")
    @Log(title = "用户微信解绑", businessType = BusinessType.UPDATE)
    @DeleteMapping("/{userId}")
    public R<Void> unbind(@PathVariable Long userId) {
        int rows = djsUserExtMapper.clearWxOpenid(userId);
        log.info("[wx-bind] 解绑 userId={} 影响行数={}", userId, rows);
        return rows > 0 ? R.ok() : R.fail("解绑失败：用户不存在或未绑定");
    }

    /** openid 脱敏：仅保留尾 4 位（admin 参考用，不暴露完整 openid）。 */
    private static String maskOpenid(String openid) {
        if (openid.length() <= 4) {
            return "****";
        }
        return "****" + openid.substring(openid.length() - 4);
    }

    /**
     * 微信绑定状态 VO。
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class WxBindStatusVo {
        /** 用户 ID */
        private Long userId;
        /** 是否已绑定微信 */
        private boolean bound;
        /** 脱敏 openid 尾号（未绑定为 null） */
        private String openidTail;
    }
}
