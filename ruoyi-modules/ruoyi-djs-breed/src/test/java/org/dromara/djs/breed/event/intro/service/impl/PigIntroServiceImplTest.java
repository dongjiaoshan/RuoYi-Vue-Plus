package org.dromara.djs.breed.event.intro.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigCreateBo;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.intro.domain.PigIntroduce;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.event.intro.domain.bo.PigIntroBatchBo;
import org.dromara.djs.breed.event.intro.domain.bo.PigIntroBo;
import org.dromara.djs.breed.event.intro.domain.query.PigIntroQuery;
import org.dromara.djs.breed.event.intro.domain.vo.IntroRecordVo;
import org.dromara.djs.breed.event.intro.domain.vo.PigIntroResultVo;
import org.dromara.djs.breed.event.intro.mapper.PigIntroduceMapper;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.supplier.domain.Supplier;
import org.dromara.djs.common.supplier.mapper.SupplierMapper;
import org.dromara.djs.common.validate.BizReferenceChecker;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PigIntroServiceImpl} 单元测试（BRD-EVENT-001）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>happy：外部引种母猪 → INSERT introduce + createPig(F→HB) + pen.current_count++</li>
 *   <li>外部引种缺 supplierId → 抛 intro.external.supplier_required</li>
 *   <li>外部引种缺 proofOssIds → 抛 intro.external.proof_required</li>
 *   <li>supplier_type != 'breed' → 抛 intro.supplier_type_invalid</li>
 *   <li>pen.barn_id 与传入 barn_id 不一致 → 抛 intro.pen_barn_mismatch</li>
 *   <li>pen 容量不足 → 抛 intro.pen_capacity_exceeded</li>
 *   <li>批量引种 happy：generateBatch + N 次 createPig + pen.current_count+=N</li>
 *   <li>内部引种（internal）：无 supplier / 无 proof 也通过</li>
 * </ul>
 *
 * @author djs
 * @since BRD-EVENT-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PigIntroServiceImpl 单元测试 (BRD-EVENT-001)")
class PigIntroServiceImplTest {

    private static final Long SUPPLIER_ID = 9000L;
    private static final Long BARN_ID = 8000L;
    private static final Long PEN_ID = 8100L;

    @Mock
    private PigIntroduceMapper introduceMapper;
    @Mock
    private IPigCoreService pigCoreService;
    @Mock
    private IBizCodeGenerator bizCodeGenerator;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private BarnMapper barnMapper;
    @Mock
    private PenMapper penMapper;
    @Mock
    private BizReferenceChecker bizReferenceChecker;
    @Mock
    private org.dromara.djs.breed.core.service.EarNoAllocator earNoAllocator;

    private PigIntroServiceImpl service;
    private org.dromara.djs.breed.core.mapper.PigMapper innerPigMapper;
    private org.dromara.common.core.service.DictService dictService;
    private org.dromara.common.core.service.OssService ossService;
    private org.dromara.djs.breed.event.transfer.service.ITransferService transferService;

