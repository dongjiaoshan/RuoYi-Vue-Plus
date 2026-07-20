package org.dromara.djs.store.inventory.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.store.inventory.domain.StoreInventory;
import org.dromara.djs.store.inventory.domain.vo.StoreInventoryVo;

/**
 * 门店独立库存 Mapper（{@code t_store_inventory}，WSA-003）。
 *
 * <p>门店盘点期初读结存、期末 UPSERT 回写结存。读写逻辑在
 * {@link org.dromara.djs.store.ledger.service.impl.StoreDailyLedgerServiceImpl}，
 * 本 mapper 仅提供 MP CRUD。</p>
 *
 * @author djs
 * @since WSA-003
 */
public interface StoreInventoryMapper extends BaseMapperPlus<StoreInventory, StoreInventoryVo> {
}
