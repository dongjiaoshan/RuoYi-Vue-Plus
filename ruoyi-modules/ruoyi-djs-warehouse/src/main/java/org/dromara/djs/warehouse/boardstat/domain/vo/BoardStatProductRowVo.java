package org.dromara.djs.warehouse.boardstat.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 卡片下钻明细的一行（入库明细 / 生产明细共用，V6-R193）。
 *
 * <p>一行 = <b>一个产品</b>在统计月内的合计量，不是一条流水 —— 甲方要的是
 * 「统计月所有入库产品的内容」，逐条流水既翻不完也对不出「这个产品这个月一共多少」。</p>
 *
 * <p>{@code qty} 与品类卡同一条聚合口径的分组更细一档（品类 × 单位 → 产品 × 单位），
 * 所以按单位把 rows 的 qty 加起来必然等于卡片上同单位那一格。</p>
 *
 * @author djs
 */
@Data
public class BoardStatProductRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品 id（雪花号，SQL 已 CAST 成字符串，避免 JS 精度截断）。 */
    private String productId;

    /** 产品名称。 */
    private String productName;

    /** 产品规格（档案未填 → 空串，前端据此不渲染规格标签）。 */
    private String productSpec;

    /** 统计月合计量（计重单位取重量合计，计数单位取条数）。 */
    private BigDecimal qty;

    /** 计量单位（档案未填 → 「未标单位」，与卡片行头一致）。 */
    private String unit;

    /** 上月同口径合计量（上月没有这个产品 → 0）。 */
    private BigDecimal prevQty;

    /** 环比%（(本月 − 上月) / 上月 × 100，2 位）；上月为 0 / 无数据 → null，前端显黑色 0.00%。 */
    private BigDecimal ratio;
}
