package org.dromara.djs.warehouse.stock.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存查询合并行背后的<b>单个库存篮</b>（V6 row223 / D-0068「各篮明细（入库时间+重量）下沉到详情里看」）。
 *
 * <p>列表页按 (产品, 库位, 耳号, 地块, 三期, 白条流水号) 合并之后，篮这一层从列表上消失了；
 * 不给一个能看到「这一行由哪几篮、各多少、各是什么时候进的」的地方，工人就再也对不出那个合计。
 * 详情弹框的「各篮明细」页签读的就是本 VO。</p>
 *
 * <p>只按 {@code stockIds} 取，不接受任何其他筛选：这些 id 就是列表那一行给出的那一组，
 * 多一个少一个都对不上它显示的合计。</p>
 *
 * @author djs
 */
@Data
public class StockBasketVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 库存篮 id。 */
    private Long id;

    /**
     * 建篮时间 —— 也就是这一篮的「入库时间」。
     *
     * <p>是<b>首次建篮</b>的时刻：后续同一个篮补货走 UPSERT 累加，时间戳不动。
     * 所以出库先进先出是「最老的篮先出」，不是「最老的那几公斤先出」（D-0068「底层按篮建账不变」）。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 这一篮当前的库存量（按产品单位计）。 */
    private BigDecimal productStock;

    /** 这一篮最近一次盘点时间（没盘过为空）。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date latestCheckTime;

    /** 这一篮的备注（单篮字段，合并行上不展示，只在这里看得到）。 */
    private String remark;
}
