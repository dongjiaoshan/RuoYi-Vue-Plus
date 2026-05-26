package org.dromara.djs.breed.med.record.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.med.record.domain.MedRecord;
import org.dromara.djs.breed.med.record.domain.vo.MedRecordVo;
import org.dromara.djs.breed.med.record.domain.vo.UsableBatchVo;

import java.util.List;

/**
 * 用药治疗流水 Mapper（BRD-MED-003）。
 *
 * <p>除 MP 通用 CRUD 外，提供 {@link #selectUsableBatchesByPig} 用于 mp 端
 * "当前用户 3 天内已领 + 剩余量 > 0" 的批次下拉，避免前端手输 batchId 导致
 * snowflake 截断（契约 2）。</p>
 *
 * @author djs
 * @since BRD-MED-003
 */
public interface MedRecordMapper extends BaseMapperPlus<MedRecord, MedRecordVo> {

    /**
     * 查"3 天内已领 + 剩余量 > 0"的可用批次列表（mp 端 picker 数据源）。
     *
     * <p>SQL 思路：</p>
     * <pre>
     * SELECT 最近一次该 batch 的 use 领用记录 + JOIN medicine info + batch
     * FROM t_breed_medicine_usage u
     *   JOIN t_breed_medicine_batch b ON b.id = u.batch_id AND b.del_flag = '0' AND b.quantity > 0
     *   JOIN t_breed_medicine_info  m ON m.id = u.medicine_id AND m.del_flag = '0'
     * WHERE u.usage_type = 'use'
     *   AND u.del_flag = '0'
     *   AND u.use_date >= CURDATE() - INTERVAL 3 DAY
     *   AND (#{operatorId} IS NULL OR u.create_by = #{operatorId})
     * GROUP BY u.batch_id  -- DISTINCT batch，取最新一次领用 max(use_date)
     * </pre>
     *
     * <p>{@code operatorId} 为 null 时返回全场最近 3 天可用批次；非 null 时仅返回
     * 该 user 领过的（mp 端登录用户 LoginHelper.getUserId 传入）。</p>
     *
     * @param operatorId 操作人 user_id（mp 端登录用户；可 null = 不限）
     * @return 可用批次列表（每个 batch 1 行）
     */
    @Select("""
        SELECT u.batch_id     AS batchId,
               u.medicine_id  AS medicineId,
               m.medicine_name AS medicineName,
               b.batch_no     AS batchNo,
               b.quantity     AS quantity,
               MAX(u.id)      AS usageId,
               DATE_FORMAT(MAX(u.use_date), '%Y-%m-%d') AS lastUseDate
        FROM t_breed_medicine_usage u
            INNER JOIN t_breed_medicine_batch b ON b.id = u.batch_id AND b.del_flag = '0' AND b.quantity > 0
            INNER JOIN t_breed_medicine_info  m ON m.id = u.medicine_id AND m.del_flag = '0'
        WHERE u.usage_type = 'use'
          AND u.del_flag = '0'
          AND u.use_date >= CURDATE() - INTERVAL 3 DAY
          AND (#{operatorId} IS NULL OR u.create_by = #{operatorId})
        GROUP BY u.batch_id, u.medicine_id, m.medicine_name, b.batch_no, b.quantity
        ORDER BY lastUseDate DESC, b.batch_no ASC
        """)
    List<UsableBatchVo> selectUsableBatchesByPig(@Param("operatorId") Long operatorId);

}
