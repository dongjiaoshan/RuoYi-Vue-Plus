package org.dromara.djs.plant.farmmap.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 图上一个已挂地块的格子（PLT-FARMMAP-001）。
 *
 * <p>只返回**已绑定**的格子。前端拿 {@code regionKey} 去 regions.generated.ts 里找几何，
 * 找不到对应 VO 的格子按「未挂」画（白底虚线）——所以未绑定的格子不需要后端返回。</p>
 *
 * @author djs
 * @since PLT-FARMMAP-001
 */
@Data
public class FarmMapRegionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 图上格子业务码。
     */
    private String regionKey;

    /**
     * 地块 ID（snowflake，Jackson 全局序列化为 string）。
     */
    private Long plotId;

    /**
     * 地块业务码。
     */
    private String plotCode;

    /**
     * 地块名称。
     */
    private String plotName;

    /**
     * 所属片区名称。
     */
    private String zoneName;

    /**
     * 所属大区（字典 {@code djs_zone_belong}：A=一期基地 / B=二期基地）。
     */
    private String zoneBelong;

    /**
     * 地块类型（字典 {@code djs_plot_type}）。
     */
    private String plotType;

    /**
     * 地块状态（字典 {@code djs_plot_status}：1=空闲 / 2=种植 / 3=采摘）。图上着色按它。
     */
    private Integer plotStatus;

    /**
     * 地块面积（亩）。
     */
    private BigDecimal plotArea;

}
