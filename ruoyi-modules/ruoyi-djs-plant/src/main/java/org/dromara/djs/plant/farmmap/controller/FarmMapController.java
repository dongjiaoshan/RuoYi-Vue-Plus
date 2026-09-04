package org.dromara.djs.plant.farmmap.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.farmmap.domain.bo.FarmMapBindBo;
import org.dromara.djs.plant.farmmap.domain.vo.FarmMapOverviewVo;
import org.dromara.djs.plant.farmmap.service.IFarmMapService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 农场地图 Controller（PLT-FARMMAP-001）。
 *
 * <p>菜单 + 按钮权限 seed 见 {@code V202609030900__PLT-FARMMAP-001-farm-map-region.sql}
 * （menu_id 8400-8402）。URL 前缀 {@code /djs/plant/farmmap}。</p>
 *
 * <p>图上格子的几何不走接口——它在前端 {@code regions.generated.ts} 里，随前端一起发版。
 * 本 controller 只管「哪个格子挂了哪块地」。</p>
 *
 * @author djs
 * @since PLT-FARMMAP-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/plant/farmmap")
public class FarmMapController extends BaseController {

    private final IFarmMapService farmMapService;

    /** 拉全图：已挂格子 + 图外地块 + 覆盖率。 */
    @SaCheckPermission("djs:plant:farmmap:list")
    @GetMapping("/overview")
    public R<FarmMapOverviewVo> overview() {
        return R.ok(farmMapService.overview());
    }

    /** 把一个格子挂到一块地上（已挂则改挂）。 */
    @SaCheckPermission("djs:plant:farmmap:bind")
    @Log(title = "农场地图绑定", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/bind")
    public R<Void> bind(@Valid @RequestBody FarmMapBindBo bo) {
        farmMapService.bind(bo);
        return R.ok();
    }

    /** 解绑一个格子。 */
    @SaCheckPermission("djs:plant:farmmap:bind")
    @Log(title = "农场地图绑定", businessType = BusinessType.DELETE)
    @DeleteMapping("/bind/{regionKey}")
    public R<Void> unbind(@PathVariable String regionKey) {
        farmMapService.unbind(regionKey);
        return R.ok();
    }

}
