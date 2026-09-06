package org.dromara.djs.warehouse.flow.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.flow.domain.query.InoutStatQuery;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatInVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatOutVo;

import java.util.List;

/**
 * 出入库统计聚合 Mapper（V6-R167 入库统计 / 出库统计）。
 *
 * <p><b>compute-on-read</b>：直接按日期区间 GROUP BY 既有流水 {@code t_warehouse_stock_flow}，
 * 不建汇总表、不加跑批（与兄弟页「出入库月汇总」同一套聚合口径，只把「先选月份再下钻」
 * 换成「日期区间 + Tab」）。</p>
 *
 * <p>自定义 {@code @Select} 含聚合，WHERE 显式带 {@code tenant_id} 与 {@code del_flag}
 * ——多租户拦截器对聚合不保证注入。</p>
 *
 * <p>量一律用 {@code change_quantity}（绝对值列）求和，<b>不用</b> {@code change_num}
 * ——后者带符号但部分写入方符号写反（见 {@code StockOverviewMapper} 同款告警）。</p>
 *
 * <p><b>SQL 提成常量、分页与导出两个方法共用同一份</b>：日期区间放开后行数可能上万，列表必须
 * 分页，导出必须全量，两个方法缺一不可；而甲方是拿导出的表去核对页面的，两份 SQL 各写一遍
 * 迟早改歪一边，届时「导出比列表多几行」这种问题极难查。文本块是编译期常量，可直接作
 * {@code @Select} 的值。</p>
 *
 * @author djs
 * @since V6-R167
 */
@Mapper
public interface InoutStatMapper {

    /**
     * 入库统计聚合 SQL：按 <b>产品名称 × 产品类型 × 规格 × 单位 × 入库方式 × 供应商</b> 聚合。
     *
     * <p><b>分组键 = 页面展示的那几列本身</b>，不是 {@code f.product_id}。产品档案表里存在
     * name + type + spec + unit 四项全同的重复档案，按 product_id 分组会让这类档案在页面上变成
     * 「七列显示值完全一样、量被拆开」的两行，甲方读成数据错。把类型 / 规格 / 单位一起放进分组键，
     * 则单位不同（小白菜 kg vs 袋）、规格不同、类型不同的行仍然各占一行，不会被错误合并。</p>
     *
     * <p>可空的规格 / 单位 / 供应商名一律 {@code COALESCE(..., '')} 归一后再分组：NULL 在 GROUP BY 里
     * 虽同组，但 SELECT 与 ORDER BY 拿 NULL 会让 service 的空值兜底与排序都不稳定。</p>
     *
     * <p>甲方「供应商为空的统计到一起」→ 无供应商 / 供应商档案已删的流水都落到
     * {@code COALESCE(sp.supplier_name, '')} 的空桶（service 把该行 supplierName 兜成「无供应商」）。
     * {@code noSupplier} 为真时只留这一桶，判据必须是<b>联出来的名字</b>而不是 {@code f.supplier_id IS NULL}
     * ——档案已删的流水 supplier_id 有值但联不出名字，同样属于这一桶。</p>
     *
     * <p>聚合放子查询、外层只排序：外层不带 GROUP BY，MyBatis-Plus 的自动 count 就是
     * {@code SELECT COUNT(*) FROM (聚合) g}，分页总数 = 聚合后的行数而不是流水条数
     * （与 {@code FeedLogMapper.selectDailyPage} 同做法）。ORDER BY 拿<b>整个分组键</b>排，
     * 键唯一 ⇒ 排序全序 ⇒ 翻页不会出现某行既在第 1 页又在第 2 页。</p>
     *
     * <p>MySQL 8 {@code ONLY_FULL_GROUP_BY}：SELECT 里每个非聚合列都在 GROUP BY 里，漏一列查询直接 500；
     * 用了 COALESCE 的列，SELECT 与 GROUP BY 必须是<b>同一个表达式</b>，写裸列会被判非法。</p>
     */
    String IN_STAT_SQL = """
        <script>
        SELECT g.productName, g.productType, g.productSpec, g.productUnit,
               g.flowType, g.supplierName, g.inboundQty
        FROM (
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
            <if test="query.dateFrom != null">
              AND f.flow_date &gt;= #{query.dateFrom}
            </if>
            <if test="query.dateTo != null">
              AND f.flow_date &lt; DATE_ADD(#{query.dateTo}, INTERVAL 1 DAY)
            </if>
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
            <if test="query.noSupplier != null and query.noSupplier">
              AND (sp.supplier_name IS NULL OR sp.supplier_name = '')
            </if>
            GROUP BY pi.product_name, pi.product_type, COALESCE(pi.product_spec, ''),
                     COALESCE(pi.product_unit, ''), f.flow_type, COALESCE(sp.supplier_name, '')
        ) g
        ORDER BY g.productName, g.productType, g.productSpec, g.productUnit, g.flowType, g.supplierName
        </script>
        """;

