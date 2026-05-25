package org.dromara.djs.common.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.oss.constant.OssConstant;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.djs.common.domain.bo.OssNotifyBo;
import org.dromara.djs.common.domain.vo.OssStsCredentialVo;
import org.dromara.djs.common.service.IOssStsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * OSS 上传凭证 service 实现（SYS-INFRA-002）。
 *
 * <p>V1 默认走 S3 <b>预签名 PUT URL</b>（ruoyi-common-oss 已封装 {@link OssClient#createPresignedPutUrl}），
 * dev 环境用 MinIO、prod 也走同一套 S3 协议（阿里云 OSS / 腾讯云 COS / 七牛云 KODO 都兼容）。</p>
 *
 * <p>当 {@code sys_oss_config.sts_role_arn} 非空（V2 切阿里云原生 STS 时），本类预留扩展点
 * （目前 throw 占位异常，提示后续 ticket 接入阿里云 STS SDK）。</p>
 *
 * @author djs
 * @since SYS-INFRA-002
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssStsServiceImpl implements IOssStsService {

    /** V1 预签名 PUT URL 模式。 */
    private static final String MODE_PRESIGNED_PUT = "PRESIGNED_PUT";

    /** V2 阿里云 RAM STS AssumeRole 模式（本 ticket 预留，未实现）。 */
    private static final String MODE_ALIYUN_STS = "ALIYUN_STS";

    /** 业务类型白名单。后端再做一道防御：前端只能上传到这些目录前缀。 */
    private static final Set<String> ALLOWED_BIZ_TYPES = Set.of(
        "pig_photo",        // 猪只照片
        "pig_intro_proof",  // 引种凭证（外部引种检疫 / 发票 / 协议照片，BRD-EVENT-001）
        "breed_mate_proof", // 配种凭证（BRD-EVENT-002 配种现场照 / 精液发票，可选）
        "farrow_proof",     // 分娩凭证（BRD-EVENT-002 分娩现场 / 仔猪状态照，可选）
        "dead_photo",       // 死亡 / 淘汰多角度照片
        "grow_photo",       // 生长记录照片
        "med_proof",        // 用药 / 疫苗凭证
        "harvest",          // 收获 / 加工照片
        "trace",            // 追溯码 / 证书图
        "store_photo",      // 门店门头 / 招牌图
        "supplier_license", // 供应商营业执照
        "avatar",           // 用户头像 / 通用
        "doc"               // 通用文档
    );

    @Value("${djs.oss-sts.region:cn-hangzhou}")
    private String defaultRegion;

    /**
     * 凭证有效期（秒）。预签名 URL TTL 不宜过长——10 分钟够前端拍照→预览→上传一气呵成。
     * <p>放在 application.yml 的 djs.oss-sts.session-duration（如未配置走 600s 默认值）。</p>
     */
    @Value("${djs.oss-sts.session-duration:600}")
    private int sessionDurationSec;

    private final org.dromara.system.mapper.SysOssMapper sysOssMapper;

    @Override
    public OssStsCredentialVo issue(String bizType) {
        if (!ALLOWED_BIZ_TYPES.contains(bizType)) {
            throw new ServiceException("非法 bizType: " + bizType);
        }

        // 生成对象键：djs/<bizType>/<yyyy>/<MM>/<dd>/<uuid>
        // 注意：key 末尾不带 .ext，前端 PUT 时通过 Content-Type 标识类型；扩展名由 originalName 在 notify 阶段记录。
        String datePath = LocalDateTime.now(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String dir = String.format("djs/%s/%s/", bizType, datePath);
        String ossKey = dir + IdUtil.fastSimpleUUID();

        // V2 扩展点：sts_role_arn 非空时走真 STS AssumeRole（本 ticket 未实现，预留分支）。

        // V1 默认：S3 预签名 PUT。
        String uploadUrl = generatePresignedPutUrl(ossKey, Duration.ofSeconds(sessionDurationSec));
        String[] bucketEndpoint = resolveBucketEndpoint();

        Date expiration = Date.from(LocalDateTime.now().plusSeconds(sessionDurationSec)
            .atZone(ZoneId.systemDefault()).toInstant());

        log.info("签发 OSS 上传凭证 bizType={} ossKey={} ttl={}s mode={}",
            bizType, ossKey, sessionDurationSec, MODE_PRESIGNED_PUT);

        return OssStsCredentialVo.builder()
            .ossKey(ossKey)
            .bucket(bucketEndpoint[0])
            .endpoint(bucketEndpoint[1])
            .region(StrUtil.blankToDefault(defaultRegion, "cn-hangzhou"))
            .expiration(expiration)
            .sessionDurationSec(sessionDurationSec)
            .bizType(bizType)
            .uploadUrl(uploadUrl)
            .dir(dir)
            .mode(MODE_PRESIGNED_PUT)
            .build();
    }

    @Override
    public Long recordUploaded(OssNotifyBo body) {
        if (!ALLOWED_BIZ_TYPES.contains(body.getBizType())) {
            throw new ServiceException("非法 bizType: " + body.getBizType());
        }
        // 防伪校验：ossKey 必须以 djs/<bizType>/ 开头
        String expectedPrefix = "djs/" + body.getBizType() + "/";
        if (!body.getOssKey().startsWith(expectedPrefix)) {
            throw new ServiceException("ossKey 与 bizType 不匹配: " + body.getOssKey());
        }

        String configKey = resolveCurrentConfigKey();
        String url = buildPublicUrl(body.getOssKey());
        String suffix = extractSuffix(body.getOriginalName());

        org.dromara.system.domain.SysOss oss = new org.dromara.system.domain.SysOss();
        oss.setFileName(body.getOssKey());
        oss.setOriginalName(body.getOriginalName());
        oss.setFileSuffix(suffix);
        oss.setUrl(url);
        oss.setService(configKey);

        // 复用 ruoyi 的 SysOssExt 写 ext1
        org.dromara.system.domain.SysOssExt ext = new org.dromara.system.domain.SysOssExt();
        ext.setBizType(body.getBizType());
        ext.setFileSize(body.getSize());
        ext.setContentType(body.getMime());
        ext.setSource("djs-sts-direct-upload");
        oss.setExt1(serializeExt(ext));

        sysOssMapper.insert(oss);
        log.info("OSS 上传回写 ok ossId={} key={} size={}B", oss.getOssId(), oss.getFileName(), body.getSize());
        return oss.getOssId();
    }

    // ---------- protected hooks（单测会覆盖避开 OssClient 真实初始化） ----------

    /**
     * 生成 S3 预签名 PUT URL。<br>
     * 提取为 protected 便于单测 stub，避开直接 mock {@link OssClient}（其内部 S3Presigner 链不可重入 mock）。
     */
    protected String generatePresignedPutUrl(String ossKey, Duration ttl) {
        return OssFactory.instance().createPresignedPutUrl(ossKey, ttl, new HashMap<>());
    }

    /**
     * 拿当前主 OSS 配置的 bucket / endpoint，用反射避免硬依赖 {@code OssClient.getProperties()}（未暴露）。
     */
    protected String[] resolveBucketEndpoint() {
        try {
            OssClient client = OssFactory.instance();
            java.lang.reflect.Field f = OssClient.class.getDeclaredField("properties");
            f.setAccessible(true);
            org.dromara.common.oss.properties.OssProperties p = (org.dromara.common.oss.properties.OssProperties) f.get(client);
            return new String[]{p.getBucketName(), p.getEndpoint()};
        } catch (Exception e) {
            log.warn("反射读取 OssProperties 失败: {}", e.getMessage());
            return new String[]{"", ""};
        }
    }

    /** 当前主 OSS 配置 key（minio / aliyun / qcloud / ...）。 */
    protected String resolveCurrentConfigKey() {
        return OssFactory.instance().getConfigKey();
    }

    /** 文件 public URL（基于当前 OSS 主配置）。 */
    protected String buildPublicUrl(String ossKey) {
        return OssFactory.instance().getUrl() + "/" + ossKey;
    }

    /**
     * 序列化 ext1 字段。默认走 ruoyi 的 {@link JsonUtils}，但 JsonUtils 需要 Spring 上下文，
     * 单测时子类覆盖直接用 Jackson 即可。
     */
    protected String serializeExt(org.dromara.system.domain.SysOssExt ext) {
        return JsonUtils.toJsonString(ext);
    }

    private String extractSuffix(String filename) {
        if (filename == null) return "";
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx) : "";
    }

    /** 仅供单测占位：检查是否走 V2 STS 模式（V1 永远 false）。 */
    @SuppressWarnings("unused")
    private boolean shouldUseAliyunSts(String stsRoleArn) {
        return StrUtil.isNotBlank(stsRoleArn)
            && !"<account-id>".equals(stsRoleArn)  // 占位行不算
            && !stsRoleArn.contains("<");
    }
}
