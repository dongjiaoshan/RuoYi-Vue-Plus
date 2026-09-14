package org.dromara.djs.breed.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 超期未定性母猪单行（BRD-STAT-COHORT-001）。
 *
 * <p>甲方口径：「超过判定节点必须要系统内给这个猪定性是分娩了还是没分娩」。
 * 本清单 = 配种已过判定日、却既无分娩记录也无返空流记录、且仍在群的母猪 —— 要现场去补录。
 * 不定性它们就一直挂在分娩率分母里无处归。</p>
 *
 * @author djs
 * @since BRD-STAT-COHORT-001
 */
@Data
public class OverdueUndecidedVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 配种记录 id。 */
    private Long breedingId;

    /** 母猪耳号。 */
    private String earNo;

    /** 配种日期。 */
    private LocalDate breedingDate;

    /** 判定日（配种日 + 判定节点天数）。 */
    private LocalDate deadline;

    /** 已超期天数。 */
    private Integer overdueDays;

    /** 配种时胎次。 */
    private Integer parity;

    /** 栋舍名称。 */
    private String barnName;

    /** 栏位名称。 */
    private String penName;

    /** 母猪当前状态码。 */
    private String currentStatus;
}