    /**
     * 出库统计聚合 SQL：按 <b>产品名称 × 产品类型 × 规格 × 单位 × 出库去向</b> 聚合。
     *
     * <p>分组键、子查询分页、ORDER BY 全序的理由与 {@link #IN_STAT_SQL} 完全一致。
     * 出库去向为空的流水归到 {@code COALESCE(f.stock_out_dest, '')} 同一桶
     * （service 把该行 outDestName 兜成「未指定」）。</p>
     */
    String OUT_STAT_SQL = """
        <script>
        SELECT g.productName, g.productType, g.productSpec, g.productUnit,
               g.stockOutDest, g.outboundQty
        FROM (
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
            <if test="query.dateFrom != null">
              AND f.flow_date &gt;= #{query.dateFrom}
            </if>
            <if test="query.dateTo != null">
              AND f.flow_date &lt; DATE_ADD(#{query.dateTo}, INTERVAL 1 DAY)
            </if>
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
        ) g
        ORDER BY g.productName, g.productType, g.productSpec, g.productUnit, g.stockOutDest
        </script>
        """;

    /**
     * 入库统计分页（V6-R167 入库统计 Tab 列表）。
     *
     * @param page       分页参数
     * @param tenantId   租户（V1 固定 '1001'）
     * @param query      筛选条件（日期区间两端可空 = 不限）
     * @param inExcluded 入库展示排除的 flow_type（{@code FlowDisplayScope.IN_EXCLUDED}，非空）
     * @return 入库统计行（按分组键全序）
     */
    @Select(IN_STAT_SQL)
    IPage<InoutStatInVo> selectInStatPage(IPage<InoutStatInVo> page,
                                          @Param("tenantId") String tenantId,
                                          @Param("query") InoutStatQuery query,
                                          @Param("inExcluded") List<String> inExcluded);

    /**
     * 入库统计全量（V6-R167 入库统计导出：导出内容与列表一致，只是不分页）。
     *
     * @param tenantId   租户
     * @param query      筛选条件
     * @param inExcluded 入库展示排除的 flow_type（非空）
     * @return 入库统计行
     */
    @Select(IN_STAT_SQL)
    List<InoutStatInVo> selectInStatList(@Param("tenantId") String tenantId,
                                         @Param("query") InoutStatQuery query,
                                         @Param("inExcluded") List<String> inExcluded);

    /**
     * 出库统计分页（V6-R167 出库统计 Tab 列表）。
     *
     * @param page        分页参数
     * @param tenantId    租户
     * @param query       筛选条件
     * @param outExcluded 出库展示排除的 flow_type（{@code FlowDisplayScope.OUT_EXCLUDED}，非空）
     * @return 出库统计行（按分组键全序）
     */
    @Select(OUT_STAT_SQL)
    IPage<InoutStatOutVo> selectOutStatPage(IPage<InoutStatOutVo> page,
                                            @Param("tenantId") String tenantId,
                                            @Param("query") InoutStatQuery query,
                                            @Param("outExcluded") List<String> outExcluded);

    /**
     * 出库统计全量（V6-R167 出库统计导出）。
     *
     * @param tenantId    租户
     * @param query       筛选条件
     * @param outExcluded 出库展示排除的 flow_type（非空）
     * @return 出库统计行
     */
    @Select(OUT_STAT_SQL)
    List<InoutStatOutVo> selectOutStatList(@Param("tenantId") String tenantId,
                                           @Param("query") InoutStatQuery query,
                                           @Param("outExcluded") List<String> outExcluded);
}
