package org.dromara.djs.common.supplier.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 供应商交易明细行（DJS-FIX-ADMIN-W22-005）。
 *
 * <p>跨模块只读聚合 VO：breed 药品入库批次 + warehouse 物资入库流水各自实现
 * {@link SupplierDealProvider} 返回本类型，common 的 SupplierController 在内存 UNION
 * 后按 {@code dealDate} 倒序 + 手动分页。</p>
 *
 * <p>放在 common 模块 {@code supplier.api} 包是因为 common 不依赖 breed / warehouse
 * （单向依赖：breed / warehouse → common），把契约下沉到 common 让上游模块各自实现，
 * 避免 common 反向依赖引入循环。</p>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-005
 */
@Data
public class SupplierDealVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 交易日期（药品取生产日期 / 物资取流水业务日期）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dealDate;

    /**
     * 交易商品名称（药品取 {@code t_breed_medicine_info.medicine_name}，
     * 物资取 {@code t_warehouse_product_info.product_name}）。
     */
    private String dealProduct;

    /**
     * 交易量。
     */
    private BigDecimal dealQuantity;

    /**
     * 计量单位（药品取 info.unit / 物资取 product.unit）。
     */
    private String dealUnit;

    /**
     * 来源类型（{@code medicine} 药品入库 / {@code material} 物资入库）；
     * 仅排序 / 调试用，前端不显式展示。
     */
    private String sourceType;

}
