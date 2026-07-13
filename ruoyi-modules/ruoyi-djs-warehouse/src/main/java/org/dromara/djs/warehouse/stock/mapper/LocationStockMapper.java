package org.dromara.djs.warehouse.stock.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueBasketVo;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueItemVo;
import org.dromara.djs.warehouse.flow.domain.vo.MatIssueLocationVo;
import org.dromara.djs.warehouse.flow.domain.vo.PackingItemVo;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.domain.vo.LocationStockVo;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 库存明细 Mapper（WMS-MD-001 / WMS-PIG-001 扩展）。
 *
 * @author djs
 * @since WMS-MD-001
 */
public interface LocationStockMapper extends BaseMapperPlus<LocationStock, LocationStockVo> {

    /**
     * 统计指定库位下"仍有库存"（{@code product_stock > 0} 且未软删）的记录数。
     *
     * <p>由 LocationInfoServiceImpl#deleteWithValidByIds 删除前校验使用。
     * 不走 LambdaQueryWrapper：BigDecimal 比较 + del_flag 判断 + tenant 走拦截器，
     * 写原生 SQL 更清晰；tenant_id 由 MP 拦截器自动注入，此处无需显式 {@code WHERE tenant_id=?}。</p>
     */
    @org.apache.ibatis.annotations.Select(
        "SELECT COUNT(1) FROM t_warehouse_location_stock "
            + "WHERE location_id = #{locationId} "
            + "  AND product_stock > 0 "
            + "  AND del_flag = '0'")
    long countActiveStockByLocation(@Param("locationId") Long locationId);

    /**
     * 按 {@code ear_no} + {@code location_id} 原子扣减白条库存（WMS-PIG-001 燎毛工序）。
     *
     * <p>核心契约：</p>
     * <ul>
     *   <li>SQL 在 {@code WHERE} 加 {@code product_stock >= deductQty} —— MySQL 行锁 + 数量校验同步发生，
     *       并发提交（同 ear_no 两次燎毛）只有一次 affectedRows > 0</li>
     *   <li>{@code tenant_id} 由 MP 多租户拦截器在 final SQL 阶段注入，应用层无需显式 WHERE</li>
     *   <li>不显式触发 MP {@code updateFill}（{@code DjsMetaObjectHandler}），因此手工 set {@code update_by} / {@code update_time}</li>
     * </ul>
     *
     * @param locationId 库位 ID
     * @param earNo      猪只耳号
     * @param deductQty  扣减数量（必须 &gt; 0）
     * @param userId     操作人（写入 update_by 字段）
     * @return affectedRows（0 = 库存不足 / 耳号不匹配 / 已软删）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = product_stock - #{deductQty},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE location_id = #{locationId} "
        + "   AND ear_no = #{earNo} "
        + "   AND product_stock >= #{deductQty} "
        + "   AND del_flag = '0'")
    int deductByEarNo(@Param("locationId") Long locationId,
                      @Param("earNo") String earNo,
                      @Param("deductQty") BigDecimal deductQty,
                      @Param("userId") Long userId);

    /**
     * 按 {@code product_id} + {@code location_id} 原子扣减库存（WMS-MAT-001 物资领用 / 损耗）。
     *
     * <p>只命中<b>非篮子行</b>（{@code plot_id / ear_no / white_bar_no} 全 NULL）：同 (库位,产品) 的
     * 耳号/地块/白条篮子各自成行，product 维度扣减绝不串扣篮子（篮子扣减走 {@link #deductStockById} /
     * {@link #deductByEarNo} / {@link #deductByPlotLocation}）。同 (库位,产品) 多篮共存时若无行限定，
     * 一条 UPDATE 会命中全部余量充足的行、每行各扣一次（扣减放大 N 倍）。</p>
     *
     * <p>SQL 在 {@code WHERE} 加 {@code product_stock >= deductQty} —— MySQL 行锁 + 数量校验同步发生，
     * 并发提交（两个工人同时领同一 product+location）只有一次 affectedRows > 0。</p>
     *
     * @return affectedRows（0 = 库存不足 / product/location 无非篮子行 / 已软删）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = product_stock - #{deductQty},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE location_id = #{locationId} "
        + "   AND product_id  = #{productId} "
        + "   AND plot_id IS NULL "
        + "   AND ear_no IS NULL "
        + "   AND white_bar_no IS NULL "
        + "   AND product_stock >= #{deductQty} "
        + "   AND del_flag = '0'")
    int deductByProductLocation(@Param("locationId") Long locationId,
                                @Param("productId") Long productId,
                                @Param("deductQty") BigDecimal deductQty,
                                @Param("userId") Long userId);

    /**
     * 按主键原子扣减库存（猪肉原材料按篮子 FIFO 领用：一篮 = 一头猪某部位，doc/14 §3）。
     *
     * <p>{@code WHERE product_stock >= deductQty} 行锁 + 余量校验同步，并发只一次成功。</p>
     *
     * @return affectedRows（1=成功，0=余量不足 / 已被并发占用 / 已软删）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = product_stock - #{deductQty},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE id = #{id} "
        + "   AND product_stock >= #{deductQty} "
        + "   AND del_flag = '0'")
    int deductStockById(@Param("id") Long id,
                        @Param("deductQty") BigDecimal deductQty,
                        @Param("userId") Long userId);

    /**
     * 按主键原子回补库存（猪肉原材料按篮子退回：把领用走的篮子重量加回原篮，doc/14 §6）。
     *
     * @return affectedRows（1=成功，0=篮子不存在 / 已软删）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = product_stock + #{addQty},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE id = #{id} "
        + "   AND del_flag = '0'")
    int addStockById(@Param("id") Long id,
                     @Param("addQty") BigDecimal addQty,
                     @Param("userId") Long userId);

    /**
     * 按 {@code plot_id} + {@code location_id} 原子扣减自产果蔬库存（步11 偏差修复 · 决策 a：
     * 自产果蔬「按地块维度」领用）。
     *
     * <p>自产果蔬入库时库存按 {@code (plot_id, location)} 维度建账（无 product_id，见
     * {@code VegReceiveServiceImpl.insertPlotStockRow} / {@code VegReceiveMapper.addStockByPlotLocation}），
     * 故领用扣减也必须按 plot 维度，镜像 {@link #deductByProductLocation}（product 维度）。</p>
     *
     * <p>SQL 在 {@code WHERE} 加 {@code product_stock >= deductQty} —— MySQL 行锁 + 数量校验同步发生，
     * 并发提交（两个工人同时领同一 plot+location）只有一次 affectedRows > 0。
     * {@code tenant_id} 由 MP 多租户拦截器在 final SQL 阶段注入；不走 MetaObjectHandler.updateFill，
     * 手工 set {@code update_by} / {@code update_time}。</p>
     *
     * @return affectedRows（0 = 库存不足 / plot/location 不匹配 / 已软删）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = product_stock - #{deductQty},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE location_id = #{locationId} "
        + "   AND plot_id     = #{plotId} "
        + "   AND product_stock >= #{deductQty} "
        + "   AND del_flag = '0'")
    int deductByPlotLocation(@Param("locationId") Long locationId,
                             @Param("plotId") Long plotId,
                             @Param("deductQty") BigDecimal deductQty,
                             @Param("userId") Long userId);

    /**
     * 按地块取默认库位（自产果蔬「按地块维度」领用时 {@code locationId} 为空的兜底）：该地块库存最多的
     * 库位；无任何库存行返 null。
     *
     * <p>镜像 {@link #selectDefaultLocationByProduct}（product 维度）。自产果蔬常落 L0003/L0004/L0006，
     * 工人未选库位时取库存最多的那个。返 null（该地块无 plot 维度 location_stock 行）→ service 抛
     * ServiceException。租户单租户显式 {@code tenant_id='1001'}（V1）。</p>
     *
     * @param plotId 地块 ID
     * @return 默认 location_id，或 null（该地块无 location_stock 行）
     */
    @Select("SELECT location_id FROM t_warehouse_location_stock "
        + " WHERE plot_id = #{plotId} AND product_id IS NULL AND del_flag = '0' AND tenant_id = '1001' "
        + " ORDER BY product_stock DESC, location_id ASC LIMIT 1")
    Long selectDefaultLocationByPlot(@Param("plotId") Long plotId);

    /**
     * 自产果蔬「按地块维度」可领用列表（步11 偏差修复 · 决策 a；Agent F mp 物资领用蔬菜 tab 消费）。
     *
     * <p>口径：以 {@code t_warehouse_location_stock} 中 {@code plot_id 非空 + product_id 为空 + 仍有库存}
     * 的行为粒度（即自产果蔬建的 plot 维度账），LEFT JOIN 地块表取地块编码、再回填作物名（plot→crop
     * 取自 {@code t_warehouse_veg_receive} 该地块自产收货的作物，取最近一条）。一个地块在多个库位有库存
     * → 各出一行（库位维度），便于工人按库位领。返 {@code product_stock > 0} 的行，
     * 外加「今天动过但已扣到 0」（{@code DATE(update_time) = CURDATE()}）的行——库存当天归零仍当日可见、
     * 便于工人当天退回 / 补登损耗，次日自然消失（row45）。</p>
     *
     * <p>VO 复用 {@link MatIssueItemVo}：</p>
     * <ul>
     *   <li>{@code productId} 置 null（自产果蔬无成品 product_id，领用时按 plotId 维度）；</li>
     *   <li>新增承载：plotId / plotCode 走 VO 扩展字段（见 VO 注释）；</li>
     *   <li>{@code productName} = 作物名（如「小白菜」）；{@code currentStock} = 该 (plot, location) 库存；
     *       {@code defaultLocationId} = 该行库位（领用时直接用）。</li>
     * </ul>
     *
     * <p>今日三量（已领/退/损）此处不按 plot 子查询（自产果蔬退/损 V1 暂不细分到 plot 维度统计），
     * 统一返 0，避免与 product 维度子查询语义混淆；如需精确按 plot 统计留 openQuestion。
     * 租户单租户显式 {@code tenant_id='1001'}。</p>
     *
     * @param locationId 库位 ID（可空，chip 选中态过滤；为空跨库位列全部自产果蔬 plot 行）
     * @return 自产果蔬可领用行（按作物名、地块编码排序）；无则空 list
     */
    @Select("""
        <script>
        SELECT NULL                                AS productId,
               NULL                                AS productCode,
               COALESCE(cr.crop_name, s.product_name) AS productName,
               COALESCE(s.product_unit, 'kg')      AS productUnit,
               cr.image_oss_id                     AS productThumb,
               'vegetable'                         AS belongType,
               s.product_stock                     AS currentStock,
               s.location_id                       AS defaultLocationId,
               s.plot_id                           AS plotId,
               pl.plot_code                        AS plotCode,
               0                                   AS todayPicked,
               0                                   AS todayReturned,
               0                                   AS todayLoss
          FROM t_warehouse_location_stock s
          LEFT JOIN t_plant_plot_info pl
            ON pl.id = s.plot_id
           AND pl.del_flag = '0'
          LEFT JOIN t_plant_crop_info cr
            ON cr.id = (
                 SELECT vr.crop_id
                   FROM t_warehouse_veg_receive vr
                  WHERE vr.receive_type = 1
                    AND vr.plot_id = s.plot_id
                    AND vr.del_flag = '0'
                    AND vr.tenant_id = '1001'
                  ORDER BY vr.receive_time DESC
                  LIMIT 1)
           AND cr.del_flag = '0'
         WHERE s.del_flag    = '0'
           AND s.tenant_id   = '1001'
           AND s.plot_id IS NOT NULL
           AND s.product_id IS NULL
           AND (s.product_stock > 0 OR DATE(s.update_time) = CURDATE())
           <if test="locationId != null"> AND s.location_id = #{locationId} </if>
         ORDER BY productName ASC, pl.plot_code ASC
        </script>
        """)
    List<MatIssueItemVo> selectSelfVegIssueItems(@Param("locationId") Long locationId);

