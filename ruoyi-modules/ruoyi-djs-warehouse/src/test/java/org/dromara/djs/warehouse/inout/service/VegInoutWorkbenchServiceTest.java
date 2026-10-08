package org.dromara.djs.warehouse.inout.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.plant.team.domain.query.PlantWorkTeamQuery;
import org.dromara.djs.plant.team.domain.vo.PlantWorkTeamVo;
import org.dromara.djs.plant.team.service.IPlantWorkTeamService;
import org.dromara.djs.warehouse.inout.domain.bo.VegInFinishBo;
import org.dromara.djs.warehouse.inout.domain.bo.VegInSubmitBo;
import org.dromara.djs.warehouse.inout.domain.bo.VegOutWorkbenchSubmitBo;
import org.dromara.djs.warehouse.inout.domain.vo.RecentOutDestVo;
import org.dromara.djs.warehouse.inout.domain.vo.VegOutProductVo;
import org.dromara.djs.warehouse.inout.domain.vo.VegOutStockRow;
import org.dromara.djs.warehouse.inout.domain.vo.VegOutStockVo;
import org.dromara.djs.warehouse.inout.mapper.VegInoutWorkbenchMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.veg.domain.bo.HarvestSubmitBo;
import org.dromara.djs.warehouse.veg.service.IVegetableHandleService;
import org.dromara.djs.warehouse.vegout.domain.bo.VegOutSubmitBo;
import org.dromara.djs.warehouse.vegout.service.IVegOutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 果蔬入库 / 出库工作台：只编排，写入全部落到毛菜处理采摘录入与毛菜间出库记账。 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VegInoutWorkbenchServiceTest {

    static final Long PRODUCT = 2001L;
    static final Long FRESH = 6L;
    static final Long SHELF = 3L;

    @Mock IVegetableHandleService vegHandleService;
    @Mock IVegOutService vegOutService;
    @Mock IPlantWorkTeamService teamService;
    @Mock LocationInfoMapper locationMapper;
    @Mock VegInoutWorkbenchMapper workbenchMapper;
    @Mock DictService dictService;
    @InjectMocks VegInoutWorkbenchService service;
    MockedStatic<LoginHelper> login;

    @BeforeAll
    static void entities() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, LocationInfo.class);
    }

    @BeforeEach
    void setup() {
        login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::getUserId).thenReturn(9L);
        when(dictService.getAllDictByDictType("djs_stock_out_dest"))
            .thenReturn(Map.of("kitchen", "厨房", "feed", "猪只饲料", "mine", "矿山"));
        when(vegHandleService.submitHarvest(any())).thenReturn(77L);
        PlantWorkTeamVo one = new PlantWorkTeamVo();
        one.setId(31L);
        PlantWorkTeamVo two = new PlantWorkTeamVo();
        two.setId(32L);
        when(teamService.queryList(any())).thenReturn(List.of(one, two));
    }

    @AfterEach
    void close() {
        login.close();
    }

    static VegOutStockRow basket(Long id, Long locationId, String locationName, Long plotId, String plotCode,
                                 String weight) {
        VegOutStockRow r = new VegOutStockRow();
        r.setStockId(id);
        r.setProductId(PRODUCT);
        r.setProductName("黄瓜");
        r.setProductUnit("kg");
        r.setLocationId(locationId);
        r.setLocationName(locationName);
        r.setPlotId(plotId);
        r.setPlotCode(plotCode);
        r.setThirdPhase(0);
        r.setStockWeight(new BigDecimal(weight));
        return r;
    }

    VegInSubmitBo inBo() {
        VegInSubmitBo bo = new VegInSubmitBo();
        bo.setPlantingRecordId(501L);
        bo.setProductId(PRODUCT);
        bo.setWeight(new BigDecimal("12.500"));
        bo.setTeamIds(Arrays.asList(31L, null, 32L, 31L));
        bo.setPerfPercent(80);
        return bo;
    }

    VegOutWorkbenchSubmitBo outBo(String destination, String outDest, String weight, Long... stockIds) {
        VegOutWorkbenchSubmitBo bo = new VegOutWorkbenchSubmitBo();
        bo.setProductId(PRODUCT);
        bo.setStockIds(List.of(stockIds));
        bo.setWeight(new BigDecimal(weight));
        bo.setDestination(destination);
        bo.setOutDest(outDest);
        return bo;
    }

    @Test
    void confirmInRecordsOneHarvestThroughTheMiniProgramEntry() {
        assertThat(service.submitIn(inBo())).isEqualTo(77L);
        ArgumentCaptor<HarvestSubmitBo> sent = ArgumentCaptor.forClass(HarvestSubmitBo.class);
        verify(vegHandleService).submitHarvest(sent.capture());
        HarvestSubmitBo h = sent.getValue();
        assertThat(h.getPlantingRecordId()).isEqualTo(501L);
        assertThat(h.getProductId()).isEqualTo(PRODUCT);
        assertThat(h.getHarvestWeight()).isEqualByComparingTo("12.5");
        assertThat(h.getWeighFinish()).isZero();
        assertThat(h.getWeighUserId()).isEqualTo(9L);
        assertThat(h.getTeamIds()).containsExactly(31L, 32L);
        assertThat(h.getPerfPercent()).isEqualTo(80);
    }

    @Test
    void confirmInWithoutPlotIsRejectedBeforeWriting() {
        VegInSubmitBo bo = inBo();
        bo.setPlantingRecordId(null);
        assertThatThrownBy(() -> service.submitIn(bo)).isInstanceOf(ServiceException.class).hasMessageContaining("采摘地块");
        verifyNoInteractions(vegHandleService);
    }

    @Test
    void confirmInRejectsNonPositiveWeight() {
        for (String w : List.of("0", "-1")) {
            VegInSubmitBo bo = inBo();
            bo.setWeight(new BigDecimal(w));
            assertThatThrownBy(() -> service.submitIn(bo)).isInstanceOf(ServiceException.class).hasMessageContaining("大于 0");
        }
        verifyNoInteractions(vegHandleService);
    }

    @Test
    void confirmInRequiresTeamAndPercentInRange() {
        VegInSubmitBo noTeam = inBo();
        noTeam.setTeamIds(Arrays.asList((Long) null));
        assertThatThrownBy(() -> service.submitIn(noTeam)).hasMessageContaining("采摘班组");
        for (Integer p : Arrays.asList(null, 0, 101)) {
            VegInSubmitBo bo = inBo();
            bo.setPerfPercent(p);
            assertThatThrownBy(() -> service.submitIn(bo)).hasMessageContaining("绩效百分比");
        }
        verifyNoInteractions(vegHandleService);
    }

    @Test
    void confirmInRejectsUnknownOrInactiveTeamsBeforeWriting() {
        VegInSubmitBo bo = inBo();
        bo.setTeamIds(List.of(31L, 999L));
        assertThatThrownBy(() -> service.submitIn(bo)).isInstanceOf(ServiceException.class)
            .hasMessageContaining("班组");
        verifyNoInteractions(vegHandleService);
    }

    @Test
    void finishIsTheZeroKilogramWeighFinishOfTheSameEntry() {
        VegInFinishBo bo = new VegInFinishBo();
        bo.setPlantingRecordId(501L);
        bo.setTeamIds(List.of(31L));
        bo.setPerfPercent(100);
        service.finishIn(bo);
        ArgumentCaptor<HarvestSubmitBo> sent = ArgumentCaptor.forClass(HarvestSubmitBo.class);
        verify(vegHandleService).submitHarvest(sent.capture());
        assertThat(sent.getValue().getHarvestWeight()).isEqualByComparingTo("0");
        assertThat(sent.getValue().getWeighFinish()).isEqualTo(1);
        assertThat(sent.getValue().getProductId()).isNull();
    }

    @Test
    void finishPropagatesTheHarvestEntryGuard() {
        when(vegHandleService.submitHarvest(any())).thenThrow(new ServiceException("请先完成地块采收操作"));
        VegInFinishBo bo = new VegInFinishBo();
        bo.setPlantingRecordId(501L);
        bo.setTeamIds(List.of(31L));
        bo.setPerfPercent(100);
        assertThatThrownBy(() -> service.finishIn(bo)).hasMessage("请先完成地块采收操作");
    }

    @Test
    void optionsListActiveTeamsAndTheFreshVegetableRoom() {
        LocationInfo loc = new LocationInfo();
        loc.setLocationName("毛菜鲜品库");
        when(locationMapper.selectOne(any())).thenReturn(loc);
        PlantWorkTeamVo team = new PlantWorkTeamVo();
        team.setId(31L);
        team.setTeamName("一组");
        when(teamService.queryList(any())).thenReturn(List.of(team));
        var options = service.inOptions();
        assertThat(options.destinationName()).isEqualTo("毛菜鲜品库");
        assertThat(options.teams()).extracting("teamName").containsExactly("一组");
        ArgumentCaptor<PlantWorkTeamQuery> q = ArgumentCaptor.forClass(PlantWorkTeamQuery.class);
        verify(teamService).queryList(q.capture());
        assertThat(q.getValue().getTeamStatus()).isEqualTo(1);
    }

    @Test
    void productCardsSumAllLocationsAndCountRealPlots() {
        VegOutStockRow noPlot = basket(13L, SHELF, "蔬菜保鲜库", null, null, "4");
        VegOutStockRow third = basket(14L, FRESH, "毛菜鲜品库", 88L, "TP", "1");
        third.setThirdPhase(1);
        when(workbenchMapper.selectVegStocks(isNull())).thenReturn(List.of(
            basket(11L, FRESH, "毛菜鲜品库", 101L, "A-001", "10"),
            basket(12L, SHELF, "蔬菜保鲜库", 101L, "A-001", "5"),
            noPlot, third));
        List<VegOutProductVo> products = service.outProducts();
        assertThat(products).hasSize(1);
        assertThat(products.getFirst().getPlotCount()).isEqualTo(1);
        assertThat(products.getFirst().getTotalStock()).isEqualByComparingTo("20");
    }

    @Test
    void plotCardsSplitByLocationKeepFifoAndPutNoPlotLast() {
        when(workbenchMapper.selectVegStocks(PRODUCT)).thenReturn(List.of(
            basket(13L, SHELF, "蔬菜保鲜库", null, null, "4"),
            basket(11L, FRESH, "毛菜鲜品库", 101L, "A-001", "10"),
            basket(15L, FRESH, "毛菜鲜品库", 101L, "A-001", "2"),
            basket(12L, SHELF, "蔬菜保鲜库", 101L, "A-001", "5")));
        List<VegOutStockVo> cards = service.outStocks(PRODUCT);
        assertThat(cards).hasSize(3);
        assertThat(cards.get(0).getLocationName()).isEqualTo("毛菜鲜品库");
        assertThat(cards.get(0).getStockIds()).containsExactly(11L, 15L);
        assertThat(cards.get(0).getStockWeight()).isEqualByComparingTo("12");
        assertThat(cards.get(1).getLocationName()).isEqualTo("蔬菜保鲜库");
        assertThat(cards.get(2).getPlotId()).isNull();
        assertThat(cards.get(2).getStockIds()).containsExactly(13L);
    }

    @Test
    void warehouseOutHandsTheCardToVegOutInFifoOrder() {
        when(workbenchMapper.selectVegStocks(PRODUCT)).thenReturn(List.of(
            basket(11L, FRESH, "毛菜鲜品库", 101L, "A-001", "10"),
            basket(15L, FRESH, "毛菜鲜品库", 101L, "A-001", "2")));
        service.submitOut(outBo("warehouse", "kitchen", "11", 15L, 11L));
        ArgumentCaptor<VegOutSubmitBo> sent = ArgumentCaptor.forClass(VegOutSubmitBo.class);
        verify(vegOutService).submitVegetableOut(sent.capture());
        VegOutSubmitBo bo = sent.getValue();
        assertThat(bo.getOutDest()).isEqualTo("kitchen");
        assertThat(bo.getOutDate()).isNotNull();
        assertThat(bo.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getStockIds()).containsExactly(11L, 15L);
            assertThat(item.getQuantity()).isEqualByComparingTo("11");
        });
    }

    @Test
    void outRejectsIncompleteCardRatherThanSkippingTheOldestBasket() {
        when(workbenchMapper.selectVegStocks(PRODUCT)).thenReturn(List.of(
            basket(11L, FRESH, "毛菜鲜品库", 101L, "A-001", "10"),
            basket(15L, FRESH, "毛菜鲜品库", 101L, "A-001", "2")));
        assertThatThrownBy(() -> service.submitOut(outBo("warehouse", "kitchen", "1", 15L)))
            .isInstanceOf(ServiceException.class).hasMessageContaining("刷新");
        verifyNoInteractions(vegOutService);
    }

    @Test
    void pigFeedOutUsesTheOrganicFeedDestination() {
        when(workbenchMapper.selectVegStocks(PRODUCT)).thenReturn(List.of(basket(13L, SHELF, "蔬菜保鲜库", null, null, "4")));
        service.submitOut(outBo("feed", null, "3.5", 13L));
        ArgumentCaptor<VegOutSubmitBo> sent = ArgumentCaptor.forClass(VegOutSubmitBo.class);
        verify(vegOutService).submitVegetableOut(sent.capture());
        assertThat(sent.getValue().getOutDest()).isEqualTo("feed");
        assertThat(sent.getValue().getItems().getFirst().getStockIds()).containsExactly(13L);
    }

    @Test
    void outRejectsMissingPlotNonPositiveWeightAndOverStock() {
        when(workbenchMapper.selectVegStocks(PRODUCT)).thenReturn(List.of(basket(11L, FRESH, "毛菜鲜品库", 101L, "A-001", "10")));
        VegOutWorkbenchSubmitBo noPlot = outBo("warehouse", "kitchen", "1");
        assertThatThrownBy(() -> service.submitOut(noPlot)).hasMessageContaining("出库地块");
        assertThatThrownBy(() -> service.submitOut(outBo("warehouse", "kitchen", "0", 11L))).hasMessageContaining("大于 0");
        assertThatThrownBy(() -> service.submitOut(outBo("warehouse", "kitchen", "10.001", 11L)))
            .hasMessageContaining("超过所选地块库存").hasMessageContaining("10");
        verifyNoInteractions(vegOutService);
    }

    @Test
    void outRejectsStaleOrMixedCardsAndBadDestinations() {
        when(workbenchMapper.selectVegStocks(PRODUCT)).thenReturn(List.of(
            basket(11L, FRESH, "毛菜鲜品库", 101L, "A-001", "10"),
            basket(12L, SHELF, "蔬菜保鲜库", 101L, "A-001", "5")));
        assertThatThrownBy(() -> service.submitOut(outBo("warehouse", "kitchen", "1", 99L))).hasMessageContaining("刷新");
        assertThatThrownBy(() -> service.submitOut(outBo("warehouse", "kitchen", "1", 11L, 12L))).hasMessageContaining("同一地块与库位");
        assertThatThrownBy(() -> service.submitOut(outBo("warehouse", null, "1", 11L))).hasMessageContaining("仓库出库去向");
        assertThatThrownBy(() -> service.submitOut(outBo("warehouse", "unknown", "1", 11L))).hasMessageContaining("仓库出库去向");
        assertThatThrownBy(() -> service.submitOut(outBo("feed", "kitchen", "1", 11L))).hasMessageContaining("不接受");
        verifyNoInteractions(vegOutService);
    }

    @Test
    void recentDestinationsKeepOnlyDictionaryValuesWithLabels() {
        RecentOutDestVo known = new RecentOutDestVo();
        known.setValue("mine");
        known.setCount(5L);
        RecentOutDestVo stale = new RecentOutDestVo();
        stale.setValue("retired");
        stale.setCount(9L);
        when(workbenchMapper.selectRecentVegOutDests()).thenReturn(List.of(stale, known));
        assertThat(service.recentOutDests()).singleElement().satisfies(r -> {
            assertThat(r.getValue()).isEqualTo("mine");
            assertThat(r.getLabel()).isEqualTo("矿山");
        });
    }

    @Test
    void recentDestinationsFillTenActiveChoicesAfterDiscardingRetiredOnes() {
        Map<String, String> dictionary = new LinkedHashMap<>();
        List<RecentOutDestVo> ranked = new java.util.ArrayList<>();
        RecentOutDestVo retired = new RecentOutDestVo();
        retired.setValue("retired");
        retired.setCount(100L);
        ranked.add(retired);
        for (int i = 1; i <= 11; i++) {
            dictionary.put("dest" + i, "去向" + i);
            RecentOutDestVo destination = new RecentOutDestVo();
            destination.setValue("dest" + i);
            destination.setCount(20L - i);
            ranked.add(destination);
        }
        when(dictService.getAllDictByDictType("djs_stock_out_dest")).thenReturn(dictionary);
        when(workbenchMapper.selectRecentVegOutDests()).thenReturn(ranked);
        assertThat(service.recentOutDests()).hasSize(10)
            .extracting(RecentOutDestVo::getValue)
            .containsExactly("dest1", "dest2", "dest3", "dest4", "dest5", "dest6", "dest7", "dest8", "dest9", "dest10");
    }
}
