package org.dromara.djs.warehouse.stock.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.stock.domain.vo.StockOverviewDailyVo;
import org.dromara.djs.warehouse.stock.domain.vo.StockOverviewDetailVo;

import java.util.Date;
import java.util.List;

/**
 * 库存总览聚合查询 Mapper（WMS-STOCK-OVERVIEW-001，仓库-admin 行56）。
 *
 * <p>compute-on-read 回放 {@code t_warehouse_stock_flow}，不建汇总表（Kevin 拍板）。
 * 自定义 @Select 含聚合，WHERE 显式带 {@code tenant_id}（多租户拦截器对聚合不保证注入）；
 * 雪花 id CAST AS CHAR 返前端防精度丢失。</p>
 *
 * <p><b>口径</b>：</p>
 * <ul>
 *   <li>列表：按自然日，当日库存非 0 的产品/商品 distinct 数（基于当日及之前流水累计余额 ≠ 0）。</li>
 *   <li>明细：按 (产品, 库位) 一行，回放流水算期初/入库/出库；期末 = 期初 + 入 − 出；
 *       损耗（loss_flow）/ 饲喂（feed_log）按产品归集为信息列（产品维度去重后只挂在该产品某一行，
 *       避免多库位重复计数）。</li>
 * </ul>
 *
 * @author djs
 * @since WMS-STOCK-OVERVIEW-001
 */
@Mapper
public interface StockOverviewMapper {

    /**
     * 按自然日汇总库存总览（行56 列表）：每日 distinct 库存非 0 产品/商品数。
     *
     * <p>对日期范围内每一天，统计「截至当天末（含）该产品累计余额 ≠ 0」的 distinct 产品数。
     * 余额回放自 {@code t_warehouse_stock_flow}（inout_type 带方向 × change_quantity 绝对值）。
     * row42「生产产品不入库」口径：{@code flow_type='ship_out'} 不参与余额回放（成品只有出库无入库，
     * 计入会得恒负余额）；{@code product_id=0} 历史孤儿流水一并排除。只返回 productCount &gt; 0 的日期，
     * 按日期倒序。</p>
     *
     * <p>实现：先取日期范围内「有流水的自然日」集合 d，对每个 d 关联其当天及之前的流水按 product_id
     * 求和 change_num，余额 ≠ 0 计入 distinct。</p>
     *
     * @param tenantId 租户（V1 固定 '1001'）
     * @param dateFrom 起始日期（含，可空）
     * @param dateTo   截止日期（含，可空）
     * @return 每日库存总览汇总
     */
    @Select("""
        <script>
        SELECT d.statDate AS statDate,
               (
                   SELECT COUNT(*) FROM (
                       SELECT s.product_id
                       FROM t_warehouse_stock_flow s
                       WHERE s.del_flag = '0' AND s.tenant_id = #{tenantId}
                         AND s.product_id IS NOT NULL AND s.product_id &lt;&gt; 0
                         AND s.flow_type &lt;&gt; 'ship_out'
                         AND s.flow_date &lt; DATE_ADD(d.statDate, INTERVAL 1 DAY)
                       GROUP BY s.product_id
                       HAVING SUM(CASE WHEN s.inout_type = 'IN' THEN s.change_quantity
                                       WHEN s.inout_type = 'OT' THEN -s.change_quantity
                                       ELSE 0 END) &lt;&gt; 0
                   ) bal_t
               ) AS productCount
        FROM (
            SELECT DISTINCT DATE_FORMAT(flow_date, '%Y-%m-%d') AS statDate
            FROM t_warehouse_stock_flow
            WHERE del_flag = '0' AND tenant_id = #{tenantId}
            <if test="dateFrom != null"> AND flow_date &gt;= #{dateFrom} </if>
            <if test="dateTo != null"> AND flow_date &lt; DATE_ADD(#{dateTo}, INTERVAL 1 DAY) </if>
        ) d
        ORDER BY d.statDate DESC
        </script>
        """)
    List<StockOverviewDailyVo> selectDailyOverview(@Param("tenantId") String tenantId,
                                                   @Param("dateFrom") Date dateFrom,
                                                   @Param("dateTo") Date dateTo);

