package org.dromara.djs.breed.med.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.med.domain.MedBatch;
import org.dromara.djs.breed.med.domain.bo.MedBatchBo;
import org.dromara.djs.breed.med.domain.query.MedBatchQuery;
import org.dromara.djs.breed.med.domain.vo.MedBatchVo;
import org.dromara.djs.breed.med.mapper.MedBatchMapper;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MedBatchServiceImpl} 单测（BRD-MED-001）。
 *
 * <p>覆盖 happy path：insert / queryById / update / softDelete + wrapper.setSql 断言。</p>
 *
 * @author djs
 * @since BRD-MED-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MedBatchServiceImpl 单元测试")
class MedBatchServiceImplTest {

    @Mock
    private MedBatchMapper medBatchMapper;

    private TestableMedBatchServiceImpl service;

    static class TestableMedBatchServiceImpl extends MedBatchServiceImpl {
        TestableMedBatchServiceImpl(MedBatchMapper mapper) {
            super(mapper);
        }

        @Override
        protected MedBatch toEntity(MedBatchBo bo) {
            if (bo == null) {
                return null;
            }
            MedBatch e = new MedBatch();
            e.setId(bo.getId());
            e.setMedicineId(bo.getMedicineId());
            e.setBatchNo(bo.getBatchNo());
            e.setProductionDate(bo.getProductionDate());
            e.setExpiryDate(bo.getExpiryDate());
            e.setQuantity(bo.getQuantity());
            e.setUnitPrice(bo.getUnitPrice());
            e.setRemark(bo.getRemark());
            return e;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableMedBatchServiceImpl(medBatchMapper);
    }

    private MedBatchBo sampleBo() {
        MedBatchBo bo = new MedBatchBo();
        bo.setMedicineId(40001L);
        bo.setBatchNo("B20260501");
        bo.setProductionDate(LocalDate.of(2026, 5, 1));
        bo.setExpiryDate(LocalDate.of(2027, 5, 1));
        bo.setQuantity(new BigDecimal("120.00"));
        bo.setUnitPrice(new BigDecimal("35.50"));
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy path → mapper.insert 调一次")
    void testInsertByBo_HappyPath() {
        MedBatchBo bo = sampleBo();
        when(medBatchMapper.insert(any(MedBatch.class))).thenAnswer(inv -> {
            MedBatch e = inv.getArgument(0);
            e.setId(50001L);
            return 1;
        });

        int rows = service.insertByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<MedBatch> captor = ArgumentCaptor.forClass(MedBatch.class);
        verify(medBatchMapper, times(1)).insert(captor.capture());
        MedBatch saved = captor.getValue();
        assertThat(saved.getMedicineId()).isEqualTo(40001L);
        assertThat(saved.getBatchNo()).isEqualTo("B20260501");
        assertThat(saved.getQuantity()).isEqualByComparingTo("120.00");
    }

    @Test
    @DisplayName("queryPageList: medicineId 过滤 → mapper.selectVoPage")
    void testQueryPageList() {
        MedBatchQuery query = new MedBatchQuery();
        query.setMedicineId(40001L);
        PageQuery pageQuery = new PageQuery(1, 10);

        MedBatchVo vo = new MedBatchVo();
        vo.setId(50001L);
        vo.setBatchNo("B20260501");
        Page<MedBatchVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(medBatchMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        TableDataInfo<MedBatchVo> result = service.queryPageList(query, pageQuery);
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows().get(0).getBatchNo()).isEqualTo("B20260501");
    }

    @Test
    @DisplayName("updateByBo: happy path + medicineId 强制锁回旧值")
    void testUpdateByBo_HappyPath() {
        MedBatch existing = new MedBatch();
        existing.setId(50001L);
        existing.setMedicineId(40001L);  // 旧归属
        when(medBatchMapper.selectById(50001L)).thenReturn(existing);
        when(medBatchMapper.updateById(any(MedBatch.class))).thenReturn(1);

        MedBatchBo bo = sampleBo();
        bo.setId(50001L);
        bo.setMedicineId(40999L);  // 尝试换药品
        bo.setQuantity(new BigDecimal("80.00"));

        int rows = service.updateByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<MedBatch> captor = ArgumentCaptor.forClass(MedBatch.class);
        verify(medBatchMapper).updateById(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("80.00");
        assertThat(captor.getValue().getMedicineId()).as("批次归属不允许改").isEqualTo(40001L);
    }

    @Test
    @DisplayName("updateByBo: id 缺失 → 抛 ServiceException")
    void testUpdateByBo_NullId() {
        MedBatchBo bo = sampleBo();
        bo.setId(null);
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("ID 不能为空");
    }

    @Test
    @DisplayName("deleteWithValidByIds: happy path → 走基类 softDelete，wrapper.setSql del_flag='1'")
    void testDeleteWithValidByIds_HappyPath() {
        when(medBatchMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(50001L, 50002L));

        assertThat(rows).isEqualTo(2);
        ArgumentCaptor<UpdateWrapper<MedBatch>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(medBatchMapper, times(2)).update(isNull(), wrapperCaptor.capture());

        List<UpdateWrapper<MedBatch>> capturedWrappers = wrapperCaptor.getAllValues();
        assertThat(capturedWrappers).hasSize(2);
        assertThat(capturedWrappers).allSatisfy(w -> {
            assertThat(w.getSqlSet()).contains("del_flag", "del_unique", "update_by", "update_time");
            assertThat(w.getExpression().getNormal().getSqlSegment()).contains("id");
        });
    }

    @Test
    @DisplayName("deleteWithValidByIds: 空集合 → 0，不打 DB")
    void testDeleteWithValidByIds_EmptyShortCircuit() {
        int rows = service.deleteWithValidByIds(Collections.emptyList());
        assertThat(rows).isZero();
        verify(medBatchMapper, times(0)).update(any(MedBatch.class), any(UpdateWrapper.class));
    }
}
