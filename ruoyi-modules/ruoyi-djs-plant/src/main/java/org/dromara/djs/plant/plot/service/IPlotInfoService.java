package org.dromara.djs.plant.plot.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.plot.domain.bo.PlotInfoBo;
import org.dromara.djs.plant.plot.domain.query.PlotInfoQuery;
import org.dromara.djs.plant.plot.domain.vo.PlotInfoVo;

import java.util.Collection;
import java.util.List;

/**
 * 地块 Service（PLT-MD-001）。
 *
 * @author djs
 * @since PLT-MD-001
 */
public interface IPlotInfoService {

    TableDataInfo<PlotInfoVo> queryPageList(PlotInfoQuery query, PageQuery pageQuery);

    List<PlotInfoVo> queryList(PlotInfoQuery query);

    PlotInfoVo queryById(Long id);

    int insertByBo(PlotInfoBo bo);

    int updateByBo(PlotInfoBo bo);

    /**
     * 软删（支持批量）。
     *
     * <p>删除前校验：D8 阶段 stub（t_plant_plant_plan / t_plant_farm_records / t_plant_plant_details
     * 均 0 行）；D9+ PLT-PLAN-001 上线后启用真校验。</p>
     */
    int deleteWithValidByIds(Collection<Long> ids);
}
