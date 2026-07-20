package org.dromara.djs.common.dict;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * djs 业务字典聚合查询 Controller（SYS-INFRA-005）。
 *
 * <p>专给小程序客户端做"字典版本探测 + 全量同步"用：</p>
 * <ul>
 *   <li>{@code GET /djs/dict/version} 仅返 SHA-256 hash，小程序每次启动 / 每小时探测一次</li>
 *   <li>{@code GET /djs/dict/full} 命中本地缓存版本不一致时拉全量</li>
 * </ul>
 *
 * <p>admin 端字典管理仍走 ruoyi 自带 {@code /system/dict/type} 和 {@code /system/dict/data}，
 * 本 controller 不重新实现 CRUD。</p>
 *
 * <p>授权：{@link SaIgnore} 免登录。dict 是公开元数据（小程序 App.vue onLaunch 在用户登录前就需要同步），
 * 与 {@code AppletAuthController#wxLogin} 同属"启动期 / 登录前"端点。</p>
 *
 * @author djs
 * @since SYS-INFRA-005
 */
@RestController
@RequestMapping("/djs/dict")
@RequiredArgsConstructor
public class DjsDictController {

    private final IDjsDictService djsDictService;

    /**
     * 返当前 djs_ 字典全集的 SHA-256 hash。
     *
     * <p>小程序端比对本地 {@code djs_dict_cache.version}：</p>
     * <ul>
     *   <li>相等 → 直接用本地缓存，不再拉 {@code /full}</li>
     *   <li>不等 → 调 {@code /full} 拉全量并更新本地缓存</li>
     * </ul>
     */
    @SaIgnore
    @GetMapping("/version")
    public R<String> version() {
        return R.ok(djsDictService.currentVersion());
    }

    /**
     * 返 djs_ 字典全量数据（version + data）。
     *
     * <p>小程序首次安装 / version 不命中时拉一次，写入本地 storage。</p>
     */
    @SaIgnore
    @GetMapping("/full")
    public R<DjsDictFullVo> full() {
        return R.ok(djsDictService.queryFull());
    }
}
