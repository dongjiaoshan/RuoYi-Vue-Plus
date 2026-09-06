package org.dromara.djs.warehouse.flow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.flow.domain.query.InoutSummaryQuery;
import org.dromara.djs.warehouse.flow.domain.vo.InoutMonthVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutSummaryInVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutSummaryOutVo;

import java.util.List;

/**
 * 出入库月汇总聚合 Mapper（V6-R154 / R155 / R156）。
 *
 * <p><b>compute-on-read</b>：直接按月 GROUP BY 既有流水 {@code t_warehouse_stock_flow}，
 * 不建汇总表、不加跑批（与兄弟页「库存月汇总」同做法）。</p>
 *
 * <p>自定义 {@code @Select} 含聚合，WHERE 显式带 {@code tenant_id} 与 {@code del_flag}
 * ——多租户拦截器对聚合不保证注入。</p>
 *
 * <p>量一律用 {@code change_quantity}（绝对值列）求和，<b>不用</b> {@code change_num}
 * ——后者带符号但部分写入方符号写反（见 {@code StockOverviewMapper} 同款告警）。</p>
 *
 * @author djs
 * @since V6-R154
 */
@Mapper
public interface InoutMonthlyMapper {

    /**
     * 有出入库流水的月份列表（V6-R154 列表）。
     *
     * <p>入 / 出两个方向各自套用展示口径排除清单后取并集，故月份集合与两个下钻页展示的行严格对应：
     * 某月只有 pack_in 流水时不出现在列表里（点进去会是空表）。</p>
     *
     * <p><b>不补零月</b>：本页统计流量，没有出入库的月份出一行全零毫无信息。</p>
     *
     * @param tenantId    租户（V1 固定 '1001'）
     * @param statMonth   月份精确筛选 yyyy-MM（可空 = 全部月份）
     * @param inExcluded  入库展示排除的 flow_type（{@code FlowDisplayScope.IN_EXCLUDED}，非空）
     * @param outExcluded 出库展示排除的 flow_type（{@code FlowDisplayScope.OUT_EXCLUDED}，非空）
     * @return 月份行，按月份倒序
     */
    @Select("""
        <script>
        SELECT DATE_FORMAT(f.flow_date, '%Y-%m') AS statMonth
        FROM t_warehouse_stock_flow f
        WHERE f.del_flag = '0' AND f.tenant_id = #{tenantId}
          AND f.product_id IS NOT NULL AND f.product_id &lt;&gt; 0
          AND (
                (f.inout_type = 'IN' AND f.flow_type NOT IN
                  <foreach collection="inExcluded" item="t" open="(" separator="," close=")">#{t}</foreach>)
             OR (f.inout_type = 'OT' AND f.flow_type NOT IN
                  <foreach collection="outExcluded" item="t" open="(" separator="," close=")">#{t}</foreach>)
          )
        <if test="statMonth != null and statMonth != ''">
          AND DATE_FORMAT(f.flow_date, '%Y-%m') = #{statMonth}
        </if>
        GROUP BY DATE_FORMAT(f.flow_date, '%Y-%m')
        ORDER BY statMonth DESC
        </script>
        """)
    List<InoutMonthVo> selectMonths(@Param("tenantId") String tenantId,
                                    @Param("statMonth") String statMonth,
                                    @Param("inExcluded") List<String> inExcluded,
                                    @Param("outExcluded") List<String> outExcluded);

