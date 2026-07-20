package org.dromara.djs.breed.event.weaning.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.event.weaning.domain.PigWeaningDetail;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 断奶逐头录重明细视图（BRD-FIX-MP-EVENT-BREED-IA-001）。
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigWeaningDetail.class)
public class PigWeaningDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long weaningId;
    private Integer pigletSeq;
    private String earNo;
    private BigDecimal weight;
    private String remark;
}