    /**
     * 按 {@code plot_id} 取该地块最近一条自产收货的作物 ID（步11；plot→crop→related_product 解析链）。
     *
     * <p>自产果蔬 plot 维度库存行不存 crop_id，领用时需 plot→crop 反查（取 {@code t_warehouse_veg_receive}
     * 该地块 {@code receive_type=1} 最近一条的 crop_id），再 service 层 {@code crop.related_product} 解析
     * 果蔬成品 product_id 写入 pick_out 流水。查不到返 null（service 兜底 product_id=0 + warn，不阻塞领用）。
     * 租户单租户显式 {@code tenant_id='1001'}。</p>
     *
     * @param plotId 地块 ID
     * @return 该地块最近自产收货的作物 ID，或 null
     */
    @Select("SELECT crop_id FROM t_warehouse_veg_receive "
        + " WHERE receive_type = 1 AND plot_id = #{plotId} AND del_flag = '0' AND tenant_id = '1001' "
        + " ORDER BY receive_time DESC LIMIT 1")
    Long selectCropIdByPlot(@Param("plotId") Long plotId);

    /**
     * 按产品取默认库位（投喂等无库位语义场景）：该产品库存最多的库位；无任何库存行返 null。
     *
     * <p>mp 饲料领退子页不让工人选库位（投喂本无库位语义），service 在 {@code locationId} 为空时
     * 用本方法兜底解析；取 {@code product_stock} 最大的库位作默认（V1 单库位常态唯一，多库位时取
     * 最大可领的那个）。返 null（产品无任何 location_stock 行）→ service 抛 ServiceException。</p>
     *
     * <p>租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户）。</p>
     *
     * @param productId 产品 ID
     * @return 默认 location_id，或 null（产品无 location_stock 行）
     */
    @Select("SELECT location_id FROM t_warehouse_location_stock "
        + " WHERE product_id = #{productId} AND del_flag = '0' AND tenant_id = '1001' "
        + " ORDER BY product_stock DESC, location_id ASC LIMIT 1")
    Long selectDefaultLocationByProduct(@Param("productId") Long productId);

    /**
     * 按 {@code product_id} + {@code location_id} 增加库存（WMS-MAT-001 物资退回 / 外购收货 / 各入库 UPSERT）。
     *
     * <p>只累加<b>非篮子行</b>（{@code plot_id / ear_no / white_bar_no} 全 NULL）：product 维度加库存
     * 绝不并进已有地块/耳号/白条篮子行（并进会污染追溯归属，且同 (库位,产品) 多篮共存时每篮各加一次、
     * 库存虚增 N 倍）。组内尚无非篮子行（affectedRows=0）→ 调用方兜底 INSERT 一条三源标签全 NULL 的新行。</p>
     *
     * <p>退回是"加回库存"，无需校验上限。{@code update_time / update_by} 同步刷新。</p>
     *
     * @return affectedRows（0 = location/product 无非篮子行，调用方兜底 INSERT）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = product_stock + #{addQty},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE location_id = #{locationId} "
        + "   AND product_id  = #{productId} "
        + "   AND plot_id IS NULL "
        + "   AND ear_no IS NULL "
        + "   AND white_bar_no IS NULL "
        + "   AND del_flag = '0'")
    int addByProductLocation(@Param("locationId") Long locationId,
                             @Param("productId") Long productId,
                             @Param("addQty") BigDecimal addQty,
                             @Param("userId") Long userId);

    /**
     * 按 {@code (location_id, product_id, ear_no)} 且 {@code white_bar_no IS NULL} + {@code is_end=0} 原子累加
     * 分割产出库存（row157：分割原材料入库按耳号合并——同一耳号整猪两半只产出同一部位不再各建一行篮子）。
     *
     * <p>与 row150 结算不冲突：结算读 {@code cut_out_in} 流水（不读 location_stock 篮），本 UPSERT 只影响
     * 篮子行数（同耳号同产品聚合成一行）。{@code white_bar_no IS NULL} 收口到分割产出篮（燎毛白条库存篮
     * white_bar_no 非空，两者不串）。{@code tenant_id} 由 MP 拦截器注入；不走 MetaObjectHandler.updateFill，
     * 手工 set {@code update_by} / {@code update_time}。</p>
     *
     * @return affectedRows（0 = 无同键篮子行 → 调用方 insert 新篮）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = product_stock + #{addQty},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE location_id = #{locationId} "
        + "   AND product_id  = #{productId} "
        + "   AND ear_no      = #{earNo} "
        + "   AND white_bar_no IS NULL "
        + "   AND is_end = 0 "
        + "   AND del_flag = '0'")
    int addByProductLocationEarNo(@Param("locationId") Long locationId,
                                  @Param("productId") Long productId,
                                  @Param("earNo") String earNo,
                                  @Param("addQty") BigDecimal addQty,
                                  @Param("userId") Long userId);

    /**
     * 按 {@code (location_id, product_id)} 且 {@code plot_id / ear_no / white_bar_no 全 NULL} 原子累加
     * 门店退货入库到「退货专属篮」（row31：门店退回猪肉/果蔬成品无地块/耳号来源，客户拍板专设一个不带
     * 地块编号/耳号的退货篮承载；再领用/发货不带追溯——客户确认符合）。
     *
     * <p>WHERE 与 {@link #addByProductLocation} 同构（均只命中 plot/ear/white_bar 全 NULL 的非篮子行），
     * 独立方法名显式表达「退货篮」入库语义：退货量累加进退货篮、绝不并进自产果蔬地块行或分割猪肉耳号行。
     * {@code updated==0}（首次退货、退货篮不存在）→ 调用方 INSERT 一条 plot/ear/white_bar 全 NULL 的新退货篮。</p>
     *
     * @return affectedRows（0 = 该 (库位,产品) 尚无退货篮 → 调用方 INSERT 新退货篮）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = product_stock + #{addQty},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE location_id = #{locationId} "
        + "   AND product_id  = #{productId} "
        + "   AND plot_id IS NULL "
        + "   AND ear_no IS NULL "
        + "   AND white_bar_no IS NULL "
        + "   AND del_flag = '0'")
    int addByProductLocationReturn(@Param("locationId") Long locationId,
                                   @Param("productId") Long productId,
                                   @Param("addQty") BigDecimal addQty,
                                   @Param("userId") Long userId);

    /**
     * 按 {@code medicine_id} + {@code location_id} 原子扣减药品库存（ADR-0012 药品归仓库库位统一，
     * FIX-MED-MODEL-004/005 领用 / 损耗 / 治疗用药）。
     *
     * <p>SQL 在 {@code WHERE} 加 {@code product_stock >= deductQty} —— MySQL 行锁 + 数量校验同步发生，
     * 并发提交（同 medicine+location 两次领用）只有一次 affectedRows > 0。
     * {@code tenant_id} 由 MP 多租户拦截器在 final SQL 阶段注入，应用层无需显式 WHERE。</p>
     *
     * @return affectedRows（0 = 库存不足 / medicine/location 不匹配 / 已软删）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = product_stock - #{deductQty},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE location_id = #{locationId} "
        + "   AND medicine_id = #{medicineId} "
        + "   AND product_stock >= #{deductQty} "
        + "   AND del_flag = '0'")
    int deductByMedicineLocation(@Param("locationId") Long locationId,
                                 @Param("medicineId") Long medicineId,
                                 @Param("deductQty") BigDecimal deductQty,
                                 @Param("userId") Long userId);

    /**
     * 按 {@code medicine_id} + {@code location_id} 增加药品库存（ADR-0012；退回 / 采购入库）。
     *
     * <p>增回库存无需校验上限；库存行不存在（affectedRows=0）由 provider 兜底新建一行。
     * {@code tenant_id} 由 MP 拦截器注入，{@code update_time / update_by} 同步刷新。</p>
     *
     * @return affectedRows（0 = location/medicine 无库存行，provider 兜底 insert）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = product_stock + #{addQty},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE location_id = #{locationId} "
        + "   AND medicine_id = #{medicineId} "
        + "   AND del_flag = '0'")
    int addByMedicineLocation(@Param("locationId") Long locationId,
                              @Param("medicineId") Long medicineId,
                              @Param("addQty") BigDecimal addQty,
                              @Param("userId") Long userId);

    /**
     * 取默认药品库位 id（ADR-0012；{@code location_type='medicine'}）。
     *
     * <p>V1 单药品库位（L0012 / 药品库），按 id 升序取第一个作默认落位。无配置返 null
     * → provider 抛「未配置药品库位」。租户单租户显式 {@code tenant_id='1001'}。</p>
     *
     * @return 默认药品库位 location_id，或 null（未配置）
     */
    @Select("SELECT id FROM t_warehouse_location_info "
        + " WHERE location_type = 'medicine' AND del_flag = '0' AND tenant_id = '1001' "
        + " ORDER BY id LIMIT 1")
    Long selectDefaultMedicineLocationId();

