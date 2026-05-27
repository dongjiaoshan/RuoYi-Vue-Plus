package org.dromara.djs.warehouse.burn.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.burn.domain.bo.PigBurnRecordBo;
import org.dromara.djs.warehouse.burn.domain.query.PigBurnRecordQuery;
import org.dromara.djs.warehouse.burn.domain.vo.PigBurnRecordVo;
import org.dromara.djs.warehouse.burn.service.IPigBurnRecordService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 燎毛工序记录 mp 端 Controller（WMS-PIG-001）。
 *
 * <p>mp 端只暴露 2 个端点：</p>
 * <ul>
 *   <li>{@code POST /applet/warehouse/pigBurn/submit} 提交燎毛记录（同事务 4 步联动）</li>
 *   <li>{@code GET  /applet/warehouse/pigBurn/myList} 我的燎毛记录（按 operator_id 过滤）</li>
 * </ul>
 *
 * <p>{@code @SaCheckLogin} 走 sa-token 真路（mp V1 mock 登录已发真 token，参 ADR-0003）。</p>
 *
 * @author djs
 * @since WMS-PIG-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/warehouse/pigBurn")
public class AppletPigBurnRecordController extends BaseController {

    private final IPigBurnRecordService service;

    /**
     * mp 提交燎毛工序（同事务联动扣减白条库存 + 写出库流水）。
     */
    @SaCheckLogin
    @PostMapping("/submit")
    public R<Long> submit(@Valid @RequestBody PigBurnRecordBo bo) {
        return R.ok(service.submitBurnRecord(bo));
    }

    /**
     * mp 我的燎毛记录列表（按 operatorId 自动过滤当前登录人）。
     */
    @SaCheckLogin
    @GetMapping("/myList")
    public TableDataInfo<PigBurnRecordVo> myList(PigBurnRecordQuery query, PageQuery pageQuery) {
        query.setOperatorId(LoginHelper.getUserId());
        return service.queryPageList(query, pageQuery);
    }

}
