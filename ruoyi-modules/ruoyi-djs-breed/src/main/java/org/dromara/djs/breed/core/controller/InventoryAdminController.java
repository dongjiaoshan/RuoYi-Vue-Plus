package org.dromara.djs.breed.core.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.core.domain.vo.InventoryBarnMatrixVo;
import org.dromara.djs.breed.core.domain.vo.InventoryDistItemVo;
import org.dromara.djs.breed.core.service.IInventoryAppletService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库存看板聚合 - admin 端（运营管理 → 农场信息 → 育肥猪信息，V6-R150）。
 *
 * <p>read-only 聚合查询，<b>不新增任何聚合逻辑</b>：直接委托 {@link IInventoryAppletService}，
 * 与小程序「猪只库存信息」页共用同一套日龄分桶 + 栋舍矩阵口径
 * （甲方要求「和小程序里的展示保持一致」）。改动该 service 会同时影响 mp 与 admin 两端。</p>
 *
 * <p>本页只用 {@code fattening}（育肥猪）/ {@code piglet}（仔猪）两段；
 * service 支持的 sow / reserve / boar 段本控制器不暴露。</p>
 *
 * @author djs
 * @since V6-R150
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/inventory")
public class InventoryAdminController extends BaseController {

    private final IInventoryAppletService inventoryAppletService;

    /**
     * 日龄分布（柱状图数据源）。段序 = 后端分桶序，含 count=0 的空段，前端自行决定是否过滤。
     */
    @SaCheckPermission("djs:ops:fattenInfo:query")
    @GetMapping("/age-dist")
    public R<List<InventoryDistItemVo>> ageDist(@RequestParam String pigType) {
        return R.ok(inventoryAppletService.ageDist(pigType));
    }

    /**
     * 栋舍 × 日龄段矩阵（列表数据源）。byAge 的 key 与 age-dist 的 label 一一对应且同序。
     */
    @SaCheckPermission("djs:ops:fattenInfo:query")
    @GetMapping("/barn-matrix")
    public R<List<InventoryBarnMatrixVo>> barnMatrix(@RequestParam String pigType) {
        return R.ok(inventoryAppletService.barnMatrix(pigType));
    }
}
