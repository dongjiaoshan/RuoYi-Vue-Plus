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
     * <p>聚合源 {@code t_plant_plant_details.average_yield}（平均亩产 kg/亩）：
     * COUNT(*) → historyPlantCount；AVG(average_yield) → avgYield；MAX(average_yield) → maxYield。
     * 仅 {@code average_yield IS NOT NULL} 行参与 AVG/MAX，COUNT 计全部明细行。</p>
     *
     * @param cropIds 作物 id 集合（已 dedupe，非空）
     * @return 聚合行（无种植记录的 cropId 不出现，service 兜底 0/null）
     */
    @Select("""
        <script>
        SELECT
            crop_id AS cropId,
            COUNT(*) AS historyPlantCount,
            AVG(average_yield) AS avgYield,
            MAX(average_yield) AS maxYield
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
     * 按 cropId 查种植记录子表（作物详情 9 列）。
     *
     * <p>JOIN 地块名 + 种植班组(plant_by) / 采摘班组(harvest_by)；亩产按 kg 返（决策#7 明细用 kg）。
     * 预计亩产取作物 {@code predicted_per}；实际亩产取 {@code average_yield}。按种植日期倒序。</p>
     *
     * @param cropId 作物 id
     * @return 种植记录子表行；无记录返空 list
     */
    @Select("""
        SELECT
            d.id AS id,
            d.begin_actualdate AS plantDate,
            p.plot_name AS plotName,
            pt.team_name AS plantTeamName,
            c.predicted_per AS predictedPer,
            d.earliest_harvestdate AS earliestHarvestDate,
            d.average_yield AS actualPer,
            d.begin_harvestdate AS pickStartDate,
            d.end_harvestdate AS pickEndDate,
            ht.team_name AS pickTeamName
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
