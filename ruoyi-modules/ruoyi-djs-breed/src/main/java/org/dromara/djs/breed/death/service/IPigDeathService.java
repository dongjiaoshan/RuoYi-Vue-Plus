package org.dromara.djs.breed.death.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.death.domain.PigDeath;
import org.dromara.djs.breed.death.domain.bo.PigDeathBo;
import org.dromara.djs.breed.death.domain.query.PigDeathQuery;
import org.dromara.djs.breed.death.domain.vo.PigDeathVo;

import java.util.Collection;

/**
 * 猪只死亡记录Service接口。
 *
 * @author djs
 * @since BRD-MD-003
 */
public interface IPigDeathService {

    /**
     * 分页查询死亡记录列表。
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<PigDeathVo> queryPageList(PigDeathQuery query, PageQuery pageQuery);

    /**
     * 查询死亡记录详情。
     *
     * @param id 主键ID
     * @return 死亡记录详情
     */
    PigDeathVo queryById(Long id);

    /**
     * 提交死亡信息。
     *
     * @param bo 死亡信息BO
     * @return 提交结果
     */
    int submitDeathInfo(PigDeathBo bo);

    /**
     * 删除死亡记录（软删）。
     *
     * @param ids 主键ID集合
     * @return 删除结果
     */
    int deleteWithValidByIds(Collection<Long> ids);

    /**
     * 根据猪只ID查询死亡记录。
     *
     * @param pigId 猪只ID
     * @return 死亡记录
     */
    PigDeath getByPigId(Long pigId);

}
