package org.dromara.djs.breed.farm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.farm.domain.bo.PenBo;
import org.dromara.djs.breed.farm.domain.query.PenQuery;
import org.dromara.djs.breed.farm.domain.vo.PenVo;
import org.dromara.djs.breed.farm.service.IPenService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * 栏位 Controller（BRD-MD-002）。
 *
 * <p>5 端点；权限串 {@code djs:breed:pen:{list,add,edit,remove}}（菜单 seed 7030-7039）。</p>
 *
 * @author djs
 * @since BRD-MD-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/pen")
public class PenController extends BaseController {

    private final IPenService penService;

    /**
     * 分页查询栏位列表（前端树切栋舍后调，必带 barnId）。
     */
    @SaCheckPermission("djs:breed:pen:list")
    @GetMapping("/list")
    public TableDataInfo<PenVo> list(PenQuery query, PageQuery pageQuery) {
        return penService.queryPageList(query, pageQuery);
    }

    /**
     * 根据 ID 查询栏位详情。
     */
    @SaCheckPermission("djs:breed:pen:list")
    @GetMapping("/getInfo/{id}")
    public R<PenVo> getInfo(@PathVariable Long id) {
        return R.ok(penService.queryById(id));
    }

    /**
     * 新增栏位。
     */
    @SaCheckPermission("djs:breed:pen:add")
    @Log(title = "栏位管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody PenBo bo) {
        return toAjax(penService.insertByBo(bo));
    }

    /**
     * 编辑栏位（barnId / penCode 不允许修改）。
     */
    @SaCheckPermission("djs:breed:pen:edit")
    @Log(title = "栏位管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody PenBo bo) {
        return toAjax(penService.updateByBo(bo));
    }

    /**
     * 删除栏位（支持批量，逗号分隔）。删除前校验 {@code t_farm_pig_info.pen_id} 引用。
     */
    @SaCheckPermission("djs:breed:pen:remove")
    @Log(title = "栏位管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(penService.deleteWithValidByIds(Arrays.asList(ids)));
    }

}
