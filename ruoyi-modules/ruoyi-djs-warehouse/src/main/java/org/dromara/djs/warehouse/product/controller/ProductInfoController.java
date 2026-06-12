package org.dromara.djs.warehouse.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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
import org.dromara.djs.warehouse.product.domain.bo.ProductStockInBo;
import org.dromara.djs.warehouse.product.domain.bo.ProductInfoBo;
import org.dromara.djs.warehouse.product.domain.query.ProductInfoQuery;
import org.dromara.djs.warehouse.product.domain.vo.ProductFlowRecordVo;
import org.dromara.djs.warehouse.product.domain.vo.ProductInfoVo;
import org.dromara.djs.warehouse.product.domain.vo.ProductProductionRecordVo;
import org.dromara.djs.warehouse.product.service.IProductInfoService;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.util.Date;
import java.util.List;

/**
 * 产品 / 商品 / 礼盒 Controller（WMS-MD-002）。
 *
 * <p>5 端点 list / getInfo / add / edit / remove + export；权限串
 * {@code djs:warehouse:product:{list,add,edit,remove,export}}（菜单 seed 在
 * {@code V202605311100__WMS-MD-002-product-master.sql}，menu_id 9030-9035）。</p>
 *
 * @author djs
 * @since WMS-MD-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/warehouse/product")
public class ProductInfoController extends BaseController {

    private final IProductInfoService productInfoService;

    /**
     * 分页查询产品列表（不带礼盒组件清单）。
     */
    @SaCheckPermission("djs:warehouse:product:list")
    @GetMapping("/list")
    public TableDataInfo<ProductInfoVo> list(ProductInfoQuery query, PageQuery pageQuery) {
        return productInfoService.queryPageList(query, pageQuery);
    }

    /**
     * 导出产品列表（不分页）。
     */
    @SaCheckPermission("djs:warehouse:product:export")
    @Log(title = "商品管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(ProductInfoQuery query, HttpServletResponse response) {
        List<ProductInfoVo> list = productInfoService.queryList(query);
        ExcelUtil.exportExcel(list, "商品数据", ProductInfoVo.class, response);
    }

    /**
     * 查询单条详情（productType=3 时附带 giftComponents）。
     */
    @SaCheckPermission("djs:warehouse:product:list")
    @GetMapping("/getInfo/{id}")
    public R<ProductInfoVo> getInfo(@PathVariable Long id) {
        return R.ok(productInfoService.queryById(id));
    }

    /**
     * 新增产品。
     */
    @SaCheckPermission("djs:warehouse:product:add")
    @Log(title = "商品管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated({ProductInfoBo.OnCreate.class, Default.class}) @RequestBody ProductInfoBo bo) {
        return toAjax(productInfoService.insertByBo(bo));
    }

    /**
     * 修改产品（productId / productType 不允许修改）。
     */
    @SaCheckPermission("djs:warehouse:product:edit")
    @Log(title = "商品管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody ProductInfoBo bo) {
        return toAjax(productInfoService.updateByBo(bo));
    }

    /**
     * 行内切换产品状态（仅改 {@code productStatus}；复用 {@code product:edit} 权限）。
     */
    @SaCheckPermission("djs:warehouse:product:edit")
    @Log(title = "商品管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody ProductInfoBo bo) {
        return toAjax(productInfoService.updateStatus(bo.getId(), bo.getProductStatus()));
    }

    /**
     * 删除产品（支持批量；若仍有库存或被原材料引用，service 抛业务异常）。
     */
    @SaCheckPermission("djs:warehouse:product:remove")
    @Log(title = "商品管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(productInfoService.deleteWithValidByIds(Arrays.asList(ids)));
    }

    /**
     * 产品详情「生产记录」子表（DJS-FIX-WMS-RALN-B）：按 productId 查生产 + 退货记录。
     *
     * @param productId   产品 ID
     * @param produceDate 生产日期（可空，{@code yyyy-MM-dd}）
     * @param produceType 生产类型 produce / return（可空）
     */
    @SaCheckPermission("djs:warehouse:product:list")
    @GetMapping("/production/{productId}")
    public R<List<ProductProductionRecordVo>> productionRecords(
        @PathVariable Long productId,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date produceDate,
        @RequestParam(required = false) String produceType) {
        return R.ok(productInfoService.queryProductionRecords(productId, produceDate, produceType));
    }

    /**
     * 商品详情「业务流水」子表（DJS-FIX-WMS-RALN-B）：按 productId 查 stock_flow（入库 / 领用出库 / 后台出库）。
     *
     * @param productId 产品 ID
     * @param bizDate   业务日期（可空，{@code yyyy-MM-dd}）
     */
    @SaCheckPermission("djs:warehouse:product:list")
    @GetMapping("/flow/{productId}")
    public R<List<ProductFlowRecordVo>> flowRecords(
        @PathVariable Long productId,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date bizDate) {
        return R.ok(productInfoService.queryFlowRecords(productId, bizDate));
    }

    /**
     * 商品配置「产品入库」（DJS-FIX-WMS-RALN-B）：录入 产品 / 库位 / 数量 → 写入库流水 + 增库存。
     */
    @SaCheckPermission("djs:warehouse:product:edit")
    @Log(title = "商品管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/inbound")
    public R<Long> inbound(@Valid @RequestBody ProductStockInBo bo) {
        return R.ok(productInfoService.inbound(bo));
    }

}
