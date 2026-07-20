package org.dromara.djs.plant.zone.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 片区 picker 轻量 VO（D12X-MP-PLANT-WORK-IA-001）。
 *
 * <p>给 mp {@code ZonePicker} 用——农事录入按片区缩小地块范围。只暴露 id + zoneCode + zoneName
 * 3 字段，对应表 {@code t_plant_plot_zone}。</p>
 *
 * <p>不复用 admin {@code PlotZoneVo}，避免 zoneBelong / zoneDesc / createTime 等无关字段进 mp 流量。</p>
 *
 * @author djs
 * @since D12X-MP-PLANT-WORK-IA-001
 */
@Data
public class ZonePickerVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 片区 ID（snowflake，Jackson 全局序列化为 string）。
     */
    private Long id;

    /**
     * 片区业务码（如 Z001）。
     */
    private String zoneCode;

    /**
     * 片区名称（如 东部温室区）。
     */
    private String zoneName;

    /**
     * 该片区当月采摘任务数（应用层聚合回填，无任务为 0）。
     *
     * <p>口径与采收列表 {@code AppletPickServiceImpl#listCropTasks} 一致：is_pick=2（非游客）
     * 且采摘窗口 [earliest, last] 与当月 [firstDay, lastDay] 相交的明细，按地块所属片区计数。</p>
     */
    private Integer pickTaskCount;

}
