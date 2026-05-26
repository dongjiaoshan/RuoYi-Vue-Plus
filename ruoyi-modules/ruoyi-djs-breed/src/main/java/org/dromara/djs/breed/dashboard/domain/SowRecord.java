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
 * 母猪日数据汇总实体（BRD-DASH-001，表 {@code t_farm_sow_record}）。
 *
 * <p>每个 tenant_id + stat_date 一行；由 {@link org.dromara.djs.breed.dashboard.job.DashboardAggregateJob}
 * 每天 00:30 跑批写入；UNIQUE(tenant_id, stat_date, del_unique) 由 BRD-DASH-001 migration 添加。</p>
 *
 * @author djs
 * @since BRD-DASH-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_sow_record")
public class SowRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 统计日期（与定时任务跑批日期对齐，T-1 = 昨日）。 */
    private LocalDate statDate;

    /** 母猪总数（当日 23:59 时点：current_status NOT IN ('END') 且 pig_type='sow'）。 */
    private Integer sowTotal;
    /** 妊娠中（current_status='PZ'/'PH'）。 */
    private Integer sowPregnant;
    /** 哺乳中（current_status='FM'）。 */
    private Integer sowFarrow;
    /** 断奶（current_status='DN'）。 */
    private Integer sowWeaning;
    /** 空怀（current_status='KH'/'LC'/'FQ'/'HB' for sow）。 */
    private Integer sowIdle;
    /** 当日淘汰头数（status_record event_type='ELIMINATE'）。 */
    private Integer sowCullingCount;
    /** 当日死亡头数（status_record event_type='DIE'）。 */
    private Integer sowDeathCount;
    /** 仔猪总数（当日 23:59 时点：current_status NOT IN ('END') 且 pig_type='piglet'）。 */
    private Integer pigletTotal;

    /** 软删标志（业务表必含）。 */
    @TableLogic
    private String delFlag;
    /** 软删唯一标识。 */
    private Long delUnique;
}
