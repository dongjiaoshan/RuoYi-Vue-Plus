package org.dromara.djs.store.manage.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.store.manage.domain.vo.StoreManageProductCountRowVo;
import org.dromara.djs.store.manage.domain.vo.StoreManageQtyRowVo;
import org.dromara.djs.warehouse.demand.core.StoreDemandStatusMapping;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理板块「门店管理」月度看板聚合 Mapper（MGMT-MP-STORE-MONTH-001，纯只读）。
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
        + "  FROM t_warehouse_demand_manage d "
        + "  JOIN t_warehouse_product_info p ON d.product_id = p.id "
        + " WHERE d.tenant_id = #{tenantId} "
        + "   AND d.del_flag = '0' "
        + "   AND p.del_flag = '0' "
        + "   AND d.store_id IS NOT NULL "
        + "   AND d.demand_status NOT IN " + StoreDemandStatusMapping.EXCLUDED_STATUS_SQL + " "
        + "   AND d.demand_date &gt;= #{monthStart} "
        + "   AND d.demand_date &lt; #{nextStart} "
        + "   AND (#{storeId} IS NULL OR d.store_id = #{storeId}) "
        + "   AND p.belong_type IN "
        + "   <foreach collection='belongTypes' item='bt' open='(' separator=',' close=')'>#{bt}</foreach>"
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
        + "  FROM t_store_daily_ledger l "
        + "  JOIN t_warehouse_product_info p ON l.product_id = p.id "
        + " WHERE l.tenant_id = #{tenantId} "
        + "   AND l.del_flag = '0' "
        + "   AND p.del_flag = '0' "
        + "   AND l.ledger_date &gt;= #{monthStart} "
        + "   AND l.ledger_date &lt; #{nextStart} "
        + "   AND (#{storeId} IS NULL OR l.store_id = #{storeId}) "
        + "   AND p.belong_type IN "
        + "   <foreach collection='belongTypes' item='bt' open='(' separator=',' close=')'>#{bt}</foreach>"
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
        + "  FROM t_store_return r "
        + "  JOIN t_warehouse_product_info p ON r.product_id = p.id "
        + " WHERE r.tenant_id = #{tenantId} "
        + "   AND r.del_flag = '0' "
        + "   AND p.del_flag = '0' "
        + "   AND r.return_direction = 'store_to_warehouse' "
        + "   AND r.store_id IS NOT NULL "
        + "   AND r.return_date &gt;= #{monthStart} "
        + "   AND r.return_date &lt; #{nextStart} "
        + "   AND (#{storeId} IS NULL OR r.store_id = #{storeId}) "
        + "   AND p.belong_type IN "
        + "   <foreach collection='belongTypes' item='bt' open='(' separator=',' close=')'>#{bt}</foreach>"
        + " GROUP BY p.belong_type, p.product_unit"
        + "</script>")
    List<StoreManageQtyRowVo> sumReturnQty(@Param("tenantId") String tenantId,
                                           @Param("storeId") Long storeId,
                                           @Param("monthStart") LocalDate monthStart,
                                           @Param("nextStart") LocalDate nextStart,
                                           @Param("belongTypes") List<String> belongTypes);

}
