package org.dromara.djs.store.manage.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.store.manage.domain.vo.StoreManageDetailRowVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageProductCountRowVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageProductQtyRowVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageQtyRowVo;
import org.dromara.djs.warehouse.demand.core.StoreDemandStatusMapping;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理板块「门店管理」月度看板聚合 + 卡片下钻明细 Mapper（MGMT-MP-STORE-MONTH-001 / V6-R180，纯只读）。
 *
 * <h3>三个指标各自的数据源（甲方口径，一句一源）</h3>
 * <ul>
 *   <li><b>需求量</b> = 门店下单量 → {@code t_warehouse_demand_manage}（同表双视角，门店视角即下单单）。
 *       排除 {@code DELETED / CANCELLED / DRAFT}——与门店端列表口径
 *       {@link StoreDemandStatusMapping#EXCLUDED_STATUS_SQL} 逐条相同：草稿没提交给仓库、
 *       取消/删除的单不算下过。</li>
 *   <li><b>销售量</b> = 门店盘点记录的销售数量 + 赠送量 → {@code t_store_daily_ledger.sale_qty + gift_qty}。</li>
 *   <li><b>退回量</b> = 门店退回记录的退回量 → {@code t_store_return}，方向锁
 *       {@code store_to_warehouse}（门店退回仓库）。这与台账里那一列<b>叫「退回量」的</b>
 *       {@code wh_return_qty} 同口径；{@code customer_to_store} 是「退货量」（顾客退给门店），
 *       是另一件事，不进本统计。</li>
 * </ul>
 *
 * <h3>品类数</h3>
 * <p>「当月到店产品品类数」= 门店日台账当月 {@code inbound_qty > 0} 的产品去重数
 * （{@code COUNT(DISTINCT product_id)}）。到店口径与下方三指标同落在门店台账体系里，不跨源。</p>
 *
 * <h3>⚠️ 卡片与「明细」共用同一份筛选条件，不准复制两套</h3>
 * <p>业态卡上的三个数与「明细」下钻页的三列必须逐条对得上（甲方就是拿明细去核卡片的），
 * 所以三个指标的表 + 筛选各抽成一份常量，卡片聚合与明细分页共用：</p>
 * <ul>
 *   <li>需求量：{@link #DEMAND_FROM} + {@link #DEMAND_WHERE}；</li>
 *   <li>销售量：{@link #SALE_FROM} + {@link #SALE_WHERE}；</li>
 *   <li>退回量：{@link #RETURN_FROM} + {@link #RETURN_WHERE}。</li>
 * </ul>
 * <p><b>改一处即两处同时生效</b>；要加筛选条件只能改常量本身。明细页顶部的合计也不另写 SQL，
 * 直接复用卡片那三个聚合方法（见 service），所以「明细合计 == 卡片数字」是构造上成立的。</p>
 *
 * <h3>约定</h3>
 * <ul>
 *   <li>不走 BaseMapperPlus 自动 tenant 注入，所有 SQL 显式
 *       {@code tenant_id = #{tenantId} AND del_flag = '0'}（照 {@code StoreDashboardMapper} 范式）。</li>
 *   <li>{@code storeId} 为 null = 全部门店合计（{@code (#{storeId} IS NULL OR store_id = #{storeId})}）。</li>
 *   <li>月份区间一律左闭右开 {@code [monthStart, nextMonthStart)}，避免月末 datetime 时分秒漏行。</li>
 *   <li>单位分组直接 {@code GROUP BY p.product_unit}：库表 collation 是 {@code utf8mb4_0900_ai_ci}
 *       （大小写不敏感），{@code kg} / {@code Kg} 自然合并成一组，不需要再 UPPER()。</li>
 * </ul>
 *
 * @author djs
 * @since MGMT-MP-STORE-MONTH-001
 */
@Mapper
public interface StoreManageMapper {

    /** 需求量口径的表与联表（卡片聚合与明细分页共用）。 */
    String DEMAND_FROM =
        "  FROM t_warehouse_demand_manage d "
            + "  JOIN t_warehouse_product_info p ON d.product_id = p.id ";

    /** 需求量口径的筛选条件（卡片聚合与明细分页共用，改这里两边同时生效）。 */
    String DEMAND_WHERE =
        " WHERE d.tenant_id = #{tenantId} "
            + "   AND d.del_flag = '0' "
            + "   AND p.del_flag = '0' "
            + "   AND d.store_id IS NOT NULL "
            + "   AND d.demand_status NOT IN " + StoreDemandStatusMapping.EXCLUDED_STATUS_SQL + " "
            + "   AND d.demand_date &gt;= #{monthStart} "
            + "   AND d.demand_date &lt; #{nextStart} "
            + "   AND (#{storeId} IS NULL OR d.store_id = #{storeId}) "
            + "   AND p.belong_type IN "
            + "   <foreach collection='belongTypes' item='bt' open='(' separator=',' close=')'>#{bt}</foreach>";

    /** 销售量口径的表与联表（卡片聚合与明细分页共用）。 */
    String SALE_FROM =
        "  FROM t_store_daily_ledger l "
            + "  JOIN t_warehouse_product_info p ON l.product_id = p.id ";

    /** 销售量口径的筛选条件（卡片聚合与明细分页共用，改这里两边同时生效）。 */
    String SALE_WHERE =
        " WHERE l.tenant_id = #{tenantId} "
            + "   AND l.del_flag = '0' "
            + "   AND p.del_flag = '0' "
            + "   AND l.ledger_date &gt;= #{monthStart} "
            + "   AND l.ledger_date &lt; #{nextStart} "
            + "   AND (#{storeId} IS NULL OR l.store_id = #{storeId}) "
            + "   AND p.belong_type IN "
            + "   <foreach collection='belongTypes' item='bt' open='(' separator=',' close=')'>#{bt}</foreach>";

    /** 退回量口径的表与联表（卡片聚合与明细分页共用）。 */
    String RETURN_FROM =
        "  FROM t_store_return r "
            + "  JOIN t_warehouse_product_info p ON r.product_id = p.id ";

    /** 退回量口径的筛选条件（卡片聚合与明细分页共用，改这里两边同时生效）。 */
    String RETURN_WHERE =
        " WHERE r.tenant_id = #{tenantId} "
            + "   AND r.del_flag = '0' "
            + "   AND p.del_flag = '0' "
            + "   AND r.return_direction = 'store_to_warehouse' "
            + "   AND r.store_id IS NOT NULL "
            + "   AND r.return_date &gt;= #{monthStart} "
            + "   AND r.return_date &lt; #{nextStart} "
            + "   AND (#{storeId} IS NULL OR r.store_id = #{storeId}) "
            + "   AND p.belong_type IN "
            + "   <foreach collection='belongTypes' item='bt' open='(' separator=',' close=')'>#{bt}</foreach>";

    /** 明细子查询的产品维度分组键（三个 UNION ALL 分支写法必须完全一致，否则同一产品会裂成多行）。 */
    String DETAIL_PRODUCT_KEYS =
        " p.id AS productId, p.product_name AS productName, p.product_spec AS productSpec, p.product_unit AS unit ";

    /** 明细子查询的 GROUP BY（与 {@link #DETAIL_PRODUCT_KEYS} 逐列对应，MySQL ONLY_FULL_GROUP_BY 要求）。 */
    String DETAIL_PRODUCT_GROUP_BY =
        " GROUP BY p.id, p.product_name, p.product_spec, p.product_unit ";

    /**
     * 明细「上月同产品」聚合的产品白名单（只查当前页那几个产品，不整月全表拉）。
     *
     * <p>拼在各 {@code *_WHERE} 之后，三支分支写法一致。</p>
     */
    String DETAIL_PRODUCT_ID_FILTER =
        "   AND p.id IN "
            + "   <foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach> ";

    /** 明细行「当月三个量全为 0 不出行」的过滤（甲方口径 D-0045，放外层保证分页 total 也不含空行）。 */
    String DETAIL_NON_EMPTY_WHERE =
        " WHERE (t.demandQty != 0 OR t.saleQty != 0 OR t.returnQty != 0) ";

    /**
     * 当月到店产品品类数（按 belong_type 分组的去重产品数）。
     *
     * @param tenantId    租户
     * @param storeId     门店 ID（可空，null 时全部门店）
     * @param monthStart  月首日（含）
     * @param nextStart   下月首日（不含）
     * @param belongTypes 业态白名单（非空）
     * @return 业态 : 去重产品数，无数据返空列表
     */
    @Select("<script>"
        + "SELECT p.belong_type AS belongType, "
        + "       CAST(COUNT(DISTINCT l.product_id) AS SIGNED) AS productCount "
        + "  FROM t_store_daily_ledger l "
        + "  JOIN t_warehouse_product_info p ON l.product_id = p.id "
        + " WHERE l.tenant_id = #{tenantId} "
        + "   AND l.del_flag = '0' "
        + "   AND p.del_flag = '0' "
        + "   AND l.inbound_qty &gt; 0 "
        + "   AND l.ledger_date &gt;= #{monthStart} "
        + "   AND l.ledger_date &lt; #{nextStart} "
        + "   AND (#{storeId} IS NULL OR l.store_id = #{storeId}) "
        + "   AND p.belong_type IN "
        + "   <foreach collection='belongTypes' item='bt' open='(' separator=',' close=')'>#{bt}</foreach>"
        + " GROUP BY p.belong_type"
        + "</script>")
    List<StoreManageProductCountRowVo> countArrivedProducts(@Param("tenantId") String tenantId,
                                                            @Param("storeId") Long storeId,
                                                            @Param("monthStart") LocalDate monthStart,
                                                            @Param("nextStart") LocalDate nextStart,
                                                            @Param("belongTypes") List<String> belongTypes);

    /**
     * 当月需求量（门店下单量）：按 belong_type × product_unit 汇总。
     *
     * <p>单位取<b>产品主数据</b> {@code p.product_unit} 而不是需求单冗余列 {@code d.product_unit}——
     * 三个指标必须用同一把单位尺子，否则同一产品在需求行落「kg」、在销售行落「份」，
     * 一张卡里会裂成两行谁也对不上。</p>
     *
     * <p>筛选条件取自 {@link #DEMAND_FROM} + {@link #DEMAND_WHERE}，与明细分页
     * {@link #selectProductDetailPage} 共用；明细页的合计也直接调本方法。</p>
     *
     * @param tenantId    租户
     * @param storeId     门店 ID（可空）
     * @param monthStart  月首日（含）
     * @param nextStart   下月首日（不含）
     * @param belongTypes 业态白名单（非空）
     * @return (业态, 单位) : 需求量合计，无数据返空列表
     */
    @Select("<script>"
        + "SELECT p.belong_type AS belongType, p.product_unit AS unit, "
        + "       COALESCE(SUM(d.demand_quantity), 0) AS qty "
        + DEMAND_FROM
        + DEMAND_WHERE
        + " GROUP BY p.belong_type, p.product_unit"
        + "</script>")
    List<StoreManageQtyRowVo> sumDemandQty(@Param("tenantId") String tenantId,
                                           @Param("storeId") Long storeId,
                                           @Param("monthStart") LocalDate monthStart,
                                           @Param("nextStart") LocalDate nextStart,
                                           @Param("belongTypes") List<String> belongTypes);

    /**
     * 当月销售量（门店盘点 sale_qty + gift_qty）：按 belong_type × product_unit 汇总。
     *
     * <p>筛选条件取自 {@link #SALE_FROM} + {@link #SALE_WHERE}，与明细分页共用。</p>
     *
     * @param tenantId    租户
     * @param storeId     门店 ID（可空）
     * @param monthStart  月首日（含）
     * @param nextStart   下月首日（不含）
     * @param belongTypes 业态白名单（非空）
     * @return (业态, 单位) : 销售量合计，无数据返空列表
     */
    @Select("<script>"
        + "SELECT p.belong_type AS belongType, p.product_unit AS unit, "
        + "       COALESCE(SUM(l.sale_qty + l.gift_qty), 0) AS qty "
        + SALE_FROM
        + SALE_WHERE
        + " GROUP BY p.belong_type, p.product_unit"
        + "</script>")
    List<StoreManageQtyRowVo> sumSaleQty(@Param("tenantId") String tenantId,
                                         @Param("storeId") Long storeId,
                                         @Param("monthStart") LocalDate monthStart,
                                         @Param("nextStart") LocalDate nextStart,
                                         @Param("belongTypes") List<String> belongTypes);

    /**
     * 当月退回量（门店退回记录，方向 store_to_warehouse）：按 belong_type × product_unit 汇总。
     *
     * <p>筛选条件取自 {@link #RETURN_FROM} + {@link #RETURN_WHERE}，与明细分页共用。</p>
     *
     * @param tenantId    租户
     * @param storeId     门店 ID（可空）
     * @param monthStart  月首日（含）
     * @param nextStart   下月首日（不含）
     * @param belongTypes 业态白名单（非空）
     * @return (业态, 单位) : 退回量合计，无数据返空列表
     */
    @Select("<script>"
        + "SELECT p.belong_type AS belongType, p.product_unit AS unit, "
        + "       COALESCE(SUM(r.return_quantity), 0) AS qty "
        + RETURN_FROM
        + RETURN_WHERE
        + " GROUP BY p.belong_type, p.product_unit"
        + "</script>")
    List<StoreManageQtyRowVo> sumReturnQty(@Param("tenantId") String tenantId,
                                           @Param("storeId") Long storeId,
                                           @Param("monthStart") LocalDate monthStart,
                                           @Param("nextStart") LocalDate nextStart,
                                           @Param("belongTypes") List<String> belongTypes);

    /**
     * 当月业态卡「明细」下钻：把卡片那三个数按<b>产品</b>拆开（V6-R180）。
     *
     * <h3>三源全外合并靠 UNION ALL</h3>
     * <p>需求 / 销售 / 退回是三张互不相干的表，某产品可能只在退回里出现（本月没下单也没卖，
     * 但退了货）——这行<b>必须出现</b>，否则甲方在卡片上看到退回量却在明细里找不着它。
     * 三个分支各自按产品聚合后 UNION ALL，外层再按 productId 归并，缺的量自然是 0，
     * 等价于三表全外连接，且不需要 MySQL 不支持的 FULL OUTER JOIN。</p>
     *
     * <p>三个分支的表与筛选逐字取自 {@code *_FROM} / {@code *_WHERE} 常量，与业态卡上的三条聚合
     * SQL 同一份；产品维度的键与 GROUP BY 取自 {@link #DETAIL_PRODUCT_KEYS} /
     * {@link #DETAIL_PRODUCT_GROUP_BY}，三分支写法完全一致，否则同一产品会在合并层裂成多行。</p>
     *
     * <p>聚合放子查询、外层只排序：外层不带 GROUP BY，MyBatis-Plus 的自动 count 就是
     * {@code SELECT COUNT(*) FROM (聚合) }，分页总数 = 合并后的产品行数而不是明细条数
     * （与 {@code InoutStatMapper} 同做法）。ORDER BY 末位补 {@code productId} 凑成全序，
     * 否则同需求量的两行在翻页时可能重复 / 漏出。</p>
     *
     * <p>产品名 / 规格 / 单位在合并层取 {@code MAX()}：它们由 productId 函数决定，
     * 组内恒为同一个值，取 MAX 即该值本身（同时满足 MySQL 8 的 ONLY_FULL_GROUP_BY）。</p>
     *
     * <h3>当月三个量全为 0 的产品不出行（甲方口径 D-0045）</h3>
     * <p>过滤写在<b>外层</b> {@link #DETAIL_NON_EMPTY_WHERE} 而不是前端 filter：MyBatis-Plus 的自动
     * count 是 {@code SELECT COUNT(*) FROM (聚合) t WHERE …}，同一个 WHERE 会一起进 count，
     * 分页 total 与「已到底」判断才不会把被过滤掉的空行算进去。</p>
     *
     * @param page        分页参数
     * @param tenantId    租户
     * @param storeId     门店 ID（可空，null = 全部门店合计）
     * @param monthStart  月首日（含）
     * @param nextStart   下月首日（不含）
     * @param belongTypes 该业态卡涵盖的 belong_type（猪肉卡 = pork + white_bar）
     * @return 按产品拆的明细行（需求量降序 → 产品名 → productId）
     */
    @Select("<script>"
        + "SELECT t.productId, t.productName, t.productSpec, t.unit, "
        + "       t.demandQty, t.saleQty, t.returnQty "
        + "  FROM ( "
        + "        SELECT g.productId AS productId, "
        + "               MAX(g.productName) AS productName, "
        + "               MAX(g.productSpec) AS productSpec, "
        + "               MAX(g.unit) AS unit, "
        + "               SUM(g.demandQty) AS demandQty, "
        + "               SUM(g.saleQty)   AS saleQty, "
        + "               SUM(g.returnQty) AS returnQty "
        + "          FROM ( "
        + "                SELECT " + DETAIL_PRODUCT_KEYS + ", "
        + "                       COALESCE(SUM(d.demand_quantity), 0) AS demandQty, "
        + "                       0 AS saleQty, 0 AS returnQty "
        + DEMAND_FROM
        + DEMAND_WHERE
        + DETAIL_PRODUCT_GROUP_BY
        + "                UNION ALL "
        + "                SELECT " + DETAIL_PRODUCT_KEYS + ", "
        + "                       0 AS demandQty, "
        + "                       COALESCE(SUM(l.sale_qty + l.gift_qty), 0) AS saleQty, "
        + "                       0 AS returnQty "
        + SALE_FROM
        + SALE_WHERE
        + DETAIL_PRODUCT_GROUP_BY
        + "                UNION ALL "
        + "                SELECT " + DETAIL_PRODUCT_KEYS + ", "
        + "                       0 AS demandQty, 0 AS saleQty, "
        + "                       COALESCE(SUM(r.return_quantity), 0) AS returnQty "
        + RETURN_FROM
        + RETURN_WHERE
        + DETAIL_PRODUCT_GROUP_BY
        + "               ) g "
        + "         GROUP BY g.productId "
        + "       ) t "
        + DETAIL_NON_EMPTY_WHERE
        + " ORDER BY t.demandQty DESC, t.productName, t.productId"
        + "</script>")
    IPage<StoreManageDetailRowVo> selectProductDetailPage(IPage<StoreManageDetailRowVo> page,
                                                          @Param("tenantId") String tenantId,
                                                          @Param("storeId") Long storeId,
                                                          @Param("monthStart") LocalDate monthStart,
                                                          @Param("nextStart") LocalDate nextStart,
                                                          @Param("belongTypes") List<String> belongTypes);

    /**
     * 指定产品在指定月份区间的三个量（明细行逐产品环比的<b>上月基数</b>，V6-R209）。
     *
     * <p>与 {@link #selectProductDetailPage} 同一套三源 UNION ALL：表与筛选逐字取自
     * {@code *_FROM} / {@code *_WHERE} 常量，只多一条 {@link #DETAIL_PRODUCT_ID_FILTER}
     * 把范围收窄到当前页那几个产品——环比的分母必须和分子同口径，否则两个数不可比。</p>
     *
     * <p>不带 {@code DETAIL_NON_EMPTY_WHERE}：上月为 0 是合法基数（service 据此判
     * {@code hasBase=false} 渲染黑色 0.00%），过滤掉反而分不清「上月是 0」和「查漏了」。</p>
     *
     * @param tenantId    租户
     * @param storeId     门店 ID（可空，null = 全部门店合计）
     * @param monthStart  区间首日（含）——环比场景传上月 1 日
     * @param nextStart   区间次日（不含）——环比场景传本月 1 日
     * @param belongTypes 该业态卡涵盖的 belong_type
     * @param productIds  只统计这几个产品（非空，空列表会拼出非法 SQL，调用方负责短路）
     * @return 产品 : 三个量，某产品该区间无任何记录时不出行（service 兜 0）
     */
    @Select("<script>"
        + "SELECT g.productId AS productId, "
        + "       SUM(g.demandQty) AS demandQty, "
        + "       SUM(g.saleQty)   AS saleQty, "
        + "       SUM(g.returnQty) AS returnQty "
        + "  FROM ( "
        + "        SELECT " + DETAIL_PRODUCT_KEYS + ", "
        + "               COALESCE(SUM(d.demand_quantity), 0) AS demandQty, "
        + "               0 AS saleQty, 0 AS returnQty "
        + DEMAND_FROM
        + DEMAND_WHERE
        + DETAIL_PRODUCT_ID_FILTER
        + DETAIL_PRODUCT_GROUP_BY
        + "        UNION ALL "
        + "        SELECT " + DETAIL_PRODUCT_KEYS + ", "
        + "               0 AS demandQty, "
        + "               COALESCE(SUM(l.sale_qty + l.gift_qty), 0) AS saleQty, "
        + "               0 AS returnQty "
        + SALE_FROM
        + SALE_WHERE
        + DETAIL_PRODUCT_ID_FILTER
        + DETAIL_PRODUCT_GROUP_BY
        + "        UNION ALL "
        + "        SELECT " + DETAIL_PRODUCT_KEYS + ", "
        + "               0 AS demandQty, 0 AS saleQty, "
        + "               COALESCE(SUM(r.return_quantity), 0) AS returnQty "
        + RETURN_FROM
        + RETURN_WHERE
        + DETAIL_PRODUCT_ID_FILTER
        + DETAIL_PRODUCT_GROUP_BY
        + "       ) g "
        + " GROUP BY g.productId"
        + "</script>")
    List<StoreManageProductQtyRowVo> selectProductMonthSums(@Param("tenantId") String tenantId,
                                                            @Param("storeId") Long storeId,
                                                            @Param("monthStart") LocalDate monthStart,
                                                            @Param("nextStart") LocalDate nextStart,
                                                            @Param("belongTypes") List<String> belongTypes,
                                                            @Param("productIds") List<Long> productIds);

}