    /**
     * 批量取药品当前库存合计（ADR-0012；供领用列表回显）。
     *
     * <p>只统计 {@code is_end=0}（未用完）且未软删的行；按 {@code medicine_id} 聚合 SUM(product_stock)。
     * 调用方对空集合直接跳过（不进 SQL）。返 {@code List<Map>}，key = {@code medicine_id} / {@code stock}。</p>
     *
     * @param ids 药品 id 集合（非空）
     * @return 每个 medicine_id 的库存合计行（无库存行的 medicine 不出）
     */
    @Select("""
        <script>
        SELECT medicine_id AS medicine_id, SUM(product_stock) AS stock
          FROM t_warehouse_location_stock
         WHERE medicine_id IN
         <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
           AND is_end = 0
           AND del_flag = '0'
           AND tenant_id = '1001'
         GROUP BY medicine_id
        </script>
        """)
    List<Map<String, Object>> selectMedicineStocks(@Param("ids") Collection<Long> ids);

    /**
     * 批量取药品商品当前库存合计（药品即仓库商品 · 按 {@code product_id} 聚合），供养殖端药品领用列表回显。
     *
     * <p>只统计 {@code is_end=0}（未用完）且未软删的行；按 {@code product_id} 聚合 SUM(product_stock)。
     * 调用方对空集合直接跳过（不进 SQL）。返 {@code List<Map>}，key = {@code product_id} / {@code stock}。</p>
     *
     * @param ids 药品商品 id 集合（非空）
     * @return 每个 product_id 的库存合计行（无库存行的药品不出）
     */
    @Select("""
        <script>
        SELECT product_id AS product_id, SUM(product_stock) AS stock
          FROM t_warehouse_location_stock
         WHERE product_id IN
         <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
           AND is_end = 0
           AND del_flag = '0'
           AND tenant_id = '1001'
         GROUP BY product_id
        </script>
        """)
    List<Map<String, Object>> selectProductStocks(@Param("ids") Collection<Long> ids);

    /**
     * 列出「药品库」库位下的药品商品（{@code buy_class='medicine'} 且 {@code store_location_id} 归属药品库）
     * + 跨库位库存合计。
     *
     * <p>药品归仓库统一管理，养殖端「药品领用」列表消费本查询（取代已弃用的 {@code t_breed_medicine_info}）。
     * 仅列存储库位归属「药品库」的药品：{@code store_location_id} 指向 {@code location_name='药品库'} 的库位
     * —— 生物农药库（种植生物农药）/ 生产物资库里的 medicine 商品不进养殖药品领用（row150：只展示药品库数据）。
     * LEFT JOIN 库存子查询（按 {@code product_id} 聚合 {@code is_end=0} 库存），无库存行按 0。
     * 单租户显式 {@code tenant_id='1001'}，未软删。keyword 非空时按 product_name 模糊过滤。
     * 排序：有库存（{@code stock>0}）优先，再按药品名（row150：有库存优先展示）。</p>
     *
     * @param keyword 药品名模糊过滤（可空）
     * @return 药品商品行（id / name / unit / spec / stock），有库存优先、再按药品名排序
     */
    @Select("""
        <script>
        SELECT p.id           AS id,
               p.product_name AS name,
               p.product_unit AS unit,
               p.product_spec AS spec,
               COALESCE(NULLIF(p.product_thumb, ''), p.image_oss_id) AS imageId,
               COALESCE(st.stock, 0) AS stock
          FROM t_warehouse_product_info p
          LEFT JOIN (SELECT product_id, SUM(product_stock) AS stock
                       FROM t_warehouse_location_stock
                      WHERE is_end = 0 AND del_flag = '0' AND tenant_id = '1001'
                      GROUP BY product_id) st
            ON st.product_id = p.id
         WHERE p.buy_class = 'medicine'
           AND p.del_flag  = '0'
           AND p.tenant_id = '1001'
           AND p.store_location_id IN (
                 SELECT id FROM t_warehouse_location_info
                  WHERE location_name = '药品库' AND del_flag = '0' AND tenant_id = '1001')
         <if test="keyword != null and keyword != ''">
           AND p.product_name LIKE CONCAT('%', #{keyword}, '%')
         </if>
         ORDER BY (COALESCE(st.stock, 0) > 0) DESC, p.product_name
        </script>
        """)
    List<Map<String, Object>> selectMedicineProducts(@Param("keyword") String keyword);

    /**
     * 按 id 集合列出药品商品（{@code buy_class='medicine'}）+ 跨库位库存合计。
     *
     * <p>供「用药治疗 / 批量用药」消费「近 3 天已领用药品」清单（id 来自
     * {@code t_breed_medicine_usage.medicine_id} 领用台账，已是药品库药品，不再加库位过滤）。
     * 返回列与 {@link #selectMedicineProducts} 一致（id / name / unit / spec / imageId / stock）。</p>
     *
     * @param ids 药品商品 id 集合（非空）
     * @return 药品商品行；按药品名排序
     */
    @Select("""
        <script>
        SELECT p.id           AS id,
               p.product_name AS name,
               p.product_unit AS unit,
               p.product_spec AS spec,
               COALESCE(NULLIF(p.product_thumb, ''), p.image_oss_id) AS imageId,
               COALESCE(st.stock, 0) AS stock
          FROM t_warehouse_product_info p
          LEFT JOIN (SELECT product_id, SUM(product_stock) AS stock
                       FROM t_warehouse_location_stock
                      WHERE is_end = 0 AND del_flag = '0' AND tenant_id = '1001'
                      GROUP BY product_id) st
            ON st.product_id = p.id
         WHERE p.buy_class = 'medicine'
           AND p.del_flag  = '0'
           AND p.tenant_id = '1001'
           AND p.id IN
           <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
         ORDER BY p.product_name
        </script>
        """)
    List<Map<String, Object>> selectMedicineProductsByIds(@Param("ids") Collection<Long> ids);

    /**
     * 按 {@code product_info.belong_type} 聚合活跃库存总量（DJS-FIX-ADMIN-W22-003 SummaryBar）。
     *
     * <p>JOIN {@code t_warehouse_product_info} 取 belong_type；只统计 {@code product_stock > 0} 且未软删的行。
     * 返 NULL 时调用方兜 0。租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户）。</p>
     *
     * @param belongType 字典 {@code djs_belong_type}（pork / vegetable / white_bar / dry_good / egg / gift_box）
     * @return SUM(product_stock)；可能为 null
     */
    @Select("""
        SELECT COALESCE(SUM(s.product_stock), 0)
          FROM t_warehouse_location_stock s
          JOIN t_warehouse_product_info p
            ON p.id = s.product_id
           AND p.del_flag = '0'
           AND p.tenant_id = s.tenant_id
         WHERE s.del_flag = '0'
           AND s.product_stock > 0
           AND s.tenant_id = '1001'
           AND p.belong_type = #{belongType}
        """)
    BigDecimal sumStockByBelongType(@Param("belongType") String belongType);

    /**
     * 按 {@code product_info.product_attr=2}（原材料）聚合库存总量。
     *
     * <p>口径：{@code product_attr=2}（原材料）属性的产品下所有 location_stock 求和；
     * 用于"其他"业态需求确认 SummaryBar 的"当前原料库存"显示。</p>
     */
    @Select("""
        SELECT COALESCE(SUM(s.product_stock), 0)
          FROM t_warehouse_location_stock s
          JOIN t_warehouse_product_info p
            ON p.id = s.product_id
           AND p.del_flag = '0'
           AND p.tenant_id = s.tenant_id
         WHERE s.del_flag = '0'
           AND s.product_stock > 0
           AND s.tenant_id = '1001'
           AND p.product_attr = 2
        """)
    BigDecimal sumRawMaterialStock();

    /**
     * mp 包材库列表（WMS-FLOW-001）：以 {@code product_info.belong_type=#{belongType}} 产品为粒度，
     * LEFT JOIN location_stock 聚合当前库存 + 最近盘点日期。
     *
     * <p>口径：belong_type 在后端强制 eq（不在前端 filter，契约 14）。一个包材产品可分布多个库位
     * → {@code SUM(product_stock)} / {@code MAX(latest_check_time)}。产品无任何 location_stock 行
     * → currentStock=0 / latestCheckTime=null（LEFT JOIN 保留产品行）。租户单租户显式 {@code tenant_id='1001'}。</p>
     *
     * <p>排序：{@code sortBy='stock'} → 库存升序（缺货优先）；否则按产品名（拼音/字典序）。</p>
     *
     * @param belongType 字典 {@code djs_belong_type}（包材为 {@code package}）
     * @param sortBy     {@code stock} 库存升序 / 其余按 product_name
     * @return 包材列表项
     */
    @Select("""
        <script>
        SELECT p.id                          AS productId,
               p.product_id                  AS productCode,
               p.product_name                AS productName,
               p.product_unit                AS productUnit,
               COALESCE(SUM(s.product_stock), 0) AS currentStock,
               MAX(s.latest_check_time)      AS latestCheckTime
          FROM t_warehouse_product_info p
          LEFT JOIN t_warehouse_location_stock s
            ON s.product_id = p.id
           AND s.del_flag   = '0'
           AND s.tenant_id  = p.tenant_id
         WHERE p.del_flag    = '0'
           AND p.tenant_id   = '1001'
           AND p.belong_type = #{belongType}
         GROUP BY p.id, p.product_id, p.product_name, p.product_unit
        <choose>
          <when test="sortBy == 'stock'">
            ORDER BY currentStock ASC, p.product_name ASC
          </when>
          <otherwise>
            ORDER BY p.product_name ASC
          </otherwise>
        </choose>
        </script>
        """)
    List<PackingItemVo> selectPackingItems(@Param("belongType") String belongType,
                                           @Param("sortBy") String sortBy);

    /**
     * 包材种类数（WMS-FLOW-001）：DISTINCT 未软删 {@code belong_type=#{belongType}} 产品数。
     */
    @Select("""
        SELECT COUNT(1)
          FROM t_warehouse_product_info p
         WHERE p.del_flag    = '0'
           AND p.tenant_id   = '1001'
           AND p.belong_type = #{belongType}
        """)
    Long countProductsByBelongType(@Param("belongType") String belongType);

    /**
     * 指定归属类型产品的最近一次盘点时间（WMS-FLOW-001 包材库今日总览 KPI）。
     *
     * <p>MAX(latest_check_time) over 该 belong_type 下所有 location_stock；无盘点返 null。</p>
     */
    @Select("""
        SELECT MAX(s.latest_check_time)
          FROM t_warehouse_location_stock s
          JOIN t_warehouse_product_info p
            ON p.id = s.product_id
           AND p.del_flag = '0'
           AND p.tenant_id = s.tenant_id
         WHERE s.del_flag    = '0'
           AND s.tenant_id   = '1001'
           AND p.belong_type = #{belongType}
        """)
    Date selectLatestCheckTimeByBelongType(@Param("belongType") String belongType);

