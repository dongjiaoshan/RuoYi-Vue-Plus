package org.dromara.djs.warehouse.burn.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 猪只信息只读 Mapper（WMS-PIG-001 跨域只读）。
 *
 * <p>本 ticket 燎毛工序需校验耳号 {@code current_status='END'}，但 {@code ruoyi-djs-warehouse}
 * 不依赖 {@code ruoyi-djs-breed}（避免模块耦合，breed 反向依赖 warehouse 的可能性更高）。
 * 这里写一个 minimal native query mapper 直接查 {@code t_farm_pig_info}，
 * 只查 {@code current_status} 一列，不引入实体类。</p>
 *
 * <p>这是 V1 的妥协方案；V2 视情况：</p>
 * <ul>
 *   <li>若 breed 暴露 {@code IPigQueryService} 接口（同步领域服务）—— 切到接口注入</li>
 *   <li>若改造为事件驱动 —— SLAUGHTER 事件已发出 {@code PigMarketingEvent}，可订阅事件预缓存"已出栏"集合</li>
 * </ul>
 *
 * @author djs
 * @since WMS-PIG-001
 */
@Mapper
public interface PigInfoReadMapper {

    /**
     * 查指定耳号的猪只 {@code current_status}（未软删）。
     *
     * <p>{@code tenant_id} 由 MP 多租户拦截器在 final SQL 注入，应用层无需显式 WHERE。</p>
     *
     * @return {@code current_status} 字符串（{@code END}/{@code HB}/{@code PZ} ...）；耳号不存在或已软删返 null
     */
    @Select("SELECT current_status FROM t_farm_pig_info "
        + "WHERE ear_no = #{earNo} AND del_flag = '0' LIMIT 1")
    String selectCurrentStatusByEarNo(@Param("earNo") String earNo);

}
