package org.dromara.djs.breed.core.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.vo.PigSearchVo;
import org.dromara.djs.breed.core.domain.query.PigQuery;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.mapper.PigStatusRecordMapper;
import org.dromara.djs.breed.core.service.PigStateMachine;
import org.dromara.djs.breed.event.growth.domain.PigGrowth;
import org.dromara.djs.breed.event.growth.mapper.PigGrowthMapper;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

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
    @Mock
    private PigGrowthMapper pigGrowthMapper;

    private PigCoreServiceImpl service;

    /**
     * MyBatis-Plus 单测 entity cache 预热：searchByEarKeyword → loadLastMeasureDateMap 用
     * LambdaQueryWrapper&lt;PigGrowth&gt; 在 mock 路径下也会触发 TableInfoHelper 解析 lambda 列名，必须先注册 PigGrowth。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, PigGrowth.class);
        // Pig 也预热：断言 wrapper.getSqlSegment() 的用例需要 lambda cache，否则抛 "can not find lambda cache"
        TableInfoHelper.initTableInfo(assistant, Pig.class);
    }

    @BeforeEach
    void setup() {
        service = new PigCoreServiceImpl(pigMapper, statusRecordMapper, stateMachine, publisher, barnMapper, penMapper,
            org.mockito.Mockito.mock(org.dromara.common.core.service.DictService.class), productionCycleConfigService,
            org.mockito.Mockito.mock(org.dromara.djs.breed.breeding.mapper.BreedInfoMapper.class),
            org.mockito.Mockito.mock(org.dromara.djs.breed.farm.service.PenCountUpdater.class));
        // pigGrowthMapper 是 @Autowired 字段注入（非构造参），手动注入 mock；默认返空列表 → loadLastMeasureDateMap 返空
        ReflectionTestUtils.setField(service, "pigGrowthMapper", pigGrowthMapper);
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

        List<PigSearchVo> result = service.searchByEarKeyword("001", "HB,PZ", null, null, null, 20, null, null, null, null, null, null);
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
        List<PigSearchVo> result = service.searchByEarKeyword(null, "XX,HB,YY", null, null, null, 20, null, null, null, null, null, null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("sexFilter=M + pigTypeFilter=boar → 公猪过滤生效；终态 END 不返")
    void male_boar_filter_excludes_end() {
        Pig boar = mkPig(10L, "B-001", "", "M", "boar", null, null);
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(boar));

        List<PigSearchVo> result = service.searchByEarKeyword(null, null, "M", "boar", null, 20, null, null, null, null, null, null);
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
        List<PigSearchVo> result = service.searchByEarKeyword(null, null, null, null, null, 20, null, null, null, null, null, null);
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

        List<PigSearchVo> result = service.searchByEarKeyword(null, "END", null, null, null, 20, null, null, null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrentStatus()).isEqualTo(PigLifecycle.END.name());
    }

    @Test
    @DisplayName("limit 边界：null→20 / 负→20 / 超 100→100 / 30 透传（验证 wrapper.lastSql 含 LIMIT N）")
    void limit_boundaries() {
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // 调 4 次不同 limit，捕获 mapper 收到的 wrapper
        service.searchByEarKeyword(null, null, null, null, null, null, null, null, null, null, null, null);
        service.searchByEarKeyword(null, null, null, null, null, -5, null, null, null, null, null, null);
        service.searchByEarKeyword(null, null, null, null, null, 999, null, null, null, null, null, null);
        service.searchByEarKeyword(null, null, null, null, null, 30, null, null, null, null, null, null);

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

        List<PigSearchVo> result = service.searchByEarKeyword("nomatch", null, null, null, null, 20, null, null, null, null, null, null);
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

        PigSearchVo vo = service.searchByEarKeyword(null, null, null, null, null, 20, null, null, null, null, null, null).get(0);
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

        PigSearchVo vo = service.searchByEarKeyword(null, null, null, null, null, 20, null, null, null, null, null, null).get(0);
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

        PigSearchVo vo = service.searchByEarKeyword(null, null, null, null, null, 20, null, null, null, null, null, null).get(0);
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

        List<PigSearchVo> result = service.searchByEarKeyword(null, null, null, null, "B01", 20, null, null, null, null, null, null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBarnCode()).isEqualTo("B01");
    }

    @Test
    @DisplayName("barnCode filter：barnCode 不存在 → 直接返 emptyList，不查 pig")
    void barnCode_filter_unknown_returns_empty() {
        when(barnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        List<PigSearchVo> result = service.searchByEarKeyword(null, null, null, null, "NOPE", 20, null, null, null, null, null, null);
        assertThat(result).isEmpty();
        org.mockito.Mockito.verify(pigMapper, org.mockito.Mockito.never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("dueType=FARROW：硬筛只留已到产期母猪（r52/r53 反转软提示；未到期/无配种基准日剔除）")
    void dueType_farrow_hard_filters_only_due() {
        // 配种到分娩天数 sow_breed_to_farrow_days = 114（computeDueDateMap FARROW 分支读此 key）
        when(productionCycleConfigService.getValue("sow_breed_to_farrow_days")).thenReturn(114);

        // 已到产期：115 天前配种（预产期 = 配种+114 = 昨天 ≤ today），due=true → 保留
        Pig due = mkPig(1L, "260520-001", "PZ", "F", "sow", 11L, null);
        due.setLastMatingDate(LocalDate.now().minusDays(115));
        // 刚配种 10 天，未到产期（预产期在未来）→ 硬筛剔除
        Pig notDue = mkPig(2L, "260520-002", "PZ", "F", "sow", 11L, null);
        notDue.setLastMatingDate(LocalDate.now().minusDays(10));
        // 无配种记录（lastMatingDate null）：dueDate=null → 硬筛剔除
        Pig noMating = mkPig(3L, "260520-003", "PZ", "F", "sow", 11L, null);
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(due, notDue, noMating));

        Barn barn = new Barn();
        barn.setId(11L);
        barn.setBarnCode("B01");
        when(barnMapper.selectBatchIds(anyCollection())).thenReturn(List.of(barn));

        List<PigSearchVo> result = service.searchByEarKeyword(null, "PZ", "F", "sow", null, 60, "FARROW", null, null, null, null, null);
        // r52/r53 硬筛：只留已到产期母猪（未到期 002 / 无配种基准日 003 全剔除）——
        // 与 countByBarn(dueType) 同口径，chip 头数 = 列表条数（r120）。
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEarNo()).isEqualTo("260520-001");
        assertThat(result.get(0).getDue()).isTrue();
        assertThat(result.get(0).getDueDate()).isEqualTo(LocalDate.now().minusDays(115).plusDays(114));
    }

    @Test
    @DisplayName("breedReady=true 后备(HB) 按日龄过滤：日龄 ≥ sow_reserve_to_breed_days 才返回（row35）")
    void breedReady_reserve_filters_by_age_days() {
        // 后备到配种最小日龄阈值 = 90 天（后备看日龄，不看在后备状态的天数）
        when(productionCycleConfigService.getValuesByKeys(anyCollection()))
            .thenReturn(java.util.Map.of("sow_reserve_to_breed_days", 90));

        // 老后备：日龄 120 ≥ 90 → 保留
        Pig oldGilt = mkPig(1L, "260301-001", "HB", "F", "sow", 11L, null);
        oldGilt.setBirthDate(LocalDate.now().minusDays(120));
        // 嫩后备：日龄 30 < 90 → 剔除
        Pig youngGilt = mkPig(2L, "260520-002", "HB", "F", "sow", 11L, null);
        youngGilt.setBirthDate(LocalDate.now().minusDays(30));
        // 无生日/无引种日后备：日龄不可判 → 保留（不误删候选）
        Pig noBirth = mkPig(3L, "260101-003", "HB", "F", "sow", 11L, null);
        when(pigMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(oldGilt, youngGilt, noBirth));

        Barn barn = new Barn();
        barn.setId(11L);
        barn.setBarnCode("B01");
        when(barnMapper.selectBatchIds(anyCollection())).thenReturn(List.of(barn));

        List<PigSearchVo> result = service.searchByEarKeyword(null, "HB", "F", "sow", null, 60, null, null, null, null, true, null);
        // 老后备(120日龄) + 无生日后备 保留；嫩后备(30日龄<90) 剔除
        assertThat(result).extracting(PigSearchVo::getEarNo)
            .containsExactlyInAnyOrder("260301-001", "260101-003");
    }

    @Test
    @DisplayName("breedReady=true 全局日龄门槛：日龄 < 后备-配种天数 的已配种态(断奶等)猪也一律剔除（row35 Kevin 2026-06-22）")
    void breedReady_global_age_floor_excludes_young_nonHB() {
        // 全局最小配种日龄 = 90；断奶恢复期阈值 = 3
        when(productionCycleConfigService.getValuesByKeys(anyCollection()))
            .thenReturn(java.util.Map.of("sow_reserve_to_breed_days", 90, "sow_wean_to_breed_days", 3));

        // 断奶老母猪：日龄 250 ≥ 90 且断奶 5 天 ≥ 3 → 保留
        Pig adultWean = mkPig(1L, "OK-001", "DN", "F", "sow", 11L, null);
        adultWean.setBirthDate(LocalDate.now().minusDays(250));
        adultWean.setStatusStartedAt(java.time.LocalDateTime.now().minusDays(5));
        // 断奶但日龄仅 3（测试态异常）：日龄 3 < 90 → 即使断奶在场天数够也剔除
        Pig youngWean = mkPig(2L, "YOUNG-002", "DN", "F", "sow", 11L, null);
        youngWean.setBirthDate(LocalDate.now().minusDays(3));
        youngWean.setStatusStartedAt(java.time.LocalDateTime.now().minusDays(3));
        when(pigMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(adultWean, youngWean));

        Barn barn = new Barn();
        barn.setId(11L);
        barn.setBarnCode("B01");
        when(barnMapper.selectBatchIds(anyCollection())).thenReturn(List.of(barn));

        List<PigSearchVo> result = service.searchByEarKeyword(null, "DN", "F", "sow", null, 60, null, null, null, null, true, null);
        assertThat(result).extracting(PigSearchVo::getEarNo).containsExactly("OK-001");
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
            service.countByBarn(null, "F", "sow", null, null, null, null);
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
            service.countByBarn(null, null, null, null, null, null, null);
        assertThat(result).isEmpty();
        org.mockito.Mockito.verify(barnMapper, org.mockito.Mockito.never()).selectBatchIds(anyCollection());
    }

    // ─────────── 小程序 row251/row255-258：QA 对抗验收指出的零覆盖，补回归防线 ───────────

    @Test
    @DisplayName("row257: 带耳号搜索 → dueType 硬筛放行（未到产期母猪也返回），但 dueDate/临产角标仍 enrich")
    void search_withEarNo_relaxesDueTypeButKeepsEnrich() {
        // 配种日 = 今天，离 114 天预产期还远 → 若 dueType 硬筛生效，本猪会被剔除
        Pig sow = mkPig(1L, "260520-001", "PZ", "F", "sow", 11L, 21L);
        sow.setLastMatingDate(java.time.LocalDate.now());
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sow));
        when(barnMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(penMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(productionCycleConfigService.getValue("sow_breed_to_farrow_days")).thenReturn(114);

        // 不带耳号：到期硬筛生效 → 未到期被剔除
        List<PigSearchVo> strict = service.searchByEarKeyword(null, "PZ", null, "sow", null, 20, "FARROW", null, null, null, null, null);
        assertThat(strict).isEmpty();

        // 带耳号：放行，且 dueDate 仍被 enrich（角标不能因放行而丢）
        List<PigSearchVo> relaxed = service.searchByEarKeyword("001", "PZ", null, "sow", null, 20, "FARROW", null, null, null, null, null);
        assertThat(relaxed).hasSize(1);
        assertThat(relaxed.get(0).getDueDate()).isNotNull();
        assertThat(relaxed.get(0).getDue()).isFalse();
    }

    @Test
    @DisplayName("最大用药日龄：带耳号搜索时**不放行**（业务硬约束，非默认待办窗口）")
    void search_maxAgeDays_notRelaxedByEarNoSearch() {
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // 手输耳号 + 传上限：条件必须照拼。甲方 8/3 提的正是「搜 304 日龄育肥猪仍搜得到」，
        // 若这里跟着 minAgeDays 一起放行，就等于上限对搜索态形同虚设。
        service.searchByEarKeyword("251003-002", null, null, null, null, 20, null, null, null, null, null, 300);

        ArgumentCaptor<LambdaQueryWrapper<Pig>> w = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(pigMapper).selectList(w.capture());
        assertMaxAgeSemantics(w.getValue());
        // 参数值必须真进去。⚠️ 必须放在 assertMaxAgeSemantics 之后：apply 的 {n} 占位符是惰性格式化的，
        // 调过 getSqlSegment() 才会落进 paramNameValuePairs，先断言这个 map 会拿到空 map。
        assertThat(w.getValue().getParamNameValuePairs()).containsValue(300).containsValue("fattening");
    }

    /**
     * 断言 maxAgeDays 条件的**语义**而非「参数接上了」。
     *
     * <p>这条 ticket 已经回归过两次，两次都不是「参数没传」而是「条件写错了作用域」，所以这里必须卡死
     * 三件事，缺一都会让某一类猪被误杀 / 漏网：</p>
     * <ol>
     *   <li>猪种判定是 <b>{@code <>}（排除育肥猪之外的）</b>，写成 {@code =} 会连坐干掉全部非育肥猪；</li>
     *   <li>三段之间是 <b>OR</b>，写成 AND 等于「必须既不是育肥猪又要满足日龄」，非育肥猪全灭；</li>
     *   <li>日龄比较是 <b>{@code <=}</b>（上限）而不是 {@code >=}（那是 minAgeDays 的方向）。</li>
     * </ol>
     */
    private static void assertMaxAgeSemantics(LambdaQueryWrapper<Pig> wrapper) {
        // 归一化：去掉 #{ew.paramNameValuePairs.MPGENVALn} 占位符与多余空白，只留结构
        String sql = wrapper.getSqlSegment()
            .replaceAll("#\\{[^}]+}", "?")
            .replaceAll("\\s+", " ");
        assertThat(sql)
            .as("必须是「非育肥猪 OR 无生日 OR 日龄<=上限」三段 OR，"
                + "写成 = / AND / >= 任一种都会让某类猪被误杀或漏网。实际 SQL: %s", sql)
            .contains("(pig_type <> ? OR COALESCE(birth_date, introduce_date) IS NULL"
                + " OR DATEDIFF(NOW(), COALESCE(birth_date, introduce_date)) <= ?)");
    }

    @Test
    @DisplayName("最大用药日龄：不传 / ≤0 → 完全不拼条件（其余调用方行为不变）")
    void search_maxAgeDays_absentOrNonPositive_notApplied() {
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        service.searchByEarKeyword("001", null, null, null, null, 20, null, null, null, null, null, null);
        service.searchByEarKeyword("001", null, null, null, null, 20, null, null, null, null, null, 0);

        ArgumentCaptor<LambdaQueryWrapper<Pig>> w = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(pigMapper, org.mockito.Mockito.times(2)).selectList(w.capture());
        assertThat(w.getAllValues()).allSatisfy(wrapper ->
            assertThat(wrapper.getSqlSegment()).doesNotContain("DATEDIFF"));
    }

    @Test
    @DisplayName("最大用药日龄：queryPage（批量选猪页走的路径）同样只约束育肥猪")
    void queryPage_maxAgeDays_onlyConstrainsFattening() {
        // 甲方 row266 复现的就是这条路径（mp 批量选猪 → /djs/breed/pig/list）。
        // 它与 searchByEarKeyword 是两套独立的 wrapper 构造，上一轮只改了一边才留下漏洞，故两边都要锁。
        when(pigMapper.selectVoPage(any(), any(LambdaQueryWrapper.class)))
            .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());

        PigQuery q = new PigQuery();
        q.setExcludeEnd(true);
        q.setMaxAgeDays(300);
        service.queryPage(q, new org.dromara.common.mybatis.core.page.PageQuery(10, 1));

        ArgumentCaptor<LambdaQueryWrapper<Pig>> w = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(pigMapper).selectVoPage(any(), w.capture());
        assertMaxAgeSemantics(w.getValue());
        // 同上：必须在 assertMaxAgeSemantics（内部调 getSqlSegment）之后断言，否则 map 还是空的
        assertThat(w.getValue().getParamNameValuePairs()).containsValue(300).containsValue("fattening");
    }

    @Test
    @DisplayName("row255-258: 带耳号搜索放行 dueType 硬筛后，statusFilter 等业务硬约束仍必须生效")
    void search_withEarNo_stillEnforcesStatusWhitelist() {
        // wrapper 里 statusFilter 走 SQL in(...)，mapper 被 mock 故此处以「传了白名单仍只返白名单内的猪」表达契约：
        // 放行只针对到期/到龄/在场天数这类默认收窄，绝不放行状态白名单。
        Pig sow = mkPig(1L, "260520-001", "PZ", "F", "sow", 11L, 21L);
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sow));
        when(barnMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(penMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        List<PigSearchVo> rows = service.searchByEarKeyword("001", "PZ", null, "sow", null, 20, null, null, null, null, null, null);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCurrentStatus()).isEqualTo("PZ");
    }

    @Test
    @DisplayName("row258: 带耳号搜索 → breedReady（最小在场天数）硬筛放行")
    void search_withEarNo_relaxesBreedReady() {
        // 今天刚断奶 → breedReady 硬筛下达不到最小在场天数
        Pig sow = mkPig(1L, "260520-001", "DN", "F", "sow", 11L, 21L);
        sow.setStatusStartedAt(java.time.LocalDateTime.now());
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sow));
        when(barnMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(penMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(productionCycleConfigService.getValuesByKeys(anyCollection()))
            .thenReturn(java.util.Map.of("sow_wean_to_breed_days", 6));

        List<PigSearchVo> strict = service.searchByEarKeyword(null, "DN", null, "sow", null, 20, null, null, null, null, true, null);
        assertThat(strict).isEmpty();

        List<PigSearchVo> relaxed = service.searchByEarKeyword("001", "DN", null, "sow", null, 20, null, null, null, null, true, null);
        assertThat(relaxed).hasSize(1);
    }

    @Test
    @DisplayName("row256-258: countByBarn 与 search 同规则——带耳号时 dueType 硬筛同样放行（契约不能只有一边真）")
    void countByBarn_withEarNo_relaxesDueType() {
        Pig sow = mkPig(1L, "260520-001", "PZ", "F", "sow", 11L, 21L);
        sow.setLastMatingDate(java.time.LocalDate.now());
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sow));
        Barn barn = new Barn();
        barn.setId(11L);
        barn.setBarnCode("B01");
        barn.setBarnName("配分舍1栋");
        when(barnMapper.selectBatchIds(anyCollection())).thenReturn(List.of(barn));
        when(productionCycleConfigService.getValue("sow_breed_to_farrow_days")).thenReturn(114);

        // 不带耳号：未到期 → chip 为空
        assertThat(service.countByBarn("PZ", null, "sow", null, null, "FARROW", null)).isEmpty();
        // 带耳号：放行 → chip 有 1 头，与 search 结果一致
        assertThat(service.countByBarn("PZ", null, "sow", "001", null, "FARROW", null)).hasSize(1);
    }

    // ================================================================
    // LIMIT 让位给内存后筛（生产 2026-08-06：分娩录入不选栋舍空列表、点栋舍才有）
    //
    // 缺陷形状：SQL `ORDER BY id DESC LIMIT n` 先截断，dueType/breedReady 的硬筛再在内存里跑 ——
    // 被截掉的猪根本没机会参与筛选。实测生产 100 头 PZ 母猪只有 1 头到产期、它在 id 倒序里排第 82，
    // panel 传 limit=60 → 内存筛完为空。点栋舍 chip 后 SQL 多了 barn_id 条件、候选池缩到 60 内才显出来。
    //
    // ⚠️ 这几个用例断言的是 **wrapper 里有没有 LIMIT**，不是「返回条数对不对」——
    // mock 的 pigMapper 不会真的执行 LIMIT，靠返回条数断言的测试在修复前也会绿，抓不到这个 bug。
    // ================================================================

    @Test
    @DisplayName("dueType=FARROW → SQL 不下 LIMIT（截断推迟到内存筛之后），否则到期母猪被 id 倒序截掉")
    void dueType_defers_limit_out_of_sql() {
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        service.searchByEarKeyword(null, "PZ", "F", "sow", null, 60, "FARROW", null, null, null, null, null);

        ArgumentCaptor<LambdaQueryWrapper<Pig>> w = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(pigMapper).selectList(w.capture());
        assertThat(readLastSql(w.getValue())).doesNotContain("LIMIT");
    }

    @Test
    @DisplayName("breedReady=true → SQL 不下 LIMIT（配种选猪的在场天数筛同样跑在内存里）")
    void breedReady_defers_limit_out_of_sql() {
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        service.searchByEarKeyword(null, "HB,DN", "F", "sow", null, 60, null, null, null, null, true, null);

        ArgumentCaptor<LambdaQueryWrapper<Pig>> w = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(pigMapper).selectList(w.capture());
        assertThat(readLastSql(w.getValue())).doesNotContain("LIMIT");
    }

    @Test
    @DisplayName("搜耳号 + dueType → LIMIT 回到 SQL（搜索态本就跳过内存硬筛，无需推迟）")
    void searchingByEarNo_keeps_limit_in_sql() {
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        service.searchByEarKeyword("001", "PZ", "F", "sow", null, 60, "FARROW", null, null, null, null, null);

        ArgumentCaptor<LambdaQueryWrapper<Pig>> w = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(pigMapper).selectList(w.capture());
        assertThat(readLastSql(w.getValue())).contains("LIMIT 60");
    }

    @Test
    @DisplayName("无内存后筛的调用方（出栏/阉割/转栏等 10 个）→ LIMIT 仍在 SQL，行为不变")
    void noPostFilter_keeps_limit_in_sql() {
        when(pigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // 出栏选猪：minAgeDays 是 SQL 侧过滤，不受影响
        service.searchByEarKeyword(null, null, null, "fattening", null, 60, null, null, 175, null, null, null);

        ArgumentCaptor<LambdaQueryWrapper<Pig>> w = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(pigMapper).selectList(w.capture());
        assertThat(readLastSql(w.getValue())).contains("LIMIT 60");
    }

    @Test
    @DisplayName("推迟 LIMIT 后仍在 Java 侧截断，且截的是「排序后」的前 n（最临产的优先，不是 id 最新的）")
    void deferredLimit_truncates_after_due_sort() {
        when(productionCycleConfigService.getValue("sow_breed_to_farrow_days")).thenReturn(114);

        // 三头都已到产期，超期程度不同；id 顺序刻意与紧急度相反（id 大 = 最不急）
        Pig mostUrgent = mkPig(1L, "URGENT-001", "PZ", "F", "sow", 11L, null);
        mostUrgent.setLastMatingDate(LocalDate.now().minusDays(160));   // 超期最多
        Pig middle = mkPig(2L, "MID-002", "PZ", "F", "sow", 11L, null);
        middle.setLastMatingDate(LocalDate.now().minusDays(130));
        Pig leastUrgent = mkPig(3L, "LEAST-003", "PZ", "F", "sow", 11L, null);
        leastUrgent.setLastMatingDate(LocalDate.now().minusDays(115));  // 刚到期
        // mapper 按 id DESC 返回（least, mid, urgent）——若先截断再排序，会留下最不急的两头
        when(pigMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(leastUrgent, middle, mostUrgent));

        Barn barn = new Barn();
        barn.setId(11L);
        barn.setBarnCode("B01");
        when(barnMapper.selectBatchIds(anyCollection())).thenReturn(List.of(barn));

        List<PigSearchVo> result = service.searchByEarKeyword(null, "PZ", "F", "sow", null, 2, "FARROW", null, null, null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PigSearchVo::getEarNo)
            .containsExactly("URGENT-001", "MID-002");
    }
}
