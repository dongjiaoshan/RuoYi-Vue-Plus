package org.dromara.djs.breed.event.eliminate.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.event.eliminate.domain.PigCulling;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 淘汰记录视图（BRD-EVENT-004 ELIMINATE）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigCulling.class)
public class PigCullingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cullingDate;

    private String cullingReason;
    private String cullingDest;
    private BigDecimal cullingWeight;
    private String ossIds;
    private Long operatorId;

    /** 淘汰记录人 sys_user.user_id（mp EmployeePicker 所选）。 */
    private Long cullingRecorderId;

    private String remark;

    /**
     * 淘汰照片可访问 URL 列表（service JOIN sys_oss resolve）。
     * <p>mp {@code <image>} 无法携 Bearer token 访问鉴权下载端点，裸 ossId 渲不出图，
     * 故后端预解析成可直接渲染的 URL；无照片返空 list。</p>
     */
    private List<String> imageUrls;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
