package org.dromara.djs.common.wechat;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 微信小程序 {@link WxMaService} Bean 装配。
 *
 * <p>从 {@link WechatProperties} 读 app-id / secret 构造单实例 WxMaService。mock 模式
 * （{@code djs.wechat.miniapp.app-id=mock} 或 {@code mock=true}）下 Bean 仍创建，但
 * {@link org.dromara.djs.common.service.impl.WechatLoginServiceImpl} 的 jscode2openid /
 * resolvePhoneByCode 会在 mock 分支短路，不触达真实微信 API，因此 mock 模式空 secret 不影响启动。</p>
 *
 * @author djs
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WxMaConfiguration {

    private final WechatProperties wechatProperties;

    /**
     * 单实例 WxMaService（持 appId/secret 配置；access_token 由 SDK 默认内存缓存管理）。
     */
    @Bean
    public WxMaService wxMaService() {
        WechatProperties.Miniapp miniapp = wechatProperties.getMiniapp();

        WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
        config.setAppid(miniapp.getAppId());
        config.setSecret(miniapp.getSecret());

        WxMaService service = new WxMaServiceImpl();
        service.setWxMaConfig(config);

        if (wechatProperties.isEffectiveMock()) {
            log.warn("[wx-ma] mock 模式（app-id={}）—— 微信真实 API 不会被调用，登录走 mock openid",
                miniapp.getAppId());
        } else {
            log.info("[wx-ma] 真实模式启用 app-id={}", miniapp.getAppId());
        }
        return service;
    }
}
