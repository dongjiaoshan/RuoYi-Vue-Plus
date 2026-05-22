package org.dromara.djs.breed.breeding.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.breeding.domain.bo.BreedConfigBo;
import org.dromara.djs.breed.breeding.domain.query.BreedConfigQuery;
import org.dromara.djs.breed.breeding.domain.vo.BreedConfigVo;

import java.util.Collection;
import java.util.List;

/**
 * 育种配置 Service（BRD-MD-001）。
 *
 * @author djs
 * @since BRD-MD-001
 */
public interface IBreedConfigService {

    TableDataInfo<BreedConfigVo> queryPageList(BreedConfigQuery query, PageQuery pageQuery);

    List<BreedConfigVo> queryList(BreedConfigQuery query);

    BreedConfigVo queryById(Long id);

    int insertByBo(BreedConfigBo bo);

    int updateByBo(BreedConfigBo bo);

    int deleteWithValidByIds(Collection<Long> ids);

}
