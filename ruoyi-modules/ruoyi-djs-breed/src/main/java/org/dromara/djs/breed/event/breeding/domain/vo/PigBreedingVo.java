package org.dromara.djs.breed.event.breeding.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.event.breeding.domain.PigBreeding;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 配种记录视图（BRD-EVENT-002 BREED）。
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigBreeding.class)
public class PigBreedingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime breedingDate;

    private String breedingType;
    private String boarEarNo;
    /** 配种精液字典 code（djs_semen；FIX-BREEDING-001 #21）。 */
    private String semenCode;
    private Integer parity;
    private Long operatorId;
    private String barnName;
    private String penName;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
