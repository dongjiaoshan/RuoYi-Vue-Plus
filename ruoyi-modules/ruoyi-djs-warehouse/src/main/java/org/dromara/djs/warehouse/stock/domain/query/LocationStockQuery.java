package org.dromara.djs.warehouse.stock.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.util.List;

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
     * 库位 ID 多选（R70 库位实体下拉多选）。非空时按 IN 过滤，优先于单值 locationId。
     */
    private List<Long> locationIds;

    /**
     * 产品 ID（精确）。
     */
    private Long productId;

    /**
     * 产品名称（模糊）。
     */
    private String productName;

    /**
     * 归属类型（{@code djs_belong_type} 字典值，精确；belong_type 不在库存表，
     * service 先按产品主数据 belong_type 解析 productId 集合再过滤库存）。
     */
    private String belongType;

    /**
     * 归属类型多选（R70 产品品类下拉多选）。非空时先按 belong_type IN 反查 product_info.id 集合再
     * 过滤库存，优先于单值 belongType。
     */
    private List<String> belongTypes;

    /**
     * 耳号（模糊）。
     */
    private String earNo;

    /**
     * 地块 ID（精确）。
     */
    private Long plotId;

    /**
     * 药品 ID（精确）。
     */
    private Long medicineId;

    /**
     * 地块编号（模糊；service 先解析匹配的 plotId 集合再过滤库存）。
     */
    private String blockNo;

    /**
     * 【三期】标识精确匹配（V6 row92）：传 1 = 只看三期库存；不传 = 全部。
     */
    private Integer thirdPhase;

    /**
     * 是否完成（1=是 / 0=否）。
     */
    private Integer isEnd;

}
