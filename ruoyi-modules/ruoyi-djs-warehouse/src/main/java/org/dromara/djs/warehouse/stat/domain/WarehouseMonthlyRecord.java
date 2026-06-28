package org.dromara.djs.warehouse.stat.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 仓库月数据记录实体（WMS-STAT-001，表 {@code t_warehouse_monthly_record}，邓博 admin row18）。
 *
 * <p>每个 tenant_id + stat_month（yyyy-MM）一行；由
 * {@link org.dromara.djs.warehouse.stat.job.WarehouseStatJob} 在日表落盘后汇总当月日表
 * （当月每日刷新、历史月固定）。admin「仓库月报表」只读列表读本表。</p>
 *
 * <p>4 个率/头数指标全部从当月已落盘日表 Σ 回读（口径：屠宰率/出品率用 Σ 分子÷Σ 分母，
 * 不是日率再平均）。率类分母≤0 落 NULL + ALWAYS。</p>
 *
 * @author djs
 * @since WMS-STAT-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_monthly_record")
public class WarehouseMonthlyRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 统计月份 yyyy-MM。 */
    private String statMonth;

    /** 屠宰头数（当月日屠宰头数之和）。 */
    private Integer slaughterCount;
    /** 屠宰率%（当月日接收重量之和/当月日送宰总重之和×100；分母 0 → null）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal slaughterRate;
    /** 白条出品率%（当月日白条总重之和/当月日送宰总重之和×100；分母 0 → null）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal barYieldRate;
    /** 分割出品率%（当月日分割产品总重之和/当月日分割白条总重之和×100；分母 0 → null）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal cutYieldRate;

    /** 软删标志（业务表必含）。 */
    @TableLogic
    private String delFlag;
    /** 软删唯一标识。 */
    private Long delUnique;
}
