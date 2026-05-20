package org.dromara.djs.common.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.domain.bo.OssNotifyBo;
import org.dromara.djs.common.domain.vo.OssStsCredentialVo;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OssStsServiceImpl happy path 单测（不启动 Spring，纯 Mockito）。
 *
 * <p>覆盖的核心分支：</p>
 * <ul>
 *   <li>issue: bizType 合法 → 返 uploadUrl + ossKey + ttl</li>
 *   <li>issue: bizType 不在白名单 → 抛 ServiceException</li>
 *   <li>recordUploaded: ossKey 前缀与 bizType 不匹配 → 抛 ServiceException</li>
 *   <li>recordUploaded: happy path → mapper.insert 调一次，返 ossId</li>
 * </ul>
 *
 * <p>避开直接 mock {@code OssClient}（其内部 S3 chain 不易 mock），改用子类覆盖
 * {@code generatePresignedPutUrl / resolveBucketEndpoint / resolveCurrentConfigKey / buildPublicUrl}
 * 四个 protected 钩子。</p>
 *
 * @author djs
 * @since SYS-INFRA-002
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OssStsServiceImpl 单元测试")
@Tag("local")
@Tag("dev")
class OssStsServiceImplTest {

    @Mock
    private SysOssMapper sysOssMapper;

    /**
     * 子类覆盖 4 个 protected 钩子，避开 OssFactory.instance() 静态调用 + OssClient 初始化。
     */
    static class TestableOssStsService extends OssStsServiceImpl {
        TestableOssStsService(SysOssMapper sysOssMapper) {
            super(sysOssMapper);
        }

        @Override
        protected String generatePresignedPutUrl(String ossKey, Duration ttl) {
            return "http://127.0.0.1:9000/djs-dev/" + ossKey + "?presigned=mock&ttl=" + ttl.getSeconds();
        }

        @Override
        protected String[] resolveBucketEndpoint() {
            return new String[]{"djs-dev", "127.0.0.1:9000"};
        }

        @Override
        protected String resolveCurrentConfigKey() {
            return "minio";
        }

        @Override
        protected String buildPublicUrl(String ossKey) {
            return "http://127.0.0.1:9000/djs-dev/" + ossKey;
        }

        @Override
        protected String serializeExt(org.dromara.system.domain.SysOssExt ext) {
            // 测试时直接拼 JSON，避开 SpringUtil 依赖
            return String.format(
                "{\"bizType\":\"%s\",\"fileSize\":%d,\"contentType\":\"%s\",\"source\":\"%s\"}",
                ext.getBizType(), ext.getFileSize(), ext.getContentType(), ext.getSource());
        }
    }

    private TestableOssStsService service;

    @BeforeEach
    void setup() {
        service = new TestableOssStsService(sysOssMapper);
        ReflectionTestUtils.setField(service, "defaultRegion", "cn-hangzhou");
        ReflectionTestUtils.setField(service, "sessionDurationSec", 600);
    }

    @Test
    @DisplayName("issue: bizType 合法 → 返 uploadUrl + ossKey + ttl")
    void testIssue_HappyPath() {
        OssStsCredentialVo vo = service.issue("pig_photo");

        assertNotNull(vo);
        assertNotNull(vo.getOssKey());
        assertTrue(vo.getOssKey().startsWith("djs/pig_photo/"),
            "ossKey should start with djs/pig_photo/, got: " + vo.getOssKey());
        assertNotNull(vo.getUploadUrl());
        assertTrue(vo.getUploadUrl().contains(vo.getOssKey()),
            "uploadUrl should contain ossKey; got: " + vo.getUploadUrl());
        assertEquals(600, vo.getSessionDurationSec());
        assertEquals("pig_photo", vo.getBizType());
        assertEquals("PRESIGNED_PUT", vo.getMode());
        assertEquals("cn-hangzhou", vo.getRegion());
        assertEquals("djs-dev", vo.getBucket());
        assertEquals("127.0.0.1:9000", vo.getEndpoint());
        assertNotNull(vo.getExpiration());
        assertTrue(vo.getDir().startsWith("djs/pig_photo/"));
    }

    @Test
    @DisplayName("issue: bizType 不在白名单 → 抛 ServiceException")
    void testIssue_InvalidBizType() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.issue("hacker_drop_table"));
        assertTrue(ex.getMessage().contains("非法 bizType"));
    }

    @Test
    @DisplayName("recordUploaded: happy path → mapper.insert 调用一次")
    void testRecordUploaded_HappyPath() {
        OssNotifyBo body = new OssNotifyBo();
        body.setOssKey("djs/pig_photo/2026/05/20/abc");
        body.setOriginalName("piglet.jpg");
        body.setSize(102400L);
        body.setMime("image/jpeg");
        body.setBizType("pig_photo");

        when(sysOssMapper.insert(any(SysOss.class))).thenAnswer(inv -> {
            SysOss oss = inv.getArgument(0);
            oss.setOssId(999L);
            return 1;
        });

        Long ossId = service.recordUploaded(body);
        assertEquals(999L, ossId);

        ArgumentCaptor<SysOss> captor = ArgumentCaptor.forClass(SysOss.class);
        verify(sysOssMapper, times(1)).insert(captor.capture());
        SysOss saved = captor.getValue();
        assertEquals("djs/pig_photo/2026/05/20/abc", saved.getFileName());
        assertEquals("piglet.jpg", saved.getOriginalName());
        assertEquals(".jpg", saved.getFileSuffix());
        assertEquals("minio", saved.getService());
        assertTrue(saved.getUrl().contains("djs/pig_photo/2026/05/20/abc"),
            "url should contain ossKey; got: " + saved.getUrl());
        assertTrue(saved.getExt1().contains("\"bizType\":\"pig_photo\""),
            "ext1 JSON should contain bizType; got: " + saved.getExt1());
        assertTrue(saved.getExt1().contains("\"fileSize\":102400"),
            "ext1 should contain fileSize; got: " + saved.getExt1());
    }

    @Test
    @DisplayName("recordUploaded: ossKey 与 bizType 前缀不匹配 → 抛 ServiceException")
    void testRecordUploaded_PrefixMismatch() {
        OssNotifyBo body = new OssNotifyBo();
        body.setOssKey("djs/dead_photo/2026/05/20/abc");
        body.setOriginalName("x.jpg");
        body.setSize(1L);
        body.setMime("image/jpeg");
        body.setBizType("pig_photo");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.recordUploaded(body));
        assertTrue(ex.getMessage().contains("不匹配"));
    }

    @Test
    @DisplayName("recordUploaded: bizType 不在白名单 → 抛 ServiceException")
    void testRecordUploaded_InvalidBizType() {
        OssNotifyBo body = new OssNotifyBo();
        body.setOssKey("djs/hacker/2026/05/20/abc");
        body.setOriginalName("x.jpg");
        body.setSize(1L);
        body.setBizType("hacker");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.recordUploaded(body));
        assertTrue(ex.getMessage().contains("非法 bizType"));
    }
}
