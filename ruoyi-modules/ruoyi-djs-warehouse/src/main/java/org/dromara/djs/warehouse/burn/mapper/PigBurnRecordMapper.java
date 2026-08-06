package org.dromara.djs.warehouse.burn.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
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

    /**
     * V6-R43 燎毛间产品入库重量调整：按差额同步燎毛记录的入库合计重量。
     *
     * <p>{@code burn_weight} = 该次提交<b>各产出行重量之和</b>（一次提交可含半只 / 猪头 / 猪蹄多行），
     * 所以只能按调整差额（delta）加减，不能用单行的新重量覆盖 —— 覆盖会把同批其它产品的重量抹掉。</p>
     *
     * @return affectedRows（1=成功；0=燎毛记录不存在 / 已软删）
     */
    @Update("UPDATE t_warehouse_pig_burn_record "
        + "   SET burn_weight = COALESCE(burn_weight, 0) + #{delta},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE id = #{id} AND del_flag = '0'")
    int adjustBurnWeight(@Param("id") Long id,
                         @Param("delta") java.math.BigDecimal delta,
                         @Param("userId") Long userId);

    /**
     * V6-R43 历史行兜底：按耳号 + 燎毛时间反查燎毛记录 id。
     *
     * <p>{@code submitBurnRecord} 把同一个 {@code burnTime} 同时写进 {@code product_inhouse.produce_time}
     * 与 {@code pig_burn_record.burn_time}，故这一对是产出行 → 燎毛记录的精确对应关系。
     * 仅用于 V202608300500 回填未命中的历史行（新数据直接走 {@code product_inhouse.burn_record_id}）；
     * 外购白条 {@code ear_no} 为 NULL → NULL 安全比较。</p>
     *
     * @return 燎毛记录 id；查不到返 null
     */
    @Select("SELECT id FROM t_warehouse_pig_burn_record "
        + " WHERE del_flag = '0' AND burn_time = #{burnTime} AND ear_no <=> #{earNo} "
        + " ORDER BY id LIMIT 1")
    Long selectIdByEarNoAndBurnTime(@Param("earNo") String earNo,
                                    @Param("burnTime") java.util.Date burnTime);

}
