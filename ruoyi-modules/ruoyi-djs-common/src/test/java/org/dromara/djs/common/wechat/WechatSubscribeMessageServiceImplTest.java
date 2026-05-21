package org.dromara.djs.common.wechat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dromara.djs.common.mapper.DjsUserExtMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * {@link WechatSubscribeMessageServiceImpl} 单元测试（SYS-INFRA-006）。
 *
 * <p>覆盖 prompt 要求的两条核心路径：</p>
 * <ol>
 *   <li><b>mock push happy</b>：mock 模式 → 返 success / mock=true / 0 retry，
 *       record 标 used=1，日志含 {@code [MOCK]} 前缀</li>
 *   <li><b>retry path</b>：非 mock 模式 + 真发抛异常 →
 *       N 次重试后返 fail（用 spy / 子类覆盖 realPush 模拟前 2 次失败第 3 次成功）</li>
 * </ol>
 *
 * <p>不依赖 Spring 容器、不依赖 Sa-Token、不连数据库 —— 纯 Mockito。</p>
 *
 * @author djs
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WechatSubscribeMessageServiceImpl 单元测试")
@Tag("local")
@Tag("dev")
class WechatSubscribeMessageServiceImplTest {

    @Mock
    private DjsUserExtMapper djsUserExtMapper;

    @Mock
    private WechatSubscribeRecordMapper recordMapper;

    private WechatProperties properties;

    @InjectMocks
    private WechatSubscribeMessageServiceImpl service;

    /** 抓取 service 日志，断言 [MOCK] 前缀。 */
    private ListAppender<ILoggingEvent> logAppender;
    private Logger serviceLogger;

