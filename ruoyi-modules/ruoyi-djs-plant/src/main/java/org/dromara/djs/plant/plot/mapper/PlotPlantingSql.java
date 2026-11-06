package org.dromara.djs.plant.plot.mapper;

/**
 * 地块种植记录共用 SQL 片段（admin 地块详情 {@link PlotInfoMapper#selectPlantingByPlot} 与
 * mp 地块查询 / 地块详情 {@link AppletPlotQueryMapper} 同一口径，片段只此一份）。
 *
 * <p>片段里只用 {@code >} / {@code >=}，不出现 {@code <}，可同时嵌进普通 {@code @Select} 与
 * {@code <script>} 动态 SQL。要求外层种植明细别名为 {@code d}（{@code t_plant_plant_details}）。</p>
 *
 * @author djs
 */
public final class PlotPlantingSql {

    /**
     * 本茬灾害损失（kg，无灾害为 0）：灾害农事记录（{@code farm_type='disaster'}）同地块、同作物，
     * 农事日期落在「本茬开工日 {@code d.begin_actualdate}（含）～ 同地块下一茬开工日（不含）」之间才算本茬；
     * 没有下一茬不设上限。未开工明细（{@code begin_actualdate} 为空）没有种植期间，恒为 0。
     *
     * <p>「下一茬」= 同地块任意作物开工日更晚的明细；下一茬开工当天的灾害归下一茬。
     * <b>同日同作物开工多条明细</b>（重复建明细）按 id 定序：id 小的那条视为被 id 大的那条接替，
     * 窗口在同日即关闭，所以同一笔灾害只扣在<b>同日同作物里 id 最大的那条</b>上，绝不重复扣。
     * 同日不同作物不互为下一茬（各扣各的作物，作物条件本就隔离）。</p>
     *
     * <p>只读 {@code t_plant_farm_records.loss_yield} 做查询期归属，不读写 {@code plant_details.loss_yield}。</p>
     */
    public static final String CURRENT_CROP_DISASTER_LOSS = """
        COALESCE((SELECT SUM(fr.loss_yield)
                    FROM t_plant_farm_records fr
                   WHERE fr.del_flag = '0' AND fr.tenant_id = '1001'
                     AND fr.farm_type = 'disaster'
                     AND fr.plot_id = d.plot_id AND fr.crop_id = d.crop_id
                     AND fr.farm_date >= d.begin_actualdate
                     AND NOT EXISTS (SELECT 1 FROM t_plant_plant_details nx
                                      WHERE nx.plot_id = d.plot_id
                                        AND nx.del_flag = '0' AND nx.tenant_id = '1001'
                                        AND (nx.begin_actualdate > d.begin_actualdate
                                             OR (nx.begin_actualdate = d.begin_actualdate
                                                 AND nx.crop_id = d.crop_id AND nx.id > d.id))
                                        AND fr.farm_date >= nx.begin_actualdate)), 0)""";

    /** 本茬预计产量 = 明细预计产量 − 本茬灾害损失，钳零。 */
    public static final String NET_EXPECTED_YIELD =
        "GREATEST(COALESCE(d.expected_yield, 0) - " + CURRENT_CROP_DISASTER_LOSS + ", 0)";

    private PlotPlantingSql() {
    }
}
