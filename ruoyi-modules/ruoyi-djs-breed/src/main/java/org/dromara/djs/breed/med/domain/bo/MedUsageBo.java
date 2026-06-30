package org.dromara.djs.breed.med.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.breed.med.domain.MedUsage;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 药品领用入参 BO（BRD-MED-002）。
 *
 * <p>{@code usageType} 决定库存动向：</p>
 * <ul>
 *   <li>{@code use} / {@code loss} → 扣减批次 quantity（不足拒绝）；</li>
 *   <li>{@code return} → 归还批次 quantity。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-MED-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = MedUsage.class, reverseConvertGenerate = false)
public class MedUsageBo extends BaseEntity {

    /**
     * 主键（编辑场景占位；本 ticket 仅 add / remove，不支持 edit）。
     */
    private Long id;

    /**
     * 药品批次 ID（可选 · r51 去批次：药品使用无批次说法）。传了则按批次校验归属 + 写台账作溯源；
     * 不传则直接按 {@code medicineId} 落账 + 扣药品库实时库存。
     */
    private Long batchId;

    /**
     * 药品 ID（必填）。库存扣减真值在仓库 location_stock，按本字段扣减。
     */
    @NotNull(message = "药品 ID 不能为空")
    private Long medicineId;

    /**
     * 类型：use / return / loss。
     */
    @Pattern(regexp = "^(use|return|loss)$",
        message = "类型必须为 use / return / loss")
    private String usageType;

    /**
     * 数量（大于 0）。
     */
    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.001", message = "数量必须大于 0")
    private BigDecimal usageQty;

    /**
     * 业务日期（必填，领用 / 退回 / 损耗发生日）。
     */
    @NotNull(message = "业务日期不能为空")
    private LocalDate useDate;

    /**
     * 关联栏位（可选）。
     */
    private Long relatedPenId;

    /**
     * 关联猪只（可选）。
     */
    private Long pigId;

    /**
     * 关联用药计划（可选）。
     */
    private Long scheduleId;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

}
