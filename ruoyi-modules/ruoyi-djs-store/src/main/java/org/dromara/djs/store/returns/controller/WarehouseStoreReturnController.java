package org.dromara.djs.store.returns.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.web.core.BaseController;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnConfirmBo;
import org.dromara.djs.store.returns.domain.bo.StoreReturnUnitBo;
import org.dromara.djs.store.returns.domain.bo.WarehouseReturnConfirmBo;
import org.dromara.djs.store.returns.domain.bo.WarehouseReturnExtraBo;
import org.dromara.djs.store.returns.domain.bo.WarehouseReturnUnitBo;
import org.dromara.djs.store.returns.domain.query.StoreReturnQuery;
import org.dromara.djs.store.returns.domain.vo.*;
import org.dromara.djs.store.returns.service.IStoreReturnService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 仓库跨店退回操作入口；复用门店退回服务，避免仓库请求被门店上下文限制。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/storeReturn")
public class WarehouseStoreReturnController extends BaseController {
    private final IStoreReturnService service;

    /** 跨门店/单位的日期汇总列表。 */
    @SaCheckPermission(value = {"djs:warehouse:storeReturn:list", "djs:warehouse:return:list"}, mode = SaMode.OR)
    @GetMapping("/store-daily")
    public TableDataInfo<StoreReturnStoreDailyVo> storeDaily(StoreReturnQuery query, PageQuery page) {
        return service.queryStoreDailyPage(query, page);
    }

    /** 单张退回单的逐产品明细。 */
    @SaCheckPermission(value = {"djs:warehouse:storeReturn:list", "djs:warehouse:storeReturn:query", "djs:warehouse:storeReturn:detail"}, mode = SaMode.OR)
    @GetMapping("/operation/items")
    public R<List<StoreReturnOpsItemVo>> items(StoreReturnQuery query) {
        return R.ok(service.listOperationItems(query));
    }

    /** 筛选门店/单位全量选项。 */
    @SaCheckPermission(value = {"djs:warehouse:storeReturn:list", "djs:warehouse:storeReturn:query"}, mode = SaMode.OR)
    @GetMapping("/operation/owner-options")
    public R<List<StoreReturnOwnerOptionVo>> ownerOptions() {
        return R.ok(service.listStoreDailyOwnerOptions());
    }

    /** 字典退回清单候选。 */
    @SaCheckPermission(value = {"djs:warehouse:storeReturn:add", "djs:warehouse:storeReturn:confirm"}, mode = SaMode.OR)
    @GetMapping("/operation/unit-candidates")
    public R<List<StoreReturnUnitCandidateVo>> unitCandidates() {
        return R.ok(service.listUnitCandidates());
    }

    /** 仓库补录按显式字典清单选品。 */
    @SaCheckPermission("djs:warehouse:storeReturn:confirm")
    @GetMapping("/operation/supplement-candidates")
    public R<List<StoreReturnUnitCandidateVo>> supplementCandidates() {
        return R.ok(service.listWarehouseSupplementCandidates());
    }

    /** 仓库按既有规则确认退回。 */
    @SaCheckPermission("djs:warehouse:storeReturn:confirm")
    @RepeatSubmit
    @PutMapping("/confirm")
    public R<Void> confirm(@Valid @RequestBody StoreReturnConfirmBo body) {
        return toAjax(service.confirm(body));
    }

    /** 仓库新增单位退回。 */
    @SaCheckPermission("djs:warehouse:storeReturn:add")
    @RepeatSubmit
    @PostMapping("/unit")
    public R<Integer> unit(@Valid @RequestBody StoreReturnUnitBo body) {
        return R.ok(service.createUnitReturns(body));
    }

    /** 汇总导出与列表使用同一筛选。 */
    @SaCheckPermission(value = {"djs:warehouse:storeReturn:export", "djs:warehouse:return:export"}, mode = SaMode.OR)
    @PostMapping("/store-daily/export")
    public void export(StoreReturnQuery query, HttpServletResponse response) {
        ExcelUtil.exportExcel(service.queryStoreDailyList(query), "退回记录", StoreReturnStoreDailyVo.class, response);
    }

    /** 逐产品明细导出。 */
    @SaCheckPermission(value = {"djs:warehouse:storeReturn:export", "djs:warehouse:return:export"}, mode = SaMode.OR)
    @PostMapping("/detail/export")
    public void detailExport(StoreReturnQuery query, HttpServletResponse response) {
        ExcelUtil.exportExcel(service.queryDetailExportList(query), "退回明细", StoreReturnDetailExportVo.class, response);
    }

    /** 新版仓库处理：确认量入库，丢弃量仅记录。 */
    @SaCheckPermission("djs:warehouse:storeReturn:confirm")
    @RepeatSubmit
    @PutMapping("/operation/confirm")
    public R<Void> confirmOperation(@Valid @RequestBody WarehouseReturnConfirmBo body) {
        return toAjax(service.confirmOperation(body));
    }

    /** 新版单位退回的原材料候选。 */
    @SaCheckPermission("djs:warehouse:storeReturn:add")
    @GetMapping("/operation/raw-candidates")
    public R<List<StoreReturnUnitCandidateVo>> rawCandidates() {
        return R.ok(service.listOperationRawCandidates());
    }

    /** 在原待处理单中新增清单产品。 */
    @SaCheckPermission("djs:warehouse:storeReturn:confirm")
    @RepeatSubmit
    @PostMapping("/operation/products")
    public R<Integer> extraProducts(@Valid @RequestBody WarehouseReturnExtraBo body) {
        return R.ok(service.addOperationProducts(body));
    }

    /** 新版原材料单位退回。 */
    @SaCheckPermission("djs:warehouse:storeReturn:add")
    @RepeatSubmit
    @PostMapping("/operation/unit")
    public R<Integer> operationUnit(@Valid @RequestBody WarehouseReturnUnitBo body) {
        return R.ok(service.createOperationUnitReturns(body));
    }
}
