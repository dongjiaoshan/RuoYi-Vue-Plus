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
import org.dromara.djs.warehouse.veg.domain.vo.HandleRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.PendingPlantingRecordVo;
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
 * <p>4 个端点：</p>
 * <ul>
 *   <li>{@code GET  /applet/warehouse/vegHandle/pending}   待处理 planting_record 列表</li>
 *   <li>{@code POST /applet/warehouse/vegHandle/submit}    提交一条 handle_record（采收 / 处理）</li>
 *   <li>{@code GET  /applet/warehouse/vegHandle/myRecords} "我的"处理流水（按 handle_user）</li>
 *   <li>{@code GET  /applet/warehouse/vegHandle/{id}/records} 汇总下钻流水（mp 详情用）</li>
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

    @SaCheckLogin
    @PostMapping("/submit")
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

}
