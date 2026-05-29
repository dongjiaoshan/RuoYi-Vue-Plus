package org.dromara.djs.plant.farm.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.farm.domain.FarmRecords;
import org.dromara.djs.plant.farm.domain.vo.FarmRecordsVo;

/**
 * 农事记录 Mapper（PLT-WORK-001）。
 *
 * @author djs
 * @since PLT-WORK-001
 */
public interface FarmRecordsMapper extends BaseMapperPlus<FarmRecords, FarmRecordsVo> {

    /**
     * 取当日已生成 record_no 中序号最大值（用于 inline 业务码生成）。
     *
     * <p>format: {@code FRyyyyMMddNNNN}（NNNN 4 位序号）。本 mapper 直接取最大 record_no 字符串，
     * 由 service 层提取后 4 位 + 1 拼下一个号。MySQL 字典排序对固定长度数字串正确。</p>
     *
     * @param prefix 期望 {@code FRyyyyMMdd}（service 拼好传入）
     * @return 当日最大 record_no；当日 0 行时返 null
     */
    @Select("SELECT MAX(record_no) FROM t_plant_farm_records WHERE tenant_id = #{tenantId} AND record_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxRecordNoByPrefix(@Param("tenantId") String tenantId, @Param("prefix") String prefix);
}
