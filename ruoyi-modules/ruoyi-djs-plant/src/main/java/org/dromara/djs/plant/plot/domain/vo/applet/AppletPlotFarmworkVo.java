package org.dromara.djs.plant.plot.domain.vo.applet;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * mp 地块详情·农事记录行（V7-R32）。一行 = 一条 {@code t_plant_farm_records}（plot_id = 本地块）。
 *
 * @author djs
 */
@Data
public class AppletPlotFarmworkVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 农事记录 id。 */
    private Long id;

    /** 农事日期。 */
    private LocalDate farmDate;

    /** 农事类型（字典 djs_farm_work_type key）。 */
    private String farmType;

    /** 农事类型中文（服务端译）。 */
    private String farmTypeLabel;

    /** 地块类型快照（1=空闲 / 2=种植 / 3=采摘）；空闲时作业的农事不挂作物。 */
    private Integer plotType;

    /** 作物 id（空闲农事为空）。 */
    private Long cropId;

    /** 作物名（空闲农事为空）。 */
    private String cropName;

    /** 处理班组单列 farm_by 的班组名（多选中间表无行时的回落值）。 */
    private String farmByName;

    /** 班组（多选逗号拼接）。 */
    private String teamNames;
}
