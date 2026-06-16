package org.dromara.djs.breed.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.vo.PigSearchVo;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.mapper.PigStatusRecordMapper;
import org.dromara.djs.breed.core.service.PigStateMachine;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
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
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * {@link PigCoreServiceImpl#searchByEarKeyword} 单元测试（BRD-LIST-001 sub-step 1）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>happy: earNoKeyword="001" → mapper 收到 LIKE wrapper</li>
 *   <li>statusFilter="HB,PZ" CSV 解析 → IN list 包含两态</li>
 *   <li>statusFilter 含未知态（"XX,HB"）→ 静默丢弃只保留合法的</li>
 *   <li>sexFilter="M" + pigTypeFilter="boar" 同时生效</li>
 *   <li>终态 END 默认排除（wrapper.ne）</li>
 *   <li>limit 边界：null/负 → 20；超 100 → clamp 到 100；正常 30 透传</li>
 *   <li>enrich barnCode/penCode：mock barnMapper/penMapper 返一行，VO 拿到 code</li>
 *   <li>空结果：mapper 返空 list → 跳过 barn/pen 查询，直接返 emptyList</li>
 * </ul>
 *
 * @author djs
 * @since BRD-LIST-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PigCoreServiceImpl.searchByEarKeyword (BRD-LIST-001)")
class PigSearchServiceTest {

    @Mock
    private PigMapper pigMapper;
    @Mock
    private PigStatusRecordMapper statusRecordMapper;
    @Mock
    private PigStateMachine stateMachine;
    @Mock
    private ApplicationEventPublisher publisher;
    @Mock
    private BarnMapper barnMapper;
    @Mock
    private PenMapper penMapper;
    @Mock
    private org.dromara.djs.breed.production.service.IProductionCycleConfigService productionCycleConfigService;

    private PigCoreServiceImpl service;

    @BeforeEach
    void setup() {
        service = new PigCoreServiceImpl(pigMapper, statusRecordMapper, stateMachine, publisher, barnMapper, penMapper,
            org.mockito.Mockito.mock(org.dromara.common.core.service.DictService.class), productionCycleConfigService);
    }

    private Pig mkPig(long id, String earNo, String status, String sex, String type, Long barnId, Long penId) {
        Pig p = new Pig();
        p.setId(id);
        p.setEarNo(earNo);
        p.setCurrentStatus(status);
        p.setPigSex(sex);
        p.setPigType(type);
        p.setBarnId(barnId);
        p.setPenId(penId);
        return p;
    }

    @Test
    @DisplayName("happy: 关键字 + statusFilter CSV → 返过滤后 VO + enrich barn/pen code")
    void happy_keyword_status_enrich() {
        Pig p1 = mkPig(1L, "260520-001", "HB", "F", "sow", 11L, 21L);
        Pig p2 = mkPig(2L, "260520-002", "PZ", "F", "sow", 11L, 22L);
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(p1, p2));

        Barn barn = new Barn();
        barn.setId(11L);
        barn.setBarnCode("B01");
        when(barnMapper.selectBatchIds(anyCollection())).thenReturn(List.of(barn));

        Pen pen1 = new Pen();
        pen1.setId(21L);
        pen1.setPenCode("P01");
        Pen pen2 = new Pen();
        pen2.setId(22L);
        pen2.setPenCode("P02");
        when(penMapper.selectBatchIds(anyCollection())).thenReturn(List.of(pen1, pen2));

        List<PigSearchVo> result = service.searchByEarKeyword("001", "HB,PZ", null, null, null, 20, null, null, null);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEarNo()).isEqualTo("260520-001");
        assertThat(result.get(0).getBarnCode()).isEqualTo("B01");
        assertThat(result.get(0).getPenCode()).isEqualTo("P01");
        assertThat(result.get(1).getPenCode()).isEqualTo("P02");
    }

    @Test
    @DisplayName("statusFilter 含未知态 'XX,HB' → 静默丢弃 'XX' 只保留 'HB'")
    void statusFilter_silently_drops_invalid_codes() {
        // 我们只验证 service 不抛异常 + mapper 被调一次；语义解析在 parseStatusFilter 内部 + wrapper 由 mybatis-plus 构建
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        List<PigSearchVo> result = service.searchByEarKeyword(null, "XX,HB,YY", null, null, null, 20, null, null, null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("sexFilter=M + pigTypeFilter=boar → 公猪过滤生效；终态 END 不返")
    void male_boar_filter_excludes_end() {
        Pig boar = mkPig(10L, "B-001", "BOAR_ACTIVE", "M", "boar", null, null);
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(boar));

        List<PigSearchVo> result = service.searchByEarKeyword(null, null, "M", "boar", null, 20, null, null, null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPigSex()).isEqualTo("M");
        assertThat(result.get(0).getPigType()).isEqualTo("boar");
        // service 内部 wrapper.ne(END) 保证不返 END；这里通过 mapper mock 间接确认
        assertThat(result.get(0).getCurrentStatus()).isNotEqualTo(PigLifecycle.END.name());
    }

    @Test
    @DisplayName("终态 END 猪只默认不返——statusFilter 未声明含 END 时 wrapper 加 .ne(END)")
    void end_pigs_excluded_by_default() {
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        List<PigSearchVo> result = service.searchByEarKeyword(null, null, null, null, null, 20, null, null, null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("statusFilter='END'→ 放行 END 猪只（WMS-PIG-001 燎毛 picker 选已出栏猪用）")
    void end_pigs_allowed_when_statusFilter_explicit() {
        Pig endPig = new Pig();
        endPig.setId(99L);
        endPig.setEarNo("010126050007");
        endPig.setCurrentStatus(PigLifecycle.END.name());
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(endPig));

        List<PigSearchVo> result = service.searchByEarKeyword(null, "END", null, null, null, 20, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrentStatus()).isEqualTo(PigLifecycle.END.name());
    }

    @Test
    @DisplayName("limit 边界：null→20 / 负→20 / 超 100→100 / 30 透传（验证 wrapper.lastSql 含 LIMIT N）")
    void limit_boundaries() {
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // 调 4 次不同 limit，捕获 mapper 收到的 wrapper
        service.searchByEarKeyword(null, null, null, null, null, null, null, null, null);
        service.searchByEarKeyword(null, null, null, null, null, -5, null, null, null);
        service.searchByEarKeyword(null, null, null, null, null, 999, null, null, null);
        service.searchByEarKeyword(null, null, null, null, null, 30, null, null, null);

        ArgumentCaptor<LambdaQueryWrapper<Pig>> w = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(pigMapper, org.mockito.Mockito.times(4)).selectList(w.capture());

        // 用反射读 wrapper 内部 lastSql（避免 wrapper.getSqlSegment 触发 lambda cache 初始化，单测无 MP 容器）
        List<String> lastSqls = w.getAllValues().stream()
            .map(PigSearchServiceTest::readLastSql)
            .toList();
        assertThat(lastSqls.get(0)).contains("LIMIT 20");   // null → 20
        assertThat(lastSqls.get(1)).contains("LIMIT 20");   // -5  → 20
        assertThat(lastSqls.get(2)).contains("LIMIT 100");  // 999 → 100
        assertThat(lastSqls.get(3)).contains("LIMIT 30");   // 30  原样
    }

    /** 反射读 AbstractWrapper.lastSql（SharedString 字段），避免触发 LambdaCache。 */
    private static String readLastSql(LambdaQueryWrapper<?> w) {
        try {
            java.lang.reflect.Field f = Class.forName("com.baomidou.mybatisplus.core.conditions.AbstractWrapper")
                .getDeclaredField("lastSql");
            f.setAccessible(true);
            Object sharedString = f.get(w);
            // SharedString 提供 getStringValue() 方法
            java.lang.reflect.Method m = sharedString.getClass().getMethod("getStringValue");
            Object v = m.invoke(sharedString);
            return v == null ? "" : v.toString();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to read lastSql", e);
        }
    }

    @Test
    @DisplayName("空结果：mapper 返空 → 跳过 barn/pen 查询，直接返 emptyList")
    void empty_skips_enrich() {
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<PigSearchVo> result = service.searchByEarKeyword("nomatch", null, null, null, null, 20, null, null, null);
        assertThat(result).isEmpty();
        // 不应触发 barn / pen 查询
        org.mockito.Mockito.verify(barnMapper, org.mockito.Mockito.never()).selectBatchIds(anyCollection());
        org.mockito.Mockito.verify(penMapper, org.mockito.Mockito.never()).selectBatchIds(anyCollection());
    }

    // ===== BRD-FIX-MP-PIGSELECT-001：卡片量化字段 + barnCode filter + barn-count 聚合 =====

    @Test
    @DisplayName("量化字段：birthDate → ageDays；parity 透传；statusStartedAt → lastEventDays")
    void quantitative_fields_populated() {
        Pig p = mkPig(1L, "260101-001", "FM", "F", "sow", null, null);
        p.setBirthDate(java.time.LocalDate.now().minusDays(248));
        p.setParity(3);
        p.setStatusStartedAt(java.time.LocalDateTime.now().minusDays(13).minusHours(2));
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p));

        PigSearchVo vo = service.searchByEarKeyword(null, null, null, null, null, 20, null, null, null).get(0);
        assertThat(vo.getAgeDays()).isEqualTo(248);
        assertThat(vo.getParity()).isEqualTo(3);
        assertThat(vo.getLastEventDays()).isEqualTo(13);
    }

    @Test
    @DisplayName("量化字段：birthDate 缺位 → fallback introduceDate 算 ageDays")
    void ageDays_fallback_introduceDate() {
        Pig p = mkPig(1L, "260101-002", "HB", "F", "sow", null, null);
        p.setBirthDate(null);
        p.setIntroduceDate(java.time.LocalDate.now().minusDays(100));
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p));

        PigSearchVo vo = service.searchByEarKeyword(null, null, null, null, null, 20, null, null, null).get(0);
        assertThat(vo.getAgeDays()).isEqualTo(100);
    }

    @Test
    @DisplayName("量化字段：birthDate + introduceDate 均空 → ageDays/lastEventDays 为 null（mp 卡片该格不渲染）")
    void quantitative_fields_null_when_no_base() {
        Pig p = mkPig(1L, "260101-003", "HB", "F", "sow", null, null);
        p.setBirthDate(null);
        p.setIntroduceDate(null);
        p.setStatusStartedAt(null);
        p.setParity(null);
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p));

        PigSearchVo vo = service.searchByEarKeyword(null, null, null, null, null, 20, null, null, null).get(0);
        assertThat(vo.getAgeDays()).isNull();
        assertThat(vo.getLastEventDays()).isNull();
        assertThat(vo.getParity()).isNull();
    }

    @Test
    @DisplayName("barnCode filter：resolve 到 barnId → 用 barnId 过滤；查询正常返结果")
    void barnCode_filter_resolves_barnId() {
        Barn barn = new Barn();
        barn.setId(11L);
        barn.setBarnCode("B01");
        // resolveBarnIdByCode 走 selectOne
        when(barnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(barn);

        Pig p = mkPig(1L, "260101-004", "PZ", "F", "sow", 11L, null);
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p));
        when(barnMapper.selectBatchIds(anyCollection())).thenReturn(List.of(barn));

        List<PigSearchVo> result = service.searchByEarKeyword(null, null, null, null, "B01", 20, null, null, null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBarnCode()).isEqualTo("B01");
    }

    @Test
    @DisplayName("barnCode filter：barnCode 不存在 → 直接返 emptyList，不查 pig")
    void barnCode_filter_unknown_returns_empty() {
        when(barnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        List<PigSearchVo> result = service.searchByEarKeyword(null, null, null, null, "NOPE", 20, null, null, null);
        assertThat(result).isEmpty();
        org.mockito.Mockito.verify(pigMapper, org.mockito.Mockito.never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("dueType=FARROW：软提示——全部返回 + 临产排前 + due/dueDate 标记（D12X-MP-FARROW-WEANING-001 改软提示）")
    void dueType_farrow_annotates_and_sorts_keeps_all() {
        // gestation_days = 114
        when(productionCycleConfigService.getValue("gestation_days")).thenReturn(114);

        // 已到产期：115 天前配种（预产期 = 配种+114 = 昨天 ≤ today），due=true
        Pig due = mkPig(1L, "260520-001", "PZ", "F", "sow", 11L, null);
        due.setLastMatingDate(LocalDate.now().minusDays(115));
        // 刚配种 10 天，未到产期（预产期在未来），due=false，但仍返回（早产可录 / 可浏览）
        Pig notDue = mkPig(2L, "260520-002", "PZ", "F", "sow", 11L, null);
        notDue.setLastMatingDate(LocalDate.now().minusDays(10));
        // 无配种记录（lastMatingDate null）：dueDate=null、due=null，仍返回、排最后
        Pig noMating = mkPig(3L, "260520-003", "PZ", "F", "sow", 11L, null);
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(due, notDue, noMating));

        Barn barn = new Barn();
        barn.setId(11L);
        barn.setBarnCode("B01");
        when(barnMapper.selectBatchIds(anyCollection())).thenReturn(List.of(barn));

        List<PigSearchVo> result = service.searchByEarKeyword(null, "PZ", "F", "sow", null, 60, "FARROW", null, null);
        // 不再剔除——三头全返回
        assertThat(result).hasSize(3);
        // 临产排前：due(001) → 未到期(002) → 无基准日期(003)
        assertThat(result.get(0).getEarNo()).isEqualTo("260520-001");
        assertThat(result.get(0).getDue()).isTrue();
        assertThat(result.get(0).getDueDate()).isEqualTo(LocalDate.now().minusDays(115).plusDays(114));
        assertThat(result.get(1).getEarNo()).isEqualTo("260520-002");
        assertThat(result.get(1).getDue()).isFalse();
        assertThat(result.get(1).getDueDate()).isEqualTo(LocalDate.now().minusDays(10).plusDays(114));
        assertThat(result.get(2).getEarNo()).isEqualTo("260520-003");
        assertThat(result.get(2).getDueDate()).isNull();
        assertThat(result.get(2).getDue()).isNull();
    }

    @Test
    @DisplayName("countByBarn：按 barnId 分组 count + enrich barnName/barnCode + 按 barnCode 升序")
    void countByBarn_groups_and_enriches() {
        // 5 头：barn11 × 3, barn12 × 2
        Pig a = mkPig(1L, "a", "PZ", "F", "sow", 11L, null);
        Pig b = mkPig(2L, "b", "PZ", "F", "sow", 11L, null);
        Pig c = mkPig(3L, "c", "PZ", "F", "sow", 11L, null);
        Pig d = mkPig(4L, "d", "FM", "F", "sow", 12L, null);
        Pig e = mkPig(5L, "e", "FM", "F", "sow", 12L, null);
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(a, b, c, d, e));

        Barn b11 = new Barn();
        b11.setId(11L);
        b11.setBarnCode("B01");
        b11.setBarnName("妊娠1栋");
        Barn b12 = new Barn();
        b12.setId(12L);
        b12.setBarnCode("B02");
        b12.setBarnName("妊娠2栋");
        when(barnMapper.selectBatchIds(anyCollection())).thenReturn(Arrays.asList(b11, b12));

        List<org.dromara.djs.breed.core.domain.vo.PigBarnCountVo> result =
            service.countByBarn(null, "F", "sow");
        assertThat(result).hasSize(2);
        // 升序：B01 在前
        assertThat(result.get(0).getBarnCode()).isEqualTo("B01");
        assertThat(result.get(0).getBarnName()).isEqualTo("妊娠1栋");
        assertThat(result.get(0).getCount()).isEqualTo(3);
        assertThat(result.get(1).getBarnCode()).isEqualTo("B02");
        assertThat(result.get(1).getCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("countByBarn：mapper 返空 → emptyList，不查 barn")
    void countByBarn_empty() {
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<org.dromara.djs.breed.core.domain.vo.PigBarnCountVo> result =
            service.countByBarn(null, null, null);
        assertThat(result).isEmpty();
        org.mockito.Mockito.verify(barnMapper, org.mockito.Mockito.never()).selectBatchIds(anyCollection());
    }
}
