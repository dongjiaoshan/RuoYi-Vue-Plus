package org.dromara.djs.breed.event.slaughter.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.breed.event.slaughter.domain.PigMarketing;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出栏记录视图（BRD-EVENT-004 SLAUGHTER）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigMarketing.class)
public class PigMarketingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime marketingDate;

    private BigDecimal outWeight;
    private String outDest;
    private Long storeId;
    private Integer isRoom;
    private String ossIds;
    private Long operatorId;

    /** 出栏操作员 userId（EmployeePicker 所选；snowflake string，出栏记录 tab 回显）。 */
    private String operator;

    /** 出栏操作员姓名（FIX-CC-PERSON-001 #48c：operator userId → USER_ID_TO_NICKNAME 翻译；记录列表显名不显 ID）。 */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operator")
    private String operatorName;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
