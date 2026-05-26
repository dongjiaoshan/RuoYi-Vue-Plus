package org.dromara.djs.breed.dashboard.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 养殖月指标统计实体（BRD-DASH-001，表 {@code t_farm_monthly_production}）。
 *
 * <p>每个 tenant_id + stat_month (CHAR(7) YYYY-MM) 一行；
 * 由 {@link org.dromara.djs.breed.dashboard.job.DashboardAggregateJob} 每天 00:30 重算当月（INSERT ON DUPLICATE KEY UPDATE）。
 * 月度对比（当月 vs 上月）的数据源。</p>
 *
 * <p><b>颜色规则（注意国内畜牧惯例反直觉）</b>：
 * 当月数值"更好" → 红色 ("better")；
 * 当月数值"更差" → 绿色 ("worse")；
 * 持平 → 黑色 ("flat")。
 * 颜色映射在 VO 层做，不持久化。</p>
 *
 * @author djs
 * @since BRD-DASH-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_monthly_production")
public class MonthlyProduction extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 统计月份（CHAR(7) YYYY-MM）。 */
    private String statMonth;

    /** 月度引种头数（t_farm_pig_introduce COUNT）。 */
    private Integer introduceCount;
    /** 月度活产仔（t_farm_pig_farrow SUM(live_born)）。 */
    private Integer bornCount;
    /** 月度断奶头数（t_farm_pig_weaning SUM(weaned_count)）。 */
    private Integer weanedCount;
    /** 月度死亡头数（status_record event_type='DIE'）。 */
    private Integer deathCount;
    /** 月度淘汰头数（status_record event_type='ELIMINATE'）。 */
    private Integer cullingCount;
    /** 月度出栏头数（status_record event_type='SLAUGHTER' or pig_marketing COUNT）。 */
    private Integer marketingCount;
    /** 月度出栏总重（kg）。 */
    private BigDecimal marketingWeight;

    @TableLogic
    private String delFlag;
    private Long delUnique;
}
