package org.dromara.djs.warehouse.common;

import org.dromara.common.core.utils.StringUtils;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 数量单位口径：哪些单位只能填整数、小数位到几位（V6 row141 / row143）。
 *
 * <p>甲方 row141 原话是「如果单位是非KG，则输入为整数」，字面执行会把 吨 / 升 / 斤 / 米 /
 * 平方米 / 亩 这些**计量**单位一起锁成整数，而 2.5 吨是合法业务值。Kevin 2026-08-28 拍板：
 * **只有计数类单位才强制整数**。</p>
 *
 * <p>名单取自库里真实在用的单位 + 几个显然的同类。计量类（可小数）= kg / 公斤 / 吨 / 升 /
 * 斤 / 米 / 平方米 / 亩；其余落在本名单里的都是计数类。</p>
 *
 * <p>⚠️ <b>不在名单里的未知单位一律按「可小数」处理</b>，不是按整数：拦错了会让人根本录不进数
 * （挡住干活），放过了只是多个小数位（数据略怪但不阻塞）。</p>
 *
 * <p>⚠️ 前端 {@code plus-ui/src/utils/weight.ts#isCountingUnit} 是同一份名单，改这里必须同步改那边，
 * 否则会出现「前端让填、后端报错」。本类是后端**唯一**一份，采购入库与产品出库都从这里取，
 * 别再在别处拷第三份。</p>
 *
 * @author djs
 */
public final class QuantityUnitRule {

    /** 业务量小数位上限：库存 / 流水的数量列都是 {@code DECIMAL(12,3)}。 */
    public static final int MAX_SCALE = 3;

    private static final Set<String> COUNTING_UNITS = Set.of(
        "份", "瓶", "袋", "盒", "个", "桶", "罐", "卷", "张", "包",
        "件", "枚", "捆", "株", "只", "根", "支", "台", "盏", "条",
        "套", "片", "双", "箱", "组", "把", "头", "提");

    private QuantityUnitRule() {
    }

    /** 是否计数类单位（只能填整数）。空 / 未知单位返 false = 按可小数处理。 */
    public static boolean isCountingUnit(String unit) {
        return StringUtils.isNotBlank(unit) && COUNTING_UNITS.contains(unit.trim());
    }

    /**
     * 该数量在该单位下是不是「填错了小数」。
     *
     * <p>{@code stripTrailingZeros()} 先归一：{@code 3.000} 与 {@code 3} 都算整数，
     * {@code 1e2} 也不会被当成负 scale 误判。</p>
     */
    public static boolean isNonIntegerForCountingUnit(BigDecimal quantity, String unit) {
        return quantity != null && isCountingUnit(unit) && quantity.stripTrailingZeros().scale() > 0;
    }
}
