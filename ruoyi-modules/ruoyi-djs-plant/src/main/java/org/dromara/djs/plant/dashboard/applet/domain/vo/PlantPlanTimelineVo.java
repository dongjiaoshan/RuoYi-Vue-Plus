package org.dromara.djs.plant.dashboard.applet.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * mp 管理·种植/采摘计划子页「计划时间轴」每月一行（FIX-MGMT-MP-PLT-001）。
 *
 * <p>种植计划按 {@code t_plant_plant_details.plant_month} 分组；采摘计划按
 * {@code MONTH(earliest_harvestdate)} 分组（同结构复用）。月头汇总 + 当月作物卡数组。</p>
 *
 * @author djs
 * @since FIX-MGMT-MP-PLT-001
 */
@Data
public class PlantPlanTimelineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 月份 1-12。 */
    private Integer month;

    /** 当月品种数。 */
    private Integer cropCount;

    /** 当月地块数。 */
    private Integer plotCount;

    /** 当月面积合计（亩）。 */
    private BigDecimal totalArea;

    /** 当月各作物卡片。 */
    private List<CropItem> crops;

    /**
     * 时间轴当月单作物卡片。
     */
    @Data
    public static class CropItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 作物名。 */
        private String cropName;

        /** 作物缩略图 OSS objectName（可空，前端解析 url）。 */
        private String cropImg;

        /** 该作物当月地块数。 */
        private Integer plotCount;

        /** 该作物当月面积合计（亩）。 */
        private BigDecimal area;

    }

}