    /**
     * 盘点完成回写（WMS-STOCK-001 completeCheck）：按 {@code product_id} + {@code location_id}
     * 把非篮子行库存设为目标绝对值，并刷新 {@code latest_check_time} / {@code check_result}。
     *
     * <p>只校准<b>非篮子行</b>（{@code plot_id / ear_no / white_bar_no} 全 NULL）——篮子行不参与盘点
     * SET（耳号/地块/白条篮子的账随各自业务链走）。同 (库位,产品) 多篮共存时若无行限定，每篮都会被
     * SET 成实盘量（组合计 = 实盘 × N）。调用方传入的 {@code checkStock} 必须是<b>非篮目标量 =
     * 实盘量 − {@link #sumBasketStockByProductLocation} 篮子行合计</b>（直接传实盘组总量会使组合计
     * 虚增一个篮子合计）；affectedRows=0 且非篮目标量 &gt; 0 才按该量新建产品维度行，差异以盘点流水留痕。</p>
     *
     * <p>与领用 / 退回的"增量"语义不同——盘点是"以实盘量校准账面"，直接 SET 绝对值。
     * {@code tenant_id} 由 MP 多租户拦截器在 final SQL 阶段注入；不走 MetaObjectHandler.updateFill，
     * 手工 set {@code update_by} / {@code update_time}。</p>
     *
     * @param locationId  库位 ID
     * @param productId   产品 ID
     * @param checkStock  非篮目标量（设为非篮子行新库存绝对值）
     * @param checkResult 盘点结果（字典 {@code djs_check_result}）
     * @param userId      操作人
     * @return affectedRows（0 = location/product 无非篮子行，service 兜底处理）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = #{checkStock},"
        + "       latest_check_time = NOW(),"
        + "       check_result = #{checkResult},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE location_id = #{locationId} "
        + "   AND product_id  = #{productId} "
        + "   AND plot_id IS NULL "
        + "   AND ear_no IS NULL "
        + "   AND white_bar_no IS NULL "
        + "   AND del_flag = '0'")
    int setStockAfterCheck(@Param("locationId") Long locationId,
                           @Param("productId") Long productId,
                           @Param("checkStock") BigDecimal checkStock,
                           @Param("checkResult") Integer checkResult,
                           @Param("userId") Long userId);

    /**
     * 按 {@code product_id} + {@code location_id} 聚合组内库存合计（盘点「系统现量」口径）。
     *
     * <p>同 (库位,产品) 可能存在多行（耳号/地块/白条篮子 + 非篮子行），系统现量必须取组合计 SUM ——
     * 任取一行（LIMIT 1）在多篮组上会拿到任意一篮的余量、盘点差异随之失真。无任何行返 0。
     * 租户单租户显式 {@code tenant_id='1001'}（V1）。</p>
     *
     * @param locationId 库位 ID
     * @param productId  产品 ID
     * @return 组内 SUM(product_stock)，无行返 0
     */
    @Select("SELECT COALESCE(SUM(product_stock), 0) FROM t_warehouse_location_stock "
        + " WHERE location_id = #{locationId} "
        + "   AND product_id  = #{productId} "
        + "   AND del_flag = '0' "
        + "   AND tenant_id = '1001'")
    BigDecimal sumStockByProductLocation(@Param("locationId") Long locationId,
                                         @Param("productId") Long productId);

    /**
     * 按 {@code product_id} + {@code location_id} 聚合组内<b>篮子行</b>库存合计
     * （{@code plot_id / ear_no / white_bar_no} 任一非 NULL）。
     *
     * <p>盘点回写换算口径：实盘量是组物理总量（含篮子），而 {@link #setStockAfterCheck} 只校准非篮子行
     * —— 非篮子行目标量 = 实盘量 − 篮子行合计。不减去篮子合计直接 SET 实盘量，组合计会虚增一个篮子
     * 合计（篮子行的账随各自业务链走、不参与盘点 SET）。无篮子行返 0。
     * 租户单租户显式 {@code tenant_id='1001'}（V1）。</p>
     *
     * @param locationId 库位 ID
     * @param productId  产品 ID
     * @return 组内篮子行 SUM(product_stock)，无篮子行返 0
     */
    @Select("SELECT COALESCE(SUM(product_stock), 0) FROM t_warehouse_location_stock "
        + " WHERE location_id = #{locationId} "
        + "   AND product_id  = #{productId} "
        + "   AND (plot_id IS NOT NULL OR ear_no IS NOT NULL OR white_bar_no IS NOT NULL) "
        + "   AND del_flag = '0' "
        + "   AND tenant_id = '1001'")
    BigDecimal sumBasketStockByProductLocation(@Param("locationId") Long locationId,
                                               @Param("productId") Long productId);

    /**
     * mp 物资领用「二级库」chip（FIX-WMS-MATISSUE-001）：某业态（{@code belong_type} 一或多值）下的产品
     * 实际分布在哪些库位 —— DISTINCT location。
     *
     * <p>口径：{@code location_stock JOIN product_info WHERE p.belong_type IN (...)}，
     * 取这些业态产品出现过的全部库位（含库存为 0 的，工人仍可能去退回 / 看该库历史）。
     * 一个库位可能存多个该业态产品 → DISTINCT 去重。租户单租户显式 {@code tenant_id='1001'}。</p>
     *
     * <p>原型：猪肉产品 tab → 白条库 / 冻品库 / 鲜品库 / 红白脏库；蔬菜 → 蔬菜保鲜库 / 重口蔬菜保鲜室。
     * 「猪肉产品」tab 含 {@code pork + white_bar} 两业态（项目猪肉链口径，参 {@code TraceServiceImpl.PORK_BELONG_TYPES}）。
     * 包材业态产品常无库位关联（location_stock 无行）→ 返空 list，前端不渲染 chip 段。</p>
     *
     * @param belongTypes 字典 {@code djs_belong_type} 值列表（如 [pork, white_bar] / [vegetable] / [egg]）
     * @return 该业态分布的库位列表（按库位名排序）；无则空 list
     */
    @Select("""
        <script>
        SELECT DISTINCT l.id          AS locationId,
               l.location_code        AS locationCode,
               l.location_name        AS locationName
          FROM t_warehouse_location_stock s
          JOIN t_warehouse_product_info p
            ON p.id = s.product_id
           AND p.del_flag = '0'
           AND p.tenant_id = s.tenant_id
          JOIN t_warehouse_location_info l
            ON l.id = s.location_id
           AND l.del_flag = '0'
           AND l.tenant_id = s.tenant_id
         WHERE s.del_flag    = '0'
           AND s.tenant_id   = '1001'
           AND p.belong_type IN
           <foreach collection="belongTypes" item="bt" open="(" separator="," close=")">#{bt}</foreach>
         ORDER BY l.location_name ASC
        </script>
        """)
    List<MatIssueLocationVo> selectMatIssueLocations(@Param("belongTypes") List<String> belongTypes);

    /**
     * mp 物资领用「待领产品卡」列表（FIX-WMS-MATISSUE-001）：以某业态（{@code belong_type} 一或多值）产品为粒度，
     * LEFT JOIN location_stock 聚合当前库存 + 默认库位，并子查询今日已领 / 已退 / 已损（全部人，与 admin 同口径）。
     *
     * <p>口径：</p>
     * <ul>
     *   <li>{@code belong_type IN (...)} 在后端强制过滤（不在前端 filter，跨层契约一致性）；
     *       「猪肉产品」tab 含 {@code pork + white_bar}（项目猪肉链口径）。</li>
     *   <li>{@code locationId} 可空：传了则只统计该库位库存（chip 选中态过滤），且只返该库位有库存行的产品；
     *       为空则跨库位 SUM 全业态产品（tab 默认态）。</li>
     *   <li>{@code currentStock} = 跨库位（或指定库位）SUM(product_stock)；产品无 location_stock 行 → 0。</li>
     *   <li>{@code defaultLocationId} = 该产品库存最多的库位（点卡进表单时作默认 locationId）。</li>
     *   <li>今日三量 = 子查询 {@code stock_flow WHERE product_id=p.id AND flow_type=? AND DATE(flow_date)=CURDATE()}
     *       （<b>全部人</b>，不按 operator 过滤——PC/admin 录入也计入，与 {@code MatFlowServiceImpl.ensureTodayCapacity}
     *       的全量额度口径一致；问题来源邓博测试 row3：PC 录入的领用/退回/损耗在 mp 不显示）。</li>
     * </ul>
     *
     * <p>仅返启用产品（{@code product_status=0}）。排序：库存升序（缺货优先）再按产品名。
     * 租户单租户显式 {@code tenant_id='1001'}。{@code lastPickTime}（排序用）取该产品全部人今日最近一次领用时间。</p>
     *
     * @param belongTypes 字典 {@code djs_belong_type} 值列表（如 [pork, white_bar] / [vegetable]）
     * @param locationId  库位 ID（可空，chip 选中态）
     * @param userId      当前登录人 user_id（今日三量按人统计）
     * @return 待领产品卡列表；无则空 list
     */
    @Select("""
        <script>
        SELECT p.id                              AS productId,
               p.product_id                      AS productCode,
               p.product_name                    AS productName,
               p.product_unit                    AS productUnit,
               COALESCE(p.product_thumb, p.image_oss_id) AS productThumb,
               p.belong_type                     AS belongType,
               COALESCE(SUM(s.product_stock), 0) AS currentStock,
               (SELECT s2.location_id
                  FROM t_warehouse_location_stock s2
                 WHERE s2.product_id = p.id
                   AND s2.del_flag = '0'
                   AND s2.tenant_id = '1001'
                 ORDER BY s2.product_stock DESC, s2.location_id ASC
                 LIMIT 1)                         AS defaultLocationId,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = p.id <if test="locationId != null"> AND f.warehouse_id = #{locationId} </if>
                            AND f.flow_type IN ('prod_pick_out','dept_pick_out','pick_out') AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayPicked,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = p.id <if test="locationId != null"> AND f.warehouse_id = #{locationId} </if>
                            AND f.flow_type IN ('prod_return_in','pick_return_in','store_return_in') AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayReturned,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = p.id <if test="locationId != null"> AND f.warehouse_id = #{locationId} </if>
                            AND f.flow_type = 'loss' AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayLoss,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = p.id <if test="locationId != null"> AND f.warehouse_id = #{locationId} </if>
                            AND f.flow_type = 'feed_out' AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayFeed,
               CASE WHEN p.belong_type IN ('vegetable','egg','dry_good','other')
                    THEN COALESCE((SELECT SUM(ih.product_weight) FROM t_warehouse_product_inhouse ih
                                    WHERE ih.product_id = p.id AND DATE(ih.produce_date) = CURDATE()
                                      AND ih.del_flag = '0' AND ih.tenant_id = '1001'), 0)
                    ELSE COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                                    WHERE f.product_id = p.id <if test="locationId != null"> AND f.warehouse_id = #{locationId} </if>
                                      AND f.flow_type IN ('prod_pick_out','dept_pick_out','pick_out')
                                      AND DATE(f.flow_date) = CURDATE() AND f.del_flag = '0' AND f.tenant_id = '1001'), 0)
                       - COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                                    WHERE f.product_id = p.id <if test="locationId != null"> AND f.warehouse_id = #{locationId} </if>
                                      AND f.flow_type IN ('prod_return_in','pick_return_in')
                                      AND DATE(f.flow_date) = CURDATE() AND f.del_flag = '0' AND f.tenant_id = '1001'), 0)
                       - COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                                    WHERE f.product_id = p.id <if test="locationId != null"> AND f.warehouse_id = #{locationId} </if>
                                      AND f.flow_type = 'loss'
                                      AND DATE(f.flow_date) = CURDATE() AND f.del_flag = '0' AND f.tenant_id = '1001'), 0)
                       - COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                                    WHERE f.product_id = p.id <if test="locationId != null"> AND f.warehouse_id = #{locationId} </if>
                                      AND f.flow_type = 'feed_out'
                                      AND DATE(f.flow_date) = CURDATE() AND f.del_flag = '0' AND f.tenant_id = '1001'), 0)
                    END                              AS remainReturnable,
               (SELECT MAX(f.flow_date) FROM t_warehouse_stock_flow f
                  WHERE f.product_id = p.id
                    AND f.flow_type IN ('prod_pick_out','dept_pick_out','pick_out') AND DATE(f.flow_date) = CURDATE()
                    AND f.del_flag = '0' AND f.tenant_id = '1001') AS lastPickTime
          FROM t_warehouse_product_info p
          LEFT JOIN t_warehouse_location_stock s
            ON s.product_id = p.id
           AND s.del_flag   = '0'
           AND s.tenant_id  = p.tenant_id
           <if test="locationId != null"> AND s.location_id = #{locationId} </if>
         WHERE p.del_flag      = '0'
           AND p.tenant_id     = '1001'
           AND p.product_status = 0
           AND (COALESCE(p.product_attr, 0) != 1 OR COALESCE(p.product_type, 0) != 1)
           AND p.belong_type IN
           <foreach collection="belongTypes" item="bt" open="(" separator="," close=")">#{bt}</foreach>
           <if test="locationId != null">
             AND EXISTS (SELECT 1 FROM t_warehouse_location_stock s3
                          WHERE s3.product_id = p.id AND s3.location_id = #{locationId}
                            AND s3.del_flag = '0' AND s3.tenant_id = '1001')
           </if>
         GROUP BY p.id, p.product_id, p.product_name, p.product_unit, p.image_oss_id, p.belong_type
         ORDER BY (lastPickTime IS NOT NULL) DESC, lastPickTime DESC, currentStock ASC, p.product_name ASC
        </script>
        """)
    List<MatIssueItemVo> selectMatIssueItems(@Param("belongTypes") List<String> belongTypes,
                                             @Param("locationId") Long locationId,
                                             @Param("userId") Long userId);

