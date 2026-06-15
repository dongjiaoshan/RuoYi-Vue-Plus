package org.dromara.djs.warehouse.cross.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 当天确认收货白条 minimal VO（FIX-STORE-TRACE-BAR-001）。
 *
 * <p>门店「猪肉追溯码管理」picker 取数：{@code t_warehouse_bar_info.status='in_stock'}（已入库）
 * 且 {@code DATE(in_time)=CURDATE()} 的白条（含外购）。picker 用 earNo 做 chip 主键，barId 兜底显示。</p>
 *
 * @author djs
 * @since FIX-STORE-TRACE-BAR-001
 */
@Data
public class TodayBarVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 白条主键（雪花）。 */
    private Long id;

    /** 白条业务码 BAR+yyMMdd+4。 */
    private String barId;

    /** 关联猪只耳号（外购白条可能为空）。 */
    private String earNo;

    /** 燎毛后入库重量 kg。 */
    private BigDecimal inWeight;

    /** 入库时间。 */
    private Date inTime;

    /** 白条状态（恒 in_stock，picker 已过滤）。 */
    private String status;
}
