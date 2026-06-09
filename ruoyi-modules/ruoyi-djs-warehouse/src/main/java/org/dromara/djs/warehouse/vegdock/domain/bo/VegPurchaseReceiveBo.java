package org.dromara.djs.warehouse.vegdock.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 外购果蔬月台收货登记入参（FIX-WMS-MP-VEGDOCK-001，原型图 42 外购产品收货）。
 *
 * <p>mp 工人在「果蔬月台 → 外购收货」tab 登记一笔外购果蔬到货：选品种(crop) + 到货重量 + 来源/供应商
 * + 到货时间 + 凭证图。提交后 service INSERT 一行 {@code t_warehouse_veg_purchase}（status=pending，
 * pendingWeight=arrivalWeight，actualWeight=0），等待「果蔬间入库」确认。</p>
 *
 * <h3>跨层契约</h3>
 * <ul>
 *   <li>{@code cropId} snowflake（前端传 string，后端 Long）；{@code supplierCode} 业务短码 String</li>
 *   <li>{@code arrivalWeight} BigDecimal &gt; 0</li>
 *   <li>{@code arrivalTime} {@code yyyy-MM-dd}（mp toYmd 归一），不传 → service 兜当天</li>
 *   <li>{@code proofOssIds} CSV string（CameraUploadWithWatermark biz_type=warehouse_veg_purchase）</li>
 * </ul>
 *
 * @author djs
 * @since FIX-WMS-MP-VEGDOCK-001
 */
@Data
public class VegPurchaseReceiveBo {

    /**
     * 作物 ID（外购果蔬品种，必填）。
     */
    @NotNull(message = "请选择外购果蔬品种")
    private Long cropId;

    /**
     * 作物名称（冗余回显，前端 picker 选中带回）。
     */
    @Size(max = 64, message = "作物名称过长")
    private String cropName;

    /**
     * 关联产品 ID（belong_type=vegetable，可选；为空则入库时按 cropId 回退）。
     */
    private Long productId;

    /**
     * 到货重量(kg)（必填，&gt; 0）。
     */
    @NotNull(message = "请填写到货重量")
    @DecimalMin(value = "0.001", message = "到货重量必须大于 0")
    private BigDecimal arrivalWeight;

    /**
     * 到货来源说明（自由文本，可选）。
     */
    @Size(max = 200, message = "来源说明过长")
    private String source;

    /**
     * 供应商业务编码（可选；mp SupplierPicker 选中值）。
     */
    @Size(max = 64, message = "供应商编码过长")
    private String supplierCode;

    /**
     * 供应商名称（冗余回显，可选）。
     */
    @Size(max = 128, message = "供应商名称过长")
    private String supplierName;

    /**
     * 到货时间（{@code yyyy-MM-dd}；不传 → 当天）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date arrivalTime;

    /**
     * 凭证图 OSS IDs CSV（可选）。
     */
    @Size(max = 500, message = "凭证图过多")
    private String proofOssIds;

    /**
     * 备注（可选）。
     */
    @Size(max = 500, message = "备注过长")
    private String remark;

}
