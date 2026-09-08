package org.dromara.djs.store.manage.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 门店管理业态卡「明细」一行（按产品拆，V6-R180）。
 *
 * <p>三个量与业态卡上的三列同源同筛选（见 {@code StoreManageMapper} 的 {@code *_WHERE} 常量），
 * 故一张卡内按单位把各行相加，等于卡片上那个单位那一行的数字。</p>
 *
 * <p>某产品只在退回源出现（本月没下单、没卖，但退了货）时该行照出，另两个量为 0
 * —— 卡片上有退回量、明细里却找不到对应产品，是甲方最容易当 bug 报的一类。
 * 三个量<b>全为 0</b> 的产品则整行不下发（甲方口径 D-0045，过滤在 SQL 外层，分页 total 同步收窄）。</p>
 *
 * <p>三个量各自带一组环比（对上一自然月同产品同口径，V6-R209），与业态卡上的比率同一套算法：
 * {@code *HasBase=false} 表示上月无基数，此时 {@code *Mom} 固定 0.00，前端渲染中性黑。</p>
 *
 * @author djs
 * @since V6-R180
 */
@Data
public class StoreManageDetailRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品 ID（snowflake，下发到 mp 是 string，前端不准 Number()）。 */
    private Long productId;

    /** 产品名称。 */
    private String productName;

    /** 产品规格（档案未填 → service 兜成 "—"）。 */
    private String productSpec;

    /** 计量单位（档案未填 → service 兜成「未设单位」，与业态卡行头一致）。 */
    private String unit;

    /** 需求量（门店下单量）。 */
    private BigDecimal demandQty;

    /** 销售量（盘点 sale_qty + gift_qty）。 */
    private BigDecimal saleQty;

    /** 退回量（门店退回仓库）。 */
    private BigDecimal returnQty;

    /** 需求量环比百分比（scale=2，12.50 = +12.50%）；上月无基数时固定 0.00。 */
    private BigDecimal demandMom;

    /** 需求量上月是否有可比基数（false → 前端渲染黑色 0.00%）。 */
    private Boolean demandHasBase;

    /** 销售量环比百分比。 */
    private BigDecimal saleMom;

    /** 销售量上月是否有可比基数。 */
    private Boolean saleHasBase;

    /** 退回量环比百分比。 */
    private BigDecimal returnMom;

    /** 退回量上月是否有可比基数。 */
    private Boolean returnHasBase;
}
