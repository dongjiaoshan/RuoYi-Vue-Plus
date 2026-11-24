package org.dromara.djs.warehouse.shipment.service;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
@Tag("local")
@Tag("dev")
class ShipmentQuantitiesTest {
    @Test void unitsRemainConsistentForWhiteBarKgAndNonKg() {
        DemandManage d = new DemandManage(); ProductProduction p = new ProductProduction();
        p.setProduceQuantity(new BigDecimal("50")); p.setDemandDeductQty(new BigDecimal("4"));
        d.setProductType("white_bar"); d.setProductUnit("kg");
        assertThat(ShipmentQuantities.of(d,p)).isEqualByComparingTo("1");
        d.setProductType("pork"); d.setProductUnit("KG");
        assertThat(ShipmentQuantities.of(d,p)).isEqualByComparingTo("50");
        d.setProductType("gift_box"); d.setProductUnit("盒");
        assertThat(ShipmentQuantities.of(d,p)).isEqualByComparingTo("4");
    }
}
