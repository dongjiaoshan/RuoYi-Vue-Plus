package org.dromara.djs.warehouse.pigbuy.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.pigbuy.domain.bo.PigPurchaseBo;
import org.dromara.djs.warehouse.pigbuy.domain.query.PigPurchaseQuery;
import org.dromara.djs.warehouse.pigbuy.domain.vo.PigPurchaseVo;
import org.dromara.djs.warehouse.pigbuy.service.IPigPurchaseService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 外购猪只到货登记 mp 端 Controller（FIX-WMS-MP-PIGBUY-001，原型图 32「外购猪只」）。
 *
 * <p>mp 端暴露端点：</p>
 * <ul>
 *   <li>{@code POST /applet/warehouse/pigPurchase/submit} 外购到货登记</li>
 *   <li>{@code GET  /applet/warehouse/pigPurchase/myList} 我的到货记录（按 operator_id 过滤）</li>
 *   <li>{@code GET  /applet/warehouse/pigPurchase/pendingList} 外购待处理列表（status=pending，
 *       燎毛 / 分割另一来源；与自养出栏来源并列）</li>
 * </ul>
 *
 * <p>{@code @SaCheckLogin} 走 sa-token 真路（mp V1 mock 登录已发真 token，参 ADR-0003）。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-PIGBUY-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/warehouse/pigPurchase")
public class AppletPigPurchaseController extends BaseController {

    private final IPigPurchaseService service;

    /**
     * mp 提交外购猪只到货登记。
     */
    @SaCheckLogin
    @PostMapping("/submit")
    public R<Long> submit(@Valid @RequestBody PigPurchaseBo bo) {
        return R.ok(service.submitPurchase(bo));
    }

    /**
     * mp 我的到货记录列表（按 operatorId 自动过滤当前登录人）。
     */
    @SaCheckLogin
    @GetMapping("/myList")
    public TableDataInfo<PigPurchaseVo> myList(PigPurchaseQuery query, PageQuery pageQuery) {
        query.setOperatorId(LoginHelper.getUserId());
        return service.queryPageList(query, pageQuery);
    }

    /**
     * mp 外购待处理列表（purchase_status='pending'，作为燎毛 / 分割的另一来源）。
     */
    @SaCheckLogin
    @GetMapping("/pendingList")
    public R<List<PigPurchaseVo>> pendingList(PigPurchaseQuery query) {
        query.setPurchaseStatus("pending");
        return R.ok(service.queryList(query));
    }

}
