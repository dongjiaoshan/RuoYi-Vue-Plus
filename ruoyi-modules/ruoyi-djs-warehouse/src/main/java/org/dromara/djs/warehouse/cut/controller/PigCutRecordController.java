package org.dromara.djs.warehouse.cut.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.cut.domain.query.PigCutRecordQuery;
import org.dromara.djs.warehouse.cut.domain.vo.PigCutRecordVo;
import org.dromara.djs.warehouse.cut.service.IPigCutRecordService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分割工序记录 admin 端 Controller（WMS-PIG-002）。
 *
 * <p>admin 端只读：list / 详情 / export。写入由 mp 端 {@link AppletPigCutController}。</p>
 *
 * @author djs
 * @since WMS-PIG-002
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/pigCut")
public class PigCutRecordController extends BaseController {

    private final IPigCutRecordService service;

    /**
     * 列表（admin，分页 + 按耳号/单号/时间区间/状态筛选）。
     */
    @SaCheckPermission("djs:warehouse:pigCut:list")
    @GetMapping("/list")
    public TableDataInfo<PigCutRecordVo> list(PigCutRecordQuery query, PageQuery pageQuery) {
        return service.queryPageList(query, pageQuery);
    }

    /**
     * 详情。
     */
    @SaCheckPermission("djs:warehouse:pigCut:list")
    @GetMapping("/{id}")
    public R<PigCutRecordVo> getInfo(@NotNull @PathVariable Long id) {
        return R.ok(service.queryById(id));
    }

    /**
     * 导出 Excel。
     */
    @SaCheckPermission("djs:warehouse:pigCut:export")
    @PostMapping("/export")
    public void export(PigCutRecordQuery query, HttpServletResponse response) {
        List<PigCutRecordVo> list = service.queryList(query);
        ExcelUtil.exportExcel(list, "分割记录", PigCutRecordVo.class, response);
    }

}
