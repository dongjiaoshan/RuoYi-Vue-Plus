package org.dromara.djs.plant.zone.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 片区查询参数（PLT-MD-001）。
 *
 * @author djs
 * @since PLT-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlotZoneQuery extends BaseEntity {

    /**
     * 片区编码（精确匹配）。
     */
    private String zoneCode;

    /**
     * 片区名称（模糊匹配）。
     */
    private String zoneName;

    /**
     * 所属大区（字典 djs_zone_belong 值，精确匹配）。
     */
    private String zoneBelong;

    /**
     * 状态（1 正常 / 2 停用）。
     */
    private Integer zoneStatus;

    /**
     * 更新人 ID（{@code sys_user.user_id}，精确匹配）。
     */
    private Long updateBy;

    /**
     * 更新时间起（{@code yyyy-MM-dd HH:mm:ss}，含；前端传日期则自动补 00:00:00）。
     */
    private String updateTimeStart;

    /**
     * 更新时间止（{@code yyyy-MM-dd HH:mm:ss}，含；前端传日期则自动补 23:59:59）。
     */
    private String updateTimeEnd;
}
