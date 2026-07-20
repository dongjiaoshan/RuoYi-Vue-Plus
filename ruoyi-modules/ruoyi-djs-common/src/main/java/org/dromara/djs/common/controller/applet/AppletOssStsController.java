package org.dromara.djs.common.controller.applet;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.djs.common.domain.bo.OssNotifyBo;
import org.dromara.djs.common.domain.vo.OssStsCredentialVo;
import org.dromara.djs.common.service.IOssStsService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OSS STS 凭证 Controller — 小程序（applet）入口（SYS-INFRA-002）。
 *
 * <p>路径前缀 {@code /djs/applet/oss/sts/*}，与 admin 端 {@code /djs/oss/sts/*} 隔开，便于：
 * <ul>
 *   <li>Sa-Token 在 application.yml 里按前缀单独放过登录前的若干端点（如有）。</li>
 *   <li>小程序客户端的 client-id（mp-applet-dongjiaoshan）权限独立管控。</li>
 * </ul>
 * 实际业务逻辑与 admin 端共用同一个 {@link IOssStsService}。</p>
 *
 * <p>权限串使用 {@code djs:applet:oss:sts}，与 admin {@code djs:common:oss:sts} 区分；
 * 小程序登录用户默认拥有此权限（通过 wechat 角色或 applet 角色绑定）。</p>
 *
 * @author djs
 * @since SYS-INFRA-002
 */
@Slf4j
@RestController
@RequestMapping("/djs/applet/oss/sts")
@RequiredArgsConstructor
@Validated
public class AppletOssStsController {

    private final IOssStsService stsService;

    /**
     * 申请上传凭证（小程序专用）。
     *
     * <p>注意：小程序端 bizType 通常仅限 pig_photo / dead_photo / grow_photo / med_proof / harvest / trace；
     * service 层 ALLOWED_BIZ_TYPES 已做白名单校验，本 controller 不重复。</p>
     */
    @GetMapping("/credential")
    public R<OssStsCredentialVo> credential(@RequestParam @NotBlank String bizType) {
        log.info("[applet] OSS credential request bizType={}", bizType);
        return R.ok(stsService.issue(bizType));
    }

    /**
     * 上传成功回写（小程序专用）。
     */
    @PostMapping("/notify")
    public R<Long> notify(@Valid @RequestBody OssNotifyBo body) {
        return R.ok(stsService.recordUploaded(body));
    }
}
