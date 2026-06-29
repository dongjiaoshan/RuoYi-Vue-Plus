package org.dromara.djs.breed.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 年度指标 VO（BRD-DASH-001）。
 *
 * <p>admin dashboard 顶部"年度指标"区域。psy / mortalityRate 字段 4 位小数；fe 按 % 显示 2 位。</p>
 *
 * @author djs
 * @since BRD-DASH-001
 */
@Data
public class AnnualIndicatorVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Short statYear;
    private Integer introduceCount;
    private Integer bornCount;
    private Integer weanedCount;
    private Integer deathCount;
    private Integer cullingCount;
    private Integer marketingCount;
    private BigDecimal marketingWeight;

    /** PSY 每头母猪年产断奶仔数（4 位小数）。 */
    private BigDecimal psy;
    /** 死亡率（DEATH / (DEATH + ALIVE)，4 位小数）。 */
    private BigDecimal mortalityRate;
    /** 肥猪死亡数（当年 Σ日 death_fattening_count）。 */
    private Integer totalFatteningDeath;
}
