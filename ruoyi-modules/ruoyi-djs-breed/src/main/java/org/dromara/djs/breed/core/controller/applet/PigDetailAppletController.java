package org.dromara.djs.breed.core.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.breed.core.domain.vo.FarrowByParityVo;
import org.dromara.djs.breed.core.domain.vo.PedigreeVo;
import org.dromara.djs.breed.core.domain.vo.SowPerformanceMpVo;
import org.dromara.djs.breed.core.service.ISowDetailService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序猪只详情聚合 Controller（BRD-FIX-MP-DETAIL-SPLIT-001）。
 *
 * <p>URL 前缀 {@code /applet/pig/*}（与 {@link PigAppletController} 同前缀，detail 聚合端点单独成类
 * 避免改 PigAppletController 既有依赖）。母猪详情 6 KPI + 产仔堆叠柱 + 育肥详情谱系卡，
 * read-only 实时聚合（Kevin 2026-05-30 D2 拍板本期 BE 从零建聚合，无 DDL、不触状态机）。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /applet/pig/sow-performance?pigId=} — 母猪性能 6 KPI（缺指标返 null）；</li>
 *   <li>{@code GET /applet/pig/farrow-by-parity?pigId=} — 产仔性能按胎次堆叠柱 4 系列；</li>
 *   <li>{@code GET /applet/pig/pedigree?pigId=} — 母系 / 父系谱系卡。</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckPermission("djs:applet:pig:search")} —— 复用 PigAppletController 同权限串
 * （mp role 默认含），详情聚合与 picker 搜索同属"读猪只"权限语义，不另开 perm seed。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-DETAIL-SPLIT-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/applet/pig")
public class PigDetailAppletController {

    private final ISowDetailService sowDetailService;

    /**
     * 母猪性能 6 KPI（原型 09）。
     *
     * @param pigId 母猪 ID（mp 端选中后回填，snowflake string，后端反序列化为 Long）
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/sow-performance")
    public R<SowPerformanceMpVo> sowPerformance(@RequestParam Long pigId) {
        return R.ok(sowDetailService.querySowPerformance(pigId));
    }

    /**
     * 产仔性能按胎次堆叠柱（原型 09 SOWDET-02）。
     *
     * @param pigId 母猪 ID
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/farrow-by-parity")
    public R<List<FarrowByParityVo>> farrowByParity(@RequestParam Long pigId) {
        return R.ok(sowDetailService.queryFarrowByParity(pigId));
    }

    /**
     * 母系 / 父系谱系卡（原型 101 FATDET-02）。
     *
     * @param pigId 本猪 ID
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/pedigree")
    public R<PedigreeVo> pedigree(@RequestParam Long pigId) {
        return R.ok(sowDetailService.queryPedigree(pigId));
    }
}
