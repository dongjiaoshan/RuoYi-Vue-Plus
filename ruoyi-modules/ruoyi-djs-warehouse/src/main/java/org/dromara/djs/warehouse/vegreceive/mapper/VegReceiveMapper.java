package org.dromara.djs.warehouse.vegreceive.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
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
 * <p>聚合口径（row55 起）：上游"发往月台"的量取自 {@code t_warehouse_handle_record} 的月台明细
 * （{@code record_type=2} 处理 + {@code handle_target=2} 月台，<b>带产品维度</b>）；本表 {@code receiveType=1}
 * 记录"已从月台入到保鲜室"的量。待入库 = 月台明细量 − 已收量 − 已结算损耗，按 (作物, 产品) 分组。
 * 不再用 {@code vegetable_handle.send_platform_weight} —— 它是 (地块, 作物) 一行的汇总列、拆不开产品，
 * 详见 {@link #selectSelfPending}。</p>
 *
 * <p>租户隔离：业务表未启全局 MP 拦截器，聚合 SQL 显式 {@code tenant_id='1001'}（V1 单租户，与
 * {@link org.dromara.djs.warehouse.stock.mapper.LocationStockMapper} 同范式）。</p>
 *
 * @author djs
 * @since FIX-WMS-VEGRECEIVE-001
 */
public interface VegReceiveMapper extends BaseMapperPlus<VegReceive, VegReceive> {

    /**
     * 自产果蔬待收货列表（<b>按产品聚合</b>，row55）。
     *
     * <h3>row55：为什么从「按作物」改成「按产品」</h3>
     * <p>送到月台的已经是产品不是作物——同一个作物可以关联多个产品（{@code t_plant_crop_product}，
     * 如红薯 → 红薯 / 红薯杆），两个产品各自送月台。旧口径按 {@code crop_id} 聚合，把它们并成一张卡，
     * 结果：红薯杆的量被算进红薯卡的待入库里，工人在月台**看不到也收不了**红薯杆，那部分货卡死。</p>
     *
     * <h3>产品维度的量从哪来</h3>
     * <p>{@code vegetable_handle.send_platform_weight} 是 (地块, 作物) 一行的汇总列，**没有产品维度**，
     * 拆不开。带产品的明细在 {@code t_warehouse_handle_record}（{@code record_type=2 处理} +
     * {@code handle_target=2 月台}），且实测两者恒等（handle 的汇总列 = 其明细行之和），故改以明细为源。
     * 历史 {@code product_id} 为空的明细 COALESCE 回落 {@code crop.related_product}（多产品之前的单映射），
     * 保证老数据不凭空消失。</p>
     *
     * <p>待入库 = 该 (作物,产品) 月台量 − 已入库 self 量 − 已标记入库完成行的 loss（row21：地块标记入库完成后
     * 剩余量结算为损耗、不再正数挂着，故扣掉损耗自然归 0——扣减用<b>全历史</b> loss，不受当天口径影响）。
     * 收货侧同样按产品分组，{@code veg_receive.product_id} 为空的历史行也回落 {@code crop.related_product}。</p>
     *
     * <p>展示列 {@code actualWeight}（row203）= 该 (作物,产品)<b>详情页当前展示地块</b>的已入库量合计，
     * 与详情页 {@link #selectInboundPlots} 用同一套地块可见性谓词（{@code platform>0} 且
     * {@code 待入库>0 或 当天有完成}）后汇总，保证「列表卡 = 详情头卡 = 详情地块卡合计」三者恒等
     * （row204 口径：列表与详情必须一致）。往日已完成、详情页不再展示的地块不计入。</p>
     *
     * <p>{@code lossWeight}（row179）= 该 (作物,产品)<b>当天</b>（{@code DATE(receive_time)=CURDATE()}）
     * 入库完成结算的 loss 合计，与详情 {@link #selectInboundPlots} 的地块 loss 口径一致。刚送到月台、当天未标记
     * 完成的 loss 恒 0（客户「刚送到不应有损耗」）。仅保留待入库 &gt; 0 的行（全部收完后从列表消失）。</p>
     */
    // CTE 引用（FROM plat p）会被 MP 租户拦截器误当实表、注出 `p.tenant_id` 报 Unknown column；
    // 本查询每层 WHERE 都已显式 tenant_id='1001'，故关掉行级注入（同 PigBreedingMapper 范式）。
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        WITH plat AS (
            SELECT hr.crop_id,
                   hr.plot_id,
                   COALESCE(hr.product_id, cr0.related_product) AS product_id,
                   SUM(hr.record_weight) AS platform
              FROM t_warehouse_handle_record hr
              LEFT JOIN t_plant_crop_info cr0 ON cr0.id = hr.crop_id
             WHERE hr.del_flag = '0'
               AND hr.tenant_id = '1001'
               AND hr.record_type = 2
               AND hr.handle_target = 2
             GROUP BY hr.crop_id, hr.plot_id, COALESCE(hr.product_id, cr0.related_product)
        ),
        recv AS (
            SELECT vr.crop_id,
                   vr.plot_id,
                   COALESCE(vr.product_id, cr1.related_product) AS product_id,
                   SUM(vr.weight) AS actual,
                   SUM(CASE WHEN vr.is_finish = 1 THEN vr.loss_weight ELSE 0 END) AS loss_all,
                   SUM(CASE WHEN vr.is_finish = 1 AND DATE(vr.receive_time) = CURDATE()
                            THEN vr.loss_weight ELSE 0 END) AS loss_today,
                   SUM(CASE WHEN vr.is_finish = 1 AND DATE(vr.receive_time) = CURDATE()
                            THEN 1 ELSE 0 END) AS finished_today
              FROM t_warehouse_veg_receive vr
              LEFT JOIN t_plant_crop_info cr1 ON cr1.id = vr.crop_id
             WHERE vr.del_flag = '0'
               AND vr.tenant_id = '1001'
               AND vr.receive_type = 1
             GROUP BY vr.crop_id, vr.plot_id, COALESCE(vr.product_id, cr1.related_product)
        ),
        plat_sum AS (
            SELECT crop_id, product_id, SUM(platform) AS platform
              FROM plat GROUP BY crop_id, product_id
        ),
        recv_sum AS (
            SELECT crop_id, product_id,
                   SUM(actual) AS actual,
                   SUM(loss_all) AS loss_all,
                   SUM(loss_today) AS loss_today
              FROM recv GROUP BY crop_id, product_id
        ),
        visible AS (
            SELECT p.crop_id, p.product_id, SUM(COALESCE(r.actual, 0)) AS actual_visible
              FROM plat p
              LEFT JOIN recv r
                     ON r.crop_id = p.crop_id
                    AND r.plot_id = p.plot_id
                    AND r.product_id <=> p.product_id
             WHERE p.platform > 0
               AND (p.platform - COALESCE(r.actual, 0) - COALESCE(r.loss_all, 0) > 0
                    OR COALESCE(r.finished_today, 0) > 0)
             GROUP BY p.crop_id, p.product_id
        )
        SELECT t.crop_id       AS cropId,
               cr.crop_name    AS cropName,
               t.product_id    AS productId,
               COALESCE(pr.product_name, cr.crop_name) AS productName,
               COALESCE(pr.image_oss_id, cr.image_oss_id) AS imageOssId,
               COALESCE(pr.product_id, cr.crop_code) AS productCode,
               t.platform - COALESCE(rs.actual, 0) - COALESCE(rs.loss_all, 0) AS pendingWeight,
               COALESCE(v.actual_visible, 0) AS actualWeight,
               COALESCE(rs.loss_today, 0)    AS lossWeight
          FROM plat_sum t
          LEFT JOIN recv_sum rs
                 ON rs.crop_id = t.crop_id AND rs.product_id <=> t.product_id
          LEFT JOIN visible v
                 ON v.crop_id = t.crop_id AND v.product_id <=> t.product_id
          LEFT JOIN t_plant_crop_info cr ON cr.id = t.crop_id
          LEFT JOIN t_warehouse_product_info pr
                 ON pr.id = t.product_id
                AND pr.del_flag = '0'
                AND pr.tenant_id = '1001'
         WHERE t.platform - COALESCE(rs.actual, 0) - COALESCE(rs.loss_all, 0) > 0
         ORDER BY t.crop_id, t.product_id
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
     * 早期现网自产食材 SKU 普遍 {@code is_buy_out=0}，当时加该条件会把列表清空。
     * <b>现状（2026-08-07 实测）</b>：果蔬里 {@code is_buy_out=1} 已有 1 个、{@code =0} 还有 20 个，
     * 即该开关已在被实际使用 —— 所以下面的 SQL <b>保留</b>了 {@code is_buy_out=1}，由 admin 产品配置显式决定
     * 哪些食材能走外购收货。（本段上文那句"不再用 is_buy_out 收口"是早期结论，已不成立，故此处更正。）</p>
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
     * <p>地块月台量 = {@code SUM(handle_record.record_weight) by plot}（row55 起改读带产品维度的月台明细，
     * 不再读 {@code send_platform_weight}）；实际入库 = 本表 self 该 (crop, product, plot) 已入量；
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
    // 同上：CTE 引用触发租户拦截器误注入，本查询各层 WHERE 已显式带 tenant_id。
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        <script>
        WITH plat AS (
            SELECT hr.plot_id,
                   SUM(hr.record_weight) AS platform
              FROM t_warehouse_handle_record hr
              LEFT JOIN t_plant_crop_info cr0 ON cr0.id = hr.crop_id
             WHERE hr.del_flag = '0'
               AND hr.tenant_id = '1001'
               AND hr.record_type = 2
               AND hr.handle_target = 2
               AND hr.crop_id = #{cropId}
               <if test="productId != null">
                 AND COALESCE(hr.product_id, cr0.related_product) = #{productId}
               </if>
             GROUP BY hr.plot_id
        ),
        recv AS (
            SELECT vr.plot_id,
                   SUM(vr.weight) AS actual,
                   SUM(CASE WHEN vr.is_finish = 1 THEN vr.loss_weight ELSE 0 END) AS loss_all,
                   SUM(CASE WHEN vr.is_finish = 1 AND DATE(vr.receive_time) = CURDATE()
                            THEN vr.loss_weight ELSE 0 END) AS loss_today,
                   SUM(CASE WHEN vr.is_finish = 1 AND DATE(vr.receive_time) = CURDATE()
                            THEN 1 ELSE 0 END) AS finished_today
              FROM t_warehouse_veg_receive vr
              LEFT JOIN t_plant_crop_info cr1 ON cr1.id = vr.crop_id
             WHERE vr.del_flag = '0'
               AND vr.tenant_id = '1001'
               AND vr.receive_type = 1
               AND vr.crop_id = #{cropId}
               <if test="productId != null">
                 AND COALESCE(vr.product_id, cr1.related_product) = #{productId}
               </if>
             GROUP BY vr.plot_id
        )
        SELECT t.plot_id     AS plotId,
               pl.plot_code  AS plotCode,
               CASE
                 WHEN t.platform - t.actual - t.loss_all &lt;= 0 THEN 'done'
                 WHEN t.actual &lt;= 0 THEN 'pending'
                 ELSE 'processing'
               END           AS inboundStatus,
               CASE
                 WHEN t.platform - t.actual - t.loss_all &gt; 0 THEN t.platform - t.actual - t.loss_all
                 ELSE 0
               END           AS pendingWeight,
               t.actual      AS actualWeight,
               t.loss_today  AS lossWeight
          FROM (
            SELECT p.plot_id,
                   p.platform,
                   COALESCE(r.actual, 0)         AS actual,
                   COALESCE(r.loss_all, 0)       AS loss_all,
                   COALESCE(r.loss_today, 0)     AS loss_today,
                   COALESCE(r.finished_today, 0) AS finishedToday
              FROM plat p
              LEFT JOIN recv r ON r.plot_id = p.plot_id
          ) t
          LEFT JOIN t_plant_plot_info pl
                 ON pl.id = t.plot_id
                AND pl.del_flag = '0'
         WHERE t.platform &gt; 0
           AND (t.platform - t.actual - t.loss_all &gt; 0 OR t.finishedToday &gt; 0)
         ORDER BY pl.plot_code, t.plot_id
        </script>
        """)
    List<VegInboundPlotVo> selectInboundPlots(@Param("cropId") Long cropId,
                                              @Param("productId") Long productId);

    /**
     * 某 (crop, product, plot) 真实剩余可入库量 = 自产月台量 − 已入库量 − 已结算损耗量（service 入库前校验防超量）。
     *
     * <p>row66：同地块当天可多趟送达、待入库量叠加。某趟标记「入库完成」时会把当趟剩余结算为损耗
     * （{@code is_finish=1} 行的 {@code loss_weight}），这部分已不可再入；故剩余可入 = 月台量 − 已入 − 已结算损耗。
     * 新送达的量不被前趟损耗吃掉，可继续入库。返回 ≥ 0；无月台量返 0。</p>
     *
     * <p>row55：三段全部按产品收窄——月台量改从 {@code handle_record}（带产品维度）取，收货 / 损耗按
     * {@code veg_receive.product_id} 过滤。否则红薯的入库会去吃红薯杆的额度、封顶校验形同虚设。
     * 历史空 {@code product_id} 两侧同样回落 {@code crop.related_product}，与列表口径一致。</p>
     */
    // 同上：三段派生表别名 p/r/l 同样会被租户拦截器盯上，各段 WHERE 已显式带 tenant_id。
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        <script>
        SELECT COALESCE(p.platform, 0) - COALESCE(r.actual, 0) - COALESCE(l.loss, 0)
          FROM (
            SELECT COALESCE(SUM(hr.record_weight), 0) AS platform
              FROM t_warehouse_handle_record hr
              LEFT JOIN t_plant_crop_info cr0 ON cr0.id = hr.crop_id
             WHERE hr.del_flag = '0'
               AND hr.tenant_id = '1001'
               AND hr.record_type = 2
               AND hr.handle_target = 2
               AND hr.crop_id = #{cropId}
               AND hr.plot_id = #{plotId}
               <if test="productId != null">
                 AND COALESCE(hr.product_id, cr0.related_product) = #{productId}
               </if>
          ) p,
          (
            SELECT COALESCE(SUM(vr.weight), 0) AS actual
              FROM t_warehouse_veg_receive vr
              LEFT JOIN t_plant_crop_info cr1 ON cr1.id = vr.crop_id
             WHERE vr.receive_type = 1
               AND vr.crop_id = #{cropId}
               AND vr.plot_id = #{plotId}
               AND vr.del_flag = '0'
               AND vr.tenant_id = '1001'
               <if test="productId != null">
                 AND COALESCE(vr.product_id, cr1.related_product) = #{productId}
               </if>
          ) r,
          (
            SELECT COALESCE(SUM(vrl.loss_weight), 0) AS loss
              FROM t_warehouse_veg_receive vrl
              LEFT JOIN t_plant_crop_info cr2 ON cr2.id = vrl.crop_id
             WHERE vrl.receive_type = 1
               AND vrl.crop_id = #{cropId}
               AND vrl.plot_id = #{plotId}
               AND vrl.is_finish = 1
               AND vrl.del_flag = '0'
               AND vrl.tenant_id = '1001'
               <if test="productId != null">
                 AND COALESCE(vrl.product_id, cr2.related_product) = #{productId}
               </if>
          ) l
        </script>
        """)
    BigDecimal selectRemainInboundWeight(@Param("cropId") Long cropId,
                                         @Param("plotId") Long plotId,
                                         @Param("productId") Long productId);

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
     * <p><b>row55 必须带 product_id</b>：这条 UPDATE 原来只按 {@code location_id + plot_id} 匹配。
     * 月台改成按产品收货之后，同一块地可以先后收红薯和红薯杆进同一个库位——第二次收货会直接把
     * 重量加到<b>先到那个产品</b>的篮子上（{@code insertPlotStockRow} 只在 affectedRows=0 时才跑），
     * 红薯杆的货被记成红薯，下游 {@code consumeVegBaskets(WHERE product_id=…)} 就永远领不到红薯杆。
     * 改动前一块地只可能收一个产品，所以这个缺陷是被「不会发生」掩着的，是 row55 把它激活了。
     * 用 {@code <=>} 而非 {@code =}：作物没配关联产品时篮子的 product_id 是 NULL，得能匹配上。</p>
     *
     * @return affectedRows（0 = 该 plot+location+product 无库存行，service 兜底 INSERT 新行）
     */
    @Update("""
        UPDATE t_warehouse_location_stock
           SET product_stock = product_stock + #{addQty},
               update_by = #{userId},
               update_time = NOW()
         WHERE location_id = #{locationId}
           AND plot_id     = #{plotId}
           AND product_id <=> #{productId}
           AND del_flag    = '0'
        """)
    int addStockByPlotLocation(@Param("locationId") Long locationId,
                               @Param("plotId") Long plotId,
                               @Param("productId") Long productId,
                               @Param("addQty") BigDecimal addQty,
                               @Param("userId") Long userId);

}
