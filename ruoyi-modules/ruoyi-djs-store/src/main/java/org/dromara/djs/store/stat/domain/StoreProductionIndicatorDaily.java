package org.dromara.djs.store.stat.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_store_production_indicator_daily")
public class StoreProductionIndicatorDaily extends TenantEntity {

    @TableId
    private Long id;
    private Long storeId;
    private LocalDate statDate;
    private Boolean orderTimely;
    private boolean orderPlaced;
    private BigDecimal porkReceivedWeightKg;
    private BigDecimal vegDamagePortionCount;
    private BigDecimal vegDamageReceivedPortionCount;
    private BigDecimal splitYieldRate;
    private BigDecimal splitLossRate;
    private BigDecimal vegReceivedPortionCount;
    private BigDecimal vegReceiveKgWeight;
    private BigDecimal whiteBarReceivedWeight;
    private BigDecimal splitProductWeight;
    private BigDecimal splitLossWeight;
    private BigDecimal porkReturnWeightKg;
    private BigDecimal vegReturnPortionCount;
    private BigDecimal vegReturnKgWeight;
    @TableLogic
    private String delFlag;
}
