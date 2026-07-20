package org.dromara.djs.warehouse.pack.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 打包录入页底部「门店(N份)」标签条数据（DJS-FIX-WMS-PACK）。
 *
 * <p>原型「肉品 / 果蔬 / 其他产品打包管理」底部需求门店标签：按某产品在需求管理表里各门店
 * 未发货需求量（{@code demand_quantity - shipped_count > 0}）聚合，给工人选门店时做参考。
 * 纯展示/选择参考——打包提交仍单店语义（带单 storeId），不做多店拆分。</p>
 *
 * @author djs
 * @since DJS-FIX-WMS-PACK
 */
@Data
public class StoreDemandCopiesVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 门店 ID（FK → t_md_store.id）。 */
    private Long storeId;

    /** 门店名称。 */
    private String storeName;

    /** 该产品在该门店的未发货需求量（份数 = SUM(demand_quantity - shipped_count)）。 */
    private BigDecimal copies;
}
