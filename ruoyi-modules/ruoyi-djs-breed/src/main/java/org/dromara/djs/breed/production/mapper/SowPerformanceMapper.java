package org.dromara.djs.breed.production.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.breed.production.domain.SowPerformance;
import org.dromara.djs.breed.production.domain.vo.SowPerformanceVo;

/**
 * 母猪生产指标 Mapper（BRD-LIST-001 详情 tab2）。
 *
 * @author djs
 * @since BRD-LIST-001
 */
public interface SowPerformanceMapper extends BaseMapperPlus<SowPerformance, SowPerformanceVo> {

    /**
     * 按 pig_id 取该母猪定时填的最新一行性能指标（{@code t_farm_sow_performance}）。
     *
     * <p>表唯一键 {@code uk_tenant_pig (tenant_id, pig_id)} → 单租户单母猪本就只有一行；
     * 仍 {@code ORDER BY last_update_date DESC, id DESC LIMIT 1} 防御性取最新，避免历史脏行影响。
     * 自定义 SQL 显式带 {@code tenant_id}（不依赖 MP 拦截器；V1 全 '1001'）。</p>
     *
     * @param pigId    母猪 ID
     * @param tenantId 租户/农场 ID（V1 恒 '1001'）
     * @return 最新一行；无则 {@code null}（service 走当场算 fallback）
     */
    @Select("""
        SELECT * FROM t_farm_sow_performance
        WHERE pig_id = #{pigId} AND tenant_id = #{tenantId} AND del_flag = '0'
        ORDER BY last_update_date DESC, id DESC
        LIMIT 1
        """)
    SowPerformance selectLatestByPigId(@Param("pigId") Long pigId, @Param("tenantId") String tenantId);
}
