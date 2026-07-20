package org.dromara.djs.common.service.impl;

import org.dromara.djs.common.domain.vo.EmployeeVo;
import org.dromara.djs.common.mapper.AppletUserQueryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AppletEmployeeService happy path 单测（纯 Mockito，不启 Spring）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>{@code queryEmployees} 把 mapper 的 {@code List<Map>} 装配成 EmployeeVo，并把
 *       userId 从 Long 转 String（snowflake 全链路 string）</li>
 *   <li>{@code queryEmployees} keyword / role 含空白时 trim 成 null（避免空串走 SQL LIKE / 角色 JOIN）</li>
 *   <li>{@code queryEmployees} mapper 返空 → service 返空数组</li>
 *   <li>{@code queryEmployees} role 过滤透传 role_key 到 mapper</li>
 *   <li>{@code queryEmployees} deptId 透传到 mapper（row34 养殖部过滤）</li>
 * </ul>
 *
 * @author djs
 * @since BRD-FIX-EMPLOYEE-PICKER-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@DisplayName("AppletEmployeeServiceImpl happy path 单测")
class AppletEmployeeServiceImplTest {

    @Mock
    private AppletUserQueryMapper appletUserQueryMapper;

    @InjectMocks
    private AppletEmployeeServiceImpl service;

    @Test
    @DisplayName("queryEmployees — 装配 EmployeeVo + userId Long→String")
    void queryEmployees_assembleAndStringifyUserId() {
        Map<String, Object> row = new HashMap<>();
        // 模拟 snowflake 19 位 long
        row.put("userId", 2058525064717926401L);
        row.put("userName", "zhangsan");
        row.put("nickName", "张三");
        row.put("deptName", "东角山农场");
        when(appletUserQueryMapper.selectEmployeesRaw(eq(null), eq(null), eq(null)))
            .thenReturn(List.of(row));

        List<EmployeeVo> list = service.queryEmployees(null, null, null);

        assertEquals(1, list.size());
        EmployeeVo vo = list.get(0);
        assertEquals("2058525064717926401", vo.getUserId(),
            "userId 必须是无精度损失的 String（snowflake 19 位）");
        assertEquals("张三", vo.getNickName());
        assertEquals("zhangsan", vo.getUserName());
        assertEquals("东角山农场", vo.getDeptName());
    }

    @Test
    @DisplayName("queryEmployees — keyword / role 含空白 trim 成 null")
    void queryEmployees_blankNormalize() {
        when(appletUserQueryMapper.selectEmployeesRaw(eq(null), eq(null), eq(null)))
            .thenReturn(Collections.emptyList());

        List<EmployeeVo> list = service.queryEmployees("   ", "  ", null);

        assertTrue(list.isEmpty());
        // 验 mapper 收到 null 而不是空白串
        verify(appletUserQueryMapper).selectEmployeesRaw(eq(null), eq(null), eq(null));
    }

    @Test
    @DisplayName("queryEmployees — mapper 返空 → service 返空数组")
    void queryEmployees_emptyResult() {
        when(appletUserQueryMapper.selectEmployeesRaw(eq("张"), eq(null), eq(null)))
            .thenReturn(Collections.emptyList());

        List<EmployeeVo> list = service.queryEmployees("张", null, null);
        assertTrue(list.isEmpty());
    }

    @Test
    @DisplayName("queryEmployees — role 过滤透传 role_key 到 mapper")
    void queryEmployees_roleFilterPassThrough() {
        Map<String, Object> row = new HashMap<>();
        row.put("userId", 108L);
        row.put("userName", "worker1");
        row.put("nickName", "养殖工人甲");
        row.put("deptName", "东角山农场");
        when(appletUserQueryMapper.selectEmployeesRaw(eq(null), eq("breed_worker"), eq(null)))
            .thenReturn(List.of(row));

        List<EmployeeVo> list = service.queryEmployees(null, "breed_worker", null);

        assertEquals(1, list.size());
        assertEquals("108", list.get(0).getUserId());
        verify(appletUserQueryMapper).selectEmployeesRaw(eq(null), eq("breed_worker"), eq(null));
    }

    @Test
    @DisplayName("queryEmployees — deptId 透传到 mapper（row34 养殖部过滤）")
    void queryEmployees_deptIdPassThrough() {
        Map<String, Object> row = new HashMap<>();
        row.put("userId", 200L);
        row.put("userName", "worker2");
        row.put("nickName", "养殖工人乙");
        row.put("deptName", "东角山-养殖部");
        when(appletUserQueryMapper.selectEmployeesRaw(eq(null), eq(null), eq(200L)))
            .thenReturn(List.of(row));

        List<EmployeeVo> list = service.queryEmployees(null, null, 200L);

        assertEquals(1, list.size());
        assertEquals("东角山-养殖部", list.get(0).getDeptName());
        verify(appletUserQueryMapper).selectEmployeesRaw(eq(null), eq(null), eq(200L));
    }
}
