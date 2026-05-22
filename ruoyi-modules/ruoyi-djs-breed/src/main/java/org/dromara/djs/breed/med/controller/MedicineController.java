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
import org.dromara.djs.breed.med.domain.bo.MedicineBo;
import org.dromara.djs.breed.med.domain.query.MedicineQuery;
import org.dromara.djs.breed.med.domain.vo.MedicineVo;
import org.dromara.djs.breed.med.service.IMedicineService;
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
 * 药品库 Controller（BRD-MED-001）。
 *
 * <p>5 端点 list / getInfo / add / edit / remove + export；
 * 权限串 {@code djs:breed:med:{list,add,edit,remove,export}}（菜单 seed 在
 * {@code V202605221100__BRD-MED-001-medicine-batch.sql}，menu_id 7040-7049）。</p>
 *
 * @author djs
 * @since BRD-MED-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/med")
public class MedicineController extends BaseController {

    private final IMedicineService medicineService;

    /**
     * 分页查询药品列表。
     */
    @SaCheckPermission("djs:breed:med:list")
    @GetMapping("/list")
    public TableDataInfo<MedicineVo> list(MedicineQuery query, PageQuery pageQuery) {
        return medicineService.queryPageList(query, pageQuery);
    }

    /**
     * 导出药品列表（不分页）。
     */
    @SaCheckPermission("djs:breed:med:export")
    @Log(title = "药品管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(MedicineQuery query, HttpServletResponse response) {
        List<MedicineVo> list = medicineService.queryList(query);
        ExcelUtil.exportExcel(list, "药品数据", MedicineVo.class, response);
    }

    /**
     * 根据 ID 查询药品详情。
     */
    @SaCheckPermission("djs:breed:med:list")
    @GetMapping("/getInfo/{id}")
    public R<MedicineVo> getInfo(@PathVariable Long id) {
        return R.ok(medicineService.queryById(id));
    }

    /**
     * 新增药品。
     */
    @SaCheckPermission("djs:breed:med:add")
    @Log(title = "药品管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated({MedicineBo.OnCreate.class, jakarta.validation.groups.Default.class}) @RequestBody MedicineBo bo) {
        return toAjax(medicineService.insertByBo(bo));
    }

    /**
     * 编辑药品（{@code medicineCode} 不允许修改）。
     */
    @SaCheckPermission("djs:breed:med:edit")
    @Log(title = "药品管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody MedicineBo bo) {
        return toAjax(medicineService.updateByBo(bo));
    }

    /**
     * 删除药品（支持批量，逗号分隔）。
     */
    @SaCheckPermission("djs:breed:med:remove")
    @Log(title = "药品管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(medicineService.deleteWithValidByIds(Arrays.asList(ids)));
    }

}
