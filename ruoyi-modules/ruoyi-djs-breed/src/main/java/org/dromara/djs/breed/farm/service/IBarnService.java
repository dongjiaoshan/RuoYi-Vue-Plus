package org.dromara.djs.breed.farm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.farm.domain.bo.BarnBo;
import org.dromara.djs.breed.farm.domain.query.BarnQuery;
import org.dromara.djs.breed.farm.domain.vo.BarnVo;

import java.util.Collection;
import java.util.List;

/**
 * 栋舍 Service（BRD-MD-002）。
 *
 * @author djs
 * @since BRD-MD-002
 */
public interface IBarnService {

    /**
     * 分页查询栋舍列表。
     */
    TableDataInfo<BarnVo> queryPageList(BarnQuery query, PageQuery pageQuery);

    /**
     * 查询栋舍列表（不分页，给左侧树用）。
     */
    List<BarnVo> queryList(BarnQuery query);

    /**
     * 根据 ID 查询单条。
     */
    BarnVo queryById(Long id);

    /**
     * 新增。
     *
     * @return 受影响行数（成功 1）
     */
    int insertByBo(BarnBo bo);

    /**
     * 修改。
     *
     * @return 受影响行数（成功 1）
     */
    int updateByBo(BarnBo bo);

    /**
     * 软删除（支持批量）。删除前校验"是否被 {@code t_farm_pig_info} 引用"——
     * 有未删的猪只挂在 barn_id 上则拒绝删除。
     *
     * @param ids 主键集合
     * @return 受影响行数
     */
    int deleteWithValidByIds(Collection<Long> ids);

}
