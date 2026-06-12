package org.dromara.djs.plant.plot.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 地块详情·种植信息子表单行（FIX-PLT-AD-DETAIL-001，按 plotId 透视）。
 *
 * <p>映射 {@code t_plant_plant_details d} JOIN 作物 / 计划 / 班组（种植 + 采摘），
 * 11 列对齐原型「地块详情·种植信息」子表：种植日期 / 作物图片 / 作物名称 / 作物编码 /
 * 种植人 / 预计亩产 / 预计最早采摘日期 / 实际亩产 / 采摘开始日期 / 采摘结束日期 / 采摘人。</p>
 *
 * <p>「种植人 / 采摘人」= 班组名（{@code plant_by} / {@code harvest_by} JOIN
 * {@code t_plant_work_team.team_name}，对齐 {@code PlantRecordVo.employee} 口径）。
 * 「预计/实际亩产」按 kg 返（决策#7：明细 / 子表用 kg）。</p>
 *
 * @author djs
 * @since FIX-PLT-AD-DETAIL-001
 */
@Data
public class PlotPlantingRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 明细 id。 */
    private Long id;

    /** 种植日期（实际开始种植日期 {@code begin_actualdate}）。 */
    private LocalDate plantDate;

    /** 作物图片（OSS objectName，取 {@code crop.image_oss_id}，回退 {@code crop_image_preview}）。 */
    private String cropImage;

    /** 作物名称。 */
    private String cropName;

    /** 作物编码。 */
    private String cropCode;

    /** 种植人（种植班组名）。 */
    private String plantByName;

    /** 预计亩产（kg/亩）。 */
    private BigDecimal expectedYield;

    /** 预计最早采摘日期。 */
    private LocalDate earliestHarvestdate;

    /** 实际亩产（kg/亩）。 */
    private BigDecimal actualYield;

    /** 采摘开始日期。 */
    private LocalDate beginHarvestdate;

    /** 采摘结束日期。 */
    private LocalDate endHarvestdate;

    /** 采摘人（采摘班组名）。 */
    private String harvestByName;
}
