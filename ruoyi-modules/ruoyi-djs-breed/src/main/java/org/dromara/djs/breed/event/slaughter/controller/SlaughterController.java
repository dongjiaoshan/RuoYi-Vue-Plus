package org.dromara.djs.breed.event.slaughter.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.event.slaughter.domain.bo.SlaughterBatchBo;
import org.dromara.djs.breed.event.slaughter.domain.bo.SlaughterBo;
import org.dromara.djs.breed.event.slaughter.domain.query.SlaughterQuery;
import org.dromara.djs.breed.event.slaughter.domain.vo.PigMarketingVo;
import org.dromara.djs.breed.event.slaughter.domain.vo.SlaughterBatchPigVo;
import org.dromara.djs.breed.event.slaughter.service.ISlaughterService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 出栏事件 Controller（BRD-EVENT-004 SLAUGHTER）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/event/slaughter")
public class SlaughterController extends BaseController {

    private final ISlaughterService slaughterService;

    @SaCheckPermission("djs:breed:event:slaughter")
    @Log(title = "出栏录入", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<PigMarketingVo> record(@Validated @RequestBody SlaughterBo bo) {
        return R.ok(slaughterService.recordSlaughter(bo));
    }

    /**
     * 批量出栏（mp 出栏录入页「批量出栏」）。
     *
     * <p>N 头猪各自出栏重量 + 共用的出栏日期/去向/人员/照片。逐头复用单只 {@code recordSlaughter}，
     * 每头都触发完整 side effect（状态机 END(MARKET) + 燎毛白条），整批同一事务原子提交；
     * 出栏记录里仍是 N 条独立的单头记录。</p>
     */
    @SaCheckPermission("djs:breed:event:slaughter")
    @Log(title = "批量出栏录入", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/batch")
    public R<List<PigMarketingVo>> recordBatch(@Validated @RequestBody SlaughterBatchBo bo) {
        return R.ok(slaughterService.recordSlaughterBatch(bo));
    }

    /**
     * 批量出栏待录入猪只（pigId → 耳号）。
     *
     * <p>mp 批量选择页只回传 pigId 数组，批量出栏录入页要按「靠左耳号 + 靠右重量输入框」逐头渲染，
     * 用本端点换耳号。返回顺序与入参一致。</p>
     *
     * <p>走 POST body 而非 GET query：雪花 id 19 位，批量选择页「全选」可选出几百头，
     * CSV 拼进 URL 约 460 头就撞 Tomcat 请求行上限（8KB）返 400 空体，录入页直接变死页。</p>
     *
     * @param pigIds 猪只 ID 列表（雪花，前端以 string 传，Jackson 反序列化为 Long）
     */
    @SaCheckPermission("djs:breed:event:slaughter")
    @PostMapping("/batch-pigs")
    public R<List<SlaughterBatchPigVo>> batchPigs(@RequestBody List<Long> pigIds) {
        return R.ok(slaughterService.listBatchPigs(pigIds));
    }

    @SaCheckPermission("djs:breed:event:slaughter:list")
    @GetMapping("/list")
    public TableDataInfo<PigMarketingVo> list(SlaughterQuery query, PageQuery pageQuery) {
        return slaughterService.queryPage(query, pageQuery);
    }
}
