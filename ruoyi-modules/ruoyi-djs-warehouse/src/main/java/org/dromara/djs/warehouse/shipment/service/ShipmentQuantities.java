package org.dromara.djs.warehouse.shipment.service;

import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import java.math.BigDecimal;

/** 实际成品按需求单位折算；白条半扇的一条50kg记录只抵1头。 */
public final class ShipmentQuantities {
    private ShipmentQuantities() {}

    public static BigDecimal of(DemandManage demand, ProductProduction production) {
        if ("white_bar".equals(demand.getProductType())) {
            return BigDecimal.ONE;
        }
        String unit = demand.getProductUnit();
        if (unit != null && ("kg".equalsIgnoreCase(unit.trim()) || "公斤".equals(unit.trim()))) {
            BigDecimal weight = production.getProduceQuantity() != null
                ? production.getProduceQuantity() : production.getProductWeight();
            return weight == null ? BigDecimal.ZERO : weight.max(BigDecimal.ZERO);
        }
        BigDecimal copies = production.getDemandDeductQty();
        return copies == null ? BigDecimal.ONE : copies.max(BigDecimal.ZERO);
    }
}
