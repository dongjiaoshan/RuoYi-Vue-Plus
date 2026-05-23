package org.dromara.djs.breed.pig.service;

import org.dromara.djs.breed.pig.domain.PigInfo;
import org.dromara.djs.breed.pig.domain.vo.PigInfoVo;

/**
 * 猪只基础信息Service接口。
 *
 * @author djs
 * @since BRD-MD-003
 */
public interface IPigInfoService {

    /**
     * 根据耳号查询猪只基础信息。
     *
     * @param earTag 耳号
     * @return 猪只基础信息
     */
    PigInfoVo queryByEarTag(String earTag);

    /**
     * 根据耳号查询猪只实体。
     *
     * @param earTag 耳号
     * @return 猪只实体
     */
    PigInfo getByEarTag(String earTag);

    /**
     * 更新猪只状态为死亡。
     *
     * @param pigId 猪只ID
     * @return 更新结果
     */
    int updateStatusToDeath(Long pigId);

}
