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
     * 根据猪只ID查询猪只实体。
     *
     * @param pigId 猪只ID
     * @return 猪只实体
     */
    PigInfo getById(Long pigId);

    /**
     * 更新猪只状态为死亡。
     * <p>使用 MyBatis-Plus 乐观锁自动处理并发控制。</p>
     * <p>直接传入 pigInfo 对象，避免重复查询数据库。</p>
     *
     * @param pigInfo 猪只信息对象
     * @return 更新结果
     */
    int updateStatusToDeath(PigInfo pigInfo);

}
