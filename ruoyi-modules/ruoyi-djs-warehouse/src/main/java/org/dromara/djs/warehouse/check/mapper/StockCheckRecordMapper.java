package org.dromara.djs.warehouse.check.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.check.domain.StockCheckRecord;
import org.dromara.djs.warehouse.check.domain.vo.StockCheckRecordVo;

/**
 * 盘点记录 Mapper（WMS-STOCK-001）。
 *
 * @author djs
 * @since WMS-STOCK-001
 */
public interface StockCheckRecordMapper extends BaseMapperPlus<StockCheckRecord, StockCheckRecordVo> {

    /**
     * 库位级业务锁核心查询：指定库位下是否存在「进行中」（{@code check_status='in_progress'}）的盘点单头。
     *
     * <p>由 stock_flow 写入入口（{@code MatFlowServiceImpl} / {@code PigBurnRecordServiceImpl}）调用，
     * 命中则抛 ServiceException 拒绝出入库。只看 header 行（{@code is_header=1}）；
     * {@code tenant_id} 由 MP 多租户拦截器在 final SQL 阶段注入，应用层无需显式 WHERE。</p>
     *
     * @param locationId 库位 ID
     * @return &gt;0 表示该库位有进行中盘点单（被锁）
     */
    @Select("SELECT COUNT(1) FROM t_warehouse_check_record "
        + "WHERE location_id = #{locationId} "
        + "  AND is_header = 1 "
        + "  AND check_status = 'in_progress' "
        + "  AND del_flag = '0'")
    long countActiveByLocation(@Param("locationId") Long locationId);

}
