package org.dromara.djs.breed.pig.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.pig.domain.PigInfo;
import org.dromara.djs.breed.pig.domain.vo.PigInfoVo;

/**
 * 猪只基础信息Mapper。
 *
 * @author djs
 * @since BRD-MD-003
 */
public interface PigInfoMapper extends BaseMapperPlus<PigInfo, PigInfoVo> {

    /**
     * 根据耳号查询猪只信息（关联字典表）。
     *
     * @param earTag 耳号
     * @return 猪只信息（含字典名称）
     */
    PigInfoVo selectVoByEarTag(String earTag);


    /**
     * 根据ID查询猪只实体（关联栋舍表）。
     *
     * @param id 猪只ID
     * @return 猪只实体（含栋舍名称）
     */
    PigInfo selectByIdWithBarn(Long id);

}
