package org.dromara.djs.warehouse.inout.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.inout.domain.vo.RecentOutDestVo;
import org.dromara.djs.warehouse.inout.domain.vo.VegOutStockRow;

import java.util.List;

/** 果蔬入库 / 出库工作台只读查询（V6 row282/row283）；写入一律走毛菜处理与毛菜间出库的既有 service。 */
public interface VegInoutWorkbenchMapper {

    /**
     * 库存里的果蔬原材料篮（全部库位，row283「此时不区分库位」），一行一个篮。
     *
     * <p>业态 {@code vegetable} + 原材料 {@code product_attr=2}（打包成品走发货 / 门店链路，与毛菜间出库同一范围约束）。
     * 排序即先进先出序：同一张地块卡背后的多个篮按建篮时间、同刻按 id 扣减。</p>
     *
     * @param productId 产品 id（可空 = 全部产品）
     */
    @Select("""
        <script>
        SELECT s.id AS stock_id, s.product_id, p.product_name, p.product_unit,
               s.plot_id, pl.plot_code, pl.plot_name, s.ear_no,
               s.location_id, l.location_name, s.third_phase, s.product_stock AS stock_weight
          FROM t_warehouse_location_stock s
          JOIN t_warehouse_location_info l ON l.id = s.location_id AND l.del_flag = '0'
          JOIN t_warehouse_product_info p ON p.id = s.product_id AND p.del_flag = '0'
          LEFT JOIN t_plant_plot_info pl ON pl.id = s.plot_id AND pl.del_flag = '0'
         WHERE s.del_flag = '0' AND s.tenant_id = '1001'
           AND p.belong_type = 'vegetable' AND p.product_attr = 2
           AND s.product_stock &gt; 0
        <if test="productId != null">
           AND s.product_id = #{productId}
        </if>
         ORDER BY p.product_name, s.product_id, s.create_time, s.id
        </script>
        """)
    List<VegOutStockRow> selectVegStocks(@Param("productId") Long productId);

    /** 近 30 天果蔬产品后台出库去向按次数排序，由 service 过滤有效字典后取前 10 个。 */
    @Select("SELECT f.stock_out_dest AS value, COUNT(*) AS count FROM t_warehouse_stock_flow f "
        + "JOIN t_warehouse_product_info p ON p.id = f.product_id AND p.del_flag = '0' AND p.belong_type = 'vegetable' "
        + "WHERE f.del_flag = '0' AND f.tenant_id = '1001' AND f.flow_type = 'backstage_out' AND f.inout_type = 'OT' "
        + "AND f.flow_date >= DATE_SUB(NOW(), INTERVAL 30 DAY) AND f.flow_date <= NOW() "
        + "AND f.stock_out_dest IS NOT NULL AND f.stock_out_dest <> '' GROUP BY f.stock_out_dest "
        + "ORDER BY COUNT(*) DESC, MAX(f.flow_date) DESC, f.stock_out_dest ASC")
    List<RecentOutDestVo> selectRecentVegOutDests();
}
