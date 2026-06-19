package org.dromara.djs.warehouse.burn.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.burn.domain.bo.PigBurnFinishBo;
import org.dromara.djs.warehouse.burn.domain.bo.PigBurnRecordBo;
import org.dromara.djs.warehouse.burn.domain.bo.PigBurnWeighBo;
import org.dromara.djs.warehouse.burn.domain.query.PigBurnRecordQuery;
import org.dromara.djs.warehouse.burn.domain.vo.BarPendingVo;
import org.dromara.djs.warehouse.burn.domain.vo.BurnProductTypeVo;
import org.dromara.djs.warehouse.burn.domain.vo.PigBurnRecordVo;
import org.dromara.djs.warehouse.burn.service.IPigBurnRecordService;
import org.dromara.djs.warehouse.location.domain.vo.LocationPickerVo;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 燎毛入库 mp 端 Controller（D12X-MP-BURN-IA-001：燎毛间 IA 重做）。
 *
 * <p>mp 端暴露端点（IA = list→detail + 按产品类型分类入库）：</p>
 * <ul>
 *   <li>{@code GET  /applet/warehouse/pigBurn/pendingList} 已出栏待燎毛入库白条列表（进页）</li>
 *   <li>{@code GET  /applet/warehouse/pigBurn/productTypes} 入库产品类型列表（整只/半只/猪头/猪蹄）</li>
 *   <li>{@code POST /applet/warehouse/pigBurn/submit} 提交燎毛入库（同事务按类型分别入库）</li>
 *   <li>{@code GET  /applet/warehouse/pigBurn/myList} 我的燎毛记录（按 operator_id 过滤）</li>
 * </ul>
 *
 * <p>{@code @SaCheckLogin} 走 sa-token 真路（mp V1 mock 登录已发真 token，参 ADR-0003）。</p>
 *
 * @author djs
 * @since D12X-MP-BURN-IA-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/warehouse/pigBurn")
public class AppletPigBurnRecordController extends BaseController {

    private final IPigBurnRecordService service;

    /**
     * mp 已出栏待燎毛入库白条列表（bar_info status IN pending_singe,singing，按出栏时间倒序）。
     */
    @SaCheckLogin
    @GetMapping("/pendingList")
    public R<List<BarPendingVo>> pendingList() {
        return R.ok(service.queryPendingBars());
    }

    /**
     * mp 入库产品类型列表（白条标准 4 类型：整只 / 半只 / 猪头 / 猪蹄）。
     */
    @SaCheckLogin
    @GetMapping("/productTypes")
    public R<List<BurnProductTypeVo>> productTypes() {
        return R.ok(service.queryProductTypes());
    }

    /**
     * mp 按产品取已配置的入库库位列表（MP-PRODIN 决策 #2）。
     *
     * <p>产品入库弹层打开时调用：返回该产品 {@code store_location_id} 配置的入库库位（启用态，不限库位类型）。
     * 单库位 → 前端只读预填；多库位 → 可选随机默认其一；空 → 前端回落自由选库位。</p>
     */
    @SaCheckLogin
    @GetMapping("/product/{productId}/inboundLocations")
    public R<List<LocationPickerVo>> productInboundLocations(@PathVariable Long productId) {
        return R.ok(service.queryProductInboundLocations(productId));
    }

    /**
     * mp 燎毛「称重」落库（pending_singe → singing 推进 + 回填 arrive_weight）。
     *
     * <p>解决现状「mp 称重只做本地态不落库」：校验 bar 在待燎毛/燎毛中态 + 到场重 ≤ 出栏重 →
     * 推 bar 到 {@code singing}（燎毛中），回填 {@code arrive_weight}。</p>
     */
    @SaCheckLogin
    @PostMapping("/weigh")
    public R<Void> weigh(@Validated @RequestBody PigBurnWeighBo bo) {
        return service.weighBurn(bo) ? R.ok() : R.fail("白条状态不符，无法称重");
    }

    /**
     * mp 提交燎毛入库（同事务按产品类型分别入库 + 推进 bar 到 in_stock + 写入库流水）。
     */
    @SaCheckLogin
    @PostMapping("/submit")
    public R<Long> submit(@Valid @RequestBody PigBurnRecordBo bo) {
        return R.ok(service.submitBurnRecord(bo));
    }

    /**
     * mp 燎毛「处理完成」（FIX-WMS-MP-BURN-001 客户 6/11 新需求）。
     *
     * <p>校验 bar 在燎毛中态 + 累计已入库产品总重 ≤ 出栏重量 + 整只/半只互斥 → 推 bar 到
     * {@code singed}（燎毛处理完成终态），从待燎毛列表移除。处理完成人取当前登录人。</p>
     */
    @SaCheckLogin
    @PostMapping("/finish")
    public R<Void> finish(@Valid @RequestBody PigBurnFinishBo bo) {
        service.finishBurn(bo.getBarInfoId(), LoginHelper.getUserId());
        return R.ok();
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
