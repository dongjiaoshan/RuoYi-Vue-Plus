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
import org.dromara.djs.breed.farm.domain.bo.BarnBo;
import org.dromara.djs.breed.farm.domain.bo.BarnCreateBo;
import org.dromara.djs.breed.farm.domain.query.BarnQuery;
import org.dromara.djs.breed.farm.domain.vo.BarnVo;
import org.dromara.djs.breed.farm.service.IBarnService;
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
 * 栋舍 Controller（BRD-MD-002）。
 *
 * <p>5 端点 list / getInfo / add / edit / remove，权限串
 * {@code djs:breed:barn:{list,add,edit,remove}}（菜单 seed 在
 * {@code V202605221100__BRD-MD-002-farm-barn-pen-menu.sql} 7020-7029 段）。</p>
 *
 * @author djs
 * @since BRD-MD-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/barn")
public class BarnController extends BaseController {

    private final IBarnService barnService;

    /**
     * 分页查询栋舍列表（前端左侧树同样走 list，pageSize 取大值取全量）。
     */
    @SaCheckPermission("djs:breed:barn:list")
    @GetMapping("/list")
    public TableDataInfo<BarnVo> list(BarnQuery query, PageQuery pageQuery) {
        return barnService.queryPageList(query, pageQuery);
    }

    /**
     * 根据 ID 查询栋舍详情。
     */
    @SaCheckPermission("djs:breed:barn:list")
    @GetMapping("/getInfo/{id}")
    public R<BarnVo> getInfo(@PathVariable Long id) {
        return R.ok(barnService.queryById(id));
    }

    /**
     * 新增栋舍。
     */
    @SaCheckPermission("djs:breed:barn:add")
    @Log(title = "栋舍管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody BarnBo bo) {
        return toAjax(barnService.insertByBo(bo));
    }

    /**
     * 新建栋舍并按三类数量批量生成栏位（对齐原型「新建栋舍」：填大栏 / 限位栏 / 产床数量自动建栏位）。
     */
    @SaCheckPermission("djs:breed:barn:add")
    @Log(title = "栋舍管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/createWithPens")
    public R<Long> createWithPens(@Validated @RequestBody BarnCreateBo bo) {
        return R.ok(barnService.createWithPens(bo));
    }

    /**
     * 编辑栋舍。
     */
    @SaCheckPermission("djs:breed:barn:edit")
    @Log(title = "栋舍管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody BarnBo bo) {
        return toAjax(barnService.updateByBo(bo));
    }

    /**
     * 删除栋舍（支持批量，逗号分隔）。删除前校验 {@code t_farm_pig_info} 引用。
     */
    @SaCheckPermission("djs:breed:barn:remove")
    @Log(title = "栋舍管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(barnService.deleteWithValidByIds(Arrays.asList(ids)));
    }

}