    /**
     * 当月入库汇总（V6-R155）：按 <b>产品名称 × 产品类型 × 规格 × 单位 × 入库方式 × 供应商</b> 聚合。
     *
     * <p><b>分组键 = 页面展示的那几列本身</b>，不是 {@code f.product_id}。产品档案表里存在
     * name + type + spec + unit 四项全同的重复档案（本库 702 行 / 594 个不同名字），按 product_id
     * 分组会让这类档案在页面上变成「七列显示值完全一样、量被拆开」的两行，甲方读成数据错。
     * 把类型 / 规格 / 单位一起放进分组键，则单位不同（小白菜 kg vs 袋）、规格不同、类型不同的行
     * 仍然各占一行，不会被错误合并。</p>
     *
     * <p>可空的规格 / 单位 / 供应商名一律 {@code COALESCE(..., '')} 归一后再分组：NULL 在 GROUP BY 里
     * 虽同组，但 SELECT 与 ORDER BY 拿 NULL 会让 service 的空值兜底与排序都不稳定。</p>
     *
     * <p>甲方「供应商字段为空的，就统计到一起」→ 无供应商 / 供应商档案已删的流水都落到
     * {@code COALESCE(sp.supplier_name, '')} 的空桶（service 把该行 supplierName 兜成「无供应商」）。</p>
     *
     * <p>MySQL 8 {@code ONLY_FULL_GROUP_BY}：SELECT 里每个非聚合列都在 GROUP BY 里，漏一列查询直接 500；
     * 用了 COALESCE 的列，SELECT 与 GROUP BY 必须是<b>同一个表达式</b>，写裸列会被判非法。</p>
     *
     * @param tenantId   租户
     * @param query      筛选条件（statMonth 必填）
     * @param inExcluded 入库展示排除的 flow_type（非空）
     * @return 入库汇总行
     */
    @Select("""
        <script>
        SELECT pi.product_name  AS productName,
               pi.product_type  AS productType,
               COALESCE(pi.product_spec, '') AS productSpec,
               COALESCE(pi.product_unit, '') AS productUnit,
               f.flow_type      AS flowType,
               COALESCE(sp.supplier_name, '') AS supplierName,
               COALESCE(SUM(f.change_quantity), 0) AS inboundQty
        FROM t_warehouse_stock_flow f
        JOIN t_warehouse_product_info pi ON pi.id = f.product_id AND pi.del_flag = '0'
        LEFT JOIN t_md_supplier sp ON sp.id = f.supplier_id AND sp.del_flag = '0'
        WHERE f.del_flag = '0' AND f.tenant_id = #{tenantId}
          AND f.inout_type = 'IN'
          AND f.product_id IS NOT NULL AND f.product_id &lt;&gt; 0
          AND f.flow_type NOT IN
              <foreach collection="inExcluded" item="t" open="(" separator="," close=")">#{t}</foreach>
          AND f.flow_date &gt;= STR_TO_DATE(CONCAT(#{query.statMonth}, '-01'), '%Y-%m-%d')
          AND f.flow_date &lt;  DATE_ADD(STR_TO_DATE(CONCAT(#{query.statMonth}, '-01'), '%Y-%m-%d'), INTERVAL 1 MONTH)
        <if test="query.productName != null and query.productName != ''">
          AND pi.product_name LIKE CONCAT('%', #{query.productName}, '%')
        </if>
        <if test="query.productTypes != null and query.productTypes.size() > 0">
          AND pi.product_type IN
              <foreach collection="query.productTypes" item="pt" open="(" separator="," close=")">#{pt}</foreach>
        </if>
        <if test="query.flowTypes != null and query.flowTypes.size() > 0">
          AND f.flow_type IN
              <foreach collection="query.flowTypes" item="ft" open="(" separator="," close=")">#{ft}</foreach>
        </if>
        <if test="query.supplierId != null">
          AND f.supplier_id = #{query.supplierId}
        </if>
        GROUP BY pi.product_name, pi.product_type, COALESCE(pi.product_spec, ''),
                 COALESCE(pi.product_unit, ''), f.flow_type, COALESCE(sp.supplier_name, '')
        ORDER BY productName, flowType, supplierName
        </script>
        """)
    List<InoutSummaryInVo> selectInSummary(@Param("tenantId") String tenantId,
                                           @Param("query") InoutSummaryQuery query,
                                           @Param("inExcluded") List<String> inExcluded);

    /**
     * 当月出库汇总（V6-R156）：按 <b>产品名称 × 产品类型 × 规格 × 单位 × 出库去向</b> 聚合。
     *
     * <p><b>分组键 = 页面展示的那几列本身</b>，不是 {@code f.product_id} —— 理由与
     * {@link #selectInSummary} 完全一致：重复产品档案（name/type/spec/unit 全同）会在页面上
     * 拆成显示值一模一样的两行；把类型 / 规格 / 单位放进分组键则单位不同的行不会被错误合并。</p>
     *
     * <p>出库去向为空的流水归到 {@code COALESCE(f.stock_out_dest, '')} 同一桶
     * （service 把该行 outDestName 兜成「未指定」）。</p>
     *
     * @param tenantId    租户
     * @param query       筛选条件（statMonth 必填）
     * @param outExcluded 出库展示排除的 flow_type（非空）
     * @return 出库汇总行
     */
    @Select("""
        <script>
        SELECT pi.product_name AS productName,
               pi.product_type AS productType,
               COALESCE(pi.product_spec, '') AS productSpec,
               COALESCE(pi.product_unit, '') AS productUnit,
               COALESCE(f.stock_out_dest, '') AS stockOutDest,
               COALESCE(SUM(f.change_quantity), 0) AS outboundQty
        FROM t_warehouse_stock_flow f
        JOIN t_warehouse_product_info pi ON pi.id = f.product_id AND pi.del_flag = '0'
        WHERE f.del_flag = '0' AND f.tenant_id = #{tenantId}
          AND f.inout_type = 'OT'
          AND f.product_id IS NOT NULL AND f.product_id &lt;&gt; 0
          AND f.flow_type NOT IN
              <foreach collection="outExcluded" item="t" open="(" separator="," close=")">#{t}</foreach>
          AND f.flow_date &gt;= STR_TO_DATE(CONCAT(#{query.statMonth}, '-01'), '%Y-%m-%d')
          AND f.flow_date &lt;  DATE_ADD(STR_TO_DATE(CONCAT(#{query.statMonth}, '-01'), '%Y-%m-%d'), INTERVAL 1 MONTH)
        <if test="query.productName != null and query.productName != ''">
          AND pi.product_name LIKE CONCAT('%', #{query.productName}, '%')
        </if>
        <if test="query.productTypes != null and query.productTypes.size() > 0">
          AND pi.product_type IN
              <foreach collection="query.productTypes" item="pt" open="(" separator="," close=")">#{pt}</foreach>
        </if>
        <if test="query.stockOutDests != null and query.stockOutDests.size() > 0">
          AND f.stock_out_dest IN
              <foreach collection="query.stockOutDests" item="sd" open="(" separator="," close=")">#{sd}</foreach>
        </if>
        GROUP BY pi.product_name, pi.product_type, COALESCE(pi.product_spec, ''),
                 COALESCE(pi.product_unit, ''), COALESCE(f.stock_out_dest, '')
        ORDER BY productName, stockOutDest
        </script>
        """)
    List<InoutSummaryOutVo> selectOutSummary(@Param("tenantId") String tenantId,
                                             @Param("query") InoutSummaryQuery query,
                                             @Param("outExcluded") List<String> outExcluded);
}
