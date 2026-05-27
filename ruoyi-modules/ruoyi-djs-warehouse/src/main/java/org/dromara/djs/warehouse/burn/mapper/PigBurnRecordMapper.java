package org.dromara.djs.warehouse.burn.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.burn.domain.PigBurnRecord;
import org.dromara.djs.warehouse.burn.domain.vo.PigBurnRecordVo;

/**
 * 燎毛工序记录 Mapper（WMS-PIG-001）。
 *
 * @author djs
 * @since WMS-PIG-001
 */
public interface PigBurnRecordMapper extends BaseMapperPlus<PigBurnRecord, PigBurnRecordVo> {

    /**
     * 查询今日（{@code burn_id LIKE 'BURN{yyMMdd}%'}）已用最大序号。
     *
     * <p>用于 inline 生成 {@code burnId}（{@code BURN+YYMMDD+4 位}）：</p>
     * <pre>
     *   SELECT MAX(burn_id) FROM ... WHERE burn_id LIKE 'BURN260604%'
     *   返回 null 时下一序号 = 1；返回 'BURN2606040003' 时下一序号 = 4
     * </pre>
     *
     * <p>并发安全 — service 层在事务里串行调用 + UNIQUE (tenant_id, burn_id, del_unique) 兜底；
     * 并发抢同一序号的极小概率场景由 SQLIntegrityConstraintViolationException 触发事务回滚 + mp 端重试。</p>
     */
    @Select("SELECT MAX(burn_id) FROM t_warehouse_pig_burn_record "
        + "WHERE burn_id LIKE CONCAT('BURN', #{yyMMdd}, '%') AND del_flag = '0'")
    String selectMaxBurnIdByDate(@Param("yyMMdd") String yyMMdd);

}
