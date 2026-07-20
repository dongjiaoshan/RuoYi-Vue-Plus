package org.dromara.djs.breed.event.eartag.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.breed.event.eartag.domain.vo.EartagPendingVo;
import org.dromara.djs.breed.event.farrow.mapper.PigFarrowMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序耳标徽标 Controller（DJS-FIX-MP-W22-003）。
 *
 * <p>专给 mp 端 breed home"仔猪耳标"卡片右上角红色徽标"待贴 N 头"用。工人第一眼判断
 * "我今天该不该进这页"——0 时 wd-badge 自动隐藏，&gt;0 时显示数字。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/breed/eartag/farrow-pending}
 *       返 {@link EartagPendingVo}（batch 数 + 仔猪总头数）。</li>
 * </ul>
 *
 * <h2>口径</h2>
 * <p>"待贴" = farrow.live_born &gt; 已贴 pigletno 行数，与 {@code statByFarrow} 同口径。
 * {@code t_farm_pig_farrow} 表本身无 status / tagged_count 冗余字段，全表动态聚合，
 * 单租户 V1 数据量低不会引起性能问题（farrow 增量低频，每天个位数）。</p>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckPermission("djs:applet:breed:eartag")} — 与现有 admin 耳标 perm
 * 同串复用语义；菜单 seed 见 {@code V<ts>__DJS-FIX-MP-W22-003-applet-eartag-menu.sql}。</p>
 *
 * @author djs
 * @since DJS-FIX-MP-W22-003
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/breed/eartag")
public class EartagAppletController {

    private final PigFarrowMapper farrowMapper;

    /**
     * 待贴耳标统计：返"还有 N 批 / M 头"。
     *
     * <p>前端 breed home {@code onShow} 调；wd-badge value=pendingPigletCount，0 时隐藏。</p>
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:breed:eartag")
    @GetMapping("/farrow-pending")
    public R<EartagPendingVo> getPending() {
        EartagPendingVo vo = new EartagPendingVo();
        Integer batches = farrowMapper.countPendingFarrows();
        Integer piglets = farrowMapper.sumPendingPiglets();
        vo.setPendingFarrowCount(batches == null ? 0 : batches);
        vo.setPendingPigletCount(piglets == null ? 0 : piglets);
        return R.ok(vo);
    }
}
