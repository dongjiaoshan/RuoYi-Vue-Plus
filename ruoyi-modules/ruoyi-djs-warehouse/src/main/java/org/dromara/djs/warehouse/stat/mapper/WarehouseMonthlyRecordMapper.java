package org.dromara.djs.warehouse.stat.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.stat.domain.WarehouseMonthlyRecord;
import org.dromara.djs.warehouse.stat.domain.vo.WarehouseMonthlyRecordVo;

/**
 * 仓库月数据记录 Mapper（WMS-STAT-001，表 {@code t_warehouse_monthly_record}）。
 *
 * @author djs
 * @since WMS-STAT-001
 */
public interface WarehouseMonthlyRecordMapper extends BaseMapperPlus<WarehouseMonthlyRecord, WarehouseMonthlyRecordVo> {
}
