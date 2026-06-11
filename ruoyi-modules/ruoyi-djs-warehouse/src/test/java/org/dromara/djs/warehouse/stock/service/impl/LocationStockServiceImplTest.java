package org.dromara.djs.warehouse.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.domain.bo.LocationStockBo;
import org.dromara.djs.warehouse.stock.domain.query.LocationStockQuery;
import org.dromara.djs.warehouse.stock.domain.vo.LocationStockVo;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.dromara.common.satoken.utils.LoginHelper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LocationStockServiceImpl} 单测（WMS-MD-001）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>queryPageList happy path：JOIN 回填 locationName 不为 null</li>
 *   <li>insertByBo happy：operatorId 走 LoginHelper.getUserId() 注入（ADR-0007）</li>
 *   <li>insertByBo error：三选一规则违反（productId + earNo 同时填）→ 抛 stock.three_way.exclusive</li>
 *   <li>insertByBo error：三选一全空 → 抛 stock.three_way.exclusive</li>
 * </ul>
 *
 * @author djs
 * @since WMS-MD-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LocationStockServiceImpl 单元测试")
class LocationStockServiceImplTest {

    @Mock
    private LocationStockMapper stockMapper;

    @Mock
    private LocationInfoMapper locationInfoMapper;

    @Mock
    private PlotInfoMapper plotInfoMapper;

    private TestableLocationStockServiceImpl service;

    private MockedStatic<LoginHelper> loginHelperMock;

    static class TestableLocationStockServiceImpl extends LocationStockServiceImpl {
        TestableLocationStockServiceImpl(LocationStockMapper baseMapper, LocationInfoMapper locInfoMapper, PlotInfoMapper plotInfoMapper) {
            super(baseMapper, locInfoMapper, plotInfoMapper);
        }

        @Override
        protected LocationStock toEntity(LocationStockBo bo) {
            if (bo == null) return null;
            LocationStock e = new LocationStock();
            e.setId(bo.getId());
            e.setLocationId(bo.getLocationId());
            e.setProductId(bo.getProductId());
            e.setEarNo(bo.getEarNo());
            e.setPlotId(bo.getPlotId());
            e.setProductName(bo.getProductName());
            e.setProductStock(bo.getProductStock());
            e.setProductUnit(bo.getProductUnit());
            e.setIsEnd(bo.getIsEnd());
            e.setRemark(bo.getRemark());
            return e;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableLocationStockServiceImpl(stockMapper, locationInfoMapper, plotInfoMapper);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(10086L);
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    private LocationStockBo sampleBo() {
        LocationStockBo bo = new LocationStockBo();
        bo.setLocationId(90001L);
        bo.setProductId(50001L);
        bo.setProductName("猪后腿肉");
        bo.setProductStock(new BigDecimal("12.500"));
        bo.setProductUnit("kg");
        return bo;
    }

    @Test
    @DisplayName("queryPageList: happy → JOIN 回填 locationName")
    void testQueryPageList_FillLocationName() {
        LocationStockQuery query = new LocationStockQuery();
        query.setLocationId(90001L);
        PageQuery pageQuery = new PageQuery(1, 10);

        LocationStockVo vo = new LocationStockVo();
        vo.setId(80001L);
        vo.setLocationId(90001L);
        vo.setProductName("猪后腿肉");
        Page<LocationStockVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(stockMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        LocationInfo loc = new LocationInfo();
        loc.setId(90001L);
        loc.setLocationName("冻品库");
        when(locationInfoMapper.selectList(any())).thenReturn(List.of(loc));

        TableDataInfo<LocationStockVo> result = service.queryPageList(query, pageQuery);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getLocationName()).as("应回填 locationName").isEqualTo("冻品库");
    }

    @Test
    @DisplayName("queryPageList: happy → JOIN 地块表回填 blockNo（地块编号 = plot_code）")
    void testQueryPageList_FillBlockNo() {
        LocationStockQuery query = new LocationStockQuery();
        PageQuery pageQuery = new PageQuery(1, 10);

        LocationStockVo vo = new LocationStockVo();
        vo.setId(80002L);
        vo.setLocationId(90001L);
        vo.setPlotId(70001L);
        vo.setProductName("小白菜");
        Page<LocationStockVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(stockMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);
        when(locationInfoMapper.selectList(any())).thenReturn(List.of());

        PlotInfo plot = new PlotInfo();
        plot.setId(70001L);
        plot.setPlotCode("DK-001");
        when(plotInfoMapper.selectList(any())).thenReturn(List.of(plot));

        TableDataInfo<LocationStockVo> result = service.queryPageList(query, pageQuery);

        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getBlockNo()).as("应回填地块编号").isEqualTo("DK-001");
    }

    @Test
    @DisplayName("insertByBo: happy → operatorId 走 LoginHelper.getUserId() 注入（ADR-0007）+ isEnd 默认 0")
    void testInsertByBo_OperatorIdInjected() {
        LocationStockBo bo = sampleBo();
        when(stockMapper.insert(any(LocationStock.class))).thenAnswer(inv -> {
            LocationStock e = inv.getArgument(0);
            e.setId(80001L);
            return 1;
        });

        int rows = service.insertByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<LocationStock> captor = ArgumentCaptor.forClass(LocationStock.class);
        verify(stockMapper, times(1)).insert(captor.capture());
        LocationStock saved = captor.getValue();
        assertThat(saved.getOperatorId()).as("ADR-0007 强制 operatorId").isEqualTo(10086L);
        assertThat(saved.getIsEnd()).as("isEnd 默认 0").isEqualTo(0);
        assertThat(saved.getProductId()).isEqualTo(50001L);
    }

    @Test
    @DisplayName("insertByBo: error → productId + earNo 同时填，抛 stock.three_way.exclusive")
    void testInsertByBo_ThreeWay_BothProductIdAndEarNo() {
        LocationStockBo bo = sampleBo();
        bo.setEarNo("01A12605001");  // productId 已有 → 同时填 → 违规

        assertThatThrownBy(() -> service.insertByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("stock.three_way.exclusive");

        verify(stockMapper, times(0)).insert(any(LocationStock.class));
    }

    @Test
    @DisplayName("insertByBo: error → productId / earNo / plotId 全空，抛 stock.three_way.exclusive")
    void testInsertByBo_ThreeWay_AllNull() {
        LocationStockBo bo = sampleBo();
        bo.setProductId(null);
        // earNo / plotId 默认就是 null

        assertThatThrownBy(() -> service.insertByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("stock.three_way.exclusive");
    }

}
