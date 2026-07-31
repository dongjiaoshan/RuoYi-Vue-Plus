package org.dromara.djs.plant.crop.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.domain.vo.CropFarmworkVo;
import org.dromara.djs.plant.crop.domain.vo.CropInfoVo;
import org.dromara.djs.plant.crop.domain.vo.CropPlantingRecordVo;
import org.dromara.djs.plant.crop.domain.vo.CropPlantingStatVo;

import java.util.Collection;
import java.util.List;

/**
 * 作物 Mapper（PLT-MD-001）。
 *
 * <p>列表主体走 {@link BaseMapperPlus#selectVoPage}；派生统计（历史种植次数 / 平均亩产 / 最大亩产）
 * 由 service 批量 enrich（{@link #selectPlantingStats}）避免 N+1。详情子表走
 * {@link #selectPlantingRecordsByCrop} / {@link #selectFarmworkByCrop}。</p>
 *
 * <p><b>聚合 SQL 租户隔离</b>：V1 未启全局 MP 多租户拦截器，{@code @Select} 原生 SQL 不会被自动注入
 * tenant 过滤 → 下列查询每张表都显式手写 {@code tenant_id='1001' AND del_flag='0'}。</p>
 *
 * @author djs
 * @since PLT-MD-001
 */
public interface CropInfoMapper extends BaseMapperPlus<CropInfo, CropInfoVo> {

    /**
     * 按 crop_id 批量聚合种植记录派生统计（历史种植次数 / 平均亩产 / 最大亩产）。
     *
     * <p>历史种植次数 historyPlantCount = COUNT(*) 全部明细行。
     * 平均亩产 avgYield / 最大亩产 maxYield（kg/亩）取「采摘完成状态」({@code harvest_status='completed'})
     * 明细的每亩实收 {@code actual_yield / plot_area}（实际产量 / 地块面积）：AVG → avgYield；MAX → maxYield。
     * 仅采摘完成且 actual_yield 非空、plot_area&gt;0 的行参与 AVG/MAX，无此类行时返 null。</p>
     *
     * @param cropIds 作物 id 集合（已 dedupe，非空）
     * @return 聚合行（无种植记录的 cropId 不出现，service 兜底 0/null）
     */
    @Select("""
        <script>
        SELECT
            crop_id AS cropId,
            COUNT(*) AS historyPlantCount,
            AVG(CASE WHEN harvest_status = 'completed' AND actual_yield IS NOT NULL AND plot_area &gt; 0
                     THEN actual_yield / plot_area END) AS avgYield,
            MAX(CASE WHEN harvest_status = 'completed' AND actual_yield IS NOT NULL AND plot_area &gt; 0
                     THEN actual_yield / plot_area END) AS maxYield
          FROM t_plant_plant_details
         WHERE tenant_id = '1001'
           AND del_flag = '0'
           AND crop_id IN
           <foreach collection='cropIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach>
         GROUP BY crop_id
        </script>
        """)
    List<CropPlantingStatVo> selectPlantingStats(@Param("cropIds") Collection<Long> cropIds);

    /**
     * 按 relatedProduct id 批量查关联产品名称（避免 N+1）。
     *
     * <p>逻辑关联 {@code t_warehouse_product_info.id → product_name}；显式手写
     * {@code tenant_id='1001' AND del_flag='0'}（V1 未启全局多租户拦截器）。</p>
     *
     * @param productIds 关联产品 id 集合（已 dedupe，非空）
     * @return 产品行（id + productName），service 收 Map 回填 relatedProductName
     */
    @Select("""
        <script>
        SELECT id, product_name AS productName
          FROM t_warehouse_product_info
         WHERE tenant_id = '1001'
           AND del_flag = '0'
           AND id IN
           <foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>
        </script>
        """)
    List<java.util.Map<String, Object>> selectProductNames(@Param("productIds") Collection<Long> productIds);

