package org.dromara.djs.warehouse.tare.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 去皮规格（{@code t_warehouse_tare_spec}）：称重页选中后从秤读数里扣掉置物框等的重量。
 *
 * @author djs
 * @since V7-R2
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_tare_spec")
public class TareSpec extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 规格名称。 */
    private String specName;

    /** 去皮重量 kg（三位小数）。 */
    private BigDecimal tareWeight;

    /** 是否默认 djs_yes_no：1=是 / 0=否。同一位置多个默认时取更新时间最近的一个。 */
    private Integer isDefault;

    /** 可选位置 CSV（字典 {@code djs_tare_position} 值）。 */
    private String positions;

    @TableLogic
    private String delFlag;

    private Long delUnique;

}
