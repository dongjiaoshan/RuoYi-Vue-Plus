package org.dromara.djs.breed.production.domain;

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
    private BigDecimal avgBornWeight;
    private BigDecimal avgWeanedWeight;
    private LocalDate lastUpdateDate;

    /** 软删标志（BRD-DASH-001 ALTER 补字段）。 */
    @TableLogic
    private String delFlag;
    /** 软删唯一标识。 */
    private Long delUnique;
}
