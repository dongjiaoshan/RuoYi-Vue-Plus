package org.dromara.djs.breed.death.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.death.domain.PigDeath;
import org.dromara.djs.breed.death.domain.vo.PigDeathVo;
import org.dromara.djs.breed.death.domain.query.PigDeathQuery;

/**
 * 猪只死亡记录Mapper。
 *
 * @author djs
 * @since BRD-MD-003
 */
public interface PigDeathMapper extends BaseMapperPlus<PigDeath, PigDeathVo> {

    /**
     * 分页查询死亡记录列表（关联查询）。
     *
     * @param page   分页参数
     * @param query  查询条件
     * @return 分页结果
     */
    IPage<PigDeathVo> selectVoPage(IPage<PigDeathVo> page, PigDeathQuery query);

    /**
     * 查询死亡记录详情（关联查询）。
     *
     * @param id 主键ID
     * @return 死亡记录详情
     */
    PigDeathVo selectVoById(Long id);

    /**
     * 根据猪只ID查询死亡记录。
     *
     * @param pigId 猪只ID
     * @return 死亡记录
     */
    default PigDeath selectByPigId(Long pigId) {
        LambdaQueryWrapper<PigDeath> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PigDeath::getPigId, pigId)
                .eq(PigDeath::getDelFlag, "0");
        return selectOne(wrapper);
    }

}
