package org.dromara.djs.store.returns.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
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
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.store.returns.domain.bo.StoreReturnBatchBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnConfirmBo;
import org.dromara.djs.store.returns.domain.query.StoreReturnQuery;
import org.dromara.djs.store.returns.domain.vo.StoreReturnVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnPorkCandidateVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnStoreDailyVo;
import org.dromara.djs.store.returns.domain.vo.StoreReturnVegCandidateVo;
import org.dromara.djs.store.returns.service.IStoreReturnService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 门店退回管理 admin Controller（STR-RETURN-001，门店域薄实现，admin only 无 mp）。
 *
 * @author djs
 * @since STR-RETURN-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/store/return")
public class StoreReturnController extends BaseController {

    private final IStoreReturnService service;

    /** 列表（分页）。门店端 + 仓库端（退货记录下钻明细）共用 → OR 权限。 */
    @SaCheckPermission(value = {"djs:store:return:list", "djs:warehouse:return:list"}, mode = SaMode.OR)
    @GetMapping("/list")
    public TableDataInfo<StoreReturnVo> list(StoreReturnQuery query, PageQuery pageQuery) {
        return service.queryPageList(query, pageQuery);
    }

    /**
     * 仓库「退货记录」外层「门店 + 当日」汇总（仅 store_to_warehouse 方向）。
     * 仓库角色经 {@code djs:warehouse:return:list} 命中（与门店 list 权限 OR），无需补授门店权限。
     */
    @SaCheckPermission(value = {"djs:store:return:list", "djs:warehouse:return:list"}, mode = SaMode.OR)
    @GetMapping("/store-daily")
    public TableDataInfo<StoreReturnStoreDailyVo> storeDaily(StoreReturnQuery query, PageQuery pageQuery) {
        return service.queryStoreDailyPage(query, pageQuery);
    }

    /** 详情。 */
    @SaCheckPermission("djs:store:return:query")
    @GetMapping("/{id}")
    public R<StoreReturnVo> getInfo(@PathVariable Long id) {
        return R.ok(service.queryById(id));
    }

    /** 新增（returnNo 服务端生成）。 */
    @SaCheckPermission("djs:store:return:add")
    @Log(title = "门店退回管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Valid @RequestBody StoreReturnBo bo) {
        return R.ok(service.insertByBo(bo));
    }

    /** 修改（不允许改 returnNo）。 */
    @SaCheckPermission("djs:store:return:edit")
    @Log(title = "门店退回管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping
    public R<Void> edit(@Valid @RequestBody StoreReturnBo bo) {
        return toAjax(service.updateByBo(bo));
    }

    /** 退回操作批量录入（原型「退回操作」整页矩阵，建 pending 待仓库确认，不入库）。 */
    @SaCheckPermission("djs:store:return:add")
    @Log(title = "门店退回操作", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/operation/batch")
    public R<Integer> batchCreate(@Valid @RequestBody StoreReturnBatchBo bo) {
        return R.ok(service.batchCreate(bo));
    }

    /**
     * 退回操作「猪肉产品」tab 候选（{@code pork} + {@code white_bar} 两子类）：与另两个 tab 同口径，
     * 取门店当日盘点台账里「期初 + 入库 − 销售 − 赠送 &gt; 0」的产品（row205；不减损坏）。
     */
    @SaCheckPermission("djs:store:return:list")
    @GetMapping("/operation/pork-candidates")
    public R<List<StoreReturnPorkCandidateVo>> porkCandidates(Long storeId) {
        return R.ok(service.listPorkCandidates(storeId));
    }

    /**
     * 退回操作「果蔬产品」tab 候选：与另两个 tab 同口径，取门店当日盘点台账里
     * 「期初 + 入库 − 销售 − 赠送 &gt; 0」的果蔬业态产品（row205；不减损坏）。
     */
    @SaCheckPermission("djs:store:return:list")
    @GetMapping("/operation/veg-candidates")
    public R<List<StoreReturnVegCandidateVo>> vegCandidates(Long storeId) {
        return R.ok(service.listVegCandidates(storeId));
    }

    /**
     * 退回操作「其他产品」tab 候选（admin row202）：干货 / 鸡蛋 / 其他 三个业态。
     *
     * <p>取数与猪肉 / 果蔬 tab 同口径 —— 门店当日盘点台账「期初 + 入库 − 销售 − 赠送 &gt; 0」
     * （row205；不减损坏，损坏的货本身就要退回）。</p>
     */
    @SaCheckPermission("djs:store:return:list")
    @GetMapping("/operation/other-candidates")
    public R<List<StoreReturnVegCandidateVo>> otherCandidates(Long storeId) {
        return R.ok(service.listOtherCandidates(storeId));
    }

    /** 仓库确认实收（原型「退回记录」仓库确认入库，pending→received 联动外购入库）。 */
    @SaCheckPermission("djs:store:return:confirm")
    @Log(title = "门店退回确认入库", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/confirm")
    public R<Void> confirm(@Valid @RequestBody StoreReturnConfirmBo bo) {
        return toAjax(service.confirm(bo));
    }

    /** 软删（支持批量）。 */
    @SaCheckPermission("djs:store:return:remove")
    @Log(title = "门店退回管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(service.deleteByIds(Arrays.asList(ids)));
    }

    /** 导出（逐条明细）。 */
    @SaCheckPermission("djs:store:return:export")
    @Log(title = "门店退回管理", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public void export(StoreReturnQuery query, HttpServletResponse response) {
        List<StoreReturnVo> list = service.queryList(query);
        ExcelUtil.exportExcel(list == null ? new ArrayList<>() : list,
            "门店退回管理", StoreReturnVo.class, response);
    }

    /**
     * 仓库「退货记录」外层「门店 + 当日」汇总导出（与 {@code /store-daily} 列表同口径）。
     * 仓库角色经 {@code djs:warehouse:return:export} 命中（与门店 export 权限 OR），无需补授门店权限。
     *
     * <p>必须是 {@code POST}：前端统一走 {@code utils/request.ts} 的 {@code download()} 通用下载方法，
     * 它以 {@code application/x-www-form-urlencoded} POST 提交（djs 其余 export 端点全是 {@code @PostMapping}）。
     * 挂 {@code @GetMapping} 会让点导出直接报 {@code Request method 'POST' is not supported}。</p>
     */
    @SaCheckPermission(value = {"djs:store:return:export", "djs:warehouse:return:export"}, mode = SaMode.OR)
    @Log(title = "门店退回汇总", businessType = BusinessType.EXPORT)
    @PostMapping("/store-daily/export")
    public void storeDailyExport(StoreReturnQuery query, HttpServletResponse response) {
        List<StoreReturnStoreDailyVo> list = service.queryStoreDailyList(query);
        ExcelUtil.exportExcel(list == null ? new ArrayList<>() : list,
            "退回记录", StoreReturnStoreDailyVo.class, response);
    }
}
