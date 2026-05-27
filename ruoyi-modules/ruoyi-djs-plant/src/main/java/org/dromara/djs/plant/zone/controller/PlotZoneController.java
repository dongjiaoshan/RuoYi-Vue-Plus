package org.dromara.djs.plant.zone.controller;

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
import org.dromara.djs.plant.zone.domain.bo.PlotZoneBo;
import org.dromara.djs.plant.zone.domain.query.PlotZoneQuery;
import org.dromara.djs.plant.zone.domain.vo.PlotZoneVo;
import org.dromara.djs.plant.zone.service.IPlotZoneService;
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
 * 片区 Controller（PLT-MD-001）。
 *
 * <p>菜单 + 按钮权限 seed 见 {@code V202606050900__PLT-MD-001-plant-master-data.sql}（menu_id 8011-8013）。
 * URL 前缀 {@code /djs/plant/zone}。</p>
 *
 * @author djs
 * @since PLT-MD-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/plant/zone")
public class PlotZoneController extends BaseController {

    private final IPlotZoneService plotZoneService;

    /** 分页查询片区列表。 */
    @SaCheckPermission("djs:plant:plot:list")
    @GetMapping("/list")
    public TableDataInfo<PlotZoneVo> list(PlotZoneQuery query, PageQuery pageQuery) {
        return plotZoneService.queryPageList(query, pageQuery);
    }

    /** 列表（不分页，给左栏树用）。 */
    @SaCheckPermission("djs:plant:plot:list")
    @GetMapping("/listAll")
    public R<List<PlotZoneVo>> listAll(PlotZoneQuery query) {
        return R.ok(plotZoneService.queryList(query));
    }

    /** 详情。 */
    @SaCheckPermission("djs:plant:plot:list")
    @GetMapping("/getInfo/{id}")
    public R<PlotZoneVo> getInfo(@PathVariable Long id) {
        return R.ok(plotZoneService.queryById(id));
    }

    /** 新增。 */
    @SaCheckPermission("djs:plant:zone:add")
    @Log(title = "种植-片区", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated({PlotZoneBo.OnCreate.class, Default.class}) @RequestBody PlotZoneBo bo) {
        return toAjax(plotZoneService.insertByBo(bo));
    }

    /** 修改。 */
    @SaCheckPermission("djs:plant:zone:edit")
    @Log(title = "种植-片区", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody PlotZoneBo bo) {
        return toAjax(plotZoneService.updateByBo(bo));
    }

    /** 删除（支持批量；删除前若有关联地块抛 plant.zone.has_plot）。 */
    @SaCheckPermission("djs:plant:zone:remove")
    @Log(title = "种植-片区", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(plotZoneService.deleteWithValidByIds(Arrays.asList(ids)));
    }

    /** 导出。 */
    @SaCheckPermission("djs:plant:plot:export")
    @Log(title = "种植-片区", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PlotZoneQuery query, HttpServletResponse response) {
        List<PlotZoneVo> list = plotZoneService.queryList(query);
        ExcelUtil.exportExcel(list, "片区数据", PlotZoneVo.class, response);
    }
}
