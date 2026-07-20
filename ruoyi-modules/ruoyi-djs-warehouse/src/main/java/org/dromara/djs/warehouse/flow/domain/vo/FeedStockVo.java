package org.dromara.djs.warehouse.flow.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 饲料库存聚合 VO（DJS-FIX-MP-W22-002）。
 *
 * <p>按 {@code product_id} 聚合 {@code t_warehouse_location_stock}（SUM(product_stock)），
 * 用于 mp 端"采食原料领用"首页第一个 Tab"饲料原料库"——展示玉米/大豆/麸皮/牧草等
 * 饲料类原料的全场总库存。</p>
 *
 * <p>belongType 在所有行恒为 {@code feed}（由 AppletFeedController 强制下推过滤），
 * 此处不再回显，避免无用字段。</p>
 *
 * @author djs
 * @since DJS-FIX-MP-W22-002
 */
@Data
public class FeedStockVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品 ID（雪花主键，{@code t_warehouse_product_info.id}）。
     */
    private Long productId;

    /**
     * 业务码（如 PROD-FEED-CORN-01）。
     */
    private String productCode;

    /**
     * 产品名（玉米 / 大豆 / 麸皮 / 牧草 / 其他原料）。
     */
    private String productName;

    /**
     * 单位（一律 kg）。
     */
    private String productUnit;

    /**
     * 全场总库存（SUM(product_stock)，跨库位聚合）。
     */
    private BigDecimal totalStock;

    /**
     * 当前登录人今日已领（当日 + 本产品 SUM(change_quantity) WHERE flow_type='pick_out'，无流水时 0）。
     */
    private BigDecimal todayPicked;

    /**
     * 当前登录人今日已退（当日 + 本产品 SUM(change_quantity) WHERE flow_type='return_in'，无流水时 0）。
     */
    private BigDecimal todayReturned;

    /**
     * 缩略图 URL（IMG-LIB-001 4 层 resolver 回填，L1 产品 image_oss_id → L2 feed 默认图 → L3 全局；前端 image 直接绑定，保证非空兜底）。
     */
    private String productImage;

}
