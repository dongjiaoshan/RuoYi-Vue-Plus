package org.dromara.djs.warehouse.pack.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionGroupVo;
import org.dromara.djs.warehouse.pack.domain.vo.ProductProductionVo;

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
     * <p>「需求门店数」{@code storeDemandCount} = 该组（同产品同生产日）生产记录去向的去重门店家数
     * {@code COUNT(DISTINCT pp.store_id)}（与子页「产品明细」每行「需求门店」一致）。未绑门店（{@code store_id IS NULL}，
     * 如发送位置=礼盒的组件）不计入。无门店 → 0。</p>
     *
     * <p>「原材料消耗量」{@code materialConsume} = 该组 {@code SUM(material_consume)}（同产品当日累计消耗的
     * 来源原材料重量；无配料行 NULL 跳过）。「原材料单位」{@code materialUnit} = {@code material_id} 经第二个
     * LEFT JOIN（{@code pmi}）取 {@code product_info.product_unit} 的 {@code MAX} 兜底（组内通常同一原料）。</p>
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
        "       MAX(pp.product_unit)   AS productUnit,",
        "       MAX(pp.product_spec)   AS productSpec,",
        "       MAX(pi.belong_type)    AS belongType,",
        "       MAX(pp.product_type)   AS productType,",
        "       SUM(pp.product_weight) AS produceQty,",
        "       COUNT(*)               AS itemCount,",
        "       SUM(pp.material_consume) AS materialConsume,",
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
        " WHERE pp.del_flag = '0'",
        "   AND pp.tenant_id = '1001'",
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

}
