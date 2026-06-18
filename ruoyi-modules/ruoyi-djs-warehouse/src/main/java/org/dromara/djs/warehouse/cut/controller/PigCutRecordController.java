package org.dromara.djs.warehouse.cut.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutDoneBo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutOutBo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutPickupBo;
import org.dromara.djs.warehouse.cut.domain.query.PigCutRecordQuery;
import org.dromara.djs.warehouse.cut.domain.vo.PigCutRecordVo;
import org.dromara.djs.warehouse.cut.service.IPigCutRecordService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分割工序记录 admin 端 Controller（WMS-PIG-002）。
 *
 * <p>list / 详情 / export 走 admin 读权限；3 个写动作（白条领用 / 分割出库 / 分割完成）
 * 复用 applet 写权限码（{@code djs:applet:warehouse:pigCut:in/out/done}），底层调用
 * 与 mp 端 {@link AppletPigCutController} 同一组 service 方法，operatorId 由
 * {@code LoginHelper} 自动取 admin 登录人。</p>
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

    /**
     * 阶段 1：白条领用（admin 收银台代录）。
     *
     * @return 新建 cut_record 主键 id
     */
    @SaCheckPermission("djs:applet:warehouse:pigCut:in")
    @Log(title = "白条领用", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/pickup")
    public R<Long> pickup(@Valid @RequestBody PigCutPickupBo bo) {
        return R.ok(service.submitPickup(bo));
    }

    /**
     * 阶段 2：分割出库称重（admin 代录，多部位一次提交）。
     */
    @SaCheckPermission("djs:applet:warehouse:pigCut:out")
    @Log(title = "分割出库", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/cutOut")
    public R<Void> cutOut(@Valid @RequestBody PigCutOutBo bo) {
        service.submitCutOut(bo);
        return R.ok();
    }

    /**
     * 阶段 3：分割完成（admin 代录）。
     */
    @SaCheckPermission("djs:applet:warehouse:pigCut:done")
    @Log(title = "分割完成", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/cutDone")
    public R<Void> cutDone(@Valid @RequestBody PigCutDoneBo bo) {
        service.submitCutDone(bo);
        return R.ok();
    }

}
