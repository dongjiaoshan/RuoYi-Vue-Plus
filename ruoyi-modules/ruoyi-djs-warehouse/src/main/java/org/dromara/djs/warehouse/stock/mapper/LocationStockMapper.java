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
     * <p>SQL 在 {@code WHERE} 加 {@code product_stock >= deductQty} —— MySQL 行锁 + 数量校验同步发生，
     * 并发提交（两个工人同时领同一 product+location）只有一次 affectedRows > 0。</p>
     *
     * @return affectedRows（0 = 库存不足 / product/location 不匹配 / 已软删）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = product_stock - #{deductQty},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE location_id = #{locationId} "
        + "   AND product_id  = #{productId} "
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
     * → 各出一行（库位维度），便于工人按库位领。仅返 {@code product_stock > 0} 的行。</p>
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
           AND s.product_stock > 0
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
     * 按 {@code product_id} + {@code location_id} 增加库存（WMS-MAT-001 物资退回）。
     *
     * <p>退回是"加回库存"，无需校验上限；但需要保证库存记录存在（不存在不允许凭空创建库存，service 层
     * 走 update 失败兜底）。{@code update_time / update_by} 同步刷新。</p>
     *
     * @return affectedRows（0 = location_id / product_id 不匹配，service 兜底）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = product_stock + #{addQty},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE location_id = #{locationId} "
        + "   AND product_id  = #{productId} "
        + "   AND del_flag = '0'")
    int addByProductLocation(@Param("locationId") Long locationId,
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
     * 把库存设为实盘绝对值，并刷新 {@code latest_check_time} / {@code check_result}。
     *
     * <p>与领用 / 退回的"增量"语义不同——盘点是"以实盘量校准账面"，直接 SET 绝对值。
     * {@code tenant_id} 由 MP 多租户拦截器在 final SQL 阶段注入；不走 MetaObjectHandler.updateFill，
     * 手工 set {@code update_by} / {@code update_time}。</p>
     *
     * @param locationId  库位 ID
     * @param productId   产品 ID
     * @param checkStock  实盘量（设为新库存绝对值）
     * @param checkResult 盘点结果（字典 {@code djs_check_result}）
     * @param userId      操作人
     * @return affectedRows（0 = location/product 不匹配，service 兜底处理）
     */
    @Update("UPDATE t_warehouse_location_stock "
        + "   SET product_stock = #{checkStock},"
        + "       latest_check_time = NOW(),"
        + "       check_result = #{checkResult},"
        + "       update_by = #{userId},"
        + "       update_time = NOW() "
        + " WHERE location_id = #{locationId} "
        + "   AND product_id  = #{productId} "
        + "   AND del_flag = '0'")
    int setStockAfterCheck(@Param("locationId") Long locationId,
                           @Param("productId") Long productId,
                           @Param("checkStock") BigDecimal checkStock,
                           @Param("checkResult") Integer checkResult,
                           @Param("userId") Long userId);

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
     * LEFT JOIN location_stock 聚合当前库存 + 默认库位，并子查询当前登录人今日已领 / 已退 / 已损。
     *
     * <p>口径：</p>
     * <ul>
     *   <li>{@code belong_type IN (...)} 在后端强制过滤（不在前端 filter，跨层契约一致性）；
     *       「猪肉产品」tab 含 {@code pork + white_bar}（项目猪肉链口径）。</li>
     *   <li>{@code locationId} 可空：传了则只统计该库位库存（chip 选中态过滤），且只返该库位有库存行的产品；
     *       为空则跨库位 SUM 全业态产品（tab 默认态）。</li>
     *   <li>{@code currentStock} = 跨库位（或指定库位）SUM(product_stock)；产品无 location_stock 行 → 0。</li>
     *   <li>{@code defaultLocationId} = 该产品库存最多的库位（点卡进表单时作默认 locationId）。</li>
     *   <li>今日三量 = 子查询 {@code stock_flow WHERE operator_id=#{userId} AND product_id=p.id
     *       AND flow_type=? AND DATE(flow_date)=CURDATE()}，与 {@code MatFlowServiceImpl.ensureTodayCapacity}
     *       同源（保证卡上「最大可领 / 退 / 损」与提交时后端额度校验一致）。</li>
     * </ul>
     *
     * <p>仅返启用产品（{@code product_status=0}）。排序：库存升序（缺货优先）再按产品名。
     * 租户单租户显式 {@code tenant_id='1001'}。{@code userId} 为空（理论不会，端点已 SaCheckLogin）时三量子查询返 0。</p>
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
                          WHERE f.operator_id = #{userId} AND f.product_id = p.id
                            AND f.flow_type = 'pick_out' AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0'), 0) AS todayPicked,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.operator_id = #{userId} AND f.product_id = p.id
                            AND f.flow_type = 'return_in' AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0'), 0) AS todayReturned,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.operator_id = #{userId} AND f.product_id = p.id
                            AND f.flow_type = 'loss' AND DATE(f.flow_date) = CURDATE()
                            AND f.del_flag = '0'), 0) AS todayLoss
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
         ORDER BY currentStock ASC, p.product_name ASC
        </script>
        """)
    List<MatIssueItemVo> selectMatIssueItems(@Param("belongTypes") List<String> belongTypes,
                                             @Param("locationId") Long locationId,
                                             @Param("userId") Long userId);

    /**
     * mp 物资领用「按耳号源篮子」列表（「按源手选」领用，猪肉(分割)按耳号）。
     *
     * <p>口径：该 pork 原料产品（{@code product_id=#{productId}}）的 {@code location_stock} 篮子，
     * {@code ear_no} 非空 + {@code product_stock > 0}，一行一篮（{@code batchCode = ear_no} 耳号）。
     * 只返有库存的篮（用完的篮自动不出现 → 「用完不可选」）。{@code locationId} 可空（chip 选中态过滤）。</p>
     *
     * <p>每篮今日已领 best-effort = 今天 {@code product_inhouse} SUM(product_weight)
     * WHERE {@code product_id + ear_no}（inhouse 带 ear_no 源标签）；今日退/损按篮维度不细分 → 0
     * （{@code stock_flow} 无 ear_no 篮维度，不为此加 DDL）。{@code batchId} / {@code productId} /
     * {@code locationId} 物理列是 BIGINT，VO 字段为 String，由 MyBatis 自动转字符串（snowflake 防截断）。
     * 租户单租户显式 {@code tenant_id='1001'}（V1）。</p>
     *
     * @param productId  pork 原料产品 ID（必填）
     * @param locationId 库位 ID（可空，chip 选中态过滤）
     * @return 该产品的耳号源篮子列表（按 id 升序 = 入库先后）；无则空 list
     */
    @Select("""
        <script>
        SELECT s.id                          AS batchId,
               s.ear_no                       AS batchCode,
               s.product_id                   AS productId,
               s.product_name                 AS productName,
               COALESCE(s.product_unit, 'kg') AS productUnit,
               s.product_stock                AS currentStock,
               COALESCE((SELECT SUM(ih.product_weight) FROM t_warehouse_product_inhouse ih
                          WHERE ih.product_id = s.product_id AND ih.ear_no = s.ear_no
                            AND DATE(ih.produce_date) = CURDATE()
                            AND ih.del_flag = '0' AND ih.tenant_id = '1001'), 0) AS todayPicked,
               0                              AS todayReturned,
               0                              AS todayLoss,
               s.location_id                  AS locationId
          FROM t_warehouse_location_stock s
         WHERE s.del_flag      = '0'
           AND s.tenant_id     = '1001'
           AND s.product_id    = #{productId}
           AND s.ear_no IS NOT NULL
           AND s.product_stock > 0
           <if test="locationId != null"> AND s.location_id = #{locationId} </if>
         ORDER BY s.id ASC
        </script>
        """)
    List<MatIssueBasketVo> selectPorkIssueByEar(@Param("productId") Long productId,
                                                @Param("locationId") Long locationId);

    /**
     * mp 物资领用「按地块源篮子」列表（「按源手选」领用，蔬菜(自产)按地块）。
     *
     * <p>口径：该自产果蔬原料产品（{@code product_id=#{productId}}）的 {@code location_stock} 篮子，
     * {@code plot_id} 非空 + {@code product_stock > 0}，一行一篮，LEFT JOIN {@code t_plant_plot_info}
     * 取地块编号（{@code batchCode = plot_code}）。只返有库存的篮（用完的篮自动不出现）。
     * {@code locationId} 可空（chip 选中态过滤）。</p>
     *
     * <p>每篮今日已领 best-effort = 今天 {@code product_inhouse} SUM(product_weight)
     * WHERE {@code product_id + plot_id}（inhouse 带 plot_id 源标签）；今日退/损按篮维度不细分 → 0。
     * 字段 String 转换 / 租户同 {@link #selectPorkIssueByEar}。</p>
     *
     * @param productId  自产果蔬原料产品 ID（必填）
     * @param locationId 库位 ID（可空，chip 选中态过滤）
     * @return 该产品的地块源篮子列表（按 id 升序）；无则空 list
     */
    @Select("""
        <script>
        SELECT s.id                          AS batchId,
               pl.plot_code                   AS batchCode,
               s.product_id                   AS productId,
               s.product_name                 AS productName,
               COALESCE(s.product_unit, 'kg') AS productUnit,
               s.product_stock                AS currentStock,
               COALESCE((SELECT SUM(ih.product_weight) FROM t_warehouse_product_inhouse ih
                          WHERE ih.product_id = s.product_id AND ih.plot_id = s.plot_id
                            AND DATE(ih.produce_date) = CURDATE()
                            AND ih.del_flag = '0' AND ih.tenant_id = '1001'), 0) AS todayPicked,
               0                              AS todayReturned,
               0                              AS todayLoss,
               s.location_id                  AS locationId
          FROM t_warehouse_location_stock s
          LEFT JOIN t_plant_plot_info pl
            ON pl.id = s.plot_id
           AND pl.del_flag = '0'
         WHERE s.del_flag      = '0'
           AND s.tenant_id     = '1001'
           AND s.product_id    = #{productId}
           AND s.plot_id IS NOT NULL
           AND s.product_stock > 0
           <if test="locationId != null"> AND s.location_id = #{locationId} </if>
         ORDER BY s.id ASC
        </script>
        """)
    List<MatIssueBasketVo> selectVegIssueByPlot(@Param("productId") Long productId,
                                                @Param("locationId") Long locationId);

}
