package org.dromara.djs.warehouse.product.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.vo.ProductFlowRecordVo;
import org.dromara.djs.warehouse.product.domain.vo.ProductProductionRecordVo;
import org.dromara.djs.warehouse.product.domain.vo.ProductInfoVo;

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
               AND sf.flow_type  = 'return_in'
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
     * <p>可选筛选 bizDate（DATE 精确）。租户单租户显式 {@code tenant_id='1001'}。</p>
     *
     * @param productId 产品 ID
     * @param bizDate   业务日期（DATE 精确匹配；可空）
     */
    @Select("""
        <script>
        SELECT sf.id          AS id,
               sf.flow_date    AS bizDate,
               CASE
                 WHEN sf.inout_type = 'IN' THEN 'in_stock'
                 WHEN sf.flow_type = 'pick_out' THEN 'pick_out'
                 ELSE 'backend_out'
               END             AS bizType,
               sf.change_quantity AS bizNum,
               pi.product_unit AS bizUnit
          FROM t_warehouse_stock_flow sf
          LEFT JOIN t_warehouse_product_info pi
            ON pi.id = sf.product_id AND pi.del_flag = '0' AND pi.tenant_id = sf.tenant_id
         WHERE sf.product_id = #{productId}
           AND sf.del_flag   = '0'
           AND sf.tenant_id  = '1001'
         <if test="bizDate != null"> AND DATE(sf.flow_date) = DATE(#{bizDate}) </if>
         ORDER BY sf.flow_date DESC, sf.id DESC
        </script>
        """)
    List<ProductFlowRecordVo> selectFlowRecords(@Param("productId") Long productId,
                                                @Param("bizDate") Date bizDate);

}
