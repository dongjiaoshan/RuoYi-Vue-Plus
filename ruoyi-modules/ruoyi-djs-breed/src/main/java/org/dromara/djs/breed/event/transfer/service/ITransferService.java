package org.dromara.djs.breed.event.transfer.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
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

    TableDataInfo<PigTransferVo> queryPage(TransferQuery query, PageQuery pageQuery);
}
