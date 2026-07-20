package org.dromara.djs.plant.pick.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * mp 采收列表「作物维度」任务卡 VO（FIX-PLT-MP-PICK-001，#3）。
 *
 * <p>原型采收列表片区分组下每张卡 = 一作物：图 + 名 + 完成率 + 开始/最晚日期 + 预计/采摘量 + 地块数。
 * 由 {@code t_plant_plant_details}（排除游客采摘 is_pick=1）按片区 + 作物维度聚合。</p>
 *
 * @author djs
 * @since FIX-PLT-MP-PICK-001
 */
@Data
public class PickCropTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 作物 id。 */
    private Long cropId;

    /** 作物名称。 */
    private String cropName;

    /** 作物缩略图 OSS objectName（可空，前端回落占位图）。 */
    private String cropImg;

    /** 完成率（整数百分比 0-100，= 已完成地块数 / 总地块数 × 100）。 */
    private Integer completionRate;

    /** 该作物下最早的计划最早采摘日期（原型「开始日期」）。 */
    private LocalDate startDate;

    /** 该作物下最晚的计划最晚采摘日期（原型「最晚日期」）。 */
    private LocalDate lastDate;

    /** 预计总产量（kg，该作物各地块 expected_yield 之和）。 */
    private BigDecimal expectedYield;

    /** 已采总产量（kg，该作物各地块 actual_yield 之和）。 */
    private BigDecimal actualYield;

    /** 该作物下地块数（明细行数）。 */
    private Integer plotCount;
}
