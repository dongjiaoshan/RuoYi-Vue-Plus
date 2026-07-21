package org.dromara.djs.warehouse.product.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.vo.MatEstimatedDemandVo;
import org.dromara.djs.warehouse.product.domain.vo.ProductFlowRecordVo;
import org.dromara.djs.warehouse.product.domain.vo.ProductProductionRecordVo;
import org.dromara.djs.warehouse.product.domain.vo.ProductInfoVo;
import org.dromara.djs.warehouse.purchase.domain.query.PurchaseInProductQuery;
import org.dromara.djs.warehouse.purchase.domain.vo.PurchaseInProductVo;

import java.util.Date;
import java.util.List;

/**
 * 产品 Mapper（WMS-MD-002）。
 *
 * @author djs
 * @since WMS-MD-002
 */
public interface ProductInfoMapper extends BaseMapperPlus<ProductInfo, ProductInfoVo> {

    /**
     * 统计指定 product 是否仍有 {@code product_stock > 0} 的库存记录。
     *
     * <p>{@link org.dromara.djs.warehouse.product.service.impl.ProductInfoServiceImpl#deleteWithValidByIds}
     * 删除前校验使用；多租户拦截器在 final SQL 阶段自动注入 {@code tenant_id} 过滤。</p>
     */
    @org.apache.ibatis.annotations.Select(
        "SELECT COUNT(1) FROM t_warehouse_location_stock "
            + "WHERE product_id = #{productId} "
            + "  AND product_stock > 0 "
            + "  AND del_flag = '0'")
    long countActiveStockByProduct(@Param("productId") Long productId);

    /**
     * 统计是否有其他 product 把指定 id 作为原材料（{@code product_material} FK）引用。
     *
     * <p>用于级联删除校验：若某产品被其他产品作为原材料引用，禁止软删（数据完整性）。</p>
     */
    @org.apache.ibatis.annotations.Select(
        "SELECT COUNT(1) FROM t_warehouse_product_info "
            + "WHERE product_material = #{productMaterialId} "
            + "  AND del_flag = '0'")
    long countReferencedAsMaterial(@Param("productMaterialId") Long productMaterialId);

    /**
     * 该原材料是否存在「对应的生产产品」（mp 物资领用软校验，doc/14 §5）。
     *
     * <p>成品经 {@code product_material} 反查原料：存在一行
     * {@code product_attr=1（生产产品）且 product_material=#{materialId}} 即视为有对应产品。
     * 用于 mp 领用前轻量提示（无对应产品 → 弹「确定仍要领用」软拦截，不阻断提交）。
     * MyBatis 把 {@code EXISTS} 返的 1/0 自动映射成 boolean。租户单租户显式 {@code tenant_id='1001'}（V1）。</p>
     *
     * @param materialId 原材料产品 id
     * @return true=存在对应生产产品 / false=无
     */
    @org.apache.ibatis.annotations.Select(
        "SELECT EXISTS(SELECT 1 FROM t_warehouse_product_info "
            + "WHERE product_material = #{materialId} "
            + "  AND product_attr = 1 "
            + "  AND del_flag = '0' "
            + "  AND tenant_id = '1001')")
    boolean existsFinishedProductByMaterial(@Param("materialId") Long materialId);

