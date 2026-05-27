package org.dromara.djs.warehouse.flow.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.flow.domain.StockFlow;

/**
 * 出入库流水 Mapper（占位，仅 INSERT 用）。
 *
 * <p>本 ticket WMS-PIG-001 燎毛工序提交时 INSERT 出库流水；D9 WMS-MAT-001 落地完整 CRUD 后会补 VO + 查询方法。</p>
 *
 * @author djs
 * @since WMS-PIG-001
 */
public interface StockFlowMapper extends BaseMapperPlus<StockFlow, StockFlow> {
}
