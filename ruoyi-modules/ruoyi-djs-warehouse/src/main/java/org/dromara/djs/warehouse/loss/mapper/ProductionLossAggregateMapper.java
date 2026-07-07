package org.dromara.djs.warehouse.loss.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 生产损耗日聚合查询 Mapper（row38，邓博）。
 *
 * <p>按「产品 + 自然日」聚合果蔬生产损耗残差五项量。果蔬生产损耗 = 果蔬领用 − 退回 − 录入损耗 − 饲喂 − 打包生产使用量
 * （邓博最终口径），残差 &gt; 0 由 service 落一条 {@code loss_type='production_loss'} 到损耗流水。
 * 全部按 {@code belong_type='vegetable'} 过滤（= 自产果蔬；外购商品/包材天然排除，符合「只算产品的」；
 * 猪肉/其他产品目前无生产损耗展示需求）。</p>
 *
 * <p>与 {@code WarehouseStatAggregateMapper} 同理：自定义聚合 @Select 多租户拦截器不保证注入
 * {@code tenant_id}，故 WHERE 显式带 {@code tenant_id}；日期列 DATETIME 按 {@code DATE(col)=#{statDate}} 截自然日。</p>
 *
 * <h3>源表 → 量映射（均果蔬口径）</h3>
 * <ul>
 *   <li>{@code t_warehouse_stock_flow}：领用 {@code prod_pick_out} / 退回 {@code prod_return_in} / 饲喂 {@code feed_out}（change_quantity, flow_date；JOIN 商品主数据按 vegetable 过滤）</li>
 *   <li>{@code t_warehouse_loss_flow}：录入损耗 {@code manual_loss}（loss_weight, loss_date；belong_type='vegetable'）</li>
 *   <li>{@code t_warehouse_product_production}：打包生产使用量 {@code material_consume}（缺省回退 product_weight，produce_date；JOIN 商品主数据按 vegetable 过滤）</li>
 * </ul>
 *
 * @author djs
 */
@Mapper
public interface ProductionLossAggregateMapper {

    /**
     * 按产品聚合某自然日的 果蔬产品领用 / 退回 / 饲喂 量（来自库存流水，belong_type='vegetable'）。
     * belong_type='vegetable' = 自产果蔬（外购商品 belong_type 空、包材非 vegetable，天然排除 → 只算产品的）；
     * stock_flow 无 belong_type 列，JOIN 商品主数据过滤。
     *
     * @param tenantId 租户
     * @param statDate 目标自然日 yyyy-MM-dd
     * @return 每产品一行：{@code {productId, pickOut, returnIn, feedOut}}
     */
    @Select("""
        SELECT sf.product_id AS productId,
               COALESCE(SUM(CASE WHEN sf.flow_type = 'prod_pick_out'  THEN sf.change_quantity ELSE 0 END), 0) AS pickOut,
               COALESCE(SUM(CASE WHEN sf.flow_type = 'prod_return_in' THEN sf.change_quantity ELSE 0 END), 0) AS returnIn,
               COALESCE(SUM(CASE WHEN sf.flow_type = 'feed_out'       THEN sf.change_quantity ELSE 0 END), 0) AS feedOut
        FROM t_warehouse_stock_flow sf
        JOIN t_warehouse_product_info p ON p.id = sf.product_id
        WHERE sf.del_flag = '0' AND sf.tenant_id = #{tenantId}
          AND DATE(sf.flow_date) = #{statDate}
          AND p.belong_type = 'vegetable'
          AND sf.flow_type IN ('prod_pick_out', 'prod_return_in', 'feed_out')
        GROUP BY sf.product_id
        """)
    List<Map<String, Object>> selectProductFlowAgg(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 按产品聚合某自然日的 果蔬产品录入损耗（{@code manual_loss}，belong_type='vegetable'）量。
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
          AND belong_type = 'vegetable'
        GROUP BY product_id
        """)
    List<Map<String, Object>> selectProductManualLoss(@Param("tenantId") String tenantId, @Param("statDate") String statDate);

    /**
     * 按<b>原料</b>产品聚合某自然日的 果蔬打包生产使用量（原料耗用，生产损耗残差减项，belong_type='vegetable'）。
     * = Σ {@code product_production.material_consume}（缺省回退 {@code product_weight}，均 kg），按 produce_date 归当日。
     *
     * <p><b>按 {@code material_id}（被消耗的原料）归集，非 product_id（成品）</b>：领用 {@code prod_pick_out}
     * 记在原料产品上（如「LT-原材料-胡萝卜」），打包消耗的也是该原料（material_id 指向它），成品是另一个产品
     * （「LT-产品-胡萝卜」，从不被领用）。故打包用量必须落到原料产品 id，才能与其领用净额对齐（原料损耗 =
     * 原料领用 − … − 该原料被打包消耗量）。material_id 为 NULL 的历史行（旧数据未记原料）不归集。
     * JOIN 商品主数据按 vegetable 过滤（= 自产果蔬原料）。</p>
     *
     * @param tenantId 租户
     * @param statDate 目标自然日 yyyy-MM-dd
     * @return 每原料产品一行：{@code {productId(=material_id), packUsage}}
     */
    @Select("""
        SELECT pp.material_id AS productId,
               COALESCE(SUM(COALESCE(pp.material_consume, pp.product_weight)), 0) AS packUsage
        FROM t_warehouse_product_production pp
        JOIN t_warehouse_product_info p ON p.id = pp.material_id
        WHERE pp.del_flag = '0' AND pp.tenant_id = #{tenantId}
          AND DATE(pp.produce_date) = #{statDate}
          AND pp.material_id IS NOT NULL
          AND p.belong_type = 'vegetable'
        GROUP BY pp.material_id
        """)
    List<Map<String, Object>> selectProductPackUsage(@Param("tenantId") String tenantId, @Param("statDate") String statDate);
}
