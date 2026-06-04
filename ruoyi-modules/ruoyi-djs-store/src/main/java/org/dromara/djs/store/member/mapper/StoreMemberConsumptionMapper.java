package org.dromara.djs.store.member.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.djs.store.member.domain.StoreMemberConsumption;
import org.dromara.djs.store.member.domain.vo.StoreMemberConsumptionVo;

/**
 * 会员手录消费记录 Mapper（STR-MEMBER-001）。
 *
 * @author djs
 * @since STR-MEMBER-001
 */
public interface StoreMemberConsumptionMapper
    extends BaseMapperPlus<StoreMemberConsumption, StoreMemberConsumptionVo> {

    /**
     * 本月录入消费记录数（{@code create_time} 落在当月，软删不计）。
     *
     * <p>{@code tenant_id} 应用层显式传入（自定义 {@code @Select} 不走 MP 多租户拦截器注入）。</p>
     *
     * @param tenantId 租户 ID
     * @return 本月录入消费记录数
     */
    @Select("SELECT COUNT(1) FROM t_store_member_consumption "
        + "WHERE del_flag = '0' AND tenant_id = #{tenantId} "
        + "  AND create_time >= DATE_FORMAT(NOW(), '%Y-%m-01') "
        + "  AND create_time < DATE_FORMAT(DATE_ADD(NOW(), INTERVAL 1 MONTH), '%Y-%m-01')")
    long countMonthlyConsumptions(@Param("tenantId") String tenantId);

}
