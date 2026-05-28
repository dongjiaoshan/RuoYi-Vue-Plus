package org.dromara.djs.breed.event.farrow.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
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
}
