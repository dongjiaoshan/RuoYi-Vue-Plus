package org.dromara.djs.breed.production.domain;

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
import java.time.LocalDate;

/**
 * 母猪生产指标实体（BRD-LIST-001 详情 tab2 数据源；表 {@code t_farm_sow_performance}）。
 *
 * <p>该表数据由 {@code BRD-DASH-001} 定时任务汇总写入（每次分娩 / 断奶事件回写或夜间批处理）；
 * 本 ticket 只 SELECT 不写入。每只母猪在每个胎次有一行（{@code pig_id + parity} 唯一）。</p>
 *
 * <p>字段映射 DB（参考 D5 SYS-INIT 建表脚本）：</p>
 * <ul>
 *   <li>{@code pig_id} → {@link #pigId}</li>
 *   <li>{@code parity} → {@link #parity}</li>
 *   <li>{@code total_born} 总产仔 / {@code total_live_born} 健仔 / {@code total_weaned} 断奶头数</li>
 *   <li>{@code avg_born_weight} 平均出生重 / {@code avg_weaned_weight} 平均断奶重</li>
 *   <li>{@code last_update_date} 最近回写日期</li>
 * </ul>
 *
 * @author djs
 * @since BRD-LIST-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_sow_performance")
public class SowPerformance extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private Long pigId;
    private String earNo;
    private Integer parity;
    private Integer totalBorn;
    private Integer totalLiveBorn;
    private Integer totalWeaned;
    // 无源数据时这些指标置 null（不瞎编）；定时任务每次重算用 updateById，
    // 必须 FieldStrategy.ALWAYS 才能把旧值覆盖成 null，否则 MP 默认跳过 null 字段（旧脏值残留）。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal avgBornWeight;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal avgWeanedWeight;
    private LocalDate lastUpdateDate;

    // ---- row11 指标算法列（BRD-STAT-001 扩列；可空 → 同样 ALWAYS） ----
    /** 平均怀孕天数（配种到分娩天数之和/状态变化次数）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal avgGestationDays;
    /** 断奶-配种天数（断奶到配种天数之和/状态变化次数）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal weanBreedDays;
    /** 返空流总次数。 */
    private Integer abnormalTotal;
    /** 窝均产仔数（总产仔之和/胎次）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal avgBornPerLitter;
    /** 窝均活仔数（活产之和/胎次）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal avgLiveBornPerLitter;
    /** 窝均断奶数（断奶头数之和/胎次）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal avgWeanedPerLitter;
    /** NPD（365−配种至分娩天数−分娩至断奶天数）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal npd;

    /** 软删标志（BRD-DASH-001 ALTER 补字段）。 */
    @TableLogic
    private String delFlag;
    /** 软删唯一标识。 */
    private Long delUnique;
}
