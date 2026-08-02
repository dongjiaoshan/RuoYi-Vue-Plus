package org.dromara.djs.breed.med.record.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.med.mapper.MedBatchMapper;
import org.dromara.djs.breed.med.record.domain.MedRecord;
import org.dromara.djs.breed.med.record.domain.bo.MedRecordBatchBo;
import org.dromara.djs.breed.med.record.domain.bo.MedRecordBo;
import org.dromara.djs.breed.med.record.mapper.MedRecordMapper;
import org.dromara.djs.common.medicine.api.MedicineProductDto;
import org.dromara.djs.common.medicine.api.MedicineStockProvider;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MedRecordServiceImpl} 单测（BRD-MED-003 · 药品维度，废弃批次）。
 *
 * <p>覆盖（用药 = 纯记录，不扣库存；库存扣减只在 MedUsage 领用/退回做）：</p>
 * <ul>
 *   <li>addSingle happy path：INSERT 1 条 drug_type=1，且不调 provider.deduct；</li>
 *   <li>addBatch happy path：1 master + N detail，且不调 provider.deduct；</li>
 *   <li>药品不存在（provider 返空）→ 拒绝；</li>
 *   <li>批量 N > 200 拒绝；pig 终态 END 拒绝；部分 pig 不存在拒绝；</li>
 *   <li>软删不回滚库存。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-MED-003
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MedRecordServiceImpl 单元测试")
class MedRecordServiceImplTest {

    private static final Long MEDICINE_ID = 40001L;

    @Mock
    private MedRecordMapper medRecordMapper;
    @Mock
    private MedBatchMapper medBatchMapper;
    @Mock
    private PigMapper pigMapper;
    @Mock
    private MedicineStockProvider medicineStockProvider;
    @Mock
    private org.dromara.djs.breed.production.service.IProductionCycleConfigService cycleConfigService;

    private TestableMedRecordServiceImpl service;

    static class TestableMedRecordServiceImpl extends MedRecordServiceImpl {
        TestableMedRecordServiceImpl(MedRecordMapper r, MedBatchMapper b,
                                     PigMapper p, MedicineStockProvider sp,
                                     org.dromara.djs.breed.production.service.IProductionCycleConfigService cc) {
            super(r, b, p, sp, cc);
        }

        @Override
        protected MedRecord toEntity(MedRecordBo bo) {
            MedRecord r = new MedRecord();
            r.setUseDate(bo.getUseDate());
            r.setPigId(bo.getPigId());
            r.setMedicineType(bo.getMedicineType());
            r.setMedicineReason(bo.getMedicineReason());
            r.setMedicineWay(bo.getMedicineWay());
            r.setMedicineId(bo.getMedicineId());
            r.setBatchId(bo.getBatchId());
            r.setUsageId(bo.getUsageId());
            r.setScheduleId(bo.getScheduleId());
            r.setMedicineDosage(bo.getMedicineDosage());
            r.setDosageUnit(bo.getDosageUnit());
            r.setRemark(bo.getRemark());
            return r;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableMedRecordServiceImpl(
            medRecordMapper, medBatchMapper, pigMapper, medicineStockProvider, cycleConfigService);
    }

    // ---------------- 工具方法 ----------------

    /** 仓库 provider 返回的药品商品行（药品即仓库商品，名从 provider 拿）。 */
    private List<MedicineProductDto> medicineProduct() {
        MedicineProductDto dto = new MedicineProductDto();
        dto.setId(MEDICINE_ID);
        dto.setName("青霉素");
        dto.setUnit("瓶");
        dto.setSpec("10ml/瓶");
        dto.setStock(new BigDecimal("100.000"));
        return List.of(dto);
    }

    private Pig pig(long id, String earNo, String status) {
        Pig p = new Pig();
        p.setId(id);
        p.setEarNo(earNo);
        p.setCurrentStatus(status);
        p.setDelFlag("0");
        return p;
    }

