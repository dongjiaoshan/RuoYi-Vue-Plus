package org.dromara.djs.warehouse.trace.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.trace.domain.query.TraceCodeQuery;
import org.dromara.djs.warehouse.trace.domain.vo.TraceCodeDetailVo;
import org.dromara.djs.warehouse.trace.domain.vo.TraceCodeListVo;
import org.dromara.djs.warehouse.trace.domain.vo.TraceEventVo;
import org.dromara.djs.warehouse.trace.service.ITraceCodeAdminService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 追溯码管理 admin Controller（TRC-ADMIN-001，admin only 无 mp）。
 *
 * <p>追溯后端归 warehouse 模块（避免 warehouse→store 反向依赖），菜单挂门店/追溯树 10500。
 * 权限串 {@code djs:warehouse:trace:{list,query,export,print}}（与 fe 菜单 perms 完全一致）。</p>
 *
 * <h3>追溯链不可篡改硬约束</h3>
 * <p>本 Controller <b>纯只读</b>：列表 / 详情 / 事件链 / 导出 / 批量取数。
 * <b>绝无</b>任何 {@code @PostMapping/@PutMapping/@DeleteMapping} 写端点改追溯码或事件
 * （{@code /export} / {@code /batch} 用 POST 仅因入参体大，非数据写入）。doc/10 §F-TRC-01。</p>
 *
 * @author djs
 * @since TRC-ADMIN-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/warehouse/trace")
public class TraceCodeAdminController extends BaseController {

    private final ITraceCodeAdminService traceCodeAdminService;

    /**
     * 追溯码分页列表（按 codeType=pork/veg/gift 等过滤）。
     */
    @SaCheckPermission("djs:warehouse:trace:list")
    @GetMapping("/list")
    public TableDataInfo<TraceCodeListVo> list(TraceCodeQuery query, PageQuery pageQuery) {
        return traceCodeAdminService.queryPage(query, pageQuery);
    }

    /**
     * 追溯码详情（主表 + 关联 + 完整事件时间轴）。
     */
    @SaCheckPermission("djs:warehouse:trace:query")
    @GetMapping("/{id}")
    public R<TraceCodeDetailVo> detail(@PathVariable Long id) {
        return R.ok(traceCodeAdminService.getDetail(id));
    }

    /**
     * 按 produceCode 查事件流水（trace_time 升序，供前端单独刷新时间轴）。
     */
    @SaCheckPermission("djs:warehouse:trace:query")
    @GetMapping("/events/{produceCode}")
    public R<List<TraceEventVo>> events(@PathVariable String produceCode) {
        return R.ok(traceCodeAdminService.getEvents(produceCode));
    }

    /**
     * 导出追溯码列表 Excel（不分页）。
     */
    @SaCheckPermission("djs:warehouse:trace:export")
    @Log(title = "追溯码管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(TraceCodeQuery query, HttpServletResponse response) {
        List<TraceCodeListVo> list = traceCodeAdminService.export(query);
        ExcelUtil.exportExcel(list, "追溯码", TraceCodeListVo.class, response);
    }

    /**
     * 批量拉详情（含事件链），供前端 jsPDF 批量打印一次取数。
     */
    @SaCheckPermission("djs:warehouse:trace:print")
    @PostMapping("/batch")
    public R<List<TraceCodeDetailVo>> batch(@RequestBody List<Long> ids) {
        return R.ok(traceCodeAdminService.batchDetail(ids));
    }

}
