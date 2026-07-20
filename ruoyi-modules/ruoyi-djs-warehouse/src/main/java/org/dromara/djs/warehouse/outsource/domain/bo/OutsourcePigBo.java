package org.dromara.djs.warehouse.outsource.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.warehouse.outsource.domain.OutsourcePig;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 外购猪只入参 BO（DJS-FIX-WMS-RALN）。
 *
 * <p>仅新增（原型无编辑）。必填校验：购买日期 / 送宰日期 / 毛猪重量 / 供应商。
 * 可空：到场时间 / 猪只标识号 / 购买人。</p>
 *
 * @author djs
 * @since DJS-FIX-WMS-RALN
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = OutsourcePig.class, reverseConvertGenerate = false)
public class OutsourcePigBo extends BaseEntity {

    /**
     * ID（仅删除/详情路径用，新增不传）。
     */
    private Long id;

    /**
     * 购买日期（必填）。
     */
    @NotNull(message = "{outsourcePig.purchaseDate.required}")
    private Date purchaseDate;

    /**
     * 到场时间（可空）。
     */
    private Date arriveTime;

    /**
     * 猪只标识号（可空）。
     */
    @Size(max = 64, message = "{outsourcePig.pigMarkNo.size}")
    private String pigMarkNo;

    /**
     * 毛猪重量 kg（必填，非负）。
     */
    @NotNull(message = "{outsourcePig.pigWeight.required}")
    @PositiveOrZero(message = "{outsourcePig.pigWeight.positive}")
    private BigDecimal pigWeight;

    /**
     * 送宰日期（必填）。
     */
    @NotNull(message = "{outsourcePig.slaughterDate.required}")
    private Date slaughterDate;

    /**
     * 供应商 ID（必填）。
     */
    @NotNull(message = "{outsourcePig.supplierId.required}")
    private Long supplierId;

    /**
     * 购买人（sys_user.user_id，可空）。
     */
    @Size(max = 32, message = "{outsourcePig.buyer.size}")
    private String buyer;

    /**
     * 备注。
     */
    @Size(max = 500, message = "{outsourcePig.remark.size}")
    private String remark;

}
