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
     * 故聚合待入库扣掉损耗自然归 0——此处扣减用<b>全历史</b> loss 保证已完成地块量归零，不受当天口径影响）。</p>
     *
     * <p>展示列 {@code actualWeight}（row203）= 该作物<b>详情页当前展示地块</b>的已入库量合计，
     * 与详情页 {@link #selectInboundPlots} 用同一套地块可见性谓词（{@code platform>0} 且
     * {@code 待入库>0 或 当天有完成}）后按作物汇总，保证「列表卡 = 详情头卡 = 详情地块卡合计」三者恒等
     * （row204 口径：列表与详情必须一致）。往日已完成、详情页不再展示的地块不计入。</p>
     *
     * <p>{@code lossWeight}（row179）= 该作物<b>当天</b>（{@code DATE(receive_time)=CURDATE()}）入库完成
     * 结算的 loss 合计，与详情 {@link #selectInboundPlots} 的地块 loss 口径一致。刚送到月台、当天未标记完成的
     * 作物 loss 恒 0（客户「刚送到不应有损耗」）；不再累计历史（昨日 / 更早）已完成地块的损耗，避免列表卡显历史
     * 累计损耗、而详情当天口径显 0 的不一致。仅保留待入库 &gt; 0 的作物（全完成作物 pending 归 0 后从列表消失）。</p>
     */
    @Select("""
        SELECT t.crop_id      AS cropId,
               cr.crop_name   AS cropName,
               cr.image_oss_id AS imageOssId,
               COALESCE(rp.product_id, cr.crop_code) AS productCode,
               t.pending      AS pendingWeight,
               COALESCE(av.actual_visible, 0) AS actualWeight,
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
                            AND DATE(vrl2.receive_time) = CURDATE()
                       ), 0) AS loss
              FROM t_warehouse_vegetable_handle vh
             WHERE vh.del_flag = '0'
               AND vh.tenant_id = '1001'
               AND vh.send_platform_weight > 0
             GROUP BY vh.crop_id
          ) t
          LEFT JOIN (
            SELECT pv.crop_id, SUM(pv.actual) AS actual_visible
              FROM (
                SELECT vh2.crop_id,
                       vh2.plot_id,
                       COALESCE(SUM(vh2.send_platform_weight), 0) AS platform,
                       COALESCE((
                             SELECT SUM(vra.weight)
                               FROM t_warehouse_veg_receive vra
                              WHERE vra.receive_type = 1
                                AND vra.crop_id = vh2.crop_id
                                AND vra.plot_id = vh2.plot_id
                                AND vra.del_flag = '0'
                                AND vra.tenant_id = '1001'
                           ), 0) AS actual,
                       COALESCE((
                             SELECT SUM(vla.loss_weight)
                               FROM t_warehouse_veg_receive vla
                              WHERE vla.receive_type = 1
                                AND vla.crop_id = vh2.crop_id
                                AND vla.plot_id = vh2.plot_id
                                AND vla.is_finish = 1
                                AND vla.del_flag = '0'
                                AND vla.tenant_id = '1001'
                           ), 0) AS loss_all,
                       (
                             SELECT COUNT(1)
                               FROM t_warehouse_veg_receive vfa
                              WHERE vfa.receive_type = 1
                                AND vfa.crop_id = vh2.crop_id
                                AND vfa.plot_id = vh2.plot_id
                                AND vfa.is_finish = 1
                                AND vfa.del_flag = '0'
                                AND vfa.tenant_id = '1001'
                                AND DATE(vfa.receive_time) = CURDATE()
                       ) AS finished_today
                  FROM t_warehouse_vegetable_handle vh2
                 WHERE vh2.del_flag = '0'
                   AND vh2.tenant_id = '1001'
                   AND vh2.send_platform_weight > 0
                 GROUP BY vh2.crop_id, vh2.plot_id
              ) pv
             WHERE pv.platform > 0
               AND (pv.platform - pv.actual - pv.loss_all > 0 OR pv.finished_today > 0)
             GROUP BY pv.crop_id
          ) av ON av.crop_id = t.crop_id
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
     * 蔬菜月台「外购产品收货」待收货列表（Kevin 2026-06-26）：返**自产食材原料 SKU**
     * （{@code product_type=1 自产 + product_attr=2 原材料}）。
     *
     * <p>口径：现场外购收货认「自产食材原料」（果蔬/猪肉/蛋/白条/干货里的原料 {@code product_attr=2}）——
     * 农场自产不够时外购同款食材、回到蔬菜月台收货入库，复用同一个自产 SKU。纯外购商品
     * （{@code product_type=2}，如饲料/药品/肥料/农药/包材，按 {@code buy_class} 分类）是生产资料、不是要在月台
     * 收的食材，走 admin 采购入库、不在此现场收货。故必须限 {@code product_type=1}：否则 297 条外购生产资料
     * （belong_type 全空、CASE 落 ELSE 显「外购原材料」）会污染列表。不再用 {@code is_buy_out=1} 收口：
     * 现网自产食材 SKU 普遍 {@code is_buy_out=0}（果蔬全部为 0），加该条件会把列表清空、和原型「外购产品收货
     * 列出果蔬产品」（小白菜/番茄）矛盾——任何自产食材都可临时外购补货，外购属性不是产品固有标记。</p>
     *
     * <p>外购无"上游月台量"概念（不来自毛菜处理），V1 收货前无预设待收量 → {@code pendingWeight=0}；
     * 工人在 mp 外购入库子页直接录入实收重量。{@code productType} 列按 {@code belong_type} 回填业态文案
     * （果蔬/猪肉/蛋等），便于 dock 卡片次标签展示。{@code cropId} 承载产品 {@code id}（snowflake，非业务码）。</p>
     *
     * <p>筛选：{@code productName} 非空时按产品名模糊匹配（mp dock 外购搜索框）。
     * 产品条件 = 产品（{@code product_type=1}）+ 原材料（{@code product_attr=2}）+ 支持外购
     * （{@code is_buy_out=1}，是否支持外购=是）+ 启用（{@code product_status=0}）。
     * 租户单租户显式 {@code tenant_id='1001'}。</p>
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
           AND p.product_type  = 1
           AND p.product_attr  = 2
           AND p.is_buy_out    = 1
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
     * 待入库 = 月台量 − 实际入库 − <b>全历史</b>已结算损耗（取 0 兜底负值）。</p>
     *
     * <p>展示列 {@code lossWeight}（row179）= 该地块<b>当天</b>（{@code DATE(receive_time)=CURDATE()}）入库完成
     * 结算的 loss 合计（{@code lossToday}），与列表卡 {@link #selectSelfPending} 的 loss 口径一致——刚送到、当天
     * 未标记完成的地块 loss 恒 0。<b>pending / 状态判定仍用全历史 loss（{@code lossAll}）</b>：昨日已完成地块的
     * 待入库量必须保持归 0（否则历史结算损耗不扣、量重新变正、已完成地块误重现），故 pending 扣减与展示 loss 拆两路。
     * 头卡损耗量 = Σ各地块 lossWeight（前端聚合），随之变为当天口径与列表卡对齐。</p>
     *
     * <p>状态按<b>真实待入库量</b>（月台量 − 已入 − 已结算全历史损耗）判：
     * &gt; 0 → 可处理（actual=0→pending / 其余→processing）；≤ 0 → done。<b>不再以「是否有 is_finish 行」锁死</b>——
     * row66：同地块当天可多趟送达、待入库量叠加，某趟标记完成只把当趟剩余结算为损耗，新送达的量让地块回到可处理，
     * 不会出现「第一趟处理完成后、同地块第二趟送来无法处理」。仅返月台量 &gt; 0 的地块。</p>
     *
     * <p><b>当天过滤（r73 ②）</b>：待入库量 &gt; 0 的地块恒显（可处理，跨日新送达也显）；待入库量 ≤ 0 时——
     * 当天有完成（{@code finishedToday > 0}）仍显 done、次日不再显示（{@code finishedToday=0} 且无待入 → 隐）。</p>
     */
    @Select("""
        SELECT t.plot_id     AS plotId,
               pl.plot_code  AS plotCode,
               CASE
                 WHEN t.platform - t.actual - t.loss_all <= 0 THEN 'done'
                 WHEN t.actual <= 0 THEN 'pending'
                 ELSE 'processing'
               END           AS inboundStatus,
               CASE
                 WHEN t.platform - t.actual - t.loss_all > 0 THEN t.platform - t.actual - t.loss_all
                 ELSE 0
               END           AS pendingWeight,
               t.actual      AS actualWeight,
               t.loss_today  AS lossWeight
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
                   COALESCE((
                       SELECT SUM(vrl.loss_weight)
                         FROM t_warehouse_veg_receive vrl
                        WHERE vrl.receive_type = 1
                          AND vrl.crop_id = #{cropId}
                          AND vrl.plot_id = vh.plot_id
                          AND vrl.is_finish = 1
                          AND vrl.del_flag = '0'
                          AND vrl.tenant_id = '1001'
                     ), 0) AS loss_all,
                   COALESCE((
                       SELECT SUM(vrt.loss_weight)
                         FROM t_warehouse_veg_receive vrt
                        WHERE vrt.receive_type = 1
                          AND vrt.crop_id = #{cropId}
                          AND vrt.plot_id = vh.plot_id
                          AND vrt.is_finish = 1
                          AND vrt.del_flag = '0'
                          AND vrt.tenant_id = '1001'
                          AND DATE(vrt.receive_time) = CURDATE()
                     ), 0) AS loss_today,
                   (
                       SELECT COUNT(1)
                         FROM t_warehouse_veg_receive vrf
                        WHERE vrf.receive_type = 1
                          AND vrf.crop_id = #{cropId}
                          AND vrf.plot_id = vh.plot_id
                          AND vrf.is_finish = 1
                          AND vrf.del_flag = '0'
                          AND vrf.tenant_id = '1001'
                          AND DATE(vrf.receive_time) = CURDATE()
                   ) AS finishedToday
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
           AND (t.platform - t.actual - t.loss_all > 0 OR t.finishedToday > 0)
         ORDER BY pl.plot_code, t.plot_id
        """)
    List<VegInboundPlotVo> selectInboundPlots(@Param("cropId") Long cropId);

    /**
     * 某 (crop, plot) 真实剩余可入库量 = 自产月台量 − 已入库量 − 已结算损耗量（service 入库前校验防超量）。
     *
     * <p>row66：同地块当天可多趟送达、待入库量叠加。某趟标记「入库完成」时会把当趟剩余结算为损耗
     * （{@code is_finish=1} 行的 {@code loss_weight}），这部分已不可再入；故剩余可入 = 月台量 − 已入 − 已结算损耗。
     * 新送达的量（{@code send_platform_weight} 增量）不被前趟损耗吃掉，可继续入库。返回 ≥ 0；无月台量返 0。</p>
     */
    @Select("""
        SELECT COALESCE(p.platform, 0) - COALESCE(r.actual, 0) - COALESCE(l.loss, 0)
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
          ) r,
          (
            SELECT COALESCE(SUM(vrl.loss_weight), 0) AS loss
              FROM t_warehouse_veg_receive vrl
             WHERE vrl.receive_type = 1
               AND vrl.crop_id = #{cropId}
               AND vrl.plot_id = #{plotId}
               AND vrl.is_finish = 1
               AND vrl.del_flag = '0'
               AND vrl.tenant_id = '1001'
          ) l
        """)
    BigDecimal selectRemainInboundWeight(@Param("cropId") Long cropId, @Param("plotId") Long plotId);

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
