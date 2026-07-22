package org.dromara.djs.plant.team.service;

import org.dromara.djs.plant.activity.mapper.PlantActivityTeamMapper;
import org.dromara.djs.plant.farm.mapper.FarmRecordsTeamMapper;
import org.dromara.djs.plant.plan.domain.PlantDetailsTeam;
import org.dromara.djs.plant.plan.mapper.PlantDetailsTeamMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 班组多选中间表统一读写服务单测（G1-TEAMS-MULTISELECT，row36/37/40）。
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlantTeamLinkServiceTest {

    @Mock
    private PlantDetailsTeamMapper detailsTeamMapper;
    @Mock
    private FarmRecordsTeamMapper farmTeamMapper;
    @Mock
    private PlantActivityTeamMapper activityTeamMapper;

    private PlantTeamLinkService service;

    private void init() {
        service = new PlantTeamLinkService(detailsTeamMapper, farmTeamMapper, activityTeamMapper);
    }

    @Test
    @DisplayName("syncDetailTeams: 先物理删旧再逐条插新（去重去空）")
    void syncDetailTeams_deleteThenInsert() {
        init();
        // [10, 10, null, 20] → 去重去空 = [10, 20]
        service.syncDetailTeams(1L, PlantTeamLinkService.ROLE_PLANT,
            java.util.Arrays.asList(10L, 10L, null, 20L));

        verify(detailsTeamMapper, times(1)).physicalDeleteByDetailRole(1L, "plant");
        ArgumentCaptor<PlantDetailsTeam> cap = ArgumentCaptor.forClass(PlantDetailsTeam.class);
        verify(detailsTeamMapper, times(2)).insert(cap.capture());
        assertThat(cap.getAllValues()).extracting(PlantDetailsTeam::getTeamId).containsExactly(10L, 20L);
        assertThat(cap.getAllValues()).allMatch(t -> "plant".equals(t.getRole()) && t.getDetailId().equals(1L));
    }

    @Test
    @DisplayName("syncDetailTeams: teamIds 为 null 时不动中间表（保留旧关联）")
    void syncDetailTeams_nullNoop() {
        init();
        service.syncDetailTeams(1L, PlantTeamLinkService.ROLE_HARVEST, null);
        verify(detailsTeamMapper, times(0)).physicalDeleteByDetailRole(any(), any());
    }

    @Test
    @DisplayName("detailTeamNames: 按 detailId+role 聚合班组名")
    void detailTeamNames_grouped() {
        init();
        when(detailsTeamMapper.selectTeamNamesByDetailIds(anyCollection())).thenReturn(List.of(
            Map.of("detailId", 1L, "role", "plant", "teamId", 10L, "teamName", "一组"),
            Map.of("detailId", 1L, "role", "plant", "teamId", 20L, "teamName", "二组"),
            Map.of("detailId", 1L, "role", "harvest", "teamId", 30L, "teamName", "三组")));

        Map<Long, Map<String, List<String>>> res = service.detailTeamNames(List.of(1L));
        assertThat(res.get(1L).get("plant")).containsExactly("一组", "二组");
        assertThat(res.get(1L).get("harvest")).containsExactly("三组");
    }

    @Test
    @DisplayName("farmTeamNames: 按 recordId 聚合班组名")
    void farmTeamNames_grouped() {
        init();
        when(farmTeamMapper.selectTeamNamesByRecordIds(anyCollection())).thenReturn(List.of(
            Map.of("recordId", 5L, "teamId", 10L, "teamName", "一组"),
            Map.of("recordId", 5L, "teamId", 20L, "teamName", "二组")));

        Map<Long, List<String>> res = service.farmTeamNames(List.of(5L));
        assertThat(res.get(5L)).containsExactly("一组", "二组");
    }

    @Test
    @DisplayName("syncActivityTeams: activityId 为空直接跳过")
    void syncActivityTeams_nullId() {
        init();
        service.syncActivityTeams(null, List.of(10L));
        verify(activityTeamMapper, times(0)).physicalDeleteByActivityId(eq(1L));
    }
}
