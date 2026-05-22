package org.dromara.djs.common.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.supplier.domain.Supplier;
import org.dromara.djs.common.supplier.domain.bo.SupplierBo;
import org.dromara.djs.common.supplier.domain.query.SupplierQuery;
import org.dromara.djs.common.supplier.domain.vo.SupplierVo;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SupplierServiceImpl} 单元测试（SYS-MD-003 + SYS-MD-FIX-002）。
 *
 * @author djs
 * @since SYS-MD-003
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SupplierServiceImpl 单元测试")
class SupplierServiceImplTest {

    @Mock
    private SupplierMapper supplierMapper;

    @Mock
    private IBizCodeGenerator bizCodeGenerator;

    @Mock
    private BizReferenceChecker bizReferenceChecker;

    private TestableSupplierServiceImpl service;

    static class TestableSupplierServiceImpl extends SupplierServiceImpl {
        TestableSupplierServiceImpl(SupplierMapper supplierMapper,
                                    IBizCodeGenerator bizCodeGenerator,
                                    BizReferenceChecker bizReferenceChecker) {
            super(supplierMapper, bizCodeGenerator, bizReferenceChecker);
        }

        @Override
        protected Supplier toEntity(SupplierBo bo) {
            if (bo == null) {
                return null;
            }
            Supplier s = new Supplier();
            s.setId(bo.getId());
            s.setSupplierName(bo.getSupplierName());
            s.setLicenseNo(bo.getLicenseNo());
            s.setLicenseImageOssId(bo.getLicenseImageOssId());
            s.setBusinessLicenseNo(bo.getBusinessLicenseNo());
            s.setCooperationStartDate(bo.getCooperationStartDate());
            s.setSupplierType(bo.getSupplierType());
            s.setLiaisonName(bo.getLiaisonName());
            s.setLiaisonPhone(bo.getLiaisonPhone());
            s.setAddress(bo.getAddress());
            s.setBusinessStatus(bo.getBusinessStatus());
            s.setSettleType(bo.getSettleType());
            s.setBankAccount(bo.getBankAccount());
            s.setBankName(bo.getBankName());
            s.setRemark(bo.getRemark());
            return s;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableSupplierServiceImpl(supplierMapper, bizCodeGenerator, bizReferenceChecker);
        when(bizCodeGenerator.generate(eq(BizCodeType.SUPPLIER_CODE), any())).thenReturn("G0001");
    }

