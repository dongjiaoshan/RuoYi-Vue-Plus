package org.dromara.djs.breed.production.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.production.domain.bo.BoarConfigBo;
import org.dromara.djs.breed.production.domain.query.BoarConfigQuery;
import org.dromara.djs.breed.production.domain.vo.BoarConfigVo;

import java.util.Collection;
import java.util.List;

/**
 * 精液 / 公猪配置 Service（BRD-MD-003 Tab2）。
 *
 * @author djs
 * @since BRD-MD-003
 */
public interface IBoarConfigService {

    TableDataInfo<BoarConfigVo> queryPageList(BoarConfigQuery query, PageQuery pageQuery);

    List<BoarConfigVo> queryList(BoarConfigQuery query);

    BoarConfigVo queryById(Long id);

    int insertByBo(BoarConfigBo bo);

    int updateByBo(BoarConfigBo bo);

    int deleteWithValidByIds(Collection<Long> ids);

}
