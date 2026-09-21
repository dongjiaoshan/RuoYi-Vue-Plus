package org.dromara.djs.breed.event.eartag.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.breed.event.eartag.domain.vo.EartagPendingVo;
import org.dromara.djs.breed.event.eartag.domain.vo.FarrowEarTagStatVo;
import org.dromara.djs.breed.event.eartag.service.IPigEarTagService;
import org.dromara.djs.breed.event.farrow.mapper.PigFarrowMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序耳标徽标 Controller（DJS-FIX-MP-W22-003）。
 *
 * <p>专给 mp 端 breed home"仔猪耳标"卡片右上角红色徽标用。工人第一眼判断
 * "我今天该不该进这页"——0 时 wd-badge 自动隐藏，&gt;0 时显示数字。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET  /djs/applet/breed/eartag/farrow-pending}
 *       返 {@link EartagPendingVo}（未断奶窝数 + 仔猪总头数）。</li>
 *   <li>{@code POST /djs/applet/breed/eartag/litter/&#123;farrowId&#125;}
 *       进出生重订正页时读本窝仔猪清单，老窝没档案则补建（D-0110，幂等）。</li>
 * </ul>
 *
 * <h2>口径</h2>
 * <p>V6 行243 起 = <b>未断奶</b>（该窝没有 {@code t_farm_pig_weaning} 记录），与选窝列表
 * {@code IFarrowService.queryPendingLitters} 同源；母猪一断奶两边同时消失。
 * 行242 起整窝在分娩提交时即自动建档，旧的「未打标」口径恒为 0 已废弃。
 * {@code t_farm_pig_farrow} 表本身无 status 冗余字段，全表动态聚合，
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
    private final IPigEarTagService eartagService;

    /**
     * 未断奶窝统计：返"还有 N 窝 / M 头"。
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

    /**
     * 本窝仔猪清单（耳号 / 性别 / 当前出生重），一头都没有就先补建整窝（V6 行243 + D-0110）。
     *
     * <p>mp「仔猪耳号标记」页点窝卡进来时调；幂等，已有档案时纯读。用 POST 而非 GET —— 它可能写库。</p>
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:breed:eartag")
    @PostMapping("/litter/{farrowId}")
    public R<FarrowEarTagStatVo> litter(@PathVariable Long farrowId) {
        return R.ok(eartagService.ensureLitterCreated(farrowId));
    }
}
