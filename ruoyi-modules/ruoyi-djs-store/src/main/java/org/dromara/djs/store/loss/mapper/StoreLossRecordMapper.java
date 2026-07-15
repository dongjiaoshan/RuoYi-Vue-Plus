package org.dromara.djs.store.loss.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.store.loss.domain.StoreLossRecord;
import org.dromara.djs.store.loss.domain.vo.StoreLossRecordVo;

/**
 * 门店损耗记录 Mapper（DENGBO-R15）。
 *
 * @author djs
 * @since DENGBO-R15
 */
public interface StoreLossRecordMapper extends BaseMapperPlus<StoreLossRecord, StoreLossRecordVo> {

    /**
     * 物理删除指定租户指定日的全部损耗记录（含已软删行）。
     *
     * <p>定时任务重跑幂等专用：唯一键 {@code (tenant_id, store_id, product_id, loss_date, loss_type)}
     * 不含 {@code del_flag}，若走 {@code @TableLogic} 软删，旧行仍占唯一键 → 重插同组合 Duplicate entry。
     * 故重跑前对该日旧行做物理删（彻底清空该日、不残留），再重插当日最新值。</p>
     *
     * @param tenantId 租户 id
     * @param lossDate 损耗日期
     * @return 删除行数
     */
    @Delete("DELETE FROM t_store_loss_record WHERE tenant_id = #{tenantId} AND loss_date = #{lossDate}")
    int physicalDeleteByDate(@Param("tenantId") String tenantId, @Param("lossDate") java.time.LocalDate lossDate);
}
