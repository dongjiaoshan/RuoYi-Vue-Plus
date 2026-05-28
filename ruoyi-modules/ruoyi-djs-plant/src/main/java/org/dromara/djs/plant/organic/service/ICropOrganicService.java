package org.dromara.djs.plant.organic.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.organic.domain.bo.CropOrganicBo;
import org.dromara.djs.plant.organic.domain.query.CropOrganicQuery;
import org.dromara.djs.plant.organic.domain.vo.CropOrganicVo;

import java.util.Collection;
import java.util.List;

/**
 * 果蔬有机证书 Service（PLT-MD-003）。
 *
 * @author djs
 * @since PLT-MD-003
 */
public interface ICropOrganicService {

    TableDataInfo<CropOrganicVo> queryPageList(CropOrganicQuery query, PageQuery pageQuery);

    List<CropOrganicVo> queryList(CropOrganicQuery query);

    CropOrganicVo queryById(Long id);

    int insertByBo(CropOrganicBo bo);

    int updateByBo(CropOrganicBo bo);

    int deleteWithValidByIds(Collection<Long> ids);
}
