package org.dromara.djs.breed.med.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.med.domain.MedBatch;
import org.dromara.djs.breed.med.domain.query.MedUsageQuery;
import org.dromara.djs.breed.med.domain.MedUsage;
import org.dromara.djs.breed.med.domain.bo.MedUsageBo;
import org.dromara.djs.breed.med.domain.vo.MedUsageVo;
import org.dromara.djs.breed.med.mapper.MedBatchMapper;
import org.dromara.djs.breed.med.mapper.MedUsageMapper;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MedUsageServiceImpl} 单测（BRD-MED-002）。
 *
 * <p>覆盖：use happy path 扣减 / 库存不足拒绝 / return 归还 / loss 扣减 /
 * 药品与批次归属不一致拒绝 / 批次不存在拒绝 / today-stat 聚合 / softDelete 不回滚库存。</p>
 *
 * <p>ADR-0012：库存真值在仓库 location_stock，use/loss 走
 * {@link MedicineStockProvider#deduct}、return 走 {@link MedicineStockProvider#add}；
 * 批次 quantity 退化为入库快照不再随领用扣减。库存不足由 provider 抛 ServiceException。</p>
 *
 * @author djs
 * @since BRD-MED-002
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MedUsageServiceImpl 单元测试")
class MedUsageServiceImplTest {

    private static final Long MEDICINE_ID = 40001L;

    @Mock
    private MedUsageMapper medUsageMapper;
    @Mock
    private MedBatchMapper medBatchMapper;
    @Mock
    private MedicineStockProvider medicineStockProvider;

    private TestableMedUsageServiceImpl service;

    static class TestableMedUsageServiceImpl extends MedUsageServiceImpl {
        // enrich 用的 medicine/pig/pen mapper 本单测不触发列表 enrich 路径，传 null 即可
        TestableMedUsageServiceImpl(MedUsageMapper m, MedBatchMapper b, MedicineStockProvider sp) {
            super(m, b, null, null, null, sp);
        }

        @Override
        protected MedUsage toEntity(MedUsageBo bo) {
            if (bo == null) {
                return null;
            }
            MedUsage u = new MedUsage();
            u.setBatchId(bo.getBatchId());
            u.setMedicineId(bo.getMedicineId());
            u.setUsageType(bo.getUsageType());
            u.setUsageQty(bo.getUsageQty());
            u.setUseDate(bo.getUseDate());
            u.setPigId(bo.getPigId());
            u.setRelatedPenId(bo.getRelatedPenId());
            u.setScheduleId(bo.getScheduleId());
            u.setRemark(bo.getRemark());
            return u;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableMedUsageServiceImpl(medUsageMapper, medBatchMapper, medicineStockProvider);
    }

    private MedBatch existingBatch(BigDecimal remaining) {
        MedBatch b = new MedBatch();
        b.setId(50001L);
        b.setMedicineId(MEDICINE_ID);
        b.setBatchNo("B20260501");
        b.setQuantity(remaining);
        b.setDelFlag("0");
        return b;
    }

    private MedUsageBo sampleBo(String type, String qty) {
        MedUsageBo bo = new MedUsageBo();
        bo.setBatchId(50001L);
        bo.setMedicineId(MEDICINE_ID);
        bo.setUsageType(type);
        bo.setUsageQty(new BigDecimal(qty));
        bo.setUseDate(LocalDate.of(2026, 5, 27));
        bo.setRemark("happy");
        return bo;
    }

    @Test
    @DisplayName("insertByBo[use]: happy path → provider.deduct 调一次 + INSERT 调一次")
    void testInsertUse_HappyPath() {
        when(medBatchMapper.selectById(50001L)).thenReturn(existingBatch(new BigDecimal("100.000")));
        when(medUsageMapper.insert(any(MedUsage.class))).thenReturn(1);

        int rows = service.insertByBo(sampleBo("use", "5.000"));

        assertThat(rows).isEqualTo(1);
        // ADR-0012：扣仓库库存（medicineId 取 batch.getMedicineId()，operatorId 单测无登录上下文为 null）
        ArgumentCaptor<BigDecimal> qtyCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(medicineStockProvider, times(1)).deduct(eq(MEDICINE_ID), qtyCaptor.capture(), any());
        assertThat(qtyCaptor.getValue()).isEqualByComparingTo("5.000");
        verify(medicineStockProvider, never()).add(any(), any(), any());

        ArgumentCaptor<MedUsage> captor = ArgumentCaptor.forClass(MedUsage.class);
        verify(medUsageMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getUsageType()).isEqualTo("use");
        assertThat(captor.getValue().getUsageQty()).isEqualByComparingTo("5.000");
    }

    @Test
    @DisplayName("insertByBo[use·去批次 r51]: batchId 为空 → 不查批次，按 medicineId 扣减 + INSERT")
    void testInsertUse_NoBatch() {
        when(medUsageMapper.insert(any(MedUsage.class))).thenReturn(1);
        MedUsageBo bo = sampleBo("use", "5.000");
        bo.setBatchId(null);   // r51 去批次：药品使用无批次说法

        int rows = service.insertByBo(bo);

        assertThat(rows).isEqualTo(1);
        // 不带批次 → 不应触碰批次查询
        verify(medBatchMapper, never()).selectById(any());
        // 按 bo.medicineId 直接扣减仓库库存
        verify(medicineStockProvider, times(1)).deduct(eq(MEDICINE_ID), any(BigDecimal.class), any());
        verify(medicineStockProvider, never()).add(any(), any(), any());
        verify(medUsageMapper, times(1)).insert(any(MedUsage.class));
    }

    @Test
    @DisplayName("insertByBo[use]: 库存不足 → provider.deduct 抛 ServiceException，不 INSERT 台账")
    void testInsertUse_InsufficientStock() {
        when(medBatchMapper.selectById(50001L)).thenReturn(existingBatch(new BigDecimal("3.000")));
        // ADR-0012：库存不足由 provider.deduct 抛业务异常（@Transactional 自然回滚）
        doThrow(new ServiceException("药品库存不足"))
            .when(medicineStockProvider).deduct(eq(MEDICINE_ID), any(BigDecimal.class), any());

        assertThatThrownBy(() -> service.insertByBo(sampleBo("use", "10.000")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("库存不足");

        verify(medUsageMapper, never()).insert(any(MedUsage.class));
    }

    @Test
    @DisplayName("insertByBo[loss]: 走 provider.deduct（与 use 一致），不调 add")
    void testInsertLoss_DeductSamePath() {
        when(medBatchMapper.selectById(50001L)).thenReturn(existingBatch(new BigDecimal("100.000")));
        when(medUsageMapper.insert(any(MedUsage.class))).thenReturn(1);

        int rows = service.insertByBo(sampleBo("loss", "2.500"));

        assertThat(rows).isEqualTo(1);
        verify(medicineStockProvider, times(1)).deduct(eq(MEDICINE_ID), any(BigDecimal.class), any());
        verify(medicineStockProvider, never()).add(any(), any(), any());
    }

    @Test
    @DisplayName("insertByBo[return]: 走 provider.add 归还仓库库存")
    void testInsertReturn_Increment() {
        when(medBatchMapper.selectById(50001L)).thenReturn(existingBatch(new BigDecimal("50.000")));
        when(medUsageMapper.insert(any(MedUsage.class))).thenReturn(1);

        int rows = service.insertByBo(sampleBo("return", "1.500"));

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<BigDecimal> qtyCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(medicineStockProvider, times(1)).add(eq(MEDICINE_ID), qtyCaptor.capture(), any());
        assertThat(qtyCaptor.getValue()).isEqualByComparingTo("1.500");
        verify(medicineStockProvider, never()).deduct(any(), any(), any());
    }

    @Test
    @DisplayName("insertByBo: 批次不存在 → 抛 ServiceException")
    void testInsert_BatchNotFound() {
        when(medBatchMapper.selectById(50001L)).thenReturn(null);

        assertThatThrownBy(() -> service.insertByBo(sampleBo("use", "5.000")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("批次不存在或已删除");

        verify(medicineStockProvider, never()).deduct(any(), any(), any());
        verify(medicineStockProvider, never()).add(any(), any(), any());
        verify(medUsageMapper, never()).insert(any(MedUsage.class));
    }

    @Test
    @DisplayName("insertByBo: 药品与批次归属不一致 → 抛 ServiceException")
    void testInsert_MedicineMismatch() {
        when(medBatchMapper.selectById(50001L)).thenReturn(existingBatch(new BigDecimal("100.000")));
        MedUsageBo bo = sampleBo("use", "5.000");
        bo.setMedicineId(99999L);   // 与 batch.medicineId=40001 不一致

        assertThatThrownBy(() -> service.insertByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("药品 ID 与批次归属不一致");

        verify(medicineStockProvider, never()).deduct(any(), any(), any());
    }

    @Test
    @DisplayName("todayStat: 聚合按 usageType → use/return/loss 缺省补 0")
    void testTodayStat_Aggregation() {
        MedUsageVo u1 = new MedUsageVo();
        u1.setUsageType("use");
        u1.setUsageQty(new BigDecimal("10.500"));
        MedUsageVo u2 = new MedUsageVo();
        u2.setUsageType("use");
        u2.setUsageQty(new BigDecimal("2.000"));
        MedUsageVo r1 = new MedUsageVo();
        r1.setUsageType("return");
        r1.setUsageQty(new BigDecimal("1.000"));

        when(medUsageMapper.selectVoList(any())).thenReturn(List.of(u1, u2, r1));

        Map<String, BigDecimal> stat = service.todayStat(null);
        assertThat(stat).hasSize(3);
        assertThat(stat.get("use")).isEqualByComparingTo("12.500");
        assertThat(stat.get("return")).isEqualByComparingTo("1.000");
        assertThat(stat.get("loss")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("insertByBo: 无效 usageType → 抛 ServiceException")
    void testInsert_InvalidType() {
        when(medBatchMapper.selectById(50001L)).thenReturn(existingBatch(new BigDecimal("100.000")));
        MedUsageBo bo = sampleBo("invalid", "5.000");

        assertThatThrownBy(() -> service.insertByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("无效的领用类型");
    }

    @Test
    @DisplayName("deleteWithValidByIds: 软删走基类 → 不调用 provider.add 回滚库存")
    void testSoftDelete_NoStockRollback() {
        when(medUsageMapper.update(any(), any())).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(60001L));

        assertThat(rows).isEqualTo(1);
        // 关键断言：软删不触发归还
        verify(medicineStockProvider, never()).add(any(), any(), any());
    }

    @Test
    @DisplayName("queryPageList[enrich]: 领用行 pig_id/pen_id 全空 → enrich 不抛 NPE（回归 Map.of().get(null)）")
    void testQueryPageList_NullEnrichIds_NoNpe() {
        // 回归 IDENRICH NPE：领用不关联猪/栏位时 pigId/relatedPenId 为 null，整页全空时
        // batchLookup 返回空 Map → 下游 .get(null)。空 Map 用 Map.of() 会抛 NPE（不可变 Map 拒 null key），
        // 修复后用 Collections.emptyMap() 返 null 不抛。本例 4 个 enrich FK 全 null，4 个 lookup 都走空 Map 分支。
        MedUsageVo vo = new MedUsageVo();
        vo.setId(70001L);
        // medicineId / batchId / pigId / relatedPenId 全 null
        Page<MedUsageVo> page = new Page<>(1, 10);
        page.setRecords(List.of(vo));
        when(medUsageMapper.selectVoPage(any(), any())).thenReturn(page);

        TableDataInfo<MedUsageVo> result = service.queryPageList(new MedUsageQuery(), new PageQuery(10, 1));

        assertThat(result.getRows()).hasSize(1);
        MedUsageVo enriched = result.getRows().get(0);
        assertThat(enriched.getEarNo()).isNull();
        assertThat(enriched.getPenCode()).isNull();
        assertThat(enriched.getMedicineName()).isNull();
        assertThat(enriched.getBatchNo()).isNull();
    }
}
