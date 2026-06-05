package org.dromara.djs.warehouse.location.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.domain.bo.LocationInfoBo;
import org.dromara.djs.warehouse.location.domain.query.LocationInfoQuery;
import org.dromara.djs.warehouse.location.domain.vo.LocationCardSummaryVo;
import org.dromara.djs.warehouse.location.domain.vo.LocationInfoVo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LocationInfoServiceImpl} 单测（WMS-MD-001）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>insertByBo happy path + locationStatus 默认 1 兜底</li>
 *   <li>queryPageList 走 mapper.selectVoPage 返 TableDataInfo</li>
 *   <li>updateByBo happy path：locationCode 强制锁回旧值</li>
 *   <li>updateByBo: id 缺失 → 抛 ServiceException</li>
 *   <li>deleteWithValidByIds happy path：无库存 → 走基类 softDelete</li>
 *   <li>deleteWithValidByIds error：库位仍有库存 → 抛 location.has_stock，不执行 softDelete</li>
 *   <li>deleteWithValidByIds: 空集合 → 0，不打 DB</li>
 * </ul>
 *
 * @author djs
 * @since WMS-MD-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LocationInfoServiceImpl 单元测试")
class LocationInfoServiceImplTest {

    @Mock
    private LocationInfoMapper locationInfoMapper;

    @Mock
    private LocationStockMapper stockMapper;

    @Mock
    private DictService dictService;

    private TestableLocationInfoServiceImpl service;

    /**
     * 子类覆盖 toEntity 钩子，避开 SpringUtils.getBean(Converter.class)（MapStruct-Plus 容器）。
     */
    static class TestableLocationInfoServiceImpl extends LocationInfoServiceImpl {
        TestableLocationInfoServiceImpl(LocationInfoMapper baseMapper, LocationStockMapper stockMapper, DictService dictService) {
            super(baseMapper, stockMapper, dictService);
        }

        @Override
        protected LocationInfo toEntity(LocationInfoBo bo) {
            if (bo == null) return null;
            LocationInfo e = new LocationInfo();
            e.setId(bo.getId());
            e.setLocationCode(bo.getLocationCode());
            e.setLocationName(bo.getLocationName());
            e.setLocationType(bo.getLocationType());
            e.setLocationThumb(bo.getLocationThumb());
            e.setLocationImg(bo.getLocationImg());
            e.setLocationStatus(bo.getLocationStatus());
            e.setCapacity(bo.getCapacity());
            e.setRemark(bo.getRemark());
            return e;
        }
    }

    @BeforeEach
    void setup() {
        service = new TestableLocationInfoServiceImpl(locationInfoMapper, stockMapper, dictService);
    }

    private LocationInfoBo sampleBo() {
        LocationInfoBo bo = new LocationInfoBo();
        bo.setLocationCode("L0001");
        bo.setLocationName("冻品库");
        bo.setLocationType("frozen");
        bo.setLocationStatus(1);
        bo.setCapacity(new BigDecimal("1000.00"));
        return bo;
    }

    @Test
    @DisplayName("insertByBo: happy path → mapper.insert 调一次 + locationStatus 默认 1 兜底")
    void testInsertByBo_HappyPath() {
        LocationInfoBo bo = sampleBo();
        bo.setLocationStatus(null);  // 触发兜底
        when(locationInfoMapper.insert(any(LocationInfo.class))).thenAnswer(inv -> {
            LocationInfo e = inv.getArgument(0);
            e.setId(90001L);
            return 1;
        });

        int rows = service.insertByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<LocationInfo> captor = ArgumentCaptor.forClass(LocationInfo.class);
        verify(locationInfoMapper, times(1)).insert(captor.capture());
        LocationInfo saved = captor.getValue();
        assertThat(saved.getLocationCode()).isEqualTo("L0001");
        assertThat(saved.getLocationName()).isEqualTo("冻品库");
        assertThat(saved.getLocationType()).isEqualTo("frozen");
        assertThat(saved.getLocationStatus()).as("null 时应兜底 1").isEqualTo(1);
    }

