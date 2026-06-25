package org.dromara.djs.warehouse.vegreceive.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.vegreceive.domain.VegReceive;
import org.dromara.djs.warehouse.vegreceive.domain.vo.VegInboundPlotVo;
import org.dromara.djs.warehouse.vegreceive.domain.vo.VegReceiveItemVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 果蔬月台收货 Mapper（FIX-WMS-VEGRECEIVE-001）。
 *
 * <p>聚合口径：上游毛菜处理"发往月台"量 {@code vegetable_handle.send_platform_weight} 是"已发往月台、
 * 待入果蔬间"的果蔬；本表 {@code receiveType=1} 记录"已从月台入到保鲜室"的量。两者相减得待入库量。</p>
 *
 * <p>租户隔离：业务表未启全局 MP 拦截器，聚合 SQL 显式 {@code tenant_id='1001'}（V1 单租户，与
 * {@link org.dromara.djs.warehouse.stock.mapper.LocationStockMapper} 同范式）。</p>
 *
 * @author djs
 * @since FIX-WMS-VEGRECEIVE-001
 */
public interface VegReceiveMapper extends BaseMapperPlus<VegReceive, VegReceive> {

    /**
     * 自产果蔬待收货列表（按作物聚合）。
     *
     * <p>待入库 = {@code SUM(vegetable_handle.send_platform_weight)} − {@code SUM(已入库 self weight)}
     * − {@code SUM(已标记入库完成行的 loss_weight)}（row21：地块标记入库完成后剩余量结算为损耗、不再正数挂着，
     * 故聚合待入库扣掉损耗自然归 0）。损耗 {@code lossWeight} = 该作物已完成行的 loss 合计。
     * 仅保留待入库 &gt; 0 的作物（全完成作物 pending 归 0 后从列表消失）。</p>
     */
    @Select("""
        SELECT t.crop_id      AS cropId,
               cr.crop_name   AS cropName,
               cr.image_oss_id AS imageOssId,
               COALESCE(rp.product_id, cr.crop_code) AS productCode,
               t.pending      AS pendingWeight,
               t.loss         AS lossWeight
          FROM (
            SELECT vh.crop_id,
                   COALESCE(SUM(vh.send_platform_weight), 0)
                     - COALESCE((
                         SELECT SUM(vr.weight)
                           FROM t_warehouse_veg_receive vr
                          WHERE vr.receive_type = 1
                            AND vr.crop_id = vh.crop_id
                            AND vr.del_flag = '0'
                            AND vr.tenant_id = '1001'
                       ), 0)
                     - COALESCE((
                         SELECT SUM(vrl.loss_weight)
                           FROM t_warehouse_veg_receive vrl
                          WHERE vrl.receive_type = 1
                            AND vrl.crop_id = vh.crop_id
                            AND vrl.is_finish = 1
                            AND vrl.del_flag = '0'
                            AND vrl.tenant_id = '1001'
                       ), 0) AS pending,
                   COALESCE((
                         SELECT SUM(vrl2.loss_weight)
                           FROM t_warehouse_veg_receive vrl2
                          WHERE vrl2.receive_type = 1
                            AND vrl2.crop_id = vh.crop_id
                            AND vrl2.is_finish = 1
                            AND vrl2.del_flag = '0'
                            AND vrl2.tenant_id = '1001'
                       ), 0) AS loss
              FROM t_warehouse_vegetable_handle vh
             WHERE vh.del_flag = '0'
               AND vh.tenant_id = '1001'
               AND vh.send_platform_weight > 0
             GROUP BY vh.crop_id
          ) t
          LEFT JOIN t_plant_crop_info cr ON cr.id = t.crop_id
          LEFT JOIN t_warehouse_product_info rp
                 ON rp.id = cr.related_product
                AND rp.del_flag = '0'
                AND rp.tenant_id = '1001'
         WHERE t.pending > 0
         ORDER BY t.crop_id
        """)
    List<VegReceiveItemVo> selectSelfPending();

