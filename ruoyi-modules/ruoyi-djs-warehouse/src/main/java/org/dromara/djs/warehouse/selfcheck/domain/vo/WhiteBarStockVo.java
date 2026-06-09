package org.dromara.djs.warehouse.selfcheck.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 白条库整只逐条 VO（§3.C.3 特殊形态：无缩略图无行内操作）。
 *
 * <p>对应 mp 契约 {@code stockCheck.ts} WhiteBarStockVo。数据源 {@code t_warehouse_bar_info}
 * 中 {@code status IN ('in_stock','pending_cut')}（在库白条）。{@code productName} 固定「白条(整只)」；
 * {@code acidDischargeDuration} 由 {@code acid_remove_time}（分钟）转「X小时Y分钟」；
 * {@code designatedStore} 留 null（bar_info 无门店字段）。</p>
 *
 * @author djs
 * @since SELFCHECK
 */
@Data
public class WhiteBarStockVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 白条记录 ID（snowflake string）。
     */
    private Long id;

    /**
     * 产品名（固定「白条(整只)」）。
     */
    private String productName;

    /**
     * 耳标批次码（如 260312-002；外购无耳号则 null）。
     */
    private String earNo;

    /**
     * 入库时间 MM-dd HH:mm。
     */
    private String inboundTime;

    /**
     * 排酸时长文案（如 6小时24分钟；无则「-」）。
     */
    private String acidDischargeDuration;

    /**
     * 猪只指定门店（bar_info 无该字段，固定 null）。
     */
    private String designatedStore;

    /**
     * 入库重量 KG。
     */
    private BigDecimal inboundWeight;

}