    /**
     * 当日库存明细（行56 详情弹窗）：某自然日按 (产品, 库位) 的库存情况。
     *
     * <p>主体来自当日及之前有流水的 (product_id, warehouse_id) 组合：</p>
     * <ul>
     *   <li>beginStock = 昨日期末结存 = Σ(按 inout_type 带符号的 change_quantity) WHERE flow_date &lt; 当日 0 点；
     *       即 IN 计 +change_quantity、OT 计 −change_quantity。
     *       <b>不</b>用 change_num 带符号求和（部分写入方 ship_out 写成了正号，会导致期初 ≠ 昨日期末）。</li>
     *   <li>inboundQty = Σ change_quantity WHERE inout_type='IN' AND DATE(flow_date)=当日；</li>
     *   <li>outboundQty = Σ change_quantity WHERE inout_type='OT' AND DATE(flow_date)=当日；</li>
     *   <li>endStock = beginStock + inboundQty − outboundQty；故昨日 endStock 恒等于今日 beginStock。</li>
     *   <li>row42「生产产品不入库」口径：{@code flow_type='ship_out'} 成品发货流水<b>不计</b>期初/出库/期末
     *       （成品无 IN 流水，计入会得恒负期末），单列 shippedQty = Σ change_quantity WHERE
     *       flow_type='ship_out' AND DATE(flow_date)=当日（信息列「已发货」）；</li>
     *   <li>{@code product_id=0} 历史孤儿流水排除（0 不是 NULL，防无名幽灵行）；</li>
     *   <li>仅保留「期初或入库或出库或已发货任一 ≠ 0」即当日相关的行（剔除当日及历史均为 0 的噪声）。</li>
     * </ul>
     *
     * <p>损耗 / 饲喂为产品维度信息列：本 @Select 不在此 JOIN（避免与库位维度笛卡尔重复计数），
     * 由 service 用 {@link #selectDailyLossByProduct} / {@link #selectDailyFeedByProduct} 单独取，
     * 按产品归集后只挂到该产品的某一行。</p>
     *
     * <p>搜索：产品名称模糊（product_info.product_name）/ 库位精确（locationId）。
     * id CAST AS CHAR 返前端。</p>
     *
     * @param tenantId    租户（V1 固定 '1001'）
     * @param date        统计自然日（必传，yyyy-MM-dd）
     * @param productName 产品名称模糊（可空）
     * @param locationId  库位 id 精确（可空）
     * @return 当日库存明细（按 产品 × 库位）
     */
    @Select("""
        <script>
        SELECT CAST(g.product_id AS CHAR) AS productId,
               COALESCE(pi.product_thumb, pi.image_oss_id) AS imageOssId,
               pi.product_id AS productCode,
               pi.product_name AS productName,
               pi.product_unit AS productUnit,
               CAST(g.warehouse_id AS CHAR) AS locationId,
               li.location_name AS locationName,
               g.beginStock AS beginStock,
               g.inboundQty AS inboundQty,
               g.outboundQty AS outboundQty,
               g.shippedQty AS shippedQty,
               (g.beginStock + g.inboundQty - g.outboundQty) AS endStock
        FROM (
            SELECT f.product_id,
                   f.warehouse_id,
                   COALESCE(SUM(CASE WHEN f.flow_type = 'ship_out' THEN 0
                                     WHEN f.flow_date &lt; #{date} AND f.inout_type = 'IN' THEN f.change_quantity
                                     WHEN f.flow_date &lt; #{date} AND f.inout_type = 'OT' THEN -f.change_quantity
                                     ELSE 0 END), 0) AS beginStock,
                   COALESCE(SUM(CASE WHEN DATE(f.flow_date) = #{date} AND f.inout_type = 'IN' THEN f.change_quantity ELSE 0 END), 0) AS inboundQty,
                   COALESCE(SUM(CASE WHEN DATE(f.flow_date) = #{date} AND f.inout_type = 'OT' AND f.flow_type &lt;&gt; 'ship_out' THEN f.change_quantity ELSE 0 END), 0) AS outboundQty,
                   COALESCE(SUM(CASE WHEN DATE(f.flow_date) = #{date} AND f.flow_type = 'ship_out' THEN f.change_quantity ELSE 0 END), 0) AS shippedQty
            FROM t_warehouse_stock_flow f
            WHERE f.del_flag = '0' AND f.tenant_id = #{tenantId}
              AND f.product_id IS NOT NULL AND f.product_id &lt;&gt; 0
              AND f.flow_date &lt; DATE_ADD(#{date}, INTERVAL 1 DAY)
            GROUP BY f.product_id, f.warehouse_id
        ) g
        LEFT JOIN t_warehouse_product_info pi ON pi.id = g.product_id AND pi.del_flag = '0'
        LEFT JOIN t_warehouse_location_info li ON li.id = g.warehouse_id AND li.del_flag = '0'
        WHERE NOT (g.beginStock = 0 AND g.inboundQty = 0 AND g.outboundQty = 0 AND g.shippedQty = 0)
        <if test="productName != null and productName != ''">
            AND pi.product_name LIKE CONCAT('%', #{productName}, '%')
        </if>
        <if test="locationId != null">
            AND g.warehouse_id = #{locationId}
        </if>
        ORDER BY pi.product_name, g.warehouse_id
        </script>
        """)
    List<StockOverviewDetailVo> selectDailyDetail(@Param("tenantId") String tenantId,
                                                  @Param("date") String date,
                                                  @Param("productName") String productName,
                                                  @Param("locationId") Long locationId);

