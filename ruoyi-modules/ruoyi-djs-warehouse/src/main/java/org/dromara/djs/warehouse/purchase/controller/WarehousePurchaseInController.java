package org.dromara.djs.warehouse.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.purchase.domain.bo.PurchaseInBo;
import org.dromara.djs.warehouse.purchase.domain.query.PurchaseInProductQuery;
import org.dromara.djs.warehouse.purchase.domain.query.PurchaseInQuery;
import org.dromara.djs.warehouse.purchase.domain.vo.PurchaseInProductVo;
import org.dromara.djs.warehouse.purchase.domain.vo.PurchaseInRecordVo;
import org.dromara.djs.warehouse.purchase.service.IWarehousePurchaseInService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采购入库简化版 Controller（D9 hotfix admin 端）。
 *
 * <p>本 hotfix 解决 D9 testing-human §5 暴露的"包材领用前置库存缺失"——给 admin 一个最简入库通道。
 * V1 真采购模块（含供应商 / 单价 / 验收）延后 D11+ WMS。</p>
 *
 * @author djs
 * @since D9 hotfix purchase-in
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/purchaseIn")
public class WarehousePurchaseInController extends BaseController {

    private final IWarehousePurchaseInService service;

    /**
     * 提交一笔采购入库（UPSERT location_stock + INSERT stock_flow 同事务）。
     */
    @SaCheckPermission("djs:warehouse:purchaseIn:add")
    @PostMapping("/submit")
    public R<Long> submit(@Valid @RequestBody PurchaseInBo bo) {
        return R.ok(service.submit(bo));
    }

    /**
     * 列表分页查询。
     */
    @SaCheckPermission("djs:warehouse:purchaseIn:list")
    @GetMapping("/list")
    public TableDataInfo<PurchaseInRecordVo> list(PurchaseInQuery query, PageQuery pageQuery) {
        return service.queryPageList(query, pageQuery);
    }

    /**
     * 采购入库「商品维度」分页列表（WMS-OUTSOURCE-001 需求1）。
     *
     * <p>以外购商品（{@code product_type=2}）为粒度，聚合当前库存 / 最后入库时间 / 采购人 /
     * 当月累计采购量。区别于 {@link #list}（流水维度，保留不动）。</p>
     */
    @SaCheckPermission("djs:warehouse:purchaseIn:list")
    @GetMapping("/productList")
    public TableDataInfo<PurchaseInProductVo> productList(PurchaseInProductQuery query, PageQuery pageQuery) {
        return service.queryProductPageList(query, pageQuery);
    }

    /**
     * 导出。
     */
    @SaCheckPermission("djs:warehouse:purchaseIn:export")
    @PostMapping("/export")
    public void export(PurchaseInQuery query, HttpServletResponse response) {
        List<PurchaseInRecordVo> list = service.queryList(query);
        ExcelUtil.exportExcel(list, "采购入库", PurchaseInRecordVo.class, response);
    }

}
