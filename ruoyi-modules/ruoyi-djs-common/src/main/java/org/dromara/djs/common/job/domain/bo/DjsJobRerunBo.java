package org.dromara.djs.common.job.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * djs 定时任务手动重跑入参 BO（DENGBO row7）。
 *
 * <p>{@code begin}..{@code end} 逐日重跑（含两端）；无日期 job（如有机证书预警）传 begin/end 均为 null，跑一次。</p>
 *
 * @author djs
 * @since DENGBO-R7
 */
@Data
public class DjsJobRerunBo implements Serializable {

    /**
     * 要重跑的 job 名（须在 registry 已注册）。
     */
    @NotBlank(message = "job 名不能为空")
    private String jobName;

    /**
     * 重跑起始日（含）；无日期 job 可为 null。
     */
    private LocalDate begin;

    /**
     * 重跑结束日（含）；无日期 job 可为 null。
     */
    private LocalDate end;

}
