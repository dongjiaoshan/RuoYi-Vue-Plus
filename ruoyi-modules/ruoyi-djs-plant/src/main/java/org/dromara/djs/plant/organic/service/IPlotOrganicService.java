package org.dromara.djs.plant.organic.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.organic.domain.bo.PlotOrganicBo;
import org.dromara.djs.plant.organic.domain.query.PlotOrganicQuery;
import org.dromara.djs.plant.organic.domain.vo.PlotOrganicVo;

import java.util.Collection;
import java.util.List;

/**
 * 土地有机证书 Service（PLT-MD-003）。
 *
 * @author djs
 * @since PLT-MD-003
 */
public interface IPlotOrganicService {

    TableDataInfo<PlotOrganicVo> queryPageList(PlotOrganicQuery query, PageQuery pageQuery);

    List<PlotOrganicVo> queryList(PlotOrganicQuery query);

    PlotOrganicVo queryById(Long id);

    int insertByBo(PlotOrganicBo bo);

    int updateByBo(PlotOrganicBo bo);

    int deleteWithValidByIds(Collection<Long> ids);
}
