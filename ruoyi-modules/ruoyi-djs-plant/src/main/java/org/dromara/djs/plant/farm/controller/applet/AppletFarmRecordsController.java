package org.dromara.djs.plant.farm.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.farm.domain.bo.DisasterBatchBo;
import org.dromara.djs.plant.farm.domain.bo.DisasterRecordBo;
import org.dromara.djs.plant.farm.domain.bo.EmptyRecordBo;
import org.dromara.djs.plant.farm.domain.bo.GrowBatchBo;
import org.dromara.djs.plant.farm.domain.bo.GrowRecordBo;
import org.dromara.djs.plant.farm.domain.bo.HarvestWeightBo;
import org.dromara.djs.plant.farm.domain.bo.PlotPickStatusBo;
import org.dromara.djs.plant.farm.domain.bo.RotationRecordBo;
import org.dromara.djs.plant.farm.domain.bo.TransplantRecordBo;
import org.dromara.djs.plant.farm.domain.query.FarmRecordsQuery;
import org.dromara.djs.plant.farm.domain.vo.DispatchSummaryVo;
import org.dromara.djs.plant.farm.domain.vo.FarmCropPlotVo;
import org.dromara.djs.plant.farm.domain.vo.CropZoneCountVo;
import org.dromara.djs.plant.farm.domain.vo.FarmCropTargetCardVo;
import org.dromara.djs.plant.farm.domain.vo.FarmRecordsVo;
import org.dromara.djs.plant.farm.service.IFarmRecordsService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 农事录入 mp 端 Controller（PLT-WORK-001）。
 *
 * <p>路径前缀 {@code /djs/applet/plant/farm/}；7 端点：</p>
 * <ul>
 *   <li>POST submit/empty       空地阶段（翻耕 / 整地 / 施肥）</li>
 *   <li>POST submit/grow        生长阶段 6 类（水肥 / 浇灌 / 除草 / 病虫防治 / 整枝绑蔓 / 采摘活动）</li>
 *   <li>POST submit/disaster    灾害（独立画布；触发 plant_details.loss_yield 累加）</li>
 *   <li>POST submit/transplant  移栽（独立画布；transplant_percent ≤60）</li>
 *   <li>POST submit/rotation    退茬（触发 plot_info.plot_status=1 + plant_details completed）</li>
 *   <li>GET  dispatchSummary    中央分发台 12 卡片今日数</li>
 *   <li>GET  myRecords          工人查询自己的记录</li>
 * </ul>
 *
 * <p>权限：父权限 {@code djs:applet:plant:work:*}（菜单 8091-8096 seed）。{@code @SaCheckLogin} 走 sa-token
 * 真路（mp V1 mock 登录已发真 token，参 ADR-0003）。</p>
 *
 * @author djs
 * @since PLT-WORK-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/applet/plant/farm")
public class AppletFarmRecordsController extends BaseController {

    private final IFarmRecordsService farmRecordsService;

    @SaCheckLogin
    @SaCheckPermission("djs:applet:plant:work:empty")
    @PostMapping("/submit/empty")
    public R<Long> submitEmpty(@Valid @RequestBody EmptyRecordBo bo) {
        return R.ok(farmRecordsService.submitEmpty(bo));
    }

    @SaCheckLogin
    @SaCheckPermission("djs:applet:plant:work:grow")
    @PostMapping("/submit/grow")
    public R<Long> submitGrow(@Valid @RequestBody GrowRecordBo bo) {
        return R.ok(farmRecordsService.submitGrow(bo));
    }

    @SaCheckLogin
    @SaCheckPermission("djs:applet:plant:work:disaster")
    @PostMapping("/submit/disaster")
    public R<Long> submitDisaster(@Valid @RequestBody DisasterRecordBo bo) {
        return R.ok(farmRecordsService.submitDisaster(bo));
    }

    @SaCheckLogin
    @SaCheckPermission("djs:applet:plant:work:transplant")
    @PostMapping("/submit/transplant")
    public R<Long> submitTransplant(@Valid @RequestBody TransplantRecordBo bo) {
        return R.ok(farmRecordsService.submitTransplant(bo));
    }

    @SaCheckLogin
    @SaCheckPermission("djs:applet:plant:work:grow")
    @PostMapping("/submit/rotation")
    public R<Long> submitRotation(@Valid @RequestBody RotationRecordBo bo) {
        return R.ok(farmRecordsService.submitRotation(bo));
    }

    /**
     * 作物 → 多地块批量录入（FIX-PLT-MP-WORK-BATCH-001 #2）：一次对所选 N 个地块下同一任务。
     *
     * @return 成功 INSERT 的农事记录行数
     */
    @SaCheckLogin
    @PostMapping("/submit/growBatch")
    public R<Integer> submitGrowBatch(@Valid @RequestBody GrowBatchBo bo) {
        return R.ok(farmRecordsService.submitGrowBatch(bo));
    }

