package org.dromara.djs.breed.production.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;
import org.dromara.djs.common.base.DjsBaseServiceImpl;

import java.io.Serial;

/**
 * 生产周期配置实体（BRD-MD-003 Tab1）。
 *
 * <p>对应表 {@code t_farm_production_cycle_config}。客户可调整的"生产建议周期"数字，
 * 给状态机 + 列表过滤 / 提醒优先级用（v1.2 客户决策：<b>无定时任务</b>，配置只决定"建议时间"，
 * 状态转换全由小程序业务事件触发）。</p>
 *
 * <p>6 个种子默认值（DDL seed）：</p>
 * <ul>
 *   <li>{@code gestation_days=114}（妊娠天数）</li>
 *   <li>{@code lactation_days=28}（哺乳天数）</li>
 *   <li>{@code nursery_days=35}（保育天数）</li>
 *   <li>{@code fattening_days=120}（育肥天数）</li>
 *   <li>{@code oestrus_cycle_days=21}（发情周期）</li>
 *   <li>{@code weaning_to_breeding_days=7}（断奶到配种）</li>
 * </ul>
 *
 * <p>读取契约（给 BRD-CORE-001 状态机调用）：<code>cycleConfigService.getValue(key)</code> 优先
 * 返回 {@code custom_value}（客户改后填这里），无则返回 {@code default_value}。</p>
 *
 * <p>软删走 {@link DjsBaseServiceImpl#softDelete}，UNIQUE(tenant_id, config_key, del_unique)。</p>
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_production_cycle_config")
public class ProductionCycleConfig extends TenantEntity implements DjsBaseServiceImpl.SoftDeletable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 业务键（如 {@code gestation_days} / {@code lactation_days}），与 DDL seed 6 行对齐。
     */
    private String configKey;

    /**
     * 业内默认值（天）；seed 时填入，admin 不可改（display only）。
     */
    private Integer defaultValue;

    /**
     * 客户自定义值（天）；null 表示沿用 {@link #defaultValue}。
     */
    private Integer customValue;

    /**
     * 单位（统一为"天"，预留字段）。
     */
    private String unit;

    /**
     * 业务含义说明（给 admin 显示）。
     */
    private String description;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记（'0' 未删 / '1' 已删）。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列（参考 BRD-MD-001 范式）。
     */
    private Long delUnique;

}
