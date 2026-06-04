package org.dromara.djs.store.operation.service.impl;

import org.dromara.djs.store.operation.domain.StoreProductRelation;
import org.dromara.djs.store.operation.domain.bo.StoreProductRelationSyncBo;
import org.dromara.djs.store.operation.mapper.StoreProductRelationMapper;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreProductRelationServiceImpl} 单测（STR-OP-001）。
 *
 * <p>覆盖全量 diff 同步核心语义：目标集合 vs 现存活跃关联，新增 INSERT / 移除 softDelete。</p>
 *
 * @author djs
 * @since STR-OP-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreProductRelationServiceImpl 产品关联同步单测")
class StoreProductRelationServiceImplTest {

    private final StoreProductRelationMapper relationMapper = mock(StoreProductRelationMapper.class);
    private final ProductInfoMapper productInfoMapper = mock(ProductInfoMapper.class);

    private final StoreProductRelationServiceImpl service =
        new StoreProductRelationServiceImpl(relationMapper, productInfoMapper);

    private StoreProductRelation rel(long id, long productId) {
        StoreProductRelation r = new StoreProductRelation();
        r.setId(id);
        r.setStoreId(5001L);
        r.setProductId(productId);
        r.setIsActive(1);
        r.setDelFlag("0");
        return r;
    }

    private ProductInfo product(long id) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setProductName("产品" + id);
        p.setProductUnit("件");
        return p;
    }

    @Test
    @DisplayName("syncRelations：目标 {8001,8002,8003}，现存 {8002}（已关联）→ 新增 8001/8003，无移除")
    void syncAddsNewProducts() {
        StoreProductRelationSyncBo bo = new StoreProductRelationSyncBo();
        bo.setStoreId(5001L);
        bo.setProductIds(List.of(8001L, 8002L, 8003L));

        // 现存仅 8002
        when(relationMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(rel(1L, 8002L))));
        // 新增的 8001/8003 存在性校验通过
        when(productInfoMapper.selectList(any())).thenReturn(List.of(product(8001L), product(8003L)));
        when(relationMapper.insert(any(StoreProductRelation.class))).thenReturn(1);

        int changed = service.syncRelations(bo);

        // 2 行新增，0 行软删
        assertThat(changed).isEqualTo(2);
        ArgumentCaptor<StoreProductRelation> captor = ArgumentCaptor.forClass(StoreProductRelation.class);
        verify(relationMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(StoreProductRelation::getProductId)
            .containsExactlyInAnyOrder(8001L, 8003L);
        assertThat(captor.getAllValues()).allMatch(r -> r.getIsActive() == 1);
    }

    @Test
    @DisplayName("syncRelations：目标 {8001}，现存 {8001,8002}（已关联）→ 移除 8002（softDelete），无新增")
    void syncRemovesUnselected() {
        StoreProductRelationSyncBo bo = new StoreProductRelationSyncBo();
        bo.setStoreId(5001L);
        bo.setProductIds(List.of(8001L));

        // 现存 8001 + 8002
        when(relationMapper.selectList(any()))
            .thenReturn(new ArrayList<>(List.of(rel(1L, 8001L), rel(2L, 8002L))));
        // softDelete 走 wrapper-only update（id=2 那条），返回受影响 1 行
        when(relationMapper.update(any(), any())).thenReturn(1);

        int changed = service.syncRelations(bo);

        // 0 行新增（8001 已存在），1 行软删（8002）
        assertThat(changed).isEqualTo(1);
        verify(relationMapper, times(0)).insert(any(StoreProductRelation.class));
        // softDelete 对 8002（id=2）执行一次 wrapper update
        verify(relationMapper, atLeastOnce()).update(any(), any());
    }
}
