package org.dromara.djs.breed.med.controller;

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
import org.dromara.djs.breed.med.domain.bo.MedBatchBo;
import org.dromara.djs.breed.med.domain.query.MedBatchQuery;
import org.dromara.djs.breed.med.domain.vo.MedBatchVo;
import org.dromara.djs.breed.med.service.IMedBatchService;
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
 * 药品批次 Controller（BRD-MED-001）。
 *
 * <p>5 端点 list / getInfo / add / edit / remove + export；
 * 权限串 {@code djs:breed:med-batch:{list,add,edit,remove,export}}。</p>
 *
 * @author djs
 * @since BRD-MED-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/med-batch")
public class MedBatchController extends BaseController {

    private final IMedBatchService medBatchService;

    /**
     * 分页查询批次列表（admin 药品批次页）。
     */
    @SaCheckPermission("djs:breed:med-batch:list")
    @GetMapping("/list")
    public TableDataInfo<MedBatchVo> list(MedBatchQuery query, PageQuery pageQuery) {
        return medBatchService.queryPageList(query, pageQuery);
    }

    /**
     * 导出批次列表（不分页）。
     */
    @SaCheckPermission("djs:breed:med-batch:export")
    @Log(title = "药品批次", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(MedBatchQuery query, HttpServletResponse response) {
        List<MedBatchVo> list = medBatchService.queryList(query);
        ExcelUtil.exportExcel(list, "药品批次", MedBatchVo.class, response);
    }

    /**
     * 根据 ID 查询批次详情。
     */
    @SaCheckPermission("djs:breed:med-batch:list")
    @GetMapping("/getInfo/{id}")
    public R<MedBatchVo> getInfo(@PathVariable Long id) {
        return R.ok(medBatchService.queryById(id));
    }

    /**
     * 新增批次。
     */
    @SaCheckPermission("djs:breed:med-batch:add")
    @Log(title = "药品批次", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody MedBatchBo bo) {
        return toAjax(medBatchService.insertByBo(bo));
    }

    /**
     * 编辑批次（{@code medicineId} 不允许修改）。
     */
    @SaCheckPermission("djs:breed:med-batch:edit")
    @Log(title = "药品批次", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody MedBatchBo bo) {
        return toAjax(medBatchService.updateByBo(bo));
    }

    /**
     * 删除批次（支持批量，逗号分隔）。
     */
    @SaCheckPermission("djs:breed:med-batch:remove")
    @Log(title = "药品批次", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(medBatchService.deleteWithValidByIds(Arrays.asList(ids)));
    }

}
