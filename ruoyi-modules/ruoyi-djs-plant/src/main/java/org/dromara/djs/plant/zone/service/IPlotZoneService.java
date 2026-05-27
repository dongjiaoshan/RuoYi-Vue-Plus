package org.dromara.djs.plant.zone.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.zone.domain.bo.PlotZoneBo;
import org.dromara.djs.plant.zone.domain.query.PlotZoneQuery;
import org.dromara.djs.plant.zone.domain.vo.PlotZoneVo;

import java.util.Collection;
import java.util.List;

/**
 * 片区 Service（PLT-MD-001）。
 *
 * @author djs
 * @since PLT-MD-001
 */
public interface IPlotZoneService {

    /** 分页查询。 */
    TableDataInfo<PlotZoneVo> queryPageList(PlotZoneQuery query, PageQuery pageQuery);

    /** 列表查询（不分页，给左栏树和下游下拉用）。 */
    List<PlotZoneVo> queryList(PlotZoneQuery query);

    /** 按 ID 查单条。 */
    PlotZoneVo queryById(Long id);

    /** 新增。 */
    int insertByBo(PlotZoneBo bo);

    /**
     * 修改（{@code zoneCode} 不允许修改）。
     */
    int updateByBo(PlotZoneBo bo);

    /**
     * 软删除（支持批量）。
     *
     * <p>删除前校验：若片区下仍有未删除地块，抛 {@code plant.zone.has_plot}。</p>
     */
    int deleteWithValidByIds(Collection<Long> ids);
}
