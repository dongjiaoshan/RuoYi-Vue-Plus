package org.dromara.djs.plant.farm.domain.query;

import lombok.Data;

import java.time.LocalDate;

/**
 * 农事记录查询条件（PLT-WORK-001 admin 列表 / mp 我的记录共用）。
 *
 * @author djs
 * @since PLT-WORK-001
 */
@Data
public class FarmRecordsQuery {

    private String recordNo;

    /** djs_farm_work_type 12 类之一；admin Tab 切换时单值，mp 我的记录留空。 */
    private String farmType;

    private Long plotId;

    private Long cropId;

    /** 处理班组 ID。 */
    private Long farmBy;

    /** mp 我的记录用：当前登录用户所属班组 — 由 controller 注入。 */
    private Long operatorId;

    private LocalDate farmDateBegin;

    private LocalDate farmDateEnd;
}
