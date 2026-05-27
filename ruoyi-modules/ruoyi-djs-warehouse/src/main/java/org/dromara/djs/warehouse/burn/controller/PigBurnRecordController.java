package org.dromara.djs.warehouse.burn.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.burn.domain.query.PigBurnRecordQuery;
import org.dromara.djs.warehouse.burn.domain.vo.PigBurnRecordVo;
import org.dromara.djs.warehouse.burn.service.IPigBurnRecordService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 燎毛工序记录 admin 端 Controller（WMS-PIG-001）。
 *
 * <p>admin 端只读：list / 详情 / export。写入由 mp 端 {@link AppletPigBurnRecordController}。</p>
 *
 * @author djs
 * @since WMS-PIG-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/pigBurn")
public class PigBurnRecordController extends BaseController {

    private final IPigBurnRecordService service;

    /**
     * 列表（admin，分页 + 按耳号/时间区间/状态筛选）。
     */
    @SaCheckPermission("djs:warehouse:pigBurn:list")
    @GetMapping("/list")
    public TableDataInfo<PigBurnRecordVo> list(PigBurnRecordQuery query, PageQuery pageQuery) {
        return service.queryPageList(query, pageQuery);
    }

    /**
     * 详情。
     */
    @SaCheckPermission("djs:warehouse:pigBurn:list")
    @GetMapping("/{id}")
    public R<PigBurnRecordVo> getInfo(@NotNull @PathVariable Long id) {
        return R.ok(service.queryById(id));
    }

    /**
     * 导出 Excel。
     */
    @SaCheckPermission("djs:warehouse:pigBurn:export")
    @PostMapping("/export")
    public void export(PigBurnRecordQuery query, HttpServletResponse response) {
        List<PigBurnRecordVo> list = service.queryList(query);
        ExcelUtil.exportExcel(list, "燎毛记录", PigBurnRecordVo.class, response);
    }

}
