package org.dromara.djs.warehouse.demand.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.demand.domain.query.DemandPrintQuery;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintDetailVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintSheetVo;
import org.dromara.djs.warehouse.demand.service.IDemandPrintService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 需求单打印 Controller（V7-R8，admin 仓库 › 需求管理 › 需求单打印弹框）。
 *
 * <p>权限 {@code djs:warehouse:demand:print}：admin 业务模块是菜单级 RBAC（DJS-PERM-MENU-006），
 * 需求管理菜单 9040 的 perms 为通配 {@code djs:warehouse:demand:*}，持有需求管理菜单的角色自动具备，
 * 不另建 F 按钮行。</p>
 *
 * @author djs
 * @since V7-R8
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/warehouse/demand/print")
public class DemandPrintController extends BaseController {

    private final IDemandPrintService demandPrintService;

    /** 订货单列表：按（需求日期 × 门店）聚合，含需求品类数。 */
    @SaCheckPermission("djs:warehouse:demand:print")
    @GetMapping("/list")
    public TableDataInfo<DemandPrintSheetVo> list(DemandPrintQuery query, PageQuery pageQuery) {
        return demandPrintService.querySheetPage(query, pageQuery);
    }

    /** 某门店某日订货单打印明细。 */
    @SaCheckPermission("djs:warehouse:demand:print")
    @GetMapping("/detail")
    public R<DemandPrintDetailVo> detail(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate demandDate,
                                         @RequestParam Long storeId) {
        return R.ok(demandPrintService.getSheetDetail(demandDate, storeId));
    }
}
