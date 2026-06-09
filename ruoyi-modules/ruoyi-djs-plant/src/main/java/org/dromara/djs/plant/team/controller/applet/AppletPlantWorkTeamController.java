package org.dromara.djs.plant.team.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.team.domain.query.PlantWorkTeamQuery;
import org.dromara.djs.plant.team.domain.vo.PlantWorkPeopleVo;
import org.dromara.djs.plant.team.domain.vo.PlantWorkTeamVo;
import org.dromara.djs.plant.team.service.IPlantWorkPeopleService;
import org.dromara.djs.plant.team.service.IPlantWorkTeamService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 班组 mp 端 Controller（PLT-WORK-001 配套 + FIX-PLT-MP-PICK-001）。
 *
 * <p>路径前缀 {@code /djs/applet/plant/team/}：</p>
 * <ul>
 *   <li>{@code GET /listAll} — 班组 picker（mp 农事录入选班组）</li>
 *   <li>{@code GET /{teamId}/members} — 班组成员下拉（采摘人员选择，FIX-PLT-MP-PICK-001，service 零改）</li>
 * </ul>
 *
 * <p>admin 端 {@link org.dromara.djs.plant.team.controller.PlantWorkTeamController} 走 {@code djs:plant:team:*}，
 * mp 工人无此权限；本 controller members 端点仅 {@code @SaCheckLogin}（采摘人员下拉，工人无 admin perm）。</p>
 *
 * @author djs
 * @since PLT-WORK-001
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/applet/plant/team")
public class AppletPlantWorkTeamController extends BaseController {

    private final IPlantWorkTeamService teamService;
    private final IPlantWorkPeopleService peopleService;

    @SaCheckLogin
    @SaCheckPermission("djs:applet:plant:team:list")
    @GetMapping("/listAll")
    public R<List<PlantWorkTeamVo>> listAll(PlantWorkTeamQuery query) {
        return R.ok(teamService.queryList(query));
    }

    /**
     * 班组成员列表（FIX-PLT-MP-PICK-001 采摘人员下拉）：直接复用 {@code peopleService.listByTeamId}。
     *
     * @param teamId 班组 id
     */
    @SaCheckLogin
    @GetMapping("/{teamId}/members")
    public R<List<PlantWorkPeopleVo>> members(@PathVariable Long teamId) {
        return R.ok(peopleService.listByTeamId(teamId));
    }
}
