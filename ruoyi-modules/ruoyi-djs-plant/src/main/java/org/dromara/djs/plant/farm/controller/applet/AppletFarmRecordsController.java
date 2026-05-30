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
import org.dromara.djs.plant.farm.domain.bo.DisasterRecordBo;
import org.dromara.djs.plant.farm.domain.bo.EmptyRecordBo;
import org.dromara.djs.plant.farm.domain.bo.GrowRecordBo;
import org.dromara.djs.plant.farm.domain.bo.RotationRecordBo;
import org.dromara.djs.plant.farm.domain.bo.TransplantRecordBo;
import org.dromara.djs.plant.farm.domain.query.FarmRecordsQuery;
import org.dromara.djs.plant.farm.domain.vo.DispatchSummaryVo;
import org.dromara.djs.plant.farm.domain.vo.FarmRecordsVo;
import org.dromara.djs.plant.farm.service.IFarmRecordsService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
