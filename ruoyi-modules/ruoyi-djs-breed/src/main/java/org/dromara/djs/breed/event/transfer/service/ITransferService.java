package org.dromara.djs.breed.event.transfer.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.transfer.domain.bo.TransferBo;
import org.dromara.djs.breed.event.transfer.domain.query.TransferQuery;
import org.dromara.djs.breed.event.transfer.domain.vo.PigTransferVo;

/**
 * 转移事件 Service（BRD-EVENT-004 TRANSFER）。
 *
 * @author djs
 * @since BRD-EVENT-004
 */
public interface ITransferService {

    PigTransferVo recordTransfer(TransferBo bo);

    TableDataInfo<PigTransferVo> queryPage(TransferQuery query, PageQuery pageQuery);
}
