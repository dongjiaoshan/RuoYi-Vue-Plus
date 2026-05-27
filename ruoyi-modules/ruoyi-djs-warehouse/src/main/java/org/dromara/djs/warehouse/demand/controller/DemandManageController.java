package org.dromara.djs.warehouse.demand.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.domain.bo.AssignPigBo;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.domain.query.DemandManageQuery;
import org.dromara.djs.warehouse.demand.domain.vo.AuditHistoryEntryVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPigVo;
import org.dromara.djs.warehouse.demand.service.IDemandManageService;
import org.dromara.djs.warehouse.demand.service.IDemandStatusService;
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
 * 需求管理 Controller（WMS-DEMAND-001）。
 *
 * <p>端点分组：CRUD 5 + 状态机 3（confirm / start-production / cancel）+ 指定猪只 3 + 历史 1 + 导出 1。</p>
 *
 * <p>权限串：{@code djs:warehouse:demand:{list,query,add,edit,remove,confirm,start_production,cancel,assign_pig,history,export}}
 * （seed 在 {@code V202606060920__WMS-DEMAND-001-menu-seed.sql}，menu_id 9040-9054）。</p>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/warehouse/demand")
public class DemandManageController extends BaseController {

    private final IDemandManageService demandService;

    private final IDemandStatusService statusService;

    // =============== CRUD ===============

    /** 分页查询。 */
    @SaCheckPermission("djs:warehouse:demand:list")
    @GetMapping("/list")
    public TableDataInfo<DemandManageVo> list(DemandManageQuery query, PageQuery pageQuery) {
        return demandService.queryPageList(query, pageQuery);
    }

    /** 详情。 */
    @SaCheckPermission("djs:warehouse:demand:query")
    @GetMapping("/getInfo/{id}")
    public R<DemandManageVo> getInfo(@PathVariable Long id) {
        return R.ok(demandService.queryById(id));
    }

    /** 新增（自动生成 demand_no；初始 status=DRAFT）。 */
    @SaCheckPermission("djs:warehouse:demand:add")
    @Log(title = "需求管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Long> add(@Validated @RequestBody DemandManageBo bo) {
        Long id = demandService.insertByBo(bo);
        return R.ok(id);
    }

    /** 修改（仅 DRAFT/SUBMITTED 可改全字段，其他态仅可改 remark）。 */
    @SaCheckPermission("djs:warehouse:demand:edit")
    @Log(title = "需求管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody DemandManageBo bo) {
        return toAjax(demandService.updateByBo(bo));
    }

    /** 批量删除（仅 DRAFT/CANCELLED 可删；级联软删关联猪只）。 */
    @SaCheckPermission("djs:warehouse:demand:remove")
    @Log(title = "需求管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(demandService.deleteWithValidByIds(Arrays.asList(ids)));
    }

    /** 导出。 */
    @SaCheckPermission("djs:warehouse:demand:export")
    @Log(title = "需求管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(DemandManageQuery query, HttpServletResponse response) {
        List<DemandManageVo> list = demandService.queryList(query);
        ExcelUtil.exportExcel(list, "需求管理", DemandManageVo.class, response);
    }

    // =============== 状态机操作 ===============

    /** 提交：DRAFT → SUBMITTED。 */
    @SaCheckPermission("djs:warehouse:demand:edit")
    @Log(title = "需求提交", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id, @RequestParam(required = false) String remark) {
        statusService.transition(id, DemandEvent.SUBMIT, LoginHelper.getUserId(), remark);
        return R.ok();
    }

    /** 确认：SUBMITTED → CONFIRMED；白条业态校验已指定猪只。 */
    @SaCheckPermission("djs:warehouse:demand:confirm")
    @Log(title = "需求确认", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id, @RequestParam(required = false) String remark) {
        statusService.transition(id, DemandEvent.CONFIRM, LoginHelper.getUserId(), remark);
        return R.ok();
    }

    /** 开始排产：CONFIRMED → IN_PRODUCTION。 */
    @SaCheckPermission("djs:warehouse:demand:start_production")
    @Log(title = "开始排产", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/start-production")
    public R<Void> startProduction(@PathVariable Long id, @RequestParam(required = false) String remark) {
        statusService.transition(id, DemandEvent.START_PRODUCTION, LoginHelper.getUserId(), remark);
        return R.ok();
    }

    /** 取消：DRAFT/SUBMITTED/CONFIRMED → CANCELLED。 */
    @SaCheckPermission("djs:warehouse:demand:cancel")
    @Log(title = "取消需求", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String remark) {
        statusService.transition(id, DemandEvent.CANCEL, LoginHelper.getUserId(), remark);
        return R.ok();
    }

    // =============== 指定猪只（白条业态）===============

    /** 批量指定猪只。 */
    @SaCheckPermission("djs:warehouse:demand:assign_pig")
    @Log(title = "指定猪只", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/pigs")
    public R<Integer> assignPigs(@PathVariable Long id, @Valid @RequestBody AssignPigBo bo) {
        return R.ok(demandService.assignPigs(id, bo));
    }

    /** 移除单头猪。 */
    @SaCheckPermission("djs:warehouse:demand:assign_pig")
    @Log(title = "移除猪只", businessType = BusinessType.UPDATE)
    @DeleteMapping("/{id}/pigs/{earNo}")
    public R<Integer> removeAssignedPig(@PathVariable Long id, @PathVariable String earNo) {
        return R.ok(demandService.removeAssignedPig(id, earNo));
    }

    /** 查询已指定猪只列表。 */
    @SaCheckPermission("djs:warehouse:demand:query")
    @GetMapping("/{id}/pigs")
    public R<List<DemandPigVo>> listAssignedPigs(@PathVariable Long id) {
        return R.ok(demandService.listAssignedPigs(id));
    }

    // =============== 状态历史 ===============

    /** 解析 audit_history JSON 为 timeline 条目。 */
    @SaCheckPermission("djs:warehouse:demand:history")
    @GetMapping("/{id}/history")
    public R<List<AuditHistoryEntryVo>> getHistory(@PathVariable Long id) {
        return R.ok(demandService.getAuditHistory(id));
    }
}
