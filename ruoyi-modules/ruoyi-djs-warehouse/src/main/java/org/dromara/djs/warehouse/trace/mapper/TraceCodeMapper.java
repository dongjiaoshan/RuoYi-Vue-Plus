package org.dromara.djs.warehouse.trace.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.trace.domain.TraceCode;
import org.dromara.djs.warehouse.trace.domain.vo.TraceCodeVo;

/**
 * 追溯码主表 Mapper（TRC-CORE-001）。
 *
 * <p>下游 TRC-ADMIN-001（D14）直接复用本 mapper 的 {@code selectVoPage / selectVoList} 做追溯码管理列表，
 * 不另建表 / mapper。</p>
 *
 * @author djs
 * @since TRC-CORE-001
 */
public interface TraceCodeMapper extends BaseMapperPlus<TraceCode, TraceCodeVo> {

}
