package org.dromara.djs.breed.med.record.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.med.record.domain.MedRecord;
import org.dromara.djs.breed.med.record.domain.vo.MedRecordVo;

import java.util.List;

/**
 * 用药治疗流水 Mapper（BRD-MED-003）。
 *
 * <p>除 MP 通用 CRUD 外，提供 {@link #selectRecentUsedMedicineIds} 用于 mp 端
 * "当前用户 3 天内已领用药品" 下拉（药品无批次说法 · 迁药品维度）——只取领用台账里
 * 的 distinct medicine_id，药品名/单位/规格/库存由仓库 provider facade 解析。</p>
 *
 * @author djs
 * @since BRD-MED-003
 */
public interface MedRecordMapper extends BaseMapperPlus<MedRecord, MedRecordVo> {

    /**
     * 查"3 天内已领用"的 distinct 药品 id 列表（mp「使用药品」picker 数据源）。
     *
     * <p>药品彻底废弃批次维度：只从领用台账 {@code t_breed_medicine_usage} 取 {@code usage_type='use'}、
     * 近 3 天、（可按 operatorId 限定当前 mp 用户）的 distinct {@code medicine_id}，按最近领用日倒序。
     * 药品详情（名/单位/规格/库存）由 {@code MedicineStockProvider.listMedicineProductsByIds}
     * 从仓库商品解析，不再 JOIN 已弃用的 {@code t_breed_medicine_info}/{@code t_breed_medicine_batch}。</p>
     *
     * <p>{@code operatorId} 为 null 时返回全场近 3 天已领用药品（admin 端）；
     * 非 null 时仅该 user 领过的（mp 端登录用户 LoginHelper.getUserId 传入）。</p>
     *
     * @param operatorId 操作人 user_id（mp 端登录用户；可 null = 不限）
     * @return 药品 id 列表（按最近领用日倒序，DISTINCT）
     */
    @Select("""
        SELECT u.medicine_id
        FROM t_breed_medicine_usage u
        WHERE u.usage_type = 'use'
          AND u.del_flag = '0'
          AND u.medicine_id IS NOT NULL
          AND u.use_date >= CURDATE() - INTERVAL 3 DAY
          AND (#{operatorId} IS NULL OR u.create_by = #{operatorId})
        GROUP BY u.medicine_id
        ORDER BY MAX(u.use_date) DESC, MAX(u.id) DESC
        """)
    List<Long> selectRecentUsedMedicineIds(@Param("operatorId") Long operatorId);

}
