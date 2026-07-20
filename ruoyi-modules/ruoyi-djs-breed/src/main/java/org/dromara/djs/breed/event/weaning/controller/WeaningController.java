package org.dromara.djs.breed.event.weaning.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.event.weaning.domain.bo.WeaningBo;
import org.dromara.djs.breed.event.weaning.domain.query.WeaningQuery;
import org.dromara.djs.breed.event.weaning.domain.vo.PigWeaningVo;
import org.dromara.djs.breed.event.weaning.domain.vo.WeaningPigletVo;
import org.dromara.djs.breed.event.weaning.service.IWeaningService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 断奶事件 Controller（BRD-EVENT-002 WEAN）。
 *
 * <p>权限串 {@code djs:breed:event:heat*} —— 与查情 / 返空合并在同一二级菜单 7127，
 * 复用列表 / 写入按钮权限。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/event/weaning")
public class WeaningController extends BaseController {

    private final IWeaningService weaningService;

    @SaCheckPermission("djs:breed:event:heat")
    @Log(title = "断奶录入", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<PigWeaningVo> record(@Validated @RequestBody WeaningBo bo) {
        return R.ok(weaningService.recordWeaning(bo));
    }

    @SaCheckPermission("djs:breed:event:heat:list")
    @GetMapping("/list")
    public TableDataInfo<PigWeaningVo> list(WeaningQuery query, PageQuery pageQuery) {
        return weaningService.queryPage(query, pageQuery);
    }

    /**
     * 按关联分娩查已贴耳标的仔猪（mp 端断奶逐头录重铺行用，FIX-WEAN-001 #31，原型 91）。
     *
     * <p>只读查询；权限复用 mp 选猪同串 {@code djs:applet:pig:search}（mp role 默认含），
     * 与断奶录入 picker 同源。该分娩无打标行时返空数组，mp 端按 live_born 数量退化铺行。</p>
     *
     * @param farrowId 关联分娩记录 ID
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/piglets")
    public R<List<WeaningPigletVo>> piglets(@RequestParam Long farrowId) {
        return R.ok(weaningService.listPigletsByFarrow(farrowId));
    }
}
