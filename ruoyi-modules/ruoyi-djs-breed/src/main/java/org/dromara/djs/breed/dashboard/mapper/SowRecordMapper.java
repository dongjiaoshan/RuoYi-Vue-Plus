package org.dromara.djs.breed.dashboard.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.dashboard.domain.SowRecord;

import java.time.LocalDate;
import java.util.List;

/**
 * 母猪日数据汇总 Mapper（BRD-DASH-001）。
 *
 * @author djs
 * @since BRD-DASH-001
 */
public interface SowRecordMapper extends BaseMapperPlus<SowRecord, SowRecord> {

    /**
     * 取近 N 天的日数据（按 stat_date 升序）。
     *
     * @param tenantId 租户
     * @param fromDate 起始日期（含）
     * @return list（默认空集合）
     */
    @Select("SELECT * FROM t_farm_sow_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND stat_date >= #{fromDate} "
        + " ORDER BY stat_date ASC")
    List<SowRecord> selectRangeAsc(@Param("tenantId") String tenantId,
                                   @Param("fromDate") LocalDate fromDate);
}
