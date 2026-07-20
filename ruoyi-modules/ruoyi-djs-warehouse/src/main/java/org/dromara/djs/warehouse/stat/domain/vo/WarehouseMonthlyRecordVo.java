package org.dromara.djs.warehouse.stat.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 仓库月数据记录视图对象（WMS-STAT-001，邓博 admin row18）。
 *
 * @author djs
 * @since WMS-STAT-001
 */
@Data
public class WarehouseMonthlyRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计月份 yyyy-MM。 */
    private String statMonth;

    /** 屠宰头数。 */
    private Integer slaughterCount;
    /** 屠宰率%。 */
    private BigDecimal slaughterRate;
    /** 白条出品率%。 */
    private BigDecimal barYieldRate;
    /** 分割出品率%。 */
    private BigDecimal cutYieldRate;
}
