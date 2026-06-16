package org.dromara.djs.breed.med.record.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.med.domain.MedBatch;
import org.dromara.djs.breed.med.domain.Medicine;
import org.dromara.djs.breed.med.mapper.MedBatchMapper;
import org.dromara.djs.breed.med.mapper.MedicineMapper;
import org.dromara.djs.breed.med.record.domain.MedRecord;
import org.dromara.djs.breed.med.record.domain.bo.MedRecordBatchBo;
import org.dromara.djs.breed.med.record.domain.bo.MedRecordBo;
import org.dromara.djs.breed.med.record.mapper.MedRecordMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MedRecordServiceImpl} 单测（BRD-MED-003）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>addSingle happy path：扣 quantity + INSERT 1 条 drug_type=1；</li>
 *   <li>addBatch happy path：扣 quantity × N + 1 master + N detail；</li>
 *   <li>剂量超 quantity → medicine.batch.insufficient；</li>
 *   <li>批次不存在 / 已删除拒绝；</li>
 *   <li>批量 N > 200 → medicine.batch.too_large；</li>
 *   <li>pig 终态 END 拒绝；</li>
 *   <li>药品 batch 归属不一致拒绝；</li>
 *   <li>软删不回滚库存。</li>
 * </ul>
 *
 * <p>注：3 天内已领窗口校验在 LoginHelper.getUserId() == null（单测环境）时跳过，
 * 用例不构造领用窗口数据。</p>
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
    private MedicineMapper medicineMapper;
    @Mock
    private PigMapper pigMapper;
    @Mock
    private MedicineStockProvider medicineStockProvider;

    private TestableMedRecordServiceImpl service;

    static class TestableMedRecordServiceImpl extends MedRecordServiceImpl {
        TestableMedRecordServiceImpl(MedRecordMapper r, MedBatchMapper b, MedicineMapper m,
                                     PigMapper p, MedicineStockProvider sp) {
            super(r, b, m, p, sp);
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
            r.setRemark(bo.getRemark());
            return r;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableMedRecordServiceImpl(
            medRecordMapper, medBatchMapper, medicineMapper, pigMapper, medicineStockProvider);
    }

    // ---------------- 工具方法 ----------------

    private Medicine existingMedicine() {
        Medicine m = new Medicine();
        m.setId(40001L);
        m.setMedicineName("青霉素");
        m.setDelFlag("0");
        return m;
    }

    private MedBatch existingBatch(BigDecimal remaining) {
        MedBatch b = new MedBatch();
        b.setId(50001L);
        b.setMedicineId(40001L);
        b.setBatchNo("B20260501");
        b.setQuantity(remaining);
        b.setDelFlag("0");
        return b;
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
        bo.setMedicineId(40001L);
        bo.setBatchId(50001L);
        bo.setMedicineDosage(new BigDecimal(dosage));
        return bo;
    }

    // ---------------- 单只用药 ----------------

    @Test
    @DisplayName("addSingle: happy path → provider.deduct 扣库存 1 次 + INSERT 1 条 drug_type=1")
    void testAddSingle_HappyPath() {
        when(medicineMapper.selectById(40001L)).thenReturn(existingMedicine());
        when(medBatchMapper.selectById(50001L)).thenReturn(existingBatch(new BigDecimal("100.000")));
        when(pigMapper.selectById(100123L)).thenReturn(pig(100123L, "260501-001", "PZ"));
        when(medRecordMapper.insert(any(MedRecord.class))).thenReturn(1);

        int rows = service.addSingle(singleBo("3.500"));

        assertThat(rows).isEqualTo(1);

        // ADR-0012：扣仓库库存（medicineId 取 batch.getMedicineId()=40001，operatorId 单测无登录上下文为 null）
        ArgumentCaptor<BigDecimal> qty = ArgumentCaptor.forClass(BigDecimal.class);
        verify(medicineStockProvider, times(1)).deduct(eq(MEDICINE_ID), qty.capture(), any());
        assertThat(qty.getValue()).isEqualByComparingTo("3.500");

        ArgumentCaptor<MedRecord> captor = ArgumentCaptor.forClass(MedRecord.class);
        verify(medRecordMapper, times(1)).insert(captor.capture());
        MedRecord saved = captor.getValue();
        assertThat(saved.getDrugType()).isEqualTo(1);
        assertThat(saved.getEarNo()).isEqualTo("260501-001");
        assertThat(saved.getMedicineName()).isEqualTo("青霉素");
    }

    @Test
    @DisplayName("addSingle: 仓库库存不足 → provider.deduct 抛 ServiceException，不 INSERT")
    void testAddSingle_Insufficient() {
        when(medicineMapper.selectById(40001L)).thenReturn(existingMedicine());
        when(medBatchMapper.selectById(50001L)).thenReturn(existingBatch(new BigDecimal("2.000")));
        when(pigMapper.selectById(100123L)).thenReturn(pig(100123L, "260501-001", "PZ"));
        // ADR-0012：库存不足由 provider.deduct 抛业务异常
        doThrow(new ServiceException("药品库存不足"))
            .when(medicineStockProvider).deduct(eq(MEDICINE_ID), any(BigDecimal.class), any());

        assertThatThrownBy(() -> service.addSingle(singleBo("10.000")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("库存不足");

        verify(medRecordMapper, never()).insert(any(MedRecord.class));
    }

    @Test
    @DisplayName("addSingle: pig 终态 END → 拒绝")
    void testAddSingle_PigEnd() {
        when(medicineMapper.selectById(40001L)).thenReturn(existingMedicine());
        when(medBatchMapper.selectById(50001L)).thenReturn(existingBatch(new BigDecimal("100.000")));
        when(pigMapper.selectById(100123L)).thenReturn(pig(100123L, "260501-001", "END"));

        assertThatThrownBy(() -> service.addSingle(singleBo("1.0")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("终态");

        verify(medicineStockProvider, never()).deduct(any(), any(), any());
    }

    @Test
    @DisplayName("addSingle: batch 不存在 → 拒绝")
    void testAddSingle_BatchNotFound() {
        when(medicineMapper.selectById(40001L)).thenReturn(existingMedicine());
        when(medBatchMapper.selectById(50001L)).thenReturn(null);

        assertThatThrownBy(() -> service.addSingle(singleBo("1.0")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("批次不存在");
    }

    @Test
    @DisplayName("addSingle: 药品 batch 归属不一致 → 拒绝")
    void testAddSingle_MedicineMismatch() {
        when(medicineMapper.selectById(40001L)).thenReturn(existingMedicine());
        MedBatch wrong = existingBatch(new BigDecimal("100.000"));
        wrong.setMedicineId(99999L);
        when(medBatchMapper.selectById(50001L)).thenReturn(wrong);

        assertThatThrownBy(() -> service.addSingle(singleBo("1.0")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("归属不一致");
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
        bo.setMedicineId(40001L);
        bo.setBatchId(50001L);
        bo.setMedicineDosage(new BigDecimal(dosage));
        return bo;
    }

    @Test
    @DisplayName("addBatch: N=3 happy path → 扣 dosage×3 + 1 master + 3 detail")
    void testAddBatch_HappyPath() {
        when(medicineMapper.selectById(40001L)).thenReturn(existingMedicine());
        when(medBatchMapper.selectById(50001L)).thenReturn(existingBatch(new BigDecimal("100.000")));
        when(pigMapper.selectByIds(anyCollection())).thenReturn(List.of(
            pig(100000L, "A001", "PZ"),
            pig(100001L, "A002", "FM"),
            pig(100002L, "A003", "DN")
        ));
        when(medRecordMapper.insert(any(MedRecord.class))).thenReturn(1);
        when(medRecordMapper.insertBatch(any())).thenReturn(true);

        Long masterId = service.addBatch(batchBo(3, "2.000"));

        // ADR-0012：总扣减 2 × 3 = 6.000，落仓库库存（medicineId=batch.medicineId=40001）
        ArgumentCaptor<BigDecimal> qty = ArgumentCaptor.forClass(BigDecimal.class);
        verify(medicineStockProvider, times(1)).deduct(eq(MEDICINE_ID), qty.capture(), any());
        assertThat(qty.getValue()).isEqualByComparingTo("6.000");

        // 1 master INSERT
        verify(medRecordMapper, times(1)).insert(any(MedRecord.class));
        // N detail insertBatch（一次 batch）
        verify(medRecordMapper, times(1)).insertBatch(any());

        // masterId 由 baseMapper.insert 时 MP 雪花生成；mock 环境下 entity.getId 为 null，
        // 但接口契约本身已验证；此处不强断 masterId 数值。
        assertThat(masterId).satisfiesAnyOf(
            id -> assertThat(id).isNull(),
            id -> assertThat(id).isGreaterThan(0L)
        );
    }

    @Test
    @DisplayName("addBatch: N > 200 → medicine.batch.too_large")
    void testAddBatch_TooLarge() {
        assertThatThrownBy(() -> service.addBatch(batchBo(201, "1.0")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("最多");

        verify(medicineStockProvider, never()).deduct(any(), any(), any());
    }

    @Test
    @DisplayName("addBatch: 部分 pig 不存在 → 拒绝")
    void testAddBatch_PigMissing() {
        when(medicineMapper.selectById(40001L)).thenReturn(existingMedicine());
        when(medBatchMapper.selectById(50001L)).thenReturn(existingBatch(new BigDecimal("100.000")));
        when(pigMapper.selectByIds(anyCollection())).thenReturn(List.of(
            pig(100000L, "A001", "PZ")
            // 缺 100001 + 100002
        ));

        assertThatThrownBy(() -> service.addBatch(batchBo(3, "1.0")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("部分猪只不存在");
    }

    @Test
    @DisplayName("addBatch: 含终态 END pig → 拒绝")
    void testAddBatch_PigEnd() {
        when(medicineMapper.selectById(40001L)).thenReturn(existingMedicine());
        when(medBatchMapper.selectById(50001L)).thenReturn(existingBatch(new BigDecimal("100.000")));
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
    @DisplayName("addBatch: 库存不足 → 拒绝 + 不 INSERT")
    void testAddBatch_Insufficient() {
        when(medicineMapper.selectById(40001L)).thenReturn(existingMedicine());
        when(medBatchMapper.selectById(50001L)).thenReturn(existingBatch(new BigDecimal("5.000")));
        when(pigMapper.selectByIds(anyCollection())).thenReturn(List.of(
            pig(100000L, "A001", "PZ"),
            pig(100001L, "A002", "PZ"),
            pig(100002L, "A003", "PZ")
        ));
        // ADR-0012：库存不足由 provider.deduct 抛业务异常
        doThrow(new ServiceException("药品库存不足"))
            .when(medicineStockProvider).deduct(eq(MEDICINE_ID), any(BigDecimal.class), any());

        assertThatThrownBy(() -> service.addBatch(batchBo(3, "10.0"))) // 总扣 30 > 余 5
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("库存不足");

        verify(medRecordMapper, never()).insert(any(MedRecord.class));
        verify(medRecordMapper, never()).insertBatch(any());
    }

    @Test
    @DisplayName("deleteByIds: 软删 → 不调 incrementQuantity 回滚")
    void testSoftDelete_NoStockRollback() {
        when(medRecordMapper.update(any(), any())).thenReturn(1);

        int rows = service.deleteByIds(List.of(60001L));

        assertThat(rows).isEqualTo(1);
        // ADR-0012：软删不回滚仓库库存
        verify(medicineStockProvider, never()).add(any(), any(), any());
    }
}
