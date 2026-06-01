package org.dromara.djs.warehouse.demand.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.domain.vo.DemandManageVo;

import java.time.LocalDate;
import java.util.Map;

/**
 * 需求管理 Mapper（WMS-DEMAND-001）。
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
public interface DemandManageMapper extends BaseMapperPlus<DemandManage, DemandManageVo> {

    /**
     * 今日 KPI 横条主表聚合（DJS-FIX-ADMIN-W22-007）：1 query 出 5 数。
     *
     * <p>「已调配」= 状态 {@code IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')}
     * （已脱离待确认态、未取消；状态集合是固定枚举不随租户变，直接写死 SQL）。</p>
     *
     * <p>返 Map 键：{@code todayPigDemand / todayVegSpeciesDemand / todayVegSpeciesAssigned /
     * todayOtherDemand / todayOtherAssigned}（白条已调配头数走 {@link #selectTodayPigAssigned} 子表）。</p>
     *
     * <p>租户隔离：未启全局 MP 拦截器，显式 {@code tenant_id='1001'}（V1 单租户，与
     * {@code LocationStockMapper} 自定义聚合范式一致）。</p>
     *
     * @param today 今日日期（Asia/Shanghai 算，由 service 传入，不用 DB CURDATE() 避免时区雷）
     * @return 聚合 Map（单行）
     */
    @Select("""
        SELECT
          COALESCE(SUM(CASE WHEN product_type = 'white_bar' THEN demand_quantity ELSE 0 END), 0) AS todayPigDemand,
          COUNT(DISTINCT CASE WHEN product_type = 'vegetable' THEN product_id END) AS todayVegSpeciesDemand,
          COUNT(DISTINCT CASE WHEN product_type = 'vegetable'
                AND demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')
                THEN product_id END) AS todayVegSpeciesAssigned,
          COUNT(CASE WHEN product_type IN ('other','gift_box') THEN 1 END) AS todayOtherDemand,
          COUNT(CASE WHEN product_type IN ('other','gift_box')
                AND demand_status IN ('CONFIRMED','IN_PRODUCTION','PARTIAL_SHIPPED','COMPLETED')
                THEN 1 END) AS todayOtherAssigned
        FROM t_warehouse_demand_manage
        WHERE demand_date = #{today}
          AND del_flag = '0'
          AND tenant_id = '1001'
        """)
    Map<String, Object> selectTodayKpiMainAgg(@Param("today") LocalDate today);

    /**
     * 今日白条已调配头数（DJS-FIX-ADMIN-W22-007）：子表去重耳号计数。
     *
     * <p>demand_pig 子表存在一行 = 这头已指定，{@code COUNT(DISTINCT ear_no)} 即已调配头数。</p>
     *
     * <p>租户隔离：显式 {@code tenant_id='1001'}（V1 单租户，与 {@code LocationStockMapper} 范式一致）。</p>
     *
     * @param today 今日日期
     * @return 今日白条已指定的去重耳号数
     */
    @Select("""
        SELECT COUNT(DISTINCT dp.ear_no)
        FROM t_warehouse_demand_pig dp
        JOIN t_warehouse_demand_manage dm ON dm.id = dp.demand_id
             AND dm.del_flag = '0'
             AND dm.tenant_id = dp.tenant_id
        WHERE dm.demand_date = #{today}
          AND dm.product_type = 'white_bar'
          AND dp.del_flag = '0'
          AND dp.tenant_id = '1001'
        """)
    Integer selectTodayPigAssigned(@Param("today") LocalDate today);
}

