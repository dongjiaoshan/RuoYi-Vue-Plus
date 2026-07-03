package org.dromara.djs.warehouse.pack.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.pack.domain.bo.MarkDamageBo;
import org.dromara.djs.warehouse.pack.domain.query.ProductProductionQuery;
import org.dromara.djs.warehouse.pack.domain.query.WhiteBarShipmentQuery;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionGroupVo;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionVo;
import org.dromara.djs.warehouse.pack.domain.vo.WhiteBarShipmentVo;
import org.dromara.djs.warehouse.pack.service.IProductProductionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 发货产品生产记录 admin 端 Controller（WMS-PACK-001）。
 *
 * <p>admin 端只读：list / 详情 / export。写入由 mp 端
 * {@link org.dromara.djs.warehouse.pack.controller.applet.AppletPackController}。</p>
 *
 * @author djs
 * @since WMS-PACK-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/production")
public class ProductProductionController extends BaseController {

    private final IProductProductionService service;

    /**
     * 列表（产品维度聚合：生产日期 + 产品分组，概览主列表）。
     *
     * <p>主列表只展示概览 5 列（生产日期 / 产品名称 / 产品品类 / 生产量 / 件数）；逐件明细经
     * 「查看」下钻 {@link #items}。</p>
     */
    @SaCheckPermission("djs:warehouse:production:list")
    @GetMapping("/list")
    public TableDataInfo<ProductProductionGroupVo> list(ProductProductionQuery query, PageQuery pageQuery) {
        return service.queryGroupPageList(query, pageQuery);
    }

    /**
     * 产品列表下钻（按"生产日期 + 产品"锁定一个生产批次，分页列出逐件产品）。
     *
     * <p>主列表「查看」→ 子页「产品列表」：逐件展示 生产编号 / 产品序号 / 产品重量 / 所属门店 + 行「追溯码」。
     * query 必带 produceDate + productId；可选 productSort（序号）/ storeId（所属门店）筛选。</p>
     */
    @SaCheckPermission("djs:warehouse:production:list")
    @GetMapping("/items")
    public TableDataInfo<ProductProductionVo> items(ProductProductionQuery query, PageQuery pageQuery) {
        return service.queryItemPageList(query, pageQuery);
    }

    /**
     * 详情。
     */
    @SaCheckPermission("djs:warehouse:production:list")
    @GetMapping("/{id}")
    public R<ProductProductionVo> getInfo(@NotNull @PathVariable Long id) {
        return R.ok(service.queryById(id));
    }

    /**
     * 标损 / 修改损坏信息（DENGBO-DAMAGE-001，契约 b）。
     *
     * <p>把一条生产记录置 {@code is_damaged=1} + 写损坏凭证图 / 备注 / 标损时间。可重复提交修改。</p>
     */
    @SaCheckPermission("djs:warehouse:production:edit")
    @PostMapping("/mark-damage")
    public R<Void> markDamage(@Validated @RequestBody MarkDamageBo bo) {
        service.markDamage(bo);
        return R.ok();
    }

    /**
     * 导出 Excel。
     */
    @SaCheckPermission("djs:warehouse:production:export")
    @PostMapping("/export")
    public void export(ProductProductionQuery query, HttpServletResponse response) {
        List<ProductProductionVo> list = service.queryList(query);
        ExcelUtil.exportExcel(list, "发货产品生产记录", ProductProductionVo.class, response);
    }

    /**
     * 白条发货记录列表（WS12 row133，「产品生产记录」下「白条发货记录」页）。
     *
     * <p>统计每日白条到发货月台 / 仓库出库情况。数据源 = {@code product_production} 中
     * {@code belong_type='white_bar'} 的出库记录；搜索发货日期区间 / 猪只耳号 / 出库方式 / 出库去向。</p>
     */
    @SaCheckPermission("djs:warehouse:whiteBarShipment:list")
    @GetMapping("/whiteBarShipment/list")
    public TableDataInfo<WhiteBarShipmentVo> whiteBarShipmentList(WhiteBarShipmentQuery query, PageQuery pageQuery) {
        return service.queryWhiteBarShipmentList(query, pageQuery);
    }

    /**
     * 白条发货记录导出 Excel（WS12 row133）。
     */
    @SaCheckPermission("djs:warehouse:whiteBarShipment:export")
    @PostMapping("/whiteBarShipment/export")
    public void whiteBarShipmentExport(WhiteBarShipmentQuery query, HttpServletResponse response) {
        List<WhiteBarShipmentVo> list = service.queryWhiteBarShipmentList(query);
        ExcelUtil.exportExcel(list, "白条发货记录", WhiteBarShipmentVo.class, response);
    }

}
