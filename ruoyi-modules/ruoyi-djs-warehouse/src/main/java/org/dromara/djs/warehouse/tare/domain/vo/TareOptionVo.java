package org.dromara.djs.warehouse.tare.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 称重页可选去皮规格。列表首项是该页实际默认项（{@code selected=true}），工人进页即选中。
 *
 * @author djs
 * @since V7-R4
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TareOptionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String specName;

    /** 去皮重量 kg。 */
    private BigDecimal tareWeight;

    /** 是否为本页实际默认项（多个默认取更新时间最近的那个）。 */
    private Boolean selected;

}
