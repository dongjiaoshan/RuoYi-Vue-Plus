package org.dromara.djs.plant.indicator.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.plant.indicator.domain.ProductionIndicator.Report;
import org.dromara.djs.plant.indicator.service.ProductionIndicatorService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 种植生产指标日、月、年查询。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/plant/production-indicators")
public class ProductionIndicatorController {

    private final ProductionIndicatorService service;

    /**
     * 查询指定闭区间的日报及所涉及月份、年份的已回刷指标。
     */
    @SaCheckPermission("djs:plant:dashboard:view")
    @GetMapping
    public R<Report> query(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(service.query(from, to));
    }
}
