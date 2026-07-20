package org.dromara.djs.plant.plot.controller;

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
import org.dromara.djs.plant.plot.domain.bo.PlotInfoBo;
import org.dromara.djs.plant.plot.domain.query.PlotInfoQuery;
import org.dromara.djs.plant.plot.domain.vo.PlotFarmworkRecordVo;
import org.dromara.djs.plant.plot.domain.vo.PlotInfoVo;
import org.dromara.djs.plant.plot.domain.vo.PlotOrganicRefVo;
import org.dromara.djs.plant.plot.domain.vo.PlotPlantingRecordVo;
import org.dromara.djs.plant.plot.service.IPlotInfoService;
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
 * 地块 Controller（PLT-MD-001）。
 *
 * <p>URL 前缀 {@code /djs/plant/plot}；菜单 + 按钮权限 seed 见 V202606050900（menu_id 8010 / 8014-8017）。</p>
 *
 * @author djs
 * @since PLT-MD-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/plant/plot")
public class PlotInfoController extends BaseController {

    private final IPlotInfoService plotInfoService;

    /** 分页查询。 */
    @SaCheckPermission("djs:plant:plot:list")
    @GetMapping("/list")
    public TableDataInfo<PlotInfoVo> list(PlotInfoQuery query, PageQuery pageQuery) {
        return plotInfoService.queryPageList(query, pageQuery);
    }

    /** 导出。 */
    @SaCheckPermission("djs:plant:plot:export")
    @Log(title = "种植-地块", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PlotInfoQuery query, HttpServletResponse response) {
        List<PlotInfoVo> list = plotInfoService.queryList(query);
        ExcelUtil.exportExcel(list, "地块数据", PlotInfoVo.class, response);
    }

    /** 详情。 */
    @SaCheckPermission("djs:plant:plot:list")
    @GetMapping("/getInfo/{id}")
    public R<PlotInfoVo> getInfo(@PathVariable Long id) {
        return R.ok(plotInfoService.queryById(id));
    }

    /** 新增。 */
    @SaCheckPermission("djs:plant:plot:add")
    @Log(title = "种植-地块", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated({PlotInfoBo.OnCreate.class, Default.class}) @RequestBody PlotInfoBo bo) {
        return toAjax(plotInfoService.insertByBo(bo));
    }

    /** 编辑（plot_code 不允许修改）。 */
    @SaCheckPermission("djs:plant:plot:edit")
    @Log(title = "种植-地块", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody PlotInfoBo bo) {
        return toAjax(plotInfoService.updateByBo(bo));
    }

    /** 删除（支持批量）。 */
    @SaCheckPermission("djs:plant:plot:remove")
    @Log(title = "种植-地块", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(plotInfoService.deleteWithValidByIds(Arrays.asList(ids)));
    }

    /** 地块详情·种植信息子表（FIX-PLT-AD-DETAIL-001，11 列，按 plotId 透视）。 */
    @SaCheckPermission("djs:plant:plot:list")
    @GetMapping("/{plotId}/planting")
    public R<List<PlotPlantingRecordVo>> listPlantingByPlot(@PathVariable Long plotId) {
        return R.ok(plotInfoService.listPlantingByPlot(plotId));
    }

    /** 地块详情·农事信息子表（FIX-PLT-AD-DETAIL-001，按 plotId 透视）。 */
    @SaCheckPermission("djs:plant:plot:list")
    @GetMapping("/{plotId}/farmwork")
    public R<List<PlotFarmworkRecordVo>> listFarmworkByPlot(@PathVariable Long plotId) {
        return R.ok(plotInfoService.listFarmworkByPlot(plotId));
    }

    /** 地块详情·认证信息子表（FIX-PLT-AD-DETAIL-001，按 plotId 关联有机证书）。 */
    @SaCheckPermission("djs:plant:plot:list")
    @GetMapping("/{plotId}/organic")
    public R<List<PlotOrganicRefVo>> listOrganicByPlot(@PathVariable Long plotId) {
        return R.ok(plotInfoService.listOrganicByPlot(plotId));
    }
}
