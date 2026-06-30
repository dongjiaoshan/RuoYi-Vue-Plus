package org.dromara.djs.warehouse.demand.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.domain.vo.DemandGroupVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandProductStoreDetailVo;
import org.dromara.djs.warehouse.pack.domain.vo.StoreDemandCopiesRowVo;
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
     * <p>{@code todayPigDemand}「今日猪需求头数」口径：白条/猪业态下仅统计【白条整只】+【白条半只】
     * 两类（产品名含「整只」/「半只」），整只按 1 头、半只按 0.5 头折算（半只 = 半头猪），
     * 猪头 / 猪蹄不计入头数。可出 0.5 小数，service 端用 BigDecimal 承接。</p>
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
          COALESCE(SUM(CASE
                  WHEN product_type IN ('white_bar','pig') AND product_name LIKE '%整只%' THEN demand_quantity
                  WHEN product_type IN ('white_bar','pig') AND product_name LIKE '%半只%' THEN demand_quantity * 0.5
                  ELSE 0 END), 0) AS todayPigDemand,
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
     * 某产品 + 某门店「最早一条未完成需求」（需求 C：打包即扣需求）。
     *
     * <p>打包时按此查到的最早未完成需求行，对其 {@code shipped_count} 累加本次打包量（{@link #incrementShipped}），
     * 把需求扣减从「发货确认」前移到「打包」，避免发货再扣造成双扣（{@code ShipmentConfirmedEventListener}
     * 已停用发货扣减）。</p>
     *
     * <p>口径：指定 {@code product_id + store_id}，状态属「已确认且未完成」（{@code demand_status IN
     * ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED')}——COMPLETED 已满足不再扣，DRAFT/SUBMITTED
     * 未确认不计入，CANCELLED/DELETED 排除），且仍有未发货余量
     * （{@code shipped_count < demand_quantity}）。取最早（{@code demand_date ASC, id ASC}）一条 LIMIT 1，
     * 让打包优先填补最早门店需求。无匹配返 null（service 端 log.warn 跳过，不报错）。</p>
     *
     * <p>租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户，与本 mapper 既有聚合 SQL
     * 范式一致）；{@code del_flag='0'}（CHAR(1) 未删）。</p>
     *
     * @param productId 产品 FK（{@code t_warehouse_product_info.id}）
     * @param storeId   门店 FK（{@code t_md_store.id}）
     * @return 最早未完成需求实体（无则 null）
     */
    @Select("""
        SELECT *
        FROM t_warehouse_demand_manage
        WHERE product_id = #{productId}
          AND store_id = #{storeId}
          AND demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED')
          AND COALESCE(shipped_count, 0) < demand_quantity
          AND del_flag = '0'
          AND tenant_id = '1001'
        ORDER BY demand_date ASC, id ASC
        LIMIT 1
        """)
    DemandManage selectOldestUncompletedDemand(@Param("productId") Long productId,
                                               @Param("storeId") Long storeId);

    /**
     * 某产品各门店未发货需求量聚合（DJS-FIX-WMS-PACK 打包录入「门店(N份)」标签条）。
     *
     * <p>按 {@code product_id} 取各门店未发货需求量。<b>每条需求行的未发货量按
     * {@code GREATEST(demand_quantity - shipped_count, 0)} 单独钳 ≥ 0 后再 SUM</b>：同门店有
     * 历史超发行（{@code shipped_count > demand_quantity}，如演示数据需求 5 发货 88）时，该行剩余
     * 为负，若不钳零会把同门店其它「确实未发货」行的剩余抵消成负值、{@code HAVING > 0} 整门店漏掉，
     * 表现为「需求量 0 份 / 无未发货门店需求」（FIX-WMS-PACKDEMAND-001 行42/43）。逐行钳零后，
     * 超发行只贡献 0，不再吃掉同门店其它需求。</p>
     *
     * <p>仅统计「<b>当天</b>（{@code demand_date = today}）+ 已确认及之后」需求（{@code demand_status IN
     * ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')}）——草稿（DRAFT）/ 待确认
     * （SUBMITTED）/ 已取消（CANCELLED）/ 已删除（DELETED）/ 非当天 均不计入打包需求统计。打包台只处理
     * 当天确认需求（与发货月台 {@code demand_date=today} 口径一致，Kevin 2026-06-26）。JOIN {@code t_md_store} 取门店名。</p>
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
               SUM(GREATEST(dm.demand_quantity - COALESCE(dm.shipped_count, 0), 0)) AS copies
        FROM t_warehouse_demand_manage dm
        JOIN t_md_store s ON s.id = dm.store_id
             AND s.del_flag = '0'
             AND s.tenant_id = dm.tenant_id
        WHERE dm.product_id = #{productId}
          AND dm.store_id IS NOT NULL
          AND dm.demand_date = #{today}
          AND dm.demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')
          AND dm.del_flag = '0'
          AND dm.tenant_id = '1001'
        GROUP BY dm.store_id, s.store_name
        HAVING copies > 0
        ORDER BY copies DESC
        """)
    List<StoreDemandCopiesVo> selectStoreDemandCopies(@Param("productId") Long productId,
                                                      @Param("today") LocalDate today);

    /**
     * 批量版「各产品各门店未发货需求份数」（打包录入卡片网格「需求量」批量聚合，去前端 N+1）。
     *
     * <p>口径与 {@link #selectStoreDemandCopies} <b>逐字一致</b>：每条需求行
     * {@code GREATEST(demand_quantity - shipped_count, 0)} 单独钳 ≥ 0 后再 SUM；仅统计「已确认及之后」
     * 需求（{@code demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')}）；
     * JOIN {@code t_md_store} 取门店名；显式 {@code tenant_id='1001'} + {@code del_flag='0'}。
     * 唯一差异：{@code product_id} 由 {@code =} 改 {@code IN(foreach)}，SELECT/GROUP BY 增加
     * {@code dm.product_id} 维度，结果扁平行带 productId 供 service 分组。</p>
     *
     * @param productIds 产品 FK 列表（{@code t_warehouse_product_info.id}）；调用方保证非空
     * @return 各（产品,门店）未发货份数扁平行（按 product_id、copies 降序；无则空 List）
     */
    @Select("""
        <script>
        SELECT dm.product_id AS productId,
               dm.store_id AS storeId,
               s.store_name AS storeName,
               SUM(GREATEST(dm.demand_quantity - COALESCE(dm.shipped_count, 0), 0)) AS copies
        FROM t_warehouse_demand_manage dm
        JOIN t_md_store s ON s.id = dm.store_id
             AND s.del_flag = '0'
             AND s.tenant_id = dm.tenant_id
        WHERE dm.product_id IN
              <foreach collection="productIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
          AND dm.store_id IS NOT NULL
          AND dm.demand_date = #{today}
          AND dm.demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')
          AND dm.del_flag = '0'
          AND dm.tenant_id = '1001'
        GROUP BY dm.product_id, dm.store_id, s.store_name
        HAVING copies > 0
        ORDER BY dm.product_id, copies DESC
        </script>
        """)
    List<StoreDemandCopiesRowVo> selectStoreDemandCopiesBatch(@Param("productIds") Collection<Long> productIds,
                                                              @Param("today") LocalDate today);

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

    /**
     * 门店「当天已确认到店」的果蔬需求产品去重清单（STORE-RETURN-VEG-CANDIDATE 退回操作果蔬 tab 数据源）。
     *
     * <p>口径：指定门店、{@code demand_date=#{day}}、果蔬业态（{@code product_type='vegetable'}）、
     * 已门店收货（{@code received_time IS NOT NULL}，即已发货到店），未取消（{@code demand_status<>'CANCELLED'}），
     * 按 {@code product_id} 去重，取冗余 {@code product_name / product_unit}（同 product 多单时取任一，
     * {@code MAX} 仅为分组兜底，名称冗余通常一致）。</p>
     *
     * <p>⚠️ 不限定 {@code demand_status='CONFIRMED'}：收货后需求会推进到 PARTIAL_SHIPPED/COMPLETED，
     * 若限定 CONFIRMED 会漏掉这些已到店的正常态需求（{@code received_time} 是「已到店」的权威信号）。</p>
     *
     * <p>租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户，与本 mapper
     * 既有聚合 SQL 范式一致）；{@code del_flag='0'}（CHAR(1) 未删）。</p>
     *
     * @param storeId 门店 FK（{@code t_md_store.id}）
     * @param day     需求日期（今日，由 service 按 Asia/Shanghai 传入，不用 DB CURDATE() 避免时区雷）
     * @return 每行 {@code {productId, productName, productUnit}}（无则空 List）
     */
    @Select("""
        SELECT product_id AS productId,
               MAX(product_name) AS productName,
               MAX(product_unit) AS productUnit
        FROM t_warehouse_demand_manage
        WHERE store_id = #{storeId}
          AND demand_date = #{day}
          AND product_type = 'vegetable'
          AND demand_status <> 'CANCELLED'
          AND received_time IS NOT NULL
          AND product_id IS NOT NULL
          AND del_flag = '0'
          AND tenant_id = '1001'
        GROUP BY product_id
        ORDER BY product_id
        """)
    List<Map<String, Object>> selectStoreReceivedVegProducts(@Param("storeId") Long storeId,
                                                             @Param("day") LocalDate day);

    /**
     * 需求汇总分组聚合（0613-10 需求管理列表重做）。
     *
     * <p>按 {@code (demand_date, product_id)} 分组，同日同产品 N 门店需求合并成一行：
     * {@code SUM(demand_quantity)} 需求量 / {@code SUM(material_qty)} 原材料计算量 /
     * {@code COUNT(DISTINCT store_id)} 需求门店数 / 已确认门店数（demand_status IN
     * CONFIRMED/IN_PRODUCTION/PARTIAL_SHIPPED/COMPLETED）/ {@code MAX(confirmer_time)} 最终确认时间。
     * 排除已取消（CANCELLED）/ 已删除（del_flag=1）单；三态 demandStatus + 确认率 confirmRate
     * 在 service 层按 storeCount/confirmedStoreCount 算（避免 SQL 重复 CASE）。</p>
     *
     * <p>产品冗余字段（productName/productSpec/productType/productUnit）取 {@code MAX}
     * 分组兜底（同 product 冗余通常一致）。{@code rawMaterial} 列存的是原材料产品的雪花 ID 字符串，
     * 外层 LEFT JOIN {@code t_warehouse_product_info} 反查成中文产品名（纯数字 ID 才关联，REGEXP 守门；
     * 取不到名 / 历史自由文本 → COALESCE 回退原值），列表直接显示原材料名称而非 ID。
     * 可选过滤：产品名 LIKE / 需求门店 / 需求日期区间。
     * 门店过滤（{@code store_id = #{storeId}}）下推 WHERE：分组前先按门店收敛行集，故汇总行的
     * 需求量 / 门店数随门店变化（仅含该门店对该日该产品的需求）。</p>
     *
     * <p>租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户，与本 mapper
     * 既有聚合 SQL 范式一致）；{@code del_flag='0'}（CHAR(1) 未删）。</p>
     *
     * @param productName 产品名 LIKE 过滤（空则不过滤，由 @if 控制）
     * @param productType 需求产品类型过滤（业态，空则不过滤；同 product_id 组内 product_type 同值，可放 WHERE）
     * @param storeId     需求门店过滤（空则不过滤；下推 WHERE store_id）
     * @param beginDate   需求日期起（空则不过滤）
     * @param endDate     需求日期止（空则不过滤）
     * @return 分组聚合行（按需求日期倒序 + 产品名升序；三态/确认率待 service 回填）
     */
    @Select("""
        <script>
        SELECT g.demandDate          AS demandDate,
               g.productId           AS productId,
               g.productName         AS productName,
               g.productSpec         AS productSpec,
               g.productType         AS productType,
               COALESCE(pm.product_name, g.rawMaterial) AS rawMaterial,
               g.productUnit         AS productUnit,
               g.demandQuantity      AS demandQuantity,
               g.materialQty         AS materialQty,
               g.storeCount          AS storeCount,
               g.confirmedStoreCount AS confirmedStoreCount,
               g.lastConfirmTime     AS lastConfirmTime
        FROM (
            SELECT demand_date              AS demandDate,
                   product_id               AS productId,
                   MAX(product_name)        AS productName,
                   MAX(product_spec)        AS productSpec,
                   MAX(product_type)        AS productType,
                   MAX(raw_material)        AS rawMaterial,
                   MAX(product_unit)        AS productUnit,
                   SUM(demand_quantity)     AS demandQuantity,
                   SUM(COALESCE(material_qty, 0)) AS materialQty,
                   COUNT(DISTINCT store_id) AS storeCount,
                   COUNT(DISTINCT CASE WHEN demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')
                         THEN store_id END) AS confirmedStoreCount,
                   MAX(confirmer_time)      AS lastConfirmTime
            FROM t_warehouse_demand_manage
            WHERE product_id IS NOT NULL
              AND store_id IS NOT NULL
              AND demand_status &lt;&gt; 'CANCELLED'
              AND demand_status &lt;&gt; 'DELETED'
              AND del_flag = '0'
              AND tenant_id = '1001'
              <if test="productName != null and productName != ''">
                AND product_name LIKE CONCAT('%', #{productName}, '%')
              </if>
              <if test="productTypes != null and productTypes.size() > 0">
                AND product_type IN
                <foreach collection="productTypes" item="pt" open="(" separator="," close=")">#{pt}</foreach>
              </if>
              <if test="(productTypes == null or productTypes.size() == 0) and productType != null and productType != ''">
                AND product_type = #{productType}
              </if>
              <if test="storeIds != null and storeIds.size() > 0">
                AND store_id IN
                <foreach collection="storeIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
              </if>
              <if test="(storeIds == null or storeIds.size() == 0) and storeId != null">
                AND store_id = #{storeId}
              </if>
              <if test="beginDate != null">
                AND demand_date &gt;= #{beginDate}
              </if>
              <if test="endDate != null">
                AND demand_date &lt;= #{endDate}
              </if>
            GROUP BY demand_date, product_id
        ) g
        LEFT JOIN t_warehouse_product_info pm
               ON g.rawMaterial REGEXP '^[0-9]+$'
              AND pm.id = CAST(g.rawMaterial AS UNSIGNED)
              AND pm.del_flag = '0'
              AND pm.tenant_id = '1001'
        ORDER BY g.demandDate DESC, g.productName ASC
        </script>
        """)
    List<DemandGroupVo> selectDemandGroupList(@Param("productName") String productName,
                                              @Param("productType") String productType,
                                              @Param("productTypes") List<String> productTypes,
                                              @Param("storeId") Long storeId,
                                              @Param("storeIds") List<Long> storeIds,
                                              @Param("beginDate") LocalDate beginDate,
                                              @Param("endDate") LocalDate endDate);

    /**
     * 在入参 demand id 集合里，筛出「至少指定 1 头未删猪只」的 demand id（0613-11 确认页「是否指定猪只」列）。
     *
     * <p>口径：{@code t_warehouse_demand_pig} 按 demand_id 去重存在未删行即视为已指定。仅查当前页 demand id
     * 集合，避免全表扫；调用方拿到集合后对页内 VO 置 {@code pigAssigned}。</p>
     *
     * <p>租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户，与本 mapper 既有聚合 SQL 范式一致）；
     * {@code del_flag='0'}（CHAR(1) 未删）。</p>
     *
     * @param demandIds 当前页涉及的 demand id 集合（空集调用方需短路，不传空 IN）
     * @return 已指定猪只的 demand id 去重集合（无则空 List）
     */
    @Select("""
        <script>
        SELECT DISTINCT demand_id
        FROM t_warehouse_demand_pig
        WHERE del_flag = '0'
          AND tenant_id = '1001'
          AND demand_id IN
          <foreach collection="demandIds" item="did" open="(" separator="," close=")">#{did}</foreach>
        </script>
        """)
    List<Long> selectDemandIdsWithPig(@Param("demandIds") Collection<Long> demandIds);
}

