package org.dromara.djs.breed.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 猪只状态机 UI 事件枚举（BRD-CORE-001）。
 *
 * <p>与字典 {@code djs_pig_status_event} 严格对齐（11 枚举）。这是 UI 层 / 子 ticket
 * 业务接口对外暴露的"事件名"；状态机内部把 OESTRUS（保持 PZ）/ NULL_RETURN（按 payload
 * 分流到 LC/KH/FQ）做不同处理，不对外暴露 CONFIRM_PREG 等内部 trigger。</p>
 *
 * @author djs
 * @since BRD-CORE-001
 */
@Getter
@AllArgsConstructor
public enum PigStatusEvent {

    /** 引种：创建猪只行（不走 fireEvent，由 createPig 路径处理）。 */
    INTRO("引种"),
    /** 配种：HB/DN/LC/KH/FQ → PZ。 */
    BREED("配种"),
    /** 分娩：PZ → FM，胎次 +1。 */
    FARROW("分娩"),
    /** 断奶：FM → DN。 */
    WEAN("断奶"),
    /** 查情：仅 PZ，状态不变（isPregnantConfirmed 仅作业务标记落 heat 记录）。 */
    OESTRUS("查情"),
    /** 返空：仅 PZ，按 payload.abnormalType 分流 → LC / KH / FQ。 */
    NULL_RETURN("返空"),
    /** 死亡：(非 END) → END，end_reason=DEAD。 */
    DIE("死亡"),
    /** 淘汰：(非 END) → END，end_reason=CULL。 */
    ELIMINATE("淘汰"),
    /** 阉割：仅 BOAR_ACTIVE / pig_sex='M'，状态不变。 */
    CASTRATE("阉割"),
    /** 转移：(非 END)，状态不变，更新 barn_id / pen_id。 */
    TRANSFER("转移"),
    /** 出栏：(非 END) → END，end_reason=MARKET，触发燎毛跨域事件。 */
    SLAUGHTER("出栏");

    private final String label;
}
