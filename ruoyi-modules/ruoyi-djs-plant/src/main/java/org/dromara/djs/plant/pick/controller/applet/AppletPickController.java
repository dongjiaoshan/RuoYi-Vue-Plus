package org.dromara.djs.plant.pick.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.pick.domain.vo.PickTaskVo;
import org.dromara.djs.plant.pick.service.IAppletPickService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * mp 端采摘任务 Controller（PLT-PLAN-002）。
 *
 * <p>仅浏览，不录入。录入闭环在 D12 PLT-PICK-001。</p>
 *
 * <h2>2 端点</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/plant/pick/myTasks?status=pending,picking}：待采摘 / 采摘中任务列表</li>
 *   <li>{@code GET /djs/applet/plant/pick/detail/{id}}：任务详情</li>
 * </ul>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Validated
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/applet/plant/pick")
public class AppletPickController extends BaseController {

    private final IAppletPickService appletPickService;

    @SaCheckPermission("djs:applet:plant:pick:list")
    @GetMapping("/myTasks")
    public R<List<PickTaskVo>> myTasks(@RequestParam(required = false) String status) {
        return R.ok(appletPickService.listMyTasks(status));
    }

    @SaCheckPermission("djs:applet:plant:pick:detail")
    @GetMapping("/detail/{id}")
    public R<PickTaskVo> detail(@PathVariable Long id) {
        return R.ok(appletPickService.getTaskDetail(id));
    }
}
