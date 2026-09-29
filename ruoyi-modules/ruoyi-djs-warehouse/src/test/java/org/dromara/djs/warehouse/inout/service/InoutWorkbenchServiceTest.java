package org.dromara.djs.warehouse.inout.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.warehouse.burn.domain.vo.BurnProductTypeVo;
import org.dromara.djs.warehouse.burn.service.IPigBurnRecordService;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.cut.domain.PigCutRecord;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutPickupBo;
import org.dromara.djs.warehouse.cut.domain.vo.CutPartReceipt;
import org.dromara.djs.warehouse.cut.domain.vo.CutProductTypeVo;
import org.dromara.djs.warehouse.cut.mapper.PigCutRecordMapper;
import org.dromara.djs.warehouse.cut.service.IPigCutRecordService;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.inout.domain.bo.*;
import org.dromara.djs.warehouse.inout.domain.vo.CutWorkbenchBarVo;
import org.dromara.djs.warehouse.inout.domain.vo.CutStoreDemandVo;
import org.dromara.djs.warehouse.inout.mapper.InoutWorkbenchMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.pack.service.IProductProductionService;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.domain.bo.StockOutBo;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.stock.service.ILocationStockService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("local") @Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class InoutWorkbenchServiceTest {
    @Mock IPigBurnRecordService burnService;
    @Mock IPigCutRecordService cutService;
    @Mock IProductProductionService productionService;
    @Mock org.dromara.djs.warehouse.flow.service.IMatFlowService matFlowService;
    @Mock ILocationStockService stockService;
    @Mock BarInfoMapper barMapper;
    @Mock PigCutRecordMapper cutMapper;
    @Mock ProductInhouseMapper inhouseMapper;
    @Mock StockFlowMapper flowMapper;
    @Mock LocationStockMapper stockMapper;
    @Mock LocationInfoMapper locationMapper;
    @Mock InoutWorkbenchMapper workbenchMapper;
    @Mock DictService dictService;
    @InjectMocks InoutWorkbenchService service;
    MockedStatic<LoginHelper> login;
    static final String KEY="bf293158-d601-47d0-a904-71f120664fba";

    @BeforeAll static void entities() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("test");
        for (Class<?> c : List.of(StockFlow.class, ProductInhouse.class, LocationStock.class, PigCutRecord.class, LocationInfo.class)) {
            TableInfoHelper.initTableInfo(assistant, c);
        }
    }
    @BeforeEach void setup() {
        org.springframework.test.util.ReflectionTestUtils.setField(service,"completionPolicy",new WeightCompletionPolicy(dictService));
        var threshold=new org.dromara.common.core.domain.dto.DictDataDTO(); threshold.setIsDefault("Y"); threshold.setDictValue("50");
        when(dictService.getDictData(anyString())).thenReturn(List.of(threshold));

        login = mockStatic(LoginHelper.class); login.when(LoginHelper::getUserId).thenReturn(9L);
        BarInfo bar = new BarInfo(); bar.setId(1L); when(barMapper.selectForUpdate(1L)).thenReturn(bar);
        LocationInfo location = new LocationInfo(); location.setId(10L); location.setLocationStatus(1);
        when(locationMapper.selectById(10L)).thenReturn(location);
        when(dictService.getAllDictByDictType("djs_stock_out_dest")).thenReturn(Map.of("kitchen", "厨房"));
    }
    @AfterEach void close() { login.close(); }

    BurnWorkbenchSubmitBo burnBo() {
        var b=new BurnWorkbenchSubmitBo(); b.setBarInfoId(1L); b.setProductId(2L); b.setWeight(new BigDecimal("5.000"));
        b.setDestination("outbound"); b.setOutDest("kitchen"); b.setRequestId(KEY); return b;
    }
    StockFlow receipt(String hash) {
        StockFlow f=new StockFlow(); f.setId(500L); f.setRequestKey(KEY); f.setRequestHash(hash); f.setWhiteBarNo("WB1"); return f;
    }
    String burnHash() { return InoutWorkbenchService.fingerprint("burn",1L,2L,"5","outbound",null,"kitchen",9L); }

    @Test void successfulReplaySurvivesConsumedSourceAndFinishedPig() {
        when(flowMapper.selectOne(any())).thenReturn(receipt(burnHash()));
        var result=service.submitBurn(burnBo());
        assertThat(result.receiptId()).isEqualTo(500L);
        assertThat(result.requestId()).isEqualTo(KEY);
        verifyNoInteractions(inhouseMapper, burnService, productionService, stockService, barMapper);
    }
    @Test void sameKeyDifferentWeightRejectedBeforeAnyMutation() {
        when(flowMapper.selectOne(any())).thenReturn(receipt(burnHash()));
        var bo=burnBo(); bo.setWeight(new BigDecimal("6"));
        assertThatThrownBy(() -> service.submitBurn(bo)).isInstanceOf(ServiceException.class).hasMessageContaining("不同操作");
        verifyNoInteractions(inhouseMapper,burnService,stockService);
    }
    @Test void sameKeySameDecimalDifferentScaleReplays() {
        when(flowMapper.selectOne(any())).thenReturn(receipt(burnHash()));
        var bo=burnBo(); bo.setWeight(new BigDecimal("5.0"));
        assertThat(service.submitBurn(bo).receiptId()).isEqualTo(500L);
    }
    @Test void secondReceiptReadAfterPigLockHandlesConcurrentRetry() {
        when(flowMapper.selectOne(any())).thenReturn(null);
        when(flowMapper.selectRequestReceipt(KEY)).thenReturn(receipt(burnHash()));
        assertThat(service.submitBurn(burnBo()).receiptId()).isEqualTo(500L);
        verify(barMapper).selectForUpdate(1L);
        verifyNoInteractions(burnService,stockService);
    }
    @Test void directBurnOutputDeductsOnlyNewBasketAndConsumesWip() {
        var product=new BurnProductTypeVo(); product.setProductId(2L); product.setIsWhiteBar(false); product.setDefaultLocationId(10L);
        when(burnService.queryProductTypes(1L)).thenReturn(List.of(product));
        when(burnService.submitBurnRecord(any())).thenReturn(11L);
        var source=new ProductInhouse(); source.setId(12L); source.setWhiteBarNo("WB1");
        when(inhouseMapper.selectOne(any())).thenReturn(source);
        var stock=new LocationStock(); stock.setId(13L); when(stockMapper.selectOne(any())).thenReturn(stock);
        when(flowMapper.selectOne(any())).thenReturn(null,receipt(null));
        when(flowMapper.updateById(any(StockFlow.class))).thenReturn(1);
        when(inhouseMapper.deductWeightById(eq(12L), any())).thenReturn(1);
        when(inhouseMapper.deleteById(12L)).thenReturn(1);
        var result=service.submitBurn(burnBo());
        assertThat(result.receiptId()).isEqualTo(500L);
        var out=ArgumentCaptor.forClass(StockOutBo.class); verify(stockService).cutRoomOut(out.capture());
        assertThat(out.getValue().getStockIds()).containsExactly(13L);
        assertThat(out.getValue().getQuantity()).isEqualByComparingTo("5");
        var saved=ArgumentCaptor.forClass(StockFlow.class); verify(flowMapper).updateById(saved.capture());
        assertThat(saved.getValue().getRequestHash()).isEqualTo(burnHash());
        verify(inhouseMapper).deleteById(12L);
    }
    @Test void invalidDestinationNeverStartsInbound() {
        var product=new BurnProductTypeVo(); product.setProductId(2L); product.setIsWhiteBar(false); product.setDefaultLocationId(10L);
        when(burnService.queryProductTypes(1L)).thenReturn(List.of(product));
        var bo=burnBo(); bo.setOutDest("not-a-dictionary-key");
        assertThatThrownBy(() -> service.submitBurn(bo)).hasMessageContaining("有效的仓库出库去向");
        verify(burnService,never()).submitBurnRecord(any());
    }
    @Test void firstCutPicksEntireHalfAndOutUsesActualProducedBasket() {
        var bo=new CutWorkbenchSubmitBo(); bo.setInhouseId(12L); bo.setProductId(2L); bo.setWeight(new BigDecimal("5"));
        bo.setDestination("outbound"); bo.setOutDest("kitchen"); bo.setRequestId(KEY);
        var source=new ProductInhouse(); source.setId(12L); source.setWhiteBarId(1L); source.setProductWeight(new BigDecimal("40"));
        when(inhouseMapper.selectById(12L)).thenReturn(source); when(inhouseMapper.selectOne(any())).thenReturn(source);
        var card=new CutWorkbenchBarVo(); card.setInhouseId(12L); when(workbenchMapper.selectUnpickedBars()).thenReturn(List.of(card));
        var product=new CutProductTypeVo(); product.setProductId(2L); product.setDefaultLocationId(10L);
        when(cutService.queryCutProductTypes()).thenReturn(List.of(product));
        when(cutService.submitPickup(any())).thenReturn(20L);
        when(cutService.submitCutOutWithReceipt(any())).thenReturn(List.of(new CutPartReceipt(500L,13L)));
        when(flowMapper.selectById(500L)).thenReturn(receipt(null)); when(flowMapper.updateById(any(StockFlow.class))).thenReturn(1);
        var result=service.submitCut(bo); assertThat(result.cutRecordId()).isEqualTo(20L);
        var pickup=ArgumentCaptor.forClass(PigCutPickupBo.class); verify(cutService).submitPickup(pickup.capture());
        assertThat(pickup.getValue().getPickupWeight()).isEqualByComparingTo("40");
        var out=ArgumentCaptor.forClass(StockOutBo.class); verify(stockService).cutRoomOut(out.capture());
        assertThat(out.getValue().getStockIds()).containsExactly(13L);
        assertThat(out.getValue().getQuantity()).isEqualByComparingTo("5");
    }
    CutWorkbenchSubmitBo storeCutBo() {
        var bo=new CutWorkbenchSubmitBo(); bo.setCutRecordId(20L); bo.setProductId(2L); bo.setWeight(new BigDecimal("5"));
        bo.setDestination("store"); bo.setStoreId(30L); bo.setProductionProductId(40L); bo.setRequestId(KEY);
        var record=new PigCutRecord(); record.setId(20L); record.setWhiteBarId(1L); when(cutMapper.selectById(20L)).thenReturn(record);
        var product=new CutProductTypeVo(); product.setProductId(2L); product.setDefaultLocationId(10L);
        when(cutService.queryCutProductTypes()).thenReturn(List.of(product));
        var demand=new CutStoreDemandVo(); demand.setProductId(40L); demand.setStoreId(30L); demand.setMinimumWeight(new BigDecimal("5")); demand.setProductUnit("kg");
        when(workbenchMapper.selectCutStoreDemands(eq(2L),any())).thenReturn(List.of(demand)); return bo;
    }
    @Test void storeCutUsesOnlyReceiptBasketAndCreatesMappedProductionBeforeSavingReceipt() {
        var bo=storeCutBo();
        when(cutService.submitCutOutWithReceipt(any())).thenReturn(List.of(new CutPartReceipt(500L,13L)));
        when(matFlowService.pickCutOutput(13L,500L)).thenReturn(55L);
        when(flowMapper.selectById(500L)).thenReturn(receipt(null)); when(flowMapper.updateById(any(StockFlow.class))).thenReturn(1);
        assertThat(service.submitCut(bo).cutRecordId()).isEqualTo(20L);
        var pack=ArgumentCaptor.forClass(org.dromara.djs.warehouse.pack.domain.bo.DryPackBo.class);
        verify(productionService).submitCutStorePack(pack.capture());
        assertThat(pack.getValue().getProductId()).isEqualTo(40L);
        assertThat(pack.getValue().getSourceInhouseId()).isEqualTo(55L);
        assertThat(pack.getValue().getStoreId()).isEqualTo(30L);
        assertThat(pack.getValue().getDeliverDest()).isEqualTo("platform");
        verifyNoInteractions(stockService);
    }
    @Test void staleStoreDemandAndInsufficientWeightCannotStartCut() {
        var bo=storeCutBo(); bo.setWeight(new BigDecimal("4.999"));
        assertThatThrownBy(() -> service.submitCut(bo)).hasMessageContaining("未满足");
        bo.setWeight(new BigDecimal("5")); bo.setProductionProductId(41L);
        assertThatThrownBy(() -> service.submitCut(bo)).hasMessageContaining("当天未满足");
        verify(cutService,never()).submitCutOutWithReceipt(any()); verifyNoInteractions(matFlowService,productionService);
    }
    @Test void failedProductionCannotSaveSuccessfulReceipt() {
        var bo=storeCutBo(); when(cutService.submitCutOutWithReceipt(any())).thenReturn(List.of(new CutPartReceipt(500L,13L)));
        when(matFlowService.pickCutOutput(13L,500L)).thenReturn(55L);
        when(productionService.submitCutStorePack(any())).thenThrow(new ServiceException("需求并发变化"));
        assertThatThrownBy(() -> service.submitCut(bo)).hasMessageContaining("并发");
        verify(flowMapper,never()).updateById(any(StockFlow.class));
    }
    @Test void failureAtDirectOutputCannotSaveSuccessfulReceipt() {
        var product=new BurnProductTypeVo(); product.setProductId(2L); product.setIsWhiteBar(false); product.setDefaultLocationId(10L);
        when(burnService.queryProductTypes(1L)).thenReturn(List.of(product)); when(burnService.submitBurnRecord(any())).thenReturn(11L);
        var source=new ProductInhouse(); source.setId(12L); source.setWhiteBarNo("WB1"); when(inhouseMapper.selectOne(any())).thenReturn(source);
        var stock=new LocationStock(); stock.setId(13L); when(stockMapper.selectOne(any())).thenReturn(stock);
        when(flowMapper.selectOne(any())).thenReturn(null,receipt(null));
        when(stockService.cutRoomOut(any())).thenThrow(new ServiceException("盘点锁定"));
        assertThatThrownBy(() -> service.submitBurn(burnBo())).hasMessageContaining("盘点锁定");
        verify(flowMapper,never()).updateById(any(StockFlow.class));
        verify(inhouseMapper,never()).deleteById(anyLong());
    }
    PigCutRecord readyCutFinish(String pickup, String produced, String original) {
        PigCutRecord record=new PigCutRecord(); record.setId(20L); record.setWhiteBarId(1L); record.setWhiteBarNo("WB1");
        record.setOutType("cut"); record.setCutStatus("cutting"); record.setPickupTime(new Date());
        record.setPickupWeight(new BigDecimal(pickup));
        when(cutMapper.selectById(20L)).thenReturn(record); when(cutMapper.selectForUpdate(20L)).thenReturn(record);
        when(flowMapper.sumCutOutByWhiteBarNo("WB1")).thenReturn(new BigDecimal(produced));
        when(workbenchMapper.selectCutOriginalInWeight(20L)).thenReturn(original==null?null:new BigDecimal(original));
        return record;
    }
    org.dromara.djs.warehouse.cut.domain.bo.PigCutDoneBo finishBo(Boolean confirmed) {
        var bo=new org.dromara.djs.warehouse.cut.domain.bo.PigCutDoneBo(); bo.setCutRecordId(20L); bo.setConfirmAbnormalWeight(confirmed); return bo;
    }
    @Test void workbenchCutPreflightAndUnconfirmedFinishDoNotChangeState() {
        readyCutFinish("40","5","40");
        var check=service.cutFinishCheck(20L);
        assertThat(check.confirmationRequired()).isTrue(); assertThat(check.message()).isEqualTo(WeightCompletionPolicy.CUT_MESSAGE);
        assertThatThrownBy(() -> service.finishCut(finishBo(null))).hasMessage(WeightCompletionPolicy.CUT_MESSAGE);
        verify(cutService,never()).submitCutDone(any());
    }
    @Test void explicitlyConfirmedCutCanComplete() {
        readyCutFinish("40","5","40"); var bo=finishBo(true); service.finishCut(bo);
        verify(cutService).submitCutDone(bo);
    }
    @Test void cutUsesOriginalInboundWeightNotReducedPickupAndRechecksActualWeight() {
        readyCutFinish("36","16","40");
        var check=service.cutFinishCheck(20L);
        assertThat(check.confirmationRequired()).isFalse();
        assertThat(check.referenceWeight()).isEqualByComparingTo("40");
        assertThat(check.actualWeight()).isEqualByComparingTo("20");
        when(flowMapper.sumCutOutByWhiteBarNo("WB1")).thenReturn(new BigDecimal("15.999"));
        assertThatThrownBy(() -> service.finishCut(finishBo(false))).hasMessage(WeightCompletionPolicy.CUT_MESSAGE);
        verify(cutService,never()).submitCutDone(any());
    }
    @Test void purchasedHalfUsesFrozenPrecoolLossToRecoverOriginalWeight() {
        var record=readyCutFinish("36","16","40"); record.setDripLoss(new BigDecimal("4"));
        assertThat(service.cutFinishCheck(20L).referenceWeight()).isEqualByComparingTo("40");
        service.finishCut(finishBo(false)); verify(cutService).submitCutDone(any());
    }
    @Test void confirmationCannotBypassCutStatusOverweightMissingSourceOrInvalidConfiguration() {
        var record=readyCutFinish("40","5","40"); record.setCutStatus("picked");
        assertThatThrownBy(() -> service.finishCut(finishBo(true))).hasMessageContaining("状态不符");
        readyCutFinish("40","41","40");
        assertThatThrownBy(() -> service.finishCut(finishBo(true))).hasMessageContaining("超过领用重");
        readyCutFinish("40","5",null);
        assertThatThrownBy(() -> service.finishCut(finishBo(true))).hasMessageContaining("基准重量");
        readyCutFinish("40","5","40"); when(dictService.getDictData(anyString())).thenReturn(List.of());
        assertThatThrownBy(() -> service.finishCut(finishBo(true))).hasMessageContaining("默认百分比");
        verify(cutService,never()).submitCutDone(any());
    }

}
