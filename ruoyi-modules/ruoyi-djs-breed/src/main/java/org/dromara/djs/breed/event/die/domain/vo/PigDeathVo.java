package org.dromara.djs.breed.event.die.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.event.die.domain.PigDeath;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 死亡记录视图（BRD-EVENT-004 DIE）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigDeath.class)
public class PigDeathVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deathDate;

    private String deathPigType;
    private String deathKind;
    private String deathReason;
    private String deathDest;
    private BigDecimal deathWeight;
    private String ossIds;
    private Long operatorId;
    private String barnName;
    private String penName;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
