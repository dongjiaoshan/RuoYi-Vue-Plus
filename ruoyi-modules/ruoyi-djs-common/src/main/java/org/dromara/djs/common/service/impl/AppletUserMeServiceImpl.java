package org.dromara.djs.common.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.constant.DjsAuthConstants;
import org.dromara.djs.common.domain.vo.ContactVo;
import org.dromara.djs.common.domain.vo.UserMeVo;
import org.dromara.djs.common.mapper.AppletUserQueryMapper;
import org.dromara.djs.common.service.IAppletUserMeService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 小程序"我的" + "通讯录" Service 实现（MIN-INFRA-003）。
 *
 * <p>纯只读，不涉及软删 / 编辑，故不继承 {@code DjsBaseServiceImpl}。所有查询都直接走
 * {@link AppletUserQueryMapper} 的原生 SQL，避开 MP wrapper 的复杂度。</p>
 *
 * <h2>角色来源</h2>
 *
 * <p>"我的"页的 roles 字段是 {@code role_key}（如 {@code admin} / {@code breed_admin}），来源为
 * sa-token {@code LoginUser.rolePermission}（不查 DB）；通讯录页每条记录的 roles 字段是
 * {@code role_name}（中文，如"超级管理员"），SQL GROUP_CONCAT 拼接后拆数组。两套字段用途不同，
 * 不要混淆。</p>
 *
 * @author djs
 * @since MIN-INFRA-003
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppletUserMeServiceImpl implements IAppletUserMeService {

    private final AppletUserQueryMapper appletUserQueryMapper;

    @Override
    public UserMeVo queryMe(Long userId) {
        if (userId == null) {
            return null;
        }
        UserMeVo me = appletUserQueryMapper.selectMeByUserId(userId);
        if (me == null) {
            log.warn("[applet-user-me] user_id={} 查询为空（已删或禁用）", userId);
            return null;
        }

        // V1 单农场：current_farm_id 若为空（mock 用户 / 老数据）回填默认值，避免前端拿到 null
        if (StrUtil.isBlank(me.getCurrentFarmId())) {
            me.setCurrentFarmId(DjsAuthConstants.DEFAULT_FARM_ID);
        }
        // V1 单农场名硬编码；V2 启用 sys_farm 表后由 farm service 查
        me.setCurrentFarmName(DjsAuthConstants.DEFAULT_FARM_NAME);

        // roles 直接取 sa-token LoginUser.rolePermission（与 /applet/auth/getInfo 一致）。
        // 单测 / 无 sa-token 上下文时 LoginHelper 会抛异常，捕获回退空集合。
        Set<String> roles = Collections.emptySet();
        try {
            LoginUser loginUser = LoginHelper.getLoginUser();
            if (loginUser != null && loginUser.getRolePermission() != null) {
                roles = loginUser.getRolePermission();
            }
        } catch (Exception e) {
            log.debug("[applet-user-me] LoginHelper.getLoginUser() failed, fallback empty roles: {}", e.getMessage());
        }
        me.setRoles(roles);
        return me;
    }

    @Override
    public List<ContactVo> queryContacts(Long currentUserId, String keyword) {
        if (currentUserId == null) {
            return Collections.emptyList();
        }
        // keyword 前后空白裁掉再判空，避免 "  " 命中 SQL <if>
        String normalizedKeyword = StrUtil.trimToNull(keyword);
        List<Map<String, Object>> rows = appletUserQueryMapper.selectContactsRaw(currentUserId, normalizedKeyword);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<ContactVo> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            ContactVo vo = new ContactVo();
            vo.setUserId(toLong(row.get("userId")));
            vo.setUsername(toStr(row.get("username")));
            vo.setNickname(toStr(row.get("nickname")));
            vo.setPhone(toStr(row.get("phone")));
            vo.setEmail(toStr(row.get("email")));
            vo.setAvatarOssId(toLong(row.get("avatarOssId")));
            vo.setDeptId(toLong(row.get("deptId")));
            vo.setDeptName(toStr(row.get("deptName")));
            // rolesRaw 是 GROUP_CONCAT 出来的逗号串；拆 + 去空 + 排序
            String rolesRaw = toStr(row.get("rolesRaw"));
            if (StrUtil.isBlank(rolesRaw)) {
                vo.setRoles(Collections.emptyList());
            } else {
                List<String> roles = new ArrayList<>();
                for (String part : rolesRaw.split(",")) {
                    String trimmed = StrUtil.trim(part);
                    if (StrUtil.isNotBlank(trimmed) && !roles.contains(trimmed)) {
                        roles.add(trimmed);
                    }
                }
                roles.sort(Comparator.naturalOrder());
                vo.setRoles(roles);
            }
            result.add(vo);
        }
        return result;
    }

    private static String toStr(Object v) {
        return v == null ? null : v.toString();
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unused")
    private static boolean nonNull(Object o) {
        return ObjectUtil.isNotNull(o);
    }
}