    /**
     * mp 物资领用「按耳号源篮子」列表（「按源手选」领用，猪肉(分割)按耳号）。
     *
     * <p>口径：该 pork 原料产品（{@code product_id=#{productId}}）的 {@code location_stock} 篮子，
     * {@code ear_no} 非空 + {@code product_stock > 0}（外加「今天动过但已扣到 0」
     * {@code DATE(update_time)=CURDATE()} 的篮，row45：当天归零仍可见、便于当天退回 / 补登损耗，次日消失），
     * 一行一篮（{@code batchCode = ear_no} 耳号）。{@code locationId} 可空（chip 选中态过滤）。</p>
     *
     * <p>每篮今日已领 best-effort = 今天 {@code product_inhouse} SUM(product_weight)
     * WHERE {@code product_id + ear_no}（inhouse 带 ear_no 源标签）；今日退/损按耳号聚合
     * {@code stock_flow}（{@code returnByBatch} / {@code lossByBatch} 已把 {@code ear_no} 写入流水），
     * 一个耳号可能跨多个库位篮 → 退/损是耳号级总量，只贴到该耳号 {@code MIN(id)} 首篮、其余篮置 0
     * 避免每篮重复显示（同耳号各篮之和 = 真实总量）。{@code batchId} / {@code productId} /
     * {@code locationId} 物理列是 BIGINT，VO 字段为 String，由 MyBatis 自动转字符串（snowflake 防截断）。
     * 租户单租户显式 {@code tenant_id='1001'}（V1）。</p>
     *
     * @param productId  pork 原料产品 ID（必填）
     * @param locationId 库位 ID（可空，chip 选中态过滤）
     * @return 该产品的耳号源篮子列表（按 id 升序 = 入库先后）；无则空 list
     */
    @Select("""
        <script>
        SELECT MIN(s.id)                     AS batchId,
               s.ear_no                       AS batchCode,
               s.product_id                   AS productId,
               MAX(s.product_name)            AS productName,
               COALESCE(MAX(s.product_unit), 'kg') AS productUnit,
               SUM(s.product_stock)           AS currentStock,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.ear_no = s.ear_no AND f.product_id = s.product_id AND f.warehouse_id = s.location_id
                            AND f.flow_type IN ('prod_pick_out','dept_pick_out','pick_out')
                            AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayPicked,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.ear_no = s.ear_no AND f.product_id = s.product_id AND f.warehouse_id = s.location_id
                            AND f.flow_type IN ('prod_return_in','pick_return_in')
                            AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayReturned,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.ear_no = s.ear_no AND f.product_id = s.product_id AND f.warehouse_id = s.location_id
                            AND f.flow_type = 'loss'
                            AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayLoss,
               s.location_id                  AS locationId
          FROM t_warehouse_location_stock s
         WHERE s.del_flag      = '0'
           AND s.tenant_id     = '1001'
           AND s.product_id    = #{productId}
           AND s.ear_no IS NOT NULL
           AND (s.product_stock > 0 OR DATE(s.update_time) = CURDATE())
           <if test="locationId != null"> AND s.location_id = #{locationId} </if>
         GROUP BY s.product_id, s.ear_no, s.location_id
         ORDER BY batchId ASC
        </script>
        """)
    List<MatIssueBasketVo> selectPorkIssueByEar(@Param("productId") Long productId,
                                                @Param("locationId") Long locationId);

    /**
     * mp 物资领用「按地块源卡」列表（「按源手选」领用，蔬菜(自产)按地块）。
     *
     * <p>口径：该自产果蔬原料产品（{@code product_id=#{productId}}）的 {@code location_stock} 篮子，
     * {@code plot_id} 非空 + {@code product_stock > 0}，<b>按地块（{@code plot_id}）聚合，一行一地块</b>
     * （一个地块在小程序里就是一张卡，多库位是线下实际、mp 不体现 —— Kevin 拍板）。LEFT JOIN
     * {@code t_plant_plot_info} 取地块编号（{@code batchCode = plot_code}）。返仍有库存的地块，外加「今天动过但
     * 已扣到 0」（{@code DATE(update_time)=CURDATE()}）的篮所属地块（row45：当天归零仍可见、便于当天退回 /
     * 补登损耗，次日消失）。{@code locationId} 可空（chip 选中态过滤；过滤后聚合只剩该库位篮，currentStock 仍是过滤后之和）。</p>
     *
     * <ul>
     *   <li>{@code batchId} = 该地块各库位篮的 {@code MIN(s.id)}（FIFO 首篮的真实 {@code location_stock.id}）
     *       —— 领用提交回传它，service {@code pickByBatch} 据该篮的 {@code plot_id} 识别为自产果蔬地块卡 →
     *       按 {@code (productId, plotId)} 跨库位 FIFO 扣减（不只扣首篮），故聚合后 batchId 仍能正确扣账；</li>
     *   <li>{@code currentStock} = 该地块各库位篮 {@code SUM(product_stock)}（地块级总量）；</li>
     *   <li>{@code todayPicked} = 该地块今日 {@code stock_flow}(pick_out 三键) SUM(change_quantity)（已是 plot 维，
     *       无库位维重复）—— 当日「已领用」毛量，与产品卡 {@link #selectMatIssueItems} 同源（pick_out 流水）。
     *       <b>不取 {@code product_inhouse}</b>：inhouse 会被退回/损耗/饲喂/打包扣减成「剩余待打包」，拿来当「已领」
     *       会比真实领用量小、且可能小于已退回量（退回>已领的假象）；</li>
     *   <li>{@code todayReturned} / {@code todayLoss} = 该地块今日 {@code stock_flow}(return_in / loss) SUM
     *       （{@code plot_id} 维度直接 SUM，无需贴首篮 hack —— 聚合后一行一地块本就不重复）；</li>
     *   <li>{@code locationId} = 该地块 FIFO 首篮所在库位（{@code MIN(s.id)} 对应行），mp 卡片回传作 BO locationId
     *       兜底（service 走地块 FIFO 跨库位扣减，该 locationId 仅 UX 占位、不限制扣减范围）。</li>
     * </ul>
     *
     * <p>字段 String 转换 / 租户同 {@link #selectPorkIssueByEar}。</p>
     *
     * @param productId  自产果蔬原料产品 ID（必填）
     * @param locationId 库位 ID（可空，chip 选中态过滤）
     * @return 该产品的地块源卡列表（按 batchId 升序，即各地块 FIFO 首篮 id）；无则空 list
     */
    @Select("""
        <script>
        SELECT MIN(s.id)                     AS batchId,
               MAX(pl.plot_code)             AS batchCode,
               s.product_id                  AS productId,
               MAX(s.product_name)           AS productName,
               COALESCE(MAX(s.product_unit), 'kg') AS productUnit,
               SUM(s.product_stock)          AS currentStock,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = s.product_id AND f.plot_id = s.plot_id
                            AND f.flow_type IN ('prod_pick_out','dept_pick_out','pick_out')
                            AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayPicked,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = s.product_id AND f.plot_id = s.plot_id AND f.flow_type IN ('prod_return_in','pick_return_in')
                            AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayReturned,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = s.product_id AND f.plot_id = s.plot_id AND f.flow_type = 'loss'
                            AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayLoss,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = s.product_id AND f.plot_id = s.plot_id AND f.flow_type = 'feed_out'
                            AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayFeed,
               (SELECT s3.location_id FROM t_warehouse_location_stock s3
                 WHERE s3.id = MIN(s.id))     AS locationId
          FROM t_warehouse_location_stock s
          LEFT JOIN t_plant_plot_info pl
            ON pl.id = s.plot_id
           AND pl.del_flag = '0'
         WHERE s.del_flag      = '0'
           AND s.tenant_id     = '1001'
           AND s.product_id    = #{productId}
           AND s.plot_id IS NOT NULL
           AND (s.product_stock > 0 OR DATE(s.update_time) = CURDATE())
           <if test="locationId != null"> AND s.location_id = #{locationId} </if>
         GROUP BY s.product_id, s.plot_id
         ORDER BY batchId ASC
        </script>
        """)
    List<MatIssueBasketVo> selectVegIssueByPlot(@Param("productId") Long productId,
                                                @Param("locationId") Long locationId);

