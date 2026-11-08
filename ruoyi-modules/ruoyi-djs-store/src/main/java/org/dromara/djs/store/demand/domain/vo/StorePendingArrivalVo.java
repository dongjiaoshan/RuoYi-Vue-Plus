package org.dromara.djs.store.demand.domain.vo;

import lombok.Data;

/** 发货月台按门店汇总的已发货、待确认到店需求。 */
@Data
public class StorePendingArrivalVo {

    /** 门店主键。 */
    private Long storeId;

    /** 门店名称。 */
    private String storeName;

    /** 待确认到店的需求单数。 */
    private Integer pendingDemandCount;

    /** 待确认需求包含的不同产品数。 */
    private Integer productKindCount;
}
