package org.dromara.djs.warehouse.pack.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionGroupVo;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionVo;
import org.dromara.djs.warehouse.pack.domain.vo.WhiteBarShipmentVo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 发货产品生产记录 Mapper（WMS-PACK-001 + WMS-SHIP-001 合并版）。
 *
 * <p>D10 P0 hotfix：原 SHIP 包 {@code org.dromara.djs.warehouse.product.mapper.ProductProductionMapper}
 * 与本 Mapper 同短名引起 MyBatis ClassPathMapperScanner bean name 冲突，admin 拒启动。
 * 合并方案 a — 本 Mapper（PACK 包）为权威，SHIP 端方法 {@link #markDeliveryChecked} 合并进来；
 * SHIP service 改 import 本路径。</p>
 *
 * @author djs
 * @since WMS-PACK-001
 */
public interface ProductProductionMapper extends BaseMapperPlus<ProductProduction, ProductProductionVo> {

    /**
     * 查询今日（按 {@code produce_no LIKE 'yyMMdd + prefix + %'}）已用最大序号。
     *
     * <p>同 burn/cut inline 范式（D8 / D9 PigBurnRecordMapper / PigCutRecordMapper）；
     * 并发安全 — service 层在事务里串行调用 + UNIQUE (tenant_id, produce_no, del_unique) 兜底；
     * 极小概率抢同一序号场景由 SQLIntegrityConstraintViolationException 触发事务回滚 + mp 端重试。</p>
     *
     * @param prefix 6 位前缀 {@code yyMMdd + 单字母前缀}，例如 {@code 260528G}（果蔬）
     */
    @Select("SELECT MAX(produce_no) FROM t_warehouse_product_production "
        + "WHERE produce_no LIKE CONCAT(#{prefix}, '%') AND del_flag = '0'")
    String selectMaxProduceNoByPrefix(@Param("prefix") String prefix);

    /**
     * 批量统计「今天已打包份数」（每条 product_production = 一份）：按 product_id 分组 COUNT 今天的生产记录。
     *
     * <p>打包台用——某成品今天已打包份数 ≥ 门店需求份数 → 卡片标「打包完成」、不可再选（避免超量打包）。</p>
     *
     * @param productIds 目标成品 id 列表（非空）
     * @return 行 {@code {productId, cnt}}；无今天记录的成品不在结果里
     */
    @Select("<script>"
        + "SELECT product_id AS productId, COUNT(*) AS cnt "
        + "  FROM t_warehouse_product_production "
        + " WHERE del_flag = '0' AND tenant_id = '1001' AND DATE(produce_date) = CURDATE() "
        + "   AND product_id IN <foreach collection='productIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
        + " GROUP BY product_id"
        + "</script>")
    List<Map<String, Object>> selectTodayPackedCount(@Param("productIds") List<Long> productIds);

    /**
     * 批量统计「今天按门店已打包份数」（每条 product_production = 一份）：按 {@code (product_id, store_id)}
     * 分组 COUNT 今天的生产记录。
     *
     * <p>FIX-WMS-PACKDEMAND-001 行52：同产品多门店需求时，「打包完成」必须<b>按门店分别判</b>——
     * 把 3 份全打给同一门店，不能让另一个门店的需求也算完成。本方法给出「productId → 各门店今天已打包份数」，
     * service 端与各门店未发货需求逐店比对，全部门店都打满才算该产品完成。</p>
     *
     * <p>只统计绑定了门店（{@code store_id IS NOT NULL}）的生产记录；未绑门店的打包（直接入库不指定门店）
     * 不计入任何门店的完成度。租户隔离 V1 单租户显式 {@code tenant_id='1001'}。</p>
     *
     * @param productIds 目标成品 id 列表（非空）
     * @return 行 {@code {productId, storeId, cnt}}；无今天记录的组合不在结果里
     */
    @Select("<script>"
        + "SELECT product_id AS productId, store_id AS storeId, COUNT(*) AS cnt "
        + "  FROM t_warehouse_product_production "
        + " WHERE del_flag = '0' AND tenant_id = '1001' AND DATE(produce_date) = CURDATE() "
        + "   AND store_id IS NOT NULL "
        + "   AND product_id IN <foreach collection='productIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
        + " GROUP BY product_id, store_id"
        + "</script>")
    List<Map<String, Object>> selectTodayPackedCountByStore(@Param("productIds") List<Long> productIds);

    /**
     * 批量统计「已完成打包累计重量」（按 product_id 分组 SUM {@code product_weight}）。
     *
     * <p>打包台用——admin 果蔬打包页按「目标成品已打包总重量」算剩余可打包量。数据源同
     * {@link #selectTodayPackedCount}（{@code t_warehouse_product_production} 打包记录），
     * 但聚合 {@code SUM(product_weight)} 而非 COUNT。已完成打包 = 全部未软删生产记录
     * （口径同主列表 {@code selectProductionGroupList} 的 {@code SUM(pp.product_weight)}，不限当日）。</p>
     *
     * <p>租户隔离 V1 单租户显式 {@code tenant_id='1001'}（与本 Mapper 其他聚合 SQL 一致）。</p>
     *
     * @param productIds 目标成品 id 列表（非空）
     * @return 行 {@code {productId, weight}}；无生产记录的成品不在结果里
     */
    @Select("<script>"
        + "SELECT product_id AS productId, COALESCE(SUM(product_weight), 0) AS weight "
        + "  FROM t_warehouse_product_production "
        + " WHERE del_flag = '0' AND tenant_id = '1001' "
        + "   AND product_id IN <foreach collection='productIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
        + " GROUP BY product_id"
        + "</script>")
    List<Map<String, Object>> selectPackedWeight(@Param("productIds") List<Long> productIds);

    /**
     * 批量将一组 product_production 行从 {@code is_delivery_check=0} 推进到 1，
     * 同时写入 {@code delivery_check_time} 与 {@code demand_id}（WMS-SHIP-001 ShipmentService 调用）。
     *
     * <p>发货采用"按可用库存匹配 + 确认时绑定 demand"语义（SHIP-DEMANDID-001）：可用库存
     * {@code demand_id} 原本为 NULL（pack 链不分配），工人发货确认时由本次 demand 绑定，
     * 一次原子 UPDATE 完成"标清点 + 绑 demand"，让 {@code ShipTraceEventListener} 之后能按
     * {@code demand_id} 查到本次发货的 production。</p>
     *
     * <p>WHERE 子句额外加 {@code is_delivery_check=0} 实现乐观锁：</p>
     * <ul>
     *   <li>若返回 affectedRows &lt; ids.size() → 有并发清点冲突 → service 层抛 ServiceException</li>
     *   <li>tenant_id 由 MP 拦截器在 final SQL 阶段注入</li>
     * </ul>
     *
     * @param ids       发货产品 id 集合
     * @param checkTime 清点时间
     * @param demandId  本次发货归属的需求 ID（回写绑定）
     * @return 实际更新行数
     */
    @Update({
        "<script>",
        "UPDATE t_warehouse_product_production",
        "   SET is_delivery_check = 1,",
        "       delivery_check_time = #{checkTime},",
        "       demand_id = #{demandId}",
        " WHERE id IN",
        "   <foreach collection='ids' item='id' separator=',' open='(' close=')'>#{id}</foreach>",
        "   AND is_delivery_check = 0",
        "   AND del_flag = '0'",
        "</script>"
    })
    int markDeliveryChecked(@Param("ids") List<Long> ids,
                            @Param("checkTime") Date checkTime,
                            @Param("demandId") Long demandId);

    /**
     * 白条领用「发货月台」门店下拉数据（row153）：当天有<b>已确认白条需求</b>的门店 + 各门店白条需求数。
     *
     * <p>白条领用页出库位置=发货月台时，门店下拉不再全量列 4 店，只列「当天该门店确有白条需求」的门店，
     * 并在门店名后带上该店当天白条需求份数。口径与打包台需求门店标签一致：需求业态
     * {@code dm.product_type='white_bar'}、需求日 {@code demand_date=CURDATE()}、状态已确认及之后
     * （{@code demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')}）；
     * 草稿 / 待确认 / 已取消 / 已删除均不计。JOIN {@code t_md_store} 取门店名。</p>
     *
     * <p>{@code demandQty = SUM(GREATEST(demand_quantity - shipped_count, 0))} 该店当天白条<b>剩余未发</b>份数。
     * 每发一个白条到发货月台即 {@code shipped_count+1}（submitWhiteBarOut → deductDemandOnPack），故 demandQty
     * 随发递减；{@code HAVING > 0} 过滤掉已发满/全零门店 —— 需求发满即从下拉消失（= 不允许继续往该门店发货）。
     * {@code storeId} 用 {@code CAST(... AS CHAR)} 返字符串防雪花门店 id 前端 JS Number 精度截断。
     * 租户隔离 V1 单租户显式 {@code tenant_id='1001'}（与本 mapper 其他原生 SQL 一致）；{@code del_flag='0'}。</p>
     *
     * @return 行 {@code {storeId(字符串), storeName, demandQty}}（按 demandQty 降序）；当天无剩余白条需求 → 空 List
     */
    @Select("SELECT CAST(dm.store_id AS CHAR) AS storeId, "
        + "       s.store_name AS storeName, "
        + "       SUM(GREATEST(dm.demand_quantity - COALESCE(dm.shipped_count, 0), 0)) AS demandQty "
        + "  FROM t_warehouse_demand_manage dm "
        + "  JOIN t_md_store s ON s.id = dm.store_id "
        + "       AND s.del_flag = '0' "
        + "       AND s.tenant_id = dm.tenant_id "
        + " WHERE dm.del_flag = '0' "
        + "   AND dm.tenant_id = '1001' "
        + "   AND dm.product_type = 'white_bar' "
        + "   AND dm.store_id IS NOT NULL "
        + "   AND dm.demand_date = CURDATE() "
        + "   AND dm.demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED') "
        + " GROUP BY dm.store_id, s.store_name "
        + "HAVING demandQty > 0 "
        + "ORDER BY demandQty DESC")
    List<Map<String, Object>> selectWhiteBarShipStores();

    /**
     * 产品维度聚合查询（主列表「产品生产」概览）。
     *
     * <p>按 {@code (product_id, DATE(produce_date))} 分组：同一产品同一天的 N 件生产记录合并成一行。
     * 范式同 {@link org.dromara.djs.warehouse.demand.mapper.DemandManageMapper#selectDemandGroupList}
     * （{@code <script>} + {@code <if>} 可选过滤 + SUM/COUNT + MAX 冗余字段兜底 + 显式 tenant_id）。</p>
     *
     * <p>产品品类 {@code belong_type} 只在 {@code t_warehouse_product_info} 维度，经 product_id FK
     * LEFT JOIN 取（组内同 product_id 必同值，放 MAX 兜底；按品类过滤时下推 WHERE pi.belong_type）。
     * 少数历史行 product_id 找不到 product_info → LEFT JOIN 后 belongType 为 NULL，前端 dict-tag 容错。</p>
     *
     * <p>「生产量」{@code produceQty} = 该组生产记录条数 {@code COUNT(*)}（一次打包/出库确认 = 一份，
     * 全业态统一按条数计量，与子页「产品明细」逐件条数一致；不再按 {@code SUM(product_weight)} 重量算，
     * 避免份计量产品重量取整后显 0）。</p>
     *
     * <p>白条业态（{@code belong_type='white_bar'}）在本主列表隐藏（WHERE {@code pi.belong_type &lt;&gt; 'white_bar'}，
     * NULL-safe 保留历史无品类行）：白条是燎毛过程态、经白条领用页管理，不进产品生产概览；详情/其它页照常。</p>
     *
     * <p>租户隔离：未启全局 MP 拦截器，显式 {@code pp.tenant_id='1001'}（V1 单租户，与本模块既有
     * 聚合 SQL 范式一致）；{@code pp.del_flag='0'}（CHAR(1) 未删）。</p>
     *
     * <p>「需求门店数」{@code storeDemandCount} = 该组（同产品同生产日）生产记录去向的去重门店家数
     * {@code COUNT(DISTINCT pp.store_id)}（与子页「产品明细」每行「需求门店」一致）。未绑门店（{@code store_id IS NULL}，
     * 如发送位置=礼盒的组件）不计入。无门店 → 0。</p>
     *
     * <p>「原材料消耗量」{@code materialConsume} = 该组 {@code SUM(material_consume)}（同产品当日累计消耗的
     * 来源原材料重量；无配料行 NULL 跳过）。「原材料名称」{@code materialName} 与「原材料单位」{@code materialUnit}
     * = {@code material_id} 经第二个 LEFT JOIN（{@code pmi}）取 {@code product_info.product_name / product_unit}
     * 的 {@code MAX} 兜底（组内通常同一原料）；无配料则 NULL，前端展示 -。</p>
     *
     * <p>「损坏量」{@code damageCount} = 该组已标损坏件数 {@code SUM(is_damaged)}（DENGBO-DAMAGE-001，
     * row50「件数」后展示，&gt;0 红色）。{@code hasDamage} 作用于组维度，经 HAVING 过滤：1=组内有损坏
     * （{@code SUM(is_damaged)>0}）/ 0=组内无损坏；空则不过滤。</p>
     *
     * @param produceNo   生产编号 LIKE 过滤（空则不过滤）
     * @param productName 产品名称 LIKE 过滤（空则不过滤）
     * @param belongType  产品品类过滤（字典 djs_belong_type，空则不过滤；下推 product_info WHERE，belongTypes 为空时 fallback）
     * @param belongTypes 产品品类多选过滤（非空时按 IN 下推 product_info.belong_type，优先于单值 belongType）
     * @param productType 产品类型过滤（空则不过滤；同 product_id 组内同值，放 WHERE）
     * @param beginDate   生产日期起（空则不过滤）
     * @param endDate     生产日期止（空则不过滤）
     * @param hasDamage   是否存在损坏（组维度 HAVING：1=有 / 0=无；空则不过滤）
     * @return 分组聚合行（按生产日期倒序 + 组内最大生产时间倒序）
     */
    @Select({
        "<script>",
        "SELECT pp.product_id          AS productId,",
        "       DATE(pp.produce_date)  AS produceDate,",
        "       MAX(pp.product_name)   AS productName,",
        "       MAX(COALESCE(pi.product_unit, pp.product_unit)) AS productUnit,",
        "       MAX(pp.product_spec)   AS productSpec,",
        "       MAX(pi.belong_type)    AS belongType,",
        "       MAX(pp.product_type)   AS productType,",
        "       COALESCE(MAX(dm.demand_qty), 0) AS demandQty,",
        // 生产量按「产品自身计量单位」：kg 计量取当日重量合计，份 / 盒 / 枚等计数单位取记录条数
        //（一次打包确认 = 一份）。需求量本就是按该单位下的单，两者同量纲，需求满足率才有意义。
        "       CASE WHEN LOWER(TRIM(MAX(COALESCE(pi.product_unit, pp.product_unit)))) IN ('kg', '公斤')",
        "            THEN COALESCE(SUM(pp.product_weight), 0)",
        "            ELSE COUNT(*) END AS produceQty,",
        "       ROUND(COALESCE(MAX(dm.demand_qty), 0) / NULLIF(",
        "           CASE WHEN LOWER(TRIM(MAX(COALESCE(pi.product_unit, pp.product_unit)))) IN ('kg', '公斤')",
        "                THEN COALESCE(SUM(pp.product_weight), 0)",
        "                ELSE COUNT(*) END, 0) * 100, 2) AS fulfillmentRate,",
        "       COUNT(*)               AS itemCount,",
        "       SUM(pp.material_consume) AS materialConsume,",
        "       MAX(pmi.product_name)  AS materialName,",
        "       MAX(pmi.product_unit)  AS materialUnit,",
        "       COUNT(DISTINCT pp.store_id) AS storeDemandCount,",
        "       COALESCE(SUM(pp.is_damaged), 0) AS damageCount",
        "  FROM t_warehouse_product_production pp",
        "  LEFT JOIN t_warehouse_product_info pi",
        "         ON pi.id = pp.product_id",
        "        AND pi.del_flag = '0'",
        "        AND pi.tenant_id = pp.tenant_id",
        "  LEFT JOIN t_warehouse_product_info pmi",
        "         ON pmi.id = pp.material_id",
        "        AND pmi.del_flag = '0'",
        "        AND pmi.tenant_id = pp.tenant_id",
        "  LEFT JOIN (",
        "       SELECT tenant_id, product_id, DATE(demand_date) AS demand_day,",
        "              SUM(demand_quantity) AS demand_qty",
        "         FROM t_warehouse_demand_manage",
        "        WHERE del_flag = '0'",
        "          AND demand_status NOT IN ('CANCELLED', 'DELETED')",
        "        GROUP BY tenant_id, product_id, DATE(demand_date)",
        "  ) dm",
        "         ON dm.tenant_id = pp.tenant_id",
        "        AND dm.product_id = pp.product_id",
        "        AND dm.demand_day = DATE(pp.produce_date)",
        " WHERE pp.del_flag = '0'",
        "   AND pp.tenant_id = '1001'",
        "   AND (pi.belong_type IS NULL OR pi.belong_type &lt;&gt; 'white_bar')",
        "   <if test='produceNo != null and produceNo != \"\"'>",
        "     AND pp.produce_no LIKE CONCAT('%', #{produceNo}, '%')",
        "   </if>",
        "   <if test='productName != null and productName != \"\"'>",
        "     AND pp.product_name LIKE CONCAT('%', #{productName}, '%')",
        "   </if>",
        "   <if test='belongTypes != null and belongTypes.size() > 0'>",
        "     AND pi.belong_type IN <foreach collection='belongTypes' item='bt' open='(' separator=',' close=')'>#{bt}</foreach>",
        "   </if>",
        "   <if test='(belongTypes == null or belongTypes.size() == 0) and belongType != null and belongType != \"\"'>",
        "     AND pi.belong_type = #{belongType}",
        "   </if>",
        "   <if test='productType != null'>",
        "     AND pp.product_type = #{productType}",
        "   </if>",
        "   <if test='beginDate != null'>",
        "     AND DATE(pp.produce_date) &gt;= DATE(#{beginDate})",
        "   </if>",
        "   <if test='endDate != null'>",
        "     AND DATE(pp.produce_date) &lt;= DATE(#{endDate})",
        "   </if>",
        " GROUP BY pp.product_id, DATE(pp.produce_date)",
        "   <if test='hasDamage != null'>",
        "     <choose>",
        "       <when test='hasDamage == 1'>HAVING COALESCE(SUM(pp.is_damaged), 0) &gt; 0</when>",
        "       <otherwise>HAVING COALESCE(SUM(pp.is_damaged), 0) = 0</otherwise>",
        "     </choose>",
        "   </if>",
        " ORDER BY produceDate DESC, MAX(pp.produce_time) DESC",
        "</script>"
    })
    List<ProductProductionGroupVo> selectProductionGroupList(@Param("produceNo") String produceNo,
                                                             @Param("productName") String productName,
                                                             @Param("belongType") String belongType,
                                                             @Param("belongTypes") List<String> belongTypes,
                                                             @Param("productType") Integer productType,
                                                             @Param("beginDate") Date beginDate,
                                                             @Param("endDate") Date endDate,
                                                             @Param("hasDamage") Integer hasDamage);

    /**
     * 标损/修改：把一条生产记录置为损坏（{@code is_damaged=1}）并写损坏凭证图 / 备注 / 标损时间（契约 b）。
     *
     * <p>幂等可重复调用（修改损坏信息时再调一次覆盖凭证图 / 备注 / 标损时间）。租户隔离 V1 单租户
     * 显式 {@code tenant_id='1001'}（与本 Mapper 其他原生 SQL 一致）；{@code del_flag='0'} 防改已删行。</p>
     *
     * @param id              生产记录 id
     * @param evidenceOssIds  损坏凭证图 OSS IDs CSV（可空）
     * @param remark          损坏备注（可空）
     * @param damageTime      标损时间
     * @return 实际更新行数（0=记录不存在/已删）
     */
    @Update({
        "<script>",
        "UPDATE t_warehouse_product_production",
        "   SET is_damaged = 1,",
        "       damage_evidence_oss_ids = #{evidenceOssIds},",
        "       damage_remark = #{remark},",
        "       damage_time = #{damageTime}",
        " WHERE id = #{id}",
        "   AND tenant_id = '1001'",
        "   AND del_flag = '0'",
        "</script>"
    })
    int updateDamage(@Param("id") Long id,
                     @Param("evidenceOssIds") String evidenceOssIds,
                     @Param("remark") String remark,
                     @Param("damageTime") Date damageTime);

    /**
     * 统计某需求下已标损坏的生产记录件数（契约 c，store 侧调用）。
     *
     * <p>{@code COUNT WHERE demand_id=? AND is_damaged=1 AND del_flag='0'}。租户隔离 V1 单租户显式
     * {@code tenant_id='1001'}。{@code demandId} 为空 → 返 0（service 端兜底）。</p>
     *
     * @param demandId 需求 FK（{@code t_warehouse_demand_manage.id}）
     * @return 该需求已标损坏件数
     */
    @Select("SELECT COUNT(*) FROM t_warehouse_product_production "
        + "WHERE demand_id = #{demandId} AND is_damaged = 1 "
        + "AND del_flag = '0' AND tenant_id = '1001'")
    long countDamagedByDemand(@Param("demandId") Long demandId);

    /**
     * row52：当日送达该店该产品的成品总重量（门店退回上限按重量比对）。
     *
     * <p>「送达该店」= 该产品当日发货清点（{@code is_delivery_check=1}、{@code delivery_check_time} 当天）
     * 的成品；门店归属经 {@code demand_id → demand.store_id} 关联（pack 链不写 production.store_id）。
     * 聚合 {@code SUM(product_weight)}。租户隔离 V1 单租户显式 {@code tenant_id='1001'}。</p>
     *
     * @param storeId   门店 FK
     * @param productId 产品 FK
     * @param date      业务日（按 delivery_check_time 当天过滤）
     * @return 当日送达该店该产品的成品总重量（无 → 0）
     */
    @Select("SELECT COALESCE(SUM(pp.product_weight), 0) "
        + "FROM t_warehouse_product_production pp "
        + "JOIN t_warehouse_demand_manage dm ON dm.id = pp.demand_id AND dm.del_flag = '0' "
        + "WHERE pp.product_id = #{productId} AND dm.store_id = #{storeId} "
        + "AND pp.is_delivery_check = 1 AND DATE(pp.delivery_check_time) = #{date} "
        + "AND pp.del_flag = '0' AND pp.tenant_id = '1001'")
    BigDecimal sumDeliveredWeightToStore(@Param("storeId") Long storeId,
                                         @Param("productId") Long productId,
                                         @Param("date") LocalDate date);

    /**
     * admin row8：当日送达该店的产品 id 集（去重）。口径同 {@link #sumDeliveredWeightToStore}
     * （{@code is_delivery_check=1}、{@code delivery_check_time} 当天、门店经 {@code demand_id → store_id} 关联）。
     * 门店退回候选按此判「当日到店有哪些产品」（进而按 belong_type / product_material 分流）。
     *
     * @param storeId 门店 FK
     * @param date    业务日（按 delivery_check_time 当天过滤）
     * @return 当日送达该店的 distinct product_id 列表（无 → 空）
     */
    @Select("SELECT DISTINCT pp.product_id "
        + "FROM t_warehouse_product_production pp "
        + "JOIN t_warehouse_demand_manage dm ON dm.id = pp.demand_id AND dm.del_flag = '0' "
        + "WHERE dm.store_id = #{storeId} AND pp.is_delivery_check = 1 "
        + "AND DATE(pp.delivery_check_time) = #{date} AND pp.del_flag = '0' AND pp.tenant_id = '1001'")
    List<Long> selectDeliveredProductIdsToStore(@Param("storeId") Long storeId, @Param("date") LocalDate date);

    /**
     * row40：当日到店该店该产品「需求订购份数」之和 {@code SUM(demand_quantity)}（门店退回上限按份数比对，份数产品口径）。
     *
     * <p>「到店」判定与 {@link #sumDeliveredWeightToStore} 同源——该产品当日发货清点
     * （{@code is_delivery_check=1}、{@code delivery_check_time} 当天）且门店经 {@code demand_id → store_id} 关联；
     * 但聚合的是<b>需求订购份数</b>而非成品重量（Kevin 2026-07-12 口径：份数产品到店量 = 当日到店该产品需求订购份数）。</p>
     *
     * <p>为避免 production ↔ demand 一对多 JOIN 造成 {@code demand_quantity} 扇出重复求和（每份成品一条 production 行，
     * 直接 SUM 会 ×份数），改从 demand 侧 SUM，用 EXISTS 关联「当日已发货清点该产品」的 production 界定到店 demand 集合。
     * 租户隔离 V1 单租户显式 {@code tenant_id='1001'}。</p>
     *
     * @param storeId   门店 FK
     * @param productId 产品 FK
     * @param date      业务日（按 delivery_check_time 当天过滤）
     * @return 当日到店该店该产品需求订购份数之和（无 → 0）
     */
    @Select("SELECT COALESCE(SUM(dm.demand_quantity), 0) "
        + "FROM t_warehouse_demand_manage dm "
        + "WHERE dm.store_id = #{storeId} AND dm.product_id = #{productId} "
        + "AND dm.del_flag = '0' AND dm.tenant_id = '1001' "
        + "AND EXISTS (SELECT 1 FROM t_warehouse_product_production pp "
        + "  WHERE pp.demand_id = dm.id AND pp.product_id = #{productId} "
        + "  AND pp.is_delivery_check = 1 AND DATE(pp.delivery_check_time) = #{date} "
        + "  AND pp.del_flag = '0' AND pp.tenant_id = '1001')")
    BigDecimal sumDeliveredQuantityToStore(@Param("storeId") Long storeId,
                                           @Param("productId") Long productId,
                                           @Param("date") LocalDate date);

    /**
     * admin row5：某白条需求「已发货白条实际重量之和」（kg）——供门店需求列表「预计到店重量」白条口径。
     * = Σ 绑定到该 demand 的 white_bar 成品已发货清点(is_delivery_check=1)重量 product_weight。
     * demand_id 门店级松散绑定，故 JOIN 商品主数据按 belong_type='white_bar' 过滤只算白条产出行。
     *
     * @param demandId 需求 FK
     * @return 已发货白条总重（无 → 0）
     */
    @Select("SELECT COALESCE(SUM(pp.product_weight), 0) "
        + "FROM t_warehouse_product_production pp "
        + "JOIN t_warehouse_product_info pi ON pi.id = pp.product_id AND pi.belong_type = 'white_bar' "
        + "WHERE pp.demand_id = #{demandId} AND pp.is_delivery_check = 1 "
        + "AND pp.del_flag = '0' AND pp.tenant_id = '1001'")
    BigDecimal sumShippedWhiteBarWeightByDemand(@Param("demandId") Long demandId);

    /**
     * admin row49/51：某果蔬 / 猪肉需求「已发货实际重量之和」（kg）——供门店需求列表「预计到店重量」果蔬/猪肉口径。
     * = Σ 绑定到该 demand 的 vegetable/pork 成品已发货清点(is_delivery_check=1)实际称重 product_weight。
     * 未发货（刚下单待确认）→ 无清点行 → 0（前端展示 '—'）；发货后按实际称重回填，非规格×需求量估算。
     *
     * @param demandId 需求 FK
     * @return 已发货果蔬/猪肉总重（无 → 0）
     */
    @Select("SELECT COALESCE(SUM(pp.product_weight), 0) "
        + "FROM t_warehouse_product_production pp "
        + "JOIN t_warehouse_product_info pi ON pi.id = pp.product_id AND pi.belong_type IN ('vegetable', 'pork') "
        + "WHERE pp.demand_id = #{demandId} AND pp.is_delivery_check = 1 "
        + "AND pp.del_flag = '0' AND pp.tenant_id = '1001'")
    BigDecimal sumShippedVegPorkWeightByDemand(@Param("demandId") Long demandId);

    /**
     * 某干货需求「已发货实际重量之和」（kg）——供门店需求列表「预计到店重量」干货口径。
     * = Σ 绑定到该 demand 的 dry_good 成品已发货清点(is_delivery_check=1)自带重量 product_weight。
     * 干货为独立成品（无组件 BOM），生产行自带真实逐份称重 product_weight（如 干羊肚菌 80g/份→0.08~0.10kg），
     * 与白条/果蔬同口径按已发货实际重回填。礼盒（多产品组合，无单一整体重量）与鸡蛋按枚计一致，不含在内、保持 '—'。
     *
     * @param demandId 需求 FK
     * @return 已发货干货总重（无 → 0）
     */
    @Select("SELECT COALESCE(SUM(pp.product_weight), 0) "
        + "FROM t_warehouse_product_production pp "
        + "JOIN t_warehouse_product_info pi ON pi.id = pp.product_id AND pi.belong_type = 'dry_good' "
        + "WHERE pp.demand_id = #{demandId} AND pp.is_delivery_check = 1 "
        + "AND pp.del_flag = '0' AND pp.tenant_id = '1001'")
    BigDecimal sumShippedDryGoodWeightByDemand(@Param("demandId") Long demandId);

    /**
     * 白条发货记录列表（WS12 row133，「产品生产记录」下「白条发货记录」页）。
     *
     * <p>数据源 = {@code t_warehouse_product_production} 中 {@code belong_type='white_bar'} 的出库记录
     * （白条整只/半只出库到发货月台 或 仓库出库），每行 = 一次白条出库事件。产品品类经 product_id FK
     * LEFT JOIN {@code product_info.belong_type} 取（白条产品固定挂 white_bar）。</p>
     *
     * <p>「出库方式」/「出库去向」派生自 {@code remark}（口径同 service {@code buildWarehouseOutRemark}
     * 落库格式「后台出库 去向=xxx [用户备注]」）：</p>
     * <ul>
     *   <li>{@code remark LIKE '后台出库%'} → outMethod='3'（仓库出库，djs_bar_out_method），
     *       outDest = remark「去向=」段解析出的字典码（djs_stock_out_dest，如 mine/kitchen）</li>
     *   <li>否则 → outMethod='1'（发货月台/发货领用，djs_bar_out_method），outDest='ship_dock'</li>
     * </ul>
     *
     * <p>过滤：{@code beginDate}/{@code endDate} 按 {@code produce_date} 区间；{@code earNo} 模糊；
     * {@code outMethods}/{@code outDests} 对派生列 IN（下推 HAVING 前用派生别名不便，改用等价 CASE 表达式
     * 在 WHERE 内直接比较）。租户隔离 V1 单租户显式 {@code tenant_id='1001'}（与本 Mapper 其他原生 SQL 一致）。</p>
     *
     * @param beginDate  发货日期起（空则不过滤）
     * @param endDate    发货日期止（空则不过滤）
     * @param earNo      猪只耳号模糊（空则不过滤）
     * @param storeId    门店筛选（{@code product_production.store_id} 精确匹配；空则不过滤）
     * @param outMethods 出库方式多选（派生 djs_bar_out_method 值 1/3；空则不过滤）
     * @param outDests   出库去向多选（djs_stock_out_dest 值；空则不过滤）
     * @return 白条发货记录行（按生产时间倒序）
     */
    @Select({
        "<script>",
        "SELECT pp.id                    AS id,",
        "       pp.produce_time          AS produceTime,",
        "       pi.product_id            AS productCode,",
        "       pp.product_name          AS productName,",
        "       pp.ear_no                AS earNo,",
        "       CASE WHEN pp.remark LIKE '后台出库%' THEN '3' ELSE '1' END AS outMethod,",
        "       CASE WHEN pp.remark LIKE '后台出库%'",
        "            THEN SUBSTRING_INDEX(SUBSTRING_INDEX(pp.remark, '去向=', -1), ' ', 1)",
        "            ELSE 'ship_dock' END AS outDest,",
        // admin row60：发货门店（pp.store_id 直存发货月台所选门店；后台出库/非到门店 store_id 为 NULL → 显示空）
        "       s.store_name             AS storeName,",
        "       pp.product_weight        AS productWeight,",
        "       pp.product_unit          AS productUnit,",
        "       pp.create_by             AS createBy",
        "  FROM t_warehouse_product_production pp",
        "  LEFT JOIN t_warehouse_product_info pi",
        "         ON pi.id = pp.product_id",
        "        AND pi.del_flag = '0'",
        "        AND pi.tenant_id = pp.tenant_id",
        "  LEFT JOIN t_md_store s",
        "         ON s.id = pp.store_id",
        "        AND s.del_flag = '0'",
        "        AND s.tenant_id = pp.tenant_id",
        " WHERE pp.del_flag = '0'",
        "   AND pp.tenant_id = '1001'",
        "   AND pi.belong_type = 'white_bar'",
        "   <if test='beginDate != null'>",
        "     AND DATE(pp.produce_date) &gt;= DATE(#{beginDate})",
        "   </if>",
        "   <if test='endDate != null'>",
        "     AND DATE(pp.produce_date) &lt;= DATE(#{endDate})",
        "   </if>",
        "   <if test='earNo != null and earNo != \"\"'>",
        "     AND pp.ear_no LIKE CONCAT('%', #{earNo}, '%')",
        "   </if>",
        "   <if test='storeId != null'>",
        "     AND pp.store_id = #{storeId}",
        "   </if>",
        "   <if test='outMethods != null and outMethods.size() > 0'>",
        "     AND (CASE WHEN pp.remark LIKE '后台出库%' THEN '3' ELSE '1' END)",
        "         IN <foreach collection='outMethods' item='m' open='(' separator=',' close=')'>#{m}</foreach>",
        "   </if>",
        "   <if test='outDests != null and outDests.size() > 0'>",
        "     AND (CASE WHEN pp.remark LIKE '后台出库%'",
        "               THEN SUBSTRING_INDEX(SUBSTRING_INDEX(pp.remark, '去向=', -1), ' ', 1)",
        "               ELSE 'ship_dock' END)",
        "         IN <foreach collection='outDests' item='d' open='(' separator=',' close=')'>#{d}</foreach>",
        "   </if>",
        " ORDER BY pp.produce_time DESC, pp.id DESC",
        "</script>"
    })
    List<WhiteBarShipmentVo> selectWhiteBarShipmentList(@Param("beginDate") Date beginDate,
                                                        @Param("endDate") Date endDate,
                                                        @Param("earNo") String earNo,
                                                        @Param("storeId") Long storeId,
                                                        @Param("outMethods") List<String> outMethods,
                                                        @Param("outDests") List<String> outDests);

}
