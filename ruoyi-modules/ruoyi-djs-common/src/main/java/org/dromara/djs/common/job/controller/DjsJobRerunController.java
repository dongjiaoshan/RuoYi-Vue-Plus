package org.dromara.djs.common.job.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.common.job.DjsJobRegistry;
import org.dromara.djs.common.job.DjsJobRunner;
import org.dromara.djs.common.job.domain.bo.DjsJobLogQuery;
import org.dromara.djs.common.job.domain.bo.DjsJobRerunBo;
import org.dromara.djs.common.job.domain.vo.DjsJobLogVo;
import org.dromara.djs.common.job.service.IDjsJobLogService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * djs 定时任务重跑 Controller（DENGBO row7）。
 *
 * <p>3 端点：</p>
 * <ul>
 *   <li>{@code GET /djs/job/rerun/list} —— 分页查 {@code t_djs_job_log} 执行日志</li>
 *   <li>{@code GET /djs/job/rerun/jobs} —— 列出 registry 已注册可重跑 job 名</li>
 *   <li>{@code POST /djs/job/rerun/run} —— 按 jobName + begin..end 逐日重跑（无日期 job 传 null 跑一次）</li>
 * </ul>
 *
 * <p>权限串 {@code djs:job:rerun:{list,exec}}。重跑经 {@link DjsJobRunner#runForDate} 落
 * {@code trigger_type=manual} 审计行；job 主体 UPSERT 幂等，重跑安全。</p>
 *
 * @author djs
 * @since DENGBO-R7
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/job/rerun")
public class DjsJobRerunController extends BaseController {

    /** 单日重跑跨度上限（防误传巨大区间打爆调度线程）。 */
    private static final long MAX_RERUN_DAYS = 366;

    private final IDjsJobLogService jobLogService;
    private final DjsJobRegistry jobRegistry;

    /**
     * 分页查询定时任务执行日志（按 run_time 倒序）。
     */
    @SaCheckPermission("djs:job:rerun:list")
    @GetMapping("/list")
    public TableDataInfo<DjsJobLogVo> list(DjsJobLogQuery query, PageQuery pageQuery) {
        return jobLogService.queryPageList(query, pageQuery);
    }

    /**
     * 列出已注册可重跑 job 名（下拉用）。
     */
    @SaCheckPermission("djs:job:rerun:list")
    @GetMapping("/jobs")
    public R<List<String>> jobs() {
        return R.ok(List.copyOf(jobRegistry.names()));
    }

    /**
     * 按日期范围手动重跑指定 job。
     *
     * <p>{@code begin}..{@code end} 逐日调用注册的重算逻辑（含两端，UPSERT 幂等）；
     * 无日期 job 传 begin/end 均为 null，跑一次。每日一条审计行 {@code trigger_type=manual}。</p>
     */
    @SaCheckPermission("djs:job:rerun:exec")
    @Log(title = "定时任务重跑", businessType = BusinessType.OTHER)
    @RepeatSubmit
    @PostMapping("/run")
    public R<Void> run(@Validated @RequestBody DjsJobRerunBo bo) {
        Consumer<LocalDate> task = jobRegistry.get(bo.getJobName());
        if (task == null) {
            throw new ServiceException("未注册的定时任务：" + bo.getJobName());
        }
        LocalDate begin = bo.getBegin();
        LocalDate end = bo.getEnd();

        // 无日期 job：begin/end 均空 → 跑一次（target_date=null）
        if (begin == null && end == null) {
            DjsJobRunner.runForDate(bo.getJobName(), DjsJobRunner.DEFAULT_TENANT, null,
                DjsJobRunner.TRIGGER_MANUAL, task);
            return R.ok();
        }

        // 有日期 job：begin/end 必须成对且 begin <= end
        if (begin == null || end == null) {
            throw new ServiceException("日期范围需同时提供开始和结束日期");
        }
        if (begin.isAfter(end)) {
            throw new ServiceException("开始日期不能晚于结束日期");
        }
        if (begin.until(end).getDays() < 0 || daysBetween(begin, end) > MAX_RERUN_DAYS) {
            throw new ServiceException("重跑日期范围不能超过 " + MAX_RERUN_DAYS + " 天");
        }

        for (LocalDate d = begin; !d.isAfter(end); d = d.plusDays(1)) {
            DjsJobRunner.runForDate(bo.getJobName(), DjsJobRunner.DEFAULT_TENANT, d,
                DjsJobRunner.TRIGGER_MANUAL, task);
        }
        return R.ok();
    }

    /** begin..end 含两端的天数（begin 与 end 同日 = 1）。 */
    private long daysBetween(LocalDate begin, LocalDate end) {
        return Objects.requireNonNull(begin).datesUntil(end.plusDays(1)).count();
    }

}
