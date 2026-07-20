package org.dromara.djs.store.operation.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.common.store.service.IStoreService;
import org.dromara.djs.store.operation.domain.StoreProductRelation;
import org.dromara.djs.store.operation.domain.StoreSaleRecord;
import org.dromara.djs.store.operation.domain.bo.StoreSaleRecordBo;
import org.dromara.djs.store.operation.domain.vo.StoreSaleImportVo;
import org.dromara.djs.store.operation.mapper.StoreProductRelationMapper;
import org.dromara.djs.store.operation.mapper.StoreSaleRecordMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreSaleRecordServiceImpl} 单测（STR-OP-001）。
 *
 * <p>覆盖：手录 addManual（产品名/单位按 productId 反查冗余、source=manual）；Excel 导入
 * importRows happy（按产品名反查该门店关联、source=excel_import）；导入失败（产品未关联抛异常）。</p>
 *
 * @author djs
 * @since STR-OP-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreSaleRecordServiceImpl 销售流水单测")
class StoreSaleRecordServiceImplTest {

    private final StoreSaleRecordMapper saleMapper = mock(StoreSaleRecordMapper.class);
    private final ProductInfoMapper productInfoMapper = mock(ProductInfoMapper.class);
    private final StoreMapper storeMapper = mock(StoreMapper.class);
    private final StoreProductRelationMapper relationMapper = mock(StoreProductRelationMapper.class);
    private final IStoreService storeService = mock(IStoreService.class);

    private final StoreSaleRecordServiceImpl service =
        new StoreSaleRecordServiceImpl(saleMapper, productInfoMapper, storeMapper, relationMapper, storeService);

    private ProductInfo product(long id, String name) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setProductName(name);
        p.setProductUnit("件");
        return p;
    }

    private StoreProductRelation rel(long productId) {
        StoreProductRelation r = new StoreProductRelation();
        r.setStoreId(5001L);
        r.setProductId(productId);
        return r;
    }

    @Test
    @DisplayName("addManual：产品名/单位按 productId 反查冗余落库，source=manual")
    void addManualHappy() {
        StoreSaleRecordBo bo = new StoreSaleRecordBo();
        bo.setStoreId(5001L);
        bo.setProductId(8001L);
        bo.setSaleDate(new Date());
        bo.setSaleQty(new BigDecimal("2"));
        bo.setSaleAmount(new BigDecimal("99.50"));

        when(productInfoMapper.selectById(8001L)).thenReturn(product(8001L, "有机白菜"));
        when(storeMapper.selectById(5001L)).thenReturn(new Store());
        when(saleMapper.insert(any(StoreSaleRecord.class))).thenReturn(1);

        service.addManual(bo);

        ArgumentCaptor<StoreSaleRecord> captor = ArgumentCaptor.forClass(StoreSaleRecord.class);
        verify(saleMapper, times(1)).insert(captor.capture());
        StoreSaleRecord saved = captor.getValue();
        assertThat(saved.getProductName()).isEqualTo("有机白菜");
        assertThat(saved.getSaleUnit()).isEqualTo("件");
        assertThat(saved.getSource()).isEqualTo("manual");
        assertThat(saved.getSaleAmount()).isEqualByComparingTo("99.50");
    }

    @Test
    @DisplayName("addManual：产品不存在 → 抛 ServiceException")
    void addManualProductNotFound() {
        StoreSaleRecordBo bo = new StoreSaleRecordBo();
        bo.setStoreId(5001L);
        bo.setProductId(9999L);
        bo.setSaleDate(new Date());
        bo.setSaleQty(BigDecimal.ONE);
        bo.setSaleAmount(BigDecimal.TEN);
        when(productInfoMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> service.addManual(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("产品不存在");
    }

    @Test
    @DisplayName("importRows happy：2 行产品均在该门店关联 → 批量入库 source=excel_import，返回 2")
    void importRowsHappy() {
        when(storeMapper.selectById(5001L)).thenReturn(new Store());
        // 该门店关联 8001/8002
        when(relationMapper.selectList(any())).thenReturn(List.of(rel(8001L), rel(8002L)));
        when(productInfoMapper.selectList(any()))
            .thenReturn(List.of(product(8001L, "有机白菜"), product(8002L, "有机番茄")));
        when(saleMapper.insertBatch(any())).thenReturn(true);

        StoreSaleImportVo r1 = new StoreSaleImportVo();
        r1.setProductName("有机白菜");
        r1.setSaleDate(new Date());
        r1.setSaleQty(new BigDecimal("5"));
        r1.setSaleAmount(new BigDecimal("25"));
        StoreSaleImportVo r2 = new StoreSaleImportVo();
        r2.setProductName("有机番茄");
        r2.setSaleDate(new Date());
        r2.setSaleQty(new BigDecimal("3"));
        r2.setSaleAmount(new BigDecimal("18"));

        int imported = service.importRows(5001L, List.of(r1, r2), 2001L);

        assertThat(imported).isEqualTo(2);
        ArgumentCaptor<Collection<StoreSaleRecord>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(saleMapper, times(1)).insertBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(2)
            .allMatch(r -> "excel_import".equals(r.getSource()))
            .allMatch(r -> r.getOperatorId().equals(2001L));
    }

    @Test
    @DisplayName("importRows 失败：产品未在该门店关联 → 抛 ServiceException（指出行号 + 产品名）")
    void importRowsProductNotRelated() {
        when(storeMapper.selectById(5001L)).thenReturn(new Store());
        when(relationMapper.selectList(any())).thenReturn(List.of(rel(8001L)));
        when(productInfoMapper.selectList(any())).thenReturn(List.of(product(8001L, "有机白菜")));

        StoreSaleImportVo bad = new StoreSaleImportVo();
        bad.setProductName("未关联产品");
        bad.setSaleDate(new Date());
        bad.setSaleQty(BigDecimal.ONE);
        bad.setSaleAmount(BigDecimal.TEN);

        assertThatThrownBy(() -> service.importRows(5001L, List.of(bad), 2001L))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("未关联产品");
        verify(saleMapper, times(0)).insertBatch(any());
    }

    @Test
    @DisplayName("deleteByIds：委托基类 softDelete（wrapper update）")
    void deleteByIdsSoftDelete() {
        when(saleMapper.update(any(), any())).thenReturn(1);
        int n = service.deleteByIds(List.of(1L, 2L));
        assertThat(n).isEqualTo(2);
        verify(saleMapper, times(2)).update(eq(null), any());
    }
}
