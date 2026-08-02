package org.dromara.djs.breed.event.castrate.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.castrate.domain.CastrateRecord;
import org.dromara.djs.breed.event.castrate.mapper.CastrateRecordMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AutoCastrateExecutor} 单元测试（admin row186 超龄公仔猪自动阉割）。
 *
 * <p>重点锁两件事：① 抢占条件 {@code is_castrated=1} 必须真的进 SQL（删掉该条件测试必须炸，
 * 否则测试锁不住并发防重）；② 抢占失败时两个写都不能发生。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AutoCastrateExecutor 单元测试 (row186)")
class AutoCastrateExecutorTest {

    @Mock
    private CastrateRecordMapper castrateMapper;
    @Mock
    private PigMapper pigMapper;
    @Mock
    private IPigCoreService pigCoreService;

    private AutoCastrateExecutor executor;

    /**
     * MyBatis-Plus 单测 entity cache 预热：LambdaUpdateWrapper 在 mock 路径下也会走 TableInfoHelper
     * 解析 lambda 列名（见 skill coder-mp-entity-cache-test）。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, Pig.class);
        TableInfoHelper.initTableInfo(assistant, CastrateRecord.class);
    }

    @BeforeEach
    void setup() {
        executor = new AutoCastrateExecutor(castrateMapper, pigMapper, pigCoreService);
    }

    private Pig mkMalePiglet(Long id, String earNo) {
        Pig p = new Pig();
        p.setId(id);
        p.setEarNo(earNo);
        p.setPigSex("M");
        p.setPigType("piglet");
        p.setCurrentStatus("");
        p.setIsCastrated(1);
        return p;
    }

    @Test
    @DisplayName("抢占 UPDATE 必须带 is_castrated=1 条件（删掉该条件本用例即失败——并发防重的唯一保障）")
    void claimUpdate_mustCarryIsCastratedCondition() {
        when(pigMapper.update(eq(null), any())).thenReturn(1);

        executor.castrateOne(mkMalePiglet(21L, "260706-M-021"), LocalDate.of(2026, 8, 1), 7);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Pig>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(pigMapper).update(eq(null), captor.capture());
        // getSqlSet = SET 片段（is_castrated=2）；getSqlSegment = WHERE 片段，必须同时含 id 与 is_castrated 两个条件
        String where = captor.getValue().getSqlSegment();
        String sets = captor.getValue().getSqlSet();
        assertThat(sets).as("SET 片段应置 is_castrated").contains("is_castrated");
        assertThat(where).as("WHERE 必须含 id").contains("id");
        assertThat(where)
            .as("WHERE 必须含 is_castrated 抢占条件，否则人工与 job 并发会写出重复阉割记录")
            .contains("is_castrated");
    }

    @Test
    @DisplayName("happy path：写阉割记录 + 发 CASTRATE 事件，castrater='系统自动' 且 operatorId 为空")
    void castrateOne_happyPath() {
        when(pigMapper.update(eq(null), any())).thenReturn(1);

        boolean done = executor.castrateOne(mkMalePiglet(11L, "260706-M-001"), LocalDate.of(2026, 8, 1), 7);

        assertThat(done).isTrue();
        ArgumentCaptor<CastrateRecord> rec = ArgumentCaptor.forClass(CastrateRecord.class);
        verify(castrateMapper).insert(rec.capture());
        assertThat(rec.getValue().getCastrater()).isEqualTo("系统自动");
        // 定时线程无登录上下文，操作人必须留空（与人工录入区分）
        assertThat(rec.getValue().getOperatorId()).isNull();
        assertThat(rec.getValue().getCastrateDate()).isEqualTo(LocalDate.of(2026, 8, 1).atStartOfDay());
        assertThat(rec.getValue().getRemark()).contains("7");

        ArgumentCaptor<PigEventBo> ev = ArgumentCaptor.forClass(PigEventBo.class);
        verify(pigCoreService).fireEvent(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo(PigStatusEvent.CASTRATE);
        assertThat(ev.getValue().getPigId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("抢占失败（并发已被人工阉割）→ 返回 false，且阉割记录与事件一个都不写")
    void castrateOne_claimLost_writesNothing() {
        when(pigMapper.update(eq(null), any())).thenReturn(0);

        boolean done = executor.castrateOne(mkMalePiglet(13L, "260706-M-003"), LocalDate.of(2026, 8, 1), 7);

        assertThat(done).isFalse();
        verify(castrateMapper, never()).insert(any(CastrateRecord.class));
        verify(pigCoreService, never()).fireEvent(any(PigEventBo.class));
    }
}
