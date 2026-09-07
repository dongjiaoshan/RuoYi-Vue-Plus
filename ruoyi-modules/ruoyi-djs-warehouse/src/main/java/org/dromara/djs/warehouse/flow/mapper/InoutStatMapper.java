package org.dromara.djs.warehouse.flow.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.flow.domain.query.InoutStatDetailQuery;
import org.dromara.djs.warehouse.flow.domain.query.InoutStatQuery;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatInDetailVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatInVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatOutDetailVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatOutVo;

import java.util.List;

/**
 * 出入库统计聚合 + 行内下钻明细 Mapper（V6-R167 / R186 / R187）。
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
 * <h3>⚠️ 聚合、导出、行内明细三者共用同一份 FROM / WHERE，不准复制多套</h3>
 * <p>甲方是拿导出的表核对页面、再点「查看详情」核对汇总行的，三条链路的行集必须严格同集合：</p>
 * <ul>
 *   <li>入库：{@link #IN_FROM} + {@link #IN_WHERE}（+ 明细再叠 {@link #IN_GROUP_KEY}）</li>
 *   <li>出库：{@link #OUT_FROM} + {@link #OUT_WHERE}（+ 明细再叠 {@link #OUT_GROUP_KEY}）</li>
 * </ul>
 * <p><b>要加筛选条件只能改常量本身</b>，不准在某一个方法上单独加 WHERE；
 * 于是「明细逐条求和 == 汇总行的量」是构造上成立的，不靠人对。
 * 文本块是编译期常量，可直接作 {@code @Select} 的值。</p>
 *
 * <p>分页与导出也共用同一份 SQL：日期区间放开后行数可能上万，列表必须分页、导出必须全量，
 * 两份 SQL 各写一遍迟早改歪一边，届时「导出比列表多几行」这种问题极难查。</p>
 *
 * @author djs
 * @since V6-R167
 */
@Mapper
public interface InoutStatMapper {

    /** 入库口径的表与联表（聚合与明细共用；明细在此之后再 LEFT JOIN 记录人）。 */
    String IN_FROM = """
        FROM t_warehouse_stock_flow f
        JOIN t_warehouse_product_info pi ON pi.id = f.product_id AND pi.del_flag = '0'
        LEFT JOIN t_md_supplier sp ON sp.id = f.supplier_id AND sp.del_flag = '0'
        """;

    /**
     * 入库口径的筛选条件（聚合与明细共用，改这里两边同时生效）。
     *
     * <p>参数名固定 {@code tenantId / query / inExcluded}，共用它的方法签名必须原样带这几个
     * {@code @Param}；{@code query} 至少要是 {@link InoutStatQuery}（明细传它的子类）。</p>
     *
     * <p>日期区间终点写 {@code < dateTo + 1 天} 而不是 {@code <= dateTo}：
     * {@code flow_date} 是 DATETIME，写 {@code <=} 会漏掉当天带时分秒的流水。</p>
     *
     * <p>甲方「供应商为空的统计到一起」→ 无供应商 / 供应商档案已删的流水都落到
     * {@code COALESCE(sp.supplier_name, '')} 的空桶（service 把该行 supplierName 兜成「无供应商」）。
     * {@code noSupplier} 为真时只留这一桶，判据必须是<b>联出来的名字</b>而不是 {@code f.supplier_id IS NULL}
     * ——档案已删的流水 supplier_id 有值但联不出名字，同样属于这一桶。</p>
     */
    String IN_WHERE = """
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
        """;

    /**
     * 入库明细锁定到某一个汇总行的分组键（仅明细用，叠在 {@link #IN_WHERE} 之后）。
     *
     * <p>只钉 <b>产品编码 + 入库方式 + 供应商</b> 三项即可：产品编码在
     * {@code t_warehouse_product_info} 上是租户内唯一键（{@code uk_product_id}），
     * 名称 / 类型 / 规格 / 单位都由它函数决定，再钉一遍是冗余。</p>
     *
     * <p>供应商用 {@code COALESCE(...) = COALESCE(#{...}, '')} 双向兜空：
     * 「无供应商」那一桶前端传 null 或空串都落在同一分支，不受 Spring 空串绑定差异影响。</p>
     */
    String IN_GROUP_KEY = """
          AND pi.product_id = #{query.productCode}
          AND f.flow_type = #{query.flowType}
          AND COALESCE(sp.supplier_name, '') = COALESCE(#{query.supplierName}, '')
        <if test="query.operatorId != null">
          AND f.operator_id = #{query.operatorId}
        </if>
        """;

