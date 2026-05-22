package org.dromara.djs.breed.farm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.farm.domain.bo.PenBo;
import org.dromara.djs.breed.farm.domain.query.PenQuery;
import org.dromara.djs.breed.farm.domain.vo.PenVo;

import java.util.Collection;
import java.util.List;

/**
 * 栏位 Service（BRD-MD-002）。
 *
 * @author djs
 * @since BRD-MD-002
 */
public interface IPenService {

    /**
     * 分页查询栏位列表。
     */
    TableDataInfo<PenVo> queryPageList(PenQuery query, PageQuery pageQuery);

    /**
     * 查询栏位列表（不分页，给左侧树用，按 barnId 过滤）。
     */
    List<PenVo> queryList(PenQuery query);

    /**
     * 根据 ID 查询单条。
     */
    PenVo queryById(Long id);

    /**
     * 新增。
     */
    int insertByBo(PenBo bo);

    /**
     * 修改（barnId / penCode 不允许变更）。
     */
    int updateByBo(PenBo bo);

    /**
     * 软删除（支持批量）。删除前校验"是否被 {@code t_farm_pig_info.pen_id} 引用"——
     * 有未删猪只占栏则拒绝删除。
     */
    int deleteWithValidByIds(Collection<Long> ids);

}