    /**
     * mp 生产领用「明日预估需求量」按原材料批量反查聚合（row18）。
     *
     * <p>口径：对每个原材料 {@code materialId}，取以它为 {@code product_material} 的所有生产产品
     * （成品 {@code product_attr=1} 且已配 {@code material_num}），聚合其
     * {@code SUM(明日需求量 × 单份用量)}——即 {@code DemandManageMapper.materialCalcQty}
     * （需求量 × 单份用量）口径的「按原材料反向汇总」版。</p>
     *
     * <p>明日 = {@code DATE_ADD(CURDATE(), INTERVAL 1 DAY)}（{@code demand_date} 是 DATE 列直接等值走索引）；
     * 汇总全部门店需求（生产备料非某门店，不加 store_id 过滤）；排除 CANCELLED / DELETED 单。
     * 成品未配 material_num 的（{@code material_num IS NULL}）不计入 → 某材料无任何配比成品则无行返回
     * → service 回填 null → 前端卡片「预估需求量」显空。租户单租户显式 {@code tenant_id='1001'}（V1）。</p>
     *
     * @param materialIds 原材料产品 id 列表（= 领用卡的 productId 集合，distinct 非空）
     * @return 每原材料一行（materialId + estimatedDemand）；无配比成品的材料不出现在结果中
     */
    @Select("""
        <script>
        SELECT p.product_material AS materialId,
               SUM(dm.demand_quantity * p.material_num) AS estimatedDemand
          FROM t_warehouse_product_info p
          JOIN t_warehouse_demand_manage dm
            ON dm.product_id = p.id
           AND dm.demand_date = DATE_ADD(CURDATE(), INTERVAL 1 DAY)
           AND dm.demand_status NOT IN ('CANCELLED', 'DELETED')
           AND dm.del_flag = '0'
           AND dm.tenant_id = '1001'
         WHERE p.product_attr = 1
           AND p.material_num IS NOT NULL
           AND p.del_flag = '0'
           AND p.tenant_id = '1001'
           AND p.product_material IN
           <foreach collection="materialIds" item="mid" open="(" separator="," close=")">#{mid}</foreach>
         GROUP BY p.product_material
        </script>
        """)
    List<MatEstimatedDemandVo> selectEstimatedDemandByMaterials(@Param("materialIds") List<Long> materialIds);

    /**
     * 产品详情「生产记录」子表（DJS-FIX-WMS-RALN-B）：按 productId 查 {@code t_warehouse_product_production}
     * 生产行 + {@code t_warehouse_stock_flow} 退货行（{@code flow_type=return_in}），UNION 后按日期倒序。
     *
     * <p>生产类型 {@code produceType}：生产行恒 {@code produce}；退货行 {@code return}。标准计重 / 差异源表
     * 无独立列，留 NULL（生产重量即数量×重量，无标准对照量）。可选筛选 produceDate（DATE 精确）+
     * produceType。租户单租户显式 {@code tenant_id='1001'}（跨表 @Select 拦截器不保证注入）。</p>
     *
     * @param productId    产品 ID
     * @param produceDate  生产日期（DATE 精确匹配；可空）
     * @param produceType  生产类型 produce / return（可空）
     */
    @Select("""
        <script>
        SELECT t.id           AS id,
               t.produce_date AS produceDate,
               t.produce_type AS produceType,
               t.produce_num  AS produceNum,
               t.produce_unit AS produceUnit,
               t.produce_weight AS produceWeight
          FROM (
            SELECT pp.id                                  AS id,
                   pp.produce_date                        AS produce_date,
                   'produce'                              AS produce_type,
                   COALESCE(pp.produce_quantity, pp.product_weight) AS produce_num,
                   pp.product_unit                        AS produce_unit,
                   pp.product_weight                      AS produce_weight
              FROM t_warehouse_product_production pp
             WHERE pp.product_id = #{productId}
               AND pp.del_flag   = '0'
               AND pp.tenant_id  = '1001'
            UNION ALL
            SELECT sf.id                                  AS id,
                   sf.flow_date                           AS produce_date,
                   'return'                               AS produce_type,
                   sf.change_quantity                     AS produce_num,
                   pi.product_unit                        AS produce_unit,
                   sf.change_quantity                     AS produce_weight
              FROM t_warehouse_stock_flow sf
              LEFT JOIN t_warehouse_product_info pi
                ON pi.id = sf.product_id AND pi.del_flag = '0' AND pi.tenant_id = sf.tenant_id
             WHERE sf.product_id = #{productId}
               AND sf.flow_type IN ('return_in', 'store_return_in', 'prod_return_in', 'pick_return_in')
               AND sf.del_flag   = '0'
               AND sf.tenant_id  = '1001'
          ) t
         WHERE 1 = 1
         <if test="produceDate != null"> AND DATE(t.produce_date) = DATE(#{produceDate}) </if>
         <if test="produceType != null and produceType != ''"> AND t.produce_type = #{produceType} </if>
         ORDER BY t.produce_date DESC, t.id DESC
        </script>
        """)
    List<ProductProductionRecordVo> selectProductionRecords(@Param("productId") Long productId,
                                                            @Param("produceDate") Date produceDate,
                                                            @Param("produceType") String produceType);

