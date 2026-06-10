package org.dromara.djs.common.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 微信小程序首次绑定手机号入参。
 *
 * 流程：前端通过 {@code <button open-type="getPhoneNumber">} 拿 phoneCode →
 *   后端调微信"手机号快速验证"接口拿真实手机号 → 比对 sys_user.phonenumber →
 *   命中后 UPDATE sys_user SET wx_openid = openid → 颁发 Sa-Token。
 *
 * V1 mock 模式：直接读 {@code phone} 字段（前端调试时跳过微信调用）；生产用 {@code phoneCode}。
 *
 * @author djs
 */
@Data
public class WechatBindPhoneBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 微信 wx.login() 新 code（真实绑定首选）：后端 jscode2session 换 openid，客户端不经手 openid。
     * 与 {@code openid} 二选一，{@code code} 优先。
     */
    private String code;

    /**
     * wechatLogin 返回的临时 openid（dev / mock 模式透传回来；真实流程用 {@code code} 派生，可空）
     */
    private String openid;

    /**
     * 小程序 client_id（sys_client.client_id）
     */
    @NotBlank(message = "clientId 不能为空")
    private String clientId;

    /**
     * getPhoneNumber 回调的 code（生产环境用）；与 phone 二选一
     */
    private String phoneCode;

    /**
     * 手机号（dev / mock 模式直接传，跳过微信"手机号快速验证"调用）
     */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