    @Test
    @DisplayName("queryPageList: 走 mapper.selectVoPage 返 TableDataInfo")
    void testQueryPageList() {
        LocationInfoQuery query = new LocationInfoQuery();
        query.setLocationType("frozen");
        PageQuery pageQuery = new PageQuery(1, 10);

        LocationInfoVo vo = new LocationInfoVo();
        vo.setId(90001L);
        vo.setLocationCode("L0001");
        Page<LocationInfoVo> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(vo));
        mockPage.setTotal(1);
        when(locationInfoMapper.selectVoPage(any(Page.class), any(Wrapper.class))).thenReturn(mockPage);

        TableDataInfo<LocationInfoVo> result = service.queryPageList(query, pageQuery);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getLocationCode()).isEqualTo("L0001");
    }

    @Test
    @DisplayName("updateByBo: happy path → mapper.updateById 调一次，locationCode 强制锁回旧值")
    void testUpdateByBo_HappyPath() {
        LocationInfo existing = new LocationInfo();
        existing.setId(90001L);
        existing.setLocationCode("L0001");
        when(locationInfoMapper.selectById(90001L)).thenReturn(existing);
        when(locationInfoMapper.updateById(any(LocationInfo.class))).thenReturn(1);

        LocationInfoBo bo = sampleBo();
        bo.setId(90001L);
        bo.setLocationCode("L9999");  // 尝试改编码
        bo.setLocationName("冻品库（改）");

        int rows = service.updateByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<LocationInfo> captor = ArgumentCaptor.forClass(LocationInfo.class);
        verify(locationInfoMapper).updateById(captor.capture());
        assertThat(captor.getValue().getLocationName()).isEqualTo("冻品库（改）");
        assertThat(captor.getValue().getLocationCode()).as("编辑端点不允许改 locationCode").isEqualTo("L0001");
    }

    @Test
    @DisplayName("updateByBo: id 缺失 → 抛 ServiceException")
    void testUpdateByBo_NullId() {
        LocationInfoBo bo = sampleBo();
        bo.setId(null);
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("ID 不能为空");
    }

    @Test
    @DisplayName("updateStatus: happy path → 仅 set id + locationStatus 单字段调 updateById（ADMIN-LIST-FIX-001）")
    void testUpdateStatus_HappyPath() {
        when(locationInfoMapper.updateById(any(LocationInfo.class))).thenReturn(1);

        int rows = service.updateStatus(90001L, 2);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<LocationInfo> captor = ArgumentCaptor.forClass(LocationInfo.class);
        verify(locationInfoMapper, times(1)).updateById(captor.capture());
        LocationInfo saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(90001L);
        assertThat(saved.getLocationStatus()).as("目标状态应为 2=停用").isEqualTo(2);
        // 仅切状态：其它业务字段保持 null（updateById 非 null 才更新，不污染 locationCode/locationName 等）
        assertThat(saved.getLocationCode()).isNull();
        assertThat(saved.getLocationName()).isNull();
    }

    @Test
    @DisplayName("updateStatus: id 缺失 → 抛 ServiceException，不打 DB")
    void testUpdateStatus_NullId() {
        assertThatThrownBy(() -> service.updateStatus(null, 2))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("ID 不能为空");
        verify(locationInfoMapper, never()).updateById(any(LocationInfo.class));
    }

    @Test
    @DisplayName("deleteWithValidByIds: 无库存 happy → 走基类 softDelete，wrapper.setSql del_flag='1'")
    void testDeleteWithValidByIds_HappyPath_NoStock() {
        when(stockMapper.countActiveStockByLocation(90001L)).thenReturn(0L);
        when(stockMapper.countActiveStockByLocation(90002L)).thenReturn(0L);
        when(locationInfoMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        int rows = service.deleteWithValidByIds(List.of(90001L, 90002L));

        assertThat(rows).isEqualTo(2);
        ArgumentCaptor<UpdateWrapper<LocationInfo>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(locationInfoMapper, times(2)).update(isNull(), wrapperCaptor.capture());
        List<UpdateWrapper<LocationInfo>> capturedWrappers = wrapperCaptor.getAllValues();
        assertThat(capturedWrappers).allSatisfy(w -> {
            assertThat(w.getSqlSet()).contains("del_flag", "del_unique", "update_by", "update_time");
            assertThat(w.getExpression().getNormal().getSqlSegment()).contains("id");
        });
    }

    @Test
    @DisplayName("deleteWithValidByIds: 库位仍有库存 → 抛 ServiceException(location.has_stock)，不走 softDelete")
    void testDeleteWithValidByIds_HasStock() {
        when(stockMapper.countActiveStockByLocation(90001L)).thenReturn(3L);
        LocationInfo loc = new LocationInfo();
        loc.setId(90001L);
        loc.setLocationName("冻品库");
        when(locationInfoMapper.selectById(90001L)).thenReturn(loc);

        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(90001L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("冻品库")
            .hasMessageContaining("不允许删除");

        verify(locationInfoMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("deleteWithValidByIds: 空集合 → 返 0 短路")
    void testDeleteWithValidByIds_EmptyShortCircuit() {
        int rows = service.deleteWithValidByIds(List.of());
        assertThat(rows).isZero();
        verify(stockMapper, never()).countActiveStockByLocation(any());
        verify(locationInfoMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("getCardSummary: happy path → 以字典 8 类为 base，部分聚合 merge，无数据类补 0")
    void testGetCardSummary_HappyPath() {
        // 字典 8 类（有序）作为 base
        Map<String, String> dict = new LinkedHashMap<>();
        dict.put("white_bar", "白条");
        dict.put("frozen", "冻品");
        dict.put("veg_fresh", "蔬菜鲜品");
        dict.put("veg_heavy", "重口蔬菜");
        dict.put("packaging", "包材");
        dict.put("medicine", "药品");
        dict.put("dry_goods", "干货");
        dict.put("raw_material", "原材料");

        // 库存聚合仅 frozen 有数据
        LocationCardSummaryVo stockFrozen = new LocationCardSummaryVo();
        stockFrozen.setLocationType("frozen");
        stockFrozen.setLocationCount(2);
        stockFrozen.setProductCount(3);
        stockFrozen.setCurrentStock(new BigDecimal("120.500"));
        // 今日流水仅 frozen 有数据
        LocationCardSummaryVo flowFrozen = new LocationCardSummaryVo();
        flowFrozen.setLocationType("frozen");
        flowFrozen.setTodayInQty(new BigDecimal("10.00"));
        flowFrozen.setTodayOutQty(new BigDecimal("4.00"));

        try (MockedStatic<TenantHelper> mocked = mockStatic(TenantHelper.class)) {
            mocked.when(TenantHelper::getTenantId).thenReturn("1001");
            when(dictService.getAllDictByDictType("djs_location_type")).thenReturn(dict);
            when(locationInfoMapper.selectStockSummaryByType(eq("1001"))).thenReturn(List.of(stockFrozen));
            when(locationInfoMapper.selectTodayFlowByType(eq("1001"))).thenReturn(List.of(flowFrozen));
            when(locationInfoMapper.selectLastCheckByType(eq("1001"))).thenReturn(List.of());

            List<LocationCardSummaryVo> cards = service.getCardSummary();

            // 固定 8 行，顺序按字典
            assertThat(cards).hasSize(8);
            assertThat(cards.get(0).getLocationType()).isEqualTo("white_bar");
            assertThat(cards.get(0).getLocationTypeLabel()).isEqualTo("白条");
            // white_bar 无数据 → 补 0
            assertThat(cards.get(0).getLocationCount()).isZero();
            assertThat(cards.get(0).getCurrentStock()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(cards.get(0).getTodayInQty()).isEqualByComparingTo(BigDecimal.ZERO);
            // frozen 有数据 → 正确 merge
            LocationCardSummaryVo frozen = cards.stream().filter(c -> "frozen".equals(c.getLocationType())).findFirst().orElseThrow();
            assertThat(frozen.getLocationCount()).isEqualTo(2);
            assertThat(frozen.getProductCount()).isEqualTo(3);
            assertThat(frozen.getCurrentStock()).isEqualByComparingTo("120.500");
            assertThat(frozen.getTodayInQty()).isEqualByComparingTo("10.00");
            assertThat(frozen.getTodayOutQty()).isEqualByComparingTo("4.00");
        }
    }

}
