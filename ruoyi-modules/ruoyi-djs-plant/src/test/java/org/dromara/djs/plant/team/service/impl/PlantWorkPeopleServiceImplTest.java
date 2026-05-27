package org.dromara.djs.plant.team.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.plant.team.constant.PlantTeamConstants;
import org.dromara.djs.plant.team.domain.PlantWorkPeople;
import org.dromara.djs.plant.team.domain.PlantWorkTeam;
import org.dromara.djs.plant.team.domain.bo.PlantWorkPeopleBatchBo;
import org.dromara.djs.plant.team.mapper.PlantWorkPeopleMapper;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlantWorkPeopleServiceImpl} 单测（PLT-MD-002）。
 *
 * <p>覆盖核心业务约束："一人只能属于一个班组"——
 * batchAddMembers 4 段校验链：team 存在 / dept_id=种植部 / 未在其他班组 / dedupe。</p>
 *
 * @author djs
 * @since PLT-MD-002
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlantWorkPeopleServiceImpl 单元测试")
class PlantWorkPeopleServiceImplTest {

    @Mock
    private PlantWorkPeopleMapper peopleMapper;

    @Mock
    private PlantWorkTeamMapper teamMapper;

    private PlantWorkPeopleServiceImpl service;

    @BeforeEach
    void setup() {
        service = new PlantWorkPeopleServiceImpl(peopleMapper, teamMapper);
    }

    private PlantWorkTeam sampleTeam(Long id, String name) {
        PlantWorkTeam t = new PlantWorkTeam();
        t.setId(id);
        t.setTeamName(name);
        t.setTeamStatus(PlantTeamConstants.TEAM_STATUS_ACTIVE);
        return t;
    }

    // ============ batchAddMembers ============

    @Test
    @DisplayName("batchAddMembers: happy path → INSERT 2 行 is_leader=2 join_date=now")
    void testBatchAddMembers_HappyPath() {
        Long teamId = 1L;
        List<Long> userIds = List.of(1001L, 1002L);

        when(teamMapper.selectById(teamId)).thenReturn(sampleTeam(teamId, "果蔬班"));
        when(peopleMapper.countValidPlantUsers(userIds, PlantTeamConstants.PLANT_DEPT_ID)).thenReturn(2L);
        when(peopleMapper.findOccupiedUsers(userIds)).thenReturn(Collections.emptyList());
        when(peopleMapper.insert(any(PlantWorkPeople.class))).thenReturn(1);

        PlantWorkPeopleBatchBo bo = new PlantWorkPeopleBatchBo();
        bo.setTeamId(teamId);
        bo.setUserIds(userIds);

        int rows = service.batchAddMembers(bo);

        assertThat(rows).isEqualTo(2);
        ArgumentCaptor<PlantWorkPeople> captor = ArgumentCaptor.forClass(PlantWorkPeople.class);
        verify(peopleMapper, times(2)).insert(captor.capture());
        for (PlantWorkPeople p : captor.getAllValues()) {
            assertThat(p.getTeamId()).isEqualTo(teamId);
            assertThat(p.getIsLeader()).as("新加入成员 is_leader 默认 2=否").isEqualTo(PlantTeamConstants.IS_LEADER_NO);
            assertThat(p.getJoinDate()).isNotNull();
            assertThat(p.getDelFlag()).isEqualTo("0");
            assertThat(p.getDelUnique()).isEqualTo(0L);
        }
        assertThat(captor.getAllValues()).extracting(PlantWorkPeople::getUserId)
            .containsExactlyInAnyOrder(1001L, 1002L);
    }

