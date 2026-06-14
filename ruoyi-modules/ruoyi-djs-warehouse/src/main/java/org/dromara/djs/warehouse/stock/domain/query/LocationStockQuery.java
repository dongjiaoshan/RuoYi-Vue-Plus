package org.dromara.djs.warehouse.stock.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 库存明细列表查询入参（WMS-MD-001）。
 *
 * @author djs
 * @since WMS-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LocationStockQuery extends BaseEntity {

    /**
     * 库位 ID（精确）。
     */
    private Long locationId;

    /**
     * 产品 ID（精确）。
     */
    private Long productId;

    /**
     * 产品名称（模糊）。
     */
    private String productName;

    /**
     * 耳号（模糊）。
     */
    private String earNo;

    /**
     * 地块 ID（精确）。
     */
    private Long plotId;

    /**
     * 地块编号（模糊；service 先解析匹配的 plotId 集合再过滤库存）。
     */
    private String blockNo;

    /**
     * 是否完成（1=是 / 0=否）。
     */
    private Integer isEnd;

}
