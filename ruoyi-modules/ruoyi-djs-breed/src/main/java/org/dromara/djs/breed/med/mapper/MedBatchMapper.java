package org.dromara.djs.breed.med.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.med.domain.MedBatch;
import org.dromara.djs.breed.med.domain.vo.MedBatchVo;

import java.math.BigDecimal;

/**
 * 药品批次 Mapper（BRD-MED-001）。
 *
 * <p>BRD-MED-002 追加 {@link #decrementQuantity} / {@link #incrementQuantity}
 * 用于领用/退回/损耗台账原子扣减或归还。</p>
 *
 * @author djs
 * @since BRD-MED-001
 */
public interface MedBatchMapper extends BaseMapperPlus<MedBatch, MedBatchVo> {

    /**
     * 原子扣减批次库存（用于 use / loss）。
     *
     * <p>WHERE 子句 {@code quantity >= #{qty}} 保证 race condition 下不会扣到负数：
     * 两个并发事务同时来扣，DB 会因为后到者读取到的 quantity 已被前者更新而 WHERE 不匹配返 0 行。
     * service 端收到 0 行后抛 ServiceException("medicine.batch.insufficient")。</p>
     *
     * <p>软删过滤 {@code del_flag='0'}；多租户隔离由 MP 全局拦截器自动注入
     * {@code tenant_id} —— 但本方法用 {@code @Update} 原生 SQL 不走 MP 拦截器，
     * 由 service 调用前显式将 {@code TenantHelper} 上下文设置好；本 DDL 索引
     * {@code idx_batch_id} + {@code id} PK 保证仅命中本租户行（id 全局雪花唯一）。</p>
     *
     * @param batchId 批次 ID
     * @param qty     扣减量（必须 > 0）
     * @return 受影响行数（成功 1，库存不足 0）
     */
    @Update("UPDATE t_breed_medicine_batch SET quantity = quantity - #{qty} "
        + "WHERE id = #{batchId} AND quantity >= #{qty} AND del_flag = '0'")
    int decrementQuantity(@Param("batchId") Long batchId, @Param("qty") BigDecimal qty);

    /**
     * 原子归还批次库存（用于 return）。
     *
     * @param batchId 批次 ID
     * @param qty     归还量（必须 > 0）
     * @return 受影响行数
     */
    @Update("UPDATE t_breed_medicine_batch SET quantity = quantity + #{qty} "
        + "WHERE id = #{batchId} AND del_flag = '0'")
    int incrementQuantity(@Param("batchId") Long batchId, @Param("qty") BigDecimal qty);

}
