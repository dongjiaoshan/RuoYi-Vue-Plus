package org.dromara.djs.common.service;

import org.dromara.djs.common.domain.bo.OssNotifyBo;
import org.dromara.djs.common.domain.vo.OssStsCredentialVo;

/**
 * OSS 上传凭证服务（SYS-INFRA-002）。
 *
 * <p>V1：基于 ruoyi-common-oss 已有的 S3 预签名 PUT URL，对 MinIO + 阿里云 OSS（S3 协议）通用。
 * V2：若客户切到真·阿里云 RAM STS AssumeRole，{@link #issue} 内部按 sys_oss_config.sts_role_arn
 * 是否填充自动切换模式。</p>
 *
 * @author djs
 * @since SYS-INFRA-002
 */
public interface IOssStsService {

    /**
     * 申请上传凭证。
     *
     * @param bizType 业务类型（决定 OSS 目录前缀），如 pig_photo / dead_photo / med_proof / harvest / trace
     * @return 上传凭证 VO（含 uploadUrl / ossKey / 过期时间）
     */
    OssStsCredentialVo issue(String bizType);

    /**
     * 上传成功回写 sys_oss 表。
     *
     * @param body 元数据
     * @return 新 ossId
     */
    Long recordUploaded(OssNotifyBo body);
}
