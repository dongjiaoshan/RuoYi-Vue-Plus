package org.dromara.djs.common.job.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.job.domain.bo.DjsJobLogQuery;
import org.dromara.djs.common.job.domain.vo.DjsJobLogVo;

import java.time.LocalDate;

/**
 * djs 定时任务执行日志 Service（DENGBO row7）。
 *
 * <p>{@link org.dromara.djs.common.job.DjsJobRunner} 在每个 job 跑前 / 跑后调用
 * recordStart / recordDone / recordFail 落审计行；admin「定时任务重跑」页查 {@link #queryPageList}。</p>
 *
 * @author djs
 * @since DENGBO-R7
 */
public interface IDjsJobLogService {

    /**
     * 分页查询执行日志（按 run_time 倒序）。
     */
    TableDataInfo<DjsJobLogVo> queryPageList(DjsJobLogQuery query, PageQuery pageQuery);

    /**
     * 记录一次执行开始（status=running），返回日志 ID 供 done / fail 回填。
     *
     * @param jobName     job 名
     * @param targetDate  重算目标日（schedule / 无日期 job 传 null）
     * @param triggerType 触发方式（schedule / manual）
     * @return 新建日志行 ID
     */
    Long recordStart(String jobName, LocalDate targetDate, String triggerType);

    /**
     * 记录执行成功（status=success + 回填耗时）。
     *
     * @param logId  recordStart 返回的日志 ID
     * @param costMs 耗时毫秒
     */
    void recordDone(Long logId, long costMs);

    /**
     * 记录执行失败（status=fail + 回填耗时 + 错误信息，截断至 1000 字符）。
     *
     * @param logId  recordStart 返回的日志 ID
     * @param costMs 耗时毫秒
     * @param error  错误信息
     */
    void recordFail(Long logId, long costMs, String error);

}
