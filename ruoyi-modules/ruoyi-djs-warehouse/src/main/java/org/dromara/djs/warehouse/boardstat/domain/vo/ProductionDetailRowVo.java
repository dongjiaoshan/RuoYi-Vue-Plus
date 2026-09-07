package org.dromara.djs.warehouse.boardstat.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 「生产明细」一行（mp 仓库统计品类卡 → 生产明细，V6-R178）。
 *
 * <p>一行 = 一条计入卡片「生产量」的生产记录。{@code qty} 照抄卡片那条 CASE 的两个分支：
 * 计重单位取本行 product_weight，计数单位取 1，故按单位 Σ qty 等于卡片数字。</p>
 *
 * @author djs
 */
@Data
public class ProductionDetailRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 生产日期。 */
    private LocalDate produceDate;

    /** 产品名称。 */
    private String productName;

    /** 产品规格（未填 → service 兜成 "—"）。 */
    private String productSpec;

    /** 本行生产量（计重单位 = 产品重量，计数单位 = 1）。 */
    private BigDecimal qty;

    /** 计量单位（档案未填 → service 兜成「未标单位」）。 */
    private String unit;

    /** 本行原材料耗用量（kg；未记原材料时为 0）。 */
    private BigDecimal materialConsume;

    /** 本行原材料名称（未记原材料 / 档案已删 → service 兜成「—」）。 */
    private String materialName;

    /** 本行原材料计量单位（未记原材料时为空串）。 */
    private String materialUnit;
}
