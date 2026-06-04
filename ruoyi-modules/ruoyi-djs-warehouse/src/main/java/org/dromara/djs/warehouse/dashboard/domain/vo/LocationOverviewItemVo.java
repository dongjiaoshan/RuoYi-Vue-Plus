package org.dromara.djs.warehouse.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 仓库看板 - 库位概览单行（DJS-FIX-ADMIN-W22-006）。
 *
 * <p>来源：{@code t_warehouse_location_stock} 按 {@code warehouse_id} 聚合
 * SUM(product_stock)，JOIN {@code t_warehouse_location_info} 取库位名 / 类型 / 状态。</p>
 *
 * <p>{@code status} 由 service 推导：库位 {@code location_status=0}（停用）或当月该库位有
 * 盘点差异（{@code t_warehouse_check_record.diff_stock != 0}）→ {@code abnormal}，否则 {@code normal}。</p>
 *
 * @author djs
 * @since DJS-FIX-ADMIN-W22-006
 */
@Data
public class LocationOverviewItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 库位 ID（snowflake，序列化为 string）。
     */
    private Long locationId;

    /**
     * 库位名称。
     */
    private String locationName;

    /**
     * 库位类型（字典 djs_location_type）。
     */
    private String locationType;

    /**
     * 当前库存合计（该库位下所有产品 product_stock 之和，kg 或 件）。
     */
    private BigDecimal currentStock;

    /**
     * 状态：normal / abnormal。
     */
    private String status;

}
