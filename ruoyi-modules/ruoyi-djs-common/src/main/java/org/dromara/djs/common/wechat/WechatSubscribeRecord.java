package org.dromara.djs.common.wechat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 微信订阅消息授权记录（SYS-INFRA-006）。
 *
 * <p>对应表 {@code mp_subscribe_record}，定义于 D1 SYS-INIT-001 common.sql。
 * <strong>全局共享表，不带 {@code tenant_id}</strong>（已加入 SYS-INFRA-007 IGNORE 名单），
 * 因此 entity 直接 {@link Serializable} 而非 {@code extends TenantEntity}。</p>
 *
 * <p>语义：每条记录表示一次小程序前端 {@code wx.requestSubscribeMessage} 用户授权。
 * 业务侧推送时调用 {@code findUsable(userId, templateId)} 查最早未过期未使用记录，
 * 推送成功后 mark {@code used=1}（{@code alwaysKeep=1} 时不 mark，可复用）。</p>
 *
 * @author djs
 */
@Data
@TableName("mp_subscribe_record")
public class WechatSubscribeRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（雪花 ID，由 MP 注入）。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID（sys_user.user_id）。 */
    private Long userId;

    /** 微信 openid。 */
    private String openid;

    /** 微信模板 ID。 */
    private String templateId;

    /** 授权时间。 */
    private LocalDateTime subscribedAt;

    /** 过期时间（订阅消息有效期 7 天；alwaysKeep=1 时为 null）。 */
    private LocalDateTime expiredAt;

    /** 0=未使用 / 1=已使用。 */
    private Integer used;

    /** 使用时间。 */
    private LocalDateTime usedAt;

    /** 是否勾选「总是保持以上选择」：1=是（不消耗）/ 0=否。 */
    private Integer alwaysKeep;

    /** 创建时间（DDL NOT NULL，无 default — 必须显式赋值）。 */
    private LocalDateTime createTime;
}
