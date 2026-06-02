package org.dromara.djs.warehouse.trace.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 追溯码 admin 端农场名查询 Mapper（TRC-ADMIN-001）。
 *
 * <p>{@code t_warehouse_trace_code.farm_id} FK → {@code sys_farm.id}。{@code sys_farm} 是 ruoyi 改造表，
 * djs 模块无现成实体 / mapper，故新建本 admin 专属 mapper 仅做 farm 名字批量查（不碰 TRC-CORE 的
 * {@code TraceCodeMapper}）。store / product / plot 名字另由各模块现成 mapper JOIN。</p>
 *
 * @author djs
 * @since TRC-ADMIN-001
 */
@Mapper
public interface TraceFarmNameMapper {

    /**
     * 按 farm_id 批量查农场名（返回 {@code [{id, farmName}]}）。
     *
     * <p>自定义聚合 SQL 不走 MP 多租户拦截器（MyBatis 注解 SQL），WHERE 显式带 {@code tenant_id}。</p>
     */
    @Select("<script>"
        + "SELECT id, farm_name AS farmName FROM sys_farm "
        + "WHERE tenant_id = #{tenantId} "
        + "  AND id IN <foreach collection='ids' item='fid' open='(' separator=',' close=')'>#{fid}</foreach>"
        + "</script>")
    List<Map<String, Object>> selectFarmNames(@Param("ids") List<Long> ids, @Param("tenantId") String tenantId);

}
