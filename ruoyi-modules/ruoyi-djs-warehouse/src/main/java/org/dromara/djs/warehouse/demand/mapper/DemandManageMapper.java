package org.dromara.djs.warehouse.demand.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandProductStoreDetailVo;
import org.dromara.djs.warehouse.pack.domain.vo.StoreDemandCopiesVo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 需求管理 Mapper（WMS-DEMAND-001）。
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
public interface DemandManageMapper extends BaseMapperPlus<DemandManage, DemandManageVo> {

    /**
     * 今日 KPI 横条主表聚合（DJS-FIX-ADMIN-W22-007）：1 query 出 5 数。
     *
     * <p>「已调配」= 状态 {@code IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')}
     * （已脱离待确认态、未取消；状态集合是固定枚举不随租户变，直接写死 SQL）。</p>
     *
     * <p>返 Map 键：{@code todayPigDemand / todayVegSpeciesDemand / todayVegSpeciesAssigned /
     * todayOtherDemand / todayOtherAssigned}（白条已调配头数走 {@link #selectTodayPigAssigned} 子表）。</p>
     *
     * <p>租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户，与
     * {@code LocationStockMapper} 自定义聚合范式一致）。</p>
     *
     * @param today 今日日期（Asia/Shanghai 算，由 service 传入，不用 DB CURDATE() 避免时区雷）
     * @return 聚合 Map（单行）
     */
    @Select("""
        SELECT
          COALESCE(SUM(CASE WHEN product_type IN ('white_bar','pig') THEN demand_quantity ELSE 0 END), 0) AS todayPigDemand,
          COUNT(DISTINCT CASE WHEN product_type = 'vegetable' THEN product_id END) AS todayVegSpeciesDemand,
          COUNT(DISTINCT CASE WHEN product_type = 'vegetable'
                AND demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')
                THEN product_id END) AS todayVegSpeciesAssigned,
          COUNT(CASE WHEN product_type IN ('other','gift_box','dry','egg') THEN 1 END) AS todayOtherDemand,
          COUNT(CASE WHEN product_type IN ('other','gift_box','dry','egg')
                AND demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')
                THEN 1 END) AS todayOtherAssigned
        FROM t_warehouse_demand_manage
        WHERE demand_date = #{today}
          AND del_flag = '0'
          AND tenant_id = '1001'
        """)
    Map<String, Object> selectTodayKpiMainAgg(@Param("today") LocalDate today);

    /**
     * 今日白条已调配头数（DJS-FIX-ADMIN-W22-007）：子表去重耳号计数。
     *
     * <p>demand_pig 子表存在一行 = 这头已指定，{@code COUNT(DISTINCT ear_no)} 即已调配头数。</p>
     *
     * <p>租户隔离：显式 {@code tenant_id='1001'}（V1 单租户，与 {@code LocationStockMapper} 范式一致）。</p>
     *
     * @param today 今日日期
     * @return 今日白条已指定的去重耳号数
     */
    @Select("""
        SELECT COUNT(DISTINCT dp.ear_no)
        FROM t_warehouse_demand_pig dp
        JOIN t_warehouse_demand_manage dm ON dm.id = dp.demand_id
             AND dm.del_flag = '0'
             AND dm.tenant_id = dp.tenant_id
        WHERE dm.demand_date = #{today}
          AND dm.product_type IN ('white_bar','pig')
          AND dp.del_flag = '0'
          AND dp.tenant_id = '1001'
        """)
    Integer selectTodayPigAssigned(@Param("today") LocalDate today);

    /**
     * 原子累加 {@code shipped_count}（CROSS-FLOW-003 发货反向更新；并发安全）。
     *
     * <p><b>禁 select+updateById</b>：两个发货事件同秒触发会丢更新。本方法用 DB 端
     * {@code SET shipped_count = COALESCE(shipped_count,0) + #{delta}} 把累加压到一条
     * UPDATE，靠 InnoDB 行锁串行化同一行的累加，不依赖 transition 的 Redisson 锁。</p>
     *
     * <p>租户隔离：未启全局 MP 拦截器，原生 {@code @Update} 不会自动注入 tenant 过滤，
     * 显式手写 {@code tenant_id}（V1 全 {@code '1001'}，与本 mapper 既有聚合 SQL 范式一致）。
     * {@code del_flag='0'}（CHAR(1) 未删，非数字 0）。</p>
     *
     * @param demandId 需求 ID
     * @param tenantId 租户 ID（V1 传 {@code "1001"}）
     * @param delta    本次发货增量（{@code event.shippedQuantity}）
     * @return 受影响行数（0 = demand 已删 / 不存在，listener 据此短路）
     */
    @Update("""
        UPDATE t_warehouse_demand_manage
        SET shipped_count = COALESCE(shipped_count, 0) + #{delta}
        WHERE id = #{demandId}
          AND tenant_id = #{tenantId}
          AND del_flag = '0'
        """)
    int incrementShipped(@Param("demandId") Long demandId,
                         @Param("tenantId") String tenantId,
                         @Param("delta") BigDecimal delta);

