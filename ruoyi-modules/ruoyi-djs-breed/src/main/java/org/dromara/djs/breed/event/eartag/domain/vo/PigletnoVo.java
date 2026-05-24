package org.dromara.djs.breed.event.eartag.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 仔猪耳标历史 VO（admin 只读列表用，{@code t_farm_pig_pigletno} 平铺出参）。
 *
 * @author djs
 * @since BRD-EVENT-003
 */
@Data
public class PigletnoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String pigletEarNo;
    private String motherEarNo;
    private String fatherEarNo;
    private Long farrowId;
    private LocalDateTime tagDate;
    private String pigletSex;
    private BigDecimal birthWeight;
    private Long pigId;
    private Long operatorId;
    private String remark;
    private Date createTime;
}
