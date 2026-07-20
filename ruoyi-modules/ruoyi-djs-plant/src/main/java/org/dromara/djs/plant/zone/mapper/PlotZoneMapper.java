package org.dromara.djs.plant.zone.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.plant.zone.domain.PlotZone;
import org.dromara.djs.plant.zone.domain.vo.PlotZoneVo;

import java.util.List;
import java.util.Map;

/**
 * 片区 Mapper（PLT-MD-001）。
 *
 * @author djs
 * @since PLT-MD-001
 */
public interface PlotZoneMapper extends BaseMapperPlus<PlotZone, PlotZoneVo> {

    /**
     * 按片区聚合「管理地块数量」（FIX-PLT-AD-ZONE-001 片区列表聚合列）。
     *
     * <p>一次 GROUP BY 取全片区的未删除地块数，service 层用 zoneId → plotCount 回填 VO，
     * 避免逐片区 N+1 count。只统计 {@code del_flag='0'} 的地块，0 地块的片区不出现在结果里
     * （service 兜底回填 0）。</p>
     *
     * <p>显式手写 {@code tenant_id='1001'}（V1 单租户口径），用
     * {@code @InterceptorIgnore(tenantLine = "true")} 关掉 MP 租户拦截器对这条手写聚合的
     * 二次注入；仅作用于本方法，不影响 mapper 其余 CRUD 的租户隔离。</p>
     *
     * @return 每行 {@code {zoneId, plotCount}}（无地块的片区不出现）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        SELECT p.zone_id AS zoneId, COUNT(*) AS plotCount
          FROM t_plant_plot_info p
         WHERE p.del_flag = '0'
           AND p.tenant_id = '1001'
           AND p.zone_id IS NOT NULL
         GROUP BY p.zone_id
        """)
    List<Map<String, Object>> selectPlotCountByZone();
}
