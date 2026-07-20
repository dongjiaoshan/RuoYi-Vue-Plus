package org.dromara.djs.warehouse.loss.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.loss.domain.vo.LossFlowVo;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * 统一损耗流水查询 Controller（库存查询详情「损耗记录」tab，行57）。
 *
 * <p>权限复用 {@code djs:warehouse:stock:list}：本接口是库存查询行钻取详情的子查询，
 * 能看库存列表即能看该产品的损耗明细。</p>
 *
 * @author djs
 * @since WMS-LOSS-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/loss")
public class LossFlowController extends BaseController {

    private final ILossFlowService service;

    /**
     * 按产品取损耗明细（库存查询详情「损耗记录」tab）。
     *
     * @param productId 产品 ID（FK → product_info.id，必填）
     * @param dateFrom  起始日期 yyyy-MM-dd（含，可空）
     * @param dateTo    截止日期 yyyy-MM-dd（含，可空）
     */
    @SaCheckPermission("djs:warehouse:stock:list")
    @GetMapping("/byProduct")
    public R<List<LossFlowVo>> byProduct(
        @NotNull @RequestParam Long productId,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo) {
        return R.ok(service.listByProduct(productId, dateFrom, dateTo));
    }

}
