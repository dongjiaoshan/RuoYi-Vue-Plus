package org.dromara.djs.plant.plot.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 地块详情·农事信息子表单行（FIX-PLT-AD-DETAIL-001，按 plotId 透视）。
 *
 * <p>映射 {@code t_plant_farm_records} JOIN 班组，列对齐原型「农事信息」子表：
 * 农事日期 / 农事类型 / 作业地块 / 作业人 / 说明。</p>
 *
 * <p>「农事类型」返字典 key（{@code djs_farm_work_type}，前端 dict-tag 翻译）；
 * 「作业人」= 处理班组名（{@code farm_by} JOIN {@code t_plant_work_team.team_name}）。</p>
 *
 * @author djs
 * @since FIX-PLT-AD-DETAIL-001
 */
@Data
public class PlotFarmworkRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 农事记录 id。 */
    private Long id;

    /** 农事日期。 */
    private LocalDate farmDate;

    /** 农事类型（字典 {@code djs_farm_work_type}）。 */
    private String farmType;

    /** 作业地块名（本片区维度即当前地块名）。 */
    private String plotName;

    /** 作业人（处理班组名）。 */
    private String farmByName;

    /** 说明（农事记录业务备注）。 */
    private String remark;
}
