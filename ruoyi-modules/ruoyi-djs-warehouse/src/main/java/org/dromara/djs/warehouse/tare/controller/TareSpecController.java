package org.dromara.djs.warehouse.tare.controller;

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
import org.dromara.djs.warehouse.tare.domain.bo.TareSpecBo;
import org.dromara.djs.warehouse.tare.domain.vo.TareOptionVo;
import org.dromara.djs.warehouse.tare.domain.vo.TareSpecVo;
import org.dromara.djs.warehouse.tare.service.ITareSpecService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 去皮规格 Controller（V7-R2 维护 / V7-R4 称重页取用）。权限串 {@code djs:warehouse:tareSpec:*}，菜单 5800-5804。
 *
 * @author djs
 * @since V7-R2
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/warehouse/tareSpec")
public class TareSpecController extends BaseController {

    private final ITareSpecService tareSpecService;

    /**
     * 分页查询去皮规格。
     */
    @SaCheckPermission("djs:warehouse:tareSpec:list")
    @GetMapping("/list")
    public TableDataInfo<TareSpecVo> list(@RequestParam(required = false) String specName, PageQuery pageQuery) {
        return tareSpecService.queryPageList(specName, pageQuery);
    }

    /**
     * 去皮规格详情。
     */
    @SaCheckPermission("djs:warehouse:tareSpec:query")
    @GetMapping("/{id}")
    public R<TareSpecVo> getInfo(@PathVariable Long id) {
        return R.ok(tareSpecService.queryById(id));
    }

    /**
     * 新增去皮规格。
     */
    @SaCheckPermission("djs:warehouse:tareSpec:add")
    @Log(title = "去皮规格管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Void> add(@Validated @RequestBody TareSpecBo bo) {
        return toAjax(tareSpecService.insertByBo(bo));
    }

    /**
     * 修改去皮规格。
     */
    @SaCheckPermission("djs:warehouse:tareSpec:edit")
    @Log(title = "去皮规格管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping
    public R<Void> edit(@Validated @RequestBody TareSpecBo bo) {
        return toAjax(tareSpecService.updateByBo(bo));
    }

    /**
     * 删除去皮规格（软删，支持批量）。
     */
    @SaCheckPermission("djs:warehouse:tareSpec:remove")
    @Log(title = "去皮规格管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(tareSpecService.deleteByIds(Arrays.asList(ids)));
    }

    /**
     * 称重页可选去皮规格。六个称重页各有自己的操作权限，规格本身不敏感，登录即可读。
     *
     * @param position 字典 {@code djs_tare_position} 值
     */
    @SaCheckLogin
    @GetMapping("/options")
    public R<List<TareOptionVo>> options(@RequestParam String position) {
        return R.ok(tareSpecService.listOptions(position));
    }

}
