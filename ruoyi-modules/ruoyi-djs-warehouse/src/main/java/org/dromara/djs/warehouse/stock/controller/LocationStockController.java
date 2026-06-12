package org.dromara.djs.warehouse.stock.controller;

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
import org.dromara.djs.warehouse.stock.domain.bo.StockOutBo;
import org.dromara.djs.warehouse.stock.domain.query.LocationStockQuery;
import org.dromara.djs.warehouse.stock.domain.vo.LocationStockVo;
import org.dromara.djs.warehouse.stock.service.ILocationStockService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库存查询 Controller（WMS-MD-001）。
 *
 * <p>本 ticket 仅暴露 list / getInfo / export 3 端点（admin 只读列表）；
 * add / edit / remove 端点不暴露，库存写入由 WMS-DEMAND-001 / WMS-STOCK-001 D8-D11 后续 ticket
 * 通过出入库流水触发。权限串 {@code djs:warehouse:stock:{list,export}}。</p>
 *
 * @author djs
 * @since WMS-MD-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/warehouse/stock")
public class LocationStockController extends BaseController {

    private final ILocationStockService stockService;

    /**
     * 分页查询库存明细。
     */
    @SaCheckPermission("djs:warehouse:stock:list")
    @GetMapping("/list")
    public TableDataInfo<LocationStockVo> list(LocationStockQuery query, PageQuery pageQuery) {
        return stockService.queryPageList(query, pageQuery);
    }

    /**
     * 根据 ID 查询库存明细详情。
     */
    @SaCheckPermission("djs:warehouse:stock:list")
    @GetMapping("/getInfo/{id}")
    public R<LocationStockVo> getInfo(@PathVariable Long id) {
        return R.ok(stockService.queryById(id));
    }

    /**
     * 导出库存明细（不分页）。
     */
    @SaCheckPermission("djs:warehouse:stock:export")
    @Log(title = "库存查询", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(LocationStockQuery query, HttpServletResponse response) {
        List<LocationStockVo> list = stockService.queryList(query);
        ExcelUtil.exportExcel(list, "库存明细", LocationStockVo.class, response);
    }

    /**
     * 库存查询行「产品出库」（DJS-FIX-WMS-RALN-B）。
     *
     * <p>按库存行 ID 出库：同事务 INSERT 出库流水 + 原子扣减库存。</p>
     */
    @SaCheckPermission("djs:warehouse:stock:out")
    @Log(title = "库存查询", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/out")
    public R<Void> productOut(@Valid @RequestBody StockOutBo bo) {
        stockService.productOut(bo);
        return R.ok();
    }

}
