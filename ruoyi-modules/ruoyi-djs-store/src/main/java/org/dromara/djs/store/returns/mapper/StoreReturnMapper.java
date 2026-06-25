package org.dromara.djs.store.returns.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.store.returns.domain.StoreReturn;
import org.dromara.djs.store.returns.domain.vo.StoreReturnVo;

/**
 * 门店退回管理 Mapper（STR-RETURN-001）。
 *
 * @author djs
 * @since STR-RETURN-001
 */
public interface StoreReturnMapper extends BaseMapperPlus<StoreReturn, StoreReturnVo> {

    /**
     * 取产品库存最多的<b>有效</b>库位（退回入库确认未选库位时兜底，STORE-RETURN-UNIFY-001）。
     *
     * <p>JOIN {@code t_warehouse_location_info}（{@code del_flag='0'}）过滤掉已删库位——避免
     * {@code location_stock} 残留指向已删库位的幽灵行（stock 多但库位不存在）被选中导致 inbound 报
     * 「库位不存在或已删除」。按库存降序、库位 id 升序取首个有效库位；产品无任何有效库存库位返 null。</p>
     *
     * <p>租户单租户显式 {@code tenant_id='1001'}（V1 未启全局 MP 拦截器）。</p>
     *
     * @param productId 入库产品 ID（果蔬已转原材料）
     * @return 库存最多的有效 location_id，或 null
     */
    @Select("SELECT ls.location_id FROM t_warehouse_location_stock ls "
        + "JOIN t_warehouse_location_info li ON li.id = ls.location_id AND li.del_flag = '0' "
        + "WHERE ls.product_id = #{productId} AND ls.del_flag = '0' AND ls.tenant_id = '1001' "
        + "ORDER BY ls.product_stock DESC, ls.location_id ASC LIMIT 1")
    Long selectStockMostValidLocation(@Param("productId") Long productId);
}
