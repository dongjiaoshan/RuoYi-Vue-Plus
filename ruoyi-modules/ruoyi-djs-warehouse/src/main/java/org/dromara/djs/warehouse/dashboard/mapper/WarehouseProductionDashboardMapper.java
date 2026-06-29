package org.dromara.djs.warehouse.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.djs.warehouse.stat.domain.WarehouseCroppRecord;
import org.dromara.djs.warehouse.stat.domain.WarehouseIndicatorRecord;
import org.dromara.djs.warehouse.stat.domain.WarehouseMonthlyRecord;

import java.time.LocalDate;
import java.util.List;

/**
 * 仓库生产效能看板聚合查询 Mapper（mp 仓库管理看板，WMS-DASH-MP-001）。
 *
 * <p>只读 WMS-STAT-001 三张落盘表（{@code t_warehouse_indicator_record} /
 * {@code t_warehouse_cropp_record} / {@code t_warehouse_monthly_record}），不实时聚合业务表。
 * 范式参照 {@link WarehouseDashboardMapper}：不走 BaseMapperPlus，所有 SQL 显式
 * {@code WHERE tenant_id = #{tenantId}}（dashboard 聚合不保证拦截器注入）。</p>
 *
 * <p>各方法返回 stat 域实体集，由 service 在 Java 侧汇总成年度 KPI / 月度趋势 / 日矩阵 / TOP10，
 * 避免每个 KPI 一条 SQL。无数据返回空列表。</p>
 *
 * @author djs
 * @since WMS-DASH-MP-001
 */
@Mapper
public interface WarehouseProductionDashboardMapper {

    /**
     * 取某区间（含端点）仓库日表行，按日期升序。
     *
     * <p>年度 KPI（区间 = 当年 1-1 ~ 12-31）与日矩阵（区间 = 近 N 日）共用。</p>
     *
     * @param tenantId 租户
     * @param from     起始统计日（含）
     * @param to       截止统计日（含）
     * @return 仓库日表行（升序）
     */
    @Select("SELECT * FROM t_warehouse_indicator_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND stat_date BETWEEN #{from} AND #{to} "
        + " ORDER BY stat_date")
    List<WarehouseIndicatorRecord> selectIndicatorRecordsInRange(@Param("tenantId") String tenantId,
        @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 取某年（stat_month LIKE 'yyyy-%'）仓库月表行，按月份升序。
     *
     * <p>月度屠宰效能趋势（柱 + 3 折线）数据源。</p>
     *
     * @param tenantId  租户
     * @param yearPrefix 年份前缀（yyyy-，如 2026-）
     * @return 仓库月表行（按 stat_month 升序）
     */
    @Select("SELECT * FROM t_warehouse_monthly_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND stat_month LIKE #{yearPrefix} "
        + " ORDER BY stat_month")
    List<WarehouseMonthlyRecord> selectMonthlyRecordsByYear(@Param("tenantId") String tenantId,
        @Param("yearPrefix") String yearPrefix);

    /**
     * 取某区间（含端点）作物日表行，按日期升序 + crop_id 升序。
     *
     * <p>年度蔬菜 KPI（区间 = 当年）、TOP10（区间 = 所选月份）、日矩阵（区间 = 所选月份近一日）共用。</p>
     *
     * @param tenantId 租户
     * @param from     起始统计日（含）
     * @param to       截止统计日（含）
     * @return 作物日表行（升序）
     */
    @Select("SELECT * FROM t_warehouse_cropp_record "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0' "
        + "   AND stat_date BETWEEN #{from} AND #{to} "
        + " ORDER BY stat_date, crop_id")
    List<WarehouseCroppRecord> selectCroppRecordsInRange(@Param("tenantId") String tenantId,
        @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 取作物名映射（crop_id → crop_name），用于 TOP10 / 日矩阵展示。
     *
     * <p>雪花 crop_id CAST AS CHAR 防精度丢失。返回的 cropId 为字符串，service 按字符串 key 索引。</p>
     *
     * @param tenantId 租户
     * @return 作物 id（字符串）+ name 行（{@code idStr} / {@code cropName}）
     */
    @Select("SELECT CAST(id AS CHAR) AS idStr, crop_name AS cropName "
        + "  FROM t_plant_crop_info "
        + " WHERE tenant_id = #{tenantId} "
        + "   AND del_flag = '0'")
    List<CropNameRow> selectCropNames(@Param("tenantId") String tenantId);

    /** crop_id → crop_name 投影行（雪花 id 字符串化）。 */
    class CropNameRow {
        /** 作物 ID（字符串，防雪花精度丢失）。 */
        private String idStr;
        /** 作物名。 */
        private String cropName;

        public String getIdStr() {
            return idStr;
        }

        public void setIdStr(String idStr) {
            this.idStr = idStr;
        }

        public String getCropName() {
            return cropName;
        }

        public void setCropName(String cropName) {
            this.cropName = cropName;
        }
    }
}
