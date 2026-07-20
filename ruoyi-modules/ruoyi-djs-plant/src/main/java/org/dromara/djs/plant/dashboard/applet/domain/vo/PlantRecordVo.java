package org.dromara.djs.plant.dashboard.applet.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * mp 管理·种植计划子页「记录 tab」单行（FIX-MGMT-MP-PLT-001）。
 *
 * <p>映射已实际开始种植的明细（{@code t_plant_plant_details.begin_actualdate IS NOT NULL}）。
 * employee = 种植班组名（JOIN {@code t_plant_work_team} on {@code plant_by}）。无 id 字段。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-PLT-001
 */
@Data
public class PlantRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 实际开始种植日期。 */
    private LocalDate date;

    /** 作物名。 */
    private String cropName;

    /** 地块编码。 */
    private String plotCode;

    /** 面积（亩）。 */
    private BigDecimal area;

    /** 员工（种植班组名）。 */
    private String employee;

}
