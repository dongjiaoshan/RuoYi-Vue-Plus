package org.dromara.djs.breed.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.breed.production.domain.vo.SowPerformanceVo;
import org.dromara.djs.breed.production.service.ISowPerformanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 母猪生产指标 Controller（BRD-LIST-001 详情 tab2，只读）。
 *
 * <p>端点：</p>
 * <ul>
 *   <li>{@code GET /djs/breed/sow-performance/{pigId}} — 返该母猪所有胎次的生产指标（parity DESC）</li>
 * </ul>
 *
 * @author djs
 * @since BRD-LIST-001
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/breed/sow-performance")
public class SowPerformanceController {

    private final ISowPerformanceService sowPerformanceService;

    @SaCheckPermission("djs:breed:pig:query")
    @GetMapping("/{pigId}")
    public R<List<SowPerformanceVo>> listByPigId(@PathVariable Long pigId) {
        return R.ok(sowPerformanceService.listByPigId(pigId));
    }
}
