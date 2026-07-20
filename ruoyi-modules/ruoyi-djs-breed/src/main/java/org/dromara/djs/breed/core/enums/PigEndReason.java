package org.dromara.djs.breed.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 猪只终止原因枚举（BRD-CORE-001）。
 *
 * <p>当 lifecycle 进入 {@link PigLifecycle#END} 时，{@code pig.end_reason} 字段
 * 取本枚举之一以区分三种终结路径。</p>
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Getter
@AllArgsConstructor
public enum PigEndReason {

    /** 死亡：DIE 事件触发。 */
    DEAD("死亡"),
    /** 淘汰：ELIMINATE 事件触发。 */
    CULL("淘汰"),
    /** 出栏：SLAUGHTER 事件触发（同时触发燎毛跨域 Spring event）。 */
    MARKET("出栏");

    private final String label;
}
