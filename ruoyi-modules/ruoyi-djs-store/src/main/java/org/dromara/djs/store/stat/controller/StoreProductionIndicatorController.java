package org.dromara.djs.store.stat.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.store.stat.domain.StoreIndicatorPeriod;
import org.dromara.djs.store.stat.domain.StoreProductionIndicatorVo;
import org.dromara.djs.store.stat.service.StoreProductionIndicatorService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/store/production-indicators")
public class StoreProductionIndicatorController {

    private final StoreProductionIndicatorService service;

    @SaCheckPermission("djs:store:dashboard:view")
    @GetMapping
    public R<List<StoreProductionIndicatorVo>> list(
        @RequestParam(required = false) Long storeId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "DAY") StoreIndicatorPeriod period,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate) {
        return R.ok(service.query(storeId, from, to, period, reportDate));
    }
}
