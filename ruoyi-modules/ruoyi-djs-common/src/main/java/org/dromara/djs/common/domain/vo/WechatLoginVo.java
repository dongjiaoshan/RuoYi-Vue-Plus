package org.dromara.djs.common.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 微信登录响应。
 *
 * 复用 ruoyi 自带 {@code org.dromara.web.domain.vo.LoginVo} 的命名约定（access_token / expire_in /
 * openid / client_id），但加入 djs 业务字段 currentFarmId / userId 方便小程序首屏直接渲染。
 *
 * @author djs
 */
@Data
public class WechatLoginVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Sa-Token access_token
     */
    @JsonProperty("access_token")
    private String accessToken;

    /**
     * token 有效期（秒）
     */
    @JsonProperty("expire_in")
    private Long expireIn;

    /**
     * 客户端 ID（mp-applet-dongjiaoshan）
     */
    @JsonProperty("client_id")
    private String clientId;

    /**
     * 微信 openid
     */
    private String openid;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户昵称
     */
    private String nickName;

    /**
     * 当前农场 ID（V1 固定 "1001"）
     */
    private String currentFarmId;
}
