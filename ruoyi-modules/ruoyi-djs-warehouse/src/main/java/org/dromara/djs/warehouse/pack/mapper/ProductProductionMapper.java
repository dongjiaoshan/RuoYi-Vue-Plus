package org.dromara.djs.warehouse.pack.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionGroupVo;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionVo;

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
     * <p>租户隔离：未启全局 MP 拦截器，显式 {@code pp.tenant_id='1001'}（V1 单租户，与本模块既有
     * 聚合 SQL 范式一致）；{@code pp.del_flag='0'}（CHAR(1) 未删）。</p>
     *
     * <p>「需求门店数」{@code storeDemandCount} 经相关子查询统计该 product_id 当前有未发货需求的
     * 门店去重家数（口径同打包台 {@link org.dromara.djs.warehouse.demand.mapper.DemandManageMapper#selectStoreDemandCopies}：
     * 「已确认及之后」需求 {@code demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')}
     * + 有门店 + 该门店剩余需求量 {@code SUM(demand_quantity - shipped_count) > 0}；草稿 / 待确认不计入）。
     * 无需求 → 0。</p>
     *
     * @param produceNo   生产编号 LIKE 过滤（空则不过滤）
     * @param productName 产品名称 LIKE 过滤（空则不过滤）
     * @param belongType  产品品类过滤（字典 djs_belong_type，空则不过滤；下推 product_info WHERE）
     * @param productType 产品类型过滤（空则不过滤；同 product_id 组内同值，放 WHERE）
     * @param beginDate   生产日期起（空则不过滤）
     * @param endDate     生产日期止（空则不过滤）
     * @return 分组聚合行（按生产日期倒序 + 组内最大生产时间倒序）
     */
    @Select({
        "<script>",
        "SELECT pp.product_id          AS productId,",
        "       DATE(pp.produce_date)  AS produceDate,",
        "       MAX(pp.product_name)   AS productName,",
        "       MAX(pp.product_unit)   AS productUnit,",
        "       MAX(pp.product_spec)   AS productSpec,",
        "       MAX(pi.belong_type)    AS belongType,",
        "       MAX(pp.product_type)   AS productType,",
        "       SUM(pp.product_weight) AS produceQty,",
        "       COUNT(*)               AS itemCount,",
        "       (SELECT COUNT(*) FROM (",
        "          SELECT dm.store_id",
        "            FROM t_warehouse_demand_manage dm",
        "           WHERE dm.product_id = pp.product_id",
        "             AND dm.store_id IS NOT NULL",
        "             AND dm.demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')",
        "             AND dm.del_flag = '0'",
        "             AND dm.tenant_id = pp.tenant_id",
        "           GROUP BY dm.store_id",
        "          HAVING SUM(dm.demand_quantity - COALESCE(dm.shipped_count, 0)) &gt; 0",
        "       ) sd) AS storeDemandCount",
        "  FROM t_warehouse_product_production pp",
        "  LEFT JOIN t_warehouse_product_info pi",
        "         ON pi.id = pp.product_id",
        "        AND pi.del_flag = '0'",
        "        AND pi.tenant_id = pp.tenant_id",
        " WHERE pp.del_flag = '0'",
        "   AND pp.tenant_id = '1001'",
        "   <if test='produceNo != null and produceNo != \"\"'>",
        "     AND pp.produce_no LIKE CONCAT('%', #{produceNo}, '%')",
        "   </if>",
        "   <if test='productName != null and productName != \"\"'>",
        "     AND pp.product_name LIKE CONCAT('%', #{productName}, '%')",
        "   </if>",
        "   <if test='belongType != null and belongType != \"\"'>",
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
        " ORDER BY produceDate DESC, MAX(pp.produce_time) DESC",
        "</script>"
    })
    List<ProductProductionGroupVo> selectProductionGroupList(@Param("produceNo") String produceNo,
                                                             @Param("productName") String productName,
                                                             @Param("belongType") String belongType,
                                                             @Param("productType") Integer productType,
                                                             @Param("beginDate") Date beginDate,
                                                             @Param("endDate") Date endDate);

}
