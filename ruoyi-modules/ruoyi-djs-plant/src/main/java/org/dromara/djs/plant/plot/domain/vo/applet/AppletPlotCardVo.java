package org.dromara.djs.plant.plot.domain.vo.applet;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * mp 管理·地块查询卡片 / 地块详情顶部当前数据卡（V7-R31 / V7-R32）。
 *
 * <p>状态取 {@code t_plant_plot_info.plot_status}（1=空闲 / 2=种植 / 3=采摘），与种植看板
 * 「当前空地块数（=1）/ 当前已种植地块数（=总-空）」同口径。</p>
 *
 * <p>当前茬 = 该地块已开工（{@code begin_actualdate} 非空）且未退茬（{@code rotated_at} 为空）的
 * 最新一条种植明细（种植日期降序、id 降序）；空闲地块不回填当前茬字段。</p>
 *
 * <p>历史统计只算已开工的种植明细：历史种植次数 = 行数；亩产最高作物 / 最高亩产 = 采摘完成行里
 * {@code actual_yield / plot_area} 最大的一条（kg/亩）。</p>
 *
 * @author djs
 */
@Data
public class AppletPlotCardVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 地块 id（snowflake，Jackson 序列化为 string）。 */
    private Long id;

    /** 地块编码。 */
    private String plotCode;

    /** 地块名称。 */
    private String plotName;

    /** 地块面积（亩）。 */
    private BigDecimal plotArea;

    /** 地块状态（1=空闲 / 2=种植 / 3=采摘）。 */
    private Integer plotStatus;

    /** 当前茬作物名。 */
    private String cropName;

    /** 当前茬种植日期。 */
    private LocalDate plantDate;

    /** 当前茬采摘开始日期。 */
    private LocalDate beginHarvestdate;

    /** 当前茬采摘结束日期。 */
    private LocalDate endHarvestdate;

    /** 当前茬预计产量（kg，已扣该地块该作物灾害损失，钳零；与 admin 地块详情同口径）。 */
    private BigDecimal expectedYield;

    /** 当前茬实际产量（kg，采摘累计）。 */
    private BigDecimal actualYield;

    /** 历史种植次数（已开工种植明细行数）。 */
    private Long historyPlantCount;

    /** 亩产最高作物名。 */
    private String maxYieldCropName;

    /** 最高亩产（kg/亩）。 */
    private BigDecimal maxYieldPerMu;
}