    /**
     * mp 物资领用「退货专属篮卡」列表（row31：门店退回猪肉/果蔬成品无地块/耳号来源，客户拍板专设一张
     * 「退货」卡承载——{@code plot_id / ear_no / white_bar_no 全 NULL} 的退货篮）。
     *
     * <p>与 {@link #selectVegIssueByPlot}（只列 {@code plot_id IS NOT NULL} 地块卡）/
     * {@link #selectPorkIssueByEar}（只列 {@code ear_no IS NOT NULL} 耳号卡）互补：本方法只捞该产品的退货篮
     * （三源标签全空），一产品聚合成一张 {@code batchCode=''}（空标签）卡，service 层追加到地块卡/耳号卡列表末尾。
     * 标签留空：外购与退回库存都无地块/耳号来源，不打「退货」字样避免把外购库存误标退货（row41），前端 chip 为空即隐藏。
     * 自产果蔬(plot 维)/分割猪肉(ear 维) 的正常库存三源标签有值，天然不入本卡；只有门店退货/外购入库的篮进来。
     * 今日三量按「三源标签全空」的流水聚合（即退货篮自身的领/退/损，pick_out/return_in/loss）。</p>
     *
     * <p>{@code batchId = MIN(s.id)}（退货篮真实 id，pickByBatch 据其 plot/ear 皆空落通用分支扣减、
     * 产出 pick_out 流水 plot/ear 皆空——再领用/发货不带追溯，客户已确认符合）。返仍有库存或今日动过的退货篮。</p>
     *
     * @param productId 产品 ID（果蔬取原材料 id / 猪肉取成品 id，与 issue*Batches 入参一致）
     * @param locationId 库位 ID（可空，chip 选中态过滤）
     * @return 该产品的退货篮卡（0~1 张，无退货库存则空 list）
     */
    @Select("""
        <script>
        SELECT MIN(s.id)                     AS batchId,
               ''                            AS batchCode,
               s.product_id                  AS productId,
               MAX(s.product_name)           AS productName,
               COALESCE(MAX(s.product_unit), 'kg') AS productUnit,
               SUM(s.product_stock)          AS currentStock,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = s.product_id AND f.plot_id IS NULL AND f.ear_no IS NULL AND f.white_bar_no IS NULL
                            AND f.flow_type IN ('prod_pick_out','dept_pick_out','pick_out')
                            AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayPicked,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = s.product_id AND f.plot_id IS NULL AND f.ear_no IS NULL AND f.white_bar_no IS NULL
                            AND f.flow_type IN ('prod_return_in','pick_return_in','store_return_in')
                            AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayReturned,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = s.product_id AND f.plot_id IS NULL AND f.ear_no IS NULL AND f.white_bar_no IS NULL
                            AND f.flow_type = 'loss' AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayLoss,
               (SELECT s3.location_id FROM t_warehouse_location_stock s3 WHERE s3.id = MIN(s.id)) AS locationId
          FROM t_warehouse_location_stock s
         WHERE s.del_flag      = '0'
           AND s.tenant_id     = '1001'
           AND s.product_id    = #{productId}
           AND s.plot_id IS NULL
           AND s.ear_no IS NULL
           AND s.white_bar_no IS NULL
           AND s.location_id IS NOT NULL
           AND (s.product_stock > 0 OR DATE(s.update_time) = CURDATE())
           <if test="locationId != null"> AND s.location_id = #{locationId} </if>
         GROUP BY s.product_id
        </script>
        """)
    List<MatIssueBasketVo> selectReturnBasketCards(@Param("productId") Long productId,
                                                   @Param("locationId") Long locationId);

    /**
     * mp 物资领用「按库位类型列待领外购商品卡」列表（WMS-OUTSOURCE-001：种植库 crop_loc / 养殖库 farm_loc）。
     *
     * <p>与 {@link #selectMatIssueItems}（按 belong_type 组织）对称，本方法按 {@code location_type} 组织：
     * 只列 {@code product_type=2}（外购）+ {@code product_status=0}（启用）+ 在该 location_type 库位下有库存
     * 的商品。用 {@code JOIN}（非 LEFT JOIN）+ {@code HAVING currentStock>0} 保证只返有货外购商品。</p>
     *
     * <p>口径：</p>
     * <ul>
     *   <li>{@code currentStock} = 该商品在该 location_type 全部库位（或指定 locationId）的 SUM(product_stock)。</li>
     *   <li>{@code defaultLocationId} = 该商品在该 location_type 下库存最多的库位（点卡进表单作默认 locationId）。</li>
     *   <li>{@code buyClass} = 商品分类（字典 djs_buy_class），前端去重成分类筛选 chip。</li>
     *   <li>今日三量子查询沿用 {@link #selectMatIssueItems} 写法（<b>全部人</b>，不按 operator 过滤），与提交时
     *       额度校验同源。</li>
     * </ul>
     *
     * <p>租户单租户显式 {@code tenant_id='1001'}（V1）。</p>
     *
     * @param locationType 字典 {@code djs_location_type} 的 value（{@code crop_loc} / {@code farm_loc}）
     * @param locationId   库位 ID（可空，chip 选中态过滤；为空则该类型全库位聚合）
     * @param userId       当前登录人 user_id（今日三量按人统计）
     * @return 待领外购商品卡列表（库存升序，再按商品名）；无则空 list
     */
    @Select("""
        <script>
        SELECT p.id                              AS productId,
               p.product_id                      AS productCode,
               p.product_name                    AS productName,
               p.product_unit                    AS productUnit,
               COALESCE(p.product_thumb, p.image_oss_id) AS productThumb,
               p.belong_type                     AS belongType,
               p.buy_class                       AS buyClass,
               COALESCE(SUM(s.product_stock), 0) AS currentStock,
               (SELECT s2.location_id
                  FROM t_warehouse_location_stock s2
                  JOIN t_warehouse_location_info l2
                    ON l2.id = s2.location_id
                   AND l2.location_type = #{locationType}
                   AND l2.del_flag = '0'
                   AND l2.tenant_id = '1001'
                 WHERE s2.product_id = p.id
                   AND s2.del_flag = '0'
                   AND s2.tenant_id = '1001'
                 ORDER BY s2.product_stock DESC, s2.location_id ASC
                 LIMIT 1)                         AS defaultLocationId,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = p.id <if test="locationId != null"> AND f.warehouse_id = #{locationId} </if>
                            AND f.flow_type IN ('prod_pick_out','dept_pick_out','pick_out') AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayPicked,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = p.id <if test="locationId != null"> AND f.warehouse_id = #{locationId} </if>
                            AND f.flow_type IN ('prod_return_in','pick_return_in','store_return_in') AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayReturned,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = p.id <if test="locationId != null"> AND f.warehouse_id = #{locationId} </if>
                            AND f.flow_type = 'loss' AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayLoss,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = p.id <if test="locationId != null"> AND f.warehouse_id = #{locationId} </if>
                            AND f.flow_type = 'feed_out' AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayFeed,
               CASE WHEN p.belong_type IN ('vegetable','egg','dry_good','other')
                    THEN COALESCE((SELECT SUM(ih.product_weight) FROM t_warehouse_product_inhouse ih
                                    WHERE ih.product_id = p.id AND DATE(ih.produce_date) = CURDATE()
                                      AND ih.del_flag = '0' AND ih.tenant_id = '1001'), 0)
                    ELSE COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                                    WHERE f.product_id = p.id <if test="locationId != null"> AND f.warehouse_id = #{locationId} </if>
                                      AND f.flow_type IN ('prod_pick_out','dept_pick_out','pick_out')
                                      AND DATE(f.flow_date) = CURDATE() AND f.del_flag = '0' AND f.tenant_id = '1001'), 0)
                       - COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                                    WHERE f.product_id = p.id <if test="locationId != null"> AND f.warehouse_id = #{locationId} </if>
                                      AND f.flow_type IN ('prod_return_in','pick_return_in')
                                      AND DATE(f.flow_date) = CURDATE() AND f.del_flag = '0' AND f.tenant_id = '1001'), 0)
                       - COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                                    WHERE f.product_id = p.id <if test="locationId != null"> AND f.warehouse_id = #{locationId} </if>
                                      AND f.flow_type = 'loss'
                                      AND DATE(f.flow_date) = CURDATE() AND f.del_flag = '0' AND f.tenant_id = '1001'), 0)
                       - COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                                    WHERE f.product_id = p.id <if test="locationId != null"> AND f.warehouse_id = #{locationId} </if>
                                      AND f.flow_type = 'feed_out'
                                      AND DATE(f.flow_date) = CURDATE() AND f.del_flag = '0' AND f.tenant_id = '1001'), 0)
                    END                              AS remainReturnable,
               (SELECT MAX(f.flow_date) FROM t_warehouse_stock_flow f
                  WHERE f.product_id = p.id
                    AND f.flow_type IN ('prod_pick_out','dept_pick_out','pick_out') AND DATE(f.flow_date) = CURDATE()
                    AND f.del_flag = '0' AND f.tenant_id = '1001') AS lastPickTime
          FROM t_warehouse_product_info p
          JOIN t_warehouse_location_stock s
            ON s.product_id = p.id
           AND s.del_flag   = '0'
           AND s.tenant_id  = p.tenant_id
          JOIN t_warehouse_location_info l
            ON l.id = s.location_id
           AND l.del_flag = '0'
           AND l.tenant_id = s.tenant_id
           AND l.location_type = #{locationType}
           <if test="locationId != null"> AND s.location_id = #{locationId} </if>
         WHERE p.del_flag       = '0'
           AND p.tenant_id      = '1001'
           AND p.product_status = 0
           AND p.product_type   = 2
         GROUP BY p.id, p.product_id, p.product_name, p.product_unit, p.product_thumb,
                  p.image_oss_id, p.belong_type, p.buy_class
        HAVING currentStock > 0
         ORDER BY (lastPickTime IS NOT NULL) DESC, lastPickTime DESC, currentStock ASC, p.product_name ASC
        </script>
        """)
    List<MatIssueItemVo> selectMatIssueItemsByType(@Param("locationType") String locationType,
                                                   @Param("locationId") Long locationId,
                                                   @Param("userId") Long userId);