    /**
     * 某产品各门店未发货需求量聚合（DJS-FIX-WMS-PACK 打包录入「门店(N份)」标签条）。
     *
     * <p>按 {@code product_id} 取各门店未发货需求量
     * {@code SUM(demand_quantity - COALESCE(shipped_count,0))}，仅保留剩余 &gt; 0 的门店，
     * 排除已取消单（{@code demand_status <> 'CANCELLED'}）。JOIN {@code t_md_store} 取门店名。</p>
     *
     * <p>租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户，与本 mapper
     * 既有聚合 SQL 范式一致）；{@code del_flag='0'}（CHAR(1) 未删）。</p>
     *
     * @param productId 产品 FK（{@code t_warehouse_product_info.id}）
     * @return 各门店未发货份数（按 copies 降序；无则空 List）
     */
    @Select("""
        SELECT dm.store_id AS storeId,
               s.store_name AS storeName,
               SUM(dm.demand_quantity - COALESCE(dm.shipped_count, 0)) AS copies
        FROM t_warehouse_demand_manage dm
        JOIN t_md_store s ON s.id = dm.store_id
             AND s.del_flag = '0'
             AND s.tenant_id = dm.tenant_id
        WHERE dm.product_id = #{productId}
          AND dm.store_id IS NOT NULL
          AND dm.demand_status <> 'CANCELLED'
          AND dm.del_flag = '0'
          AND dm.tenant_id = '1001'
        GROUP BY dm.store_id, s.store_name
        HAVING copies > 0
        ORDER BY copies DESC
        """)
    List<StoreDemandCopiesVo> selectStoreDemandCopies(@Param("productId") Long productId);

    /**
     * 按 product_id 聚合「有该产品需求的去重门店数」（D-FIX-24 决策 #8 列表 storeCount）。
     *
     * <p>口径：同 product_id 下，非取消单（{@code demand_status <> 'CANCELLED'}）、有门店
     * （{@code store_id IS NOT NULL}）的去重门店数。仅统计入参 product_id 集合，避免全表扫。</p>
     *
     * <p>租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户）；
     * {@code del_flag='0'}（CHAR(1) 未删）。</p>
     *
     * @param productIds 当前页涉及的产品 ID 集合（空集调用方需短路，不传空 IN）
     * @return 每行 {@code {productId, storeCount}}（Map 键 productId / storeCount）
     */
    @Select("""
        <script>
        SELECT product_id AS productId,
               COUNT(DISTINCT store_id) AS storeCount
        FROM t_warehouse_demand_manage
        WHERE store_id IS NOT NULL
          AND demand_status &lt;&gt; 'CANCELLED'
          AND del_flag = '0'
          AND tenant_id = '1001'
          AND product_id IN
          <foreach collection="productIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
        GROUP BY product_id
        </script>
        """)
    List<Map<String, Object>> selectStoreCountByProductIds(@Param("productIds") Collection<Long> productIds);

    /**
     * 某产品「按门店聚合需求量明细」（D-FIX-24 决策 #8 详情弹窗）。
     *
     * <p>口径：同 product_id 下，非取消单、有门店，按 store 分组求需求量合计 + 单数。
     * JOIN {@code t_md_store} 取门店名。按需求量降序。</p>
     *
     * <p>租户隔离：显式 {@code tenant_id='1001'}；{@code del_flag='0'}。</p>
     *
     * @param productId 产品 FK
     * @return 各门店需求量明细（无则空 List）
     */
    @Select("""
        SELECT dm.store_id AS storeId,
               s.store_name AS storeName,
               SUM(dm.demand_quantity) AS demandQuantity,
               COUNT(*) AS demandCount
        FROM t_warehouse_demand_manage dm
        JOIN t_md_store s ON s.id = dm.store_id
             AND s.del_flag = '0'
             AND s.tenant_id = dm.tenant_id
        WHERE dm.product_id = #{productId}
          AND dm.store_id IS NOT NULL
          AND dm.demand_status <> 'CANCELLED'
          AND dm.del_flag = '0'
          AND dm.tenant_id = '1001'
        GROUP BY dm.store_id, s.store_name
        ORDER BY demandQuantity DESC
        """)
    List<DemandProductStoreDetailVo> selectProductStoreDetail(@Param("productId") Long productId);
}

