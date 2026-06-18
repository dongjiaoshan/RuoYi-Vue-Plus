package org.dromara.djs.warehouse.cut.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutDoneBo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutOutBo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutPickupBo;
import org.dromara.djs.warehouse.cut.domain.query.PigCutRecordQuery;
import org.dromara.djs.warehouse.cut.domain.vo.BarInfoVo;
import org.dromara.djs.warehouse.cut.domain.vo.CutProductTypeVo;
import org.dromara.djs.warehouse.cut.domain.vo.PigCutRecordVo;
import org.dromara.djs.warehouse.cut.service.IPigCutRecordService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分割工序 mp 端 Controller（WMS-PIG-002）。
 *
 * <p>5 个端点：</p>
 * <ul>
 *   <li>{@code POST /applet/warehouse/pigCut/pickup}  阶段 1 白条领用</li>
 *   <li>{@code POST /applet/warehouse/pigCut/cutOut}  阶段 2 出库称重（多部位）</li>
 *   <li>{@code POST /applet/warehouse/pigCut/cutDone} 阶段 3 出库完成</li>
 *   <li>{@code GET  /applet/warehouse/pigCut/myList}   我的分割记录</li>
 *   <li>{@code GET  /applet/warehouse/pigCut/availableBars} 待领用白条列表（status='in_stock'）</li>
 * </ul>
 *
 * <p>{@code @SaCheckLogin} 走 sa-token 真路（mp V1 mock 登录已发真 token，参 ADR-0003）。</p>
 *
 * @author djs
 * @since WMS-PIG-002
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/warehouse/pigCut")
public class AppletPigCutController extends BaseController {

    private final IPigCutRecordService service;

    /**
     * 阶段 1：白条领用。
     */
    @SaCheckLogin
    @PostMapping("/pickup")
    public R<Long> pickup(@Valid @RequestBody PigCutPickupBo bo) {
        return R.ok(service.submitPickup(bo));
    }

    /**
     * 阶段 2：出库称重（多部位提交）。
     */
    @SaCheckLogin
    @PostMapping("/cutOut")
    public R<Void> cutOut(@Valid @RequestBody PigCutOutBo bo) {
        service.submitCutOut(bo);
        return R.ok();
    }

    /**
     * 阶段 3：出库完成。
     */
    @SaCheckLogin
    @PostMapping("/cutDone")
    public R<Void> cutDone(@Valid @RequestBody PigCutDoneBo bo) {
        service.submitCutDone(bo);
        return R.ok();
    }

    /**
     * 我的分割记录列表（按 operatorId 过滤）。
     */
    @SaCheckLogin
    @GetMapping("/myList")
    public TableDataInfo<PigCutRecordVo> myList(PigCutRecordQuery query, PageQuery pageQuery) {
        query.setOperatorId(LoginHelper.getUserId());
        return service.queryPageList(query, pageQuery);
    }

    /**
     * 待领用白条列表（status='in_stock'，最近 50 条，按入库时间倒序）。
     */
    @SaCheckLogin
    @GetMapping("/availableBars")
    public R<List<BarInfoVo>> availableBars() {
        return R.ok(service.queryAvailableBars());
    }

    /**
     * 分割车间产品类型列表（标准分割成品 5 类：精瘦肉 / 部位肉 / 骨类 / 猪皮 / 碎料）。
     *
     * <p>出库录入页打开时调用：动态加载 {@code product_workshop=2} 分割车间产品卡，
     * 替代写死的 5 部位枚举。提交时 partItems.productId 指向所选产品。</p>
     */
    @SaCheckLogin
    @GetMapping("/productTypes")
    public R<List<CutProductTypeVo>> productTypes() {
        return R.ok(service.queryCutProductTypes());
    }

}
