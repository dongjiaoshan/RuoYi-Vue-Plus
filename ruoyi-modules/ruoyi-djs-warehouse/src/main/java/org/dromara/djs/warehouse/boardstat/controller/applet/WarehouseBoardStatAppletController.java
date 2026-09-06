package org.dromara.djs.warehouse.boardstat.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.warehouse.boardstat.domain.vo.WarehouseBoardStatVo;
import org.dromara.djs.warehouse.boardstat.service.IWarehouseBoardStatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序仓库管理「仓库统计」tab Controller（V6-R178）。
 *
 * <p>看板为只读聚合、无写操作，与兄弟端点
 * {@code /djs/applet/warehouse/dashboard/*} 一致仅挂 {@code @SaCheckLogin}：
 * 进得了「仓库管理」tab（tab 可见性由 {@code djs:mptab:warehouse:dashboard} gate）就看得到本 tab 的数。</p>
 *
 * @author djs
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/applet/warehouse/boardstat")
public class WarehouseBoardStatAppletController {

    private final IWarehouseBoardStatService boardStatService;

    /**
     * 月度品类统计：4 张品类卡 × 若干单位 × 入库量 / 生产量 / 原材料消耗量 + 环比。
     *
     * @param month 统计月份 yyyy-MM（可空，缺省当月）
     * @return 月度品类统计 VO
     */
    @SaCheckLogin
    @GetMapping("/category")
    public R<WarehouseBoardStatVo> category(
        @RequestParam(value = "month", required = false) String month) {
        return R.ok(boardStatService.getCategoryStat(month));
    }
}
