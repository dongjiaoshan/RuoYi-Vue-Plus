package org.dromara.djs.plant.plan.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.plan.domain.PlantDetails;
import org.dromara.djs.plant.plan.domain.vo.PlantDetailsVo;
import org.dromara.djs.plant.plan.domain.vo.PlantMonthTaskVo;

import java.util.List;

/**
 * 种植计划明细 Mapper（PLT-PLAN-001）。
 *
 * @author djs
 * @since PLT-PLAN-001
 */
public interface PlantDetailsMapper extends BaseMapperPlus<PlantDetails, PlantDetailsVo> {

    /**
     * 当月播种任务列表（FIX-PLANT-SEED-001，mp 播种首页「种植任务」tab）。
     *
     * <p>以当月种植明细为基，JOIN 地块（片区 / 面积）+ 作物（名 / 图）+ 计划（单号 / 自由文本日期），
     * 按片区 + 地块码排序，mp 端再按 zoneName 本地分组 + 片区 chips 计数。</p>
     *
     * <p>"当月" = {@code plant_month = #{month}}（明细表存 1-12 月份，非具体日期）。
     * 租户隔离：V1 单租户显式 {@code tenant_id='1001'}（未启全局 MP 拦截器，对齐
     * {@link PlantPlanMapper#selectDemandSummary}）。</p>
     *
     * @param month 当前月份 1-12
     * @return 当月播种任务卡列表（无数据返空列表）
     */
    @Select("""
        SELECT
            d.id            AS id,
            d.plant_id      AS plantId,
            p.plan_no       AS planNo,
            d.plot_id       AS plotId,
            pl.plot_code    AS plotCode,
            pl.plot_name    AS plotName,
            pl.zone_id      AS zoneId,
            z.zone_name     AS zoneName,
            d.crop_id       AS cropId,
            c.crop_name     AS cropName,
            c.crop_image_preview AS cropImg,
            d.plot_area     AS area,
            d.plant_month   AS plantMonth,
            d.plant_period  AS plantPeriod,
            p.plant_date    AS planDate,
            d.expected_yield AS expectedYield,
            d.actual_yield  AS actualYield,
            d.plant_status  AS plantStatus
          FROM t_plant_plant_details d
          JOIN t_plant_plant_plan p
            ON p.id = d.plant_id
           AND p.del_flag = '0'
           AND p.tenant_id = d.tenant_id
          LEFT JOIN t_plant_plot_info pl
            ON pl.id = d.plot_id
           AND pl.del_flag = '0'
          LEFT JOIN t_plant_plot_zone z
            ON z.id = pl.zone_id
           AND z.del_flag = '0'
          LEFT JOIN t_plant_crop_info c
            ON c.id = d.crop_id
           AND c.del_flag = '0'
         WHERE d.del_flag = '0'
           AND d.tenant_id = '1001'
           AND d.plant_month = #{month}
         ORDER BY z.zone_name ASC, pl.plot_code ASC, d.id ASC
        """)
    List<PlantMonthTaskVo> selectMonthTasks(@Param("month") Integer month);
}
