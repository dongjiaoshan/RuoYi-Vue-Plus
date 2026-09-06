package org.dromara.djs.warehouse.boardstat.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 品类卡内单行（一个计量单位一行，三指标同处该单位量纲）。
 *
 * <p>同一品类下有多种单位（猪肉 kg 原料 + 份装成品）时出多行，行序按单位名升序。</p>
 *
 * <p>环比 {@code *Ratio}：{@code (本月 - 上月) / 上月 × 100}，保留 2 位。
 * <b>上月无数据或上月为 0 时为 {@code null}</b> —— 前端据此显示黑色 0.00%
 * （不是 0，也不是涨绿跌红的彩色 0）。</p>
 *
 * @author djs
 */
@Data
public class CategoryUnitStatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 计量单位（kg / 份 / 枚 / 盒 …）。 */
    private String unit;

    /** 入库量（本月原材料入库合计）。 */
    private BigDecimal inboundQty;

    /** 入库量环比%，null = 上月无数据。 */
    private BigDecimal inboundRatio;

    /** 生产量（本月产品生产合计；kg 单位取重量、计数单位取记录条数）。 */
    private BigDecimal produceQty;

    /** 生产量环比%，null = 上月无数据。 */
    private BigDecimal produceRatio;

    /** 原材料消耗量（本月生产消耗的原材料合计）。 */
    private BigDecimal materialQty;

    /** 原材料消耗量环比%，null = 上月无数据。 */
    private BigDecimal materialRatio;
}
