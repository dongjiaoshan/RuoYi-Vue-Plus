package org.dromara.djs.common.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
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
 * OSS STS / 预签名上传凭证 Controller（admin 端 SYS-INFRA-002）。
 *
 * <p>V1 实现：基于 S3 预签名 PUT URL（MinIO dev / 阿里云 OSS prod 通吃）。前端拿 {@code uploadUrl}
 * 后用浏览器 fetch 做一次 HTTP PUT 上传文件 body，不走后端中转，省带宽 + RTT。</p>
 *
 * <p>上传成功后必须调 {@code POST /djs/oss/sts/notify} 把元数据写入 {@code sys_oss} 表，业务字段
 * 保存 {@code ossId} 而非 URL。</p>
 *
 * <p>对应小程序端的 applet 入口在 {@code applet/AppletOssStsController}（同套 service，只是权限标识不同）。</p>
 *
 * @author djs
 * @since SYS-INFRA-002
 */
@Slf4j
@RestController
@RequestMapping("/djs/oss/sts")
@RequiredArgsConstructor
@Validated
public class OssStsController extends BaseController {

    private final IOssStsService stsService;

    /**
     * 申请 STS 上传凭证（admin 直传 OSS 用）。
     *
     * @param bizType 业务类型：pig_photo / dead_photo / grow_photo / med_proof / harvest / trace / avatar / doc
     * @return 凭证 VO（含 uploadUrl + ossKey + 过期时间）
     */
    @SaCheckPermission("djs:common:oss:sts")
    @GetMapping("/credential")
    public R<OssStsCredentialVo> credential(@RequestParam @NotBlank String bizType) {
        return R.ok(stsService.issue(bizType));
    }

    /**
     * 上传成功回写（前端 PUT OSS 成功后回调，落 sys_oss 表）。
     *
     * @return 新生成的 ossId（业务表保存这个）
     */
    @SaCheckPermission("djs:common:oss:sts")
    @PostMapping("/notify")
    public R<Long> notify(@Valid @RequestBody OssNotifyBo body) {
        return R.ok(stsService.recordUploaded(body));
    }
}
