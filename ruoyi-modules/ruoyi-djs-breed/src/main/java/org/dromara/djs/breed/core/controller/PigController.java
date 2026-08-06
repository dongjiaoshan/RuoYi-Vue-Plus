package org.dromara.djs.breed.core.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.core.domain.bo.PigEarNoUpdateBo;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.domain.query.PigQuery;
import org.dromara.djs.breed.core.domain.query.PigStatusRecordQuery;
import org.dromara.djs.breed.core.domain.vo.PigDetailVo;
import org.dromara.djs.breed.core.domain.vo.PigStatusRecordVo;
import org.dromara.djs.breed.core.domain.vo.PigVo;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 猪只主表 + 状态机 Controller（BRD-CORE-001）。
 *
 * <p>5 端点：</p>
 * <ul>
 *   <li>{@code GET /list}          → {@code djs:breed:pig:list}（admin 列表）</li>
 *   <li>{@code GET /{id}}          → {@code djs:breed:pig:query}（详情 + 最近 20 条历史）</li>
 *   <li>{@code GET /{id}/history}  → {@code djs:breed:pig:query}（全量历史，最多 200 条）</li>
 *   <li>{@code POST /event}        → {@code djs:breed:pig:event}（通用事件入口，运维/调试用）</li>
 *   <li>{@code PUT /{id}/ear-no}   → {@code djs:breed:pig:edit}（BRD-LIST-EDIT-001 修改耳号，纠错专用窄口）</li>
 * </ul>
 *
 * <p><b>注意 {@code POST /event}</b>：业务侧 BRD-EVENT-* 子 ticket 不应直接暴露给前端，
 * 应自己写自己的业务端点（如 {@code POST /djs/breed/breeding/record}），内部先 INSERT 业务表
 * 再调 {@code pigCoreService.fireEvent}。本端点仅赋给系统级角色（boss / 调试），不下放普通用户。</p>
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/pig")
public class PigController extends BaseController {

    private final IPigCoreService pigCoreService;

    /** 分页查询猪只列表。 */
    @SaCheckPermission("djs:breed:pig:list")
    @GetMapping("/list")
    public TableDataInfo<PigVo> list(PigQuery query, PageQuery pageQuery) {
        return pigCoreService.queryPage(query, pageQuery);
    }

    /** 详情（含 recentHistory 最近 20 条状态变更）。 */
    @SaCheckPermission("djs:breed:pig:query")
    @GetMapping("/{id}")
    public R<PigDetailVo> getInfo(@PathVariable Long id) {
        return R.ok(pigCoreService.queryDetail(id));
    }

    /** 全量历史（DESC by change_time，最多 200 条）。 */
    @SaCheckPermission("djs:breed:pig:query")
    @GetMapping("/{id}/history")
    public R<List<PigStatusRecordVo>> history(@PathVariable Long id) {
        return R.ok(pigCoreService.listHistory(id));
    }

    /** 全局状态流水分页（admin"事件台账"页用）。 */
    @SaCheckPermission("djs:breed:pig:query")
    @GetMapping("/status-record/list")
    public TableDataInfo<PigStatusRecordVo> statusRecordList(PigStatusRecordQuery query, PageQuery pageQuery) {
        return pigCoreService.queryStatusRecordPage(query, pageQuery);
    }

    /**
     * 通用事件入口（运维 / 调试用 — 业务侧请使用各事件 ticket 的专用端点）。
     */
    @SaCheckPermission("djs:breed:pig:event")
    @Log(title = "猪只状态机事件", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/event")
    public R<PigStatusRecordVo> fireEvent(@Validated @RequestBody PigEventBo bo) {
        return R.ok(pigCoreService.fireEvent(bo));
    }

    /**
     * 修改耳号（BRD-LIST-EDIT-001）——小程序录入手误录错序号时的纠错口子，admin 猪只主表用。
     * 只改 {@code ear_no}/{@code ear_tag}，不碰状态机/其他字段；不走 {@link #fireEvent}。
     */
    @SaCheckPermission("djs:breed:pig:edit")
    @Log(title = "修改耳号", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{id}/ear-no")
    public R<PigVo> updateEarNo(@PathVariable Long id, @Validated @RequestBody PigEarNoUpdateBo bo) {
        return R.ok(pigCoreService.updateEarNo(id, bo));
    }
}
