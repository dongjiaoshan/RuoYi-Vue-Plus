package org.dromara.djs.breed.core.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.breed.core.domain.vo.InventoryBarnMatrixVo;
import org.dromara.djs.breed.core.domain.vo.InventoryBoarItemVo;
import org.dromara.djs.breed.core.domain.vo.InventoryDistItemVo;
import org.dromara.djs.breed.core.service.IInventoryAppletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库存看板聚合 - 小程序端（read-only 聚合查询，BRD-INVENTORY-001，原型 10）。
 *
 * <p>URL 前缀 {@code /applet/inventory/*}，与现有 applet 系列保持统一前缀风格。
 * 权限串复用 {@code djs:applet:pig:search}（mp role 默认含，与选猪 / barn-count 同源）。</p>
 *
 * <p>pigType 段：sow / fattening / piglet / reserve（后备，走 current_status='HB'）/ boar。</p>
 *
 * @author djs
 * @since BRD-INVENTORY-001
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/inventory")
public class InventoryAppletController {

    private final IInventoryAppletService inventoryAppletService;

    /** 栋舍 × 状态矩阵 */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/barn-matrix")
    public R<List<InventoryBarnMatrixVo>> barnMatrix(@RequestParam String pigType) {
        return R.ok(inventoryAppletService.barnMatrix(pigType));
    }

    /** 胎次分布（仅母猪） */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/parity-dist")
    public R<List<InventoryDistItemVo>> parityDist(@RequestParam String pigType) {
        return R.ok(inventoryAppletService.parityDist(pigType));
    }

    /** 日龄分布 */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/age-dist")
    public R<List<InventoryDistItemVo>> ageDist(@RequestParam String pigType) {
        return R.ok(inventoryAppletService.ageDist(pigType));
    }

    /** 公猪列表 */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:pig:search")
    @GetMapping("/boar-list")
    public R<List<InventoryBoarItemVo>> boarList() {
        return R.ok(inventoryAppletService.boarList());
    }
}
