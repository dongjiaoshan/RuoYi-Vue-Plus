package org.dromara.djs.plant.crop.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 作物详情·种植信息子表行（FIX-PLT-AD-DETAIL-001）。
 *
 * <p>按 {@code cropId} 聚合 {@code t_plant_plant_details} JOIN 地块 / 班组（种植人/采摘人），
 * 9 列对齐原型「作物详情 → 种植信息」tab。预计/实际亩产按 kg 返（决策#7 明细用 kg）。</p>
 *
 * @author djs
 * @since FIX-PLT-AD-DETAIL-001
 */
@Data
public class CropPlantingRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 明细 ID。 */
    private Long id;

    /** 种植日期（实际开始种植日）。 */
    private Date plantDate;

    /** 种植地块名。 */
    private String plotName;

    /** 种植人（种植班组名）。 */
    private String plantTeamName;

    /** 预计亩产 kg/亩（取作物 predicted_per）。 */
    private BigDecimal predictedPer;

    /** 预计最早采摘日期。 */
    private Date earliestHarvestDate;

    /** 实际亩产 kg/亩（average_yield）。 */
    private BigDecimal actualPer;

    /** 采摘开始日期。 */
    private Date pickStartDate;

    /** 采摘结束日期。 */
    private Date pickEndDate;

    /** 采摘人（采摘班组名）。 */
    private String pickTeamName;
}
