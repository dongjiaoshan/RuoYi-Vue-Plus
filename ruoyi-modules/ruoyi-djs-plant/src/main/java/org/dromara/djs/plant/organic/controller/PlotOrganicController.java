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
import org.dromara.djs.plant.organic.domain.bo.PlotOrganicBo;
import org.dromara.djs.plant.organic.domain.query.PlotOrganicQuery;
import org.dromara.djs.plant.organic.domain.vo.PlotOrganicVo;
import org.dromara.djs.plant.organic.job.OrganicWarningJob;
import org.dromara.djs.plant.organic.service.IPlotOrganicService;
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
 * 土地有机证书 Controller（PLT-MD-003）。
 *
 * <p>URL 前缀 {@code /djs/plant/plotOrganic}；菜单 + 按钮权限 seed 见 V202606051130（menu_id 8050-8055）。
 *    {@code /scanWarning} 端点供管理员手动触发扫描（V1 测试用），生产由 Spring @Scheduled 自动跑。</p>
 *
 * @author djs
 * @since PLT-MD-003
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/plant/plotOrganic")
public class PlotOrganicController extends BaseController {

    private final IPlotOrganicService plotOrganicService;
    private final OrganicWarningJob organicWarningJob;

    /** 分页查询。 */
    @SaCheckPermission("djs:plant:plotOrganic:list")
    @GetMapping("/list")
    public TableDataInfo<PlotOrganicVo> list(PlotOrganicQuery query, PageQuery pageQuery) {
        return plotOrganicService.queryPageList(query, pageQuery);
    }

    /** 导出。 */
    @SaCheckPermission("djs:plant:plotOrganic:export")
    @Log(title = "种植-土地有机证书", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PlotOrganicQuery query, HttpServletResponse response) {
        List<PlotOrganicVo> list = plotOrganicService.queryList(query);
        ExcelUtil.exportExcel(list, "土地有机证书", PlotOrganicVo.class, response);
    }

    /** 详情。 */
    @SaCheckPermission("djs:plant:plotOrganic:list")
    @GetMapping("/getInfo/{id}")
    public R<PlotOrganicVo> getInfo(@PathVariable Long id) {
        return R.ok(plotOrganicService.queryById(id));
    }

    /** 新增。 */
    @SaCheckPermission("djs:plant:plotOrganic:add")
    @Log(title = "种植-土地有机证书", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated({PlotOrganicBo.OnCreate.class, Default.class}) @RequestBody PlotOrganicBo bo) {
        return toAjax(plotOrganicService.insertByBo(bo));
    }

    /** 编辑（organic_no 不允许修改）。 */
    @SaCheckPermission("djs:plant:plotOrganic:edit")
    @Log(title = "种植-土地有机证书", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody PlotOrganicBo bo) {
        return toAjax(plotOrganicService.updateByBo(bo));
    }

    /** 删除（支持批量）。 */
    @SaCheckPermission("djs:plant:plotOrganic:remove")
    @Log(title = "种植-土地有机证书", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(plotOrganicService.deleteWithValidByIds(Arrays.asList(ids)));
    }

    /**
     * 手动触发预警扫描（V1 管理员调试 / 演示用）。
     *
     * <p>生产由 {@code OrganicWarningJob#scanWarning()} 通过 Spring @Scheduled 每天 0 点自动跑。</p>
     */
    @SaCheckPermission("djs:plant:plotOrganic:edit")
    @Log(title = "种植-土地有机证书-触发预警扫描", businessType = BusinessType.OTHER)
    @PostMapping("/scanWarning")
    public R<Void> scanWarning() {
        organicWarningJob.scanWarning();
        return R.ok();
    }
}
