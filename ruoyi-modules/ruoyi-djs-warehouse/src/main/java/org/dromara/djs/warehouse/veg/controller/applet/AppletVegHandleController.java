package org.dromara.djs.warehouse.veg.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.veg.domain.bo.HandleRecordSubmitBo;
import org.dromara.djs.warehouse.veg.domain.bo.HarvestSubmitBo;
import org.dromara.djs.warehouse.veg.domain.bo.PickActivitySubmitBo;
import org.dromara.djs.warehouse.veg.domain.bo.ProcessSubmitBo;
import org.dromara.djs.warehouse.veg.domain.vo.HandleRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.PendingPlantingRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegCropVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegPlotDetailVo;
import org.dromara.djs.warehouse.veg.service.IVegetableHandleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 毛菜处理 mp 端 Controller（WMS-VEG-001）。
 *
 * <p>端点：</p>
 * <ul>
 *   <li>{@code GET  /applet/warehouse/vegHandle/pending}   待处理 planting_record 列表</li>
 *   <li>{@code POST /applet/warehouse/vegHandle/submit}    <b>已停用</b>（V6 row102）—— 恒返 410，指向 harvest / process</li>
 *   <li>{@code GET  /applet/warehouse/vegHandle/myRecords} "我的"处理流水（按 handle_user）</li>
 *   <li>{@code GET  /applet/warehouse/vegHandle/{id}/records} 汇总下钻流水（mp 详情用）</li>
 *   <li>{@code GET  /applet/warehouse/vegHandle/crops}     菜品列表（按 crop 聚合 4 重量）</li>
 *   <li>{@code GET  /applet/warehouse/vegHandle/crops/{cropId}/plots} 某菜品地块明细列表</li>
 *   <li>{@code POST /applet/warehouse/vegHandle/harvest}   采摘重量录入（地块维度）</li>
 *   <li>{@code POST /applet/warehouse/vegHandle/process}   果蔬处理录入（地块维度 + 去向）</li>
 * </ul>
 *
 * <p>{@code @SaCheckLogin} 走 sa-token 真路径（ADR-0003 mock 登录已发真 token）。</p>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/warehouse/vegHandle")
public class AppletVegHandleController extends BaseController {

    private final IVegetableHandleService service;

    @SaCheckLogin
    @GetMapping("/pending")
    public R<List<PendingPlantingRecordVo>> pending() {
        return R.ok(service.listPending());
    }

    /**
     * 遗留通用录入入口 —— <b>已停用</b>，恒返 410 并指向新入口（{@code /harvest} / {@code /process}）。
     *
     * <p>路由保留而不是直接删掉：删了老客户端只会拿到「请求地址不存在」，
     * 留着能给出「为什么不能用、该用哪个」。</p>
     */
    @SaCheckLogin
    @PostMapping("/submit")
    @SuppressWarnings("deprecation")
    public R<Long> submit(@Valid @RequestBody HandleRecordSubmitBo bo) {
        return R.ok(service.submitHandleRecord(bo));
    }

    @SaCheckLogin
    @GetMapping("/myRecords")
    public TableDataInfo<HandleRecordVo> myRecords(PageQuery pageQuery) {
        return service.myRecords(pageQuery);
    }

    @SaCheckLogin
    @GetMapping("/{id}/records")
    public R<List<HandleRecordVo>> records(@NotNull @PathVariable Long id) {
        return R.ok(service.listRecords(id));
    }

    /**
     * 毛菜处理菜品列表（按 crop 聚合 4 重量）。
     */
    @SaCheckLogin
    @GetMapping("/crops")
    public R<List<VegCropVo>> crops() {
        return R.ok(service.listCrops());
    }

    /**
     * 某菜品下地块明细列表。
     *
     * @param cropId 作物 ID
     */
    @SaCheckLogin
    @GetMapping("/crops/{cropId}/plots")
    public R<List<VegPlotDetailVo>> cropPlots(@NotNull @PathVariable Long cropId) {
        return R.ok(service.listPlotsByCrop(cropId));
    }

    /**
     * 采摘重量录入（地块维度）。
     */
    @SaCheckLogin
    @PostMapping("/harvest")
    public R<Long> harvest(@Valid @RequestBody HarvestSubmitBo bo) {
        return R.ok(service.submitHarvest(bo));
    }

    /**
     * 果蔬处理录入（地块维度 + 去向）。
     */
    @SaCheckLogin
    @PostMapping("/process")
    public R<Long> process(@Valid @RequestBody ProcessSubmitBo bo) {
        return R.ok(service.submitProcess(bo));
    }

    /**
     * 采摘去向录入（DENGBO-R4 决策 A，mp 采摘活动录入弹窗）。
     *
     * <p>编排：plant 写 activity per-event 行 + 非销售累加地块产量；非销售去向写仓库台账。
     * 销售去向不写仓库库存（只进产量分摊）。warehouse → plant 单向依赖，编排放本模块。</p>
     */
    @SaCheckLogin
    @PostMapping("/pickActivity")
    public R<Long> pickActivity(@Valid @RequestBody PickActivitySubmitBo bo) {
        return R.ok(service.submitPickActivity(bo));
    }

}
