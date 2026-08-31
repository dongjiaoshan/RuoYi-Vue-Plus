package org.dromara.djs.warehouse.trace.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.trace.domain.bo.TracePageConfigImageBo;
import org.dromara.djs.warehouse.trace.domain.vo.TracePageConfigVo;
import org.dromara.djs.warehouse.trace.service.ITracePageConfigService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 追溯码配置管理 admin Controller（V6-R146，admin only 无 mp）。
 *
 * <p><b>为什么权限串是 {@code djs:common:*} 而代码在 warehouse 模块</b>：菜单按甲方要求挂在
 * 「系统管理 → 数据管理」下，menu_id / perms 在 Flyway 里已定死为 {@code djs:common:traceCodeConfig:*}；
 * 而配置的数据归属是追溯域，追溯表与写码 hook 都在 {@code ruoyi-djs-warehouse}（避免 warehouse→store
 * 反向依赖），所以代码放这里。<b>不是放错模块</b>，权限审计时别按模块名回改。</p>
 *
 * <h3>只有三个端点</h3>
 * <p>list / query / 换图。<b>没有</b> add / remove：pork、veg 两行是 Flyway 预置的配置项，
 * 删掉一行公开端点就取不到基地介绍图了。</p>
 *
 * @author djs
 * @since V6-R146
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/common/traceCodeConfig")
public class TracePageConfigController extends BaseController {

    private final ITracePageConfigService tracePageConfigService;

    /**
     * 配置列表（固定两行，无搜索无分页）。
     */
    @SaCheckPermission("djs:common:traceCodeConfig:list")
    @GetMapping("/list")
    public R<List<TracePageConfigVo>> list() {
        return R.ok(tracePageConfigService.queryList());
    }

    /**
     * 单条配置详情（上传弹窗打开时取最新值回填）。
     */
    @SaCheckPermission("djs:common:traceCodeConfig:query")
    @GetMapping("/{id}")
    public R<TracePageConfigVo> getInfo(@PathVariable Long id) {
        return R.ok(tracePageConfigService.getVoById(id));
    }

    /**
     * 保存基地介绍页图片（换图；ossId 传空 = 清空，H5 回落内置版式）。
     */
    @SaCheckPermission("djs:common:traceCodeConfig:upload")
    @RepeatSubmit
    @Log(title = "追溯码配置管理", businessType = BusinessType.UPDATE)
    @PutMapping("/image")
    public R<Void> updateImage(@Validated @RequestBody TracePageConfigImageBo bo) {
        return toAjax(tracePageConfigService.updateImage(bo));
    }
}
