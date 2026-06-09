package org.dromara.djs.breed.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 年度繁殖与配种 + 产房仔猪质量指标 VO（FIX-MGMT-MP-BRD-001，原型"种猪 tab·年度繁殖与配种 / 年度产房与仔猪质量"）。
 *
 * <p>对应原型种猪 tab 的 ②年度繁殖与配种指标 + ③年度产房与仔猪质量两组 KPI。V1 数据量小，
 * 全部实时算（不依赖聚合表），底表 {@code t_farm_pig_breeding / farrow / weaning}。</p>
 *
 * <p>口径默认（D-FIX-8 _open-issues #7.1-7.5，Kevin 兽医可改）：</p>
 * <ul>
 *   <li>{@link #mateRate} 配种率 = 分娩窝数 / 配种次数（#7.1）</li>
 *   <li>{@link #farrowRate} 分娩率 = 有效分娩窝数 / 配种次数（#7.2，V1 有效分娩 = 分娩记录数）</li>
 *   <li>{@link #weanMateInterval} 断配间隔 = AVG(下次配种日 − 上次断奶日) 天（#7.3）</li>
 *   <li>{@link #avgNonProductiveDays} 平均非生产天数 NPD（#7.4，标准式：(妊娠期外的天数)，V1 近似见 service）</li>
 *   <li>{@link #farrowingLossRate} 产房损失率 = (分娩活仔 − 断奶数) / 分娩活仔（#7.5）</li>
 * </ul>
 *
 * <p>所有比率字段为 BigDecimal（4 位小数，0~1 区间，前端乘 100 显示百分比）。</p>
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

    /** 配种率 = 分娩窝数 / 配种次数（0~1，4 位小数）。 */
    private BigDecimal mateRate;

    /** 分娩率 = 有效分娩窝数 / 配种次数（0~1，4 位小数）。 */
    private BigDecimal farrowRate;

    /** 断配间隔（天）= AVG(下次配种日 − 上次断奶日)，1 位小数。 */
    private BigDecimal weanMateInterval;

    /** 平均非生产天数 NPD（天，1 位小数）。 */
    private BigDecimal avgNonProductiveDays;

    // ---- ③年度产房与仔猪质量 ----

    /** 年度分娩活仔总数（头）。 */
    private BigDecimal totalLiveBorn;

    /** 窝均活仔数 = 年度活仔总数 / 分娩窝数（头/窝，2 位小数）。 */
    private BigDecimal avgLiveBornPerLitter;

    /** 窝均断奶数 = 年度断奶总数 / 断奶窝数（头/窝，2 位小数）。 */
    private BigDecimal avgWeanedPerLitter;

    /** 产房损失率 = (分娩活仔 − 断奶数) / 分娩活仔（0~1，4 位小数）。 */
    private BigDecimal farrowingLossRate;
}