    @Test
    @DisplayName("batchAddMembers: 用户已在其他班组 → 抛 ServiceException(已属于班组)")
    void testBatchAddMembers_UserAlreadyInOtherTeam() {
        Long teamId = 2L;
        List<Long> userIds = List.of(1001L);

        when(teamMapper.selectById(teamId)).thenReturn(sampleTeam(teamId, "薯类班"));
        when(peopleMapper.countValidPlantUsers(userIds, PlantTeamConstants.PLANT_DEPT_ID)).thenReturn(1L);
        // 模拟 1001 已在果蔬班
        when(peopleMapper.findOccupiedUsers(userIds)).thenReturn(List.of(
            Map.of("userId", 1001L, "nickName", "张三", "teamId", 1L, "teamName", "果蔬班")
        ));

        PlantWorkPeopleBatchBo bo = new PlantWorkPeopleBatchBo();
        bo.setTeamId(teamId);
        bo.setUserIds(userIds);

        assertThatThrownBy(() -> service.batchAddMembers(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已属于班组")
            .hasMessageContaining("张三")
            .hasMessageContaining("果蔬班");

        verify(peopleMapper, never()).insert(any(PlantWorkPeople.class));
    }

    @Test
    @DisplayName("batchAddMembers: team 不存在 → 抛 ServiceException")
    void testBatchAddMembers_TeamNotFound() {
        Long teamId = 99L;
        when(teamMapper.selectById(teamId)).thenReturn(null);

        PlantWorkPeopleBatchBo bo = new PlantWorkPeopleBatchBo();
        bo.setTeamId(teamId);
        bo.setUserIds(List.of(1001L));

        assertThatThrownBy(() -> service.batchAddMembers(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("班组不存在");

        verify(peopleMapper, never()).countValidPlantUsers(anyCollection(), anyLong());
    }

    @Test
    @DisplayName("batchAddMembers: 包含非种植部 user → 抛 ServiceException")
    void testBatchAddMembers_NotPlantDept() {
        Long teamId = 1L;
        List<Long> userIds = List.of(1001L, 2002L);

        when(teamMapper.selectById(teamId)).thenReturn(sampleTeam(teamId, "果蔬班"));
        // 只有 1 个属于种植部，缺 1
        when(peopleMapper.countValidPlantUsers(userIds, PlantTeamConstants.PLANT_DEPT_ID)).thenReturn(1L);

        PlantWorkPeopleBatchBo bo = new PlantWorkPeopleBatchBo();
        bo.setTeamId(teamId);
        bo.setUserIds(userIds);

        assertThatThrownBy(() -> service.batchAddMembers(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("非种植部员工或已停用账户");

        verify(peopleMapper, never()).insert(any(PlantWorkPeople.class));
    }

    @Test
    @DisplayName("batchAddMembers: 空 userIds → 0 行短路，无 DB 访问")
    void testBatchAddMembers_EmptyShortCircuit() {
        PlantWorkPeopleBatchBo bo = new PlantWorkPeopleBatchBo();
        bo.setTeamId(1L);
        bo.setUserIds(List.of());

        int rows = service.batchAddMembers(bo);

        assertThat(rows).isZero();
        verify(teamMapper, never()).selectById(anyLong());
        verify(peopleMapper, never()).insert(any(PlantWorkPeople.class));
    }

    @Test
    @DisplayName("batchAddMembers: dedupe — 重复 userId 只 INSERT 1 行")
    void testBatchAddMembers_Dedupe() {
        Long teamId = 1L;
        // 故意重复
        List<Long> userIds = List.of(1001L, 1001L);

        when(teamMapper.selectById(teamId)).thenReturn(sampleTeam(teamId, "果蔬班"));
        when(peopleMapper.countValidPlantUsers(anyCollection(), eq(PlantTeamConstants.PLANT_DEPT_ID))).thenReturn(1L);
        when(peopleMapper.findOccupiedUsers(anyCollection())).thenReturn(Collections.emptyList());
        when(peopleMapper.insert(any(PlantWorkPeople.class))).thenReturn(1);

        PlantWorkPeopleBatchBo bo = new PlantWorkPeopleBatchBo();
        bo.setTeamId(teamId);
        bo.setUserIds(userIds);

        int rows = service.batchAddMembers(bo);

        assertThat(rows).as("dedupe 后只插 1 行").isEqualTo(1);
        verify(peopleMapper, times(1)).insert(any(PlantWorkPeople.class));
    }

    // ============ removeMember ============

    @Test
    @DisplayName("removeMember: happy path → 软删 + leader_id 非该 user 不动")
    void testRemoveMember_NonLeader() {
        Long teamId = 1L;
        Long userId = 1001L;

        PlantWorkPeople row = new PlantWorkPeople();
        row.setId(50001L);
        row.setTeamId(teamId);
        row.setUserId(userId);
        row.setIsLeader(PlantTeamConstants.IS_LEADER_NO);
        when(peopleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);
        when(peopleMapper.update(any(), any())).thenReturn(1);

        PlantWorkTeam team = sampleTeam(teamId, "果蔬班");
        team.setLeaderId(9999L);   // 非该 user
        when(teamMapper.selectById(teamId)).thenReturn(team);

        int rows = service.removeMember(teamId, userId);

        assertThat(rows).isEqualTo(1);
        // leader_id 不被 update
        verify(teamMapper, never()).updateById(any(PlantWorkTeam.class));
    }

    @Test
    @DisplayName("removeMember: 调出的是 leader → 同步清空 team.leader_id")
    void testRemoveMember_IsLeader_ClearsLeader() {
        Long teamId = 1L;
        Long userId = 1001L;

        PlantWorkPeople row = new PlantWorkPeople();
        row.setId(50001L);
        row.setTeamId(teamId);
        row.setUserId(userId);
        row.setIsLeader(PlantTeamConstants.IS_LEADER_YES);
        when(peopleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);
        when(peopleMapper.update(any(), any())).thenReturn(1);

        PlantWorkTeam team = sampleTeam(teamId, "果蔬班");
        team.setLeaderId(userId);  // 是该 user
        when(teamMapper.selectById(teamId)).thenReturn(team);

        int rows = service.removeMember(teamId, userId);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<PlantWorkTeam> captor = ArgumentCaptor.forClass(PlantWorkTeam.class);
        verify(teamMapper, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getLeaderId()).as("调出 leader 后 team.leader_id 清空").isNull();
    }

    @Test
    @DisplayName("removeMember: user 不在该班组 → 抛 ServiceException")
    void testRemoveMember_NotFound() {
        when(peopleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.removeMember(1L, 1001L))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("用户不在该班组中");

        verify(peopleMapper, never()).update(any(), any());
    }

    // ============ listCandidateUsers ============

    @Test
    @DisplayName("listCandidateUsers: 走 dept_id=201 + tenant 透传")
    void testListCandidateUsers_PlantDeptHardcoded() {
        when(peopleMapper.listCandidateUsers(eq(PlantTeamConstants.PLANT_DEPT_ID), anyString()))
            .thenReturn(Collections.emptyList());

        service.listCandidateUsers();

        verify(peopleMapper, times(1))
            .listCandidateUsers(eq(PlantTeamConstants.PLANT_DEPT_ID), anyString());
    }
}