    /**
     * 灾害「作物 → 多地块」批量录入（FIX-PLT-MP-WORK-BATCH-001 灾害批量，#2）：
     * 一次对所选 N 个地块下同一灾害（统一类型/损失率 + 各自损失产量），每地块累加 plant_details.loss_yield。
     *
     * @return 成功 INSERT 的灾害记录行数
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:plant:work:disaster")
    @PostMapping("/submit/disasterBatch")
    public R<Integer> submitDisasterBatch(@Valid @RequestBody DisasterBatchBo bo) {
        return R.ok(farmRecordsService.submitDisasterBatch(bo));
    }

    /**
     * 采摘活动管理「采摘重量录入」（FIX-PLT-MP-HARVEST-001 #3=a）：累加 plant_details.actual_yield +
     * 写一行 harvest_activity 记录携带 harvest_weight。采收 tab 已去重量，本端点为采摘重量唯一录入口。
     *
     * @return 新增农事记录 id
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:plant:work:grow")
    @PostMapping("/submitHarvestWeight")
    public R<Long> submitHarvestWeight(@Valid @RequestBody HarvestWeightBo bo) {
        return R.ok(farmRecordsService.submitHarvestWeight(bo));
    }

    /**
     * 农事多选页候选地块（FIX-PLT-MP-WORK-BATCH-001 #2）：按作物列出可批量录入的地块
     * （含上次同类农事日期 + 间隔天数）。退茬只列采摘完成地块。
     */
    @SaCheckLogin
    @GetMapping("/cropPlots")
    public R<List<FarmCropPlotVo>> cropPlots(@RequestParam Long cropId, @RequestParam String farmType) {
        return R.ok(farmRecordsService.listCropPlots(cropId, farmType));
    }

    /**
     * 工种列表页「作物目标卡」聚合（FIX-PLT-MP-CROPSEL-001）：种植类工种作业 tab 的作物卡数据源。
     *
     * <p>只列已种植作物，按工种状态规则过滤后聚合可操作地块去重数 + 最近同工种农事日期 + 作物图。
     * 退茬只列采摘完成作物；移栽只列保育地块（plot_type='nursery'）上种植的作物。</p>
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:plant:work:grow")
    @GetMapping("/cropTargetCards")
    public R<List<FarmCropTargetCardVo>> cropTargetCards(@RequestParam String farmType,
                                                         @RequestParam(required = false) Long zoneId,
                                                         @RequestParam(required = false) String plotCode) {
        return R.ok(farmRecordsService.listCropTargetCards(farmType, zoneId, plotCode));
    }

    /**
     * 种植类工种作业 tab「片区胶囊」计数（FIX-PLT-MP-CROPSEL-001 P12/P15·补片区筛选）：
     * 原型作物 selector 顶部 {@code A区(N)} 胶囊，按工种规则统计每个活跃片区可操作地块去重数（含 0）。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:plant:work:grow")
    @GetMapping("/cropZoneCounts")
    public R<List<CropZoneCountVo>> cropZoneCounts(@RequestParam String farmType) {
        return R.ok(farmRecordsService.listCropZoneCounts(farmType));
    }

    /**
     * 土地采摘状态调整（FIX-PLT-MP-HARVEST-001 #3）：手动切换地块采摘状态（采摘中 ↔ 采摘完成），
     * 仅状态调整不录重量（重量录入权威源为采收 tab）。
     *
     * @return 受影响的明细行数
     */
    @SaCheckLogin
    @PostMapping("/adjustPlotPickStatus")
    public R<Integer> adjustPlotPickStatus(@Valid @RequestBody PlotPickStatusBo bo) {
        return R.ok(farmRecordsService.adjustPlotPickStatus(bo));
    }

    @SaCheckLogin
    @SaCheckPermission("djs:applet:plant:work:dispatch")
    @GetMapping("/dispatchSummary")
    public R<DispatchSummaryVo> dispatchSummary() {
        return R.ok(farmRecordsService.dispatchSummary());
    }

    @SaCheckLogin
    @SaCheckPermission("djs:applet:plant:work:myList")
    @GetMapping("/myRecords")
    public TableDataInfo<FarmRecordsVo> myRecords(FarmRecordsQuery query, PageQuery pageQuery) {
        query.setOperatorId(LoginHelper.getUserId());
        return farmRecordsService.myRecords(query, pageQuery);
    }

    /**
     * 农事「记录」tab 列表：按 farmType 全场展示（不按登录人班组过滤），各工种列表页记录 tab +
     * 采摘活动记录 tab 共用。与 {@link #myRecords}（我的全部农事，按本人班组过滤）口径区分。
     */
    @SaCheckLogin
    @GetMapping("/records")
    public TableDataInfo<FarmRecordsVo> records(FarmRecordsQuery query, PageQuery pageQuery) {
        return farmRecordsService.queryPageList(query, pageQuery);
    }
}
