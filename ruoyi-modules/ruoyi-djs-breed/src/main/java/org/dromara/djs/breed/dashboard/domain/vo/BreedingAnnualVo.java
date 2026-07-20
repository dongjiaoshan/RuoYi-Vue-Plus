package org.dromara.djs.breed.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 年度繁殖与配种 + 产房仔猪质量指标 VO（FIX-MGMT-MP-BRD-001，原型"种猪 tab·年度繁殖与配种 / 年度产房与仔猪质量"）。
 *
 * <p>对应原型种猪 tab 的 ②年度繁殖与配种指标 + ③年度产房与仔猪质量两组 KPI。数据取年表
 * {@code t_farm_year_production}（聚合 job 每日 00:30 重算落盘）为权威源，取代旧版 live 实时算
 * （旧算法率类有跨月窗口伪影、NPD 摊算过大等偏差，甲方口径以年表为准）。</p>
 *
 * <p>字段 ← 年表列映射：</p>
 * <ul>
 *   <li>{@link #mateRate} 配种率 / {@link #farrowRate} 分娩率 ← {@code year_farrow_rate}
 *       （V1 有效分娩 = 分娩记录，配种率与分娩率同口径）</li>
 *   <li>{@link #weanMateInterval} 断配间隔 ← {@code wean_breed_interval}（天）</li>
 *   <li>{@link #avgNonProductiveDays} 平均非生产天数 NPD ← {@code avg_npd_days}（天）</li>
 *   <li>{@link #totalBornCount} 总产仔数 ← {@code total_born_count} / {@link #totalLiveBorn} 总活仔 ← {@code total_live_born}</li>
 *   <li>{@link #farrowingLossRate} 产房损失率 ← {@code farrow_loss_rate}</li>
 * </ul>
 *
 * <p>率类字段（配种率 / 分娩率 / 产房损失率）为年表已 ×100 的百分比数值（如 55.56），前端直接拼 "%"。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-BRD-001
 */
@Data
public class BreedingAnnualVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计年份。 */
    private Integer year;

    // ---- ②年度繁殖与配种 ----

    /** 配种率（年表 year_farrow_rate，百分比数值如 55.56；V1 与分娩率同口径）。 */
    private BigDecimal mateRate;

    /** 分娩率（年表 year_farrow_rate，百分比数值如 55.56）。 */
    private BigDecimal farrowRate;

    /** 断配间隔（天）= AVG(下次配种日 − 上次断奶日)，1 位小数。 */
    private BigDecimal weanMateInterval;

    /** 平均非生产天数 NPD（天，1 位小数）。 */
    private BigDecimal avgNonProductiveDays;

    // ---- ③年度产房与仔猪质量 ----

    /** 年度总产仔数（头，含死胎；年表 total_born_count）。 */
    private BigDecimal totalBornCount;

    /** 年度分娩活仔总数（头；年表 total_live_born）。 */
    private BigDecimal totalLiveBorn;

    /** 窝均活仔数 = 年度活仔总数 / 分娩窝数（头/窝，2 位小数）。 */
    private BigDecimal avgLiveBornPerLitter;

    /** 窝均断奶数 = 年度断奶总数 / 断奶窝数（头/窝，2 位小数）。 */
    private BigDecimal avgWeanedPerLitter;

    /** 产房损失率（年表 farrow_loss_rate，百分比数值如 9.52）。 */
    private BigDecimal farrowingLossRate;
}
