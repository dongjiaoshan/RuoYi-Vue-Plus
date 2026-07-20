package org.dromara.djs.plant.farm.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 工种列表页「作物目标卡」聚合 VO（FIX-PLT-MP-CROPSEL-001）。
 *
 * <p>种植类工种（移栽 / 水肥 / 浇灌 / 除草 / 整枝绑蔓 / 病虫防治 / 退茬 / 灾害 / 采摘）作业 tab
 * 的作物卡数据源。只含「已种植」作物（{@code t_plant_plant_details} 有行），按工种状态规则过滤后
 * 聚合每个作物的可操作地块去重数。</p>
 *
 * <p>工种状态规则（service {@code resolveStatusFilter} 一处定义）：</p>
 * <ul>
 *   <li>rotation（退茬）→ {@code harvest_status='completed'}（采摘完成）</li>
 *   <li>transplant（移栽）→ {@code plant_status='ongoing'} 且 {@code plot_type='nursery'}（保育地块）</li>
 *   <li>其余生长（water_fertilize/irrigation/weed/pest_control/pruning/harvest_activity）→ {@code plant_status='ongoing'}</li>
 * </ul>
 *
 * @author djs
 * @since FIX-PLT-MP-CROPSEL-001
 */
@Data
public class FarmCropTargetCardVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 作物 id（雪花，前端按 string 处理）。 */
    private Long id;

    /** 作物名。 */
    private String cropName;

    /** 作物业务码。 */
    private String cropCode;

    /** 作物图 public URL（IMG-LIB-001 resolver 回填，可空）。 */
    private String cropImg;

    /** 按工种规则过滤后的可操作地块去重数（{@code COUNT(DISTINCT plot_id)}）。 */
    private Integer plotCount;

    /** 该作物最近一次同工种农事日期（无记录时为空）。 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastFarmDate;
}
