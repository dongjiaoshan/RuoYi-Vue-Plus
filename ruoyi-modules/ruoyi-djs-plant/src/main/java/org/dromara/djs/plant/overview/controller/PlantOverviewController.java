package org.dromara.djs.plant.overview.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.overview.domain.vo.CropDetailVo;
import org.dromara.djs.plant.overview.domain.vo.CropOverviewExportVo;
import org.dromara.djs.plant.overview.domain.vo.PlantOverviewSummaryVo;
import org.dromara.djs.plant.overview.service.IPlantOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 种植总览 Controller（FIX-PLT-AD-OVERVIEW-001）。
 *
 * <p>种植板块新着陆/总览页（替代已删的旧种植看板 PLT-DASH-001）。纯只读：</p>
 * <ul>
 *   <li>GET  /summary            5 产量 KPI（吨）+ 作物卡片列表（kg，可按作物名称过滤）</li>
 *   <li>POST /cropCard/export    按作物名称过滤条件导出作物卡片（一作物一行）</li>
 *   <li>GET  /cropDetail         按 cropId 分页查逐地块明细（14 列）</li>
 *   <li>POST /cropDetail/export  按 cropId 导出当前作物逐地块明细</li>
 * </ul>
 *
 * @author djs
 * @since FIX-PLT-AD-OVERVIEW-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/plant/overview")
public class PlantOverviewController extends BaseController {

    private final IPlantOverviewService overviewService;

    /**
     * 种植总览汇总：5 产量 KPI（吨）+ 作物卡片列表（kg）。
     *
     * @param cropName 作物名称模糊关键字（可空；只过滤卡片列表，KPI 恒全场口径）
     * @return 汇总 VO（无数据时各 KPI 为 0、crops 空列表）
     */
    @SaCheckPermission("djs:plant:overview:view")
    @GetMapping("/summary")
    public R<PlantOverviewSummaryVo> summary(@RequestParam(required = false) String cropName) {
        return R.ok(overviewService.getSummary(cropName));
    }

    /**
     * 导出作物卡片（row147，一作物一行横向展示，与页面同过滤同排序）。
     *
     * @param cropName 作物名称模糊关键字（可空）
     * @param response 响应
     */
    @SaCheckPermission("djs:plant:overview:export")
    @Log(title = "种植-种植总览作物", businessType = BusinessType.EXPORT)
    @PostMapping("/cropCard/export")
    public void exportCropCards(@RequestParam(required = false) String cropName, HttpServletResponse response) {
        List<CropOverviewExportVo> list = overviewService.getCropCardExportList(cropName);
        ExcelUtil.exportExcel(list, "种植总览", CropOverviewExportVo.class, response);
    }

    /**
     * 作物详情：按 cropId 分页查逐地块明细（14 列）。
     *
     * @param cropId    作物 id
     * @param pageQuery 分页参数（默认 20 条/页）
     * @return 明细分页
     */
    @SaCheckPermission("djs:plant:overview:view")
    @GetMapping("/cropDetail")
    public TableDataInfo<CropDetailVo> cropDetail(@RequestParam Long cropId, PageQuery pageQuery) {
        return overviewService.getCropDetailPage(cropId, pageQuery);
    }

    /**
     * 导出当前作物逐地块明细（FastExcel）。
     *
     * @param cropId   作物 id
     * @param response 响应
     */
    @SaCheckPermission("djs:plant:overview:view")
    @Log(title = "种植-种植总览作物明细", businessType = BusinessType.EXPORT)
    @PostMapping("/cropDetail/export")
    public void export(@RequestParam Long cropId, HttpServletResponse response) {
        List<CropDetailVo> list = overviewService.getCropDetailList(cropId);
        String cropName = overviewService.getCropName(cropId);
        String sheetName = StringUtils.isBlank(cropName) ? "作物详情" : "作物详情-" + cropName;
        ExcelUtil.exportExcel(list, sheetName, CropDetailVo.class, response);
    }
}
