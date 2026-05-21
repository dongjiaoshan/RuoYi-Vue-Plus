package org.dromara.djs.common.wechat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * {@link WechatSubscribeRecord} 的 mapper（SYS-INFRA-006）。
 *
 * <p>表 {@code mp_subscribe_record} 是全局共享表（{@code tenant_id} IGNORE），
 * 因此查询不会被多租户拦截器注入 {@code tenant_id} 条件 —— 直接 BaseMapper 即可。</p>
 *
 * @author djs
 */
@Mapper
public interface WechatSubscribeRecordMapper extends BaseMapper<WechatSubscribeRecord> {

    /**
     * 查找用户对某模板的一条可用授权记录。
     *
     * <p>匹配规则：</p>
     * <ul>
     *   <li>{@code user_id = ?} 且 {@code template_id = ?}</li>
     *   <li>{@code used = 0} 或 {@code always_keep = 1}（长期订阅不消耗）</li>
     *   <li>{@code expired_at IS NULL OR expired_at > NOW()}（未过期）</li>
     *   <li>按 {@code subscribed_at ASC} 取最早一条（FIFO 消费）</li>
     * </ul>
     *
     * @param userId     用户 ID
     * @param templateId 微信模板 ID
     * @return 命中记录；未命中返 null
     */
    @Select("""
        SELECT id, user_id, openid, template_id, subscribed_at, expired_at,
               used, used_at, always_keep, create_time
        FROM mp_subscribe_record
        WHERE user_id = #{userId}
          AND template_id = #{templateId}
          AND (used = 0 OR always_keep = 1)
          AND (expired_at IS NULL OR expired_at > NOW())
        ORDER BY subscribed_at ASC
        LIMIT 1
        """)
    WechatSubscribeRecord findUsable(@Param("userId") Long userId, @Param("templateId") String templateId);

    /**
     * 标记一条订阅记录为已使用（仅当 {@code always_keep=0}）。
     *
     * @param id      记录主键
     * @param usedAt  使用时间
     * @return 影响行数
     */
    @Update("""
        UPDATE mp_subscribe_record
        SET used = 1, used_at = #{usedAt}
        WHERE id = #{id} AND always_keep = 0
        """)
    int markUsed(@Param("id") Long id, @Param("usedAt") LocalDateTime usedAt);
}
