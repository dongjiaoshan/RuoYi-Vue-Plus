package org.dromara.djs.warehouse.cross.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.domain.vo.TodayBarVo;

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
     * 燎毛产品逐项入库阶段乐观锁：bar_info.status pending_singe/singing → singing（燎毛中，FIX-WMS-MP-BURN-001）。
     *
     * <p>客户 6/11 新需求：产品逐个入库时只推进到中间态 {@code singing}（不直推 {@code in_stock}），
     * 解决「多产品逐个入库到第 2 个抛白条状态不符」的现状 bug。bar 终态由「处理完成」按钮调
     * {@link #updateStatusToSinged} 推进。WHERE status IN ('pending_singe','singing') 幂等 +
     * 兜并发；首次入库回填 in_time/in_method=1，后续幂等推进。</p>
     */
    @Update("UPDATE t_warehouse_bar_info "
        + "   SET status='singing', in_time=#{inTime}, in_method=1,"
        + "       update_by=#{userId}, update_time=NOW() "
        + " WHERE id = #{id} AND status IN ('pending_singe','singing') AND del_flag = '0'")
    int updateStatusToSinging(@Param("id") Long id,
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

    /**
     * 分页查「当天确认收货白条」（FIX-STORE-TRACE-BAR-001 门店猪肉追溯 picker 口径）。
     *
     * <p>口径：{@code status='in_stock'}（已入库 / 确认收货）且 {@code DATE(in_time)=CURDATE()} 的白条，
     * 含外购（bar_info 即白条主表，不按业态过滤）。按 in_time DESC 排序（最新入库排前）。
     * 当天无白条入库 → 空结果（picker 显示「暂无当天确认收货白条」，属正常态非 bug）。</p>
     *
     * <p>显式 {@code tenant_id='1001' AND del_flag='0'}（V1 单租户，原生 SQL 不自动注入）。</p>
     *
     * @param page 分页参数（MP 分页拦截器填充 total / 切片）
     * @return 当天 in_stock 白条分页（id / barId / earNo / inWeight / inTime / status）
     */
    @Select("""
        SELECT id, bar_id AS barId, ear_no AS earNo, in_weight AS inWeight, in_time AS inTime, status
          FROM t_warehouse_bar_info
         WHERE tenant_id = '1001'
           AND del_flag = '0'
           AND status = 'in_stock'
           AND DATE(in_time) = CURDATE()
         ORDER BY in_time DESC, id DESC
        """)
    IPage<TodayBarVo> selectTodayInStockBarPage(IPage<TodayBarVo> page);

}
