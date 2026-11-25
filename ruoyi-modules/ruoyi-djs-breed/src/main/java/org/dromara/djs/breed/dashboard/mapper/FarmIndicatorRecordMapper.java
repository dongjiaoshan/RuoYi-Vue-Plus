package org.dromara.djs.breed.dashboard.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.dashboard.domain.FarmIndicatorRecord;
import org.dromara.djs.breed.dashboard.domain.vo.FarmIndicatorRecordVo;

import java.time.LocalDate;

/**
 * 养殖农场日数据记录 Mapper（BRD-STAT-001，表 {@code t_farm_indicator_record}）。
 *
 * @author djs
 * @since BRD-STAT-001
 */
public interface FarmIndicatorRecordMapper extends BaseMapperPlus<FarmIndicatorRecord, FarmIndicatorRecordVo> {
    /** 锁定前一日报，避免相邻日期重算交错时读到将被替换的期末数。 */
    @Select("""
        SELECT end_production_sow_count, end_fattening_count
          FROM t_farm_indicator_record
         WHERE tenant_id = #{tenantId} AND stat_date = #{day} AND del_flag = '0'
         FOR UPDATE
        """)
    FarmIndicatorRecord selectEndStockForUpdate(String tenantId, LocalDate day);

    /** 单日补跑时同步已存在的次日日报期初值；次日缺报时不造行。 */
    @Update("""
        UPDATE t_farm_indicator_record
           SET begin_production_sow_count = #{productionSows}, begin_fattening_count = #{fattening}
         WHERE tenant_id = #{tenantId} AND stat_date = #{day} AND del_flag = '0'
        """)
    int updateBeginningStock(String tenantId, LocalDate day, int productionSows, int fattening);
}