    @BeforeEach
    void setup() {
        innerPigMapper = org.mockito.Mockito.mock(org.dromara.djs.breed.core.mapper.PigMapper.class);
        dictService = org.mockito.Mockito.mock(org.dromara.common.core.service.DictService.class);
        ossService = org.mockito.Mockito.mock(org.dromara.common.core.service.OssService.class);
        transferService = org.mockito.Mockito.mock(org.dromara.djs.breed.event.transfer.service.ITransferService.class);
        service = new PigIntroServiceImpl(
            introduceMapper, pigCoreService, bizCodeGenerator,
            supplierMapper, barnMapper, penMapper, bizReferenceChecker,
            innerPigMapper,
            dictService,
            earNoAllocator,
            ossService,
            transferService);
        // 用户填首号路径默认无撞号（existsEarNo 返 null 放行）
        when(innerPigMapper.existsEarNo(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        // 通用 stubs
        when(bizCodeGenerator.generate(eq(BizCodeType.INTRO_NO), anyMap()))
            .thenReturn("INT202605260001");
        // 单头耳号走 EarNoAllocator（ADR-0011：品系+品种+公母+出生日 → 带分隔符格式）
        when(earNoAllocator.allocateOne(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn("4-04-251200-001");
        Supplier breedSupplier = new Supplier();
        breedSupplier.setId(SUPPLIER_ID);
        breedSupplier.setSupplierType("breed");
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(breedSupplier);
        Barn barn = new Barn();
        barn.setId(BARN_ID);
        barn.setBarnCode("A1");
        when(barnMapper.selectById(BARN_ID)).thenReturn(barn);
        Pen pen = new Pen();
        pen.setId(PEN_ID);
        pen.setBarnId(BARN_ID);
        pen.setCapacity(50);
        pen.setCurrentCount(10);
        when(penMapper.selectById(PEN_ID)).thenReturn(pen);
        // introduceMapper.insert 模拟自增 id 注入
        AtomicLong introIdSeq = new AtomicLong(7777L);
        when(introduceMapper.insert(any(PigIntroduce.class))).thenAnswer(inv -> {
            PigIntroduce intro = inv.getArgument(0);
            intro.setId(introIdSeq.getAndIncrement());
            return 1;
        });
        // createPig 默认返母猪 HB
        when(pigCoreService.createPig(any(PigCreateBo.class))).thenAnswer(inv -> {
            PigCreateBo bo = inv.getArgument(0);
            Pig pig = new Pig();
            pig.setId(1234L);
            pig.setEarNo(bo.getEarNo());
            pig.setEarTag(bo.getEarTag());
            pig.setPigSex(bo.getPigSex());
            pig.setPigType(bo.getPigType());
            pig.setCurrentStatus("F".equals(bo.getPigSex()) ? "HB" : "");
            return pig;
        });
    }

    @Test
    @DisplayName("happy: 外部引种 1 头母猪 → HB + status_record(INTRO) + pen+1")
    void happy_external_single_sow() {
        PigIntroBo bo = mkSingleBo("external", "F");
        PigIntroResultVo result = service.introduce(bo);

        assertThat(result.getIntroduce().getIntroduceNo()).isEqualTo("INT202605260001");
        assertThat(result.getIntroduce().getPigCount()).isEqualTo(1);
        assertThat(result.getPigs()).hasSize(1);
        assertThat(result.getPigs().get(0).getCurrentStatus()).isEqualTo("HB");
        assertThat(result.getPigs().get(0).getPigType()).isEqualTo("sow");

        ArgumentCaptor<PigCreateBo> captor = ArgumentCaptor.forClass(PigCreateBo.class);
        verify(pigCoreService).createPig(captor.capture());
        assertThat(captor.getValue().getEarNo()).isEqualTo("4-04-251200-001");
        assertThat(captor.getValue().getPigSex()).isEqualTo("F");
        assertThat(captor.getValue().getSupplierId()).isEqualTo(SUPPLIER_ID);

        verify(penMapper, times(1)).update(eq(null), any());
    }

    @Test
    @DisplayName("公猪引种 → 空状态('')（非种母猪无繁殖态，ADR-0016）")
    void happy_boar() {
        PigIntroBo bo = mkSingleBo("external", "M");
        PigIntroResultVo result = service.introduce(bo);
        assertThat(result.getPigs().get(0).getCurrentStatus()).isEqualTo("");
        assertThat(result.getPigs().get(0).getPigType()).isEqualTo("boar");
    }

    @Test
    @DisplayName("外部引种缺 supplierId → 抛 intro.external.supplier_required")
    void external_missing_supplier() {
        PigIntroBo bo = mkSingleBo("external", "F");
        bo.setSupplierId(null);
        assertThatThrownBy(() -> service.introduce(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("intro.external.supplier_required");
        verify(pigCoreService, never()).createPig(any());
        verify(introduceMapper, never()).insert(any(PigIntroduce.class));
    }

    @Test
    @DisplayName("外部引种缺 proofOssIds → 抛 intro.external.proof_required")
    void external_missing_proof() {
        PigIntroBo bo = mkSingleBo("external", "F");
        bo.setProofOssIds(null);
        assertThatThrownBy(() -> service.introduce(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("intro.external.proof_required");
        verify(pigCoreService, never()).createPig(any());
    }

    @Test
    @DisplayName("supplier_type != 'breed' → 抛 intro.supplier_type_invalid")
    void supplier_type_not_breed() {
        Supplier feed = new Supplier();
        feed.setId(SUPPLIER_ID);
        feed.setSupplierType("feed");
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(feed);

        PigIntroBo bo = mkSingleBo("external", "F");
        assertThatThrownBy(() -> service.introduce(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("intro.supplier_type_invalid");
    }

    @Test
    @DisplayName("pen.barn_id 不一致 → 抛 intro.pen_barn_mismatch")
    void pen_barn_mismatch() {
        Pen pen = new Pen();
        pen.setId(PEN_ID);
        pen.setBarnId(9999L);  // 与 BARN_ID 不一致
        pen.setCapacity(50);
        pen.setCurrentCount(10);
        when(penMapper.selectById(PEN_ID)).thenReturn(pen);

        PigIntroBo bo = mkSingleBo("external", "F");
        assertThatThrownBy(() -> service.introduce(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("intro.pen_barn_mismatch");
    }

    @Test
    @DisplayName("pen 容量不足 → 抛 intro.pen_capacity_exceeded")
    void pen_capacity_exceeded() {
        Pen pen = new Pen();
        pen.setId(PEN_ID);
        pen.setBarnId(BARN_ID);
        pen.setCapacity(11);
        pen.setCurrentCount(10);
        when(penMapper.selectById(PEN_ID)).thenReturn(pen);

        PigIntroBatchBo bo = new PigIntroBatchBo();
        copyFromSingle(mkSingleBo("external", "F"), bo);
        bo.setPigCount(5);  // 剩余 1，请求 5 → 失败
        bo.setStartEarNo("BATCH-A001");

        assertThatThrownBy(() -> service.introduceBatch(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("intro.pen_capacity_exceeded");
    }

    @Test
    @DisplayName("内部引种（internal）：无 supplier / 无 proof 也通过")
    void internal_ok_without_supplier() {
        PigIntroBo bo = mkSingleBo("internal", "F");
        bo.setSupplierId(null);
        bo.setProofOssIds(null);

        PigIntroResultVo result = service.introduce(bo);
        assertThat(result.getPigs()).hasSize(1);
        assertThat(result.getIntroduce().getIntroduceType()).isEqualTo("internal");
        assertThat(result.getIntroduce().getSupplierId()).isNull();
    }

    @Test
    @DisplayName("批量引种 5 头母猪后端生成（startEarNo 空）：EarNoAllocator 连号 + 5 次 createPig + pen 加 5")
    void happy_batch_5_sows() {
        when(earNoAllocator.allocate(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), eq(5)))
            .thenReturn(List.of(
                "4-04-251200-001", "4-04-251200-002", "4-04-251200-003",
                "4-04-251200-004", "4-04-251200-005"));

        PigIntroBatchBo bo = new PigIntroBatchBo();
        copyFromSingle(mkSingleBo("external", "F"), bo);
        bo.setPigCount(5);
        // startEarNo 空 → 走后端 allocator 生成

        PigIntroResultVo result = service.introduceBatch(bo);

        assertThat(result.getIntroduce().getPigCount()).isEqualTo(5);
        assertThat(result.getPigs()).hasSize(5);
        assertThat(result.getPigs()).allMatch(s -> "HB".equals(s.getCurrentStatus()));

        verify(earNoAllocator).allocate(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), eq(5));
        verify(pigCoreService, times(5)).createPig(any(PigCreateBo.class));
        // pen.current_count += 5 via setSql wrapper
        verify(penMapper, times(1)).update(eq(null), any());
    }

    @Test
    @DisplayName("批量引种用户填合法带分隔符 startEarNo → 以用户首号为起点连号（601-5 用户可改），不调 allocator")
    void batch_userStartEarNo_overrides() {
        PigIntroBatchBo bo = new PigIntroBatchBo();
        copyFromSingle(mkSingleBo("external", "F"), bo);
        bo.setPigCount(3);
        bo.setStartEarNo("4-04-251200-010");  // 用户改首号 → 后续 0011 / 0012

        PigIntroResultVo result = service.introduceBatch(bo);

        assertThat(result.getPigs()).hasSize(3);
        assertThat(result.getPigs().get(0).getEarNo()).isEqualTo("4-04-251200-010");
        assertThat(result.getPigs().get(1).getEarNo()).isEqualTo("4-04-251200-011");
        assertThat(result.getPigs().get(2).getEarNo()).isEqualTo("4-04-251200-012");
        // 走用户首号路径，不调 allocator.allocate
        verify(earNoAllocator, times(0)).allocate(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("D12X-INTRO-EARNO 外部引种单头走批量端点 pigCount=1 + 用户改带分隔符 startEarNo → 落库 earNo = 用户填的（@Min 放开到 1）")
    void batch_count1_userStartEarNo() {
        PigIntroBatchBo bo = new PigIntroBatchBo();
        copyFromSingle(mkSingleBo("external", "F"), bo);
        bo.setPigCount(1);                       // 单头走批量端点以消费 startEarNo
        bo.setStartEarNo("4-04-251200-888");  // 用户填的合法带分隔符首号

        PigIntroResultVo result = service.introduceBatch(bo);

        assertThat(result.getIntroduce().getPigCount()).isEqualTo(1);
        assertThat(result.getIntroduce().getStartEarNo()).isEqualTo("4-04-251200-888");
        assertThat(result.getPigs()).hasSize(1);
        // 落库 earNo = 用户填的首号（不调后端 allocator）
        assertThat(result.getPigs().get(0).getEarNo()).isEqualTo("4-04-251200-888");
        verify(earNoAllocator, times(0)).allocate(any(), any(), any(), any(), anyInt());
        // pen.current_count += 1
        verify(penMapper, times(1)).update(eq(null), any());
    }

    @Test
    @DisplayName("FIX-CC-EARNO 用户首号 seq < 后台最小可用号 → 抛 intro.start_ear_no.too_small（不 createPig）")
    void batch_userStartEarNo_belowMin_throws() {
        when(earNoAllocator.nextSeqForDateSegment(org.mockito.ArgumentMatchers.anyString())).thenReturn(11L);

        PigIntroBatchBo bo = new PigIntroBatchBo();
        copyFromSingle(mkSingleBo("external", "F"), bo);
        bo.setPigCount(1);
        bo.setStartEarNo("4-04-251200-010");  // seq=10 < 后台 min 11

        assertThatThrownBy(() -> service.introduceBatch(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("intro.start_ear_no.too_small");
        verify(pigCoreService, never()).createPig(any());
    }

    @Test
    @DisplayName("FIX-CC-EARNO 用户首号 seq == 后台最小可用号 → 放行，以用户首号连号")
    void batch_userStartEarNo_atMin_ok() {
        when(earNoAllocator.nextSeqForDateSegment(org.mockito.ArgumentMatchers.anyString())).thenReturn(10L);

        PigIntroBatchBo bo = new PigIntroBatchBo();
        copyFromSingle(mkSingleBo("external", "F"), bo);
        bo.setPigCount(2);
        bo.setStartEarNo("4-04-251200-010");  // seq=10 == min 10 → OK

        PigIntroResultVo result = service.introduceBatch(bo);

        assertThat(result.getPigs()).hasSize(2);
        assertThat(result.getPigs().get(0).getEarNo()).isEqualTo("4-04-251200-010");
        assertThat(result.getPigs().get(1).getEarNo()).isEqualTo("4-04-251200-011");
    }

    @Test
    @DisplayName("INTRO_NO 编码生成器被调用一次（每次 introduce 一行业务表）")
    void intro_no_generated_once() {
        PigIntroBo bo = mkSingleBo("external", "F");
        service.introduce(bo);
        verify(bizCodeGenerator, times(1)).generate(eq(BizCodeType.INTRO_NO), anyMap());
    }

    @Test
    @DisplayName("code 路径：barnCode + penCode 二选一 → service 解析成 id 后走原逻辑")
    void resolve_barn_pen_by_code() {
        // 准备 lambdaQuery selectOne 的 stub —— 用 any() 匹配 Wrapper，按调用顺序返回
        Barn barn = new Barn();
        barn.setId(BARN_ID);
        barn.setBarnCode("1");
        when(barnMapper.selectOne(any())).thenReturn(barn);
        Pen pen = new Pen();
        pen.setId(PEN_ID);
        pen.setBarnId(BARN_ID);
        pen.setPenCode("11");
        pen.setCapacity(50);
        pen.setCurrentCount(10);
        when(penMapper.selectOne(any())).thenReturn(pen);

        PigIntroBo bo = mkSingleBo("external", "F");
        bo.setBarnId(null);
        bo.setPenId(null);
        bo.setBarnCode("1");
        bo.setPenCode("11");

        PigIntroResultVo result = service.introduce(bo);
        assertThat(result.getPigs()).hasSize(1);
        // resolve 后 bo 上的 id 已被回填，下游 createPig 收到正确 barn/pen id
        ArgumentCaptor<PigCreateBo> captor = ArgumentCaptor.forClass(PigCreateBo.class);
        verify(pigCoreService).createPig(captor.capture());
        assertThat(captor.getValue().getBarnId()).isEqualTo(BARN_ID);
        assertThat(captor.getValue().getPenId()).isEqualTo(PEN_ID);
    }

    @Test
    @DisplayName("mp supplierCode 路径误填 feed 类供应商 → 解析处即抛 intro.supplier_code_type_invalid（友好报错非 500）")
    void resolve_supplier_code_not_breed() {
        // mp 工人手输 supplierCode（无 breed 过滤 picker），解析到 feed 类供应商
        Supplier feed = new Supplier();
        feed.setId(SUPPLIER_ID);
        feed.setSupplierType("feed");
        when(supplierMapper.selectOne(any())).thenReturn(feed);

        PigIntroBo bo = mkSingleBo("external", "F");
        bo.setSupplierId(null);
        bo.setSupplierCode("FEED0001");

        assertThatThrownBy(() -> service.introduce(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("intro.supplier_code_type_invalid");
        // 解析处即拦截，绝不进 INSERT / createPig
        verify(introduceMapper, never()).insert(any(PigIntroduce.class));
        verify(pigCoreService, never()).createPig(any());
    }

    @Test
    @DisplayName("mp supplierCode 路径填 breed 类供应商 → 解析通过走原引种逻辑")
    void resolve_supplier_code_breed_ok() {
        Supplier breed = new Supplier();
        breed.setId(SUPPLIER_ID);
        breed.setSupplierType("breed");
        when(supplierMapper.selectOne(any())).thenReturn(breed);

        PigIntroBo bo = mkSingleBo("external", "F");
        bo.setSupplierId(null);
        bo.setSupplierCode("BREED001");

        PigIntroResultVo result = service.introduce(bo);
        assertThat(result.getPigs()).hasSize(1);
        // 解析后 supplierId 回填，下游 createPig 收到正确 supplierId
        ArgumentCaptor<PigCreateBo> captor = ArgumentCaptor.forClass(PigCreateBo.class);
        verify(pigCoreService).createPig(captor.capture());
        assertThat(captor.getValue().getSupplierId()).isEqualTo(SUPPLIER_ID);
    }

    @Test
    @DisplayName("barnCode 找不到 → 抛 intro.barn_not_found")
    void barn_code_not_found() {
        when(barnMapper.selectOne(any())).thenReturn(null);

        PigIntroBo bo = mkSingleBo("external", "F");
        bo.setBarnId(null);
        bo.setBarnCode("nonexistent");

        assertThatThrownBy(() -> service.introduce(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("intro.barn_not_found");
        verify(introduceMapper, never()).insert(any(PigIntroduce.class));
    }

    @Test
    @DisplayName("PostConstruct：注册 supplier 引用关系给 BizReferenceChecker")
    void registers_supplier_references() {
        service.registerReferences();
        verify(bizReferenceChecker).register("t_md_supplier", "t_farm_pig_introduce", "supplier_id");
        verify(bizReferenceChecker).register("t_md_supplier", "t_farm_pig_info", "supplier_id");
    }

    @Test
    @DisplayName("D12X-INTRO 外部引种 persist 落 operator + introduceWeight（601-3 人员 / D2 体重）")
    void external_persists_operator_and_weight() {
        PigIntroBo bo = mkSingleBo("external", "F");
        bo.setOperator("1900000001");
        bo.setIntroduceWeight(new BigDecimal("85.50"));

        service.introduce(bo);

        ArgumentCaptor<PigIntroduce> captor = ArgumentCaptor.forClass(PigIntroduce.class);
        verify(introduceMapper).insert(captor.capture());
        assertThat(captor.getValue().getOperator()).isEqualTo("1900000001");
        assertThat(captor.getValue().getIntroduceWeight()).isEqualByComparingTo("85.50");
    }

    @Test
    @DisplayName("D12X-INTRO 引种记录：内部翻品系 label + join Pig 算日龄，外部 ageDays=null（601-6）")
    void records_strain_label_and_age_days() {
        // 内部引种一行：pigId 关联猪只 birth_date → 算日龄；外部引种一行：pigId=null → ageDays null
        PigIntroduce internal = new PigIntroduce();
        internal.setIntroduceNo("INT-INTERNAL");
        internal.setIntroduceType("internal");
        internal.setStartEarNo("4-04-251200-001");
        internal.setPigCount(1);
        internal.setPigBreedCode("04");
        internal.setPigStrainCode("4");
        internal.setIntroduceDate(LocalDate.of(2026, 6, 1));
        internal.setPigId(5001L);
        internal.setOperator("1900000001");

        PigIntroduce external = new PigIntroduce();
        external.setIntroduceNo("INT-EXTERNAL");
        external.setIntroduceType("external");
        external.setStartEarNo("4-04-251200-099");
        external.setPigCount(1);
        external.setPigBreedCode("04");
        external.setPigStrainCode("4");
        external.setIntroduceDate(LocalDate.of(2026, 6, 1));
        external.setPigId(null);
        external.setProofOssIds("100,101");

        Page<PigIntroduce> page = new Page<>(1, 10, 2);
        page.setRecords(List.of(internal, external));
        when(introduceMapper.selectPage(any(), any())).thenReturn(page);

        // 外部引种凭证图 ossId → 可访问 URL（service JOIN sys_oss resolve）
        when(ossService.selectUrlByIds(eq("100,101"))).thenReturn("https://oss/a.jpg,https://oss/b.jpg");

        // pigId=5001 的猪 birth_date 在 50 天前
        LocalDate birth = LocalDate.now().minusDays(50);
        Pig pig = new Pig();
        pig.setId(5001L);
        pig.setBirthDate(birth);
        when(innerPigMapper.selectBatchIds(any())).thenReturn(List.of(pig));

        // 品系字典翻译命中
        when(dictService.getDictLabel(eq("djs_pig_strain"), eq("4"))).thenReturn("杜洛克");
        when(dictService.getDictLabel(eq("djs_pig_breed"), eq("04"))).thenReturn("杜洛克");

        TableDataInfo<IntroRecordVo> result = service.queryAppletRecords(new PigIntroQuery(), new PageQuery(1, 10));
        List<IntroRecordVo> rows = result.getRows();
        assertThat(rows).hasSize(2);

        IntroRecordVo internalVo = rows.stream().filter(r -> "INT-INTERNAL".equals(r.getIntroduceNo())).findFirst().orElseThrow();
        assertThat(internalVo.getPigStrainLabel()).isEqualTo("杜洛克");
        assertThat(internalVo.getAgeDays()).isEqualTo(50);
        // VO 仍保留 pigCount（前端不渲染，但 payload 在）
        assertThat(internalVo.getPigCount()).isEqualTo(1);
        // 内部引种无凭证图 → 空 list
        assertThat(internalVo.getProofImageUrls()).isEmpty();

        IntroRecordVo externalVo = rows.stream().filter(r -> "INT-EXTERNAL".equals(r.getIntroduceNo())).findFirst().orElseThrow();
        assertThat(externalVo.getPigStrainLabel()).isEqualTo("杜洛克");
        assertThat(externalVo.getAgeDays()).isNull();
        // 外部引种凭证图 resolve 出可访问 URL 列表
        assertThat(externalVo.getProofImageUrls()).containsExactly("https://oss/a.jpg", "https://oss/b.jpg");
    }

    // ---------- 测试夹具 ----------

    private PigIntroBo mkSingleBo(String type, String sex) {
        PigIntroBo bo = new PigIntroBo();
        bo.setIntroduceType(type);
        bo.setIntroduceDate(LocalDate.of(2026, 5, 26));
        bo.setSupplierId(SUPPLIER_ID);
        bo.setProofOssIds("100,101");
        bo.setPigSex(sex);
        // ADR-0011 位码：品系=4 杜洛克 / 品种=04 杜洛克
        bo.setPigBreedCode("04");
        bo.setPigStrainCode("4");
        bo.setBirthDate(LocalDate.of(2025, 12, 1));
        bo.setBarnId(BARN_ID);
        bo.setPenId(PEN_ID);
        bo.setRemark("unit-test");
        return bo;
    }

    private void copyFromSingle(PigIntroBo src, PigIntroBo dst) {
        dst.setIntroduceType(src.getIntroduceType());
        dst.setIntroduceDate(src.getIntroduceDate());
        dst.setSupplierId(src.getSupplierId());
        dst.setProofOssIds(src.getProofOssIds());
        dst.setPigSex(src.getPigSex());
        dst.setPigBreedCode(src.getPigBreedCode());
        dst.setPigStrainCode(src.getPigStrainCode());
        dst.setBirthDate(src.getBirthDate());
        dst.setBarnId(src.getBarnId());
        dst.setPenId(src.getPenId());
        dst.setRemark(src.getRemark());
    }
}
