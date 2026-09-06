package org.dromara.djs.warehouse.boardstat.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.boardstat.domain.vo.CategoryUnitQtyRow;

import java.time.LocalDate;
import java.util.List;

/**
 * mp 仓库统计「品类 × 单位 × 三指标」月度聚合 Mapper（V6-R178）。
 *
 * <p><b>compute-on-read</b>：按月直接 GROUP BY 业务表，不建汇总表、不加跑批
 * （与兄弟页「出入库月汇总」同做法）。三个指标各一条 SQL，本月与上月各调一次算环比。</p>
 *
 * <p>三指标口径（甲方 V6-R178 原文逐条对应）：</p>
 * <ul>
 *   <li><b>入库量</b> ← {@code t_warehouse_stock_flow} 入库方向流水，<b>只算原材料产品</b>
 *       （{@code product_info.product_attr = 2}，字典 djs_product_attr）。「只统计原材料」这条
 *       甲方写在入库量那一句里，<b>只约束入库量</b>，另两个指标不受它约束。</li>
 *   <li><b>生产量</b> ← {@code t_warehouse_product_production}（产品生产），量按产品自身计量单位取：
 *       kg / 公斤 → 当月重量合计，份 / 盒 / 枚等计数单位 → 记录条数（一次打包确认 = 一份）。
 *       与 {@code ProductProductionMapper.selectGroupList} 同一口径，两处必须一起改。</li>
 *   <li><b>原材料消耗量</b> ← 同表 {@code material_consume}，按<b>原材料自身</b>的品类与单位归组
 *       —— 消耗量是原材料的量，只有落在原材料的单位上，才能与同一行的入库量直接对比。</li>
 * </ul>
 *
 * <p>入库量套用 {@code FlowDisplayScope.IN_EXCLUDED} 排除清单，与 admin 入库记录页 / 入库汇总同集合，
 * 否则甲方拿两边对不上会当 bug 重报。</p>
 *
 * <p>自定义 {@code @Select} 含聚合，WHERE 显式带 {@code tenant_id} 与 {@code del_flag}
 * ——多租户拦截器对聚合不保证注入。</p>
 *
 * <p>MySQL 8 {@code ONLY_FULL_GROUP_BY}：SELECT 里的非聚合列与 GROUP BY 必须是同一个表达式；
 * 用 {@code COALESCE} 归一过的单位列，两处写法完全一致，CASE 判定里的单位则套 {@code MAX()}
 * （聚合恒合法，且分组内单位唯一，取 MAX 即该单位本身）。</p>
 *
 * @author djs
 * @since V6-R178
 */
@Mapper
public interface WarehouseBoardStatMapper {

    /**
     * 当月「入库量」：按品类 × 单位合计入库流水量，仅原材料产品（product_attr = 2）。
     *
     * @param tenantId     租户（V1 固定 '1001'）
     * @param belongTypes  统计的品类（djs_belong_type，非空）
     * @param inExcluded   入库展示排除的 flow_type（{@code FlowDisplayScope.IN_EXCLUDED}，非空）
     * @param from         统计月首日（含）
     * @param toExclusive  次月首日（不含）
     * @return 品类 × 单位 × 入库量
     */
    @Select("""
        <script>
        SELECT pi.belong_type AS belongType,
               COALESCE(pi.product_unit, '') AS productUnit,
               COALESCE(SUM(f.change_quantity), 0) AS qty
        FROM t_warehouse_stock_flow f
        JOIN t_warehouse_product_info pi
          ON pi.id = f.product_id AND pi.del_flag = '0' AND pi.tenant_id = f.tenant_id
        WHERE f.del_flag = '0' AND f.tenant_id = #{tenantId}
          AND f.inout_type = 'IN'
          AND f.product_id IS NOT NULL AND f.product_id &lt;&gt; 0
          AND f.flow_type NOT IN
              <foreach collection="inExcluded" item="t" open="(" separator="," close=")">#{t}</foreach>
          AND pi.product_attr = 2
          AND pi.belong_type IN
              <foreach collection="belongTypes" item="b" open="(" separator="," close=")">#{b}</foreach>
          AND f.flow_date &gt;= #{from}
          AND f.flow_date &lt;  #{toExclusive}
        GROUP BY pi.belong_type, COALESCE(pi.product_unit, '')
        </script>
        """)
    List<CategoryUnitQtyRow> selectInboundByCategoryUnit(@Param("tenantId") String tenantId,
                                                         @Param("belongTypes") List<String> belongTypes,
                                                         @Param("inExcluded") List<String> inExcluded,
                                                         @Param("from") LocalDate from,
                                                         @Param("toExclusive") LocalDate toExclusive);

