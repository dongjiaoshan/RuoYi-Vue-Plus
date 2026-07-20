package org.dromara.djs.common.store.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.domain.dto.UserDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.UserService;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.domain.StoreUserRelation;
import org.dromara.djs.common.store.domain.vo.StoreUserVo;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.common.store.mapper.StoreUserRelationMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreUserRelationServiceImpl} 单元测试（STORE-PERM-001 / STR-USER-REL-001）。
 *
 * <p>覆盖 happy path：listUsersByStore（关联 sys_user）/ bindUsers（全新增 + 已绑跳过 + 软删复活）/
 * unbind（软删）/ listStoreIdsByUser / countUsersByStore。</p>
 *
 * @author djs
 * @since STORE-PERM-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreUserRelationServiceImpl 单元测试")
class StoreUserRelationServiceImplTest {

    @Mock
    private StoreUserRelationMapper baseMapper;

    @Mock
    private UserService userService;

    @Mock
    private StoreMapper storeMapper;

    private StoreUserRelationServiceImpl service;

    /**
     * MP 单测 entity cache 预热：service 内 LambdaQueryWrapper 在 mock 路径下也会触发
     * TableInfoHelper.getTableInfo() 解析 lambda 列名，必须先注册 entity。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, StoreUserRelation.class);
        TableInfoHelper.initTableInfo(assistant, Store.class);
    }

    @BeforeEach
    void setup() {
        service = new StoreUserRelationServiceImpl(baseMapper, userService, storeMapper);
    }

    @Test
    @DisplayName("listUsersByStore: 关联 sys_user 取账号/昵称/电话；人员已删则跳过展示")
    void testListUsersByStore() {
        StoreUserRelation rel1 = new StoreUserRelation();
        rel1.setId(1L);
        rel1.setStoreId(100L);
        rel1.setUserId(201L);
        StoreUserRelation rel2 = new StoreUserRelation();
        rel2.setId(2L);
        rel2.setStoreId(100L);
        rel2.setUserId(202L); // 这个人已删
        when(baseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rel1, rel2));

        UserDTO u1 = new UserDTO();
        u1.setUserId(201L);
        u1.setUserName("store_zhang");
        u1.setNickName("张店员");
        u1.setPhonenumber("13800138201");
        when(userService.selectListByIds(any())).thenReturn(List.of(u1)); // 202 不在返回 → 跳过

        List<StoreUserVo> result = service.listUsersByStore(100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(201L);
        assertThat(result.get(0).getNickName()).isEqualTo("张店员");
    }

    @Test
    @DisplayName("listUsersByStore: storeId 为空 → 返空列表，不打 DB")
    void testListUsersByStore_NullStore() {
        assertThat(service.listUsersByStore(null)).isEmpty();
        verify(baseMapper, times(0)).selectList(any());
    }

    @Test
    @DisplayName("bindUsers: 全新增 → 跳过已绑、无可复活则 insert")
    void testBindUsers_AllNew() {
        // 当前无任何有效绑定
        when(baseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        // 无可复活的软删行
        when(baseMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);
        when(baseMapper.insert(any(StoreUserRelation.class))).thenReturn(1);

        int affected = service.bindUsers(100L, List.of(201L, 202L));

        assertThat(affected).isEqualTo(2);
        verify(baseMapper, times(2)).insert(any(StoreUserRelation.class));
    }

    @Test
    @DisplayName("bindUsers: 已有有效绑定的 userId 跳过")
    void testBindUsers_SkipExisting() {
        StoreUserRelation existing = new StoreUserRelation();
        existing.setId(1L);
        existing.setStoreId(100L);
        existing.setUserId(201L);
        when(baseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(existing));
        when(baseMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);
        when(baseMapper.insert(any(StoreUserRelation.class))).thenReturn(1);

        // 201 已绑跳过；202 新增
        int affected = service.bindUsers(100L, List.of(201L, 202L));

        assertThat(affected).isEqualTo(1);
        verify(baseMapper, times(1)).insert(any(StoreUserRelation.class));
    }

    @Test
    @DisplayName("bindUsers: 软删行复活（update 命中）→ 不再 insert")
    void testBindUsers_Revive() {
        when(baseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        // 复活 update 命中 1 行
        when(baseMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        int affected = service.bindUsers(100L, List.of(201L));

        assertThat(affected).isEqualTo(1);
        verify(baseMapper, times(0)).insert(any(StoreUserRelation.class));
    }

    @Test
    @DisplayName("bindUsers: storeId 为空 → 抛 ServiceException")
    void testBindUsers_NullStore() {
        assertThatThrownBy(() -> service.bindUsers(null, List.of(1L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("门店 ID 不能为空");
    }

    @Test
    @DisplayName("bindUsers: 空 userIds → 返 0")
    void testBindUsers_EmptyUsers() {
        assertThat(service.bindUsers(100L, Collections.emptyList())).isZero();
    }

    @Test
    @DisplayName("unbind: 命中有效绑定 → 走基类软删（set del_flag/del_unique/update_*）")
    void testUnbind_HappyPath() {
        StoreUserRelation rel = new StoreUserRelation();
        rel.setId(5L);
        rel.setStoreId(100L);
        rel.setUserId(201L);
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(rel);
        when(baseMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.unbind(100L, 201L);

        assertThat(rows).isEqualTo(1);
        verify(baseMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("unbind: 无有效绑定 → 幂等返 0")
    void testUnbind_NotFound() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThat(service.unbind(100L, 201L)).isZero();
    }

    @Test
    @DisplayName("unbind: 参数缺失 → 抛 ServiceException")
    void testUnbind_NullArgs() {
        assertThatThrownBy(() -> service.unbind(null, 201L))
            .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("listStoreIdsByUser: 去重返门店 ID")
    void testListStoreIdsByUser() {
        StoreUserRelation r1 = new StoreUserRelation();
        r1.setStoreId(100L);
        StoreUserRelation r2 = new StoreUserRelation();
        r2.setStoreId(200L);
        when(baseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(r1, r2));

        List<Long> ids = service.listStoreIdsByUser(201L);

        assertThat(ids).containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    @DisplayName("listStoreIdsByUser: userId 为空 → 返空")
    void testListStoreIdsByUser_Null() {
        assertThat(service.listStoreIdsByUser(null)).isEmpty();
    }

    @Test
    @DisplayName("countUsersByStore: 走 selectCount")
    void testCountUsersByStore() {
        when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(3L);
        assertThat(service.countUsersByStore(100L)).isEqualTo(3);
    }
}
