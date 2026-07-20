package org.dromara.djs.common.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.djs.common.domain.vo.EmployeeVo;
import org.dromara.djs.common.mapper.AppletUserQueryMapper;
import org.dromara.djs.common.service.IAppletEmployeeService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 员工选择器 Service 实现（BRD-FIX-EMPLOYEE-PICKER-001）。
 *
 * <p>纯只读，复用 {@link AppletUserQueryMapper} 的原生 SQL 查 {@code sys_user}（按强约束 #1 不动
 * ruoyi system 模块源码）。mapper 出口 {@code List<Map>}，本层手工拷贝并把 {@code userId} 从
 * {@code Long} 转 {@code String}（snowflake 全链路 string 跨层契约），装配成 EmployeeVo。</p>
 *
 * @author djs
 * @since BRD-FIX-EMPLOYEE-PICKER-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppletEmployeeServiceImpl implements IAppletEmployeeService {

    private final AppletUserQueryMapper appletUserQueryMapper;

    @Override
    public List<EmployeeVo> queryEmployees(String keyword, String role, Long deptId) {
        // 前后空白裁掉再判空，避免 "  " 命中 SQL <if>
        String normalizedKeyword = StrUtil.trimToNull(keyword);
        String normalizedRole = StrUtil.trimToNull(role);
        List<Map<String, Object>> rows =
            appletUserQueryMapper.selectEmployeesRaw(normalizedKeyword, normalizedRole, deptId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<EmployeeVo> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            EmployeeVo vo = new EmployeeVo();
            // userId 转 String：snowflake 19 位，给 mp 走 string 防 Number() 截断
            vo.setUserId(toStr(row.get("userId")));
            vo.setUserName(toStr(row.get("userName")));
            vo.setNickName(toStr(row.get("nickName")));
            vo.setDeptName(toStr(row.get("deptName")));
            result.add(vo);
        }
        return result;
    }

    private static String toStr(Object v) {
        return v == null ? null : v.toString();
    }
}
