package org.dromara.djs.common.encoder;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * {@link BizCodeRule} 持久层（SYS-INFRA-004）。
 *
 * <p>规则记录变更频率极低（项目初始化时种子数据 + 偶尔 admin 调整），
 * 一律走 MyBatis-Plus 的 {@code selectList / selectOne} 即可，无需自定义 SQL。
 * MyBatis-Plus 多租户拦截器会自动按当前 tenant_id 过滤。</p>
 *
 * @author djs
 * @since SYS-INFRA-004
 */
@Mapper
public interface BizCodeRuleMapper extends BaseMapperPlus<BizCodeRule, BizCodeRule> {
}
