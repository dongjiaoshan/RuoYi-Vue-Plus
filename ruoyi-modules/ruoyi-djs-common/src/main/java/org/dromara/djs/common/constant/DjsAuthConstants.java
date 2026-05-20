package org.dromara.djs.common.constant;

/**
 * djs 鉴权相关常量。
 *
 * @author djs
 */
public final class DjsAuthConstants {

    private DjsAuthConstants() {
    }

    /**
     * 微信小程序 client_id（sys_client.client_id）
     */
    public static final String MP_APPLET_CLIENT_ID = "mp-applet-dongjiaoshan";

    /**
     * 微信小程序 client_key
     */
    public static final String MP_APPLET_CLIENT_KEY = "mp-applet";

    /**
     * 业务码：微信 openid 未绑定 sys_user，前端需引导走 bind-phone 流程
     */
    public static final int BIZ_CODE_WECHAT_NEED_BIND = 40001;

    /**
     * 业务码：手机号未在 sys_user.phonenumber 注册，提示联系管理员
     */
    public static final int BIZ_CODE_PHONE_NOT_REGISTERED = 40002;

    /**
     * V1 默认农场 ID（与 djs 业务表 tenant_id DEFAULT '1001' 一致；
     * V2 多农场启用前，所有用户的 current_farm_id 都是这个值）
     */
    public static final String DEFAULT_FARM_ID = "1001";

    /**
     * V1 默认农场名称
     */
    public static final String DEFAULT_FARM_NAME = "东角山主农场";
}
