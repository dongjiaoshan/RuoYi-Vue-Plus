package org.dromara.djs.breed.event.heat.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 母猪查情记录实体（BRD-EVENT-002 OESTRUS）。
 *
 * <p>对应表 {@code t_farm_pig_heat}（SYS-INIT-001 DDL）。状态机处理（见 ADR-0010）：</p>
 * <ul>
 *   <li>{@code isPregnantConfirmed=1} → fireEvent(OESTRUS)，状态保持 PZ（仅落审计记录，不切态）；</li>
 *   <li>{@code isPregnantConfirmed=0} → 仅写记录，不调状态机，pig 状态保持 PZ。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_pig_heat")
public class PigHeat extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private Long pigId;
    private String earNo;

    /** 操作当时猪只日龄(事件日-出生日/引种日,落库冻结快照,ADR-0017) */
    private Integer ageDays;

    private LocalDateTime heatDate;

    /** 查情结果（字典 djs_heat_result：例 1=有发情迹象 2=无 3=确认妊娠）。 */
    private String heatResult;

    /** 是否兽医确认妊娠（0/1）；true 时触发 OESTRUS 事件（状态仍保持 PZ，仅落审计）。 */
    private Integer isPregnantConfirmed;

    private Long operatorId;

    @TableLogic
    private String delFlag;

    private String remark;
}
