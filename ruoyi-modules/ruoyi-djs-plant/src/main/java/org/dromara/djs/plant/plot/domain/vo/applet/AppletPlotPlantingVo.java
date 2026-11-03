package org.dromara.djs.plant.plot.domain.vo.applet;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * mp 地块详情·种植记录卡（V7-R32）。一行 = 一条已开工的种植明细（{@code t_plant_plant_details}）。
 *
 * @author djs
 */
@Data
public class AppletPlotPlantingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 种植明细 id。 */
    private Long id;

    /** 作物 id。 */
    private Long cropId;

    /** 作物名称。 */
    private String cropName;

    /** 作物图片 public URL（入参先承载 image_oss_id，service 层解析成 URL）。 */
    private String cropImage;

    /** 种植日期（begin_actualdate）。 */
    private LocalDate plantDate;

    /** 采摘开始日期。 */
    private LocalDate beginHarvestdate;

    /** 采摘结束日期。 */
    private LocalDate endHarvestdate;

    /** 预计产量（kg，已扣灾害损失，钳零）。 */
    private BigDecimal expectedYield;

    /** 实际产量（kg）。 */
    private BigDecimal actualYield;

    /** 种植班组（多选逗号拼接）。 */
    private String plantTeamNames;

    /** 采摘班组（多选逗号拼接）。 */
    private String harvestTeamNames;

    /** 采摘状态（djs_pick_status）：仅 completed 行参与「最高」评比，不下发前端展示。 */
    private String harvestStatus;

    /** 本条种植面积（亩）。 */
    private BigDecimal plotArea;

    /** 是否本地块亩产最高的一茬（与地块查询卡「亩产最高作物」同一行）。 */
    private Boolean top;
}