    /**
     * 当日各产品损耗量（产品维度信息列）：Σ loss_weight GROUP BY product_id WHERE DATE(loss_date)=当日。
     *
     * <p>返回 productId(String) + lossQty，service 按产品归集挂到明细行。</p>
     *
     * @param tenantId 租户（V1 固定 '1001'）
     * @param date     统计自然日（yyyy-MM-dd）
     * @return 当日各产品损耗量
     */
    @Select("""
        SELECT CAST(product_id AS CHAR) AS productId,
               COALESCE(SUM(loss_weight), 0) AS lossQty
        FROM t_warehouse_loss_flow
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND product_id IS NOT NULL AND product_id != 0
          AND DATE(loss_date) = #{date}
        GROUP BY product_id
        """)
    List<StockOverviewDetailVo> selectDailyLossByProduct(@Param("tenantId") String tenantId,
                                                         @Param("date") String date);

    /**
     * 当日各产品饲喂量（产品维度信息列）：Σ feed_weight GROUP BY product_id WHERE DATE(feed_date)=当日。
     *
     * <p>仅果蔬原材料 feed_log 有 product_id；返回 productId(String) + feedQty，service 按产品归集。</p>
     *
     * @param tenantId 租户（V1 固定 '1001'）
     * @param date     统计自然日（yyyy-MM-dd）
     * @return 当日各产品饲喂量
     */
    @Select("""
        SELECT CAST(product_id AS CHAR) AS productId,
               COALESCE(SUM(feed_weight), 0) AS feedQty
        FROM t_warehouse_feed_log
        WHERE del_flag = '0' AND tenant_id = #{tenantId}
          AND product_id IS NOT NULL AND product_id != 0
          AND DATE(feed_date) = #{date}
        GROUP BY product_id
        """)
    List<StockOverviewDetailVo> selectDailyFeedByProduct(@Param("tenantId") String tenantId,
                                                         @Param("date") String date);

}
