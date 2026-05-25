package org.dromara.djs.breed.event.nullreturn.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.event.nullreturn.domain.PigAbnormal;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 返空流记录视图（BRD-EVENT-002 NULL_RETURN）。 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigAbnormal.class)
public class PigAbnormalVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime abnormalDate;

    /** xlsx 字典码 R/A/N（前端按字典 djs_abnormal_type 翻译）。 */
    private String abnormalType;

    private Long relatedBreedingId;
    private String abnormalReason;
    private Long operatorId;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
