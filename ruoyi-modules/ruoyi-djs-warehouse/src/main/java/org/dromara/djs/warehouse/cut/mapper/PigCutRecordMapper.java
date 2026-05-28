package org.dromara.djs.warehouse.cut.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.cut.domain.PigCutRecord;
import org.dromara.djs.warehouse.cut.domain.vo.PigCutRecordVo;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 分割工序记录 Mapper（WMS-PIG-002）。
 *
 * @author djs
 * @since WMS-PIG-002
 */
public interface PigCutRecordMapper extends BaseMapperPlus<PigCutRecord, PigCutRecordVo> {

    /**
     * 查询今日（{@code cut_id LIKE 'CUT{yyMMdd}%'}）已用最大序号。
     *
     * <p>同 burn_id inline 生成模式（D8 PigBurnRecordMapper#selectMaxBurnIdByDate）。
     * 并发安全 — service 层在事务里串行调用 + UNIQUE (tenant_id, cut_id, del_unique) 兜底；
     * 并发抢同一序号的极小概率场景由 SQLIntegrityConstraintViolationException 触发事务回滚 + mp 端重试。</p>
     */
    @Select("SELECT MAX(cut_id) FROM t_warehouse_pig_cut_record "
        + "WHERE cut_id LIKE CONCAT('CUT', #{yyMMdd}, '%') AND del_flag = '0'")
    String selectMaxCutIdByDate(@Param("yyMMdd") String yyMMdd);

    /**
     * cutOut 首次提交时推进 cut_status：picked → cutting + 写入 cut_start_time。
     *
     * <p>乐观锁：WHERE cut_status='picked'，affectedRows==0 = 状态已是 cutting 或被并发改动。
     * tenant_id 由 MP 拦截器自动注入。</p>
     */
    @Update("UPDATE t_warehouse_pig_cut_record "
        + "   SET cut_status='cutting', cut_start_time = #{cutStartTime},"
        + "       update_by = #{userId}, update_time = NOW() "
        + " WHERE id = #{id} AND cut_status = 'picked' AND del_flag = '0'")
    int updateStatusToCutting(@Param("id") Long id,
                              @Param("cutStartTime") Date cutStartTime,
                              @Param("userId") Long userId);

    /**
     * cutDone 提交时推进 cut_status：cutting → done + 写入 cut_done_time / drip_loss / acid_remove_minutes。
     *
     * <p>乐观锁：WHERE cut_status='cutting'。</p>
     */
    @Update("UPDATE t_warehouse_pig_cut_record "
        + "   SET cut_status='done', cut_done_time = #{cutDoneTime}, drip_loss = #{dripLoss},"
        + "       acid_remove_minutes = #{acidRemoveMinutes},"
        + "       remark = COALESCE(#{remark}, remark),"
        + "       proof_oss_ids = COALESCE(#{proofOssIds}, proof_oss_ids),"
        + "       update_by = #{userId}, update_time = NOW() "
        + " WHERE id = #{id} AND cut_status = 'cutting' AND del_flag = '0'")
    int updateStatusToDone(@Param("id") Long id,
                           @Param("cutDoneTime") Date cutDoneTime,
                           @Param("dripLoss") BigDecimal dripLoss,
                           @Param("acidRemoveMinutes") Integer acidRemoveMinutes,
                           @Param("remark") String remark,
                           @Param("proofOssIds") String proofOssIds,
                           @Param("userId") Long userId);

}
