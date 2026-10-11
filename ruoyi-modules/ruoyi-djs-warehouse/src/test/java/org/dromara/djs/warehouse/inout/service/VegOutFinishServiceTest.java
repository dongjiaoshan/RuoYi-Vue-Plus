package org.dromara.djs.warehouse.inout.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.inout.domain.bo.VegOutFinishBo;
import org.dromara.djs.warehouse.inout.domain.vo.VegOutStockRow;
import org.dromara.djs.warehouse.inout.mapper.VegInoutWorkbenchMapper;
import org.dromara.djs.warehouse.loss.domain.LossFlow;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
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
@MockitoSettings(strictness = Strictness.LENIENT)
class VegOutFinishServiceTest {
    @Mock VegInoutWorkbenchMapper workbenchMapper;
    @Mock LocationStockMapper stockMapper;
    @Mock ILossFlowService lossService;
    @Mock IStockCheckService checkService;
    @InjectMocks VegOutFinishService service;
    MockedStatic<LoginHelper> login;
    @BeforeEach void setup() {
        login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::getUserId).thenReturn(9L);
        when(lossService.record(any(LossFlow.class))).thenReturn(81L);
        when(stockMapper.deductStockById(anyLong(), any(), eq(9L))).thenReturn(1);
    }
    @AfterEach void close() { login.close(); }
    static VegOutStockRow row(long id,long location,long plot,String weight) {
        var r=new VegOutStockRow(); r.setStockId(id);r.setProductId(2001L);r.setLocationId(location);
        r.setPlotId(plot);r.setProductName("黄瓜");r.setProductUnit("kg");r.setThirdPhase(1);r.setStockWeight(new BigDecimal(weight));return r;
    }
    static VegOutFinishBo bo(Long... ids) { var b=new VegOutFinishBo();b.setProductId(2001L);b.setStockIds(Arrays.asList(ids));return b; }
    @Test void finishesOnlyCompleteSelectedGroupAndWritesTypedLossPerBasket() {
        when(workbenchMapper.selectVegStocksForUpdate(2001L)).thenReturn(List.of(row(1,6,8,"2.500"),row(2,6,8,"1.250"),row(3,3,8,"7")));
        assertThat(service.finish(bo(2L,1L))).hasSize(2);
        verify(stockMapper).deductStockById(1L,new BigDecimal("2.500"),9L);
        verify(stockMapper).deductStockById(2L,new BigDecimal("1.250"),9L);
        verify(stockMapper,never()).deductStockById(eq(3L),any(),anyLong());
        var losses=ArgumentCaptor.forClass(LossFlow.class);verify(lossService,times(2)).record(losses.capture());
        assertThat(losses.getAllValues()).allSatisfy(f->{assertThat(f.getLossType()).isEqualTo("veg_handle_loss");assertThat(f.getSourceBizType()).isEqualTo("veg_out_finish");assertThat(f.getSourceFlowId()).isNull();assertThat(f.getPlotId()).isEqualTo(8L);assertThat(f.getLocationId()).isEqualTo(6L);});
        assertThat(losses.getAllValues()).extracting(LossFlow::getSourceBizId).containsExactlyInAnyOrder(1L,2L);
        assertThat(losses.getAllValues()).extracting(f->f.getLossWeight().toPlainString()).containsExactlyInAnyOrder("2.500","1.250");
    }
    @Test void rejectsPartialOrStaleGroupWithoutWriting() {
        when(workbenchMapper.selectVegStocksForUpdate(2001L)).thenReturn(List.of(row(1,6,8,"2"),row(2,6,8,"1")));
        assertThatThrownBy(()->service.finish(bo(1L))).isInstanceOf(ServiceException.class);verifyNoInteractions(stockMapper,lossService);
    }
    @Test void rejectsMixedLocationsWithoutWriting() {
        when(workbenchMapper.selectVegStocksForUpdate(2001L)).thenReturn(List.of(row(1,6,8,"2"),row(2,3,8,"1")));
        assertThatThrownBy(()->service.finish(bo(1L,2L))).isInstanceOf(ServiceException.class);verifyNoInteractions(stockMapper,lossService);
    }
    @Test void rejectsMixedPlotsWithoutWriting() {
        when(workbenchMapper.selectVegStocksForUpdate(2001L)).thenReturn(List.of(row(1,6,8,"2"),row(2,6,9,"1")));
        assertThatThrownBy(()->service.finish(bo(1L,2L))).isInstanceOf(ServiceException.class);verifyNoInteractions(stockMapper,lossService);
    }
    @Test void rejectsDuplicateAndNullIds() {
        assertThatThrownBy(()->service.finish(bo(1L,1L))).isInstanceOf(ServiceException.class);
        assertThatThrownBy(()->service.finish(bo(1L,null))).isInstanceOf(ServiceException.class);verifyNoInteractions(workbenchMapper,stockMapper,lossService);
    }
    @Test void alreadyFinishedOrWrongProductCannotProduceSecondLoss() {
        when(workbenchMapper.selectVegStocksForUpdate(2001L)).thenReturn(List.of());
        assertThatThrownBy(()->service.finish(bo(1L))).isInstanceOf(ServiceException.class);verifyNoInteractions(stockMapper,lossService);
    }
    @Test void lockedLocationRejectsBeforeAnyDeduction() {
        when(workbenchMapper.selectVegStocksForUpdate(2001L)).thenReturn(List.of(row(1,6,8,"2")));
        doThrow(new ServiceException("盘点锁定")).when(checkService).assertLocationUnlocked(6L);
        assertThatThrownBy(()->service.finish(bo(1L))).isInstanceOf(ServiceException.class);verifyNoInteractions(stockMapper,lossService);
    }
    @Test void deductionFailureThrowsAndDoesNotWriteLoss() {
        when(workbenchMapper.selectVegStocksForUpdate(2001L)).thenReturn(List.of(row(1,6,8,"2")));
        when(stockMapper.deductStockById(eq(1L),any(),eq(9L))).thenReturn(0);
        assertThatThrownBy(()->service.finish(bo(1L))).isInstanceOf(ServiceException.class);verifyNoInteractions(lossService);
    }
    @Test void rejectedLossRecordAbortsWholeFinish() {
        when(workbenchMapper.selectVegStocksForUpdate(2001L)).thenReturn(List.of(row(1,6,8,"2")));
        when(lossService.record(any(LossFlow.class))).thenReturn(null);
        assertThatThrownBy(()->service.finish(bo(1L))).isInstanceOf(ServiceException.class).hasMessageContaining("损耗明细写入失败");
    }
    @Test void lossLedgerFailurePropagatesForTransactionRollback() {
        when(workbenchMapper.selectVegStocksForUpdate(2001L)).thenReturn(List.of(row(1,6,8,"2")));
        when(lossService.record(any(LossFlow.class))).thenThrow(new ServiceException("损耗写入失败"));
        assertThatThrownBy(()->service.finish(bo(1L))).isInstanceOf(ServiceException.class).hasMessageContaining("损耗写入失败");
    }
}
