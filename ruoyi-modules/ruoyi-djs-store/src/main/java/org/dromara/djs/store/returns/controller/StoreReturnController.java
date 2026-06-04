package org.dromara.djs.store.returns.controller;

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
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.store.returns.domain.bo.StoreReturnBo;
import org.dromara.djs.store.returns.domain.query.StoreReturnQuery;
import org.dromara.djs.store.returns.domain.vo.StoreReturnVo;
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

    /** 列表（分页）。 */
    @SaCheckPermission("djs:store:return:list")
    @GetMapping("/list")
    public TableDataInfo<StoreReturnVo> list(StoreReturnQuery query, PageQuery pageQuery) {
        return service.queryPageList(query, pageQuery);
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

    /** 软删（支持批量）。 */
    @SaCheckPermission("djs:store:return:remove")
    @Log(title = "门店退回管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(service.deleteByIds(Arrays.asList(ids)));
    }

    /** 导出。 */
    @SaCheckPermission("djs:store:return:export")
    @Log(title = "门店退回管理", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public void export(StoreReturnQuery query, HttpServletResponse response) {
        List<StoreReturnVo> list = service.queryList(query);
        ExcelUtil.exportExcel(list == null ? new ArrayList<>() : list,
            "门店退回管理", StoreReturnVo.class, response);
    }
}
