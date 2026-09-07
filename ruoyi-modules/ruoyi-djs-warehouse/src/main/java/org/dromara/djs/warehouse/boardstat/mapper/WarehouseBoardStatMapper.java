package org.dromara.djs.warehouse.boardstat.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.boardstat.domain.vo.BoardStatProductRowVo;
import org.dromara.djs.warehouse.boardstat.domain.vo.CategoryUnitQtyRow;

import java.time.LocalDate;
import java.util.List;

/**
 * mp 仓库统计「品类 × 单位 × 三指标」月度聚合 + 卡片下钻明细 Mapper（V6-R178）。
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
 * <h3>⚠️ 卡片与明细共用同一份筛选条件，不准复制两套</h3>
 * <p>明细（V6-R193 起按<b>产品</b>聚合，不是逐条流水）与卡片必须对得上（甲方就是拿明细去核卡片的），
 * 所以两者的 FROM / WHERE 一律取自本接口的 {@code *_FROM} / {@code *_WHERE} 常量：</p>
 * <ul>
 *   <li>入库：{@link #INBOUND_FROM} + {@link #INBOUND_WHERE}
 *       —— {@link #selectInboundByCategoryUnit}（卡片）与 {@link #selectInboundDetailByProduct}（明细）共用；</li>
 *   <li>生产：{@link #PRODUCE_FROM} + {@link #PRODUCE_WHERE} + {@link #PRODUCE_UNIT_EXPR}
 *       —— {@link #selectProduceByCategoryUnit}（卡片）与 {@link #selectProduceDetailByProduct}（明细）共用。</li>
 * </ul>
 * <p><b>改一处即两处同时生效</b>；要加筛选条件只能改常量本身，不准在某一个方法上单独加 WHERE。
 * 明细只是把卡片的 GROUP BY 再细一档（品类 × 单位 → 产品 × 单位），所以「按单位 Σ 明细 == 卡片数字」
 * 是构造上成立的；弹窗上方的 {@code totals} 更是直接复用卡片那两个聚合方法（见 service），不另写 SQL。</p>
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

    /** 入库口径的表与联表（卡片聚合与明细分页共用；明细在此之后再 LEFT JOIN 供应商 / 库位）。 */
    String INBOUND_FROM = """
        FROM t_warehouse_stock_flow f
        JOIN t_warehouse_product_info pi
          ON pi.id = f.product_id AND pi.del_flag = '0' AND pi.tenant_id = f.tenant_id
        """;

    /**
     * 入库口径的筛选条件（卡片聚合与明细分页共用，改这里两边同时生效）。
     *
     * <p>参数名固定 {@code tenantId / inExcluded / belongTypes / from / toExclusive}，
     * 共用它的方法签名必须原样带这几个 {@code @Param}。</p>
     */
    String INBOUND_WHERE = """
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
        """;

    /** 生产口径的表与联表（卡片聚合与明细分页共用；明细在此之后再 LEFT JOIN 原材料档案）。 */
    String PRODUCE_FROM = """
        FROM t_warehouse_product_production pp
        JOIN t_warehouse_product_info pi
          ON pi.id = pp.product_id AND pi.del_flag = '0' AND pi.tenant_id = pp.tenant_id
        """;

    /**
     * 生产口径的筛选条件（卡片聚合与明细分页共用，改这里两边同时生效）。
     *
     * <p>参数名固定 {@code tenantId / belongTypes / from / toExclusive}。</p>
     */
    String PRODUCE_WHERE = """
        WHERE pp.del_flag = '0' AND pp.tenant_id = #{tenantId}
          AND pi.belong_type IN
              <foreach collection="belongTypes" item="b" open="(" separator="," close=")">#{b}</foreach>
          AND pp.produce_date &gt;= #{from}
          AND pp.produce_date &lt;  #{toExclusive}
        """;

    /** 生产量取哪个单位：产品档案单位优先，档案没填才退回生产记录上的冗余单位。 */
    String PRODUCE_UNIT_EXPR = "COALESCE(pi.product_unit, pp.product_unit, '')";

    /** 计重单位集合：落在这里的按重量求和，其余单位按记录条数计（一次打包确认 = 一份）。 */
    String PRODUCE_WEIGHT_UNITS = "('kg', '公斤')";

    /**
     * 当月「入库量」：按品类 × 单位合计入库流水量，仅原材料产品（product_attr = 2）。
     *
     * <p>筛选条件取自 {@link #INBOUND_FROM} + {@link #INBOUND_WHERE}，与
     * {@link #selectInboundDetailByProduct} 共用；明细弹窗上方的合计也直接调本方法，
     * 所以「明细合计 == 卡片入库量」恒成立。</p>
     *
     * @param tenantId     租户（V1 固定 '1001'）
     * @param belongTypes  统计的品类（djs_belong_type，非空）
     * @param inExcluded   入库展示排除的 flow_type（{@code FlowDisplayScope.IN_EXCLUDED}，非空）
     * @param from         统计月首日（含）
     * @param toExclusive  次月首日（不含）
     * @return 品类 × 单位 × 入库量
     */
    @Select("<script>"
        + """
        SELECT pi.belong_type AS belongType,
               COALESCE(pi.product_unit, '') AS productUnit,
               COALESCE(SUM(f.change_quantity), 0) AS qty
        """
        + INBOUND_FROM
        + INBOUND_WHERE
        + """
        GROUP BY pi.belong_type, COALESCE(pi.product_unit, '')
        """
        + "</script>")
    List<CategoryUnitQtyRow> selectInboundByCategoryUnit(@Param("tenantId") String tenantId,
                                                         @Param("belongTypes") List<String> belongTypes,
                                                         @Param("inExcluded") List<String> inExcluded,
                                                         @Param("from") LocalDate from,
                                                         @Param("toExclusive") LocalDate toExclusive);

    /**
     * 当月「入库明细」：把计入入库量的那批流水<b>按产品</b>聚合（甲方 V6-R193「点入库明细弹出提示框，
     * 显示统计月所有入库产品的内容」）。
     *
     * <p>行集与 {@link #selectInboundByCategoryUnit} <b>严格同集合</b>（共用 {@link #INBOUND_FROM}
     * + {@link #INBOUND_WHERE}），只是把 GROUP BY 从「品类 × 单位」细化成「产品 × 单位」，
     * 聚合表达式仍是同一个 {@code SUM(change_quantity)} —— 故按单位 Σ qty 必然等于卡片上的入库量。</p>
     *
     * <p>产品名 / 规格用 {@code MAX()} 取而不进 GROUP BY：它们随产品 id 唯一，
     * 进 GROUP BY 只会在规格被改过的历史数据上把同一个产品劈成两行。</p>
     *
     * <p>量降序 —— 弹窗里最占量的产品排最前，甲方一眼看的就是这个；同量再按 productId 补成全序。</p>
     *
     * @param tenantId    租户
     * @param belongTypes 该品类卡涵盖的 belong_type（猪肉卡 = pork + white_bar）
     * @param inExcluded  入库展示排除的 flow_type
     * @param from        统计月首日（含）
     * @param toExclusive 次月首日（不含）
     * @return 产品 × 单位 × 入库量（量降序）
     */
    @Select("<script>"
        + """
        SELECT CAST(pi.id AS CHAR) AS productId,
               MAX(pi.product_name) AS productName,
               MAX(COALESCE(pi.product_spec, '')) AS productSpec,
               COALESCE(SUM(f.change_quantity), 0) AS qty,
               COALESCE(pi.product_unit, '') AS unit
        """
        + INBOUND_FROM
        + INBOUND_WHERE
        + """
        GROUP BY pi.id, COALESCE(pi.product_unit, '')
        ORDER BY qty DESC, productId
        """
        + "</script>")
    List<BoardStatProductRowVo> selectInboundDetailByProduct(@Param("tenantId") String tenantId,
                                                             @Param("belongTypes") List<String> belongTypes,
                                                             @Param("inExcluded") List<String> inExcluded,
                                                             @Param("from") LocalDate from,
                                                             @Param("toExclusive") LocalDate toExclusive);

    /**
     * 当月「生产量」：按品类 × 单位合计产品生产量（kg 取重量合计，计数单位取条数）。
     *
     * <p>筛选条件取自 {@link #PRODUCE_FROM} + {@link #PRODUCE_WHERE}，与
     * {@link #selectProduceDetailByProduct} 共用；明细弹窗上方的合计也直接调本方法。</p>
     *
     * @param tenantId    租户
     * @param belongTypes 统计的品类（非空）
     * @param from        统计月首日（含）
     * @param toExclusive 次月首日（不含）
     * @return 品类 × 单位 × 生产量
     */
    @Select("<script>"
        + "SELECT pi.belong_type AS belongType,\n"
        + "       " + PRODUCE_UNIT_EXPR + " AS productUnit,\n"
        + "       CASE WHEN LOWER(TRIM(MAX(" + PRODUCE_UNIT_EXPR + "))) IN " + PRODUCE_WEIGHT_UNITS + "\n"
        + "            THEN COALESCE(SUM(pp.product_weight), 0)\n"
        + "            ELSE COUNT(*) END AS qty\n"
        + PRODUCE_FROM
        + PRODUCE_WHERE
        + "GROUP BY pi.belong_type, " + PRODUCE_UNIT_EXPR + "\n"
        + "</script>")
    List<CategoryUnitQtyRow> selectProduceByCategoryUnit(@Param("tenantId") String tenantId,
                                                         @Param("belongTypes") List<String> belongTypes,
                                                         @Param("from") LocalDate from,
                                                         @Param("toExclusive") LocalDate toExclusive);

    /**
     * 当月「生产明细」：把计入生产量的那批生产记录<b>按产品</b>聚合（甲方 V6-R193「点生产明细弹出提示框，
     * 显示统计月所有生产产品的内容」）。
     *
     * <p>行集与 {@link #selectProduceByCategoryUnit} <b>严格同集合</b>（共用 {@link #PRODUCE_FROM}
     * + {@link #PRODUCE_WHERE}），GROUP BY 由「品类 × 单位」细化成「产品 × 单位」，
     * 量表达式照抄卡片那条 CASE（计重单位 SUM(product_weight) / 计数单位 COUNT(*)）
     * —— 分组内单位唯一，故按单位 Σ qty 必然等于卡片上的生产量。</p>
     *
     * <p>不再联原材料档案：弹窗只列「产品名称 / 规格 / 数量 / 环比」四列，本行耗了什么原料
     * 与「这个产品这个月生产了多少」不是一个问题，卡片上的原材料消耗另有口径
     * （见 {@link #selectMaterialConsumeByCategoryUnit}）。</p>
     *
     * @param tenantId    租户
     * @param belongTypes 该品类卡涵盖的 belong_type
     * @param from        统计月首日（含）
     * @param toExclusive 次月首日（不含）
     * @return 产品 × 单位 × 生产量（量降序）
     */
    @Select("<script>"
        + "SELECT CAST(pi.id AS CHAR) AS productId,\n"
        + "       MAX(pi.product_name) AS productName,\n"
        + "       MAX(COALESCE(pi.product_spec, pp.product_spec, '')) AS productSpec,\n"
        + "       CASE WHEN LOWER(TRIM(MAX(" + PRODUCE_UNIT_EXPR + "))) IN " + PRODUCE_WEIGHT_UNITS + "\n"
        + "            THEN COALESCE(SUM(pp.product_weight), 0)\n"
        + "            ELSE COUNT(*) END AS qty,\n"
        + "       " + PRODUCE_UNIT_EXPR + " AS unit\n"
        + PRODUCE_FROM
        + PRODUCE_WHERE
        + "GROUP BY pi.id, " + PRODUCE_UNIT_EXPR + "\n"
        + "ORDER BY qty DESC, productId\n"
        + "</script>")
    List<BoardStatProductRowVo> selectProduceDetailByProduct(@Param("tenantId") String tenantId,
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
