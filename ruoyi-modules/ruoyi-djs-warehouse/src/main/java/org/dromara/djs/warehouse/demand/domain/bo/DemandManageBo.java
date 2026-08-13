package org.dromara.djs.warehouse.demand.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.warehouse.demand.domain.DemandManage;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 需求录入 BO（WMS-DEMAND-001）。
 *
 * <p>4 业态共用本 BO；业态字段差异由 service 端按 {@code productType} 做应用层校验。
 * 编辑路径下 {@code demandNo} 由后端强制锁回旧值；{@code demandStatus} 不在本 BO（走状态机端点）。</p>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = DemandManage.class, reverseConvertGenerate = false)
public class DemandManageBo extends BaseEntity {

    /** 主键（编辑时必填）。 */
    private Long id;

    /** 需求日期（新增 + 编辑都必填）。 */
    @NotNull(message = "{demand.field.demandDate.required}")
    private LocalDate demandDate;

    /** 提单门店 ID，FK {@code t_md_store.id}。 */
    @NotNull(message = "{demand.field.storeId.required}")
    private Long storeId;

    /** 产品 ID（snowflake），FK {@code t_warehouse_product_info.id}。 */
    @NotNull(message = "{demand.field.productId.required}")
    private Long productId;

    /** 产品名称（冗余，前端从 product picker 带回）。 */
    @NotBlank(message = "{demand.field.productName.required}")
    @Size(max = 128, message = "{demand.field.productName.size}")
    private String productName;

    /** 业态：white_bar / vegetable / gift_box / other。 */
    @NotBlank(message = "{demand.field.productType.required}")
    @Size(max = 32, message = "{demand.field.productType.size}")
    private String productType;

    /** 产品规格。 */
    @Size(max = 64, message = "{demand.field.productSpec.size}")
    private String productSpec;

    /**
     * 需求量（必须正数，最多 9 位整数 + 3 位小数）。
     *
     * <p>{@code @Digits} 与 DDL {@code DECIMAL(12,3)} 对齐：不卡的话 1.2345 会被 MySQL
     * <b>静默四舍五入</b>成 1.235（用户不知情），超长整数则漏成通用 500。
     * 门店端两个写入口（{@code StoreDemandBatchBo} 下单 / {@code StoreDemandQuantityBo} 改量）
     * 早有同款约束，本 BO 是仓库端录入与编辑入口，三处必须一致。</p>
     */
    @NotNull(message = "{demand.field.demandQuantity.required}")
    @Positive(message = "{demand.field.demandQuantity.positive}")
    @Digits(integer = 9, fraction = 3, message = "需求量最多 9 位整数、3 位小数")
    private BigDecimal demandQuantity;

    /** 单位。 */
    @NotBlank(message = "{demand.field.productUnit.required}")
    @Size(max = 16, message = "{demand.field.productUnit.size}")
    private String productUnit;

    /** 原材料描述。 */
    @Size(max = 255, message = "{demand.field.rawMaterial.size}")
    private String rawMaterial;

    /** 原材料计算量。 */
    private BigDecimal materialQty;

    /** 需求备注。 */
    @Size(max = 500, message = "{demand.field.demandRemark.size}")
    private String demandRemark;

    /** 需求说明。 */
    @Size(max = 500, message = "{demand.field.demandExplain.size}")
    private String demandExplain;

    /** 期望到货日。 */
    private LocalDate expectedArriveDate;

    /** 需求类型（字典 {@code djs_demand_mailing_type}：store 门店 / mailing 个人邮寄）。 */
    @Size(max = 16, message = "{demand.field.demandType.size}")
    private String demandType;

    /** 预计到店重量(kg)。 */
    private BigDecimal expectedWeight;

    /** 通用备注（继承自 BaseEntity 的 remark）。 */
    @Size(max = 500, message = "{demand.field.remark.size}")
    private String remark;
}
