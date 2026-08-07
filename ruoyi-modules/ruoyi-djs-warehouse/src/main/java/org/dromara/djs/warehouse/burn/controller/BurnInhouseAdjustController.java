package org.dromara.djs.warehouse.burn.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.burn.domain.bo.BurnInhouseAdjustBo;
import org.dromara.djs.warehouse.burn.domain.query.BurnInhouseAdjustQuery;
import org.dromara.djs.warehouse.burn.domain.vo.BurnInhouseAdjustVo;
import org.dromara.djs.warehouse.burn.service.IBurnInhouseAdjustService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 燎毛间产品重量调整 Controller（V6-R43，admin 系统管理 → 数据管理）。
 *
 * @author djs
 * @since V6-R43
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/burnAdjust")
public class BurnInhouseAdjustController extends BaseController {

    private final IBurnInhouseAdjustService service;

    /**
     * 列表：燎毛间产品入库记录（按入库日期区间 / 产品名 / 是否调整筛选）。
     */
    @SaCheckPermission("djs:warehouse:burnAdjust:list")
    @GetMapping("/list")
    public TableDataInfo<BurnInhouseAdjustVo> list(BurnInhouseAdjustQuery query, PageQuery pageQuery) {
        return service.queryPageList(query, pageQuery);
    }

    /**
     * 调整某条记录的产品入库重量（同步库存 / 入库流水 / 燎毛记录）。
     */
    @SaCheckPermission("djs:warehouse:burnAdjust:edit")
    @Log(title = "燎毛间产品重量调整", businessType = BusinessType.UPDATE)
    @PostMapping("/adjust")
    public R<Void> adjust(@Valid @RequestBody BurnInhouseAdjustBo bo) {
        service.adjustWeight(bo);
        return R.ok();
    }

}
