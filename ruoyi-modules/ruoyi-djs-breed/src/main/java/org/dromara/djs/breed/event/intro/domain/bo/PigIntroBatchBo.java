package org.dromara.djs.breed.event.intro.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 批量引种入参（BRD-EVENT-001）。
 *
 * <p>批量引种约束：{@code pigSex} 统一（同一批同性别）；{@code pigCount} 决定生成 N 个连续耳号 +
 * N 头猪只。整批一个事务，任一头失败全回滚。</p>
 *
 * @author djs
 * @since BRD-EVENT-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PigIntroBatchBo extends PigIntroBo {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 批量引种头数（≥ 1）。下限放开到 1：mp 外部引种单头也走本端点，以消费"用户可改首耳号"
     * （{@code startEarNo} 仅本 BO 有，单头 {@code POST /intro} 端点纯后端生成耳号）。
     * service 层 count=1 是 count=N 的退化形态（连号分配 1 个 / 容量校验 remaining≥1 / pen 原子加 1），无副作用。
     * 上限 200 是为了避免单事务过大触发 MySQL row lock / undo log 压力。
     */
    @NotNull(message = "intro.count.required")
    @Min(value = 1, message = "intro.count.batch_min")
    @Max(value = 200, message = "intro.count.batch_max")
    private Integer pigCount;

    /**
     * 起始耳号（外部引种"用户可改"，601-5 / ADR-0011 §2.6）。
     *
     * <p>空 → 后端按权威格式（品系2-品种2-出生yyMMdd6-序号3，6/15 起不再编码性别）当天级 max+1 生成，整批连号；
     * 非空 → 校格式 + 提交时逐头 {@code existsEarNo} 探测 UNIQUE，以用户首号为起点连号。</p>
     *
     * <p>regex 用 {@code ^(\d{1,2}-\d{2}-\d{6}-\d{3})?$} —— **允许留空**（留空走上面"后端生成"分支，对齐 601-5
     * 设计 + allocateEarNos isBlank 分支）；非空才校 {@code 02-01-260609-001} 格式（与 {@link EarNoAllocator} 输出
     * 同口径；品系位宽 1~2 兼容历史 1 位数据 + 当前 2 位字典码）。message 套 {@code {}} 走 i18n 解析返中文，
     * 否则前端直显原始 key。注意 @Pattern 在入参绑定阶段先触发，与 service 端 {@code allocateFromUserStart}
     * 的 matches 校验必须同口径，否则带 {@code -} 值先在此被拦 422。</p>
     */
    @Pattern(regexp = "^(\\d{1,2}-\\d{2}-\\d{6}-\\d{3})?$", message = "{intro.start_ear_no.pattern}")
    private String startEarNo;
}
