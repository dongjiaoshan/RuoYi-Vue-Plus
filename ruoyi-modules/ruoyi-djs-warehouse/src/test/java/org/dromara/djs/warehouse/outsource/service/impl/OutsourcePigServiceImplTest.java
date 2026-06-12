package org.dromara.djs.warehouse.outsource.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.supplier.domain.Supplier;
import org.dromara.djs.common.supplier.mapper.SupplierMapper;
import org.dromara.djs.warehouse.outsource.domain.OutsourcePig;
import org.dromara.djs.warehouse.outsource.domain.bo.OutsourcePigBo;
import org.dromara.djs.warehouse.outsource.domain.query.OutsourcePigQuery;
import org.dromara.djs.warehouse.outsource.domain.vo.OutsourcePigVo;
import org.dromara.djs.warehouse.outsource.mapper.OutsourcePigMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OutsourcePigServiceImpl} 单测（DJS-FIX-WMS-RALN）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>insertByBo happy → toEntity 转换后 insert</li>
 *   <li>queryPageList happy → 分页 + supplierName 批量回填</li>
 *   <li>deleteWithValidByIds → 软删；空集合短路返 0</li>
 * </ul>
 *
 * <p>MyBatis-Plus entity cache 预热：buildQueryWrapper 内部 LambdaQueryWrapper 在 mock
 * 路径下也触发 TableInfoHelper 解析列名，先注册 OutsourcePig entity。</p>
 *
 * @author djs
 * @since DJS-FIX-WMS-RALN
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OutsourcePigServiceImpl 单元测试")
class OutsourcePigServiceImplTest {

    @Mock
    private OutsourcePigMapper baseMapper;

    @Mock
    private SupplierMapper supplierMapper;

    private OutsourcePigServiceImpl service;

    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, OutsourcePig.class);
        TableInfoHelper.initTableInfo(assistant, Supplier.class);
    }

    @BeforeEach
    void setUp() {
        service = new TestableOutsourcePigServiceImpl(baseMapper, supplierMapper);
    }

    /**
     * 子类覆盖 toEntity 钩子，避开 MapstructUtils 的 Spring 上下文依赖（参 DemandManageServiceImplTest 范式）。
     */
    static class TestableOutsourcePigServiceImpl extends OutsourcePigServiceImpl {
        TestableOutsourcePigServiceImpl(OutsourcePigMapper m, SupplierMapper sm) {
            super(m, sm);
        }

        @Override
        protected OutsourcePig toEntity(OutsourcePigBo bo) {
            if (bo == null) {
                return null;
            }
            OutsourcePig e = new OutsourcePig();
            e.setId(bo.getId());
            e.setPurchaseDate(bo.getPurchaseDate());
            e.setArriveTime(bo.getArriveTime());
            e.setPigMarkNo(bo.getPigMarkNo());
            e.setPigWeight(bo.getPigWeight());
            e.setSlaughterDate(bo.getSlaughterDate());
            e.setSupplierId(bo.getSupplierId());
            e.setBuyer(bo.getBuyer());
            e.setRemark(bo.getRemark());
            return e;
        }
    }

    @Test
    @DisplayName("insertByBo: happy → 转换后 insert，受影响 1 行")
    void testInsertByBo_Happy() {
        OutsourcePigBo bo = new OutsourcePigBo();
        bo.setPurchaseDate(new Date());
        bo.setSlaughterDate(new Date());
        bo.setPigWeight(new BigDecimal("133.21"));
        bo.setSupplierId(60001L);
        bo.setPigMarkNo("1222");
        bo.setBuyer("100");

        when(baseMapper.insert(any(OutsourcePig.class))).thenReturn(1);

        int affected = service.insertByBo(bo);

        assertThat(affected).isEqualTo(1);
        verify(baseMapper, times(1)).insert(any(OutsourcePig.class));
    }

    @Test
    @DisplayName("queryPageList: happy → 分页返回 + supplierName 批量回填")
    void testQueryPageList_FillSupplierName() {
        OutsourcePigQuery query = new OutsourcePigQuery();
        query.setSupplierId(60001L);
        PageQuery pageQuery = new PageQuery(1, 10);

        OutsourcePigVo vo = new OutsourcePigVo();
        vo.setId(1L);
        vo.setSupplierId(60001L);
        vo.setPigWeight(new BigDecimal("133.21"));
        Page<OutsourcePigVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(baseMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        Supplier supplier = new Supplier();
        supplier.setId(60001L);
        supplier.setSupplierName("供应商A");
        when(supplierMapper.selectBatchIds(anyCollection())).thenReturn(List.of(supplier));

        TableDataInfo<OutsourcePigVo> result = service.queryPageList(query, pageQuery);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getSupplierName()).isEqualTo("供应商A");
    }

    @Test
    @DisplayName("deleteWithValidByIds: 空集合短路返 0；有值走基类软删（update + del_flag）")
    void testDelete() {
        assertThat(service.deleteWithValidByIds(List.of())).isZero();

        when(baseMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        int affected = service.deleteWithValidByIds(List.of(1L));
        assertThat(affected).isEqualTo(1);
        verify(baseMapper, times(1)).update(any(), any(Wrapper.class));
    }

}
