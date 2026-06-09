package org.dromara.djs.plant.dashboard.applet.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 管理·种植管理看板「农场种植情况一览」6 KPI（FIX-MGMT-MP-PLT-001）。
 *
 * <p>只读聚合，数据源 {@code t_plant_plot_info}（前 3 个地块计数，{@code plot_status} 1=空闲/2=种植中/3=采摘）
 * + {@code t_plant_plant_details}（已种品种数 / 当前种植面积 / 可采摘品种数）。无 id 字段。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-PLT-001
 */
@Data
public class PlantManageOverviewVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 总地块数（COUNT t_plant_plot_info）。 */
    private Integer totalPlotCount;

    /** 当前种植中地块数（plot_status=2）。 */
    private Integer plantingPlotCount;

    /** 当前空地数（plot_status=1）。 */
    private Integer idlePlotCount;

    /** 已种植果蔬品种数（COUNT DISTINCT crop_id WHERE plant_status<>'pending'）。 */
    private Integer plantedCropCount;

    /** 当前种植面积（亩，SUM plot_area，进行中明细 plant_status='ongoing'）。 */
    private BigDecimal currentPlantArea;

    /** 近一月可采摘品种数（earliest_harvestdate 落今~+1 月，COUNT DISTINCT crop_id）。 */
    private Integer harvestableCropCount;

}
