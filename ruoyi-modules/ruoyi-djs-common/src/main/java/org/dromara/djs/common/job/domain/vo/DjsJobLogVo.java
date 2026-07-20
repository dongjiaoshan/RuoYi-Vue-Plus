package org.dromara.djs.common.job.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.common.job.domain.DjsJobLog;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * djs 定时任务执行日志视图对象（DENGBO row7）。
 *
 * @author djs
 * @since DENGBO-R7
 */
@Data
@AutoMapper(target = DjsJobLog.class)
public class DjsJobLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日志 ID。
     */
    private Long id;

    /**
     * job 名。
     */
    private String jobName;

    /**
     * 重算目标日（null = 默认 / 无日期 job）。
     */
    private LocalDate targetDate;

    /**
     * 执行状态（running / success / fail）。
     */
    private String status;

    /**
     * 失败信息。
     */
    private String errorMsg;

    /**
     * 耗时毫秒。
     */
    private Long costMs;

    /**
     * 触发时间。
     */
    private LocalDateTime runTime;

    /**
     * 触发方式（schedule / manual）。
     */
    private String triggerType;

}
