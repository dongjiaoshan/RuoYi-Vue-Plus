package org.dromara.djs.warehouse.trace.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.trace.domain.TraceEvent;
import org.dromara.djs.warehouse.trace.domain.vo.TraceEventVo;

/**
 * 追溯事件流水 Mapper（TRC-CORE-001），immutable append-only。
 *
 * <p>service 只用 {@code insert} 写流水 + {@code selectVoList} 查事件链，绝不 update / delete。
 * 下游 TRC-ADMIN-001（D14）复用本 mapper 查某追溯码的事件链。</p>
 *
 * @author djs
 * @since TRC-CORE-001
 */
public interface TraceEventMapper extends BaseMapperPlus<TraceEvent, TraceEventVo> {

}
