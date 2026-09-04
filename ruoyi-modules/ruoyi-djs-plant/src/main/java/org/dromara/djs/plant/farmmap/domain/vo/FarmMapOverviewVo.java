package org.dromara.djs.plant.farmmap.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 农场地图一次性拉全（PLT-FARMMAP-001）。
 *
 * <p>页面一进来要同时知道三件事：图上哪些格子挂了地、哪些地还没挂到图上、覆盖率多少。
 * 三个都来自同一次「地块全量 + 绑定全量」的内存 join，拆成三个端点会重复算，
 * 还会在两次请求之间出现覆盖率对不上的中间态，所以合成一个端点返回。</p>
 *
 * @author djs
 * @since PLT-FARMMAP-001
 */
@Data
public class FarmMapOverviewVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 已挂地块的格子。未挂的格子不在这里，前端按「未挂」画。
     */
    private List<FarmMapRegionVo> regions;

    /**
     * 图外地块：还没挂到图上的地块。页面右侧列出来，从这儿照常进排产。
     */
    private List<FarmMapUnboundPlotVo> unboundPlots;

    /**
     * 地块总数（{@code t_plant_plot_info} 未删行数）。
     */
    private Integer plotTotal;

    /**
     * 已挂数量。{@code boundCount / plotTotal} 就是页面顶部那个覆盖率。
     */
    private Integer boundCount;

}