    /**
     * 外购原材料待收货列表（蔬菜月台「外购产品收货」分段，Kevin 2026-06-25）：返**所有可外购的原材料**
     * （{@code is_buy_out=1 可外购 + product_attr=2 原材料}，不限 belong_type）。
     *
     * <p>与采购入库口径互补、按 {@code product_attr} 二分：可采购的<b>商品</b>（attr≠2，外购商品 + 自产可外购商品）
     * → admin 采购入库；可外购的<b>原材料</b>（attr=2，如自产果蔬/猪肉/蛋/白条标了可外购的）→ 本蔬菜月台收货。
     * 故不再限 {@code belong_type='vegetable'}：番茄/苤蓝(果蔬) + 五花肉(猪肉) + 土鸡蛋(蛋) + 白条(半只) 等
     * 可外购原材料都在此收货。</p>
     *
     * <p>外购无"上游月台量"概念（不来自毛菜处理），V1 收货前无预设待收量 → {@code pendingWeight=0}；
     * 工人在 mp 外购入库子页直接录入实收重量。{@code productType} 列按 {@code belong_type} 回填业态文案
     * （果蔬/猪肉/蛋等），便于 dock 卡片次标签展示。{@code cropId} 承载产品 {@code id}（snowflake，非业务码）。</p>
     *
     * <p>筛选：{@code productName} 非空时按产品名模糊匹配（mp dock 外购搜索框）。
     * 仅返启用产品（{@code product_status=0}）。租户单租户显式 {@code tenant_id='1001'}。</p>
     *
     * @param productName 产品名称模糊关键字（可空）
     * @return 外购待收货列表项（pendingWeight 恒 0）
     */
    @Select("""
        <script>
        SELECT p.id            AS cropId,
               p.product_name  AS cropName,
               p.image_oss_id  AS imageOssId,
               p.product_id    AS productCode,
               CASE p.belong_type
                 WHEN 'vegetable' THEN '果蔬产品'
                 WHEN 'pork'      THEN '猪肉产品'
                 WHEN 'egg'       THEN '鸡蛋产品'
                 WHEN 'white_bar' THEN '白条产品'
                 WHEN 'dry_good'  THEN '干货产品'
                 ELSE '外购原材料'
               END             AS productType,
               p.product_unit  AS productUnit,
               0               AS pendingWeight
          FROM t_warehouse_product_info p
         WHERE p.del_flag      = '0'
           AND p.tenant_id     = '1001'
           AND p.is_buy_out    = 1
           AND p.product_attr  = 2
           AND p.product_status = 0
           <if test="productName != null and productName != ''">
             AND p.product_name LIKE CONCAT('%', #{productName}, '%')
           </if>
         ORDER BY p.product_name ASC, p.id ASC
        </script>
        """)
    List<VegReceiveItemVo> selectPurchasedPending(@Param("productName") String productName);

    /**
     * 某作物下、按地块的果蔬间入库行。
     *
     * <p>地块月台量 = {@code SUM(send_platform_weight) by plot}；实际入库 = 本表 self 该 (crop, plot) 已入量；
     * 待入库 = 月台量 − 实际入库（取 0 兜底负值）。状态：已标记入库完成（{@code is_finish=1} 存在）→ done（步10
     * Part1 锁定，优先于数量支，避免毛菜处理回灌月台量把 done 翻回 processing）/ actual=0→pending /
     * actual&lt;月台量→processing / actual≥月台量→done。仅返月台量 &gt; 0 的地块。</p>
     */
    @Select("""
        SELECT t.plot_id     AS plotId,
               pl.plot_code  AS plotCode,
               CASE
                 WHEN t.finished > 0 THEN 'done'
                 WHEN t.actual <= 0 THEN 'pending'
                 WHEN t.actual >= t.platform THEN 'done'
                 ELSE 'processing'
               END           AS inboundStatus,
               CASE
                 WHEN t.finished > 0 THEN 0
                 WHEN t.platform - t.actual > 0 THEN t.platform - t.actual
                 ELSE 0
               END           AS pendingWeight,
               t.actual      AS actualWeight
          FROM (
            SELECT vh.plot_id,
                   COALESCE(SUM(vh.send_platform_weight), 0) AS platform,
                   COALESCE((
                       SELECT SUM(vr.weight)
                         FROM t_warehouse_veg_receive vr
                        WHERE vr.receive_type = 1
                          AND vr.crop_id = #{cropId}
                          AND vr.plot_id = vh.plot_id
                          AND vr.del_flag = '0'
                          AND vr.tenant_id = '1001'
                     ), 0) AS actual,
                   (
                       SELECT COUNT(1)
                         FROM t_warehouse_veg_receive vrf
                        WHERE vrf.receive_type = 1
                          AND vrf.crop_id = #{cropId}
                          AND vrf.plot_id = vh.plot_id
                          AND vrf.is_finish = 1
                          AND vrf.del_flag = '0'
                          AND vrf.tenant_id = '1001'
                   ) AS finished
              FROM t_warehouse_vegetable_handle vh
             WHERE vh.del_flag = '0'
               AND vh.tenant_id = '1001'
               AND vh.crop_id = #{cropId}
               AND vh.send_platform_weight > 0
             GROUP BY vh.plot_id
          ) t
          LEFT JOIN t_plant_plot_info pl
                 ON pl.id = t.plot_id
                AND pl.del_flag = '0'
         WHERE t.platform > 0
         ORDER BY pl.plot_code, t.plot_id
        """)
    List<VegInboundPlotVo> selectInboundPlots(@Param("cropId") Long cropId);

