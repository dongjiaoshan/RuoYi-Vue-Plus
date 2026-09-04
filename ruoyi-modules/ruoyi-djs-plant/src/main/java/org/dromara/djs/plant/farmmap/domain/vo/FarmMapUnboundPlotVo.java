package org.dromara.djs.plant.farmmap.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 图外地块：还没挂到图上任何格子的地块（PLT-FARMMAP-001）。
 *
 * <p>这份清单同时是绑定时的候选列表——点中一个空格子后，能挂的就是这里面的地块
 * （已挂的地块不出现，因为 1:1）。</p>
 *
 * @author djs
 * @since PLT-FARMMAP-001
 */
@Data
public class FarmMapUnboundPlotVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 地块 ID（snowflake，Jackson 全局序列化为 string）。
     */
    private Long id;

    /**
     * 地块业务码。
     */
    private String plotCode;

    /**
     * 地块名称。
     */
    private String plotName;

    /**
     * 所属片区名称。绑定时按片区分组挑，比在 167 块地里平铺找快得多。
     */
    private String zoneName;

    /**
     * 所属大区（A=一期基地 / B=二期基地）。
     */
    private String zoneBelong;

}