    /**
     * 商品详情「业务流水」子表（DJS-FIX-WMS-RALN-B）：按 productId 查 {@code t_warehouse_stock_flow}，
     * 派生 bizType（入库 / 领用出库 / 后台出库），回填 product_unit 作业务单位。
     *
     * <p>可选筛选 bizDate 区间 [bizDateFrom, bizDateTo]（DATE，含端点）。租户单租户显式 {@code tenant_id='1001'}。</p>
     *
     * @param productId   产品 ID
     * @param bizDateFrom 业务日期区间起（DATE，含当天；可空）
     * @param bizDateTo   业务日期区间止（DATE，含当天；可空）
     */
    @Select("""
        <script>
        SELECT sf.id          AS id,
               sf.flow_date    AS bizDate,
               CASE
                 WHEN sf.inout_type = 'IN' THEN 'in_stock'
                 WHEN sf.flow_type IN ('pick_out', 'prod_pick_out', 'dept_pick_out') THEN 'pick_out'
                 ELSE 'backend_out'
               END             AS bizType,
               sf.change_quantity AS bizNum,
               pi.product_unit AS bizUnit,
               sp.supplier_name AS supplierName,
               su.nick_name     AS operatorName
          FROM t_warehouse_stock_flow sf
          LEFT JOIN t_warehouse_product_info pi
            ON pi.id = sf.product_id AND pi.del_flag = '0' AND pi.tenant_id = sf.tenant_id
          LEFT JOIN t_md_supplier sp
            ON sp.id = sf.supplier_id AND sp.del_flag = '0'
          LEFT JOIN sys_user su
            ON su.user_id = sf.operator_id AND su.del_flag = '0'
         WHERE sf.product_id = #{productId}
           AND sf.del_flag   = '0'
           AND sf.tenant_id  = '1001'
         <if test="bizDateFrom != null"> AND DATE(sf.flow_date) &gt;= DATE(#{bizDateFrom}) </if>
         <if test="bizDateTo != null"> AND DATE(sf.flow_date) &lt;= DATE(#{bizDateTo}) </if>
         ORDER BY sf.flow_date DESC, sf.id DESC
        </script>
        """)
    List<ProductFlowRecordVo> selectFlowRecords(@Param("productId") Long productId,
                                                @Param("bizDateFrom") Date bizDateFrom,
                                                @Param("bizDateTo") Date bizDateTo);

