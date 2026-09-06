package org.dromara.djs.warehouse.boardstat.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 「品类 × 单位 → 量」聚合投影行（mp 仓库统计三指标共用）。
 *
 * <p>三个指标（入库量 / 生产量 / 原材料消耗量）SQL 形状不同但输出同构，故共用一个投影：
 * service 拿到三份行集后按 belongType 归到 4 张卡、按 productUnit 归到卡内行。</p>
 *
 * @author djs
 */
@Data
public class CategoryUnitQtyRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品品类（字典 djs_belong_type 原值：pork / white_bar / vegetable / egg / dry_good）。 */
    private String belongType;

    /** 计量单位（产品档案 product_unit 原文，空值已在 SQL 里归一成空串）。 */
    private String productUnit;

    /** 该品类该单位下的量。 */
    private BigDecimal qty;
}
