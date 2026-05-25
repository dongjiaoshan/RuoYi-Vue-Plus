package org.dromara.djs.breed.event.farrow.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.breed.event.farrow.domain.PigFarrow;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分娩记录视图（BRD-EVENT-002）。
 *
 * <p>admin 只读列表 / mp 端"分娩 → 选 farrow 贴耳标"两端复用。
 * 字段顺序与列表展示一致：日期 / 母猪 / 仔猪明细 / 备注。</p>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PigFarrow.class)
public class PigFarrowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long pigId;
    private String earNo;
    private Long breedingId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime farrowDate;

    private Integer totalBorn;
    private Integer liveBorn;
    private Integer deadBorn;
    private Integer mummyBorn;
    private Integer weakBorn;
    private Integer maleCount;
    private Integer femaleCount;
    private BigDecimal totalWeight;
    private BigDecimal avgWeight;
    private Integer parity;
    private Long operatorId;
    private String barnName;
    private String penName;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 已贴耳标头数（用于 mp 端"分娩 picker"显示"已贴/活产"，列表场景可空）。 */
    private Integer tagged;

    /** 剩余可贴 = liveBorn - tagged。 */
    private Integer remaining;
}
