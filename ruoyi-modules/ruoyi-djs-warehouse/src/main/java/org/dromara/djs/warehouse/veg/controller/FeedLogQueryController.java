package org.dromara.djs.warehouse.veg.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.veg.domain.vo.FeedLogVo;
import org.dromara.djs.warehouse.veg.service.IFeedLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * 饲料饲喂明细查询 Controller（库存查询详情「饲料饲喂记录」tab，行57）。
 *
 * <p>仅当库存行产品是【果蔬且原材料】时前端才显示该 tab 并调本接口。权限复用
 * {@code djs:warehouse:stock:list}：能看库存列表即能看该产品的饲喂明细。</p>
 *
 * @author djs
 * @since WMS-VEG-FEED-LOG-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/feedLog")
public class FeedLogQueryController extends BaseController {

    private final IFeedLogService service;

    /**
     * 按产品取饲喂明细（库存查询详情「饲料饲喂记录」tab）。
     *
     * @param productId 产品 ID（FK → product_info.id，必填）
     * @param dateFrom  起始日期 yyyy-MM-dd（含，可空）
     * @param dateTo    截止日期 yyyy-MM-dd（含，可空）
     */
    @SaCheckPermission("djs:warehouse:stock:list")
    @GetMapping("/byProduct")
    public R<List<FeedLogVo>> byProduct(
        @NotNull @RequestParam Long productId,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo) {
        return R.ok(service.listByProduct(productId, dateFrom, dateTo));
    }

}
