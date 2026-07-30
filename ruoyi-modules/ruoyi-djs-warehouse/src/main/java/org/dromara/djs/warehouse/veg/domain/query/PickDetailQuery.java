package org.dromara.djs.warehouse.veg.domain.query;

import lombok.Data;

import java.time.LocalDate;

/**
 * 采摘明细查询参数（admin 只读列表筛选，FIX-ADMIN-0721）。
 *
 * <p>数据源 = 毛菜处理称重记录（{@code t_warehouse_handle_record} record_type=1 采收过磅流水），
 * 与仓库统计权威口径同源。</p>
 *
 * @author djs
 * @since FIX-ADMIN-0721
 */
@Data
public class PickDetailQuery {

    /** 采摘日期下界（含，按 DATE(handle_time)）。 */
    private LocalDate pickDateBegin;

    /** 采摘日期上界（含，按 DATE(handle_time)）。 */
    private LocalDate pickDateEnd;

    /** 作物名称模糊匹配（planting_record 冗余 crop_name）。 */
    private String cropName;

    /** 采摘班组精确匹配（采收记录实际班组 / 采摘活动班组集合）。 */
    private Long teamId;

    /** 统计来源精确匹配：1=毛菜处理间 2=采摘活动（对派生表 statSource，row44）。 */
    private String statSource;

}
