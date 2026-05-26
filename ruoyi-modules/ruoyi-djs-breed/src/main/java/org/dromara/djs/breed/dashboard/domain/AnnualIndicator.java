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
 * 年度指标实体（BRD-DASH-001，表 {@code t_farm_annual_indicator}）。
 *
 * <p>每个 tenant_id + stat_year 一行；由聚合 job 每天 00:30 重算当年。
 * 用于 admin dashboard 顶部"年度指标"区域。</p>
 *
 * @author djs
 * @since BRD-DASH-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_annual_indicator")
public class AnnualIndicator extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 统计年份。 */
    private Short statYear;

    private Integer introduceCount;
    private Integer bornCount;
    private Integer weanedCount;
    private Integer deathCount;
    private Integer cullingCount;
    private Integer marketingCount;
    private BigDecimal marketingWeight;

    /** PSY = 年度断奶头数 / 当年平均母猪存栏（4 位小数，前端 % 显示）。 */
    private BigDecimal psy;
    /** 死亡率（DEATH / (DEATH + ALIVE_END_OF_YEAR)，4 位小数）。 */
    private BigDecimal mortalityRate;

    @TableLogic
    private String delFlag;
    private Long delUnique;
}
