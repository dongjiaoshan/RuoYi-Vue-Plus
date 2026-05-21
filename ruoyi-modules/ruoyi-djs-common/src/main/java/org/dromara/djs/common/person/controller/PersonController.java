package org.dromara.djs.common.person.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.common.person.domain.bo.PersonBo;
import org.dromara.djs.common.person.domain.query.PersonQuery;
import org.dromara.djs.common.person.domain.vo.PersonVo;
import org.dromara.djs.common.person.service.IPersonService;
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
import java.util.List;

/**
 * 人员主数据 Controller（SYS-MD-001）。
 *
 * <p>本模块为首个业务 CRUD 范例，后续 SYS-MD-002 / 003 / BRD-MD-* / WMS-MD-* 等主数据严格按本结构复制：</p>
 * <ul>
 *   <li>5 端点 list / getInfo / add / edit / remove；额外 export</li>
 *   <li>权限串 {@code djs:<域>:<resource>:{list,add,edit,remove,export}}，与前端 {@code v-hasPermi} 完全对齐</li>
 *   <li>分页：list 端点同时绑定 {@link PersonQuery} + {@link PageQuery}</li>
 *   <li>删除：{@code DELETE /remove/{ids}} 支持批量（逗号分隔）</li>
 * </ul>
 *
 * @author djs
 * @since SYS-MD-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/common/person")
public class PersonController extends BaseController {

    private final IPersonService personService;

    /**
     * 分页查询人员列表。
     */
    @SaCheckPermission("djs:common:person:list")
    @GetMapping("/list")
    public TableDataInfo<PersonVo> list(PersonQuery query, PageQuery pageQuery) {
        return personService.queryPageList(query, pageQuery);
    }

    /**
     * 导出人员列表（不分页，应用相同筛选条件）。
     */
    @SaCheckPermission("djs:common:person:export")
    @Log(title = "人员管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PersonQuery query, HttpServletResponse response) {
        List<PersonVo> list = personService.queryList(query);
        ExcelUtil.exportExcel(list, "人员数据", PersonVo.class, response);
    }

    /**
     * 根据 ID 查询人员详情。
     */
    @SaCheckPermission("djs:common:person:list")
    @GetMapping("/getInfo/{id}")
    public R<PersonVo> getInfo(@PathVariable Long id) {
        return R.ok(personService.queryById(id));
    }

    /**
     * 新增人员。
     */
    @SaCheckPermission("djs:common:person:add")
    @Log(title = "人员管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody PersonBo bo) {
        return toAjax(personService.insertByBo(bo));
    }

    /**
     * 编辑人员。
     */
    @SaCheckPermission("djs:common:person:edit")
    @Log(title = "人员管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody PersonBo bo) {
        return toAjax(personService.updateByBo(bo));
    }

    /**
     * 删除人员（支持批量，逗号分隔）。
     */
    @SaCheckPermission("djs:common:person:remove")
    @Log(title = "人员管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(personService.deleteWithValidByIds(Arrays.asList(ids)));
    }

}
