package org.dromara.djs.store.check.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.store.check.domain.bo.StoreCheckCreateBo;
import org.dromara.djs.store.check.domain.bo.StoreCheckLineBo;
import org.dromara.djs.store.check.domain.query.StoreCheckQuery;
import org.dromara.djs.store.check.domain.vo.StoreCheckHeaderVo;
import org.dromara.djs.store.check.domain.vo.StoreCheckRecordVo;
import org.dromara.djs.store.check.service.IStoreCheckService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门店盘点 admin 端 Controller（STR-STOCK-001，admin only 无 mp）。
 *
 * <p>端点：盘点单列表（header 维度）/ 明细 line 列表 / 新建（锁门店）/ 完成（解锁）/ 取消 /
 * 录实盘明细 / 删明细 / 导出。权限串 {@code djs:store:check:{list,query,add,complete,cancel,export}}（menu 10200-10205）。</p>
 *
 * @author djs
 * @since STR-STOCK-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/store/check")
public class StoreCheckController extends BaseController {

    private final IStoreCheckService checkService;

    /**
     * 盘点单列表（盘点单维度，header 聚合 + 盈亏计）。
     */
    @SaCheckPermission("djs:store:check:list")
    @GetMapping("/list")
    public TableDataInfo<StoreCheckHeaderVo> list(StoreCheckQuery query, PageQuery pageQuery) {
        return checkService.queryHeaderPage(query, pageQuery);
    }

    /**
     * 盘点明细 line 列表（按 checkId / storeId 查某盘点单的逐项核对明细）。
     */
    @SaCheckPermission("djs:store:check:query")
    @GetMapping("/lines")
    public R<List<StoreCheckRecordVo>> lines(StoreCheckQuery query) {
        return R.ok(checkService.queryLineList(query));
    }

    /**
     * 新建盘点单（INSERT header status='in_progress' → 锁门店）。
     */
    @SaCheckPermission("djs:store:check:add")
    @Log(title = "门店盘点", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody StoreCheckCreateBo bo) {
        return R.ok(checkService.createCheck(bo));
    }

    /**
     * 录入 / 修改实盘明细 line（admin 详情页选产品 + 录实盘量）。
     */
    @SaCheckPermission("djs:store:check:add")
    @Log(title = "门店盘点", businessType = BusinessType.UPDATE)
    @PostMapping("/line")
    public R<Long> submitLine(@Valid @RequestBody StoreCheckLineBo bo) {
        return R.ok(checkService.submitLine(bo));
    }

    /**
     * 删除实盘明细 line（仅进行中盘点单可删）。
     */
    @SaCheckPermission("djs:store:check:add")
    @Log(title = "门店盘点", businessType = BusinessType.DELETE)
    @DeleteMapping("/line/{id}")
    public R<Void> removeLine(@PathVariable Long id) {
        checkService.removeLine(id);
        return R.ok();
    }

    /**
     * 完成盘点（落差异 + 解锁门店；门店 V1 不回写实物库存）。
     */
    @SaCheckPermission("djs:store:check:complete")
    @Log(title = "门店盘点", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{id}")
    public R<Void> complete(@PathVariable Long id) {
        checkService.completeCheck(id);
        return R.ok();
    }

    /**
     * 取消盘点（解锁门店，不回写库存）。
     */
    @SaCheckPermission("djs:store:check:cancel")
    @Log(title = "门店盘点", businessType = BusinessType.UPDATE)
    @PutMapping("/cancel/{id}")
    public R<Void> cancel(@PathVariable Long id) {
        checkService.cancelCheck(id);
        return R.ok();
    }

    /**
     * 导出盘点明细（不分页）。
     */
    @SaCheckPermission("djs:store:check:export")
    @Log(title = "门店盘点", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(StoreCheckQuery query, HttpServletResponse response) {
        List<StoreCheckRecordVo> list = checkService.queryLineList(query);
        ExcelUtil.exportExcel(list, "门店盘点明细", StoreCheckRecordVo.class, response);
    }

}
