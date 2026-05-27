package org.dromara.djs.plant.crop.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.crop.domain.bo.CropInfoBo;
import org.dromara.djs.plant.crop.domain.query.CropInfoQuery;
import org.dromara.djs.plant.crop.domain.vo.CropInfoVo;
import org.dromara.djs.plant.crop.service.ICropInfoService;
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
 * 作物 Controller（PLT-MD-001）。
 *
 * @author djs
 * @since PLT-MD-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/plant/crop")
public class CropInfoController extends BaseController {

    private final ICropInfoService cropInfoService;

    @SaCheckPermission("djs:plant:crop:list")
    @GetMapping("/list")
    public TableDataInfo<CropInfoVo> list(CropInfoQuery query, PageQuery pageQuery) {
        return cropInfoService.queryPageList(query, pageQuery);
    }

    @SaCheckPermission("djs:plant:crop:export")
    @Log(title = "种植-作物", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(CropInfoQuery query, HttpServletResponse response) {
        List<CropInfoVo> list = cropInfoService.queryList(query);
        ExcelUtil.exportExcel(list, "作物数据", CropInfoVo.class, response);
    }

    @SaCheckPermission("djs:plant:crop:list")
    @GetMapping("/getInfo/{id}")
    public R<CropInfoVo> getInfo(@PathVariable Long id) {
        return R.ok(cropInfoService.queryById(id));
    }

    @SaCheckPermission("djs:plant:crop:add")
    @Log(title = "种植-作物", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated({CropInfoBo.OnCreate.class, Default.class}) @RequestBody CropInfoBo bo) {
        return toAjax(cropInfoService.insertByBo(bo));
    }

    @SaCheckPermission("djs:plant:crop:edit")
    @Log(title = "种植-作物", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody CropInfoBo bo) {
        return toAjax(cropInfoService.updateByBo(bo));
    }

    @SaCheckPermission("djs:plant:crop:remove")
    @Log(title = "种植-作物", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(cropInfoService.deleteWithValidByIds(Arrays.asList(ids)));
    }
}
