package org.dromara.djs.breed.event.growth.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.event.growth.domain.PigGrowth;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生长记录视图（BRD-EVENT-005 GROWTH）。
 *
 * @author djs
 * @since BRD-EVENT-005
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigGrowth.class)
public class PigGrowthVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate measureDate;

    private BigDecimal weight;
    private BigDecimal backfatThickness;
    private BigDecimal backHeight;
    private String photoOssIds;
    private Long operatorId;
    private String barnName;
    private String penName;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
