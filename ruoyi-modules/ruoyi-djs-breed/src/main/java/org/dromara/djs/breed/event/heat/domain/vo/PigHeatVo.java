package org.dromara.djs.breed.event.heat.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.event.heat.domain.PigHeat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 查情记录视图（BRD-EVENT-002 OESTRUS）。 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigHeat.class)
public class PigHeatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime heatDate;

    private String heatResult;
    private Integer isPregnantConfirmed;
    private Long operatorId;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
