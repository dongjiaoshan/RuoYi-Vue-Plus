package org.dromara.djs.breed.event.weaning.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.event.weaning.domain.PigWeaning;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 断奶记录视图（BRD-EVENT-002 WEAN）。
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigWeaning.class)
public class PigWeaningVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;
    private Long farrowId;
    private Long breedingId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime weaningDate;

    private Integer weanedCount;
    private BigDecimal weanedWeight;
    private BigDecimal avgWeanedWeight;
    private Long operatorId;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
