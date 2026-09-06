package org.dromara.djs.warehouse.demand.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.domain.vo.DemandGroupVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandProductStoreDetailVo;
import org.dromara.djs.warehouse.demand.domain.vo.StoreDemandDayAggVo;
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
     * 需求量 compare-and-set（V6-R140 需求调整管理）。
     *
     * <p>只有当库里的 {@code demand_quantity} 仍等于调整人看到的旧值时才写入，返回受影响行数。
     * 0 行 = 期间被别人改过，调用方据此拒绝本次调整。</p>
     *
     * <p>为什么不用 {@code updateById}：本表虽有 {@code version} 列，但实体上没挂 {@code @Version}，
     * {@code updateById} 生成的 UPDATE 不带任何版本条件。并发调整时三个请求会全部成功，
     * 而留痕表里三行的 {@code old_quantity} 都记成同一个初始值 —— 拿这张表回放得不出真实序列，
     * 甲方要的正是这张审计表。CAS 把「读到的旧值」写进 WHERE，让后到者失败而不是静默覆盖。</p>
     *
     * @param id        需求单 ID
     * @param oldQty    调整人读到的旧需求量
     * @param newQty    调整后需求量
     * @param updateBy  更新人（原生 SQL 不走 MyBatis-Plus 自动填充，需显式传）
     * @return 受影响行数（1 = 成功，0 = 期间被改过）
     */
    @Update("""
        UPDATE t_warehouse_demand_manage
           SET demand_quantity = #{newQty}, update_by = #{updateBy}, update_time = NOW()
         WHERE id = #{id} AND demand_quantity = #{oldQty} AND del_flag = '0'
        """)
    int compareAndSetQuantity(@Param("id") Long id,
                              @Param("oldQty") BigDecimal oldQty,
                              @Param("newQty") BigDecimal newQty,
                              @Param("updateBy") Long updateBy);

    /**
     * 明日 KPI 横条主表聚合（DJS-FIX-ADMIN-W22-007）：JOIN 产品主数据 belong_type，1 query 出 8 数。
     *
     * <p>「已调配」= 该行所属产品（同 {@code demand_date + product_id} 组）当日<b>已全部确认</b>——组内
     * 全部非 CANCELLED/DELETED、{@code store_id} 非空的行都已脱离 DRAFT/SUBMITTED 待确认态
     * （派生表 {@code fc.fully_confirmed=1} 门，SQL 里按 product_id 分组求得）。与列表
     * {@code selectDemandGroupList} 的「已全部确认」为同一口径；不再按单行状态集合判定。</p>
     *
     * <p>{@code todayPigDemand}「明日猪需求头数」口径：按产品主数据 {@code belong_type='white_bar'}
     * 识别白条carcass，产品名含「半」（半扇 / 白条·半只）按 0.5 头折算、其余（整只白条）按 1 头计。
     * 非白条产品不计入头数——{@code product_type='pig'} 的黑毛猪瘦肉 / 腰子等实为 {@code belong_type='pork'}
     * 切割品，改由 {@code todayPorkDemand} 承接。可出 0.5 小数，service 端用 BigDecimal 承接。</p>
     *
     * <p>{@code todayPigAssigned}「明日白条已配头数」口径：与 {@code todayPigDemand} 同
     * {@code belong_type='white_bar'} + 半只 ×0.5 折算规则，但仅计所属产品当日已全部确认
     * （{@code fc.fully_confirmed=1}）的头数；不再按 demand_pig 子表指定耳号计。</p>
     *
     * <p>{@code todayPorkDemand / todayPorkAssigned}「明日猪肉产品需求 / 已调配条数」口径：
     * 按产品主数据 {@code belong_type='pork'}（字典 djs_belong_type 猪肉产品）计条数；已调配限所属产品当日已全部确认。</p>
     *
     * <p>{@code todayOtherDemand / todayOtherAssigned}「明日其他产品需求 / 已调配条数」口径：
     * {@code product_type IN ('other','gift_box','dry','egg')} 且 {@code belong_type} 非 pork
     * （NULL-safe 排除混进 other 业态的猪肉切割品）；已调配限所属产品当日已全部确认。</p>
     *
     * <p>返 Map 键：{@code todayPigDemand / todayPigAssigned / todayPorkDemand / todayPorkAssigned /
     * todayVegSpeciesDemand / todayVegSpeciesAssigned / todayOtherDemand / todayOtherAssigned}。</p>
     *
     * <p>租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户，与
     * {@code LocationStockMapper} 自定义聚合范式一致）。</p>
     *
     * @param today KPI 统计日期（service 按 Asia/Shanghai 传入；需求 KPI 口径 = 明日，不用 DB CURDATE() 避免时区雷）
     * @return 聚合 Map（单行）
     */
    @Select("""
        SELECT
          COALESCE(SUM(CASE
                  WHEN pi.belong_type = 'white_bar' AND dm.product_name LIKE '%半%' THEN dm.demand_quantity * 0.5
                  WHEN pi.belong_type = 'white_bar' THEN dm.demand_quantity
                  ELSE 0 END), 0) AS todayPigDemand,
          COALESCE(SUM(CASE
                  WHEN pi.belong_type = 'white_bar' AND fc.fully_confirmed = 1 AND dm.product_name LIKE '%半%' THEN dm.demand_quantity * 0.5
                  WHEN pi.belong_type = 'white_bar' AND fc.fully_confirmed = 1 THEN dm.demand_quantity
                  ELSE 0 END), 0) AS todayPigAssigned,
          COUNT(CASE WHEN pi.belong_type = 'pork' THEN 1 END) AS todayPorkDemand,
          COUNT(CASE WHEN pi.belong_type = 'pork'
                AND dm.demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')
                AND fc.fully_confirmed = 1
                THEN 1 END) AS todayPorkAssigned,
          COUNT(DISTINCT CASE WHEN dm.product_type = 'vegetable' THEN dm.product_id END) AS todayVegSpeciesDemand,
          COUNT(DISTINCT CASE WHEN dm.product_type = 'vegetable'
                AND fc.fully_confirmed = 1
                THEN dm.product_id END) AS todayVegSpeciesAssigned,
          COUNT(CASE WHEN dm.product_type IN ('other','gift_box','dry','egg')
                AND (pi.belong_type IS NULL OR pi.belong_type <> 'pork') THEN 1 END) AS todayOtherDemand,
          COUNT(CASE WHEN dm.product_type IN ('other','gift_box','dry','egg')
                AND (pi.belong_type IS NULL OR pi.belong_type <> 'pork')
                AND dm.demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')
                AND fc.fully_confirmed = 1
                THEN 1 END) AS todayOtherAssigned
        FROM t_warehouse_demand_manage dm
        LEFT JOIN t_warehouse_product_info pi
               ON pi.id = dm.product_id AND pi.del_flag = '0' AND pi.tenant_id = '1001'
        LEFT JOIN (
          SELECT product_id,
                 CASE WHEN SUM(CASE WHEN demand_status IN ('DRAFT','SUBMITTED') THEN 1 ELSE 0 END) = 0 THEN 1 ELSE 0 END AS fully_confirmed
          FROM t_warehouse_demand_manage
          WHERE demand_date = #{today} AND del_flag = '0' AND tenant_id = '1001'
            AND store_id IS NOT NULL AND demand_status NOT IN ('CANCELLED','DELETED')
          GROUP BY product_id
        ) fc ON fc.product_id = dm.product_id
        WHERE dm.demand_date = #{today}
          AND dm.del_flag = '0'
          AND dm.tenant_id = '1001'
        """)
    Map<String, Object> selectTodayKpiMainAgg(@Param("today") LocalDate today);

    /**
     * 原子累加 {@code shipped_count}（CROSS-FLOW-003 发货反向更新；并发安全）。
     *
     * <p><b>禁 select+updateById</b>：两个发货事件同秒触发会丢更新。本方法用 DB 端
     * {@code SET shipped_count = COALESCE(shipped_count,0) + #{delta}} 把累加压到一条
     * UPDATE，靠 InnoDB 行锁串行化同一行的累加，不依赖 transition 的 Redisson 锁。</p>
     *
     * <p><b>上界守卫（并发防超打）</b>：WHERE 带
     * {@code COALESCE(shipped_count,0) + #{delta} <= demand_quantity}——两个工人并发给同门店
     * 同产品打包时，各自读到的「剩余份数」应用层校验挡不住 race，累加必须在 DB 端与上界
     * 校验原子完成。守卫未命中（会超上界）→ affected 0，调用方
     * {@code deductDemandOnPack} 据此抛业务异常回滚本次打包。</p>
     *
     * <p>租户隔离：未启全局 MP 拦截器，原生 {@code @Update} 不会自动注入 tenant 过滤，
     * 显式手写 {@code tenant_id}（V1 全 {@code '1001'}，与本 mapper 既有聚合 SQL 范式一致）。
     * {@code del_flag='0'}（CHAR(1) 未删，非数字 0）。</p>
     *
     * @param demandId 需求 ID
     * @param tenantId 租户 ID（V1 传 {@code "1001"}）
     * @param delta    本次发货增量（{@code event.shippedQuantity}）
     * @return 受影响行数（0 = demand 已删 / 不存在 / 累加将超 {@code demand_quantity} 上界，
     *         调用方据此抛业务异常）
     */
    @Update("""
        UPDATE t_warehouse_demand_manage
        SET shipped_count = COALESCE(shipped_count, 0) + #{delta}
        WHERE id = #{demandId}
          AND tenant_id = #{tenantId}
          AND del_flag = '0'
          AND COALESCE(shipped_count, 0) + #{delta} <= demand_quantity
        """)
    int incrementShipped(@Param("demandId") Long demandId,
                         @Param("tenantId") String tenantId,
                         @Param("delta") BigDecimal delta);

    /**
     * 某产品 + 某门店<b>全部</b>未完成需求行（需求日升序、同日 id 升序）—— <b>打包扣需求的唯一查询</b>
     * （份数路径 {@code deductDemandOnPack} 与 KG 路径 {@code planKgDemandRows} 都走它）。
     *
     * <p>口径：指定 {@code product_id + store_id}，需求日 <b>今天及以后</b>（{@code demand_date >= CURDATE()}）、
     * 状态属「已确认且未完成」（{@code demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED')}——
     * COMPLETED 已满足不再扣，DRAFT/SUBMITTED 未确认不计入，CANCELLED/DELETED 排除），且仍有未发货余量
     * （{@code shipped_count < demand_quantity}）。</p>
     *
     * <p><b>为什么返回全部行而不是最早一行</b>：门店同一天分批下单会给同一产品落多行需求，打包台展示的
     * 「剩余需求」又是跨行求和（{@link #selectStoreDemandCopies} 按门店 SUM）。只吃最早那一行的话，工人照
     * 屏幕上的总数打要么被拒、要么多打的量被静默丢弃，剩下的行永远备不齐，整店被出车闸拦死。调用方据此
     * 一次读齐候选行、先校验够不够、够了再逐行扣，「超量」在写任何一行之前就报错，不依赖事务回滚擦半截写入。</p>
     *
     * <p><b>为什么下界是「今天及以后」而不是「只限当天」也不是「不限日期」</b>（甲方 2026-08-06 V6 row27
     * 「只要有需求，就可以进行打包。即今天可以对明天需求进行打包」）：</p>
     * <ul>
     *   <li>只限当天 → 明天的需求今天打不了，正是甲方要改的点。</li>
     *   <li>完全不限日期 → 会扣到<b>已过期</b>的历史未满需求（打包台展示端亦然），既把陈旧数据翻出来给工人看，
     *       也让今天的打包记到上周的单上。故下界钳在今天。</li>
     * </ul>
     *
     * <p>⚠️ <b>展示端 {@link #selectStoreDemandCopies} / {@link #selectStoreDemandCopiesBatch} 与本方法用同一个
     * {@code >= today} 下界，三者必须同时改、不得只改一边</b>（只改展示 → 打包后数字不减；只改扣减 →
     * 扣到看不见的行）。</p>
     *
     * <p>租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户，与本 mapper 既有聚合 SQL
     * 范式一致）；{@code del_flag='0'}（CHAR(1) 未删）。</p>
     *
     * @param productId 产品 FK（{@code t_warehouse_product_info.id}）
     * @param storeId   门店 FK（{@code t_md_store.id}）
     * @return 今天及以后、已确认未完成、仍有余量的需求行（需求日升序、同日 id 升序）；无则空 List
     */
    @Select("""
        SELECT *
        FROM t_warehouse_demand_manage
        WHERE product_id = #{productId}
          AND store_id = #{storeId}
          AND demand_date >= CURDATE()
          AND demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED')
          AND COALESCE(shipped_count, 0) < demand_quantity
          AND del_flag = '0'
          AND tenant_id = '1001'
        ORDER BY demand_date ASC, id ASC
        """)
    List<DemandManage> selectUncompletedDemands(@Param("productId") Long productId,
                                                @Param("storeId") Long storeId);

    /**
     * 白条领用「发货月台」关联需求（row205，邓博 2026-07-05）：该门店该产品今天及以后的需求，优先未完成、其次已完成。
     *
     * <p>V6 row27：下界与门店下拉 {@code selectWhiteBarShipStores}、扣减端
     * {@link #selectUncompletedDemands} 一并从「= 当天」放宽到「&gt;= 当天」——三者必须同步，
     * 否则会出现「下拉里看不到该门店，扣减却扣到它」或反之。</p>
     *
     * <p>用途：白条领用页发货月台出库时把关联需求记入 {@code cut_record.target_demand_id}。与门店下拉
     * {@code selectWhiteBarShipStores} 同状态集（含 COMPLETED —— 门店因有需求才出现在下拉，用户选中即应回填该需求），
     * 但按 {@code product_id} 精确匹配到具体需求单；无匹配需求 → null。仅作引用记录、不做扣减
     * （扣减仍用 {@link #selectUncompletedDemands}，只认未完成）。</p>
     *
     * @return 该门店该产品今天及以后的需求（优先未完成、再按需求日/id 升序取最早；无则 null）
     */
    @Select("""
        SELECT *
        FROM t_warehouse_demand_manage
        WHERE product_id = #{productId}
          AND store_id = #{storeId}
          AND demand_date >= CURDATE()
          AND demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')
          AND del_flag = '0'
          AND tenant_id = '1001'
        ORDER BY (demand_status = 'COMPLETED') ASC, demand_date ASC, id ASC
        LIMIT 1
        """)
    DemandManage selectShipTargetDemand(@Param("productId") Long productId,
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
     * <p>仅统计「需求日 <b>今天及以后</b>（{@code demand_date >= today}）+ 已确认及之后」需求
     * （{@code demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')}）——草稿（DRAFT）/
     * 待确认（SUBMITTED）/ 已取消（CANCELLED）/ 已删除（DELETED）/ 已过期（{@code demand_date < today}）
     * 均不计入打包需求统计。</p>
     *
     * <p>下界从「= today」放宽到「&gt;= today」是甲方 2026-08-06（V6 row27）的口径：「只要有需求，就可以进行
     * 打包，即今天可以对明天需求进行打包」。过期需求仍排除——放开下界会把陈旧未满行翻出来给打包工人看。
     * 扣减端 {@link #selectUncompletedDemands} 用同一下界，两端必须同时改。</p>
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
          AND dm.demand_date >= #{today}
          -- 不含 COMPLETED：V6-R160「缺量发车」会把没发满的需求也推到 COMPLETED 终态，
          -- 这种行 GREATEST(需求-已发,0) 仍 > 0，留着会让打包台继续喊「还需 N 份」，
          -- 而扣减端 selectUncompletedDemands 早就不认 COMPLETED —— 展示与扣减对不上。
          -- 正常发满的 COMPLETED 本来就被 HAVING copies > 0 滤掉，去掉不改变既有行为。
          AND dm.demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED')
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
     * {@code GREATEST(demand_quantity - shipped_count, 0)} 单独钳 ≥ 0 后再 SUM；需求日 {@code >= today}
     * （V6 row27，今天可打明天的需求，过期需求排除）；仅统计「已确认及之后」
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
          AND dm.demand_date >= #{today}
          -- 不含 COMPLETED：V6-R160「缺量发车」会把没发满的需求也推到 COMPLETED 终态，
          -- 这种行 GREATEST(需求-已发,0) 仍 > 0，留着会让打包台继续喊「还需 N 份」，
          -- 而扣减端 selectUncompletedDemands 早就不认 COMPLETED —— 展示与扣减对不上。
          -- 正常发满的 COMPLETED 本来就被 HAVING copies > 0 滤掉，去掉不改变既有行为。
          AND dm.demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED')
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
     * @return 每行 {@code {productId, productName, productUnit, arrivedQuantity}}（arrivedQuantity=当日该产品需求订购份数 SUM，供退回上限；无则空 List）
     */
    @Select("""
        SELECT product_id AS productId,
               MAX(product_name) AS productName,
               MAX(product_unit) AS productUnit,
               COALESCE(SUM(demand_quantity), 0) AS arrivedQuantity
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
     * {@code SUM(demand_quantity)} 需求量 / {@code COUNT(DISTINCT store_id)} 需求门店数 /
     * 已确认门店数（demand_status IN CONFIRMED/IN_PRODUCTION/PARTIAL_SHIPPED/COMPLETED）/
     * {@code MAX(confirmer_time)} 最终确认时间。排除已取消（CANCELLED）/ 已删除（del_flag=1）单；
     * 三态 demandStatus + 确认率 confirmRate 在 service 层按 storeCount/confirmedStoreCount 算
     * （避免 SQL 重复 CASE）。</p>
     *
     * <p>产品冗余字段（productName/productSpec/productType/productUnit）取 {@code MAX}
     * 分组兜底（同 product 冗余通常一致）。业态 {@code belongType} 取需求产品自身
     * {@code product_info.belong_type}（字典 {@code djs_belong_type}）。</p>
     *
     * <p><b>原材料列（rawMaterialName / materialCalcQty / materialUnit）源自需求产品配置的自引用原材料</b>
     * （row127）：需求产品 {@code pi.product_material} 自引用到原材料产品 {@code pm}，取其
     * {@code pm.product_name}（原材料名称）+ {@code pm.product_unit}（原材料单位）。
     * 兼容别名：{@code rawMaterial}=原材料名称、{@code materialQty}=原材料计算量（前端旧列绑定）。
     * <b>原材料计算量 = 需求量 × 单份用量</b>（{@code pi.material_num}，产品配置「原材料计算量」/单份用量，
     * 主要鸡蛋按枚数配比）：{@code SUM(dm.demand_quantity) * MAX(pi.material_num)}。
     * <b>单位一致兜底：产品单位与原材料单位一致（忽略大小写/空白）、已配关联原材料且未配 material_num 配比时，
     * 计算量直接 = 需求量</b>（如 大米10斤 袋→袋；显式配了配比的仍走配比分支，不被单位一致劫持）。
     * <b>kg 兜底：产品单位为 kg（含公斤）且已配关联原材料（{@code product_material} 非空）时，
     * 原材料按 1:1 kg 计——计算量直接 = 需求量（{@code SUM(dm.demand_quantity)}），不依赖 material_num</b>
     * （kg 产品无枚数配比、material_num 恒 NULL；如筒子骨散装 kg 需求 6 → 计算量 6）。
     * 非 kg 且产品未配 material_num（果蔬 / 部分猪肉）时计算量为 NULL（前端显空，不误显 0）；未配 product_material 时原材料名/单位为 NULL。
     * 不再读需求行冗余 {@code dm.raw_material / dm.material_qty}（历史留空列，恒 NULL / 0）。</p>
     *
     * <p><b>下单时间 / 下单人（row181）</b>：一行是「同日同产品被 N 家门店下的单」的合并，两者在行上不是单值，
     * 故取组内<b>最早一单</b>——{@code orderTime = MIN(dm.create_time)}；{@code ordererName} =
     * 最早那一单的 {@code sys_user.nick_name}，组内下单人多于一个时拼成 {@code 张三 等 N 人}
     * （N = {@code COUNT(DISTINCT dm.create_by)}），与 mp 门店需求日卡
     * （{@link #selectStoreDemandDayPage} 的 {@code ordererName}）同一种形态。
     * 昵称用<b>相关子查询取一行</b>而不是 {@code GROUP_CONCAT + SUBSTRING_INDEX}：昵称含逗号会被切断，
     * 且 {@code group_concat_max_len} 有截断风险。子查询里重复了门店过滤条件 —— 它是唯一会改变
     * <b>组内成员</b>的筛选（产品名 / 业态按产品整体命中，日期区间与分组键同维），漏了会出现
     * 「按门店筛之后，下单人还是别家店的人」。</p>
     *
     * <p>可选过滤：产品名 LIKE / 需求门店 / 需求日期区间。
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
     * @return 分组聚合行（按<b>需求确认率升序</b>排，同率再按需求日期倒序 + 产品名升序；三态/确认率待 service 回填）
     */
    @Select("""
        <script>
        SELECT dm.demand_date              AS demandDate,
               dm.product_id               AS productId,
               MAX(dm.product_name)        AS productName,
               MAX(dm.product_spec)        AS productSpec,
               MAX(dm.product_unit)        AS productUnit,
               MAX(dm.product_type)        AS productType,
               MAX(pi.belong_type)         AS belongType,
               MAX(pm.product_name)        AS rawMaterialName,
               MAX(pm.product_unit)        AS materialUnit,
               SUM(dm.demand_quantity)     AS demandQuantity,
               CASE WHEN MAX(pi.product_material) IS NOT NULL AND MAX(pi.material_num) IS NULL
                         AND LOWER(TRIM(MAX(dm.product_unit))) = LOWER(TRIM(MAX(pm.product_unit)))
                         THEN SUM(dm.demand_quantity)
                    WHEN LOWER(TRIM(MAX(dm.product_unit))) IN ('kg','公斤') AND MAX(pi.product_material) IS NOT NULL
                         THEN SUM(dm.demand_quantity)
                    WHEN MAX(pi.material_num) IS NULL THEN NULL
                    ELSE SUM(dm.demand_quantity) * MAX(pi.material_num) END AS materialCalcQty,
               COUNT(DISTINCT dm.store_id) AS storeCount,
               COUNT(DISTINCT CASE WHEN dm.demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')
                     THEN dm.store_id END) AS confirmedStoreCount,
               COUNT(*)                    AS demandCount,
               COUNT(CASE WHEN dm.demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')
                     THEN 1 END)           AS confirmedDemandCount,
               MAX(dm.confirmer_time)      AS lastConfirmTime,
               MIN(dm.create_time)         AS orderTime,
               CONCAT(
                 IFNULL((SELECT u.nick_name
                           FROM t_warehouse_demand_manage d2
                           LEFT JOIN sys_user u ON u.user_id = d2.create_by AND u.del_flag = '0'
                          WHERE d2.demand_date = dm.demand_date
                            AND d2.product_id = dm.product_id
                            AND d2.store_id IS NOT NULL
                            AND d2.demand_status NOT IN ('CANCELLED','DELETED')
                            AND d2.del_flag = '0'
                            AND d2.tenant_id = '1001'
                            <if test="storeIds != null and storeIds.size() > 0">
                              AND d2.store_id IN
                              <foreach collection="storeIds" item="sid2" open="(" separator="," close=")">#{sid2}</foreach>
                            </if>
                            <if test="(storeIds == null or storeIds.size() == 0) and storeId != null">
                              AND d2.store_id = #{storeId}
                            </if>
                          ORDER BY d2.create_time ASC, d2.id ASC
                          LIMIT 1), ''),
                 CASE WHEN COUNT(DISTINCT dm.create_by) > 1
                      THEN CONCAT(' 等 ', COUNT(DISTINCT dm.create_by), ' 人') ELSE '' END
               )                           AS ordererName
        FROM t_warehouse_demand_manage dm
        LEFT JOIN t_warehouse_product_info pi
               ON pi.id = dm.product_id AND pi.del_flag = '0' AND pi.tenant_id = '1001'
        LEFT JOIN t_warehouse_product_info pm
               ON pm.id = pi.product_material AND pm.del_flag = '0' AND pm.tenant_id = '1001'
        WHERE dm.product_id IS NOT NULL
          AND dm.store_id IS NOT NULL
          AND dm.demand_status &lt;&gt; 'CANCELLED'
          AND dm.demand_status &lt;&gt; 'DELETED'
          AND dm.del_flag = '0'
          AND dm.tenant_id = '1001'
          <if test="productName != null and productName != ''">
            AND dm.product_name LIKE CONCAT('%', #{productName}, '%')
          </if>
          <!-- 「需求产品类型」筛选统一按产品配置「产品类别」(产品 belong_type)，非内部业态 product_type；
               param 仍叫 productTypes/productType，但承载 djs_belong_type 字典值（pork/vegetable/...）。 -->
          <if test="productTypes != null and productTypes.size() > 0">
            AND pi.belong_type IN
            <foreach collection="productTypes" item="pt" open="(" separator="," close=")">#{pt}</foreach>
          </if>
          <if test="(productTypes == null or productTypes.size() == 0) and productType != null and productType != ''">
            AND pi.belong_type = #{productType}
          </if>
          <if test="storeIds != null and storeIds.size() > 0">
            AND dm.store_id IN
            <foreach collection="storeIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
          </if>
          <if test="(storeIds == null or storeIds.size() == 0) and storeId != null">
            AND dm.store_id = #{storeId}
          </if>
          <if test="beginDate != null">
            AND dm.demand_date &gt;= #{beginDate}
          </if>
          <if test="endDate != null">
            AND dm.demand_date &lt;= #{endDate}
          </if>
        GROUP BY dm.demand_date, dm.product_id
        <!-- 默认排序 = 需求确认率升序（row32 甲方：从小到大，最没被确认的排最前）。
             确认率口径与 service 层 confirmRate 逐字一致：组内已确认需求单数 / 组内需求单数
             （row166 按【需求单】而非门店）。必须排在 SQL：列表是「先聚合全量、后内存分页」，
             前端只能对当前页排序，第 2 页的 0% 会掉在第 1 页的 100% 后面。
             NULL / 分母 0 的行（GROUP BY 每组至少 1 行，理论不出现；仅防御未来口径变化）
             由 COALESCE 兜成 0 —— 与「一条都没确认」同档排最前：宁可让「无从判断确认情况」的行
             先被看到，也不要沉到末页。
             同确认率保留改造前次序作 tie-breaker：需求日期倒序 + 产品名升序。 -->
        ORDER BY COALESCE(COUNT(CASE WHEN dm.demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')
                                     THEN 1 END) / NULLIF(COUNT(*), 0), 0) ASC,
                 dm.demand_date DESC,
                 MAX(dm.product_name) ASC
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
     * 门店需求「按天聚合」分页（mp 门店需求卡 row66）。
     *
     * <p>一行 = 一个 {@code (demand_date, store_id)}。<b>统计口径统一排除门店态 DELETED 的行</b>
     * （{@code demand_status NOT IN ('DELETED','CANCELLED','DRAFT')} + {@code del_flag='0'}）——所以某天全部行
     * 都被删/取消时该天整条不出现，与契约 §1.2 一致。</p>
     *
     * <p>三个派生列的算法：</p>
     * <ul>
     *   <li>{@code arrivedCount / shippedCount / confirmedCount} 逐行按
     *       {@link org.dromara.djs.warehouse.demand.core.StoreDemandStatusMapping} 的映射表分桶计数，
     *       日状态阶梯与确认率由 service 用这三个数 + {@code totalCount} 派生（不在 SQL 里做，便于单测）。</li>
     *   <li>{@code ordererName} 相关子查询取当天<b>最早一条</b>需求的下单人昵称
     *       （{@code create_time ASC, id ASC LIMIT 1}）——不用 GROUP_CONCAT+SUBSTRING_INDEX，
     *       昵称含逗号会被切断，且 {@code group_concat_max_len} 有截断风险。</li>
     *   <li>{@code damagedCount} 相关子查询按当天需求 id 集合统计
     *       {@code t_warehouse_product_production.is_damaged=1} 的件数（与 store 单条口径
     *       {@code countDamagedByDemand} 同条件，只是范围从单条扩到当天）。</li>
     * </ul>
     *
     * <p>分页在 SQL 层（MP {@code PaginationInnerInterceptor} 对 GROUP BY 查询自动包
     * {@code SELECT COUNT(*) FROM (...) TOTAL} 求总数），不做「聚合全量再内存切片」。</p>
     *
     * <p>租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户，与本 mapper 既有聚合 SQL 范式一致）。</p>
     *
     * @param page      分页对象
     * @param storeId   门店过滤（null 不过滤——mp 端恒传当前门店）
     * @param beginDate 需求日期起（null 不过滤）
     * @param endDate   需求日期止（null 不过滤）
     * @return 按天聚合行（需求日期倒序）
     */
    @Select("""
        <script>
        SELECT DATE_FORMAT(dm.demand_date, '%Y-%m-%d')                    AS demandDate,
               dm.store_id                                                AS storeId,
               COUNT(DISTINCT dm.product_id)                              AS categoryCount,
               COUNT(*)                                                   AS totalCount,
               SUM(CASE WHEN dm.demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')
                         AND dm.received_time IS NOT NULL THEN 1 ELSE 0 END)  AS arrivedCount,
               SUM(CASE WHEN dm.demand_status IN ('PARTIAL_SHIPPED','COMPLETED')
                         AND dm.received_time IS NULL THEN 1 ELSE 0 END)      AS shippedCount,
               SUM(CASE WHEN dm.demand_status IN ('CONFIRMED','IN_PRODUCTION')
                         AND dm.received_time IS NULL THEN 1 ELSE 0 END)      AS confirmedCount,
               DATE_FORMAT(MAX(GREATEST(dm.create_time, IFNULL(dm.update_time, dm.create_time))),
                           '%Y-%m-%d %H:%i')                              AS lastOrderTime,
               CONCAT(
                 IFNULL((SELECT u.nick_name
                           FROM t_warehouse_demand_manage d2
                           LEFT JOIN sys_user u ON u.user_id = d2.create_by AND u.del_flag = '0'
                          WHERE d2.store_id = dm.store_id
                            AND d2.demand_date = dm.demand_date
                            AND d2.demand_status NOT IN ('DELETED','CANCELLED','DRAFT')
                            AND d2.del_flag = '0'
                            AND d2.tenant_id = '1001'
                          ORDER BY d2.create_time ASC, d2.id ASC
                          LIMIT 1), ''),
                 CASE WHEN COUNT(DISTINCT dm.create_by) > 1
                      THEN CONCAT(' 等 ', COUNT(DISTINCT dm.create_by), ' 人') ELSE '' END
               )                                                          AS ordererName,
               (SELECT COUNT(*)
                  FROM t_warehouse_product_production pp
                  JOIN t_warehouse_demand_manage d3
                    ON d3.id = pp.demand_id
                   AND d3.store_id = dm.store_id
                   AND d3.demand_date = dm.demand_date
                   AND d3.demand_status NOT IN ('DELETED','CANCELLED','DRAFT')
                   AND d3.del_flag = '0'
                   AND d3.tenant_id = '1001'
                 WHERE pp.is_damaged = 1
                   AND pp.del_flag = '0'
                   AND pp.tenant_id = '1001')                             AS damagedCount
        FROM t_warehouse_demand_manage dm
        WHERE dm.store_id IS NOT NULL
          AND dm.demand_status NOT IN ('DELETED','CANCELLED','DRAFT')
          AND dm.del_flag = '0'
          AND dm.tenant_id = '1001'
          <if test="storeId != null">
            AND dm.store_id = #{storeId}
          </if>
          <if test="beginDate != null">
            AND dm.demand_date &gt;= #{beginDate}
          </if>
          <if test="endDate != null">
            AND dm.demand_date &lt;= #{endDate}
          </if>
        GROUP BY dm.demand_date, dm.store_id
        ORDER BY dm.demand_date DESC, dm.store_id ASC
        </script>
        """)
    Page<StoreDemandDayAggVo> selectStoreDemandDayPage(IPage<StoreDemandDayAggVo> page,
                                                       @Param("storeId") Long storeId,
                                                       @Param("beginDate") LocalDate beginDate,
                                                       @Param("endDate") LocalDate endDate);

    /**
     * 本店各产品「最近一次下单时间」（mp 下单目录 row68 的非果蔬排序键）。
     *
     * <p>口径：指定门店 + 指定产品集合，取 {@code MAX(create_time)} 精确到分，
     * <b>排除门店态「已删除」的行</b>（{@code demand_status IN ('DELETED','CANCELLED')} + {@code del_flag='1'}），
     * 与 {@link #selectStoreDemandDayPage} 的统计口径**同一套**。</p>
     *
     * <p>为什么不像早先那样「取消的也算下过」：{@code CANCELLED} 与 {@code DELETED} 在门店视角
     * 是同一个状态（都叫「已删除」，见 {@code StoreDemandStatusMapping}）。只排 {@code del_flag=1}
     * 会变成「mp 里删掉的不算历史、后台取消的算」——同样一件事一半算一半不算，属 CLAUDE.md §0 禁止的
     * 「两边兼容」。这里二选一取「站得住的单才算历史」：排序反映的是这家店真正在订的东西。
     * 放弃的是「取消行也体现过下单意图」这层信息，接受。</p>
     *
     * <p>租户隔离：显式 {@code tenant_id='1001'}（V1 单租户）。</p>
     *
     * @param storeId    门店 FK
     * @param productIds 产品 FK 集合（调用方保证非空）
     * @return 每行 {@code {productId, lastOrderTime}}；从未下过单的产品不出现在结果中
     */
    @Select("""
        <script>
        SELECT product_id                                        AS productId,
               DATE_FORMAT(MAX(create_time), '%Y-%m-%d %H:%i')   AS lastOrderTime
        FROM t_warehouse_demand_manage
        WHERE store_id = #{storeId}
          AND product_id IS NOT NULL
          AND del_flag = '0'
          AND demand_status NOT IN ('DELETED','CANCELLED','DRAFT')
          AND tenant_id = '1001'
          AND product_id IN
          <foreach collection="productIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
        GROUP BY product_id
        </script>
        """)
    List<Map<String, Object>> selectLastOrderTimeByStore(@Param("storeId") Long storeId,
                                                         @Param("productIds") Collection<Long> productIds);


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

    /**
     * 该门店是否「不可再下单」（不存在 / 已删除 / 已终止合作），返 &gt;0 表示不可用。
     *
     * <p>给编辑路径的门店闸用。warehouse 模块不反向依赖 common.store 的门店服务，
     * 直接按 {@code t_md_store.business_status} 判：{@code '1'} = 已终止合作
     * （与 {@code StoreUserRelationServiceImpl.BUSINESS_STATUS_TERMINATED} 同一常量口径）。</p>
     *
     * @param storeId 门店主键
     * @return 不可用计数（0 = 门店正常）
     */
    @Select("""
        SELECT CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
          FROM t_md_store
         WHERE id = #{storeId}
           AND del_flag = '0'
           AND (business_status IS NULL OR business_status <> '1')
        """)
    Integer countTerminatedStore(@Param("storeId") Long storeId);
}