    /**
     * 按 cropId 查种植记录子表（作物详情 9 列）。
     *
     * <p>JOIN 地块名 + 种植班组(plant_by) / 采摘班组(harvest_by)。
     * 预计亩产 predictedPer 取作物 {@code predicted_per}（kg/亩）；实际产量 actualYield 取明细
     * {@code actual_yield}（实收 kg）；实际亩产 actualPer = {@code actual_yield / plot_area}（kg/亩）。
     * 按种植日期倒序。</p>
     *
     * @param cropId 作物 id
     * @return 种植记录子表行；无记录返空 list
     */
    @Select("""
        SELECT
            d.id AS id,
            d.begin_actualdate AS plantDate,
            p.plot_code AS plotCode,
            p.plot_name AS plotName,
            COALESCE((SELECT GROUP_CONCAT(wt.team_name ORDER BY wt.id SEPARATOR ',')
                        FROM t_plant_details_team dtj
                        JOIN t_plant_work_team wt ON wt.id = dtj.team_id AND wt.del_flag = '0'
                       WHERE dtj.detail_id = d.id AND dtj.role = 'plant' AND dtj.del_flag = '0'),
                     pt.team_name) AS plantTeamName,
            c.predicted_per AS predictedPer,
            d.earliest_harvestdate AS earliestHarvestDate,
            d.actual_yield AS actualYield,
            ROUND(d.actual_yield / NULLIF(p.plot_area, 0), 3) AS actualPer,
            d.begin_harvestdate AS pickStartDate,
            d.end_harvestdate AS pickEndDate,
            COALESCE((SELECT GROUP_CONCAT(wt.team_name ORDER BY wt.id SEPARATOR ',')
                        FROM t_plant_details_team dtj
                        JOIN t_plant_work_team wt ON wt.id = dtj.team_id AND wt.del_flag = '0'
                       WHERE dtj.detail_id = d.id AND dtj.role = 'harvest' AND dtj.del_flag = '0'),
                     ht.team_name) AS pickTeamName
          FROM t_plant_plant_details d
          LEFT JOIN t_plant_plot_info p
                 ON p.id = d.plot_id AND p.tenant_id = '1001' AND p.del_flag = '0'
          LEFT JOIN t_plant_crop_info c
                 ON c.id = d.crop_id AND c.tenant_id = '1001' AND c.del_flag = '0'
          LEFT JOIN t_plant_work_team pt
                 ON pt.id = d.plant_by AND pt.tenant_id = '1001' AND pt.del_flag = '0'
          LEFT JOIN t_plant_work_team ht
                 ON ht.id = d.harvest_by AND ht.tenant_id = '1001' AND ht.del_flag = '0'
         WHERE d.tenant_id = '1001'
           AND d.del_flag = '0'
           AND d.crop_id = #{cropId}
         ORDER BY d.begin_actualdate DESC, d.id DESC
        """)
    List<CropPlantingRecordVo> selectPlantingRecordsByCrop(@Param("cropId") Long cropId);

    /**
     * 按 cropId 查农事记录子表（作物详情 农事信息 tab）。
     *
     * <p>JOIN 地块名 + 处理班组(farm_by)；farmType 返字典值，前端 dict-tag 渲染。按农事日期倒序。</p>
     *
     * @param cropId 作物 id
     * @return 农事记录子表行；无记录返空 list
     */
    @Select("""
        SELECT
            f.id AS id,
            f.farm_date AS farmDate,
            f.farm_type AS farmType,
            p.plot_name AS plotName,
            t.team_name AS teamName,
            f.remark AS remark
          FROM t_plant_farm_records f
          LEFT JOIN t_plant_plot_info p
                 ON p.id = f.plot_id AND p.tenant_id = '1001' AND p.del_flag = '0'
          LEFT JOIN t_plant_work_team t
                 ON t.id = f.farm_by AND t.tenant_id = '1001' AND t.del_flag = '0'
         WHERE f.tenant_id = '1001'
           AND f.del_flag = '0'
           AND f.crop_id = #{cropId}
         ORDER BY f.farm_date DESC, f.id DESC
        """)
    List<CropFarmworkVo> selectFarmworkByCrop(@Param("cropId") Long cropId);
}
