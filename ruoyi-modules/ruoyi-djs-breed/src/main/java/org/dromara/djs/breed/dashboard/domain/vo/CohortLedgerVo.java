package org.dromara.djs.breed.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 配种批次去向台账单行（BRD-STAT-COHORT-001）。
 *
 * <p>回答甲方的对账问题：「这批一共配种多少头，中间损失多少，分娩率为什么是这个数据」。
 * 一行 = 一个配种月，各去向桶互斥且合计 = {@link #bred}。</p>
 *
 * <p>{@link #pending} 与 {@link #undecided} 是两回事：前者判定日还没到、结局未定，属正常在途；
 * 后者已过判定日却查不到任何分娩/返空流记录，需要现场补录定性，是待办。</p>
 *
 * @author djs
 * @since BRD-STAT-COHORT-001
 */
@Data
public class CohortLedgerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 配种月（yyyy-MM）。 */
    private String breedMonth;

    /** 本月配种头数（= 各去向桶之和）。 */
    private Integer bred;

    /** 其中已过判定日的头数（分娩率的分母）。 */
    private Integer matured;

    /** 已到期且在判定节点内分娩（分娩率的分子）。 */
    private Integer farrow;

    /** 已到期且超判定节点才分娩：判定时算「未分娩」，不进分子，单列便于追查。 */
    private Integer farrowLate;

    /** 返情头数。 */
    private Integer returnCount;

    /** 空怀头数。 */
    private Integer emptyCount;

    /** 流产头数。 */
    private Integer abortCount;

    /** 配种后离群（淘汰 / 死亡）头数。 */
    private Integer goneCount;

    /** 已到期但无任何结局记录，待现场定性。 */
    private Integer undecided;

    /** 判定日未到、结局未定（正常在途）。 */
    private Integer pending;

    /** 本月最早判定日。 */
    private LocalDate firstDeadline;

    /** 本月最晚判定日。 */
    private LocalDate lastDeadline;

    /** 分娩率%（farrow / matured × 100；matured=0 时为 0）。 */
    private BigDecimal farrowRate;
}
