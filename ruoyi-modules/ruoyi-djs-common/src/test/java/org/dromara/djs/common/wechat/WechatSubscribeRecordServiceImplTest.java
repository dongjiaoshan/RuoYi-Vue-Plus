package org.dromara.djs.common.wechat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link WechatSubscribeRecordServiceImpl} 单元测试（SYS-INFRA-008）。
 *
 * <p>覆盖 prompt 要求的两条核心路径：</p>
 * <ol>
 *   <li><b>happy path</b>：mapper.selectOne 返 null → 走 INSERT 分支，
 *       验证返 record id 不为空、{@code mapper.insert} 调一次、{@code mapper.updateById} 未调</li>
 *   <li><b>重复授权</b>：mapper.selectOne 返已存在 record → 走 UPDATE 分支，
 *       验证返回的是旧 id、{@code mapper.updateById} 调一次、{@code mapper.insert} 未调</li>
 * </ol>
 *
 * <p>纯 Mockito 不连库、不起 Spring。</p>
 *
 * @author djs
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WechatSubscribeRecordServiceImpl 单元测试")
@Tag("local")
@Tag("dev")
class WechatSubscribeRecordServiceImplTest {

    @Mock
    private WechatSubscribeRecordMapper recordMapper;

    @InjectMocks
    private WechatSubscribeRecordServiceImpl service;

    private WechatSubscribeRecordBo bo;

    @BeforeEach
    void setUp() {
        bo = new WechatSubscribeRecordBo();
        bo.setUserId(1001L);
        bo.setOpenid("openid_kevin");
        bo.setTemplateId("tmpl_slaughter_001");
        bo.setSubscribedAt(LocalDateTime.of(2026, 5, 21, 14, 30));
        bo.setAlwaysKeep(false);
        bo.setExpiredAt(LocalDateTime.of(2026, 5, 28, 14, 30));
    }

    // ------------------------------------------------------------------
    // 1. happy path — INSERT
    // ------------------------------------------------------------------

    @Test
    @DisplayName("happy: 无同 (user_id, template_id, used=0) 记录 → INSERT 新行 + 返新 id")
    void testInsertBo_NoExisting_InsertsNew() {
        // arrange: selectOne 返 null
        when(recordMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        // mapper.insert 时 MP 不会自动注入 ID（因为 Mockito 没实际调 MyBatis），
        // 这里手动模拟 IdType.ASSIGN_ID 行为：拦截 insert 调用，在传入 entity 上填 id。
        when(recordMapper.insert(any(WechatSubscribeRecord.class))).thenAnswer(inv -> {
            WechatSubscribeRecord arg = inv.getArgument(0);
            arg.setId(8001L);
            return 1;
        });

        // act
        Long recordId = service.insertBo(bo);

        // assert: 返回新 id
        assertNotNull(recordId);
        assertEquals(8001L, recordId);

        // assert: INSERT 调一次，UPDATE 未调
        ArgumentCaptor<WechatSubscribeRecord> captor = ArgumentCaptor.forClass(WechatSubscribeRecord.class);
        verify(recordMapper, times(1)).insert(captor.capture());
        verify(recordMapper, never()).updateById(any(WechatSubscribeRecord.class));

        // assert: INSERT 字段对齐 BO
        WechatSubscribeRecord inserted = captor.getValue();
        assertEquals(1001L, inserted.getUserId());
        assertEquals("openid_kevin", inserted.getOpenid());
        assertEquals("tmpl_slaughter_001", inserted.getTemplateId());
        assertEquals(LocalDateTime.of(2026, 5, 21, 14, 30), inserted.getSubscribedAt());
        assertEquals(LocalDateTime.of(2026, 5, 28, 14, 30), inserted.getExpiredAt());
        assertEquals(0, inserted.getUsed(), "新 INSERT used 必须为 0");
        assertEquals(0, inserted.getAlwaysKeep(), "alwaysKeep=false → 0");
        assertNotNull(inserted.getCreateTime(), "create_time NOT NULL，必须显式填值");
    }

    @Test
    @DisplayName("happy: alwaysKeep=true → INSERT 时 always_keep 字段写 1")
    void testInsertBo_AlwaysKeepTrue_StoresFlagOne() {
        bo.setAlwaysKeep(true);
        when(recordMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(recordMapper.insert(any(WechatSubscribeRecord.class))).thenAnswer(inv -> {
            WechatSubscribeRecord arg = inv.getArgument(0);
            arg.setId(8002L);
            return 1;
        });

        service.insertBo(bo);

        ArgumentCaptor<WechatSubscribeRecord> captor = ArgumentCaptor.forClass(WechatSubscribeRecord.class);
        verify(recordMapper, times(1)).insert(captor.capture());
        assertEquals(1, captor.getValue().getAlwaysKeep());
    }

    // ------------------------------------------------------------------
    // 2. 重复授权 — UPDATE
    // ------------------------------------------------------------------

    @Test
    @DisplayName("重复授权: 同 (user_id, template_id, used=0) 已存在 → UPDATE 覆盖 + 返旧 id")
    void testInsertBo_ExistingRecord_UpdatesInsteadOfInsert() {
        // arrange: 已存在一条 used=0 行
        WechatSubscribeRecord existing = new WechatSubscribeRecord();
        existing.setId(7777L);
        existing.setUserId(1001L);
        existing.setTemplateId("tmpl_slaughter_001");
        existing.setOpenid("openid_old");
        existing.setUsed(0);
        existing.setAlwaysKeep(0);
        existing.setSubscribedAt(LocalDateTime.of(2026, 5, 20, 9, 0));
        existing.setExpiredAt(LocalDateTime.of(2026, 5, 27, 9, 0));
        existing.setCreateTime(LocalDateTime.of(2026, 5, 20, 9, 0));
        when(recordMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        // 新一次授权 BO：alwaysKeep 改 true，openid 改新值
        bo.setAlwaysKeep(true);
        bo.setOpenid("openid_new");
        bo.setSubscribedAt(LocalDateTime.of(2026, 5, 21, 14, 30));
        bo.setExpiredAt(null);  // alwaysKeep=true 时通常不带 expiredAt

        when(recordMapper.updateById(any(WechatSubscribeRecord.class))).thenReturn(1);

        // act
        Long recordId = service.insertBo(bo);

        // assert: 返旧 id
        assertEquals(7777L, recordId);

        // assert: UPDATE 调一次，INSERT 未调
        ArgumentCaptor<WechatSubscribeRecord> captor = ArgumentCaptor.forClass(WechatSubscribeRecord.class);
        verify(recordMapper, times(1)).updateById(captor.capture());
        verify(recordMapper, never()).insert(any(WechatSubscribeRecord.class));

        // assert: UPDATE 字段覆盖
        WechatSubscribeRecord updated = captor.getValue();
        assertEquals(7777L, updated.getId(), "保留旧 id");
        assertEquals("openid_new", updated.getOpenid(), "openid 被覆盖");
        assertEquals(LocalDateTime.of(2026, 5, 21, 14, 30), updated.getSubscribedAt(),
            "subscribedAt 被覆盖");
        assertEquals(1, updated.getAlwaysKeep(), "alwaysKeep 被覆盖为 1");
        assertEquals(null, updated.getExpiredAt(), "expiredAt 被覆盖为 null");
        // used 不应被改动
        assertEquals(0, updated.getUsed(), "used=0 保持");
    }
}
