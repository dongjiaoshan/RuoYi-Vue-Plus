package org.dromara.djs.breed.event.castrate.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.event.castrate.domain.CastrateRecord;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 阉割记录视图（BRD-EVENT-004 CASTRATE）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = CastrateRecord.class)
public class CastrateRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime castrateDate;

    private Long operatorId;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
