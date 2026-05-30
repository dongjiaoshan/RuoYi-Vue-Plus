package org.dromara.djs.breed.event.farrow.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.breed.event.farrow.domain.vo.FarrowBarnCountVo;
import org.dromara.djs.breed.event.farrow.domain.vo.FarrowLitterVo;
import org.dromara.djs.breed.event.farrow.domain.vo.FarrowPickerVo;
import org.dromara.djs.breed.event.farrow.service.IFarrowService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序分娩 picker Controller（D9 closing Group D — FarrowPicker）。
 *
 * <p>专给 mp 端 {@code FarrowPicker} 用——业务上：工人先选母猪 earNo → 调本端点反查
 * 该母猪最近 N 次仍有 remain 的分娩记录 → 选中后自动 set farrowId（比手输 19 位
 * snowflake 更顺工人思维，参 _open-issues #8 决策 a）。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/breed/farrow/recent-by-mother?earNo=&limit=}
 *       返回最近 N 次（默认 5）仍有 {@code remainEartag > 0} 的分娩记录。</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckPermission("djs:applet:breed:farrow:list")} — mp / staff / employee
 * role 默认含；菜单 seed 见 {@code V202606071540__D9-closing-applet-farrow-menu.sql}。</p>
 *
 * @author djs
 * @since D9-closing Group D
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/breed/farrow")
public class FarrowAppletController {

    private final IFarrowService farrowService;

    /**
     * 按母猪 earNo 反查最近 N 次未贴满标的分娩记录。
     *
     * <p>语义：</p>
     * <ul>
     *   <li>earNo 必填——FarrowPicker 强制先选母猪</li>
     *   <li>仅返 {@code remainEartag > 0}（已贴满 liveBorn 的分娩 picker 不显示）</li>
     *   <li>按 farrow_date 倒序，默认 5 条，最多 20 条</li>
     * </ul>
     *
     * @param earNo 母猪耳号（业务码）
     * @param limit 默认 5，最多 20
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:breed:farrow:list")
    @GetMapping("/recent-by-mother")
    public R<List<FarrowPickerVo>> recentByMother(
        @RequestParam String earNo,
        @RequestParam(required = false, defaultValue = "5") Integer limit
    ) {
        return R.ok(farrowService.queryRecentByMotherEarNo(earNo, limit));
    }

    /**
     * 待打标分娩窝列表（BRD-FIX-MP-EVENT-MISC-IA-001 — 仔猪耳号"选窝"网格，原型 96）。
     *
     * <p>仅返仍有未贴满标的窝；可选 {@code motherEarNo}（母猪耳号下拉）/ {@code barnName}（分娩栋舍 chip）过滤。
     * 卡片含公母数 / 分娩日期；选中后 mp 端用同条数据渲染「分娩概况」auto-fill 卡。</p>
     *
     * @param motherEarNo 母猪耳号过滤（可空）
     * @param barnName    分娩栋舍名过滤（chip 点击后传，可空）
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:breed:farrow:list")
    @GetMapping("/pending-litters")
    public R<List<FarrowLitterVo>> pendingLitters(
        @RequestParam(required = false) String motherEarNo,
        @RequestParam(required = false) String barnName
    ) {
        return R.ok(farrowService.queryPendingLitters(motherEarNo, barnName));
    }

    /**
     * 待打标分娩栋舍 chip（BRD-FIX-MP-EVENT-MISC-IA-001 — 原型 96 顶部"分娩1栋(12)"）。
     *
     * <p>按 farrow.barn_name 聚合待打标窝数；barn_name 为空的窝不进 chip；count 0 不返；barnName 升序。</p>
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:breed:farrow:list")
    @GetMapping("/pending-barn-count")
    public R<List<FarrowBarnCountVo>> pendingBarnCount() {
        return R.ok(farrowService.countPendingLittersByBarn());
    }
}
