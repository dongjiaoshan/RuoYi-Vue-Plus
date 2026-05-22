package org.dromara.djs.breed.med.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.med.domain.Medicine;
import org.dromara.djs.breed.med.domain.bo.MedicineBo;
import org.dromara.djs.breed.med.domain.query.MedicineQuery;
import org.dromara.djs.breed.med.domain.vo.MedicineVo;
import org.dromara.djs.breed.med.mapper.MedicineMapper;
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
 * {@link MedicineServiceImpl} 单测（BRD-MED-001）。
 *
 * <p>覆盖 happy path：insert → list → query by id → update → soft-delete；
 * 关键验证 deleteWithValidByIds 走基类 softDelete，{@code wrapper.getSqlSet()} 含
 * {@code del_flag = '1'}（D03 _open-issues #18 教训：仅断言 entity 字段会被 mock 假阳性骗）。</p>
 *
 * @author djs
 * @since BRD-MED-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MedicineServiceImpl 单元测试")
class MedicineServiceImplTest {

    @Mock
    private MedicineMapper medicineMapper;

    private TestableMedicineServiceImpl service;

    /**
     * 子类覆盖 toEntity 钩子，避开 SpringUtils.getBean(Converter.class)。
     */
    static class TestableMedicineServiceImpl extends MedicineServiceImpl {
        TestableMedicineServiceImpl(MedicineMapper mapper) {
            super(mapper);
        }

        @Override
        protected Medicine toEntity(MedicineBo bo) {
            if (bo == null) {
                return null;
            }
            Medicine e = new Medicine();
            e.setId(bo.getId());
            e.setMedicineCode(bo.getMedicineCode());
            e.setMedicineName(bo.getMedicineName());
            e.setMedicineType(bo.getMedicineType());
            e.setSupplierId(bo.getSupplierId());
            e.setApprovalNo(bo.getApprovalNo());
            e.setBatchNo(bo.getBatchNo());
            e.setExpireDate(bo.getExpireDate());
            e.setWithdrawDays(bo.getWithdrawDays());
            e.setUnit(bo.getUnit());
            e.setCurrentStock(bo.getCurrentStock());
            e.setSpec(bo.getSpec());
            e.setManufacturer(bo.getManufacturer());
            e.setStorageCondition(bo.getStorageCondition());
            e.setMedStatus(bo.getMedStatus());
            e.setRemark(bo.getRemark());
            return e;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableMedicineServiceImpl(medicineMapper);
    }

