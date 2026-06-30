package org.dromara.djs.common.job.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * djs 定时任务执行日志实体（DENGBO row7）。
 *
 * <p>对应表 {@code t_djs_job_log}，所有经 {@link org.dromara.djs.common.job.DjsJobRunner} 跑的
 * djs job（养殖聚合 / 仓库统计 / 有机证书预警）每次执行落一行：定时触发 {@code trigger_type='schedule'}，
 * admin 手动重跑 {@code trigger_type='manual'} 且落 {@code target_date}（重算目标日）。</p>
 *
 * <p>job 主体逻辑均 UPSERT 幂等，失败可安全重跑；本表只做审计，不参与业务计算。</p>
 *
 * @author djs
 * @since DENGBO-R7
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_djs_job_log")
public class DjsJobLog extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日志 ID（雪花）。
     */
    @TableId
    private Long id;

    /**
     * job 名（breed-aggregate / warehouse-stat / organic-warning）。
     */
    private String jobName;

    /**
     * 重算目标日（手动重跑落具体日；schedule / 无日期 job 为 null）。
     */
    private LocalDate targetDate;

    /**
     * 执行状态（running / success / fail）。
     */
    private String status;

    /**
     * 失败信息（status=fail 时落，截断至 1000 字符）。
     */
    private String errorMsg;

    /**
     * 耗时毫秒（done / fail 时回填）。
     */
    private Long costMs;

    /**
     * 触发时间。
     */
    private LocalDateTime runTime;

    /**
     * 触发方式（schedule 定时 / manual 手动重跑）。
     */
    private String triggerType;

    /**
     * 软删标记（'0' 未删 / '1' 已删）。
     */
    @TableLogic
    private String delFlag;

}