    /**
     * 当月「生产量」：按品类 × 单位合计产品生产量（kg 取重量合计，计数单位取条数）。
     *
     * @param tenantId    租户
     * @param belongTypes 统计的品类（非空）
     * @param from        统计月首日（含）
     * @param toExclusive 次月首日（不含）
     * @return 品类 × 单位 × 生产量
     */
    @Select("""
        <script>
        SELECT pi.belong_type AS belongType,
               COALESCE(pi.product_unit, pp.product_unit, '') AS productUnit,
               CASE WHEN LOWER(TRIM(MAX(COALESCE(pi.product_unit, pp.product_unit, '')))) IN ('kg', '公斤')
                    THEN COALESCE(SUM(pp.product_weight), 0)
                    ELSE COUNT(*) END AS qty
        FROM t_warehouse_product_production pp
        JOIN t_warehouse_product_info pi
          ON pi.id = pp.product_id AND pi.del_flag = '0' AND pi.tenant_id = pp.tenant_id
        WHERE pp.del_flag = '0' AND pp.tenant_id = #{tenantId}
          AND pi.belong_type IN
              <foreach collection="belongTypes" item="b" open="(" separator="," close=")">#{b}</foreach>
          AND pp.produce_date &gt;= #{from}
          AND pp.produce_date &lt;  #{toExclusive}
        GROUP BY pi.belong_type, COALESCE(pi.product_unit, pp.product_unit, '')
        </script>
        """)
    List<CategoryUnitQtyRow> selectProduceByCategoryUnit(@Param("tenantId") String tenantId,
                                                         @Param("belongTypes") List<String> belongTypes,
                                                         @Param("from") LocalDate from,
                                                         @Param("toExclusive") LocalDate toExclusive);

    /**
     * 当月「原材料消耗量」：按<b>原材料</b>品类 × 原材料单位合计生产记录的 {@code material_consume}。
     *
     * <p>未记原材料（{@code material_id} 为空）的生产记录不计入 —— 消耗量无从归属品类，
     * 硬塞进成品品类会让「入库了多少原料 / 耗了多少原料」这组对比失真。</p>
     *
     * @param tenantId    租户
     * @param belongTypes 统计的品类（非空，按原材料的 belong_type 匹配）
     * @param from        统计月首日（含）
     * @param toExclusive 次月首日（不含）
     * @return 品类 × 单位 × 原材料消耗量
     */
    @Select("""
        <script>
        SELECT pm.belong_type AS belongType,
               COALESCE(pm.product_unit, '') AS productUnit,
               COALESCE(SUM(pp.material_consume), 0) AS qty
        FROM t_warehouse_product_production pp
        JOIN t_warehouse_product_info pm
          ON pm.id = pp.material_id AND pm.del_flag = '0' AND pm.tenant_id = pp.tenant_id
        WHERE pp.del_flag = '0' AND pp.tenant_id = #{tenantId}
          AND pp.material_id IS NOT NULL
          AND pm.belong_type IN
              <foreach collection="belongTypes" item="b" open="(" separator="," close=")">#{b}</foreach>
          AND pp.produce_date &gt;= #{from}
          AND pp.produce_date &lt;  #{toExclusive}
        GROUP BY pm.belong_type, COALESCE(pm.product_unit, '')
        </script>
        """)
    List<CategoryUnitQtyRow> selectMaterialConsumeByCategoryUnit(@Param("tenantId") String tenantId,
                                                                 @Param("belongTypes") List<String> belongTypes,
                                                                 @Param("from") LocalDate from,
                                                                 @Param("toExclusive") LocalDate toExclusive);
}
