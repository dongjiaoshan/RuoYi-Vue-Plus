package org.dromara.djs.common.config;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 业务配置版本聚合的只读 Mapper（CROSS-DICT-001）。
 *
 * <p>对客户可调 config 表（{@link ConfigTableConstants#CONFIG_TABLES}）做只读 SELECT，
 * 拿业务参数列实时值供 {@link DjsConfigServiceImpl} 聚合算 SHA-256。<b>只读、不写</b>。</p>
 *
 * <p><b>为什么 djs-common 内直接写 Mapper、不走域模块 service</b>：{@code ruoyi-djs-common}
 * 是所有域模块（breed/plant/warehouse/store）的依赖底座，<b>绝不能反向 import</b>
 * {@code org.dromara.djs.breed.*ServiceImpl}（会造成 common→breed 循环依赖编译炸）。
 * 这里对 config 表做轻量只读 Mapper（同 {@code AppletUserQueryMapper} 直接 @Select
 * ruoyi 表的思路），不依赖任何域模块 service。</p>
 *
 * <p><b>tenant 过滤</b>：V1 未启全局多租户拦截器，原生 SQL 不自动注入 tenant 过滤，
 * 故显式手写 {@code tenant_id = '1001' AND del_flag = '0'}。</p>
 *
 * <p><b>SQL 注入安全</b>：{@code table / columns / orderBy} 全部来自
 * {@link ConfigTableConstants} 编译期常量白名单，非用户输入，{@code ${}} 拼接安全；
 * 唯一的运行期变量 tenant 走 {@code #{}} 预编译占位。</p>
 *
 * @author djs
 * @since CROSS-DICT-001
 */
@Mapper
public interface DjsConfigMapper {

    /**
     * 查询单张 config 表的业务参数列全量行（按 orderBy 稳定排序）。
     *
     * @param table   物理表名（白名单常量）
     * @param columns 业务参数列，逗号分隔（白名单常量拼接）
     * @param orderBy 稳定排序键（白名单常量）
     * @param tenantId 租户（V1 固定 '1001'）
     * @return 每行一个 {@code LinkedHashMap}，key 为列名（保列序），value 为列实时值
     */
    @Select("""
        SELECT ${columns}
        FROM ${table}
        WHERE tenant_id = #{tenantId}
          AND del_flag = '0'
        ORDER BY ${orderBy}
        """)
    List<Map<String, Object>> selectConfigRows(@Param("table") String table,
                                               @Param("columns") String columns,
                                               @Param("orderBy") String orderBy,
                                               @Param("tenantId") String tenantId);
}
