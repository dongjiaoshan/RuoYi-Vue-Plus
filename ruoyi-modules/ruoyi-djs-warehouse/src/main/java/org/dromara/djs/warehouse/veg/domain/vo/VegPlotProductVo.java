package org.dromara.djs.warehouse.veg.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 毛菜处理地块的「按产品」明细行 VO（V6 row17/row18）。
 *
 * <p>一行 = 该作物配置的一个产品。mp 用它做两件事：</p>
 * <ul>
 *   <li>采摘 / 处理录入弹窗的产品选择（只有一个 → 只展示不可改；两个及以上 → 下拉）；</li>
 *   <li>顶部信息框在「地块」下逐行列出产品名与它自己的剩余重量。</li>
 * </ul>
 *
 * @author djs
 */
@Data
public class VegPlotProductVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品 ID（= t_warehouse_product_info.id）。 */
    private Long productId;

    /** 产品名称。 */
    private String productName;

    /**
     * 该产品在本地块的剩余重量(kg)（V6 row102 口径 = 该 {@code (地块, 产品)} 在毛菜间 L0006 的实时库存）。
     *
     * <p>它同时是<b>处理录入的可出库上限</b> —— 服务端按同一份库存校验，两边同源才不会出现
     * 「页面显示还有 30kg、提交却说库存不足」。</p>
     */
    private BigDecimal remainWeight;

    /**
     * 是否可选来录入：{@code true} = 该产品仍在作物的产品配置里；
     * {@code false} = 已被移出配置、但地里还有没处理完的存量，只展示不给选。
     *
     * <p>没有这一位的话，被移出配置的产品要么在信息框里凭空消失（各产品之和对不上地块剩余），
     * 要么被当成可选项而实际提交会被服务端拒。</p>
     */
    private Boolean selectable;
}
