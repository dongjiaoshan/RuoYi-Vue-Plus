package org.dromara.djs.warehouse.flow.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * mp 包材库列表项 VO（GET {@code /applet/warehouse/packing/list}）。
 *
 * <p>口径：以 {@code product_info.belong_type='package'} 的包材产品为粒度，
 * LEFT JOIN {@code t_warehouse_location_stock} 聚合当前库存 + 取最近盘点日期。
 * 一个包材产品可能分布在多个库位 → SUM(product_stock) / MAX(latest_check_time)。</p>
 *
 * @author djs
 * @since WMS-FLOW-001
 */
@Data
public class PackingItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品主键（snowflake；前端按 string 处理）。
     */
    private Long productId;

    /**
     * 产品业务码（PROD-xxx）。
     */
    private String productCode;

    /**
     * 包材名称。
     */
    private String productName;

    /**
     * 单位（个 / 卷 / 张 等）。
     */
    private String productUnit;

    /**
     * 当前库存（跨库位 SUM；无库存记录时为 0）。
     */
    private BigDecimal currentStock;

    /**
     * 上次盘点日期（跨库位 MAX；从未盘点为 null）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date latestCheckTime;

}
