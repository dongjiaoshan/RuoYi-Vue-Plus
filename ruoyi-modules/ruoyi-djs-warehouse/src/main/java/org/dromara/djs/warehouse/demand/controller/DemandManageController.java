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
import org.dromara.djs.breed.core.domain.vo.PigAvailableVo;
import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.domain.bo.AssignPigBo;
import org.dromara.djs.warehouse.demand.domain.bo.DemandBatchConfirmBo;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.domain.query.DemandManageQuery;
import org.dromara.djs.warehouse.demand.domain.vo.AuditHistoryEntryVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandBatchConfirmResultVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandGroupVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPigVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandProductStoreDetailVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandSummaryVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandTodayKpiVo;
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
 * <p>端点分组：CRUD 5 + 状态机 3（submit / confirm / cancel）+ 指定猪只 3 + 历史 1 + 导出 1
 * + 产品门店明细 1。无「开始排产」（CONFIRMED 即用户最终态，发货链路自动推进）。</p>
 *
 * <p>权限串：{@code djs:warehouse:demand:{list,query,add,edit,remove,confirm,cancel,assign_pig,history,export}}
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

    /**
     * 需求汇总分组列表（0613-10 需求管理列表重做）。
     *
     * <p>按「需求日期 + 需求产品」分组聚合：同日同产品 N 门店需求合并成一行（需求量 / 原材料计算量 /
     * 需求门店数 / 三态状态 / 确认率 / 最终确认时间）。点「查看需求」携 demandDate+productId 下钻确认页。</p>
     *
     * <p>权限：复用 {@code djs:warehouse:demand:list}（同入口同角色，不需新菜单）。</p>
     */
    @SaCheckPermission("djs:warehouse:demand:list")
    @GetMapping("/group-list")
    public TableDataInfo<DemandGroupVo> groupList(DemandManageQuery query, PageQuery pageQuery) {
        return demandService.queryGroupList(query, pageQuery);
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

    /** 批量删除（仅 DRAFT/SUBMITTED/CANCELLED 可删，删除即置 DELETED 终态；级联软删关联猪只）。 */
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

    /** 取消：DRAFT/SUBMITTED/CONFIRMED → CANCELLED。 */
    @SaCheckPermission("djs:warehouse:demand:cancel")
    @Log(title = "取消需求", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String remark) {
        statusService.transition(id, DemandEvent.CANCEL, LoginHelper.getUserId(), remark);
        return R.ok();
    }

    /**
     * 批量确认需求（row41）：确认选中「需求日期+产品」分组下所有 SUBMITTED 态需求单，
     * 逐条走确认状态机（逻辑与「查看需求」里的单条确认一致）。
     *
     * <p>逐条互不阻断：白条未指定猪只 / 状态不符等单条失败被捕获后计入结果，返回「成功 X 条 / 失败 Y 条」。</p>
     */
    @SaCheckPermission("djs:warehouse:demand:confirm")
    @Log(title = "批量确认需求", businessType = BusinessType.UPDATE)
    @PostMapping("/batch-confirm")
    public R<DemandBatchConfirmResultVo> batchConfirm(@Validated @RequestBody DemandBatchConfirmBo bo) {
        List<Long> ids = demandService.listSubmittedIdsByGroups(bo.getGroups());
        DemandBatchConfirmResultVo result = new DemandBatchConfirmResultVo();
        Long operator = LoginHelper.getUserId();
        for (Long id : ids) {
            try {
                statusService.transition(id, DemandEvent.CONFIRM, operator, bo.getRemark());
                result.setConfirmed(result.getConfirmed() + 1);
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
                result.getFailReasons().add("需求 " + id + "：" + e.getMessage());
            }
        }
        return R.ok(result);
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

    /**
     * 「可出栏」育肥猪分页列表（DJS-FIX-ADMIN-W22-001 / test row48）。
     *
     * <p>用于「指定猪只」对话框：列出 {@code pig_type='fattening'} AND {@code current_status != 'END'}
     * AND 未被其它非取消态需求占用、<b>且满足养殖出栏日龄配置</b>（{@code slaughter_age_days}）的到龄活体猪。
     * VO 含耳号 / 性别 / 品种品系 label / 日龄 / 最新背膘。日龄阈值过滤在需求域 service 内部按配置取，
     * 与 mp 出栏选猪口径一致（row48）。</p>
     *
     * <p>权限：复用 {@code djs:warehouse:demand:list}（同入口同角色）。</p>
     */
    @SaCheckPermission("djs:warehouse:demand:list")
    @GetMapping("/pigs/available")
    public TableDataInfo<PigAvailableVo> listAvailablePigs(PageQuery pageQuery) {
        return demandService.listAvailablePigsForOutbound(pageQuery);
    }

    // =============== 产品门店需求明细（D-FIX-24 决策 #8）===============

    /**
     * 某产品「按门店聚合需求量明细」（列表「详情」弹窗）。
     *
     * <p>返回有该产品需求的各门店及需求量合计 / 单数（非取消单），供列表「门店」列点详情
     * 展示「该产品被几门店需求 + 各门店需求量」。</p>
     *
     * <p>权限：复用 {@code djs:warehouse:demand:query}（同入口同角色）。</p>
     */
    @SaCheckPermission("djs:warehouse:demand:query")
    @GetMapping("/product-store-detail")
    public R<List<DemandProductStoreDetailVo>> getProductStoreDetail(@RequestParam Long productId) {
        return R.ok(demandService.listProductStoreDetail(productId));
    }

    // =============== 状态历史 ===============

    /** 解析 audit_history JSON 为 timeline 条目。 */
    @SaCheckPermission("djs:warehouse:demand:history")
    @GetMapping("/{id}/history")
    public R<List<AuditHistoryEntryVo>> getHistory(@PathVariable Long id) {
        return R.ok(demandService.getAuditHistory(id));
    }

    // =============== 业态摘要（DJS-FIX-ADMIN-W22-003）===============

    /**
     * 需求确认页 SummaryBar 摘要（4 业态 union DTO）。
     *
     * <p>白条：可出栏育肥猪头数；蔬菜：进行中计划地块/产量/最早采摘/库存；
     * 礼盒：成品库存；其他：原料库存。详见 {@link DemandSummaryVo}。</p>
     *
     * <p>权限：复用 {@code djs:warehouse:demand:list}（同入口同角色）。</p>
     */
    @SaCheckPermission("djs:warehouse:demand:list")
    @GetMapping("/summary")
    public R<DemandSummaryVo> getSummary(@RequestParam String productType) {
        return R.ok(demandService.getSummary(productType));
    }

    // =============== 今日 KPI 横条（DJS-FIX-ADMIN-W22-007）===============

    /**
     * 需求管理页顶部「今日全局」KPI 横条：一次返 6 个跨业态数字。
     *
     * <p>白条需求/已调配头数 + 果蔬需求/已调配品种数 + 其他需求/已调配条数。
     * 与 {@code /summary}（当前业态摘要）互不替代：KPI 横条渲染在 SummaryBar 上方。</p>
     *
     * <p>权限：复用 {@code djs:warehouse:demand:list}（同入口同角色）。</p>
     */
    @SaCheckPermission("djs:warehouse:demand:list")
    @GetMapping("/kpi/today")
    public R<DemandTodayKpiVo> getTodayKpi() {
        return R.ok(demandService.getTodayKpi());
    }
}