    /**
     * 某 (crop, plot) 自产月台量 − 已入库量 = 剩余可入库量（service 入库前校验防超量）。
     *
     * <p>返回剩余可入库（≥ 0）；无任何月台量返 0。</p>
     */
    @Select("""
        SELECT COALESCE(p.platform, 0) - COALESCE(r.actual, 0)
          FROM (
            SELECT COALESCE(SUM(vh.send_platform_weight), 0) AS platform
              FROM t_warehouse_vegetable_handle vh
             WHERE vh.del_flag = '0'
               AND vh.tenant_id = '1001'
               AND vh.crop_id = #{cropId}
               AND vh.plot_id = #{plotId}
          ) p,
          (
            SELECT COALESCE(SUM(vr.weight), 0) AS actual
              FROM t_warehouse_veg_receive vr
             WHERE vr.receive_type = 1
               AND vr.crop_id = #{cropId}
               AND vr.plot_id = #{plotId}
               AND vr.del_flag = '0'
               AND vr.tenant_id = '1001'
          ) r
        """)
    BigDecimal selectRemainInboundWeight(@Param("cropId") Long cropId, @Param("plotId") Long plotId);

    /**
     * 统计某 (crop, plot) 自产入库是否已标记完成（{@code is_finish=1}）的收货行数。
     *
     * <p>步10 Part1 锁定守门：果蔬间入库一旦标记「入库完成」，该地块即锁定不可再次入库。
     * service 在 {@link org.dromara.djs.warehouse.vegreceive.service.impl.VegReceiveServiceImpl#inbound}
     * 开头调用，&gt; 0 即拒绝。</p>
     *
     * <p>仅统计自产（{@code receive_type=1}）+ 未软删（{@code del_flag='0'}）行；
     * {@code tenant_id} V1 单租户显式 {@code '1001'}（与本 mapper 其他聚合 SQL 同范式）。</p>
     *
     * @param cropId 作物 id
     * @param plotId 地块 id
     * @return 已标记入库完成的收货行数（0 = 未锁定，可入库）
     */
    @Select("""
        SELECT COUNT(1)
          FROM t_warehouse_veg_receive
         WHERE receive_type = 1
           AND crop_id   = #{cropId}
           AND plot_id   = #{plotId}
           AND is_finish = 1
           AND del_flag  = '0'
           AND tenant_id = '1001'
        """)
    long countFinishedByPlot(@Param("cropId") Long cropId, @Param("plotId") Long plotId);

    /**
     * 取作物名称（VegInbound 提交时冗余写入 receive 记录；自产无 product，从 vegetable_handle 冗余取）。
     */
    @Select("""
        SELECT crop_name
          FROM t_plant_crop_info
         WHERE id = #{cropId}
         LIMIT 1
        """)
    String selectCropName(@Param("cropId") Long cropId);

    /**
     * 按 {@code plot_id} + {@code location_id} 原子增加蔬菜库存（自产果蔬入库 UPSERT 的 UPDATE 分支）。
     *
     * <p>蔬菜采摘入库走 {@code plot_id} 维度（库存三维互斥之一，{@code product_id}/{@code ear_no}/{@code plot_id}）。
     * {@link org.dromara.djs.warehouse.stock.mapper.LocationStockMapper} 只有 byProduct / byEarNo 维度，本表
     * 自建 byPlot 维度的增量 SQL（操作同一张 {@code t_warehouse_location_stock} 表，不侵入 stock 子域 Java）。</p>
     *
     * <p>{@code tenant_id} 由 MP 多租户拦截器在 final SQL 阶段注入；不走 MetaObjectHandler.updateFill，
     * 手工 set {@code update_by} / {@code update_time}。</p>
     *
     * @return affectedRows（0 = 该 plot+location 无库存行，service 兜底 INSERT 新行）
     */
    @Update("""
        UPDATE t_warehouse_location_stock
           SET product_stock = product_stock + #{addQty},
               update_by = #{userId},
               update_time = NOW()
         WHERE location_id = #{locationId}
           AND plot_id     = #{plotId}
           AND del_flag    = '0'
        """)
    int addStockByPlotLocation(@Param("locationId") Long locationId,
                               @Param("plotId") Long plotId,
                               @Param("addQty") BigDecimal addQty,
                               @Param("userId") Long userId);

}
