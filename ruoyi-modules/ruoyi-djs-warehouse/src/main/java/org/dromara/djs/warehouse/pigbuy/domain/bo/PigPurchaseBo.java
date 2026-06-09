package org.dromara.djs.warehouse.pigbuy.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.warehouse.pigbuy.domain.PigPurchase;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 外购猪只到货登记入参 BO（FIX-WMS-MP-PIGBUY-001）。
 *
 * <p>mp 端「外购猪只」登记表单提交：来源（活猪 / 白条）/ 数量 / 到货重量 / 供应商 / 到货时间 /
 * 凭证图。Service 生成 {@code purchaseNo} + 置 {@code purchaseStatus=pending} + INSERT。</p>
 *
 * <h3>跨层契约</h3>
 * <ul>
 *   <li>{@code sourceType} 必填，字典 {@code djs_pig_source}（live / white_bar）</li>
 *   <li>{@code quantity} 必填，&gt;= 1；{@code arriveWeight} 必填，&gt; 0</li>
 *   <li>{@code supplierName} 必填；{@code arriveTime} 必填</li>
 *   <li>{@code proofOssIds} 可选（逗号分隔 OSS id）</li>
 *   <li>{@code operatorId} 由 service 用 {@code LoginHelper.getUserId()} 注入，前端不传</li>
 * </ul>
 *
 * @author djs
 * @since FIX-WMS-MP-PIGBUY-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PigPurchase.class, reverseConvertGenerate = false)
public class PigPurchaseBo extends BaseEntity {

    /**
     * 来源类型（字典 {@code djs_pig_source}：live 活猪 / white_bar 白条；必填）。
     */
    @NotBlank(message = "来源类型不能为空")
    @Size(max = 16, message = "来源类型长度不能超过 {max} 个字符")
    private String sourceType;

    /**
     * 到货数量（头 / 条，必填，&gt;= 1）。
     */
    @NotNull(message = "到货数量不能为空")
    @Min(value = 1, message = "到货数量必须大于 0")
    private Integer quantity;

    /**
     * 到货重量 kg（必填，&gt; 0）。
     */
    @NotNull(message = "到货重量不能为空")
    @DecimalMin(value = "0.001", message = "到货重量必须大于 0")
    private BigDecimal arriveWeight;

    /**
     * 供应商名称（必填，自由文本）。
     */
    @NotBlank(message = "供应商不能为空")
    @Size(max = 128, message = "供应商名称长度不能超过 {max} 个字符")
    private String supplierName;

    /**
     * 到货时间（必填）。
     */
    @NotNull(message = "到货时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date arriveTime;

    /**
     * 到货凭证图 OSS IDs CSV（可选）。
     */
    @Size(max = 500, message = "凭证图数据长度不能超过 {max} 个字符")
    private String proofOssIds;

    /**
     * 备注（可选）。
     */
    @Size(max = 500, message = "备注长度不能超过 {max} 个字符")
    private String remark;

}
