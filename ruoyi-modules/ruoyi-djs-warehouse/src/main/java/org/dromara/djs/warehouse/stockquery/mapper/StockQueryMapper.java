package org.dromara.djs.warehouse.stockquery.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.stockquery.domain.vo.StockQueryFlowVo;
import org.dromara.djs.warehouse.stockquery.domain.vo.StockQueryItemVo;
import org.dromara.djs.warehouse.stockquery.domain.vo.StockQueryStatVo;

import java.util.List;

/**
 * mp 库存查询 hub 聚合 Mapper（FIX-WMS-MP-STOCKQUERY-001 独占，read-only）。
 *
 * <p>全注解 SQL，直接 LEFT JOIN 主数据回填名称后映射独占 VO；完全不复用 admin VO 的 AutoMapper 转换，
 * 与共享 {@code StockFlowMapper / LocationStockMapper} 解耦。本 mapper 不挂 {@code @TableName}，
 * 不继承 {@code BaseMapperPlus}（无单表 CRUD 语义），仅承载跨表查询。</p>
 *
 * <p>多租户：mp V1 单租户，固定条件（del_flag / tenant_id 等）全部由 service 层注入 {@link QueryWrapper}，
 * 与本模块既有自定义 SQL {@code StockFlowMapper.selectSupplierDeals} 口径一致（不依赖 MP 拦截器对
 * 别名 SQL 的注入）。</p>
 *
 * <p>{@code ${ew.customSqlSegment}} 自带 {@code WHERE} 关键字（参 ruoyi-demo TestDemoMapper.xml
 * {@code SELECT * FROM test_demo ${ew.customSqlSegment}} 先例），故 SQL 中不再写自有 WHERE，
 * 全部过滤 + 排序条件由 wrapper 承载。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-STOCKQUERY-001
 */
public interface StockQueryMapper {

    /**
     * 库存查询分页（原型图27）：t_warehouse_location_stock LEFT JOIN t_warehouse_location_info 取库位名。
     *
     * <p>全部过滤条件（del_flag / tenant_id / product_id IS NOT NULL / 库位 / 产品名 / 排序）由
     * {@link QueryWrapper}（service 层构造，{@code s.} 别名前缀）下推；{@code customSqlSegment}
     * 自带 WHERE，故此处 FROM/JOIN 后直接拼接，不写自有 WHERE。仅产品维度库存（排除猪只 / 地块行）。</p>
     */
    @Select("""
        SELECT s.id, s.product_id AS productId, s.product_name AS productName,
               s.location_id AS locationId, l.location_name AS locationName,
               s.product_stock AS productStock, s.product_unit AS productUnit
          FROM t_warehouse_location_stock s
          LEFT JOIN t_warehouse_location_info l ON l.id = s.location_id AND l.del_flag = '0'
          ${ew.customSqlSegment}
        """)
    Page<StockQueryItemVo> selectStockPage(@Param("page") Page<StockQueryItemVo> page,
                                           @Param(Constants.WRAPPER) QueryWrapper<?> ew);

    /**
     * 出入库流水分页（原型图55）：t_warehouse_stock_flow LEFT JOIN product_info + supplier 取名称。
     *
     * <p>全部过滤条件（del_flag / tenant_id / inout_type / 产品 id 集合 / 日期区间 / 排序）由
     * {@link QueryWrapper}（service 层构造，{@code f.} 别名前缀）下推；{@code customSqlSegment}
     * 自带 WHERE，故 JOIN 后直接拼接。</p>
     */
    @Select("""
        SELECT f.id, f.flow_no AS flowNo, f.flow_date AS flowDate,
               f.product_id AS productId, p.product_name AS productName, p.product_unit AS productUnit,
               f.inout_type AS inoutType, f.flow_type AS flowType,
               f.change_quantity AS changeQuantity,
               f.supplier_id AS supplierId, sup.supplier_name AS supplierName,
               f.operator_id AS operatorId, f.remark
          FROM t_warehouse_stock_flow f
          LEFT JOIN t_warehouse_product_info p ON p.id = f.product_id AND p.del_flag = '0'
          LEFT JOIN t_md_supplier sup ON sup.id = f.supplier_id AND sup.del_flag = '0'
          ${ew.customSqlSegment}
        """)
    Page<StockQueryFlowVo> selectFlowPage(@Param("page") Page<StockQueryFlowVo> page,
                                          @Param(Constants.WRAPPER) QueryWrapper<?> ew);

    /**
     * 退货 / 损耗统计聚合（按产品 group by）：复用 stock_flow + product_info，按 flowType 过滤。
     *
     * <p>退货统计传 {@code flowType='return_in'}，损耗统计传 {@code flowType='loss'}（djs_flow_type 字典
     * 权威 value）。数量取 {@code SUM(change_quantity)}（绝对值列）。按数量合计倒序。</p>
     *
     * @param flowType djs_flow_type value（return_in / loss）
     * @return 各产品聚合行（无数据返空 list）
     */
    @Select("""
        SELECT f.product_id AS productId, p.product_name AS productName, p.product_unit AS productUnit,
               COUNT(1) AS flowCount, COALESCE(SUM(f.change_quantity), 0) AS totalQuantity
          FROM t_warehouse_stock_flow f
          LEFT JOIN t_warehouse_product_info p ON p.id = f.product_id AND p.del_flag = '0'
         WHERE f.del_flag = '0' AND f.tenant_id = '1001'
           AND f.flow_type = #{flowType}
         GROUP BY f.product_id, p.product_name, p.product_unit
         ORDER BY totalQuantity DESC
        """)
    List<StockQueryStatVo> selectStatByFlowType(@Param("flowType") String flowType);

}
