package org.dromara.djs.plant.plot.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.plot.domain.bo.PlotInfoBo;
import org.dromara.djs.plant.plot.domain.query.PlotInfoQuery;
import org.dromara.djs.plant.plot.domain.vo.PlotFarmworkRecordVo;
import org.dromara.djs.plant.plot.domain.vo.PlotInfoVo;
import org.dromara.djs.plant.plot.domain.vo.PlotOrganicRefVo;
import org.dromara.djs.plant.plot.domain.vo.PlotPlantingRecordVo;

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
     * 软删（支持批量）。删除前校验：仅空闲态地块可删，种植/采摘态拦截。
     */
    int deleteWithValidByIds(Collection<Long> ids);

    /**
     * 地块详情·种植信息子表（FIX-PLT-AD-DETAIL-001，11 列，按 plotId 透视）。
     */
    List<PlotPlantingRecordVo> listPlantingByPlot(Long plotId);

    /**
     * 地块详情·农事信息子表（FIX-PLT-AD-DETAIL-001，按 plotId 透视）。
     */
    List<PlotFarmworkRecordVo> listFarmworkByPlot(Long plotId);

    /**
     * 地块详情·认证信息子表（FIX-PLT-AD-DETAIL-001，按 plotId 关联有机证书）。
     */
    List<PlotOrganicRefVo> listOrganicByPlot(Long plotId);
}
