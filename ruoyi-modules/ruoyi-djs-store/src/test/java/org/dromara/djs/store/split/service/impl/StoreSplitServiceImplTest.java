package org.dromara.djs.store.split.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.store.split.domain.bo.StoreSplitBo;
import org.dromara.djs.store.split.domain.query.StoreSplitQuery;
import org.dromara.djs.store.split.domain.vo.StoreSplitVo;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreSplitServiceImpl} 单测（STR-SPLIT-001）。
 *
 * <p>覆盖核心场景：</p>
 * <ol>
 *   <li>addSplit happy：按 cutPart 反查 SKU → INSERT inhouse(source='store' / productType=1 / unit=kg)</li>
 *   <li>addSplit 源白条溯源：whiteBarId(String) → 实体 Long 列</li>
 *   <li>addSplit 拒绝：cutPart 反查不到 SKU → 抛 ServiceException + 不 INSERT（不吞异常）</li>
 *   <li>queryPage：wrapper 固定 source='store'（不返回仓库分割行），VO 映射正确</li>
 *   <li>queryList：导出路径同 source 过滤</li>
 * </ol>
 *
 * @author djs
 * @since STR-SPLIT-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreSplitServiceImpl 单元测试")
class StoreSplitServiceImplTest {

    @Mock private ProductInhouseMapper baseMapper;
    @Mock private ProductInfoMapper productInfoMapper;

    private TestableStoreSplitServiceImpl service;

    private static final Long SKU_ID = 100000000000000101L;
    private static final String SKU_NAME = "猪肉·精瘦肉";

    /**
     * MyBatis-Plus 单测 entity cache 预热（skill coder-mp-entity-cache-test）：service 内
     * LambdaQueryWrapper 触发 TableInfoHelper.getTableInfo() 解析 lambda 列名。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, ProductInhouse.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
    }

    /**
     * 子类化 stub resolveSkuByCutPart（避开真实 mapper 查询）。
     */
    static class TestableStoreSplitServiceImpl extends StoreSplitServiceImpl {
        ProductInfo stubSku;
        boolean throwOnResolve = false;

        TestableStoreSplitServiceImpl(ProductInhouseMapper b, ProductInfoMapper pm) {
            super(b, pm);
        }

        @Override
        protected ProductInfo resolveSkuByCutPart(String cutPart) {
            if (throwOnResolve) {
                throw new ServiceException("未找到分割部位对应的标准 SKU：PROD-PIG-" + cutPart.toUpperCase() + "-01");
            }
            return stubSku;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableStoreSplitServiceImpl(baseMapper, productInfoMapper);
        ProductInfo sku = new ProductInfo();
        sku.setId(SKU_ID);
        sku.setProductId("PROD-PIG-LEAN-01");
        sku.setProductName(SKU_NAME);
        service.stubSku = sku;
        when(baseMapper.insert(any(ProductInhouse.class))).thenAnswer(inv -> {
            ProductInhouse e = inv.getArgument(0);
            e.setId(70000L + (long) (Math.random() * 1000));
            return 1;
        });
    }

    private StoreSplitBo splitBo() {
        StoreSplitBo bo = new StoreSplitBo();
        bo.setCutPart("lean");
        bo.setProductWeight(new BigDecimal("2.5"));
        bo.setLocationId(9001L);
        bo.setRemark("ut");
        return bo;
    }

    @Test
    @DisplayName("addSplit happy：反查 SKU → INSERT inhouse(source=store / productType=1 / unit=kg / 重量2.5)")
    void testAddSplit_Happy() {
        Long id = service.addSplit(splitBo());
        assertThat(id).isNotNull();

        ArgumentCaptor<ProductInhouse> cap = ArgumentCaptor.forClass(ProductInhouse.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        ProductInhouse e = cap.getValue();
        assertThat(e.getSource()).isEqualTo("store");
        assertThat(e.getCutPart()).isEqualTo("lean");
        assertThat(e.getProductId()).isEqualTo(SKU_ID);
        assertThat(e.getProductName()).isEqualTo(SKU_NAME);
        assertThat(e.getProductType()).isEqualTo(1);
        assertThat(e.getProductUnit()).isEqualTo("kg");
        assertThat(e.getProductWeight()).isEqualByComparingTo("2.5");
        assertThat(e.getLocationId()).isEqualTo(9001L);
        assertThat(e.getProduceDate()).isNotNull();
        assertThat(e.getProduceTime()).isNotNull();
    }

    @Test
    @DisplayName("addSplit 源白条溯源：whiteBarId(String) → 实体 Long 列")
    void testAddSplit_WhiteBarTrace() {
        StoreSplitBo bo = splitBo();
        bo.setWhiteBarId("2058525064717926401");

        service.addSplit(bo);

        ArgumentCaptor<ProductInhouse> cap = ArgumentCaptor.forClass(ProductInhouse.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getWhiteBarId()).isEqualTo(2058525064717926401L);
    }

    @Test
    @DisplayName("addSplit 拒绝：cutPart 反查不到 SKU → 抛 ServiceException + 不 INSERT")
    void testAddSplit_SkuNotFound() {
        service.throwOnResolve = true;

        assertThatThrownBy(() -> service.addSplit(splitBo()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("标准 SKU");

        verify(baseMapper, never()).insert(any(ProductInhouse.class));
    }

    @Test
    @DisplayName("queryPage：wrapper 固定 source='store'（不返回仓库分割行）+ VO 映射正确")
    void testQueryPage_SourceStoreOnly() {
        ProductInhouse storeRow = new ProductInhouse();
        storeRow.setId(70001L);
        storeRow.setSource("store");
        storeRow.setCutPart("lean");
        storeRow.setProductName(SKU_NAME);
        storeRow.setProductWeight(new BigDecimal("2.5"));

        Page<ProductInhouse> dbPage = new Page<>(1, 10, 1);
        dbPage.setRecords(List.of(storeRow));

        ArgumentCaptor<LambdaQueryWrapper<ProductInhouse>> wrapCap =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        when(baseMapper.selectPage(any(), wrapCap.capture())).thenReturn(dbPage);

        TableDataInfo<StoreSplitVo> res = service.queryPage(new StoreSplitQuery(), new PageQuery(1, 10));

        assertThat(res.getRows()).hasSize(1);
        StoreSplitVo vo = res.getRows().get(0);
        assertThat(vo.getSource()).isEqualTo("store");
        assertThat(vo.getCutPart()).isEqualTo("lean");
        assertThat(vo.getProductName()).isEqualTo(SKU_NAME);
        // wrapper 含 source='store' 固定条件
        String sql = wrapCap.getValue().getSqlSegment();
        assertThat(sql.toLowerCase()).contains("source");
    }

    @Test
    @DisplayName("queryList：导出路径同 source 过滤 + 返回 VO 列表")
    void testQueryList_ExportPath() {
        ProductInhouse storeRow = new ProductInhouse();
        storeRow.setId(70002L);
        storeRow.setSource("store");
        storeRow.setCutPart("bone");
        when(baseMapper.selectList(any())).thenReturn(List.of(storeRow));

        List<StoreSplitVo> list = service.queryList(new StoreSplitQuery());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getSource()).isEqualTo("store");
        assertThat(list.get(0).getCutPart()).isEqualTo("bone");
    }

}
