package org.dromara.djs.plant.zone.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;
import org.dromara.djs.common.base.DjsBaseServiceImpl;

import java.io.Serial;

/**
 * 片区实体（PLT-MD-001）。
 *
 * <p>对应表 {@code t_plant_plot_zone}（V202606050900）：</p>
 * <ul>
 *   <li>下游 {@code t_plant_plot_info.zone_id} 一对多关联</li>
 *   <li>删除前校验：片区下若仍有未删除地块，service 抛 {@code plant.zone.has_plot}</li>
 * </ul>
 *
 * <p>软删走 {@code del_flag} + {@code del_unique}，服务层走 {@link DjsBaseServiceImpl#softDelete}。</p>
 *
 * @author djs
 * @since PLT-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_plot_zone")
public class PlotZone extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 片区业务码（用户手填；UNIQUE(tenant_id, zone_code, del_unique)）。
     */
    private String zoneCode;

    /**
     * 片区名称（例 A 区 / 东部温室区）。
     */
    private String zoneName;

    /**
     * 片区说明。
     */
    private String zoneDesc;

    /**
     * 所属大区（例 东部）。
     */
    private String zoneBelong;

    /**
     * 状态（字典 {@code sys_normal_disable}：1=正常 / 2=停用）。
     */
    private Integer zoneStatus;

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
