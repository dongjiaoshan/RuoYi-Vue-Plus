package org.dromara.djs.plant.plot.domain.vo.applet;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * mp 管理·地块查询顶部三统计框（V7-R38）：种植数 / 采摘数 / 空地数。
 *
 * <p>与地块查询列表同一组条件（地块名称模糊 + 状态精确）计数，按 {@code t_plant_plot_info.plot_status}
 * 归类（2=种植 / 3=采摘 / 1=空闲），与种植看板地块计数同源。选了状态后只有对应那一项有数，其余为 0。</p>
 *
 * @author djs
 */
@Data
public class AppletPlotSummaryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 种植数（plot_status=2）。 */
    private Long plantingCount;

    /** 采摘数（plot_status=3）。 */
    private Long pickingCount;

    /** 空地数（plot_status=1）。 */
    private Long idleCount;
}
