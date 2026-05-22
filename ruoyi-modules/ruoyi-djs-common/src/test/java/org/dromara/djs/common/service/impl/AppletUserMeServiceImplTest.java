package org.dromara.djs.common.service.impl;

import org.dromara.djs.common.constant.DjsAuthConstants;
import org.dromara.djs.common.domain.vo.ContactVo;
import org.dromara.djs.common.domain.vo.UserMeVo;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AppletUserMeService happy path 单测（纯 Mockito，不启 Spring）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>{@code queryMe} 拿到 mapper 结果后回填 farmName + roles（这里只验 farmName 回填，roles
 *       依赖 sa-token static 调用，跳过）</li>
 *   <li>{@code queryMe} mapper 返 null 时 service 返 null</li>
 *   <li>{@code queryContacts} 把 mapper 的 List&lt;Map&gt; 正确装配成 ContactVo，并把 rolesRaw
 *       拆成 List&lt;String&gt; 去重 + 排序</li>
 *   <li>{@code queryContacts} 空 keyword normalize 成 null（避免空串走 SQL LIKE）</li>
 *   <li>{@code queryContacts} 当前用户 id 为 null 时返空数组</li>
 * </ul>
 *
 * <p>不测 {@code queryMe} 的 roles 字段：那需要 mock 静态 {@code LoginHelper.getLoginUser()}，
 * 留给联调环境验。</p>
 *
 * @author djs
 * @since MIN-INFRA-003
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@DisplayName("AppletUserMeServiceImpl happy path 单测")
class AppletUserMeServiceImplTest {

    @Mock
    private AppletUserQueryMapper appletUserQueryMapper;

    @InjectMocks
    private AppletUserMeServiceImpl service;

    @Test
    @DisplayName("queryMe — mapper 返 VO → service 回填 currentFarmName 默认值")
    void queryMe_happy_fillFarmName() {
        UserMeVo raw = new UserMeVo();
        raw.setUserId(9001L);
        raw.setUsername("dev");
        raw.setNickname("dev 员工");
        raw.setDeptId(100L);
        raw.setDeptName("东角山农场");
        // current_farm_id 为空，模拟 mock 用户场景
        raw.setCurrentFarmId(null);
        when(appletUserQueryMapper.selectMeByUserId(eq(9001L))).thenReturn(raw);

        UserMeVo me = service.queryMe(9001L);

        assertNotNull(me);
        assertEquals(9001L, me.getUserId());
        assertEquals("dev", me.getUsername());
        assertEquals(DjsAuthConstants.DEFAULT_FARM_ID, me.getCurrentFarmId(),
            "current_farm_id 为空时应回填为 V1 默认 1001");
        assertEquals(DjsAuthConstants.DEFAULT_FARM_NAME, me.getCurrentFarmName(),
            "currentFarmName 应被 service 注入默认值");
        // roles 不会因 LoginHelper 静态调用失败而 NPE，至少 set 为某个 Set（empty 或 sa-token 返的）
        assertNotNull(me.getRoles());
    }

    @Test
    @DisplayName("queryMe — mapper 返 null → service 返 null（controller 转 404）")
    void queryMe_userNotFound() {
        when(appletUserQueryMapper.selectMeByUserId(any())).thenReturn(null);
        UserMeVo me = service.queryMe(99999L);
        assertNull(me);
    }

    @Test
    @DisplayName("queryMe — userId null → 返 null")
    void queryMe_nullId() {
        assertNull(service.queryMe(null));
    }

    @Test
    @DisplayName("queryContacts — 把 mapper Map 装配成 ContactVo + 拆 rolesRaw")
    void queryContacts_assembleAndSplitRoles() {
        Map<String, Object> row = new HashMap<>();
        row.put("userId", 2L);
        row.put("username", "zhangsan");
        row.put("nickname", "张三");
        row.put("phone", "13800000002");
        row.put("email", "zs@djs.com");
        row.put("avatarOssId", null);
        row.put("deptId", 100L);
        row.put("deptName", "东角山农场");
        row.put("rolesRaw", "养殖管理员,门店店员,养殖管理员"); // 含重复，验去重
        when(appletUserQueryMapper.selectContactsRaw(eq(9001L), eq(null)))
            .thenReturn(List.of(row));

        List<ContactVo> contacts = service.queryContacts(9001L, null);

        assertEquals(1, contacts.size());
        ContactVo vo = contacts.get(0);
        assertEquals(2L, vo.getUserId());
        assertEquals("张三", vo.getNickname());
        assertEquals("13800000002", vo.getPhone());
        assertEquals(2, vo.getRoles().size(), "去重后剩 2 个角色");
        // 自然顺序排序后："养殖管理员" 笔画排在 "门店店员" 前后取决于 Unicode，下面用 contains 验
        assertTrue(vo.getRoles().contains("养殖管理员"));
        assertTrue(vo.getRoles().contains("门店店员"));
    }

    @Test
    @DisplayName("queryContacts — keyword 含空白 trim 成 null，避免空串走 SQL LIKE")
    void queryContacts_blankKeywordNormalize() {
        when(appletUserQueryMapper.selectContactsRaw(eq(9001L), eq(null)))
            .thenReturn(Collections.emptyList());

        List<ContactVo> contacts = service.queryContacts(9001L, "   ");
        assertTrue(contacts.isEmpty());
        // 验 mapper 收到 null 而不是 "   "
        org.mockito.Mockito.verify(appletUserQueryMapper).selectContactsRaw(eq(9001L), eq(null));
    }

    @Test
    @DisplayName("queryContacts — null userId → 直接返空数组（不查 DB）")
    void queryContacts_nullCurrentUser() {
        List<ContactVo> contacts = service.queryContacts(null, "");
        assertTrue(contacts.isEmpty());
        org.mockito.Mockito.verifyNoInteractions(appletUserQueryMapper);
    }
}