    private SupplierBo sampleBo() {
        SupplierBo bo = new SupplierBo();
        bo.setSupplierName("华农饲料原材料有限公司");
        bo.setSupplierType("feed");
        bo.setLiaisonName("王经理");
        bo.setLiaisonPhone("13800138001");
        bo.setAddress("上海市浦东新区");
        bo.setBusinessStatus("0");
        bo.setSettleType("monthly");
        bo.setRemark("SYS-MD-FIX-002 单测样本");
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy path → supplier_code 由后端生成 / dealCount=0 / purchaseQty=0 / mapper.insert 调一次")
    void testInsertByBo_HappyPath() {
        SupplierBo bo = sampleBo();
        when(supplierMapper.insert(any(Supplier.class))).thenAnswer(inv -> {
            Supplier entity = inv.getArgument(0);
            entity.setId(20001L);
            return 1;
        });

        int rows = service.insertByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(supplierMapper, times(1)).insert(captor.capture());
        Supplier saved = captor.getValue();
        assertThat(saved.getSupplierName()).isEqualTo("华农饲料原材料有限公司");
        assertThat(saved.getSupplierType()).isEqualTo("feed");
        assertThat(saved.getBusinessStatus()).isEqualTo("0");
        assertThat(saved.getSettleType()).isEqualTo("monthly");
        assertThat(saved.getSupplierCode()).isEqualTo("G0001");
        // FIX-002 聚合冗余字段：始终 0
        assertThat(saved.getDealCount()).isEqualTo(0);
        assertThat(saved.getPurchaseQty()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("insertByBo: 未传 businessStatus → 默认 '0'（合作中）/ 未传 settleType → 默认 cash")
    void testInsertByBo_DefaultsApplied() {
        SupplierBo bo = sampleBo();
        bo.setBusinessStatus(null);
        bo.setSettleType(null);
        when(supplierMapper.insert(any(Supplier.class))).thenReturn(1);

        service.insertByBo(bo);

        ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(supplierMapper).insert(captor.capture());
        Supplier saved = captor.getValue();
        assertThat(saved.getBusinessStatus()).isEqualTo("0");
        assertThat(saved.getSettleType()).isEqualTo("cash");
    }

    @Test
    @DisplayName("queryPageList: 走 mapper.selectVoPage 返回封装后的 TableDataInfo")
    void testQueryPageList() {
        SupplierQuery query = new SupplierQuery();
        query.setSupplierName("华农");
        PageQuery pageQuery = new PageQuery(1, 10);

        SupplierVo vo = new SupplierVo();
        vo.setId(20001L);
        vo.setSupplierName("华农饲料原材料有限公司");
        Page<SupplierVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(supplierMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        TableDataInfo<SupplierVo> result = service.queryPageList(query, pageQuery);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getSupplierName()).isEqualTo("华农饲料原材料有限公司");
    }

    @Test
    @DisplayName("queryById: 走 mapper.selectVoById")
    void testQueryById() {
        SupplierVo vo = new SupplierVo();
        vo.setId(20001L);
        vo.setSupplierName("华农饲料原材料有限公司");
        when(supplierMapper.selectVoById(20001L)).thenReturn(vo);

        SupplierVo got = service.queryById(20001L);
        assertThat(got).isNotNull();
        assertThat(got.getSupplierName()).isEqualTo("华农饲料原材料有限公司");
    }

    @Test
    @DisplayName("updateByBo: happy path → 编辑不允许覆盖 supplier_code / dealCount / purchaseQty（保留 DB 原值）")
    void testUpdateByBo_PreservesSupplierCodeAndAggregates() {
        Supplier existing = new Supplier();
        existing.setId(20001L);
        existing.setSupplierCode("G0007");
        existing.setSupplierName("华农饲料原材料有限公司");
        existing.setDealCount(3);
        existing.setPurchaseQty(new BigDecimal("123.456"));
        when(supplierMapper.selectById(20001L)).thenReturn(existing);
        when(supplierMapper.updateById(any(Supplier.class))).thenReturn(1);

        SupplierBo bo = sampleBo();
        bo.setId(20001L);
        bo.setSupplierName("华农饲料原材料股份公司");

        int rows = service.updateByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(supplierMapper).updateById(captor.capture());
        Supplier saved = captor.getValue();
        assertThat(saved.getSupplierName()).isEqualTo("华农饲料原材料股份公司");
        assertThat(saved.getSupplierCode()).isEqualTo("G0007");
        // FIX-002 聚合冗余字段保留 DB 原值
        assertThat(saved.getDealCount()).isEqualTo(3);
        assertThat(saved.getPurchaseQty()).isEqualTo(new BigDecimal("123.456"));
    }

    @Test
    @DisplayName("updateByBo: id 缺失 → 抛 ServiceException")
    void testUpdateByBo_NullId() {
        SupplierBo bo = sampleBo();
        bo.setId(null);
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("供应商 ID 不能为空");
    }

    @Test
    @DisplayName("updateByBo: DB 查不到（已软删 / id 错误）→ 抛 ServiceException")
    void testUpdateByBo_NotExist() {
        when(supplierMapper.selectById(99999L)).thenReturn(null);

        SupplierBo bo = sampleBo();
        bo.setId(99999L);

        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("供应商不存在");
    }

    @Test
    @DisplayName("deleteWithValidByIds: happy path → 走基类 softDelete，每个 id 一次 wrapper-only update，sqlSet 显式写 del_flag/del_unique/update_by/update_time")
    void testDeleteWithValidByIds_HappyPath() {
        when(supplierMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(20001L, 20002L));

        assertThat(rows).isEqualTo(2);
        ArgumentCaptor<UpdateWrapper<Supplier>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(supplierMapper, times(2)).update(isNull(), wrapperCaptor.capture());

        List<UpdateWrapper<Supplier>> capturedWrappers = wrapperCaptor.getAllValues();
        assertThat(capturedWrappers).hasSize(2);
        assertThat(capturedWrappers).allSatisfy(w -> {
            assertThat(w.getSqlSet()).contains("del_flag", "del_unique", "update_by", "update_time");
            assertThat(w.getExpression().getNormal().getSqlSegment()).contains("id");
        });
        assertThat(capturedWrappers.get(0).getParamNameValuePairs().values())
            .extracting(Object::toString).contains("20001");
        assertThat(capturedWrappers.get(1).getParamNameValuePairs().values())
            .extracting(Object::toString).contains("20002");
        assertThat(capturedWrappers).allSatisfy(w ->
            assertThat(w.getParamNameValuePairs().values()).extracting(Object::toString).contains("1"));
    }

    @Test
    @DisplayName("deleteWithValidByIds: 空集合 → 直接返 0，不打 DB")
    void testDeleteWithValidByIds_EmptyShortCircuit() {
        int rows = service.deleteWithValidByIds(Collections.emptyList());
        assertThat(rows).isZero();
        verify(supplierMapper, times(0)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("deleteWithValidByIds: 被业务事件引用 → 抛 ServiceException 阻止软删（BRD-EVENT-001 回填）")
    void testDeleteWithValidByIds_ReferencedThrows() {
        when(bizReferenceChecker.isReferenced(eq("t_md_supplier"), eq(20001L))).thenReturn(true);

        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(20001L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("供应商被业务记录引用");

        // 命中即抛，未调用 mapper.update
        verify(supplierMapper, times(0)).update(isNull(), any(UpdateWrapper.class));
    }
}
