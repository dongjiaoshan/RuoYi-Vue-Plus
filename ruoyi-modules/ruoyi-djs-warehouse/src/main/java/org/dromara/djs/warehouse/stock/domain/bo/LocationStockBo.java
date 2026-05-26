package org.dromara.djs.warehouse.stock.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.warehouse.stock.domain.LocationStock;

import java.math.BigDecimal;

/**
 * 库存明细入参 BO（WMS-MD-001）。
 *
 * <p>本 ticket admin 不暴露新增/编辑入口（库存由 WMS-DEMAND-001 / WMS-STOCK-001 D8-D11 通过流水触发写入），
 * BO 保留供 service 内部 / 单测使用。</p>
 *
 * <p>3 维互斥（productId / earNo / plotId 三选一），由 service 实现层显式校验。</p>
 *
 * @author djs
 * @since WMS-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = LocationStock.class, reverseConvertGenerate = false)
public class LocationStockBo extends BaseEntity {

    /**
     * 库存 ID（编辑时必填）。
     */
    private Long id;

    /**
     * 所在库位（FK）。
     */
    @NotNull(message = "{stock.location.required}")
    private Long locationId;

    /**
     * 产品 ID（与 earNo/plotId 三选一）。
     */
    private Long productId;

    /**
     * 猪只耳号（与 productId/plotId 三选一）。
     */
    @Size(max = 32, message = "{stock.ear_no.size}")
    private String earNo;

    /**
     * 地块 ID（与 productId/earNo 三选一）。
     */
    private Long plotId;

    /**
     * 产品名称（冗余字段，必填）。
     */
    @NotBlank(message = "{stock.product_name.required}")
    @Size(max = 128, message = "{stock.product_name.size}")
    private String productName;

    /**
     * 当前库存数量。
     */
    @NotNull(message = "{stock.product_stock.required}")
    @PositiveOrZero(message = "{stock.product_stock.positive}")
    private BigDecimal productStock;

    /**
     * 产品单位。
     */
    @NotBlank(message = "{stock.product_unit.required}")
    @Size(max = 16, message = "{stock.product_unit.size}")
    private String productUnit;

    /**
     * 是否完成（默认 0=否）。
     */
    private Integer isEnd;

    /**
     * 备注。
     */
    @Size(max = 500, message = "{stock.remark.size}")
    private String remark;

}