    /** 出库口径的表与联表（聚合与明细共用；明细在此之后再 LEFT JOIN 记录人）。 */
    String OUT_FROM = """
        FROM t_warehouse_stock_flow f
        JOIN t_warehouse_product_info pi ON pi.id = f.product_id AND pi.del_flag = '0'
        """;

    /**
     * 出库口径的筛选条件（聚合与明细共用，改这里两边同时生效）。
     *
     * <p>参数名固定 {@code tenantId / query / outExcluded}。日期区间的半开右端理由同
     * {@link #IN_WHERE}。</p>
     */
    String OUT_WHERE = """
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
        """;

    /**
     * 出库明细锁定到某一个汇总行的分组键（仅明细用，叠在 {@link #OUT_WHERE} 之后）。
     *
     * <p>出库去向用 {@code COALESCE(...) = COALESCE(#{...}, '')} 双向兜空 ——
     * 「未指定」那一桶前端传 null 或空串都落在同一分支。</p>
     */
    String OUT_GROUP_KEY = """
          AND pi.product_id = #{query.productCode}
          AND COALESCE(f.stock_out_dest, '') = COALESCE(#{query.outDest}, '')
        <if test="query.operatorId != null">
          AND f.operator_id = #{query.operatorId}
        </if>
        """;

    /** 明细行的记录人联表（入库 / 出库明细共用；档案被删也照出行，只是姓名为空）。 */
    String OPERATOR_JOIN = """
        LEFT JOIN sys_user u ON u.user_id = f.operator_id AND u.del_flag = '0'
        """;

    /**
     * 入库统计聚合 SQL：按 <b>产品编码 × 产品名称 × 产品类型 × 规格 × 单位 × 入库方式 × 供应商</b> 聚合。
     *
     * <p><b>分组键含产品编码</b>（{@code pi.product_id} 业务码，不是 {@code f.product_id} 外键）：
     * 甲方 row187 要产品编码当列表第一列，即把编码当作这张表的身份列；一行只能挂一个编码，
     * 所以 name + type + spec + unit 全同的重复档案（本库存在 11 组）各占一行、量各归各。
     * 键里同时留着名称 / 类型 / 规格 / 单位，是为了让 SELECT 与 ORDER BY 能直接取到它们
     * （MySQL 8 {@code ONLY_FULL_GROUP_BY}），编码唯一 ⇒ 这几列不会额外裂行。</p>
     *
     * <p>可空的规格 / 单位 / 供应商名一律 {@code COALESCE(..., '')} 归一后再分组：NULL 在 GROUP BY 里
     * 虽同组，但 SELECT 与 ORDER BY 拿 NULL 会让 service 的空值兜底与排序都不稳定。</p>
     *
     * <p>聚合放子查询、外层只排序：外层不带 GROUP BY，MyBatis-Plus 的自动 count 就是
     * {@code SELECT COUNT(*) FROM (聚合) g}，分页总数 = 聚合后的行数而不是流水条数
     * （与 {@code FeedLogMapper.selectDailyPage} 同做法）。ORDER BY 拿<b>整个分组键</b>排，
     * 键唯一 ⇒ 排序全序 ⇒ 翻页不会出现某行既在第 1 页又在第 2 页。</p>
     *
     * <p>MySQL 8 {@code ONLY_FULL_GROUP_BY}：SELECT 里每个非聚合列都在 GROUP BY 里，漏一列查询直接 500；
     * 用了 COALESCE 的列，SELECT 与 GROUP BY 必须是<b>同一个表达式</b>，写裸列会被判非法。</p>
     */
    String IN_STAT_SQL = "<script>"
        + """
        SELECT g.productCode, g.productName, g.productType, g.productSpec, g.productUnit,
               g.flowType, g.supplierName, g.supplierName AS supplierKey, g.inboundQty
        FROM (
            SELECT pi.product_id    AS productCode,
                   pi.product_name  AS productName,
                   pi.product_type  AS productType,
                   COALESCE(pi.product_spec, '') AS productSpec,
                   COALESCE(pi.product_unit, '') AS productUnit,
                   f.flow_type      AS flowType,
                   COALESCE(sp.supplier_name, '') AS supplierName,
                   COALESCE(SUM(f.change_quantity), 0) AS inboundQty
        """
        + IN_FROM
        + IN_WHERE
        + """
            GROUP BY pi.product_id, pi.product_name, pi.product_type, COALESCE(pi.product_spec, ''),
                     COALESCE(pi.product_unit, ''), f.flow_type, COALESCE(sp.supplier_name, '')
        ) g
        ORDER BY g.productName, g.productCode, g.productType, g.productSpec, g.productUnit,
                 g.flowType, g.supplierName
        """
        + "</script>";

