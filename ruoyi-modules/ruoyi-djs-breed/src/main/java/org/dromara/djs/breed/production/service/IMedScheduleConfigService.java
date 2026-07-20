package org.dromara.djs.breed.production.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.production.domain.bo.MedScheduleConfigBo;
import org.dromara.djs.breed.production.domain.query.MedScheduleConfigQuery;
import org.dromara.djs.breed.production.domain.vo.MedScheduleConfigVo;

import java.util.Collection;
import java.util.List;

/**
 * 药品 / 疫苗周期配置 Service（BRD-MD-003 Tab3）。
 *
 * @author djs
 * @since BRD-MD-003
 */
public interface IMedScheduleConfigService {

    TableDataInfo<MedScheduleConfigVo> queryPageList(MedScheduleConfigQuery query, PageQuery pageQuery);

    List<MedScheduleConfigVo> queryList(MedScheduleConfigQuery query);

    MedScheduleConfigVo queryById(Long id);

    int insertByBo(MedScheduleConfigBo bo);

    int updateByBo(MedScheduleConfigBo bo);

    int deleteWithValidByIds(Collection<Long> ids);

}
