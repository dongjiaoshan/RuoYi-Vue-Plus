package org.dromara.djs.common.config;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * djs 业务配置版本广播 Controller（CROSS-DICT-001）。
 *
 * <p>专给小程序业务页做"业务配置版本探测"用：</p>
 * <ul>
 *   <li>{@code GET /djs/config/version} 仅返客户可调配置全集的 SHA-256 hash，
 *       小程序业务页 {@code onShow} 比对本地缓存版本，不一致才重拉对应 config，
 *       避免工人拿着旧的出栏日龄 / 妊娠天数等参数干活。</li>
 * </ul>
 *
 * <p>admin 端各 config 管理仍走各域自带控制器（BreedConfig / ProductionCycleConfig 等），
 * 本 controller 不重新实现 CRUD，且 SHA-256 实时聚合范式下各 config 控制器代码一行不改。</p>
 *
 * <p>授权：{@link SaIgnore} 免登录。与 {@code DjsDictController} 同范式——小程序 App.vue
 * onLaunch 在用户登录前就需要预热配置版本，故启动期 / 登录前可探测。</p>
 *
 * @author djs
 * @since CROSS-DICT-001
 */
@RestController
@RequestMapping("/djs/config")
@RequiredArgsConstructor
public class DjsConfigController {

    private final IDjsConfigService djsConfigService;

    /**
     * 返当前业务配置全集的 SHA-256 hash（64 字符小写 hex）。
     *
     * <p>小程序端比对本地 {@code djs_config_version}：</p>
     * <ul>
     *   <li>相等 → 直接用本地缓存的 config，不重拉</li>
     *   <li>不等 → 重拉对应业务配置并更新本地版本</li>
     * </ul>
     */
    @SaIgnore
    @GetMapping("/version")
    public R<String> version() {
        return R.ok(djsConfigService.currentVersion());
    }
}
