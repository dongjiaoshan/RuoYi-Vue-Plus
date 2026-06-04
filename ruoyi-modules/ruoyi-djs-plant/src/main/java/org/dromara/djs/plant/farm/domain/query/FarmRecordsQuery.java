package org.dromara.djs.plant.farm.domain.query;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 农事记录查询条件（PLT-WORK-001 admin 列表 / mp 我的记录共用；PLT-WORK-002 扩 farmWorkTypes 多选）。
 *
 * @author djs
 * @since PLT-WORK-001
 */
@Data
public class FarmRecordsQuery {

    private String recordNo;

    /** djs_farm_work_type 12 类之一；admin Tab 切换时单值，mp 我的记录留空。 */
    private String farmType;

    /**
     * djs_farm_work_type 多选（PLT-WORK-002 admin 5 Tab 用）。
     *
     * <p>每个 Tab 对应一组 farm_work_type，前端切 Tab 时传入该组，service 走 {@code IN} 过滤。
     * 与 {@link #farmType} 单值互斥优先：本字段非空时 buildWrapper 走 IN，忽略 farmType。</p>
     */
    private List<String> farmWorkTypes;

    private Long plotId;

    private Long cropId;

    /** 灾害类型筛选（字典 djs_disaster_type；PLT-WORK-003 admin 灾害子页用，对 disaster_type 列 eq）。 */
    private String disasterType;

    /** 预警筛选（字典 djs_yes_no：1=是 / 2=否；PLT-WORK-003 admin 灾害子页用，对 is_warning 列 eq）。 */
    private Integer isWarning;

    /** 处理班组 ID。 */
    private Long farmBy;

    /** mp 我的记录用：当前登录用户所属班组 — 由 controller 注入。 */
    private Long operatorId;

    private LocalDate farmDateBegin;

    private LocalDate farmDateEnd;
}