    /**
     * 出库统计聚合 SQL：按 <b>产品编码 × 产品名称 × 产品类型 × 规格 × 单位 × 出库去向</b> 聚合。
     *
     * <p>分组键含产品编码、子查询分页、ORDER BY 全序的理由与 {@link #IN_STAT_SQL} 完全一致。
     * 出库去向为空的流水归到 {@code COALESCE(f.stock_out_dest, '')} 同一桶
     * （service 把该行 outDestName 兜成「未指定」）。</p>
     */
    String OUT_STAT_SQL = "<script>"
        + """
        SELECT g.productCode, g.productName, g.productType, g.productSpec, g.productUnit,
               g.stockOutDest, g.outboundQty
        FROM (
            SELECT pi.product_id   AS productCode,
                   pi.product_name AS productName,
                   pi.product_type AS productType,
                   COALESCE(pi.product_spec, '') AS productSpec,
                   COALESCE(pi.product_unit, '') AS productUnit,
                   COALESCE(f.stock_out_dest, '') AS stockOutDest,
                   COALESCE(SUM(f.change_quantity), 0) AS outboundQty
        """
        + OUT_FROM
        + OUT_WHERE
        + """
            GROUP BY pi.product_id, pi.product_name, pi.product_type, COALESCE(pi.product_spec, ''),
                     COALESCE(pi.product_unit, ''), COALESCE(f.stock_out_dest, '')
        ) g
        ORDER BY g.productName, g.productCode, g.productType, g.productSpec, g.productUnit,
                 g.stockOutDest
        """
        + "</script>";

    /**
     * 入库明细 SQL（V6-R186 「查看详情」）：某个汇总行在日期区间内的逐条入库流水。
     *
     * <p>行集与 {@link #IN_STAT_SQL} <b>严格同集合</b>（共用 {@link #IN_FROM} + {@link #IN_WHERE}，
     * 再叠 {@link #IN_GROUP_KEY} 钉到那一行），逐行 {@code inboundQty} 就是聚合里被 SUM 的那一列
     * {@code change_quantity}，故明细求和必然等于汇总行的入库量。</p>
     *
     * <p>排序按业务日期倒序；同秒的行再按 {@code f.id} 倒序补成全序，
     * 否则翻页时同一行可能既在第 1 页又在第 2 页。</p>
     */
    String IN_DETAIL_SQL = "<script>"
        + """
        SELECT f.flow_date       AS flowDate,
               pi.product_id     AS productCode,
               pi.product_name   AS productName,
               COALESCE(pi.product_spec, '') AS productSpec,
               COALESCE(pi.product_unit, '') AS productUnit,
               f.change_quantity AS inboundQty,
               COALESCE(sp.supplier_name, '') AS supplierName,
               u.nick_name       AS operatorName,
               f.create_time     AS createTime
        """
        + IN_FROM
        + OPERATOR_JOIN
        + IN_WHERE
        + IN_GROUP_KEY
        + """
        ORDER BY f.flow_date DESC, f.id DESC
        """
        + "</script>";

