package org.dromara.djs.breed.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 猪只生命周期状态枚举（BRD-CORE-001）。
 *
 * <p>与字典 {@code djs_pig_lifecycle} 严格对齐（9 枚举，见 ADR-0010）。{@link #END} 为终态，
 * 进入后任何 {@code fireEvent} 调用都会被拒绝，由 {@code pig.end_reason} 字段
 * 区分 DEAD / CULL / MARKET 三种结局。</p>
 *
 * <p>{@link #BOAR_ACTIVE} 是公猪唯一活跃态，CASTRATE 事件不改变状态（只写阉割记录表）。</p>
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Getter
@AllArgsConstructor
public enum PigLifecycle {

    /** 后备：母猪未配种或公猪未启用；piglet/fattening 也默认此态。 */
    HB("后备"),
    /** 配种：母猪已配种，等待分娩（查情确认妊娠仅落记录不切态）。 */
    PZ("配种"),
    /** 分娩：已分娩，正在哺乳。 */
    FM("分娩"),
    /** 断奶：已断奶，等待再次配种。 */
    DN("断奶"),
    /** 流产：妊娠期流产。 */
    LC("流产"),
    /** 空怀：配种失败（无妊娠）。 */
    KH("空怀"),
    /** 返情：配种后返情。 */
    FQ("返情"),
    /** 终止：DIE / ELIMINATE / SLAUGHTER 都进此态，end_reason 区分。 */
    END("终止"),
    /** 公猪在产：公猪唯一活跃态。 */
    BOAR_ACTIVE("公猪在产");

    private final String label;

    /** 终态判定（END 后不允许再 fireEvent）。 */
    public boolean isTerminal() {
        return this == END;
    }
}
