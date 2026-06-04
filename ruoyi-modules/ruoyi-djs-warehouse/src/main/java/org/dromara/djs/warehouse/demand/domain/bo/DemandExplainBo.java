package org.dromara.djs.warehouse.demand.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 需求说明改备注入参（WMS-DEMAND-002 mp 调度员用）。
 *
 * <p>对应 {@code t_warehouse_demand_manage.demand_explain}（VARCHAR(500)，D8 已建）——
 * 调度员录入客户实操备注，如 "25 号之前每天 1 头猪送到矿业 / 背膘不要太厚"。</p>
 *
 * @author djs
 * @since WMS-DEMAND-002
 */
@Data
public class DemandExplainBo {

    /** 需求说明（自由备注，最长 500 字；可清空设为空串）。 */
    @Size(max = 500, message = "{demand.field.demandExplain.size}")
    private String demandExplain;
}