    /**
     * 出库明细 SQL（V6-R186 「查看详情」）：某个汇总行在日期区间内的逐条出库流水。
     *
     * <p>共用口径与 {@link #IN_DETAIL_SQL} 同理（{@link #OUT_FROM} + {@link #OUT_WHERE}
     * + {@link #OUT_GROUP_KEY}）。出库去向不再单独查字典：明细与汇总行同一个桶，
     * 由 service 用同一份字典翻译落到 {@code outDestName}。</p>
     */
    String OUT_DETAIL_SQL = "<script>"
        + """
        SELECT f.flow_date       AS flowDate,
               pi.product_id     AS productCode,
               pi.product_name   AS productName,
               COALESCE(pi.product_spec, '') AS productSpec,
               COALESCE(pi.product_unit, '') AS productUnit,
               f.change_quantity AS outboundQty,
               COALESCE(f.stock_out_dest, '') AS stockOutDest,
               u.nick_name       AS operatorName,
               f.create_time     AS createTime
        """
        + OUT_FROM
        + OPERATOR_JOIN
        + OUT_WHERE
        + OUT_GROUP_KEY
        + """
        ORDER BY f.flow_date DESC, f.id DESC
        """
        + "</script>";

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

    /**
     * 入库明细分页（V6-R186 入库统计行「查看详情」）。
     *
     * @param page       分页参数
     * @param tenantId   租户
     * @param query      分组键 + 明细自己的日期区间 / 记录人筛选
     * @param inExcluded 入库展示排除的 flow_type（非空，与汇总同一份）
     * @return 入库明细行（业务日期倒序）
     */
    @Select(IN_DETAIL_SQL)
    IPage<InoutStatInDetailVo> selectInDetailPage(IPage<InoutStatInDetailVo> page,
                                                  @Param("tenantId") String tenantId,
                                                  @Param("query") InoutStatDetailQuery query,
                                                  @Param("inExcluded") List<String> inExcluded);

    /**
     * 入库明细全量（V6-R186 明细导出：与弹窗表格逐列一致，只是不分页）。
     *
     * @param tenantId   租户
     * @param query      分组键 + 明细筛选
     * @param inExcluded 入库展示排除的 flow_type（非空）
     * @return 入库明细行
     */
    @Select(IN_DETAIL_SQL)
    List<InoutStatInDetailVo> selectInDetailList(@Param("tenantId") String tenantId,
                                                 @Param("query") InoutStatDetailQuery query,
                                                 @Param("inExcluded") List<String> inExcluded);

    /**
     * 出库明细分页（V6-R186 出库统计行「查看详情」）。
     *
     * @param page        分页参数
     * @param tenantId    租户
     * @param query       分组键 + 明细自己的日期区间 / 记录人筛选
     * @param outExcluded 出库展示排除的 flow_type（非空，与汇总同一份）
     * @return 出库明细行（业务日期倒序）
     */
    @Select(OUT_DETAIL_SQL)
    IPage<InoutStatOutDetailVo> selectOutDetailPage(IPage<InoutStatOutDetailVo> page,
                                                    @Param("tenantId") String tenantId,
                                                    @Param("query") InoutStatDetailQuery query,
                                                    @Param("outExcluded") List<String> outExcluded);

    /**
     * 出库明细全量（V6-R186 明细导出）。
     *
     * @param tenantId    租户
     * @param query       分组键 + 明细筛选
     * @param outExcluded 出库展示排除的 flow_type（非空）
     * @return 出库明细行
     */
    @Select(OUT_DETAIL_SQL)
    List<InoutStatOutDetailVo> selectOutDetailList(@Param("tenantId") String tenantId,
                                                   @Param("query") InoutStatDetailQuery query,
                                                   @Param("outExcluded") List<String> outExcluded);
}
