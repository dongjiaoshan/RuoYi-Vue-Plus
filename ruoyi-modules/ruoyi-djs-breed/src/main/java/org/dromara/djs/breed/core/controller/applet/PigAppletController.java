package org.dromara.djs.breed.core.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.breed.core.domain.vo.PigSearchVo;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序猪只 Controller（BRD-LIST-001 D7 carryover #1）。
 *
 * <p>URL 前缀 {@code /applet/pig/*}，与现有 applet 系列（{@code /applet/user/*} / {@code /applet/auth/*}）
 * 保持小程序统一前缀风格——admin 端继续走 {@code /djs/breed/pig/*}（{@link
 * org.dromara.djs.breed.core.controller.PigController}）。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /applet/pig/search} — 耳号关键字 + 状态/性别/类型过滤的轻量搜索，给 mp 端 PigPicker
 *       组件用（让 D6 12 个事件表单从"手输 earNo 兜底"升级到 picker 选猪）。</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckPermission("djs:applet:pig:search")} —— mp role 默认含；admin role 也含，便于
 * 联调时 admin 端调试 picker 行为。</p>
 *
 * @author djs
 * @since BRD-LIST-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/applet/pig")
public class PigAppletController {

    private final IPigCoreService pigCoreService;

    /**
     * 耳号关键字搜索（picker 用）。
     *
     * <p>语义：永不返回 END 猪只；其他过滤都是可选的，都不传时返最近 N 条非 END。</p>
     *
     * @param earNoKeyword  耳号 LIKE 中部匹配；空 → 不过滤
     * @param statusFilter  状态 CSV（如 {@code "HB,DN,LC,KH,FQ"} 给配种 picker 用）
     * @param sexFilter     {@code "M"} / {@code "F"}；castrate 表单调用时强制 {@code "M"}
     * @param pigTypeFilter {@code "sow"/"boar"/"piglet"/"fattening"}
     * @param limit         1-100，默认 20
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/search")
    public R<List<PigSearchVo>> search(
        @RequestParam(required = false) String earNoKeyword,
        @RequestParam(required = false) String statusFilter,
        @RequestParam(required = false) String sexFilter,
        @RequestParam(required = false) String pigTypeFilter,
        @RequestParam(required = false, defaultValue = "20") Integer limit
    ) {
        return R.ok(pigCoreService.searchByEarKeyword(earNoKeyword, statusFilter, sexFilter, pigTypeFilter, limit));
    }
}
