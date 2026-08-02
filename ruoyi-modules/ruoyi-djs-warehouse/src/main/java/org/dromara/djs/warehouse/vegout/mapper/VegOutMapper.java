package org.dromara.djs.warehouse.vegout.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutBatchVo;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutCandidateVo;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutDetailVo;

import java.util.Date;
import java.util.List;

/**
 * 毛菜间出库查询 mapper（admin row187）。
 *
 * <p>没有独立主表：出库单 = {@code t_warehouse_stock_flow} 里同一个 {@code batch_no} 的若干行聚合，
 * 明细即那些行本身。故本 mapper 只有查询，没有 BaseMapper CRUD。</p>
 *
 * <p>显式 {@code tenant_id='1001' AND del_flag='0'}（V1 无全局租户拦截器）。</p>
 *
 * @author djs
 */
public interface VegOutMapper {

    /**
     * 新增抽屉左侧可选产品：毛菜鲜品库（L0006）里的果蔬库存行，一行一个「产品 × 地块」篮。
     *
     * <p>只列 {@code product_stock > 0} 的行（库存为 0 没法出库）。按产品名模糊筛。</p>
     *
     * @param productName 产品名称（模糊，可空）
     * @return 可选产品行
     */
    @Select("""
        <script>
        SELECT s.id                AS stockId,
               s.product_id        AS productId,
               p.product_name      AS productName,
               p.product_spec      AS productSpec,
               s.product_stock     AS stockWeight,
               p.product_unit      AS productUnit,
               s.plot_id           AS plotId,
               pl.plot_code        AS plotCode
          FROM t_warehouse_location_stock s
          JOIN t_warehouse_location_info l ON l.id = s.location_id AND l.del_flag = '0'
          JOIN t_warehouse_product_info p  ON p.id = s.product_id  AND p.del_flag = '0'
          LEFT JOIN t_plant_plot_info pl   ON pl.id = s.plot_id    AND pl.del_flag = '0'
         WHERE s.del_flag = '0'
           AND s.tenant_id = '1001'
           AND l.location_code = #{freshVegCode}
           AND p.belong_type = 'vegetable'
           AND s.product_stock &gt; 0
        <if test="productName != null and productName != ''">
           AND p.product_name LIKE CONCAT('%', #{productName}, '%')
        </if>
         ORDER BY p.product_name, pl.plot_code
        </script>
        """)
    List<VegOutCandidateVo> selectCandidates(@Param("freshVegCode") String freshVegCode,
                                             @Param("productName") String productName);

    /**
     * 出库单列表：按 {@code batch_no} 聚合（品类数 = 去重产品数，重量 = 出库量合计）。
     *
     * @return 分页出库单
     */
    @Select("""
        <script>
        SELECT f.batch_no                    AS batchNo,
               MIN(f.flow_date)              AS outDate,
               MIN(f.stock_out_dest)         AS outDest,
               COUNT(DISTINCT f.product_id)  AS productKinds,
               SUM(f.change_quantity)        AS totalWeight,
               MIN(f.operator_id)            AS operatorId
          FROM t_warehouse_stock_flow f
         WHERE f.del_flag = '0'
           AND f.tenant_id = '1001'
           AND f.batch_no IS NOT NULL
        <if test="beginDate != null">
           AND f.flow_date &gt;= #{beginDate}
        </if>
        <if test="endDate != null">
           AND f.flow_date &lt;= #{endDate}
        </if>
        <if test="outDest != null and outDest != ''">
           AND f.stock_out_dest = #{outDest}
        </if>
        <if test="operatorId != null">
           AND f.operator_id = #{operatorId}
        </if>
         GROUP BY f.batch_no
         ORDER BY MIN(f.flow_date) DESC, f.batch_no DESC
        </script>
        """)
    IPage<VegOutBatchVo> selectBatchPage(IPage<VegOutBatchVo> page,
                                         @Param("beginDate") Date beginDate,
                                         @Param("endDate") Date endDate,
                                         @Param("outDest") String outDest,
                                         @Param("operatorId") Long operatorId);

    /**
     * 出库单明细：该 {@code batch_no} 下的产品行，可按产品名模糊筛。
     */
    @Select("""
        <script>
        SELECT p.product_name   AS productName,
               p.product_spec   AS productSpec,
               f.change_quantity AS outWeight,
               pl.plot_code     AS plotCode
          FROM t_warehouse_stock_flow f
          JOIN t_warehouse_product_info p ON p.id = f.product_id AND p.del_flag = '0'
          LEFT JOIN t_plant_plot_info pl  ON pl.id = f.plot_id   AND pl.del_flag = '0'
         WHERE f.del_flag = '0'
           AND f.tenant_id = '1001'
           AND f.batch_no = #{batchNo}
        <if test="productName != null and productName != ''">
           AND p.product_name LIKE CONCAT('%', #{productName}, '%')
        </if>
         ORDER BY p.product_name
        </script>
        """)
    List<VegOutDetailVo> selectBatchDetail(@Param("batchNo") String batchNo,
                                           @Param("productName") String productName);
}