    @BeforeEach
    void setUp() {
        properties = new WechatProperties();
        // 默认: app-id=mock + mock=true（effectiveMock=true）
        properties.getMiniapp().setAppId("mock");
        properties.getMiniapp().setMock(true);
        properties.getMiniapp().getTemplates().put(
            WechatMsgType.SLAUGHTER_NOTICE.getCode(), "tmpl_slaughter_001");
        properties.getMiniapp().getTemplates().put(
            WechatMsgType.STOCK_ALERT.getCode(), "tmpl_stock_001");
        // 重试参数压到 0ms 退避，单测快跑
        properties.getMiniapp().getRetry().setMaxAttempts(3);
        properties.getMiniapp().getRetry().setInitialBackoffMs(0L);
        properties.getMiniapp().getRetry().setBackoffMultiplier(1.0d);

        service = new WechatSubscribeMessageServiceImpl(djsUserExtMapper, recordMapper, properties);

        // 装一个 ListAppender 到 service 的 logger 上，方便断言 [MOCK] 前缀
        serviceLogger = (Logger) LoggerFactory.getLogger(WechatSubscribeMessageServiceImpl.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
        serviceLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        if (serviceLogger != null && logAppender != null) {
            serviceLogger.detachAppender(logAppender);
        }
    }

    // ------------------------------------------------------------------
    // 1. mock push happy
    // ------------------------------------------------------------------

    @Test
    @DisplayName("mock 模式 push: 找到 openid + 找到 record → 返 success / mock=true / mark used")
    void testMockPush_Happy() throws Exception {
        // arrange
        Long userId = 1001L;
        when(djsUserExtMapper.selectWxOpenid(userId)).thenReturn("openid_mock_kevin");

        WechatSubscribeRecord rec = new WechatSubscribeRecord();
        rec.setId(7001L);
        rec.setUserId(userId);
        rec.setOpenid("openid_mock_kevin");
        rec.setTemplateId("tmpl_slaughter_001");
        rec.setUsed(0);
        rec.setAlwaysKeep(0);
        rec.setSubscribedAt(LocalDateTime.now().minusHours(1));
        when(recordMapper.findUsable(eq(userId), eq("tmpl_slaughter_001"))).thenReturn(rec);
        when(recordMapper.markUsed(eq(7001L), any(LocalDateTime.class))).thenReturn(1);

        // act
        Map<String, String> data = new HashMap<>();
        data.put("thing1", "EAR0001");
        data.put("date2", "2026-05-20 14:30");
        data.put("thing3", "已审核通过");

        WxPushResult result = service.push(userId, WechatMsgType.SLAUGHTER_NOTICE, data).get();

        // assert: 推送结果
        assertNotNull(result);
        assertTrue(result.isSuccess(), "mock 模式 happy path 应返 success=true");
        assertTrue(result.isMock(), "mock=true 标志位");
        assertEquals(0, result.getRetryCount(), "首次成功 retryCount=0");
        assertEquals("openid_mock_kevin", result.getOpenid());
        assertEquals(WechatMsgType.SLAUGHTER_NOTICE, result.getMsgType());
        assertNull(result.getErrorMsg());

        // assert: mark used 调用
        verify(recordMapper, times(1)).markUsed(eq(7001L), any(LocalDateTime.class));

        // assert: 日志含 [MOCK] 前缀
        boolean hasMockLog = logAppender.list.stream()
            .anyMatch(e -> e.getFormattedMessage().contains("[MOCK] push to openid=openid_mock_kevin")
                && e.getFormattedMessage().contains("type=SLAUGHTER_NOTICE")
                && e.getFormattedMessage().contains("templateId=tmpl_slaughter_001"));
        assertTrue(hasMockLog, "应有 [MOCK] push to openid=... 日志");
    }

    @Test
    @DisplayName("mock 模式 push: 用户未绑定 wx_openid → skipped (success=false, retry=0)")
    void testMockPush_NoOpenid_Skipped() throws Exception {
        Long userId = 2001L;
        when(djsUserExtMapper.selectWxOpenid(userId)).thenReturn(null);

        WxPushResult result = service.push(userId, WechatMsgType.SLAUGHTER_NOTICE, Map.of()).get();

        assertNotNull(result);
        assertEquals(false, result.isSuccess());
        assertEquals(0, result.getRetryCount());
        assertEquals("wx_openid not bound", result.getErrorMsg());
        verify(recordMapper, never()).markUsed(anyLong(), any());
    }

    @Test
    @DisplayName("mock 模式 push: 模板 ID 未配置 → skipped")
    void testMockPush_NoTemplate_Skipped() throws Exception {
        Long userId = 3001L;
        when(djsUserExtMapper.selectWxOpenid(userId)).thenReturn("openid_x");
        // SALES_SUMMARY 没在 setUp templates map 里 → 应跳过

        WxPushResult result = service.push(userId, WechatMsgType.SALES_SUMMARY, Map.of()).get();

        assertEquals(false, result.isSuccess());
        assertEquals("template id not configured", result.getErrorMsg());
        verify(recordMapper, never()).findUsable(anyLong(), any());
    }

    @Test
    @DisplayName("pushBatch: 3 个用户中 1 绑定 / 1 未绑定 → 返回 3 条结果，对应顺序正确")
    void testPushBatch_Mixed() throws Exception {
        when(djsUserExtMapper.selectWxOpenid(101L)).thenReturn("openid_A");
        when(djsUserExtMapper.selectWxOpenid(102L)).thenReturn(null);
        when(djsUserExtMapper.selectWxOpenid(103L)).thenReturn("openid_C");

        WechatSubscribeRecord r1 = stubRecord(901L, 101L, "openid_A");
        WechatSubscribeRecord r3 = stubRecord(903L, 103L, "openid_C");
        when(recordMapper.findUsable(101L, "tmpl_slaughter_001")).thenReturn(r1);
        when(recordMapper.findUsable(103L, "tmpl_slaughter_001")).thenReturn(r3);
        when(recordMapper.markUsed(anyLong(), any())).thenReturn(1);

        List<WxPushResult> results = service.pushBatch(
            List.of(101L, 102L, 103L),
            WechatMsgType.SLAUGHTER_NOTICE,
            Map.of("thing1", "X")
        ).get();

        assertEquals(3, results.size());
        assertTrue(results.get(0).isSuccess());
        assertEquals(false, results.get(1).isSuccess()); // 未绑定
        assertTrue(results.get(2).isSuccess());
    }

    // ------------------------------------------------------------------
    // 2. retry path（真发模拟前 2 次失败，第 3 次成功）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("retry path: 真发模式前 2 次失败第 3 次成功 → 返 success / retryCount=2")
    void testRealPush_RetryThenSucceed() throws Exception {
        // 切到真发模式
        properties.getMiniapp().setAppId("wx_real_appid");
        properties.getMiniapp().setMock(false);

        // 用一个 anonymous 子类覆盖 realPush，控制成功 / 失败序列
        final int[] callCount = {0};
        WechatSubscribeMessageServiceImpl spied = new WechatSubscribeMessageServiceImpl(
            djsUserExtMapper, recordMapper, properties) {
            @Override
            protected void realPushOverridable(String openid, String templateId, Map<String, String> data) {
                callCount[0]++;
                if (callCount[0] < 3) {
                    throw new RuntimeException("simulated wx api 5xx attempt=" + callCount[0]);
                }
                // 第 3 次成功（不抛异常即可）
            }
        };

        Long userId = 4001L;
        when(djsUserExtMapper.selectWxOpenid(userId)).thenReturn("openid_real");
        WechatSubscribeRecord rec = stubRecord(801L, userId, "openid_real");
        when(recordMapper.findUsable(userId, "tmpl_slaughter_001")).thenReturn(rec);
        when(recordMapper.markUsed(eq(801L), any())).thenReturn(1);

        WxPushResult result = spied.push(userId, WechatMsgType.SLAUGHTER_NOTICE, Map.of("k", "v")).get();

        assertTrue(result.isSuccess(), "第 3 次成功，整体 success=true");
        assertEquals(2, result.getRetryCount(), "重试 2 次（attempt 1/2 失败，attempt 3 成功）");
        assertEquals(false, result.isMock());
        verify(recordMapper, times(1)).markUsed(eq(801L), any());
        assertEquals(3, callCount[0], "realPush 被调用 3 次");
    }

    @Test
    @DisplayName("retry path: 真发模式 3 次全失败 → 返 fail / retryCount=3 / errorMsg 非空")
    void testRealPush_AllFail() throws Exception {
        properties.getMiniapp().setAppId("wx_real_appid");
        properties.getMiniapp().setMock(false);

        WechatSubscribeMessageServiceImpl spied = new WechatSubscribeMessageServiceImpl(
            djsUserExtMapper, recordMapper, properties) {
            @Override
            protected void realPushOverridable(String openid, String templateId, Map<String, String> data) {
                throw new RuntimeException("wx api always down");
            }
        };

        Long userId = 5001L;
        when(djsUserExtMapper.selectWxOpenid(userId)).thenReturn("openid_dead");
        WechatSubscribeRecord rec = stubRecord(701L, userId, "openid_dead");
        when(recordMapper.findUsable(userId, "tmpl_slaughter_001")).thenReturn(rec);

        WxPushResult result = spied.push(userId, WechatMsgType.SLAUGHTER_NOTICE, Map.of("k", "v")).get();

        assertEquals(false, result.isSuccess());
        assertEquals(3, result.getRetryCount());
        assertTrue(result.getErrorMsg().contains("wx api always down"));
        verify(recordMapper, never()).markUsed(anyLong(), any());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private WechatSubscribeRecord stubRecord(Long recordId, Long userId, String openid) {
        WechatSubscribeRecord r = new WechatSubscribeRecord();
        r.setId(recordId);
        r.setUserId(userId);
        r.setOpenid(openid);
        r.setTemplateId("tmpl_slaughter_001");
        r.setUsed(0);
        r.setAlwaysKeep(0);
        r.setSubscribedAt(LocalDateTime.now().minusHours(1));
        return r;
    }
}
