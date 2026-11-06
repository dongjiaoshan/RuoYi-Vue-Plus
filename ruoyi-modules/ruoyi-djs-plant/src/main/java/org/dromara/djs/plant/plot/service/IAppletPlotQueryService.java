package org.dromara.djs.plant.plot.service;

import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotCardVo;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotFarmworkVo;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotPlantingVo;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotSummaryVo;

import java.util.List;

/**
 * mp 管理·地块查询 / 地块详情只读 Service（V7-R31 / V7-R32）。
 *
 * @author djs
 */
public interface IAppletPlotQueryService {

    /**
     * 地块查询列表（名称模糊 + 状态），每卡回填当前茬与历史统计。
     *
     * @param plotName   地块名称关键字（可空）
     * @param plotStatus 1=空闲 / 2=种植 / 3=采摘（可空 = 全部）
     * @return 卡片列表（按地块编码升序）
     */
    List<AppletPlotCardVo> listPlots(String plotName, Integer plotStatus);

    /**
     * 地块查询顶部统计（种植数 / 采摘数 / 空地数），条件与 {@link #listPlots} 完全一致。
     *
     * @param plotName   地块名称关键字（可空）
     * @param plotStatus 1=空闲 / 2=种植 / 3=采摘（可空 = 全部）
     * @return 三项计数（均非空）
     */
    AppletPlotSummaryVo summary(String plotName, Integer plotStatus);

    /**
     * 地块详情顶部当前数据卡。
     *
     * @param plotId 地块 id
     * @return 当前数据（空闲地块只带基础字段）
     */
    AppletPlotCardVo getPlot(Long plotId);

    /**
     * 地块详情·种植记录（已开工明细，种植日期降序），亩产最高一茬标 top。
     *
     * @param plotId 地块 id
     * @return 种植记录
     */
    List<AppletPlotPlantingVo> listPlantings(Long plotId);

    /**
     * 地块详情·农事记录（农事日期降序）。空闲时作业的农事不挂作物。
     *
     * @param plotId 地块 id
     * @return 农事记录
     */
    List<AppletPlotFarmworkVo> listFarmworks(Long plotId);
}