    private MedRecordBo singleBo(String dosage) {
        MedRecordBo bo = new MedRecordBo();
        bo.setUseDate(LocalDateTime.of(2026, 5, 27, 9, 30));
        bo.setPigId(100123L);
        bo.setMedicineType("treatment");
        bo.setMedicineWay("injection");
        bo.setMedicineId(MEDICINE_ID);
        bo.setMedicineDosage(new BigDecimal(dosage));
        return bo;
    }

    // ---------------- 单只用药 ----------------

    @Test
    @DisplayName("addSingle: happy path → INSERT 1 条 drug_type=1，且不扣库存（用药纯记录）")
    void testAddSingle_HappyPath() {
        when(medicineStockProvider.listMedicineProductsByIds(anyCollection())).thenReturn(medicineProduct());
        when(pigMapper.selectById(100123L)).thenReturn(pig(100123L, "260501-001", "PZ"));
        when(medRecordMapper.insert(any(MedRecord.class))).thenReturn(1);

        int rows = service.addSingle(singleBo("3.500"));

        assertThat(rows).isEqualTo(1);

        // 用药不扣库存（库存扣减只在领用/退回做，用药再扣会双扣）
        verify(medicineStockProvider, never()).deduct(any(), any(), any());

        ArgumentCaptor<MedRecord> captor = ArgumentCaptor.forClass(MedRecord.class);
        verify(medRecordMapper, times(1)).insert(captor.capture());
        MedRecord saved = captor.getValue();
        assertThat(saved.getDrugType()).isEqualTo(1);
        assertThat(saved.getEarNo()).isEqualTo("260501-001");
        assertThat(saved.getMedicineName()).isEqualTo("青霉素");
    }

