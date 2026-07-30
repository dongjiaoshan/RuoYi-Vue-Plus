package org.dromara.djs.plant.activity.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.common.mapper.DjsUserExtMapper;
import org.dromara.djs.plant.activity.domain.PlantActivity;
import org.dromara.djs.plant.activity.domain.vo.PlantActivityVo;
import org.dromara.djs.plant.activity.service.IPlantActivityService;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.plant.team.domain.PlantWorkTeam;
import org.dromara.djs.plant.team.mapper.PlantWorkTeamMapper;
import org.dromara.djs.plant.team.service.PlantTeamLinkService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * mp 端采摘活动 Controller（FIX-PLT-HARVEST-ACTIVITY-001）。
 *
 * <p>路径前缀 {@code /djs/applet/plant/activity}：</p>
 * <ul>
 *   <li>{@code GET /records?cropId=&begin=&end=} — 采摘活动记录列表（倒序，
 *       {@code cropName} / {@code teamName} 批量 enrich）</li>
 * </ul>
 *
 * @author djs
 * @since FIX-PLT-HARVEST-ACTIVITY-001
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/applet/plant/activity")
public class AppletPlantActivityController extends BaseController {

    /** 采摘去向字典 key（djs_pick_dest：sale/veg_fresh/platform/loss/feed）。 */
    private static final String DICT_PICK_DEST = "djs_pick_dest";

    private final IPlantActivityService plantActivityService;
    private final CropInfoMapper cropInfoMapper;
    private final PlantWorkTeamMapper teamMapper;
    private final DictService dictService;
    private final DjsUserExtMapper userExtMapper;
    /** row129 采摘活动班组多选中间表读服务（多班组顿号拼接展示，替代旧单列 activityBy）。 */
    private final PlantTeamLinkService teamLinkService;

    /**
     * 采摘活动记录列表（mp 采摘活动记录，按采摘日期倒序）。
     *
     * @param cropId 作物 id（可空 = 全场查询，不按作物过滤）
     * @param begin  起始采摘日期（含，可空）
     * @param end    结束采摘日期（含，可空）
     */
    @GetMapping("/records")
    public R<List<PlantActivityVo>> records(@RequestParam(required = false) Long cropId,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        List<PlantActivity> records = plantActivityService.listRecords(cropId, begin, end);
        if (records.isEmpty()) {
            return R.ok(List.of());
        }

        // 批量 enrich：作物名按 records 里出现的 cropId 去重批量查（全场场景多作物，防串名）；
        // 班组名按 activityBy 去重批量查
        List<Long> cropIds = records.stream()
            .map(PlantActivity::getCropId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, String> cropNameMap = cropIds.isEmpty()
            ? Map.of()
            : cropInfoMapper.selectByIds(cropIds).stream()
                .filter(c -> c.getCropName() != null)
                .collect(Collectors.toMap(CropInfo::getId, CropInfo::getCropName, (a, b) -> a));

        List<Long> teamIds = records.stream()
            .map(PlantActivity::getActivityBy)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, String> teamNameMap = teamIds.isEmpty()
            ? Map.of()
            : teamMapper.selectByIds(teamIds).stream()
                .filter(t -> t.getTeamName() != null)
                .collect(Collectors.toMap(PlantWorkTeam::getId, PlantWorkTeam::getTeamName, (a, b) -> a));

        // row164：采摘处理人姓名批量 enrich（recorder_id → sys_user.nick_name），去重防 N+1
        List<Long> recorderIds = records.stream()
            .map(PlantActivity::getRecorderId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, String> recorderNameMap = recorderIds.isEmpty()
            ? Map.of()
            : userExtMapper.selectNickNamesByIds(recorderIds).stream()
                .filter(m -> m.get("userId") != null && m.get("nickName") != null)
                .collect(Collectors.toMap(
                    m -> ((Number) m.get("userId")).longValue(),
                    m -> String.valueOf(m.get("nickName")),
                    (a, b) -> a));

        // row129：采摘活动班组多选 → 从 junction t_plant_activity_team 批量取各活动的班组名（多组顿号拼接），
        // 替代旧单列 activityBy 单班组展示；junction 无记录时回落旧单列 teamNameMap（历史/单选路径）。
        List<Long> activityIds = records.stream()
            .map(PlantActivity::getId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, List<String>> activityTeamNameMap = teamLinkService.activityTeamNames(activityIds);

        List<PlantActivityVo> list = records.stream().map(r -> {
            PlantActivityVo vo = new PlantActivityVo();
            vo.setId(r.getId());
            vo.setCropId(r.getCropId());
            vo.setCropName(r.getCropId() == null ? null : cropNameMap.get(r.getCropId()));
            vo.setActivityDate(r.getActivityDate());
            vo.setDailyWeight(r.getDailyWeight());
            vo.setActivityBy(r.getActivityBy());
            List<String> multiTeamNames = activityTeamNameMap.get(r.getId());
            vo.setTeamName((multiTeamNames != null && !multiTeamNames.isEmpty())
                ? String.join("、", multiTeamNames)
                : (r.getActivityBy() == null ? null : teamNameMap.get(r.getActivityBy())));
            // row164：采摘去向字典 label（缺失回落原始值）+ 采摘处理人姓名
            vo.setPickDest(r.getPickDest());
            vo.setPickDestLabel(StringUtils.isBlank(r.getPickDest())
                ? null
                : dictService.getDictLabel(DICT_PICK_DEST, r.getPickDest()));
            vo.setRecorderId(r.getRecorderId());
            vo.setRecorderName(r.getRecorderId() == null ? null : recorderNameMap.get(r.getRecorderId()));
            return vo;
        }).collect(Collectors.toList());

        return R.ok(list);
    }
}
