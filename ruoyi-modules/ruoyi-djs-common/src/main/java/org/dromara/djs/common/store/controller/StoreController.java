package org.dromara.djs.common.store.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.common.store.domain.bo.StoreBo;
import org.dromara.djs.common.store.domain.query.StoreQuery;
import org.dromara.djs.common.store.domain.vo.StoreVo;
import org.dromara.djs.common.store.service.IStoreService;
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
 * 门店主数据 Controller（SYS-MD-002）。
 *
 * <p>结构与 {@code PersonController}（SYS-MD-001）严格一致，复用同一 CRUD 范例：</p>
 * <ul>
 *   <li>5 端点 list / getInfo / add / edit / remove；额外 export</li>
 *   <li>权限串 {@code djs:common:store:{list,add,edit,remove,export}}（菜单 seed 在
 *       {@code V202605211300__SYS-MD-002-menu.sql}）</li>
 *   <li>分页：list 端点同时绑定 {@link StoreQuery} + {@link PageQuery}</li>
 *   <li>删除：{@code DELETE /remove/{ids}} 支持批量（逗号分隔）</li>
 * </ul>
 *
 * @author djs
 * @since SYS-MD-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/common/store")
public class StoreController extends BaseController {

    private final IStoreService storeService;

    /**
     * 分页查询门店列表。
     */
    @SaCheckPermission("djs:common:store:list")
    @GetMapping("/list")
    public TableDataInfo<StoreVo> list(StoreQuery query, PageQuery pageQuery) {
        return storeService.queryPageList(query, pageQuery);
    }

    /**
     * 导出门店列表（不分页，应用相同筛选条件）。
     */
    @SaCheckPermission("djs:common:store:export")
    @Log(title = "门店管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(StoreQuery query, HttpServletResponse response) {
        List<StoreVo> list = storeService.queryList(query);
        ExcelUtil.exportExcel(list, "门店数据", StoreVo.class, response);
    }

    /**
     * 根据 ID 查询门店详情。
     */
    @SaCheckPermission("djs:common:store:list")
    @GetMapping("/getInfo/{id}")
    public R<StoreVo> getInfo(@PathVariable Long id) {
        return R.ok(storeService.queryById(id));
    }

    /**
     * 新增门店（storeCode 由后端生成）。
     */
    @SaCheckPermission("djs:common:store:add")
    @Log(title = "门店管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody StoreBo bo) {
        return toAjax(storeService.insertByBo(bo));
    }

    /**
     * 编辑门店。
     */
    @SaCheckPermission("djs:common:store:edit")
    @Log(title = "门店管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody StoreBo bo) {
        return toAjax(storeService.updateByBo(bo));
    }

    /**
     * 删除门店（支持批量，逗号分隔）。
     */
    @SaCheckPermission("djs:common:store:remove")
    @Log(title = "门店管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(storeService.deleteWithValidByIds(Arrays.asList(ids)));
    }

    /**
     * 设置店长（SYS-MD-FIX-002）。
     *
     * <p>编辑表单不允许直接改 {@code manager_user_id}（防越权）；必须走本端点。
     * 传 {@code userId=0} 或不传表示清空店长。</p>
     */
    @SaCheckPermission("djs:common:store:setManager")
    @Log(title = "门店管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{storeId}/manager")
    public R<Void> setManager(@PathVariable Long storeId, @RequestParam(required = false) Long userId) {
        // userId == 0 → 视为清空
        Long realUserId = (userId == null || userId == 0L) ? null : userId;
        return toAjax(storeService.setManager(storeId, realUserId));
    }

}
