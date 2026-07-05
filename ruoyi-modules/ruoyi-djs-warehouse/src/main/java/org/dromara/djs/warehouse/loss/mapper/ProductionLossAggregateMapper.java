package org.dromara.djs.warehouse.loss.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 生产损耗日聚合查询 Mapper（row38，邓博）。
 *
 * <p>按「产品 + 自然日」聚合生产损耗残差四项量。生产损耗 = 产品领用 − 产品退回 − 产品录入损耗 − 产品饲喂
 * （邓博 row38 口径），残差 &gt; 0 由 service 落一条 {@code loss_type='production_loss'} 到损耗流水。</p>
 *
 * <p>与 {@code WarehouseStatAggregateMapper} 同理：自定义聚合 @Select 多租户拦截器不保证注入
 * {@code tenant_id}，故 WHERE 显式带 {@code tenant_id}；日期列 DATETIME 按 {@code DATE(col)=#{statDate}} 截自然日。</p>
 *
 * <h3>源表 → 量映射</h3>
 * <ul>
 *   <li>{@code t_warehouse_stock_flow}：领用 {@code prod_pick_out} / 退回 {@code prod_return_in} / 饲喂 {@code feed_out}（change_quantity, flow_date）</li>
 *   <li>{@code t_warehouse_loss_flow}：录入损耗 {@code manual_loss}（loss_weight, loss_date）</li>
 * </ul>
 *
 * @author djs
 */
@Mapper
public interface ProductionLossAggregateMapper {

    /**
     * 按产品聚合某自然日的 产品领用 / 退回 / 饲喂 量（来自库存流水）。
     *
     * @param tenantId 租户
     * @param statDate 目标自然日 yyyy-MM-dd
     * @return 每产品一行：{@code {productId, pickOut, returnIn, feedOut}}
     */
    @Select("""
        SELECT product_id AS productId,
               COALESCE(SUM(CASE WHEN flow_type = 'prod_pick_out'  THEN change_quantity ELSE 0 END), 0) AS pickOut,
               COALESCE(SUM(CASE WHEN flow_type = 'prod_return_in' THEN change_quantity ELSE 0 END), 0) AS returnIn,
               COALESCE(SUM(CASE WHEN flow_type = 'feed_out'       THEN change_quantity ELSE 0 END), 0) AS feedOut
        FROM t_warehouse_stock_flow
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(flow_date) = #{statDate}
          AND product_id IS NOT NULL
          AND flow_type IN ('prod_pick_out', 'prod_return_in', 'feed_out')
        GROUP BY product_id
        """)
    List<Map<String, Object>> selectProductFlowAgg(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 按产品聚合某自然日的 产品录入损耗（{@code manual_loss}）量。
     *
     * @param tenantId 租户
     * @param statDate 目标自然日 yyyy-MM-dd
     * @return 每产品一行：{@code {productId, manualLoss}}
     */
    @Select("""
        SELECT product_id AS productId, COALESCE(SUM(loss_weight), 0) AS manualLoss
        FROM t_warehouse_loss_flow
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND DATE(loss_date) = #{statDate}
          AND loss_type = 'manual_loss'
          AND product_id IS NOT NULL
        GROUP BY product_id
        """)
    List<Map<String, Object>> selectProductManualLoss(@Param("tenantId") String tenantId, @Param("statDate") String statDate);
}
