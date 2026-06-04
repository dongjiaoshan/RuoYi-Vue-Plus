package org.dromara.djs.warehouse.cross.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.cross.domain.BarInfo;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 白条 Mapper minimal（跨域共享 / D9 closing Group B 从 cut/mapper 挪入 cross/mapper）。
 *
 * @author djs
 * @since WMS-PIG-002
 */
public interface BarInfoMapper extends BaseMapperPlus<BarInfo, BarInfo> {

    /**
     * 燎毛入库阶段乐观锁：bar_info.status pending_singe/singing → in_stock（D12X-MP-BURN-IA-001）。
     *
     * <p>WHERE status IN ('pending_singe','singing') 保证并发燎毛工只有一个成功推进白条到入库态。
     * affectedRows==0 → 调用方抛"白条状态不符（已入库或不在待燎毛态）"。
     * 同步回填 in_weight（各类型入库重量合计）/ in_time / in_method=1（1=燎毛间）。</p>
     */
    @Update("UPDATE t_warehouse_bar_info "
        + "   SET status='in_stock', in_weight=#{inWeight}, in_time=#{inTime}, in_method=1,"
        + "       update_by=#{userId}, update_time=NOW() "
        + " WHERE id = #{id} AND status IN ('pending_singe','singing') AND del_flag = '0'")
    int updateStatusToInStock(@Param("id") Long id,
                              @Param("inWeight") BigDecimal inWeight,
                              @Param("inTime") Date inTime,
                              @Param("userId") Long userId);

    /**
     * pickup 阶段乐观锁：bar_info.status in_stock → pending_cut。
     *
     * <p>WHERE status='in_stock' 保证并发分割师同时领用同一条白条只有一个成功。
     * affectedRows==0 → 抛"白条已被领用或状态不符"。</p>
     */
    @Update("UPDATE t_warehouse_bar_info "
        + "   SET status='pending_cut', update_by=#{userId}, update_time=NOW() "
        + " WHERE id = #{id} AND status = 'in_stock' AND del_flag = '0'")
    int updateStatusToPendingCut(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * cutOut 首次提交：bar_info.status pending_cut → cutting。
     *
     * <p>同事务幂等：如已是 cutting 则 affectedRows==0，调用方忽略。</p>
     */
    @Update("UPDATE t_warehouse_bar_info "
        + "   SET status='cutting', update_by=#{userId}, update_time=NOW() "
        + " WHERE id = #{id} AND status = 'pending_cut' AND del_flag = '0'")
    int updateStatusToCutting(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * cutDone 阶段：bar_info.status cutting → cut_done + 写入出库相关字段。
     *
     * <p>{@code outMethod}=2（分割间）；{@code acidRemoveTime}=分钟数；{@code acidRemoveLoss}=滴水损失。</p>
     */
    @Update("UPDATE t_warehouse_bar_info "
        + "   SET status='cut_done', out_time=#{outTime}, out_weight=#{outWeight},"
        + "       out_method=2, acid_remove_time=#{acidRemoveTime}, acid_remove_loss=#{acidRemoveLoss},"
        + "       update_by=#{userId}, update_time=NOW() "
        + " WHERE id = #{id} AND status = 'cutting' AND del_flag = '0'")
    int updateStatusToCutDone(@Param("id") Long id,
                              @Param("outTime") Date outTime,
                              @Param("outWeight") BigDecimal outWeight,
                              @Param("acidRemoveTime") Integer acidRemoveTime,
                              @Param("acidRemoveLoss") BigDecimal acidRemoveLoss,
                              @Param("userId") Long userId);

}
