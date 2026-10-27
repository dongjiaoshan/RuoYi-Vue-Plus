package org.dromara.djs.warehouse.demand.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 需求单打印列表行（V7-R8）：一行 = 一张订货单 = 一个（需求日期 × 门店）。
 *
 * @author djs
 * @since V7-R8
 */
@Data
public class DemandPrintSheetVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 需求日期。 */
    private LocalDate demandDate;

    /** 需求门店 FK（{@code t_md_store.id}）。 */
    private Long storeId;

    /** 需求门店名（JOIN {@code t_md_store.store_name}）。 */
    private String storeName;

    /** 需求品类数 = 该门店该日有效需求的去重产品数（与打印明细行数恒等）。 */
    private Integer categoryCount;
}
