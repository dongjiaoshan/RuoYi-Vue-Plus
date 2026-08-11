package org.dromara.djs.store.demand.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.core.domain.vo.PigAvailableVo;
import org.dromara.djs.breed.core.service.IPigQueryService;
import org.dromara.djs.store.demand.domain.bo.StoreDemandBatchBo;
import org.dromara.djs.store.demand.service.IStoreDemandAppletService;
import org.dromara.djs.store.demand.service.IStoreDemandService;
import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.domain.bo.AssignPigBo;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.domain.query.DemandManageQuery;
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
 * 门店端需求 Controller（STR-DEMAND-001，admin PC 门店店长视角）。
 *
 * <p><b>薄封装</b>：列表 / 详情 / 编辑 / 删除 / 取消 / 指定猪只全部 delegate warehouse
 * {@link IDemandManageService} + {@link IDemandStatusService}；门店发起需求（创建即 SUBMITTED）
 * 走门店专属 {@link IStoreDemandService#createStoreDemand}。</p>
 *
 * <p>门店视角隔离 = 按 {@code query.storeId} 显式过滤（V1 不做行级数据权限拦截器，UI 下拉引导；
 * store_id 行级隔离留 V2 配合多租户拦截器一起做，详 _open-issues STR-DEMAND-001-B）。</p>
 *
 * <p>权限串：{@code djs:store:demand:{list,query,add,edit,remove,cancel,assign_pig}}
 * （seed 在 {@code V20260614xxxx__STR-DEMAND-001-menu.sql}，menu_id 10001-10019 门店域段）。</p>
 *
 * @author djs
 * @since STR-DEMAND-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/store/demand")
public class StoreDemandController extends BaseController {

    private final IStoreDemandService storeDemandService;

    /** 门店撤回复用 mp 侧的「仅待确认」闸（同一条业务规则不写两套）。 */
    private final IStoreDemandAppletService storeDemandAppletService;

    private final IDemandManageService demandManageService;

    private final IDemandStatusService demandStatusService;

    private final IPigQueryService pigQueryService;

    /**
     * 分页查询（门店视角：query.storeId 由 admin UI 门店下拉显式传）。
     *
     * <p>走门店专属 {@link IStoreDemandService#queryStoreList}：列表主体复用 warehouse
     * {@code queryPageList}，并对「已发货」行回填门店专属 {@code damagedCount} 损坏数量列（row48）。</p>
     */
    @SaCheckPermission("djs:store:demand:list")
    @GetMapping("/list")
    public TableDataInfo<DemandManageVo> list(DemandManageQuery query, PageQuery pageQuery) {
        return storeDemandService.queryStoreList(query, pageQuery);
    }

    /** 详情。 */
    @SaCheckPermission("djs:store:demand:query")
    @GetMapping("/getInfo/{id}")
    public R<DemandManageVo> getInfo(@PathVariable Long id) {
        return R.ok(demandManageService.queryById(id));
    }

    /** 门店发起需求（创建即 SUBMITTED，跳过 DRAFT）。 */
    @SaCheckPermission("djs:store:demand:add")
    @Log(title = "门店需求", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Long> add(@Validated @RequestBody DemandManageBo bo) {
        return R.ok(storeDemandService.createStoreDemand(bo));
    }

    /** 购物车整单发起需求（原型「新增需求」多产品整页，一次提交多条 → 逐条 SUBMITTED）。 */
    @SaCheckPermission("djs:store:demand:add")
    @Log(title = "门店需求整单", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/operation/batch")
    public R<Integer> batchCreate(@Validated @RequestBody StoreDemandBatchBo bo) {
        return R.ok(storeDemandService.batchCreate(bo));
    }

    /** 门店收货确认（原型列表「确认收货」，CONFIRMED 态 patch received_time / received_by）。 */
    @SaCheckPermission("djs:store:demand:receive")
    @Log(title = "门店需求确认收货", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{id}/receive")
    public R<Void> receive(@PathVariable Long id) {
        storeDemandService.receive(id);
        return R.ok();
    }

    /** 编辑（仅 DRAFT/SUBMITTED 态可改业务字段，规则由 warehouse service 兜底）。 */
    @SaCheckPermission("djs:store:demand:edit")
    @Log(title = "门店需求", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody DemandManageBo bo) {
        return toAjax(demandManageService.updateByBo(bo));
    }

    /** 批量删除（仅 DRAFT/SUBMITTED/CANCELLED 态可删，删除即置 DELETED 终态；规则由 warehouse service 兜底）。 */
    @SaCheckPermission("djs:store:demand:remove")
    @Log(title = "门店需求", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(demandManageService.deleteWithValidByIds(Arrays.asList(ids)));
    }

    /**
     * 取消：门店撤回<b>待确认</b>需求（SUBMITTED → CANCELLED）。
     *
     * <p>走 {@link IStoreDemandAppletService#cancelSubmitted} 而不是直接打状态机：门店只准动
     * 「待确认」的行（甲方 row70 第 4 条），而状态机本身允许 {@code CONFIRMED → CANCELLED}。</p>
     *
     * <p>这条权限串 {@code djs:store:demand:*} <b>门店店员本人就持有</b>，独立验收实测：
     * mp 端点对已确认行返 400 之后，同一个 token 打本端点仍 200 把它撤成 CANCELLED，
     * 日卡品数 -1、确认率归零。闸只装在 mp 那一侧等于没装。
     * 仓库侧 {@code /djs/warehouse/demand/{id}/cancel} 不受影响（仓库管理员撤单是正当能力）。</p>
     */
    @SaCheckPermission("djs:store:demand:cancel")
    @Log(title = "取消门店需求", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String remark) {
        storeDemandAppletService.cancelSubmitted(id, remark);
        return R.ok();
    }

    /** 白条业态：批量指定猪只。 */
    @SaCheckPermission("djs:store:demand:assign_pig")
    @Log(title = "门店需求指定猪只", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/pigs")
    public R<Integer> assignPigs(@PathVariable Long id, @Valid @RequestBody AssignPigBo bo) {
        return R.ok(demandManageService.assignPigs(id, bo));
    }

    /** 白条业态：查询已指定猪只列表。 */
    @SaCheckPermission("djs:store:demand:query")
    @GetMapping("/{id}/pigs")
    public R<List<DemandPigVo>> listAssignedPigs(@PathVariable Long id) {
        return R.ok(demandManageService.listAssignedPigs(id));
    }

    /** 白条业态：可出栏育肥猪分页（指定猪只对话框用，复用养殖域只读查询）。 */
    @SaCheckPermission("djs:store:demand:list")
    @GetMapping("/pigs/available")
    public TableDataInfo<PigAvailableVo> listAvailablePigs(PageQuery pageQuery) {
        return pigQueryService.listAvailableForOutbound(pageQuery);
    }
}
