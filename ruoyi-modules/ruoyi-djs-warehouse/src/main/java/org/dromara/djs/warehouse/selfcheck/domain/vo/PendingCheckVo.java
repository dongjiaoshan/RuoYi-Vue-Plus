package org.dromara.djs.warehouse.selfcheck.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 待盘点产品卡 VO（库存盘点 tab）。
 *
 * <p>对应 mp 契约 {@code stockCheck.ts} PendingCheckVo。location_stock 该库位 {@code product_stock > 0}，
 * 按 {@code latest_check_time ASC}（久未盘排前）。{@code plotOrEarNo = COALESCE(plot 维, ear_no)}；
 * {@code lastCheckResult} 由 check_result code 转中文。</p>
 *
 * @author djs
 * @since SELFCHECK
 */
@Data
public class PendingCheckVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品 ID（snowflake string）。
     */
    private Long productId;

    /**
     * 产品名称。
     */
    private String productName;

    /**
     * 当前库存量。
     */
    private BigDecimal stock;

    /**
     * 单位。
     */
    private String productUnit;

    /**
     * 地块/耳号（无则 null，前端展示「无」）。
     */
    private String plotOrEarNo;

    /**
     * 最近盘点结果文案（正常 / 异常 / 计损）。
     */
    private String lastCheckResult;

    /**
     * 最近盘点时间 yyyy-MM-dd HH:mm:ss。
     */
    private String lastCheckTime;

    /**
     * 入库日期 yyyy-MM-dd（取 location_stock 该库存行建账时间 create_time）。
     */
    private String inboundDate;

}
