package org.dromara.djs.plant.organic.controller;

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
import org.dromara.djs.plant.organic.domain.bo.CropOrganicBo;
import org.dromara.djs.plant.organic.domain.bo.CropOrganicRelateBo;
import org.dromara.djs.plant.organic.domain.query.CropOrganicQuery;
import org.dromara.djs.plant.organic.domain.vo.CropOrganicRelExportVo;
import org.dromara.djs.plant.organic.domain.vo.CropOrganicVo;
import org.dromara.djs.plant.organic.service.ICropOrganicService;
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
 * 果蔬有机证书 Controller（PLT-MD-003）。
 *
 * <p>URL 前缀 {@code /djs/plant/cropOrganic}；菜单 + 按钮权限 seed 见 V202606051130（menu_id 8060-8065）。</p>
 *
 * @author djs
 * @since PLT-MD-003
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/plant/cropOrganic")
public class CropOrganicController extends BaseController {

    private final ICropOrganicService cropOrganicService;

    /** 分页查询。 */
    @SaCheckPermission("djs:plant:cropOrganic:list")
    @GetMapping("/list")
    public TableDataInfo<CropOrganicVo> list(CropOrganicQuery query, PageQuery pageQuery) {
        return cropOrganicService.queryPageList(query, pageQuery);
    }

    /** 导出。 */
    @SaCheckPermission("djs:plant:cropOrganic:export")
    @Log(title = "种植-果蔬有机证书", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(CropOrganicQuery query, HttpServletResponse response) {
        List<CropOrganicVo> list = cropOrganicService.queryList(query);
        ExcelUtil.exportExcel(list, "果蔬有机证书", CropOrganicVo.class, response);
    }

    /**
     * 导出某证书已关联的作物明细（row148，一作物一行：证书编号 / 作物名称 / 作物编号）。
     *
     * <p>行操作「导出」入口，复用整表导出权限。</p>
     *
     * @param id       证书 id
     * @param response 响应
     */
    @SaCheckPermission("djs:plant:cropOrganic:export")
    @Log(title = "种植-果蔬有机证书关联作物", businessType = BusinessType.EXPORT)
    @PostMapping("/relatedCrops/export")
    public void exportRelatedCrops(@RequestParam Long id, HttpServletResponse response) {
        List<CropOrganicRelExportVo> list = cropOrganicService.queryRelatedCropsForExport(id);
        ExcelUtil.exportExcel(list, "证书关联作物", CropOrganicRelExportVo.class, response);
    }

    /** 详情。 */
    @SaCheckPermission("djs:plant:cropOrganic:list")
    @GetMapping("/getInfo/{id}")
    public R<CropOrganicVo> getInfo(@PathVariable Long id) {
        return R.ok(cropOrganicService.queryById(id));
    }

    /** 新增。 */
    @SaCheckPermission("djs:plant:cropOrganic:add")
    @Log(title = "种植-果蔬有机证书", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated({CropOrganicBo.OnCreate.class, Default.class}) @RequestBody CropOrganicBo bo) {
        return toAjax(cropOrganicService.insertByBo(bo));
    }

    /** 编辑（crop_cert_no 不允许修改）。 */
    @SaCheckPermission("djs:plant:cropOrganic:edit")
    @Log(title = "种植-果蔬有机证书", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody CropOrganicBo bo) {
        return toAjax(cropOrganicService.updateByBo(bo));
    }

    /** 删除（支持批量）。 */
    @SaCheckPermission("djs:plant:cropOrganic:remove")
    @Log(title = "种植-果蔬有机证书", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(cropOrganicService.deleteWithValidByIds(Arrays.asList(ids)));
    }

    /**
     * 关联作物（列表行操作「关联作物」el-transfer 弹窗，一证多作物）。
     *
     * <p>复用 edit 权限，仅更新证书-作物关联，不动证书主行。</p>
     */
    @SaCheckPermission("djs:plant:cropOrganic:edit")
    @Log(title = "种植-果蔬有机证书关联作物", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/relate")
    public R<Void> relate(@Validated @RequestBody CropOrganicRelateBo bo) {
        return toAjax(cropOrganicService.relateCrops(bo));
    }
}
