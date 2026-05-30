package org.dromara.djs.breed.event.growth.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.growth.domain.query.GrowthQuery;
import org.dromara.djs.breed.event.growth.domain.vo.PigGrowthVo;
import org.dromara.djs.breed.event.growth.service.IPigGrowthService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序生长记录列表 Controller（BRD-FIX-MP-EVENT-MISC-IA-001）。
 *
 * <p>专给 mp 端"生长记录"页第 3 段「记录列表」timeline（原型 15）用——按 earNo / pigId 拉
 * 某猪只历史生长记录（measureDate / ageDays / operatorName / photoOssIds，倒序）。</p>
 *
 * <h2>端点</h2>
 * <ul>
 *   <li>{@code GET /djs/applet/breed/growth/list?earNo=&pigId=}
 *       分页返 {@link PigGrowthVo}（含 enrich 的 ageDays）。</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 * <p>{@code @SaCheckPermission("djs:applet:breed:growth:list")} — mp / staff / employee
 * role 默认含；菜单 seed 见 {@code V202606111700__BRD-FIX-MP-EVENT-MISC-IA-applet-growth-menu.sql}。
 * admin 端 {@code djs:breed:event:growth:list} 走另一 controller，权限分离。</p>
 *
 * @author djs
 * @since BRD-FIX-MP-EVENT-MISC-IA-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/applet/breed/growth")
public class GrowthAppletController {

    private final IPigGrowthService growthService;

    /**
     * mp 端按猪只拉生长记录 timeline。
     *
     * <p>{@code earNo} / {@code pigId} 至少传一个（GrowthQuery 都为空时退化为全场最近记录，
     * mp 端业务上总会先选猪再进记录列表）。倒序 by measureDate，含 enrich 的 ageDays。</p>
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:breed:growth:list")
    @GetMapping("/list")
    public TableDataInfo<PigGrowthVo> list(GrowthQuery query, PageQuery pageQuery) {
        return growthService.queryPage(query, pageQuery);
    }
}