    /**
     * admin「生产物资领用」行粒度列表（WMS-MATPICK-ADMIN-001，行55 admin 镜像）。
     *
     * <p>与 mp {@link #selectMatIssueItems}（按 product 聚合成卡 + 当前登录人今日三量）口径不同：</p>
     * <ul>
     *   <li><b>行粒度</b> = {@code location_stock} 行（一个产品在多库位 / 多耳号 / 多白条 / 多地块 → 各出一行），
     *       admin 列表列「产品编码 / 库位 / 产品名 / 当前库存 / 单位 / 耳号 / 白条号 / 地块编号 / 今日四量」，需库位 +
     *       耳号 + 白条号 + 地块编号到行，故不聚合到 product；</li>
     *   <li><b>{@code batchId} = 该行 {@code location_stock.id}</b>（复合业务键 {@code (product_id, location_id,
     *       ear_no, white_bar_no, plot_id)} 的物理主键代理）：admin 领用 / 退回 / 损耗提交时回传本 {@code batchId}，
     *       service 走 {@code pickByBatch} / {@code returnByBatch} / {@code lossByBatch} 按该篮 id 行锁精确扣减 +
     *       写该篮 {@code ear_no} / {@code white_bar_no} 源标签。<b>防串扣</b>：不回传 batchId 而只按
     *       {@code (productId, locationId)} 会走 {@code consumePorkBaskets} 的 FIFO 跨耳号扣减（点耳号 A 行却扣到
     *       耳号 B 篮，row126），故猪肉行必须带 batchId 走精确篮；</li>
     *   <li><b>今日四量按全部人 + 按本行库位</b>（admin 看全部人记录，不按 {@code operator_id} 过滤；但
     *       <b>按 {@code f.warehouse_id = s.location_id} 限定到本行库位</b>，多库位同产品各行只统计本库位流水，
     *       不再把某库位的领/退/损泄漏到该产品所有库位行 —— row41）：今日已领 / 退 / 损 / 饲喂
     *       = 该 {@code (product_id, location_id)} 当日 {@code stock_flow} 各 flow_type SUM。
     *       领用覆盖 {@code prod_pick_out / dept_pick_out / pick_out}（三键，FIX-WMS-FLOWDICT-003 拆分）；
     *       退回覆盖 {@code prod_return_in / pick_return_in}；损耗 {@code loss}；饲喂 {@code feed_out}。</li>
     * </ul>
     *
     * <p>口径细节：</p>
     * <ul>
     *   <li>{@code belong_type IN (...)}（tab 业态过滤，后端强制，不在前端 filter）；只列启用产品
     *       （{@code product_status=0}）+ 仍有库存（{@code product_stock > 0}）的 location_stock 行，
     *       外加「今天动过但已扣到 0」（{@code DATE(update_time)=CURDATE()}）的行（row45：库存当天归零仍当日
     *       可见、便于当天退回 / 补登损耗，次日自然消失）。</li>
     *   <li>{@code keyword} 可空：非空时模糊匹配产品名 / 库位名 / 耳号 / 地块编号（admin 搜索栏 4 字段合一）。</li>
     *   <li>{@code defaultLocationId} = 该行库位（admin 点行进操作弹窗直接用，不再解析默认库位）。</li>
     *   <li>productThumb 取 {@code COALESCE(product_thumb, image_oss_id)}（service 层 resolver 再回填 url）。</li>
     * </ul>
     *
     * <p>租户单租户显式 {@code tenant_id='1001'}（V1）；snowflake id 由 MyBatis 自动转字符串防截断（VO Long → JSON）。</p>
     *
     * @param belongTypes 字典 {@code djs_belong_type} 值列表（如 [pork] / [vegetable] / [package]）
     * @param keyword     模糊关键字（可空；匹配产品名 / 库位名 / 耳号 / 地块编号）
     * @return 行粒度待领产品行（按库存升序、产品名）；无则空 list
     */
    @Select("""
        <script>
        SELECT s.id                              AS batchId,
               p.id                              AS productId,
               p.product_id                      AS productCode,
               p.product_name                    AS productName,
               p.product_unit                    AS productUnit,
               COALESCE(p.product_thumb, p.image_oss_id) AS productThumb,
               p.belong_type                     AS belongType,
               s.product_stock                   AS currentStock,
               s.location_id                     AS defaultLocationId,
               l.location_name                   AS locationName,
               s.ear_no                          AS earNo,
               s.white_bar_no                    AS whiteBarNo,
               s.plot_id                         AS plotId,
               pl.plot_code                      AS plotCode,
               <!-- row32：今日四量子查询按 product_id + warehouse_id（+ ear_no / white_bar_no）关联，
                    不按 plot_id —— 退回(prod_return_in/pick_return_in)/损耗(loss)流水不写 plot_id（仅 pick_out
                    带 plot），若强求 f.plot_id = s.plot_id 则自产果蔬（库存行带 plot）的今日退回/损耗恒 0，
                    与 mp 按 product 汇总不一致（客户 row32）。去 plot 关联后按库位维度真实汇总。 -->
               <!-- row39：今日出库(领用)子查询 **须** 按 plot_id 关联——pick_out 流水带 plot（自产果蔬按地块领用），
                    同一产品同库位的多地块行若不按 plot 关联会共享同一领用汇总（四季豆两地块都显 0.501，实际只领了一个地块）。
                    NULL-safe：猪肉耳号行 / 非地块物资行 plot_id 两端皆 NULL 仍匹配（不影响 row32——退回/损耗流水 plot_id=NULL
                    故那三个子查询保持不按 plot 关联）。 -->
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = p.id AND f.warehouse_id = s.location_id
                            AND (f.ear_no = s.ear_no OR (f.ear_no IS NULL AND s.ear_no IS NULL))
                            AND (f.white_bar_no = s.white_bar_no OR (f.white_bar_no IS NULL AND s.white_bar_no IS NULL))
                            AND (f.plot_id = s.plot_id OR (f.plot_id IS NULL AND s.plot_id IS NULL))
                            AND f.flow_type IN ('prod_pick_out','dept_pick_out','pick_out')
                            AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayPicked,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = p.id AND f.warehouse_id = s.location_id
                            AND (f.ear_no = s.ear_no OR (f.ear_no IS NULL AND s.ear_no IS NULL))
                            AND (f.white_bar_no = s.white_bar_no OR (f.white_bar_no IS NULL AND s.white_bar_no IS NULL))
                            AND f.flow_type IN ('prod_return_in','pick_return_in','store_return_in')
                            AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayReturned,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = p.id AND f.warehouse_id = s.location_id
                            AND (f.ear_no = s.ear_no OR (f.ear_no IS NULL AND s.ear_no IS NULL))
                            AND (f.white_bar_no = s.white_bar_no OR (f.white_bar_no IS NULL AND s.white_bar_no IS NULL))
                            AND f.flow_type = 'loss' AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayLoss,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = p.id AND f.warehouse_id = s.location_id
                            AND (f.ear_no = s.ear_no OR (f.ear_no IS NULL AND s.ear_no IS NULL))
                            AND (f.white_bar_no = s.white_bar_no OR (f.white_bar_no IS NULL AND s.white_bar_no IS NULL))
                            AND f.flow_type = 'feed_out' AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayFeed
          FROM t_warehouse_location_stock s
          JOIN t_warehouse_product_info p
            ON p.id = s.product_id
           AND p.del_flag = '0'
           AND p.tenant_id = s.tenant_id
          LEFT JOIN t_warehouse_location_info l
            ON l.id = s.location_id
           AND l.del_flag = '0'
           AND l.tenant_id = s.tenant_id
          LEFT JOIN t_plant_plot_info pl
            ON pl.id = s.plot_id
           AND pl.del_flag = '0'
         WHERE s.del_flag       = '0'
           AND s.tenant_id      = '1001'
           AND (s.product_stock > 0 OR DATE(s.update_time) = CURDATE())
           AND p.product_status = 0
           <!-- row2：与 mp selectMatIssueItems 同口径——排除自产成品（product_attr=1 且 product_type=1），
                领用列表只列可领原料/物资；防 admin 多显成品行导致与 mp 卡数不一致。 -->
           AND (COALESCE(p.product_attr, 0) != 1 OR COALESCE(p.product_type, 0) != 1)
           <!-- row2：包材/物资类无耳号、无地块的库存行若 location_id 为空 = 无意义的孤儿库存行
                （mp 按 product 聚合时已折叠进总库存），admin 行视图剔除，避免多出"库位空"重复行使两端行数不一致。
                有耳号(猪肉篮)/有地块(自产果蔬篮)的行保留——它们靠 ear_no/plot_id 而非 location 成行。 -->
           AND NOT (s.location_id IS NULL AND s.ear_no IS NULL AND s.plot_id IS NULL)
           <!-- row27：毛菜鲜品库（L0006）= 毛菜处理前的原料暂存库，果蔬不在此生产领用（与 mp matPack
                TAB_LOCATION_RULE 蔬菜业态只取蔬菜保鲜库/重口味蔬菜库、排除毛菜库同口径）。 -->
           AND NOT (p.belong_type = 'vegetable' AND l.location_name LIKE '%毛菜%')
           AND p.belong_type IN
           <foreach collection="belongTypes" item="bt" open="(" separator="," close=")">#{bt}</foreach>
           <if test="keyword != null and keyword != ''">
             AND (p.product_name LIKE CONCAT('%', #{keyword}, '%')
               OR l.location_name LIKE CONCAT('%', #{keyword}, '%')
               OR s.ear_no LIKE CONCAT('%', #{keyword}, '%')
               OR s.white_bar_no LIKE CONCAT('%', #{keyword}, '%')
               OR pl.plot_code LIKE CONCAT('%', #{keyword}, '%'))
           </if>
         ORDER BY s.product_stock ASC, p.product_name ASC, s.location_id ASC
        </script>
        """)
    List<MatIssueItemVo> selectAdminMatIssueRows(@Param("belongTypes") List<String> belongTypes,
                                                 @Param("keyword") String keyword);

