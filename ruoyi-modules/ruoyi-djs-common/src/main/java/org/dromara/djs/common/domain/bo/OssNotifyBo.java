package org.dromara.djs.common.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * OSS 上传成功回写 BO（SYS-INFRA-002）。
 *
 * <p>前端直传 OSS 成功后调 {@code POST /djs/oss/sts/notify} 把元数据登记到 sys_oss 表，
 * 业务字段保存 {@code ossId}，列表展示时按 {@code service} JOIN sys_oss 取 URL。</p>
 *
 * @author djs
 * @since SYS-INFRA-002
 */
@Data
public class OssNotifyBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 对象键（必须等于 /credential 返回的 ossKey，后端做防伪校验）。
     */
    @NotBlank(message = "ossKey 不能为空")
    @Size(max = 512)
    private String ossKey;

    /**
     * 原始文件名（含扩展名）。
     */
    @NotBlank(message = "originalName 不能为空")
    @Size(max = 200)
    private String originalName;

    /**
     * 文件大小（字节）。
     */
    @NotNull(message = "size 不能为空")
    @Positive
    private Long size;

    /**
     * MIME 类型，例：image/jpeg。
     */
    @Size(max = 80)
    private String mime;

    /**
     * 业务类型（与 /credential 入参一致）。
     */
    @NotBlank(message = "bizType 不能为空")
    @Size(max = 40)
    private String bizType;
}
