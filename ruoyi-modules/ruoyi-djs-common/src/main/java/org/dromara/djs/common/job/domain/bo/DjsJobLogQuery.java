package org.dromara.djs.common.job.domain.bo;

import lombok.Data;

import java.io.Serializable;

/**
 * djs 定时任务日志列表查询入参（DENGBO row7）。
 *
 * <p>分页参数由 Controller 单独接收 {@link org.dromara.common.mybatis.core.page.PageQuery}。</p>
 *
 * @author djs
 * @since DENGBO-R7
 */
@Data
public class DjsJobLogQuery implements Serializable {

    /**
     * job 名（精确匹配，可空）。
     */
    private String jobName;

    /**
     * 执行状态（running / success / fail，精确匹配，可空）。
     */
    private String status;

    /**
     * 触发方式（schedule / manual，精确匹配，可空）。
     */
    private String triggerType;

}
