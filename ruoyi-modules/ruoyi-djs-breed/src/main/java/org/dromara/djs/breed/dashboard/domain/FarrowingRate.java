package org.dromara.djs.breed.dashboard.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 同期配种分娩记录实体（BRD-STAT-FARROWRATE-001，表 {@code t_farm_farrowing_rate}）。
 *
 * <p>一条配种记录一行（{@code tenant_id + breeding_id} 唯一），把「这一窝后来怎么样了」摊平成
 * 三个结局日期列，供甲方直接查表对账；同时是 mp 月/年分娩率的取数来源（row230 / row231），
 * 取代原先从 {@code t_farm_monthly_production} / {@code t_farm_year_production} 读落盘率值的做法。
 * ⚠️ admin「配种批次对账」页不走本表，它按配种月自行归集（D-0072 明确保留）。</p>
 *
 * <p><b>分娩率口径</b>（甲方 2026-09-18 拍板）：</p>
 * <ul>
 *   <li>分母 = {@link #expectedFarrowDate} <b>已到</b>（≤ 收口日 T-1）且落在该月/该年的记录数（D-0090）</li>
 *   <li>分子 = {@link #farrowDate} 非空 <b>且</b> {@code farrowDate ≤ expectedFarrowDate} 的记录数（D-0091，晚产窝不算）</li>
 * </ul>
 *
 * <p>这两条与原 cohort 口径逐字等价（{@code 分娩日 ≤ 配种日+judgeDays ⟺ DATEDIFF ≤ judgeDays}），
 * 故本表只换数据源、不改数字 —— staging 实测分子 12 / 晚产窝 0 与 cohort 完全一致。</p>
 *
 * <p>由 {@link org.dromara.djs.breed.dashboard.job.DashboardAggregateJob} 每日按
 * 配种 → 分娩 → 返空流 → 死淘 四步刷新近一个月；历史数据由迁移
 * {@code V202609181000__BRD-STAT-FARROWRATE-001} 全量初始化一次。</p>
 *
 * @author djs
 * @since BRD-STAT-FARROWRATE-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_farrowing_rate")
public class FarrowingRate extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 猪只ID（{@code t_farm_pig_info.id}）。 */
    private Long pigId;

    /** 母猪耳号（冗余自 {@code t_farm_pig_info.ear_tag}，便于甲方直接查表对账；猪只已删则为空）。 */
    private String earTag;

    /** 配种ID（{@code t_farm_pig_breeding.id}）；与 tenant_id 组成唯一键，一条配种一行。 */
    private Long breedingId;

    /** 配种日期（取自配种记录表，只留日期部分）。 */
    private LocalDate breedingDate;

    /**
     * 预估分娩日 = 配种日 + 判定节点天数（{@code sow_farrow_judge_deadline_days}，缺省 119）。
     *
     * <p>⚠️ <b>落盘快照</b>：每日任务只刷近一个月，配置日后若改，更早的历史行不会自动跟着变。
     * 聚合时检测到配置漂移会 {@code log.warn} 提示重跑全量初始化，不静默纠偏。</p>
     */
    private LocalDate expectedFarrowDate;

    /** 分娩日期（按配种ID查分娩记录表，同一配种多条取最早；查不到留空）。 */
    private LocalDate farrowDate;

    /** 返空流日期（按配种ID查返空流记录表，含返情R/空怀N/流产A，多条取最早；查不到留空）。 */
    private LocalDate abnormalDate;

    /**
     * 死淘日期（按<b>猪只ID</b>查 {@code t_farm_status_record} 的 DIE/ELIMINATE，多条取最早；查不到留空）。
     *
     * <p>甲方原文就是「查询猪只ID」：一头猪只死一次，她名下每条配种记录都会写上同一个死淘日期。
     * 本列不参与分娩率计算，只供对账。</p>
     */
    private LocalDate cullDate;

    /** 软删标志（业务表必含）。 */
    @TableLogic
    private String delFlag;
}
