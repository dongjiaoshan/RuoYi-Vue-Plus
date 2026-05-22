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
 * 药品 / 疫苗周期配置实体（BRD-MD-003 Tab3）。
 *
 * <p>对应表 {@code t_farm_med_schedule_config}。给 BRD-MED-002 自动产生"待打疫苗 / 待用药"
 * 任务时查询用，配置只决定"建议时机"，不会自动 trigger（v1.2 无定时任务）。</p>
 *
 * <p>例：仔猪出生后 7 天打三联苗 → {@code med_type='vaccine', event_trigger='birth', days_offset=7}。</p>
 *
 * @author djs
 * @since BRD-MD-003
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_farm_med_schedule_config")
public class MedScheduleConfig extends TenantEntity implements DjsBaseServiceImpl.SoftDeletable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 药品类型（字典 {@code djs_med_type}：vaccine / antibiotic / nutrition / disinfectant / other）。
     */
    private String medType;

    /**
     * 触发时机（业务事件枚举：birth / weaning / fattening_start / mating / pregnant 等）。
     * 由 BRD-MED-002 自动建任务时按事件查询。
     */
    private String eventTrigger;

    /**
     * 触发时机 +/- N 天偏移（正数 = 事件后，负数 = 事件前）。
     */
    private Integer daysOffset;

    /**
     * 业务含义说明（给 admin 显示，例："仔猪出生后 7 天打三联苗"）。
     */
    private String description;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列。
     */
    private Long delUnique;

}