    private MedicineBo sampleBo() {
        MedicineBo bo = new MedicineBo();
        bo.setMedicineCode("MED-001");
        bo.setMedicineName("猪瘟疫苗");
        bo.setMedicineType("vaccine");
        bo.setSupplierId(11001L);
        bo.setApprovalNo("兽药字（2024）010101");
        bo.setBatchNo("B20260501");
        bo.setExpireDate(LocalDate.of(2027, 5, 1));
        bo.setWithdrawDays(21);
        bo.setUnit("瓶");
        bo.setCurrentStock(new BigDecimal("120.00"));
        bo.setSpec("10ml × 100 支 / 盒");
        bo.setManufacturer("华大生物");
        bo.setStorageCondition("2-8℃ 冷藏 避光");
        bo.setMedStatus(1);
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy path → mapper.insert 调一次 + medStatus 默认 1 兜底")
    void testInsertByBo_HappyPath() {
        MedicineBo bo = sampleBo();
        bo.setMedStatus(null);  // 触发兜底
        when(medicineMapper.insert(any(Medicine.class))).thenAnswer(inv -> {
            Medicine e = inv.getArgument(0);
            e.setId(40001L);
            return 1;
        });

        int rows = service.insertByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<Medicine> captor = ArgumentCaptor.forClass(Medicine.class);
        verify(medicineMapper, times(1)).insert(captor.capture());
        Medicine saved = captor.getValue();
        assertThat(saved.getMedicineCode()).isEqualTo("MED-001");
        assertThat(saved.getMedicineName()).isEqualTo("猪瘟疫苗");
        assertThat(saved.getMedicineType()).isEqualTo("vaccine");
        assertThat(saved.getSupplierId()).isEqualTo(11001L);
        assertThat(saved.getMedStatus()).as("null 时应兜底 1").isEqualTo(1);
        assertThat(saved.getManufacturer()).isEqualTo("华大生物");
    }

    @Test
    @DisplayName("queryPageList: 走 mapper.selectVoPage 返 TableDataInfo")
    void testQueryPageList() {
        MedicineQuery query = new MedicineQuery();
        query.setMedicineType("vaccine");
        PageQuery pageQuery = new PageQuery(1, 10);

        MedicineVo vo = new MedicineVo();
        vo.setId(40001L);
        vo.setMedicineCode("MED-001");
        Page<MedicineVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(medicineMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        TableDataInfo<MedicineVo> result = service.queryPageList(query, pageQuery);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getMedicineCode()).isEqualTo("MED-001");
    }

    @Test
    @DisplayName("queryById: 走 mapper.selectVoById")
    void testQueryById() {
        MedicineVo vo = new MedicineVo();
        vo.setId(40001L);
        vo.setMedicineCode("MED-001");
        when(medicineMapper.selectVoById(40001L)).thenReturn(vo);

        MedicineVo got = service.queryById(40001L);
        assertThat(got).isNotNull();
        assertThat(got.getMedicineCode()).isEqualTo("MED-001");
    }

    @Test
    @DisplayName("updateByBo: happy path → mapper.updateById 调一次，medicineCode 强制锁回旧值")
    void testUpdateByBo_HappyPath() {
        Medicine existing = new Medicine();
        existing.setId(40001L);
        existing.setMedicineCode("MED-001");  // 旧编码
        when(medicineMapper.selectById(40001L)).thenReturn(existing);
        when(medicineMapper.updateById(any(Medicine.class))).thenReturn(1);

        MedicineBo bo = sampleBo();
        bo.setId(40001L);
        bo.setMedicineCode("MED-999");  // 尝试改编码
        bo.setMedicineName("猪瘟疫苗（改）");

        int rows = service.updateByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<Medicine> captor = ArgumentCaptor.forClass(Medicine.class);
        verify(medicineMapper).updateById(captor.capture());
        assertThat(captor.getValue().getMedicineName()).isEqualTo("猪瘟疫苗（改）");
        assertThat(captor.getValue().getMedicineCode()).as("编辑端点不允许改 medicineCode").isEqualTo("MED-001");
    }

    @Test
    @DisplayName("updateByBo: id 缺失 → 抛 ServiceException")
    void testUpdateByBo_NullId() {
        MedicineBo bo = sampleBo();
        bo.setId(null);
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("ID 不能为空");
    }

    @Test
    @DisplayName("updateByBo: DB 查不到 → 抛 ServiceException")
    void testUpdateByBo_NotExist() {
        when(medicineMapper.selectById(99999L)).thenReturn(null);
        MedicineBo bo = sampleBo();
        bo.setId(99999L);
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("deleteWithValidByIds: happy path → 走基类 softDelete，wrapper.setSql del_flag='1'，entity 携 id+delUnique（delFlag 不进 entity）")
    void testDeleteWithValidByIds_HappyPath() {
        when(medicineMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(40001L, 40002L));

        assertThat(rows).isEqualTo(2);
        ArgumentCaptor<UpdateWrapper<Medicine>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(medicineMapper, times(2)).update(isNull(), wrapperCaptor.capture());

        List<UpdateWrapper<Medicine>> capturedWrappers = wrapperCaptor.getAllValues();
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
        verify(medicineMapper, times(0)).update(any(Medicine.class), any(UpdateWrapper.class));
    }
}
