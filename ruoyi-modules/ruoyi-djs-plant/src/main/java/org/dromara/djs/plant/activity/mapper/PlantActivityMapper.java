package org.dromara.djs.plant.activity.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.activity.domain.PlantActivity;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 采摘活动 Mapper（FIX-PLT-HARVEST-ACTIVITY-001）。
 *
 * <p>主体走 {@link BaseMapperPlus} 通用 CRUD；
 * 列表 {@code cropName} / {@code teamName} 由 service 层批量 enrich。</p>
 *
 * @author djs
 * @since FIX-PLT-HARVEST-ACTIVITY-001
 */
public interface PlantActivityMapper extends BaseMapperPlus<PlantActivity, PlantActivity> {

    /**
     * 批量按 (crop_id, activity_date) 聚合采摘活动重量（235 采摘活动管理「已采重量」取数）。
     *
     * <p>一次 {@code IN(cropIds)} + 全局日期窗口 {@code [minDate, maxDate]} + {@code GROUP BY crop_id, activity_date}，
     * 避免 N+1。各作物各自的采摘窗口（[earliestHarvestdate, lastHarvestdate]）粒度在 service 端按 (cropId, activityDate)
     * 行过滤累加（各作物窗口不同，故按日粒度返回让 service 精确聚合）。显式 {@code tenant_id='1001'} + {@code del_flag='0'}
     * （V1 单农场，关租户行注入）。</p>
     *
     * @param cropIds 作物 id 集合（空集时调用方需自行短路，勿传空）
     * @param minDate 全局窗口下界（所有作物窗口并集的最小起始日，缩小扫描）
     * @param maxDate 全局窗口上界（所有作物窗口并集的最大结束日）
     * @return 每行 {@code {cropId, activityDate, daySum}}
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        <script>
        SELECT crop_id AS cropId, activity_date AS activityDate, COALESCE(SUM(daily_weight), 0) AS daySum
          FROM t_plant_plant_activity
         WHERE del_flag = '0'
           AND tenant_id = '1001'
           AND activity_date BETWEEN #{minDate} AND #{maxDate}
           AND crop_id IN
           <foreach collection="cropIds" item="cid" open="(" separator="," close=")">#{cid}</foreach>
         GROUP BY crop_id, activity_date
        </script>
        """)
    List<Map<String, Object>> selectDailyWeightByCropInRange(@Param("cropIds") Collection<Long> cropIds,
                                                             @Param("minDate") LocalDate minDate,
                                                             @Param("maxDate") LocalDate maxDate);
}
