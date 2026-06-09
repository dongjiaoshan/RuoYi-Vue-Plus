package org.dromara.djs.warehouse.selfcheck.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 库详情产品行 VO（标准库：缩略图 + 名 + 库存 + 规格/地块 + 上次盘点日期/状态）。
 *
 * <p>对应 mp 契约 {@code stockCheck.ts} StoreProductVo。location_stock JOIN product_info，
 * 仅 {@code product_stock > 0}。{@code plotName} 本期留空（不 JOIN 种植域）。</p>
 *
 * @author djs
 * @since SELFCHECK
 */
@Data
public class StoreProductVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品 ID（snowflake string）。
     */
    private Long productId;

    /**
     * 产品业务码。
     */
    private String productCode;

    /**
     * 产品名称。
     */
    private String productName;

    /**
     * 现存库存量。
     */
    private BigDecimal stock;

    /**
     * 单位（个 / kg / 头）。
     */
    private String productUnit;

    /**
     * 规格（标准库；如 21cm*22cm*23cm）。
     */
    private String productSpec;

    /**
     * 地块（蔬菜库；本期不 JOIN 种植域，留空）。
     */
    private String plotName;

    /**
     * 上次盘点日期 yyyy-MM-dd。
     */
    private String lastCheckDate;

    /**
     * 盘点状态文案：正常 / 异常 / 计损。
     */
    private String lastCheckResult;

    /**
     * 缩略图 url。
     */
    private String thumbUrl;

}
