package org.dromara.djs.warehouse.veg.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.veg.domain.HandleRecordTeam;

/**
 * 采摘重量录入·采摘班组多选中间表 Mapper（ROW64）。
 *
 * @author djs
 * @since ROW64
 */
public interface HandleRecordTeamMapper extends BaseMapperPlus<HandleRecordTeam, HandleRecordTeam> {

    /**
     * 物理删除某采收记录的全部旧班组关联（sync 先删后插）。
     *
     * @param recordId {@code t_warehouse_handle_record.id}
     * @return 删除行数
     */
    @Delete("DELETE FROM t_warehouse_handle_record_team WHERE record_id = #{recordId}")
    int physicalDeleteByRecordId(@Param("recordId") Long recordId);
}
