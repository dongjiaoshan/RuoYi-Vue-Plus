package org.dromara.djs.store.ledger.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.store.ledger.domain.bo.StoreDailyLedgerBatchBo;
import org.dromara.djs.store.ledger.domain.query.StoreDailyLedgerQuery;
import org.dromara.djs.store.ledger.domain.vo.StoreDailyLedgerCandidateVo;
import org.dromara.djs.store.ledger.domain.vo.StoreDailyLedgerHeaderVo;
import org.dromara.djs.store.ledger.domain.vo.StoreDailyLedgerVo;
import org.dromara.djs.store.ledger.service.IStoreDailyLedgerService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 门店经营流水盘点台账 admin Controller（STORE-LEDGER-001，原型「门店管理>门店盘点」重建）。
 *
 * <p>权限串复用门店盘点 {@code djs:store:check:{list,query,add}}（与原盘点菜单一致，行为重建）。</p>
 *
 * @author djs
 * @since STORE-LEDGER-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/store/ledger")
public class StoreDailyLedgerController extends BaseController {

    private final IStoreDailyLedgerService service;

    /** 盘点单列表（按 门店 + 盘点日期 分组，分页）。 */
    @SaCheckPermission("djs:store:check:list")
    @GetMapping("/list")
    public TableDataInfo<StoreDailyLedgerHeaderVo> list(StoreDailyLedgerQuery query, PageQuery pageQuery) {
        return service.queryHeaderPage(query, pageQuery);
    }

    /** 新增当日盘点候选：门店关联产品全 SKU + 预填 saleQty/returnQty（inboundQty 默认 0 手填）。 */
    @SaCheckPermission("djs:store:check:add")
    @GetMapping("/candidates")
    public R<List<StoreDailyLedgerCandidateVo>> candidates(@RequestParam Long storeId,
                                                           @RequestParam(required = false) LocalDate ledgerDate) {
        return R.ok(service.listCandidates(storeId, ledgerDate));
    }

    /** 当日盘点整表批量提交（一次落某门店某日多产品行，service 算 closing_qty）。 */
    @SaCheckPermission("djs:store:check:add")
    @Log(title = "门店当日盘点", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/batch")
    public R<Integer> batchSave(@Valid @RequestBody StoreDailyLedgerBatchBo bo) {
        return R.ok(service.batchSave(bo));
    }

    /** 盘点详情（按 storeId + ledgerDate 取一组明细行，只读）。 */
    @SaCheckPermission("djs:store:check:query")
    @GetMapping("/detail")
    public R<List<StoreDailyLedgerVo>> detail(@RequestParam Long storeId, @RequestParam LocalDate ledgerDate) {
        return R.ok(service.queryDetail(storeId, ledgerDate));
    }

    /** 产品盘点历史（按 productId + 日期范围，给产品详情用，按日期倒序）。 */
    @SaCheckPermission("djs:store:check:query")
    @GetMapping("/history")
    public R<List<StoreDailyLedgerVo>> history(StoreDailyLedgerQuery query) {
        return R.ok(service.queryHistoryByProduct(query));
    }
}
