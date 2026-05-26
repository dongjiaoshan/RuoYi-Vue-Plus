package org.dromara.djs.warehouse.location.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.location.domain.bo.LocationInfoBo;
import org.dromara.djs.warehouse.location.domain.query.LocationInfoQuery;
import org.dromara.djs.warehouse.location.domain.vo.LocationInfoVo;
import org.dromara.djs.warehouse.location.service.ILocationInfoService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 库位 Controller（WMS-MD-001）。
 *
 * <p>5 端点 list / getInfo / add / edit / remove + export；
 * 权限串 {@code djs:warehouse:location:{list,add,edit,remove,export}}（菜单 seed 在
 * {@code V202605290900__WMS-MD-001-warehouse-master.sql}，menu_id 9010-9015）。</p>
 *
 * @author djs
 * @since WMS-MD-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/warehouse/location")
public class LocationInfoController extends BaseController {

    private final ILocationInfoService locationInfoService;

    /**
     * 分页查询库位列表。
     */
    @SaCheckPermission("djs:warehouse:location:list")
    @GetMapping("/list")
    public TableDataInfo<LocationInfoVo> list(LocationInfoQuery query, PageQuery pageQuery) {
        return locationInfoService.queryPageList(query, pageQuery);
    }

    /**
     * 导出库位列表（不分页）。
     */
    @SaCheckPermission("djs:warehouse:location:export")
    @Log(title = "库位管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(LocationInfoQuery query, HttpServletResponse response) {
        List<LocationInfoVo> list = locationInfoService.queryList(query);
        ExcelUtil.exportExcel(list, "库位数据", LocationInfoVo.class, response);
    }

    /**
     * 根据 ID 查询库位详情。
     */
    @SaCheckPermission("djs:warehouse:location:list")
    @GetMapping("/getInfo/{id}")
    public R<LocationInfoVo> getInfo(@PathVariable Long id) {
        return R.ok(locationInfoService.queryById(id));
    }

    /**
     * 新增库位。
     */
    @SaCheckPermission("djs:warehouse:location:add")
    @Log(title = "库位管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated({LocationInfoBo.OnCreate.class, Default.class}) @RequestBody LocationInfoBo bo) {
        return toAjax(locationInfoService.insertByBo(bo));
    }

    /**
     * 编辑库位（{@code locationCode} 不允许修改）。
     */
    @SaCheckPermission("djs:warehouse:location:edit")
    @Log(title = "库位管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody LocationInfoBo bo) {
        return toAjax(locationInfoService.updateByBo(bo));
    }

    /**
     * 删除库位（支持批量；删除前若有 product_stock {@code > 0} 的库存，service 抛 location.has_stock）。
     */
    @SaCheckPermission("djs:warehouse:location:remove")
    @Log(title = "库位管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(locationInfoService.deleteWithValidByIds(Arrays.asList(ids)));
    }

}
