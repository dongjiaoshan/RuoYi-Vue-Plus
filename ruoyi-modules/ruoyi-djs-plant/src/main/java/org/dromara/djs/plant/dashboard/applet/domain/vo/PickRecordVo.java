package org.dromara.djs.plant.dashboard.applet.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * mp 管理·采摘计划子页「记录 tab」单行（FIX-MGMT-MP-PLT-001）。
 *
 * <p>映射已实际开始采摘的明细（{@code t_plant_plant_details.begin_harvestdate IS NOT NULL}）。
 * pickType = {@code is_pick} 翻中文（1=活动 / 2=基地）；weightKg = {@code actual_yield}（kg）；
 * employee = 采摘班组名（JOIN {@code t_plant_work_team} on {@code harvest_by}）。无 id 字段。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-PLT-001
 */
@Data
public class PickRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 实际采摘开始日期。 */
    private LocalDate date;

    /** 采摘类型中文（基地 / 活动）。 */
    private String pickType;

    /** 作物名。 */
    private String cropName;

    /** 地块编码。 */
    private String plotCode;

    /** 采摘重量（kg）。 */
    private BigDecimal weightKg;

    /** 员工（采摘班组名）。 */
    private String employee;

}
