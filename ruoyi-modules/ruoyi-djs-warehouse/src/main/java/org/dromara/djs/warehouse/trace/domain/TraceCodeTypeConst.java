package org.dromara.djs.warehouse.trace.domain;

/**
 * 追溯码类型常量（TRC-CORE-001），对齐字典 {@code djs_trace_code_type} + DDL
 * {@code t_warehouse_trace_code.code_type VARCHAR(16)}。
 *
 * <p>3 业态（doc/11 §4.1 + DDL 实证）：</p>
 * <ul>
 *   <li>{@link #PORK}：猪肉链（{@code belong_type ∈ {pork, white_bar}}），走 {@code pig_ear_no} 链路，
 *       productCode2={@code PG}</li>
 *   <li>{@link #VEG}：果蔬链（{@code belong_type ∈ {vegetable, dry_good, egg}}），走地块 + 认证链路，
 *       productCode2={@code VG}</li>
 *   <li>{@link #GIFT}：礼盒（{@code belong_type = gift_box}），{@code gift_components} V1 写 NULL，
 *       productCode2={@code GF}</li>
 * </ul>
 *
 * @author djs
 * @since TRC-CORE-001
 */
public final class TraceCodeTypeConst {

    /** 猪肉链。 */
    public static final String PORK = "pork";

    /** 果蔬链。 */
    public static final String VEG = "veg";

    /** 礼盒。 */
    public static final String GIFT = "gift";

    /** 追溯码业务码占位符 {@code productCode2} 的值（猪肉）。 */
    public static final String PRODUCT_CODE_PORK = "PG";

    /** 追溯码业务码占位符 {@code productCode2} 的值（果蔬）。 */
    public static final String PRODUCT_CODE_VEG = "VG";

    /** 追溯码业务码占位符 {@code productCode2} 的值（礼盒）。 */
    public static final String PRODUCT_CODE_GIFT = "GF";

    private TraceCodeTypeConst() {
    }
}
