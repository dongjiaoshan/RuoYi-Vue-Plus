package org.dromara.djs.breed.core.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.core.domain.PigStatusRecord;
import org.dromara.djs.breed.core.domain.vo.PigStatusRecordVo;

/**
 * 状态变更记录 Mapper（BRD-CORE-001）。
 *
 * @author djs
 * @since BRD-CORE-001
 */
public interface PigStatusRecordMapper extends BaseMapperPlus<PigStatusRecord, PigStatusRecordVo> {

    /** 永久转育肥以事件历史为准，当前位置/状态变化不会解除限制（D-0150）。 */
    @Select("SELECT EXISTS (SELECT 1 FROM t_farm_status_record r"
        + " JOIN t_farm_pig_info p ON p.id = r.pig_id AND p.tenant_id = r.tenant_id"
        + " WHERE r.pig_id = #{pigId} AND r.event_type = 'TO_FATTEN')")
    boolean hasToFattenHistory(@Param("pigId") Long pigId);
}
