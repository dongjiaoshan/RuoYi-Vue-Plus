package org.dromara.djs.warehouse.vegout.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 毛菜间出库-可选产品的<b>原始库存篮行</b>（一行一个 {@code t_warehouse_location_stock}）。
 *
 * <p>只在 mapper → service 之间流动，不出接口：service 把它按
 * (产品, 库位, 耳号, 地块, 三期) 归并成对外的 {@link VegOutCandidateVo}
 * （V6 row224 / D-0068「按汇总显示，不分开」）。</p>
 *
 * <p>为什么不在 SQL 里 {@code GROUP BY} + {@code GROUP_CONCAT} 直接出结果：合并后这一行
 * 要带着<b>全部</b>篮 id 回来（提交时跨篮 FIFO 扣减要用），而 {@code GROUP_CONCAT} 默认被
 * {@code group_concat_max_len=1024} 截断 —— 雪花 id 一个 20 字符，约 51 个篮就开始<b>静默</b>丢 id，
 * 丢掉的那部分库存永远扣不到。在 Java 侧归并没有这个上限。</p>
 *
 * @author djs
 */
@Data
public class VegOutCandidateRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 库存行 id。 */
    private Long stockId;

    /** 产品 id。 */
    private Long productId;

    /** 产品业务编号。 */
    private String productCode;

    /** 产品名称。 */
    private String productName;

    /** 产品规格。 */
    private String productSpec;

    /** 这一篮的库存量（按 {@link #productUnit} 计量）。 */
    private BigDecimal stockWeight;

    /** 计量单位。 */
    private String productUnit;

    /** 地块 id（可空）。 */
    private Long plotId;

    /** 地块编号（冗余展示）。 */
    private String plotCode;

    /** 地块名称。 */
    private String plotName;

    /** 猪只耳号。 */
    private String earNo;

    /** 篮子实际所在库位名称。 */
    private String locationName;

    /** 三期标识（1 = 三期）。 */
    private Integer thirdPhase;

    /** 产品业态（djs_belong_type）。 */
    private String belongType;

    /** 产品销售价格（新增页「销售单价」默认值）。 */
    private BigDecimal salePrice;
}