    @Test
    @DisplayName("addSingle: pig 终态 END → 拒绝，不扣库存")
    void testAddSingle_PigEnd() {
        when(medicineStockProvider.listMedicineProductsByIds(anyCollection())).thenReturn(medicineProduct());
        when(pigMapper.selectById(100123L)).thenReturn(pig(100123L, "260501-001", "END"));

        assertThatThrownBy(() -> service.addSingle(singleBo("1.0")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("终态");

        verify(medicineStockProvider, never()).deduct(any(), any(), any());
    }

    @Test
    @DisplayName("addSingle: 药品不存在（provider 返空）→ 拒绝")
    void testAddSingle_MedicineNotFound() {
        when(medicineStockProvider.listMedicineProductsByIds(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> service.addSingle(singleBo("1.0")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("药品不存在");

        verify(medicineStockProvider, never()).deduct(any(), any(), any());
    }

    // ---------------- 批量用药 ----------------

    private MedRecordBatchBo batchBo(int n, String dosage) {
        MedRecordBatchBo bo = new MedRecordBatchBo();
        bo.setUseDate(LocalDateTime.of(2026, 5, 27, 9, 30));
        List<Long> ids = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ids.add(100000L + i);
        }
        bo.setPigIds(ids);
        bo.setMedicineType("treatment");
        bo.setMedicineWay("injection");
        bo.setMedicineId(MEDICINE_ID);
        bo.setMedicineDosage(new BigDecimal(dosage));
        return bo;
    }

    @Test
    @DisplayName("addBatch: N=3 happy path → 1 master + 3 detail，master 存合计用量，且不扣库存")
    void testAddBatch_HappyPath() {
        when(medicineStockProvider.listMedicineProductsByIds(anyCollection())).thenReturn(medicineProduct());
        when(pigMapper.selectByIds(anyCollection())).thenReturn(List.of(
            pig(100000L, "A001", "PZ"),
            pig(100001L, "A002", "FM"),
            pig(100002L, "A003", "DN")
        ));
        when(medRecordMapper.insert(any(MedRecord.class))).thenReturn(1);
        when(medRecordMapper.insertBatch(any())).thenReturn(true);

        Long masterId = service.addBatch(batchBo(3, "2.000"));

        // 用药不扣库存（纯记录）
        verify(medicineStockProvider, never()).deduct(any(), any(), any());

        // master 行存合计用量 2 × 3 = 6.000
        ArgumentCaptor<MedRecord> masterCaptor = ArgumentCaptor.forClass(MedRecord.class);
        verify(medRecordMapper, times(1)).insert(masterCaptor.capture());
        assertThat(masterCaptor.getValue().getMedicineDosage()).isEqualByComparingTo("6.000");
        verify(medRecordMapper, times(1)).insertBatch(any());

        assertThat(masterId).satisfiesAnyOf(
            id -> assertThat(id).isNull(),
            id -> assertThat(id).isGreaterThan(0L)
        );
    }

    @Test
    @DisplayName("addBatch: N > 200 → 拒绝")
    void testAddBatch_TooLarge() {
        assertThatThrownBy(() -> service.addBatch(batchBo(201, "1.0")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("最多");

        verify(medicineStockProvider, never()).deduct(any(), any(), any());
    }

    @Test
    @DisplayName("addBatch: 部分 pig 不存在 → 拒绝")
    void testAddBatch_PigMissing() {
        when(medicineStockProvider.listMedicineProductsByIds(anyCollection())).thenReturn(medicineProduct());
        when(pigMapper.selectByIds(anyCollection())).thenReturn(List.of(
            pig(100000L, "A001", "PZ")
        ));

        assertThatThrownBy(() -> service.addBatch(batchBo(3, "1.0")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("部分猪只不存在");
    }

    @Test
    @DisplayName("addBatch: 含终态 END pig → 拒绝")
    void testAddBatch_PigEnd() {
        when(medicineStockProvider.listMedicineProductsByIds(anyCollection())).thenReturn(medicineProduct());
        when(pigMapper.selectByIds(anyCollection())).thenReturn(List.of(
            pig(100000L, "A001", "PZ"),
            pig(100001L, "A002", "END"),
            pig(100002L, "A003", "FM")
        ));

        assertThatThrownBy(() -> service.addBatch(batchBo(3, "1.0")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("终态");
    }

    @Test
    @DisplayName("deleteByIds: 软删 → 不调 provider.add 回滚库存")
    void testSoftDelete_NoStockRollback() {
        when(medRecordMapper.update(any(), any())).thenReturn(1);

        int rows = service.deleteByIds(List.of(60001L));

        assertThat(rows).isEqualTo(1);
        verify(medicineStockProvider, never()).add(any(), any(), any());
    }

    @Test
    @DisplayName("listUsableBatches: 使用药品数据源走仓库领用流水（provider.listRecentPickedMedicineIds），覆盖两个领用入口（row131）")
    void testListUsableBatches_FromWarehousePickFlow() {
        Long operatorId = 5001L;
        // 近 N 天已领药品 id 从仓库领用出库流水取（覆盖疫苗药品页 + 物资领用药品库两入口）
        when(medicineStockProvider.listRecentPickedMedicineIds(operatorId, 15)).thenReturn(List.of(MEDICINE_ID));
        when(medicineStockProvider.listMedicineProductsByIds(anyCollection())).thenReturn(medicineProduct());

        var list = service.listUsableBatches(operatorId);

        // 数据源必须是仓库流水，不再查 t_breed_medicine_usage 台账
        verify(medicineStockProvider).listRecentPickedMedicineIds(operatorId, 15);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getMedicineId()).isEqualTo(MEDICINE_ID);
        assertThat(list.get(0).getMedicineName()).isEqualTo("青霉素");
    }

    @Test
    @DisplayName("listUsableBatches: 近 N 天无领用 → 空列表，不再查药品详情")
    void testListUsableBatches_Empty() {
        Long operatorId = 5001L;
        when(medicineStockProvider.listRecentPickedMedicineIds(operatorId, 15)).thenReturn(List.of());

        var list = service.listUsableBatches(operatorId);

        assertThat(list).isEmpty();
        verify(medicineStockProvider, never()).listMedicineProductsByIds(anyCollection());
    }
}
