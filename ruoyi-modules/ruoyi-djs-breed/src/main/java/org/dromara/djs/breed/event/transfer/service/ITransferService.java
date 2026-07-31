package org.dromara.djs.breed.event.transfer.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.transfer.domain.bo.ToFattenBo;
import org.dromara.djs.breed.event.transfer.domain.bo.TransferBatchBo;
import org.dromara.djs.breed.event.transfer.domain.bo.TransferBo;
import org.dromara.djs.breed.event.transfer.domain.query.TransferQuery;
import org.dromara.djs.breed.event.transfer.domain.vo.PigTransferVo;

import java.util.List;

/**
 * 转移事件 Service（BRD-EVENT-004 TRANSFER）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
public interface ITransferService {

    PigTransferVo recordTransfer(TransferBo bo);

    /**
     * 批量转移（BRD-FIX-MP-EVENT-LEAVE-IA-001）。逐头复用单只
     * {@link #recordTransfer}，每只都触发完整 side effect（barn/pen 切换 +
     * piglet→fattening 的 pig_type 切换），整批同一事务原子提交。
     *
     * @return 每只的转移结果（顺序与入参 earNos 对应）
     */
    List<PigTransferVo> recordTransferBatch(TransferBatchBo bo);

    /**
     * 后备种母猪「转为育肥猪」（admin row162）。
     *
     * <p>仅允许 {@code pig_type='sow'} 且 {@code current_status='HB'} 的猪；执行后：
     * 状态 HB → YF（育肥）、类型 sow → fattening、栋舍/栏位落到入参位置（空则沿用当前位置），
     * 并写一条转移记录 + 事件台账。</p>
     *
     * <p>事件台账固定写两条：先 {@code TRANSFER} 再 {@code TO_FATTEN}。转育肥表单本身就是一次转移
     * （录转移日期 / 负责人 / 目标栋舍栏位），与转移记录一一对应；位置没变也照记，否则「表单默认
     * 预填当前位置、用户只改日期负责人」这条最常见路径在台账上按「转移」查不到（见 admin row163）。</p>
     *
     * @param bo 转移日期 / 负责人 / 目标栋舍 / 目标栏位
     * @return 本次写入的转移记录
     */
    PigTransferVo toFatten(ToFattenBo bo);

    TableDataInfo<PigTransferVo> queryPage(TransferQuery query, PageQuery pageQuery);
}
