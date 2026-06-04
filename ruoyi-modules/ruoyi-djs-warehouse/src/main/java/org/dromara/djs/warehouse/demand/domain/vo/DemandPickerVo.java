package org.dromara.djs.warehouse.demand.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 需求单 picker 轻量 VO（MP-PICKERS-001）。
 *
 * <p>给 mp {@code DemandPicker} 用——调度发货页选可发货需求单。只暴露 id + demandNo +
 * customerName + productName + status 5 字段，足够 picker 列表显示 + 二级标签。</p>
 *
 * <p>不复用 admin {@code DemandManageVo}，避免 audit_history JSON / shipped_count 等
 * 无关重字段进 mp 流量。{@code customerName} 由 controller 批量 JOIN
 * {@code t_md_store.store_name} 填充（demand 主表只存 store_id）。</p>
 *
 * @author djs
 * @since MP-PICKERS-001
 */
@Data
public class DemandPickerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 需求单 ID（snowflake，Jackson 全局序列化为 string）。
     */
    private Long id;

    /**
     * 需求单号（业务码，如 DEMAND-20260601-0001）。
     */
    private String demandNo;

    /**
     * 客户/门店名称（由 store_id 关联 {@code t_md_store.store_name} 填充；门店缺失时为 null）。
     */
    private String customerName;

    /**
     * 产品名称（demand 主表冗余字段，便于 picker 显示）。
     */
    private String productName;

    /**
     * 需求单状态（字典语义见 {@code DemandStatus}：CONFIRMED / IN_PRODUCTION）。
     */
    private String status;

    /**
     * 当前可清点（待分配）产品数 —— mp 选需求时显示，方便工人挑有货的需求。
     * 口径同发货匹配（ShipmentService.findAvailableProductionsForDemand）：
     * demand_id IS NULL + is_delivery_check=0 + store/业态 匹配。
     */
    private Integer availableProductionCount;

}