    /**
     * admin「生产物资领用」<b>猪肉产品聚合</b>列表（row24：admin 猪肉从「按篮行粒度」收敛成「一产品一行」）。
     *
     * <p>问题背景（row24）：admin 猪肉 tab 原走行粒度 {@link #selectAdminMatIssueRows}，一篮（耳号 + 白条号 +
     * 库位）一行 → 同一分割产品 5 篮就平铺 5 行，客户报「admin 比 mp 多显示产品」。mp 猪肉是
     * {@link #selectMatIssueItems} 按 {@code product_id} 聚合成一卡、篮明细点卡下钻。本查询把 admin 猪肉
     * 也聚合到产品维度，与 mp 卡口径一致（一产品一行、库存 = 该产品所有篮 SUM），篮明细进领用弹框选源
     * （{@link #selectAdminPorkBaskets}），提交仍按篮 {@code batchId} 走 service {@code pickByBatch} 精确扣减。</p>
     *
     * <p>与 mp {@link #selectMatIssueItems} 的差异（务必保住 admin 现口径）：</p>
     * <ul>
     *   <li><b>今日四量按全部人</b>：与 mp 端点子查询同样<b>不按 operator 过滤</b>（mp 那侧 {@code userId} 入参
     *       实际未在 SQL 使用，今日量本就是全部人 + 按 productId 全库位），admin 直接同口径全部人聚合，无 per-user
     *       回归。领用覆盖 {@code prod_pick_out / dept_pick_out / pick_out}；退回 {@code prod_return_in /
     *       pick_return_in}；损耗 {@code loss}；饲喂 {@code feed_out}（猪肉无饲喂恒 0）。</li>
     *   <li><b>零库存维持只显有货</b>：{@code JOIN}（非 mp 的 LEFT JOIN）+ {@code HAVING} 保「该产品尚有库存
     *       （SUM {@code product_stock} &gt; 0）或旗下任一篮当天动过（{@code MAX(update_time) = CURDATE()}）」
     *       的产品才出行——与行粒度 {@link #selectAdminMatIssueRows} 的「有货或当天动过」门槛一致，
     *       不引入 mp LEFT JOIN 全量的零库存产品行。</li>
     *   <li><b>belong_type 维持 pork</b>：仅按传入 tab 业态过滤（不纳 white_bar 整只），排除自产成品
     *       （{@code product_attr=1 且 product_type=1}）与孤儿库存行，与行粒度口径一致。</li>
     * </ul>
     *
     * <p>产品聚合行的 {@code batchId} / {@code earNo} / {@code whiteBarNo} / {@code plotId} / {@code plotCode}
     * 均为 null（篮级信息下沉到弹框选源）；{@code defaultLocationId} 取该产品库存最多的库位（弹框未选篮时兜底）。
     * 租户单租户显式 {@code tenant_id='1001'}（V1）。</p>
     *
     * @param belongTypes 字典 {@code djs_belong_type} 值列表（猪肉 tab 传 [pork]）
     * @param keyword     模糊关键字（可空；匹配产品名 / 产品编码）
     * @return 产品聚合行（按库存升序、产品名）；无则空 list
     */
    @Select("""
        <script>
        SELECT p.id                              AS productId,
               p.product_id                      AS productCode,
               p.product_name                    AS productName,
               p.product_unit                    AS productUnit,
               COALESCE(p.product_thumb, p.image_oss_id) AS productThumb,
               p.belong_type                     AS belongType,
               -- admin row55：猪肉按 (产品, 耳号) 分行，耳号列展示；无耳号（整只白条）ear_no 为 NULL 折成一产品一行。
               s.ear_no                          AS earNo,
               MAX(s.white_bar_no)               AS whiteBarNo,
               -- row56：行已到耳号粒度、篮唯一（每 (产品,耳号) 恰一篮），batchId 直接带在行上，
               -- 领用出库弹框去掉源篮子选择，提交按此 batchId 走 pickByBatch 精确扣该篮。
               MIN(s.id)                         AS batchId,
               COALESCE(SUM(s.product_stock), 0) AS currentStock,
               (SELECT s2.location_id
                  FROM t_warehouse_location_stock s2
                 WHERE s2.product_id = p.id
                   AND (s2.ear_no = s.ear_no OR (s2.ear_no IS NULL AND s.ear_no IS NULL))
                   AND s2.del_flag = '0'
                   AND s2.tenant_id = '1001'
                 ORDER BY s2.product_stock DESC, s2.location_id ASC
                 LIMIT 1)                         AS defaultLocationId,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = p.id
                            AND (f.ear_no = s.ear_no OR (f.ear_no IS NULL AND s.ear_no IS NULL))
                            AND f.flow_type IN ('prod_pick_out','dept_pick_out','pick_out')
                            AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayPicked,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = p.id
                            AND (f.ear_no = s.ear_no OR (f.ear_no IS NULL AND s.ear_no IS NULL))
                            AND f.flow_type IN ('prod_return_in','pick_return_in','store_return_in')
                            AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayReturned,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = p.id
                            AND (f.ear_no = s.ear_no OR (f.ear_no IS NULL AND s.ear_no IS NULL))
                            AND f.flow_type = 'loss' AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayLoss,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = p.id
                            AND (f.ear_no = s.ear_no OR (f.ear_no IS NULL AND s.ear_no IS NULL))
                            AND f.flow_type = 'feed_out' AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayFeed
          FROM t_warehouse_product_info p
          JOIN t_warehouse_location_stock s
            ON s.product_id = p.id
           AND s.del_flag   = '0'
           AND s.tenant_id  = p.tenant_id
           AND NOT (s.location_id IS NULL AND s.ear_no IS NULL AND s.plot_id IS NULL)
         WHERE p.del_flag       = '0'
           AND p.tenant_id      = '1001'
           AND p.product_status = 0
           AND (COALESCE(p.product_attr, 0) != 1 OR COALESCE(p.product_type, 0) != 1)
           AND p.belong_type IN
           <foreach collection="belongTypes" item="bt" open="(" separator="," close=")">#{bt}</foreach>
           <if test="keyword != null and keyword != ''">
             AND (p.product_name LIKE CONCAT('%', #{keyword}, '%')
               OR p.product_id LIKE CONCAT('%', #{keyword}, '%')
               OR s.ear_no LIKE CONCAT('%', #{keyword}, '%'))
           </if>
         GROUP BY p.id, p.product_id, p.product_name, p.product_unit, p.product_thumb,
                  p.image_oss_id, p.belong_type, s.ear_no
        HAVING SUM(s.product_stock) > 0 OR MAX(DATE(s.update_time)) = CURDATE()
         ORDER BY p.product_name ASC, s.ear_no ASC
        </script>
        """)
    List<MatIssueItemVo> selectAdminMatPorkProducts(@Param("belongTypes") List<String> belongTypes,
                                                    @Param("keyword") String keyword);

    /**
     * admin「生产物资领用」<b>猪肉篮明细</b>（row24 弹框选源：产品行点领用 / 退回 / 损耗 → 列该产品全部篮供选）。
     *
     * <p>口径同行粒度 {@link #selectAdminMatIssueRows} 的猪肉篮（一行 = 一个 {@code (ear_no, white_bar_no,
     * location)} 篮），但收敛到单产品：</p>
     * <ul>
     *   <li><b>放宽 null-ear</b>：admin 现在能领 {@code ear_no IS NULL} 的白条整只篮（分割前整只 white_bar），
     *       故本查询<b>不加</b> mp {@link #selectPorkIssueByEar} 的 {@code ear_no IS NOT NULL} 约束——避免收敛后
     *       admin 弹框丢掉现在能领的 null-ear 篮（row24 回归点）。{@code batchCode} = 耳号，无耳号则回退白条号，
     *       仍无则该库位标识，保证每篮有可读次标题。</li>
     *   <li><b>门槛一致</b>：{@code product_stock > 0} 或当天动过（{@code DATE(update_time) = CURDATE()}），
     *       与行粒度一致（当天归零篮仍可选、便于当天退回 / 补登损耗）。</li>
     *   <li>今日三量 = 该篮 {@code (product_id, warehouse_id, ear_no/white_bar_no)} 当日 {@code stock_flow} SUM
     *       （全部人）；{@code batchId} = {@code MIN(location_stock.id)}（同 (ear_no, white_bar_no, location)
     *       篮聚合，提交回传走 {@code pickByBatch} 精确扣该篮）。</li>
     * </ul>
     *
     * @param productId pork 产品 ID（必填）
     * @param keyword   模糊关键字（可空；匹配耳号 / 白条号）
     * @return 该产品的篮明细（按 id 升序 = 入库先后）；无则空 list
     */
    @Select("""
        <script>
        SELECT MIN(s.id)                     AS batchId,
               COALESCE(s.ear_no, s.white_bar_no, CONCAT('库位', s.location_id)) AS batchCode,
               s.product_id                   AS productId,
               MAX(s.product_name)            AS productName,
               COALESCE(MAX(s.product_unit), 'kg') AS productUnit,
               SUM(s.product_stock)           AS currentStock,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = s.product_id AND f.warehouse_id = s.location_id
                            AND (f.ear_no = s.ear_no OR (f.ear_no IS NULL AND s.ear_no IS NULL))
                            AND (f.white_bar_no = s.white_bar_no OR (f.white_bar_no IS NULL AND s.white_bar_no IS NULL))
                            AND f.flow_type IN ('prod_pick_out','dept_pick_out','pick_out')
                            AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayPicked,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = s.product_id AND f.warehouse_id = s.location_id
                            AND (f.ear_no = s.ear_no OR (f.ear_no IS NULL AND s.ear_no IS NULL))
                            AND (f.white_bar_no = s.white_bar_no OR (f.white_bar_no IS NULL AND s.white_bar_no IS NULL))
                            AND f.flow_type IN ('prod_return_in','pick_return_in','store_return_in')
                            AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayReturned,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = s.product_id AND f.warehouse_id = s.location_id
                            AND (f.ear_no = s.ear_no OR (f.ear_no IS NULL AND s.ear_no IS NULL))
                            AND (f.white_bar_no = s.white_bar_no OR (f.white_bar_no IS NULL AND s.white_bar_no IS NULL))
                            AND f.flow_type = 'loss'
                            AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0' AND f.tenant_id = '1001'), 0) AS todayLoss,
               s.location_id                  AS locationId
          FROM t_warehouse_location_stock s
         WHERE s.del_flag      = '0'
           AND s.tenant_id     = '1001'
           AND s.product_id    = #{productId}
           AND (s.product_stock > 0 OR DATE(s.update_time) = CURDATE())
           <if test="keyword != null and keyword != ''">
             AND (s.ear_no LIKE CONCAT('%', #{keyword}, '%')
               OR s.white_bar_no LIKE CONCAT('%', #{keyword}, '%'))
           </if>
         GROUP BY s.product_id, s.ear_no, s.white_bar_no, s.location_id
         ORDER BY batchId ASC
        </script>
        """)
    List<MatIssueBasketVo> selectAdminPorkBaskets(@Param("productId") Long productId,
                                                  @Param("keyword") String keyword);

}
