package org.dromara.djs.store.member.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.store.member.domain.StoreMember;
import org.dromara.djs.store.member.domain.vo.StoreMemberVo;

/**
 * 会员档案 Mapper（STR-MEMBER-001）。
 *
 * @author djs
 * @since STR-MEMBER-001
 */
public interface StoreMemberMapper extends BaseMapperPlus<StoreMember, StoreMemberVo> {

    /**
     * 本月新增会员数（{@code create_time} 落在当月，软删不计）。
     *
     * <p>{@code tenant_id} 由应用层显式传入：自定义 {@code @Select} 不走 MP 多租户拦截器注入
     * （prompt-be §0.5 规则），V2 启用拦截器后原生 SQL 不被自动加 {@code tenant_id} 过滤。</p>
     *
     * @param tenantId 租户 ID
     * @return 本月新增会员数
     */
    @Select("SELECT COUNT(1) FROM t_store_member "
        + "WHERE del_flag = '0' AND tenant_id = #{tenantId} "
        + "  AND create_time >= DATE_FORMAT(NOW(), '%Y-%m-01') "
        + "  AND create_time < DATE_FORMAT(DATE_ADD(NOW(), INTERVAL 1 MONTH), '%Y-%m-01')")
    long countMonthlyMembers(@Param("tenantId") String tenantId);

}
