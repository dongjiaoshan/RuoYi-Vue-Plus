package org.dromara.djs.warehouse.cut.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.cut.domain.PigCutRecord;
import org.dromara.djs.warehouse.cut.domain.vo.PigCutRecordVo;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

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

    /**
     * 批量统计各白条的分割品总重（P7 统计 compute-on-read）。
     *
     * <p>口径：{@code product_inhouse.white_bar_id} 匹配且 JOIN {@code t_warehouse_product_info}
     * （{@code inhouse.product_id = pi.id} —— 注意 inhouse.product_id 存的是 product_info 雪花 id
     * 而非业务码字符串）判定 {@code pi.product_workshop=2}（分割车间产出），替代旧 {@code cut_part}
     * 非空判定（前端已弃 5 部位枚举，cut_part 仅作兜底可能为空）。按 {@code white_bar_id} 聚合，
     * 一次 IN 查询避免 N+1。返回每行 {@code {whiteBarId, totalWeight}}；无记录的白条不在结果中
     * （service 端按缺失处理）。</p>
     *
     * @param whiteBarIds 白条 id 集合（service 已去重 + 非空过滤）
     * @return 每行含 {@code whiteBarId}（Long）与 {@code totalWeight}（BigDecimal）
     */
    @Select("<script>"
        + "SELECT ih.white_bar_id AS whiteBarId, COALESCE(SUM(ih.product_weight), 0) AS totalWeight "
        + "  FROM t_warehouse_product_inhouse ih "
        + "  JOIN t_warehouse_product_info pi ON ih.product_id = pi.id AND pi.product_workshop = 2 "
        + " WHERE ih.del_flag = '0' "
        + "   AND ih.white_bar_id IN "
        + "   <foreach collection='whiteBarIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
        + " GROUP BY ih.white_bar_id"
        + "</script>")
    List<Map<String, Object>> sumCutProductWeightByWhiteBarIds(@Param("whiteBarIds") Collection<Long> whiteBarIds);

}
