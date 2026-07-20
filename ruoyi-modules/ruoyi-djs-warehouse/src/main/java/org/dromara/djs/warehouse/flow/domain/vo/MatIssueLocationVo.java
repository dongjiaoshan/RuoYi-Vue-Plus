package org.dromara.djs.warehouse.flow.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * mp 物资领用「二级库」chip VO（FIX-WMS-MATISSUE-001）。
 *
 * <p>口径：某业态（{@code belong_type}）下的产品分布在哪些库位 —— 从
 * {@code t_warehouse_location_stock JOIN t_warehouse_product_info} 反查 DISTINCT location。
 * 原型「物资领用」页每个业态 tab 下的橙底库位 chip（白条库 / 冻品库 / 蔬菜保鲜库 / 外购干货库 ...）。</p>
 *
 * <p>不给 {@code t_warehouse_location_info} 加「业态归属」字段（admin 无此维护入口、原型也未要求），
 * 而是数据驱动：库位归属由「该库位实际存了哪个业态的产品」推导。包材业态产品常不分库位 → chip 列表为空，
 * mp 前端不渲染 chip 段（与原型「包材产品 tab 无 chip」一致）。</p>
 *
 * @author djs
 * @since FIX-WMS-MATISSUE-001
 */
@Data
public class MatIssueLocationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 库位 ID（snowflake；前端按 string 处理，作 chip 选中 key）。
     */
    private Long locationId;

    /**
     * 库位业务码（如 LOC0001）。
     */
    private String locationCode;

    /**
     * 库位名称（chip 文案，如「白条库」）。
     */
    private String locationName;

}
