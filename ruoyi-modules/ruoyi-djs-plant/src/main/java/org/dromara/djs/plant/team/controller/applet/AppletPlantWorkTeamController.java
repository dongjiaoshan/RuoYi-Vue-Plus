package org.dromara.djs.plant.team.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.team.domain.query.PlantWorkTeamQuery;
import org.dromara.djs.plant.team.domain.vo.PlantWorkTeamVo;
import org.dromara.djs.plant.team.service.IPlantWorkTeamService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 班组 mp 端 Controller（PLT-WORK-001 配套）。
 *
 * <p>路径前缀 {@code /djs/applet/plant/team/}；仅提供 listAll picker 端点供 mp 农事录入选班组。
 * admin 端 {@link org.dromara.djs.plant.team.controller.PlantWorkTeamController} 走 {@code djs:plant:team:*}，
 * mp 工人无此权限，本 controller 独立 perms {@code djs:applet:plant:team:list}（菜单 8097）。</p>
 *
 * @author djs
 * @since PLT-WORK-001
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/applet/plant/team")
public class AppletPlantWorkTeamController extends BaseController {

    private final IPlantWorkTeamService teamService;

    @SaCheckLogin
    @SaCheckPermission("djs:applet:plant:team:list")
    @GetMapping("/listAll")
    public R<List<PlantWorkTeamVo>> listAll(PlantWorkTeamQuery query) {
        return R.ok(teamService.queryList(query));
    }
}
