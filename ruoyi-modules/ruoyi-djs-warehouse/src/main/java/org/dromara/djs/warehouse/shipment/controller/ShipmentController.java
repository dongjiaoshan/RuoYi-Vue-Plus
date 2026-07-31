package org.dromara.djs.warehouse.shipment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.shipment.domain.query.ShipmentQuery;
import org.dromara.djs.warehouse.shipment.domain.vo.ShipmentVo;
import org.dromara.djs.warehouse.shipment.service.IShipmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * 发货流水 admin Controller（WMS-SHIP-001）。
 *
 * <p>admin 端只读 + 导出（不暴露 add/edit — 发货由 mp 工人在月台触达）。</p>
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/shipment")
public class ShipmentController extends BaseController {

    private final IShipmentService service;

    /** 列表（分页）。 */
    @SaCheckPermission("djs:warehouse:shipment:list")
    @GetMapping("/list")
    public TableDataInfo<ShipmentVo> list(ShipmentQuery query, PageQuery pageQuery) {
        return service.queryPageList(query, pageQuery);
    }

    /** 详情。 */
    @SaCheckPermission("djs:warehouse:shipment:query")
    @GetMapping("/{id}")
    public R<ShipmentVo> getInfo(@PathVariable Long id) {
        return R.ok(service.queryById(id));
    }

    /**
     * 导出 Excel。
     *
     * <p>必须是 {@code POST}：前端「发货流水」导出走 {@code utils/request.ts} 的 {@code download()}
     * 通用下载方法（{@code application/x-www-form-urlencoded} POST），挂 {@code @GetMapping}
     * 会直接报 {@code Request method 'POST' is not supported}（与退货记录 row156 同一处缺陷）。</p>
     */
    @SaCheckPermission("djs:warehouse:shipment:export")
    @Log(title = "发货流水", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(ShipmentQuery query, HttpServletResponse response) {
        List<ShipmentVo> list = service.queryList(query);
        ExcelUtil.exportExcel(list == null ? new ArrayList<>() : list,
            "发货流水", ShipmentVo.class, response);
    }
}
