package org.dromara.djs.warehouse.boardstat.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 明细页顶部「按单位的合计」一行（V6-R178）。
 *
 * <p>数值不另写 SQL，直接取卡片那两个聚合方法的结果，所以合计与卡片数字构造上一致
 * ——甲方点进明细第一眼就能拿它对上刚才那张卡。</p>
 *
 * @author djs
 */
@Data
public class BoardStatUnitTotalVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 计量单位（kg / 份 / 枚 …；档案未填单位为「未标单位」）。 */
    private String unit;

    /** 该单位下的合计量（= 品类卡上同单位那一格的数字）。 */
    private BigDecimal qty;
}
