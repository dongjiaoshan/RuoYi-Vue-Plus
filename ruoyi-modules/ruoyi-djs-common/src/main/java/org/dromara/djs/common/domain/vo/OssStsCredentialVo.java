package org.dromara.djs.common.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * OSS 上传凭证 VO（SYS-INFRA-002）。
 *
 * <p>本 ticket 用 <b>S3 预签名 PUT URL</b> 作为统一直传机制（MinIO dev / 阿里云 OSS prod 通吃），
 * 而不是直接调阿里云 STS AssumeRole（MinIO 不支持 STS）。前端拿到 {@link #uploadUrl} 后用浏览器
 * fetch / uni.uploadFile 做一次 HTTP PUT 即可，无需手算签名。</p>
 *
 * <p>当客户后续提供阿里云 RAM 子账号 + role ARN 时，{@code OssStsServiceImpl} 内部可切换为
 * 真·STS 方案（{@link #accessKeyId} / {@link #accessKeySecret} / {@link #sessionToken} 字段已预留），
 * 而对前端调用方契约（{@link #uploadUrl} + {@link #ossKey}）无变化。</p>
 *
 * @author djs
 * @since SYS-INFRA-002
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OssStsCredentialVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ---------------- 通用字段（预签名 PUT 模式 + STS 模式都用） ----------------

    /**
     * 后端预生成的对象键 (Object Key)。
     * <p>格式: {@code djs/<bizType>/<yyyy>/<MM>/<dd>/<uuid>.<ext>}</p>
     * <p>前端必须把这个 key 原样回传给 /notify 端点。</p>
     */
    private String ossKey;

    /**
     * 桶名（业务字段透传）。
     */
    private String bucket;

    /**
     * OSS endpoint（不含协议头）。
     */
    private String endpoint;

    /**
     * 区域。
     */
    private String region;

    /**
     * 凭证过期时间（与 sessionDurationSec 对应）。
     */
    private Date expiration;

    /**
     * 凭证有效期（秒），便于前端做"接近过期重拉"。
     */
    private Integer sessionDurationSec;

    /**
     * 业务类型（透传，前端 debug / 上传成功回写时复用）。
     */
    private String bizType;

    // ---------------- V1 预签名 PUT 模式字段 ----------------

    /**
     * 预签名 PUT URL（V1 默认模式，MinIO / 阿里云 S3 协议通吃）。
     * <p>前端用 HTTP PUT 把文件 body 上传到这个 URL 即可，无需额外签名。</p>
     */
    private String uploadUrl;

    // ---------------- V2 阿里云 STS AssumeRole 模式字段（V1 预留 null） ----------------

    /**
     * 临时 access key id（仅 STS 模式返回）。
     */
    private String accessKeyId;

    /**
     * 临时 access key secret（仅 STS 模式返回）。
     */
    private String accessKeySecret;

    /**
     * 临时 session token（仅 STS 模式返回，对应阿里云 SecurityToken）。
     */
    private String sessionToken;

    /**
     * 上传目录前缀（STS policy 限制范围，例：djs/pig_photo/2026/05/20/）。
     */
    private String dir;

    /**
     * 模式标识："PRESIGNED_PUT"（V1 默认） / "ALIYUN_STS"（V2）。
     * <p>前端可据此走不同上传逻辑。</p>
     */
    private String mode;
}
