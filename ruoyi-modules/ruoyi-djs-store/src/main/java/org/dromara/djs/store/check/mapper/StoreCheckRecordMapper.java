package org.dromara.djs.store.check.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.store.check.domain.StoreCheckRecord;
import org.dromara.djs.store.check.domain.vo.StoreCheckRecordVo;

/**
 * 门店盘点记录 Mapper（STR-STOCK-001）。
 *
 * @author djs
 * @since STR-STOCK-001
 */
public interface StoreCheckRecordMapper extends BaseMapperPlus<StoreCheckRecord, StoreCheckRecordVo> {

    /**
     * 门店级业务锁核心查询：指定门店下是否存在「进行中」（{@code check_status='in_progress'}）的盘点单头。
     *
     * <p>由门店销售出库写入入口（STR-OP-001 {@code StoreSaleRecordServiceImpl}）调用，
     * 命中则抛 ServiceException 拒绝出库。只看 header 行（{@code is_header=1}）；
     * {@code tenant_id} 由 MP 多租户拦截器在 final SQL 阶段注入，但 V1 拦截器未启用，
     * 此 {@code @Select} 手写 SQL 显式带 {@code tenant_id} 防 V2 启用后原生 SQL 不被注入（prompt-be §0.5 规则）。</p>
     *
     * @param storeId   门店 ID
     * @param productId 产品 ID（&gt; 0 时锁该 store 下该 product 的 line；传 null 仅看 header 是否进行中）
     * @param tenantId  租户 ID（应用层显式传入，避免依赖拦截器注入原生 SQL）
     * @return &gt;0 表示该门店（该产品）有进行中盘点单（被锁）
     */
    @Select("<script>"
        + "SELECT COUNT(1) FROM t_store_check_record "
        + "WHERE store_id = #{storeId} "
        + "  AND check_status = 'in_progress' "
        + "  AND del_flag = '0' "
        + "  AND tenant_id = #{tenantId} "
        + "  <when test='productId != null'> "
        + "    AND ( (is_header = 1) OR (is_header = 0 AND product_id = #{productId}) ) "
        + "  </when> "
        + "  <when test='productId == null'> "
        + "    AND is_header = 1 "
        + "  </when> "
        + "</script>")
    long countActive(@Param("storeId") Long storeId,
                     @Param("productId") Long productId,
                     @Param("tenantId") String tenantId);

}