    /**
     * 采购入库「商品维度」分页聚合查询（WMS-OUTSOURCE-001 需求1）。
     *
     * <p>以 {@code product_type=2}（外购商品）为分页骨架，子查询聚合：</p>
     * <ul>
     *   <li>{@code currentStock} = SUM(location_stock.product_stock)；</li>
     *   <li>{@code lastInTime} = MAX(stock_flow.flow_date) WHERE flow_type='purchase_in'；</li>
     *   <li>{@code lastPurchaserId} = 最近一笔 purchase_in 流水的 operator_id（service 经 @Translation 转中文名）；</li>
     *   <li>{@code monthInTotal} = SUM(change_quantity) WHERE flow_type='purchase_in' AND 当月
     *       [本月 1 号 ≤ flow_date < 下月 1 号]。</li>
     * </ul>
     *
     * <p>{@code storeLocationName} / {@code imageUrl} 走 service 层批量回填（禁 N+1，不在 SQL JOIN）。
     * 多库位 {@code store_location_id} 为 CSV，{@code storeLocationId} 入参用 FIND_IN_SET 命中。
     * 租户单租户显式 {@code tenant_id='1001'}（V1）。</p>
     *
     * @param page  分页对象
     * @param query 查询条件（productName / buyClass / supplierId / storeLocationId）
     * @return 商品维度聚合分页结果
     */
    @Select("""
        <script>
        SELECT p.id                AS productId,
               p.product_id         AS productCode,
               p.product_name       AS productName,
               p.product_unit       AS productUnit,
               p.product_spec       AS productSpec,
               p.product_thumb      AS productThumb,
               p.image_oss_id       AS imageOssId,
               p.buy_class          AS buyClass,
               p.store_location_id  AS storeLocationId,
               p.supplier_id        AS supplierId,
               sp.supplier_name     AS supplierName,
               COALESCE((SELECT SUM(s.product_stock) FROM t_warehouse_location_stock s
                          WHERE s.product_id = p.id AND s.del_flag = '0' AND s.tenant_id = '1001'), 0) AS currentStock,
               (SELECT MAX(f.flow_date) FROM t_warehouse_stock_flow f
                 WHERE f.product_id = p.id AND f.flow_type = 'purchase_in' AND f.del_flag = '0'
                   AND f.tenant_id = '1001') AS lastInTime,
               (SELECT f.operator_id FROM t_warehouse_stock_flow f
                 WHERE f.product_id = p.id AND f.flow_type = 'purchase_in' AND f.del_flag = '0'
                   AND f.tenant_id = '1001'
                 ORDER BY f.flow_date DESC, f.id DESC LIMIT 1) AS lastPurchaserId,
               COALESCE((SELECT SUM(f.change_quantity) FROM t_warehouse_stock_flow f
                          WHERE f.product_id = p.id AND f.flow_type = 'purchase_in' AND f.del_flag = '0'
                            AND f.tenant_id = '1001'
                            AND f.flow_date >= DATE_FORMAT(NOW(), '%Y-%m-01')
                            AND f.flow_date &lt; DATE_FORMAT(DATE_ADD(NOW(), INTERVAL 1 MONTH), '%Y-%m-01')), 0) AS monthInTotal
          FROM t_warehouse_product_info p
          LEFT JOIN t_md_supplier sp
            ON sp.id = p.supplier_id AND sp.del_flag = '0'
         WHERE p.del_flag     = '0'
           AND p.tenant_id    = '1001'
           AND (p.product_type = 2
                OR (p.is_buy_out = 1 AND (p.product_attr IS NULL OR p.product_attr &lt;&gt; 2))
                OR EXISTS (SELECT 1 FROM t_warehouse_stock_flow f2
                            WHERE f2.product_id = p.id AND f2.flow_type = 'purchase_in'
                              AND f2.del_flag = '0' AND f2.tenant_id = '1001'))
           <if test="query.productName != null and query.productName != ''">
             AND p.product_name LIKE CONCAT('%', #{query.productName}, '%')
           </if>
           <if test="query.buyClasses != null and query.buyClasses.size() > 0">
             AND p.buy_class IN
             <foreach collection="query.buyClasses" item="bc" open="(" separator="," close=")">#{bc}</foreach>
           </if>
           <if test="(query.buyClasses == null or query.buyClasses.size() == 0) and query.buyClass != null and query.buyClass != ''">
             AND p.buy_class = #{query.buyClass}
           </if>
           <if test="query.supplierIds != null and query.supplierIds.size() > 0">
             AND p.supplier_id IN
             <foreach collection="query.supplierIds" item="sid" open="(" separator="," close=")">#{sid}</foreach>
           </if>
           <if test="(query.supplierIds == null or query.supplierIds.size() == 0) and query.supplierId != null">
             AND p.supplier_id = #{query.supplierId}
           </if>
           <if test="query.storeLocationIds != null and query.storeLocationIds.size() > 0">
             AND
             <foreach collection="query.storeLocationIds" item="lid" open="(" separator=" OR " close=")">FIND_IN_SET(#{lid}, p.store_location_id)</foreach>
           </if>
           <if test="(query.storeLocationIds == null or query.storeLocationIds.size() == 0) and query.storeLocationId != null">
             AND FIND_IN_SET(#{query.storeLocationId}, p.store_location_id)
           </if>
         ORDER BY p.product_name ASC
        </script>
        """)
    IPage<PurchaseInProductVo> selectPurchaseInProductPage(@Param("page") IPage<PurchaseInProductVo> page,
                                                           @Param("query") PurchaseInProductQuery query);

}
