package org.dromara.djs.plant.cropstat.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.cropstat.domain.vo.CropPlotStatVo;
import org.dromara.djs.plant.cropstat.service.ICropPlotStatService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作物在田地块统计 Controller。
 *
 * <p>供门店/仓库需求下单页果蔬产品列（剩余地块 / 预计产量 / 最早·最晚可采摘日期）读取。
 * 纯只读聚合、无入参，一次返全量作物（当前库量级 20 余行）。</p>
 *
 * @author djs
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/plant/crop-stat")
public class CropPlotStatController extends BaseController {

    private final ICropPlotStatService cropPlotStatService;

    /**
     * 按作物聚合的在田地块统计。
     */
    @SaCheckPermission("djs:plant:crop:list")
    @GetMapping("/plot")
    public R<List<CropPlotStatVo>> listPlotStat() {
        return R.ok(cropPlotStatService.listPlotStat());
    }
}
