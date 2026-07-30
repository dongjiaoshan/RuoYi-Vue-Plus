package org.dromara.djs.plant.perf.controller;

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
import org.dromara.djs.plant.perf.domain.query.PlantWorkPerformanceQuery;
import org.dromara.djs.plant.perf.domain.vo.PerfListRow;
import org.dromara.djs.plant.perf.domain.vo.PlantWorkPerformanceVo;
import org.dromara.djs.plant.perf.service.IPlantWorkPerformanceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 种植班组绩效结算 Controller（PLT-PERF-001）。
 *
 * <p>URL 前缀 {@code /djs/plant/work-performance}。菜单 + 按钮权限 seed 见
 * {@code V202606141400__PLT-PERF-001-performance-align-menu.sql}（menu_id 8200-8203）。</p>
 *
 * <p>绩效由 {@code /generate} 系统生成（手动触发，V1 无定时任务），不提供 add/edit/remove。</p>
 *
 * @author djs
 * @since PLT-PERF-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/plant/work-performance")
public class PlantWorkPerformanceController extends BaseController {

    private final IPlantWorkPerformanceService performanceService;

    /** 分页查询绩效列表（班组 × 月聚合；query: statMonth / teamId）。 */
    @SaCheckPermission("djs:plantPerformance:list")
    @GetMapping("/list")
    public TableDataInfo<PerfListRow> list(PlantWorkPerformanceQuery query, PageQuery pageQuery) {
        return performanceService.queryPageList(query, pageQuery);
    }

    /** 详情按作物分行（产量绩效 tab：某班组某月全部作物的采摘量 / 单价 / 绩效额）。 */
    @SaCheckPermission("djs:plantPerformance:query")
    @GetMapping("/crop-rows")
    public R<List<PlantWorkPerformanceVo>> cropRows(@RequestParam Long teamId, @RequestParam String statMonth) {
        return R.ok(performanceService.queryCropRows(teamId, statMonth));
    }

    /** 手动生成指定月份的班组绩效结算（幂等：先软删该月旧行再聚合 INSERT）。 */
    @SaCheckPermission("djs:plantPerformance:generate")
    @Log(title = "种植-班组绩效", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/generate")
    public R<Integer> generate(@RequestParam String statMonth) {
        return R.ok("结算生成成功", performanceService.generate(statMonth));
    }

    /** 导出绩效列表（班组 × 月聚合行，与主列表一致）。 */
    @SaCheckPermission("djs:plantPerformance:export")
    @Log(title = "种植-班组绩效", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PlantWorkPerformanceQuery query, HttpServletResponse response) {
        List<PerfListRow> list = performanceService.queryList(query);
        ExcelUtil.exportExcel(list, "班组绩效", PerfListRow.class, response);
    }

    /** 导出绩效详情（双 sheet：产量绩效逐作物行 + 该班组该月农事记录，与详情抽屉两 tab 同口径）。 */
    @SaCheckPermission("djs:plantPerformance:export")
    @Log(title = "种植-班组绩效", businessType = BusinessType.EXPORT)
    @PostMapping("/export-detail")
    public void exportDetail(@RequestParam Long teamId, @RequestParam String statMonth, HttpServletResponse response) {
        performanceService.exportDetail(teamId, statMonth, response);
    }
}
