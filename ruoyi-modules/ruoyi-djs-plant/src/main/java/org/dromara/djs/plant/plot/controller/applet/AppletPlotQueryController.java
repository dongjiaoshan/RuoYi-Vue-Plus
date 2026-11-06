package org.dromara.djs.plant.plot.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotCardVo;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotFarmworkVo;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotPlantingVo;
import org.dromara.djs.plant.plot.domain.vo.applet.AppletPlotSummaryVo;
import org.dromara.djs.plant.plot.service.IAppletPlotQueryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * mp 管理·种植看板「地块查询」+「地块详情」Controller（V7-R31 / V7-R32）。纯只读。
 *
 * <ul>
 *   <li>{@code GET list?plotName=&plotStatus=}  地块查询卡片列表</li>
 *   <li>{@code GET summary?plotName=&plotStatus=} 地块查询顶部统计（种植 / 采摘 / 空地数，参数同 list）</li>
 *   <li>{@code GET {plotId}}                    地块详情当前数据卡</li>
 *   <li>{@code GET {plotId}/plantings}          种植记录（种植日期降序，top=亩产最高一茬）</li>
 *   <li>{@code GET {plotId}/farmworks}          农事记录（农事日期降序，筛选在前端做）</li>
 * </ul>
 *
 * <p>鉴权同种植看板 {@code /djs/applet/plant/dashboard}：{@code @SaCheckLogin}，不挂 admin 菜单 perm。</p>
 *
 * @author djs
 */
@SaCheckLogin
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/applet/plant/plotQuery")
public class AppletPlotQueryController {

    private final IAppletPlotQueryService plotQueryService;

    /**
     * 地块查询列表。
     *
     * @param plotName   地块名称关键字（模糊）
     * @param plotStatus 1=空闲 / 2=种植 / 3=采摘；空=全部
     * @return 卡片列表
     */
    @GetMapping("/list")
    public R<List<AppletPlotCardVo>> list(@RequestParam(required = false) String plotName,
                                          @RequestParam(required = false) Integer plotStatus) {
        return R.ok(plotQueryService.listPlots(plotName, plotStatus));
    }

    /**
     * 地块查询顶部统计框（种植数 / 采摘数 / 空地数），参数与 {@link #list} 完全一致。
     *
     * @param plotName   地块名称关键字（模糊）
     * @param plotStatus 1=空闲 / 2=种植 / 3=采摘；空=全部
     * @return 三项计数
     */
    @GetMapping("/summary")
    public R<AppletPlotSummaryVo> summary(@RequestParam(required = false) String plotName,
                                          @RequestParam(required = false) Integer plotStatus) {
        return R.ok(plotQueryService.summary(plotName, plotStatus));
    }

    /**
     * 地块详情当前数据卡。
     *
     * @param plotId 地块 id
     * @return 当前数据
     */
    @GetMapping("/{plotId}")
    public R<AppletPlotCardVo> getInfo(@PathVariable Long plotId) {
        return R.ok(plotQueryService.getPlot(plotId));
    }

    /**
     * 地块详情·种植记录。
     *
     * @param plotId 地块 id
     * @return 种植记录
     */
    @GetMapping("/{plotId}/plantings")
    public R<List<AppletPlotPlantingVo>> plantings(@PathVariable Long plotId) {
        return R.ok(plotQueryService.listPlantings(plotId));
    }

    /**
     * 地块详情·农事记录。
     *
     * @param plotId 地块 id
     * @return 农事记录
     */
    @GetMapping("/{plotId}/farmworks")
    public R<List<AppletPlotFarmworkVo>> farmworks(@PathVariable Long plotId) {
        return R.ok(plotQueryService.listFarmworks(plotId));
    }
}
