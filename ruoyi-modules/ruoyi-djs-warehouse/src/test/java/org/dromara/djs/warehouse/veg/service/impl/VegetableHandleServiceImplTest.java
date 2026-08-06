package org.dromara.djs.warehouse.veg.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.veg.domain.HandleRecord;
import org.dromara.djs.warehouse.veg.domain.PlantingRecord;
import org.dromara.djs.warehouse.veg.domain.VegetableHandle;
import org.dromara.djs.warehouse.veg.domain.bo.HandleRecordSubmitBo;
import org.dromara.djs.warehouse.veg.domain.query.PickDetailQuery;
import org.dromara.djs.warehouse.veg.domain.vo.PickDetailVo;
import org.dromara.djs.warehouse.veg.mapper.HandleRecordMapper;
import org.dromara.djs.warehouse.veg.mapper.PlantingRecordMapper;
import org.dromara.djs.warehouse.veg.mapper.VegetableHandleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VegetableHandleServiceImpl} 单测（WMS-VEG-001）。
 *
 * <p>覆盖：</p>
 * <ol>
 *   <li>采收 happy：首次 record_type=1 → 创建 vegetable_handle + INSERT record + picked 累加</li>
 *   <li>处理-入库 happy：record_type=2 + handle_target=1 → stock_in 累加 + stock_flow IN</li>
 *   <li>处理-饲料 happy：record_type=2 + handle_target=3 → feed 累加 + 不写 stock_flow</li>
 *   <li>handle_target=1 但缺 location → 抛 + 任何 INSERT 不调用</li>
 *   <li>plantingRecord done → 抛</li>
 * </ol>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("VegetableHandleServiceImpl 单元测试")
class VegetableHandleServiceImplTest {

    @Mock
    private VegetableHandleMapper handleMapper;
    @Mock
    private HandleRecordMapper recordMapper;
    @Mock
    private org.dromara.djs.warehouse.veg.mapper.HandleRecordTeamMapper recordTeamMapper;
    @Mock
    private PlantingRecordMapper plantingRecordMapper;
    @Mock
    private StockFlowMapper stockFlowMapper;
    @Mock
    private LocationInfoMapper locationInfoMapper;
    @Mock
    private IBizCodeGenerator bizCodeGenerator;
    @Mock
    private org.dromara.djs.common.image.service.ImageUrlResolver imageUrlResolver;
    @Mock
    private CropInfoMapper cropInfoMapper;
    @Mock
    private org.dromara.djs.warehouse.veg.mapper.FeedLogMapper feedLogMapper;
    @Mock
    private org.dromara.djs.plant.plan.mapper.PlantDetailsMapper plantDetailsMapper;
    @Mock
    private org.dromara.djs.warehouse.stock.mapper.LocationStockMapper locationStockMapper;
    @Mock
    private org.dromara.djs.warehouse.product.mapper.ProductInfoMapper productInfoMapper;
    @Mock
    private org.dromara.djs.warehouse.loss.service.ILossFlowService lossFlowService;
    @Mock
    private org.dromara.djs.plant.activity.service.IPlantActivityService plantActivityService;
    @Mock
    private org.dromara.djs.plant.crop.service.ICropProductService cropProductService;

    private VegetableHandleServiceImpl service;

    private MockedStatic<LoginHelper> loginHelperMock;

    @BeforeEach
    void setup() {
        service = new VegetableHandleServiceImpl(
            handleMapper, recordMapper, recordTeamMapper, plantingRecordMapper, stockFlowMapper,
            locationInfoMapper, bizCodeGenerator, imageUrlResolver, cropInfoMapper, feedLogMapper,
            plantDetailsMapper, locationStockMapper, productInfoMapper, lossFlowService, plantActivityService,
            cropProductService);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(9001L);
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    private PlantingRecord samplePlanting(String status) {
        PlantingRecord p = new PlantingRecord();
        p.setId(60001L);
        p.setPlotId(11001L);
        p.setCropId(12001L);
        p.setPlotName("地块A");
        p.setCropName("卷心菜");
        p.setHarvestWeight(new BigDecimal("50.000"));
        p.setHandleStatus(status);
        return p;
    }

    // -------- happy 采收 --------

    @Test
    @DisplayName("采收 happy：首次 record_type=1 → 创建 vegetable_handle + record + picked 累加")
    void testPick_Happy_CreatesHandle() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("pending"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(null);
        when(handleMapper.insert(any(VegetableHandle.class))).thenAnswer(inv -> {
            VegetableHandle h = inv.getArgument(0);
            h.setId(70001L);
            return 1;
        });
        when(plantingRecordMapper.advanceHandleStatus(eq(60001L), eq("pending"), eq("processing"), eq(9001L)))
            .thenReturn(1);

        HandleRecordSubmitBo bo = new HandleRecordSubmitBo();
        bo.setPlantingRecordId(60001L);
        bo.setRecordType(1);
        bo.setRecordWeight(new BigDecimal("50.000"));

        Long handleId = service.submitHandleRecord(bo);

        assertThat(handleId).isEqualTo(70001L);

        // 1 行 vegetable_handle INSERT
        ArgumentCaptor<VegetableHandle> handleCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).insert(handleCap.capture());
        assertThat(handleCap.getValue().getPlantingRecordId()).isEqualTo(60001L);
        assertThat(handleCap.getValue().getHandleStatus()).isEqualTo("processing");

        // 1 行 handle_record INSERT
        ArgumentCaptor<HandleRecord> recCap = ArgumentCaptor.forClass(HandleRecord.class);
        verify(recordMapper, times(1)).insert(recCap.capture());
        HandleRecord rec = recCap.getValue();
        assertThat(rec.getRecordType()).isEqualTo(1);
        assertThat(rec.getRecordWeight()).isEqualByComparingTo("50.000");
        assertThat(rec.getHandleUser()).isEqualTo(9001L);
        assertThat(rec.getHandleId()).isEqualTo(70001L);

        // 聚合 UPDATE：picked=50, handled=feed=stock_in=0；loss=0（未处理完成不结算损耗，序号9-Req1 客户 2026-06-20）
        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        VegetableHandle upd = updCap.getValue();
        assertThat(upd.getPickedWeight()).isEqualByComparingTo("50.000");
        assertThat(upd.getHandledWeight()).isEqualByComparingTo("0");
        assertThat(upd.getFeedWeight()).isEqualByComparingTo("0");
        assertThat(upd.getStockInWeight()).isEqualByComparingTo("0");
        assertThat(upd.getLossWeight()).isEqualByComparingTo("0");

        // 没有 stock_flow
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));

        // planting_record 状态推进 pending → processing
        verify(plantingRecordMapper).advanceHandleStatus(eq(60001L), eq("pending"), eq("processing"), eq(9001L));
    }

    // -------- happy 处理-入库 --------

    @Test
    @DisplayName("处理-入库 happy：record_type=2 target=1 → stock_in 累加 + 1 行 stock_flow IN")
    void testHandle_StockIn_Happy() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        // 已有 vegetable_handle 行：picked=50 / handled=0 / feed=0 / stock_in=0
        VegetableHandle existing = new VegetableHandle();
        existing.setId(70001L);
        existing.setPlantingRecordId(60001L);
        existing.setPlotId(11001L);
        existing.setCropId(12001L);
        existing.setPickedWeight(new BigDecimal("50.000"));
        existing.setHandledWeight(BigDecimal.ZERO);
        existing.setFeedWeight(BigDecimal.ZERO);
        existing.setSendPlatformWeight(BigDecimal.ZERO);
        existing.setStockInWeight(BigDecimal.ZERO);
        existing.setLossWeight(BigDecimal.ZERO);
        existing.setHandleStatus("processing");
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(existing);

        // 作物 12001 配置了 related_product=88001（入库写 stock_flow 需可解析 product_id，
        // 无映射时服务显式抛错 —— happy path 必须配好映射）
        CropInfo crop = new CropInfo();
        crop.setId(12001L);
        crop.setRelatedProduct(88001L);
        when(cropInfoMapper.selectById(12001L)).thenReturn(crop);

        LocationInfo loc = new LocationInfo();
        loc.setId(90001L);
        loc.setLocationName("蔬菜鲜品库");
        when(locationInfoMapper.selectById(90001L)).thenReturn(loc);

        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap()))
            .thenReturn("F2606070IN0001");

        HandleRecordSubmitBo bo = new HandleRecordSubmitBo();
        bo.setPlantingRecordId(60001L);
        bo.setRecordType(2);
        bo.setHandleTarget(1);
        bo.setRecordWeight(new BigDecimal("30.000"));
        bo.setLocationId(90001L);

        service.submitHandleRecord(bo);

        // 不创建新 handle
        verify(handleMapper, never()).insert(any(VegetableHandle.class));

        // record_type=2 record 写入
        ArgumentCaptor<HandleRecord> recCap = ArgumentCaptor.forClass(HandleRecord.class);
        verify(recordMapper, times(1)).insert(recCap.capture());
        assertThat(recCap.getValue().getHandleTarget()).isEqualTo(1);
        assertThat(recCap.getValue().getLocationId()).isEqualTo(90001L);

        // 聚合 UPDATE：stockIn=30, handled=30, picked=50；loss=0（未处理完成不结算损耗，序号9-Req1）
        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        VegetableHandle upd = updCap.getValue();
        assertThat(upd.getStockInWeight()).isEqualByComparingTo("30.000");
        assertThat(upd.getHandledWeight()).isEqualByComparingTo("30.000");
        assertThat(upd.getPickedWeight()).isEqualByComparingTo("50.000");
        assertThat(upd.getLossWeight()).isEqualByComparingTo("0");

        // 1 行 stock_flow IN
        ArgumentCaptor<StockFlow> flowCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(flowCap.capture());
        StockFlow flow = flowCap.getValue();
        assertThat(flow.getFlowType()).isEqualTo("veg_stock_in");
        assertThat(flow.getInoutType()).isEqualTo("IN");
        assertThat(flow.getChangeNum()).isEqualByComparingTo("30.000");
        assertThat(flow.getWarehouseId()).isEqualTo(90001L);
        assertThat(flow.getPlotId()).isEqualTo(11001L);
        // product_id 走作物 related_product 映射解析
        assertThat(flow.getProductId()).isEqualTo(88001L);
    }

    // -------- happy 处理-饲料 --------

    @Test
    @DisplayName("处理-饲料 happy：target=3 → feed 累加 / 不写 stock_flow / handled 不变")
    void testHandle_Feed_Happy() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        VegetableHandle existing = new VegetableHandle();
        existing.setId(70001L);
        existing.setPlantingRecordId(60001L);
        existing.setPlotId(11001L);
        existing.setCropId(12001L);
        existing.setPickedWeight(new BigDecimal("50.000"));
        existing.setHandledWeight(new BigDecimal("30.000"));
        existing.setFeedWeight(BigDecimal.ZERO);
        existing.setSendPlatformWeight(BigDecimal.ZERO);
        existing.setStockInWeight(new BigDecimal("30.000"));
        existing.setLossWeight(BigDecimal.ZERO);
        existing.setHandleStatus("processing");
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(existing);

        HandleRecordSubmitBo bo = new HandleRecordSubmitBo();
        bo.setPlantingRecordId(60001L);
        bo.setRecordType(2);
        bo.setHandleTarget(3);
        bo.setRecordWeight(new BigDecimal("10.000"));

        service.submitHandleRecord(bo);

        // 聚合 UPDATE：feed=10, handled=30 不变；loss=0（未处理完成不结算损耗，序号9-Req1；饲料不算已处理）
        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        VegetableHandle upd = updCap.getValue();
        assertThat(upd.getFeedWeight()).isEqualByComparingTo("10.000");
        assertThat(upd.getHandledWeight()).isEqualByComparingTo("30.000");
        assertThat(upd.getLossWeight()).isEqualByComparingTo("0");

        // 没有 stock_flow
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        // submitHandleRecord（admin 路径）feed 只累加聚合字段，不写 feed_log（feed_log 由 mp 录入路径写）
        verify(feedLogMapper, never()).insert(any(org.dromara.djs.warehouse.veg.domain.FeedLog.class));
    }

    // -------- error path --------

    @Test
    @DisplayName("入库缺 location → 抛 + 任何 INSERT 不调用")
    void testHandle_StockIn_MissingLocation() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        VegetableHandle existing = new VegetableHandle();
        existing.setId(70001L);
        existing.setPickedWeight(new BigDecimal("50.000"));
        existing.setHandleStatus("processing");
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(existing);

        HandleRecordSubmitBo bo = new HandleRecordSubmitBo();
        bo.setPlantingRecordId(60001L);
        bo.setRecordType(2);
        bo.setHandleTarget(1);
        bo.setRecordWeight(new BigDecimal("30.000"));
        // locationId 故意不填

        assertThatThrownBy(() -> service.submitHandleRecord(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("入库需指定库位");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
    }

    @Test
    @DisplayName("planting_record done → 抛")
    void testPlantingDone_Reject() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("done"));

        HandleRecordSubmitBo bo = new HandleRecordSubmitBo();
        bo.setPlantingRecordId(60001L);
        bo.setRecordType(1);
        bo.setRecordWeight(new BigDecimal("10.000"));

        assertThatThrownBy(() -> service.submitHandleRecord(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已处理完成");

        verify(handleMapper, never()).insert(any(VegetableHandle.class));
        verify(recordMapper, never()).insert(any(HandleRecord.class));
    }

    @Test
    @DisplayName("首次录入非采收 → 抛")
    void testFirstSubmit_NotPick() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("pending"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(null);

        HandleRecordSubmitBo bo = new HandleRecordSubmitBo();
        bo.setPlantingRecordId(60001L);
        bo.setRecordType(2);
        bo.setHandleTarget(3);
        bo.setRecordWeight(new BigDecimal("10.000"));

        assertThatThrownBy(() -> service.submitHandleRecord(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("首次录入必须是采收记录");

        verify(handleMapper, never()).insert(any(VegetableHandle.class));
        verify(recordMapper, never()).insert(any(HandleRecord.class));
    }

    @Test
    @DisplayName("并发 CAS 失败：advanceHandleStatus pending→processing 返 0（已被并发工人推进），主事务不崩")
    void testConcurrent_AdvanceReturnZero_NoCrash() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("pending"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(null);
        when(handleMapper.insert(any(VegetableHandle.class))).thenAnswer(inv -> {
            VegetableHandle h = inv.getArgument(0);
            h.setId(70001L);
            return 1;
        });
        // 模拟并发：另一个工人 0.001s 前已推进 pending→processing，本事务 CAS 返 0
        when(plantingRecordMapper.advanceHandleStatus(eq(60001L), eq("pending"), eq("processing"), eq(9001L)))
            .thenReturn(0);

        HandleRecordSubmitBo bo = new HandleRecordSubmitBo();
        bo.setPlantingRecordId(60001L);
        bo.setRecordType(1);
        bo.setRecordWeight(new BigDecimal("50.000"));

        // 不应抛 — CAS 失败仅说明状态已被并发推进，主事务（采收 record + 聚合）已落库才是核心语义
        Long handleId = service.submitHandleRecord(bo);
        assertThat(handleId).isEqualTo(70001L);

        // record + handle 仍正常 INSERT/UPDATE
        verify(recordMapper, times(1)).insert(any(HandleRecord.class));
        verify(handleMapper, times(1)).updateById(any(VegetableHandle.class));

        // advanceHandleStatus 被调一次（即使返 0）
        verify(plantingRecordMapper, times(1)).advanceHandleStatus(
            eq(60001L), eq("pending"), eq("processing"), eq(9001L));
    }

    @Test
    @DisplayName("并发终态：isFinish=1 但 advanceHandleStatus processing→done 返 0（已被推完），主事务不崩")
    void testConcurrent_FinishAdvanceReturnZero_NoCrash() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        VegetableHandle existing = new VegetableHandle();
        existing.setId(70001L);
        existing.setPickedWeight(new BigDecimal("50.000"));
        existing.setHandledWeight(new BigDecimal("40.000"));
        existing.setFeedWeight(new BigDecimal("10.000"));
        existing.setSendPlatformWeight(BigDecimal.ZERO);
        existing.setStockInWeight(new BigDecimal("40.000"));
        existing.setLossWeight(BigDecimal.ZERO);
        existing.setHandleStatus("processing");
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(existing);
        // 并发：另一个 finish 提交 0.001s 前已推 done
        when(plantingRecordMapper.advanceHandleStatus(anyLong(), any(), any(), anyLong())).thenReturn(0);

        HandleRecordSubmitBo bo = new HandleRecordSubmitBo();
        bo.setPlantingRecordId(60001L);
        bo.setRecordType(1);
        bo.setRecordWeight(new BigDecimal("0.001"));
        bo.setIsFinish(1);

        // 不抛：CAS 失败被吸收，主事务（vegetable_handle.done + record + 聚合）仍落库
        service.submitHandleRecord(bo);

        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper).updateById(updCap.capture());
        assertThat(updCap.getValue().getIsFinish()).isEqualTo(1);
        assertThat(updCap.getValue().getHandleStatus()).isEqualTo("done");
    }

    // -------- 作物→产品转换 related_product（果疏全流程 Part II） --------

    @Test
    @DisplayName("采收建汇总行：crop.related_product 命中 → vegetable_handle.product_id 取该映射值")
    void testPick_ResolvesProductIdFromCropRelatedProduct() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("pending"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(null);
        // 作物 12001 配置了 related_product=88001（→ t_warehouse_product_info.id，belong_type=vegetable）
        CropInfo crop = new CropInfo();
        crop.setId(12001L);
        crop.setRelatedProduct(88001L);
        when(cropInfoMapper.selectById(12001L)).thenReturn(crop);
        when(handleMapper.insert(any(VegetableHandle.class))).thenAnswer(inv -> {
            VegetableHandle h = inv.getArgument(0);
            h.setId(70001L);
            return 1;
        });

        HandleRecordSubmitBo bo = new HandleRecordSubmitBo();
        bo.setPlantingRecordId(60001L);
        bo.setRecordType(1);
        bo.setRecordWeight(new BigDecimal("50.000"));

        service.submitHandleRecord(bo);

        // vegetable_handle INSERT 时 product_id = crop.related_product，重量不变
        ArgumentCaptor<VegetableHandle> handleCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).insert(handleCap.capture());
        assertThat(handleCap.getValue().getProductId()).isEqualTo(88001L);
        assertThat(handleCap.getValue().getPickedWeight()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("采收建汇总行：crop.related_product 为空 → product_id 降级（不抛、不阻塞采摘）")
    void testPick_RelatedProductNull_Degrades() {
        PlantingRecord planting = samplePlanting("pending");
        planting.setProductId(null); // 旧来源也空
        when(plantingRecordMapper.selectById(60001L)).thenReturn(planting);
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(null);
        // 作物未配 related_product（现网全 NULL 场景）
        CropInfo crop = new CropInfo();
        crop.setId(12001L);
        crop.setRelatedProduct(null);
        when(cropInfoMapper.selectById(12001L)).thenReturn(crop);
        when(handleMapper.insert(any(VegetableHandle.class))).thenAnswer(inv -> {
            VegetableHandle h = inv.getArgument(0);
            h.setId(70001L);
            return 1;
        });

        HandleRecordSubmitBo bo = new HandleRecordSubmitBo();
        bo.setPlantingRecordId(60001L);
        bo.setRecordType(1);
        bo.setRecordWeight(new BigDecimal("50.000"));

        // 不抛：降级保持现状（product_id 回退到旧来源 null）
        Long handleId = service.submitHandleRecord(bo);
        assertThat(handleId).isEqualTo(70001L);

        ArgumentCaptor<VegetableHandle> handleCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).insert(handleCap.capture());
        assertThat(handleCap.getValue().getProductId()).isNull();
        // 采摘聚合仍正常落库
        verify(recordMapper, times(1)).insert(any(HandleRecord.class));
        verify(handleMapper, times(1)).updateById(any(VegetableHandle.class));
    }

    @Test
    @DisplayName("处理-入库：stock_flow.product_id 取作物 related_product 映射值")
    void testHandle_StockIn_FlowProductIdFromCrop() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        VegetableHandle existing = new VegetableHandle();
        existing.setId(70001L);
        existing.setPlantingRecordId(60001L);
        existing.setPlotId(11001L);
        existing.setCropId(12001L);
        existing.setProductId(88001L); // 建汇总行时已解析存入
        existing.setPickedWeight(new BigDecimal("50.000"));
        existing.setHandledWeight(BigDecimal.ZERO);
        existing.setFeedWeight(BigDecimal.ZERO);
        existing.setSendPlatformWeight(BigDecimal.ZERO);
        existing.setStockInWeight(BigDecimal.ZERO);
        existing.setLossWeight(BigDecimal.ZERO);
        existing.setHandleStatus("processing");
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(existing);

        CropInfo crop = new CropInfo();
        crop.setId(12001L);
        crop.setRelatedProduct(88001L);
        when(cropInfoMapper.selectById(12001L)).thenReturn(crop);

        LocationInfo loc = new LocationInfo();
        loc.setId(90001L);
        when(locationInfoMapper.selectById(90001L)).thenReturn(loc);
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap()))
            .thenReturn("F2606070IN0001");

        HandleRecordSubmitBo bo = new HandleRecordSubmitBo();
        bo.setPlantingRecordId(60001L);
        bo.setRecordType(2);
        bo.setHandleTarget(1);
        bo.setRecordWeight(new BigDecimal("30.000"));
        bo.setLocationId(90001L);

        service.submitHandleRecord(bo);

        ArgumentCaptor<StockFlow> flowCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(flowCap.capture());
        assertThat(flowCap.getValue().getProductId()).isEqualTo(88001L);
        // 重量不变
        assertThat(flowCap.getValue().getChangeNum()).isEqualByComparingTo("30.000");
    }

    @Test
    @DisplayName("isFinish=1 → vegetable_handle.done + planting_record processing → done")
    void testFinish_AdvanceStatus() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        VegetableHandle existing = new VegetableHandle();
        existing.setId(70001L);
        existing.setPickedWeight(new BigDecimal("50.000"));
        existing.setHandledWeight(new BigDecimal("40.000"));
        existing.setFeedWeight(new BigDecimal("10.000"));
        existing.setSendPlatformWeight(BigDecimal.ZERO);
        existing.setStockInWeight(new BigDecimal("40.000"));
        existing.setLossWeight(BigDecimal.ZERO);
        existing.setHandleStatus("processing");
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(existing);
        when(plantingRecordMapper.advanceHandleStatus(anyLong(), any(), any(), anyLong())).thenReturn(1);

        HandleRecordSubmitBo bo = new HandleRecordSubmitBo();
        bo.setPlantingRecordId(60001L);
        bo.setRecordType(1);
        bo.setRecordWeight(new BigDecimal("0.001"));
        bo.setIsFinish(1);

        service.submitHandleRecord(bo);

        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper).updateById(updCap.capture());
        assertThat(updCap.getValue().getIsFinish()).isEqualTo(1);
        assertThat(updCap.getValue().getHandleStatus()).isEqualTo("done");

        // planting_record 推 processing → done
        verify(plantingRecordMapper).advanceHandleStatus(eq(60001L), eq("processing"), eq("done"), eq(9001L));
    }

    // -------- submitProcess 新守门 + 损耗结算（客户 2026-06-20 序号6/9） --------

    private VegetableHandle sampleHandle(String picked, String handled, int isWeighed) {
        VegetableHandle h = new VegetableHandle();
        h.setId(70001L);
        h.setPlantingRecordId(60001L);
        h.setPlotId(11001L);
        h.setCropId(12001L);
        h.setPickedWeight(new BigDecimal(picked));
        h.setHandledWeight(new BigDecimal(handled));
        h.setFeedWeight(BigDecimal.ZERO);
        h.setSendPlatformWeight(BigDecimal.ZERO);
        // 行59 损耗口径 loss = picked − stockIn − sendPlatform − feed：
        // 保持台账自洽 handled = stockIn + sendPlatform（已处理量全记入库分量）
        h.setStockInWeight(new BigDecimal(handled));
        h.setLossWeight(BigDecimal.ZERO);
        h.setIsWeighed(isWeighed);
        h.setHandleStatus("processing");
        return h;
    }

    private org.dromara.djs.warehouse.veg.domain.bo.ProcessSubmitBo processBo(
        Integer target, String weight, int finish) {
        org.dromara.djs.warehouse.veg.domain.bo.ProcessSubmitBo bo =
            new org.dromara.djs.warehouse.veg.domain.bo.ProcessSubmitBo();
        bo.setPlantingRecordId(60001L);
        bo.setHandleTarget(target);
        bo.setProcessWeight(new BigDecimal(weight));
        bo.setProcessFinish(finish);
        bo.setProcessUserId(9001L);
        return bo;
    }

    @Test
    @DisplayName("序号6-Req2：果蔬处理累计 > 采摘 → 抛「不得大于采摘」，不落库")
    void testProcess_OverHarvest_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        // picked=50, 已处理 handled=40，本次月台再处理 20 → 60 > 50 → 拦
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "40.000", 1));

        assertThatThrownBy(() -> service.submitProcess(processBo(2, "20.000", 0)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("果蔬处理重量不得大于果蔬采摘录入重量");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
    }

    @Test
    @DisplayName("序号9-Req2：未称重完成却勾处理完成 → 抛「请先完成地块称重」，不落库")
    void testProcess_FinishWithoutWeigh_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        // picked=50, handled=10, 本次 10（不超采摘）；但 is_weighed=2 未称重完成 + processFinish=1 → 拦
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "10.000", 2));

        assertThatThrownBy(() -> service.submitProcess(processBo(2, "10.000", 1)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("请先完成地块称重");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
    }

    @Test
    @DisplayName("序号9-Req1：处理完成才结算损耗 loss=采摘−处理（饲料不算已处理）")
    void testProcess_Finish_SettlesLoss() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        // picked=50, handled=30, 本次月台 10 → handled=40；is_weighed=1 + finish=1 → 结算 loss=50-40=10
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "30.000", 1));
        when(plantingRecordMapper.advanceHandleStatus(anyLong(), any(), any(), anyLong())).thenReturn(1);

        service.submitProcess(processBo(2, "10.000", 1));

        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        VegetableHandle upd = updCap.getValue();
        assertThat(upd.getHandledWeight()).isEqualByComparingTo("40.000");
        assertThat(upd.getLossWeight()).isEqualByComparingTo("10.000");
        assertThat(upd.getIsFinish()).isEqualTo(1);
        assertThat(upd.getHandleStatus()).isEqualTo("done");
    }

    // -------- V6 row28 / row29：0 kg 收口（重量为 0 也能把地块推到完成）--------

    /**
     * 采摘录入 BO（V6 row28 四种组合共用）。
     *
     * @param weight      采摘重量
     * @param weighFinish 地块是否称重完成 1=是 / 0=否
     * @param teamIds     采摘班组（传 null 模拟「不选绩效组」）
     */
    private org.dromara.djs.warehouse.veg.domain.bo.HarvestSubmitBo harvestBo(
        String weight, int weighFinish, List<Long> teamIds) {
        org.dromara.djs.warehouse.veg.domain.bo.HarvestSubmitBo bo =
            new org.dromara.djs.warehouse.veg.domain.bo.HarvestSubmitBo();
        bo.setPlantingRecordId(60001L);
        bo.setHarvestWeight(new BigDecimal(weight));
        bo.setWeighFinish(weighFinish);
        bo.setWeighUserId(9001L);
        bo.setTeamIds(teamIds);
        return bo;
    }

    /** 未称重完成的汇总行（isWeighed=2），采摘录入的正常前置状态 */
    private VegetableHandle harvestHandle(String picked) {
        VegetableHandle h = new VegetableHandle();
        h.setId(70001L);
        h.setPlantingRecordId(60001L);
        h.setPlotId(11001L);
        h.setCropId(12001L);
        h.setPickedWeight(new BigDecimal(picked));
        h.setHandledWeight(BigDecimal.ZERO);
        h.setFeedWeight(BigDecimal.ZERO);
        h.setSendPlatformWeight(BigDecimal.ZERO);
        h.setStockInWeight(BigDecimal.ZERO);
        h.setLossWeight(BigDecimal.ZERO);
        h.setIsWeighed(2);
        h.setHandleStatus("processing");
        return h;
    }

    @Test
    @DisplayName("row28①：重量 0 + 称重完成=是 → 提交成功，落 0 kg 记录 + is_weighed 推 1，picked 不变")
    void testHarvest_ZeroWeight_WeighFinish_Ok() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(harvestHandle("50.000"));
        // weighFinish=1 的前置门：种植端该地块采摘已完成
        when(plantDetailsMapper.selectCount(any())).thenReturn(1L);

        Long handleId = service.submitHarvest(harvestBo("0", 1, List.of(30001L)));
        assertThat(handleId).isEqualTo(70001L);

        ArgumentCaptor<HandleRecord> recCap = ArgumentCaptor.forClass(HandleRecord.class);
        verify(recordMapper, times(1)).insert(recCap.capture());
        assertThat(recCap.getValue().getRecordWeight()).isEqualByComparingTo("0");
        assertThat(recCap.getValue().getIsWeighed()).isEqualTo(1);

        // 聚合：picked 加 0 仍是 50（不能变 NULL，否则汇总脏），is_weighed 推 1
        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        assertThat(updCap.getValue().getPickedWeight()).isEqualByComparingTo("50.000");
        assertThat(updCap.getValue().getIsWeighed()).isEqualTo(1);
    }

    @Test
    @DisplayName("row28②：重量 0 + 称重完成=否 → 抛「必须打开地块是否称重完成」，不落库")
    void testHarvest_ZeroWeight_NoFinish_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(harvestHandle("50.000"));

        assertThatThrownBy(() -> service.submitHarvest(harvestBo("0", 0, List.of(30001L))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("必须打开「地块是否称重完成」");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
    }

    @Test
    @DisplayName("row28③：重量 0 + 称重完成=是 + 不选绩效组 → 提交成功，team_id 留空、中间表不写行")
    void testHarvest_ZeroWeight_WeighFinish_NoTeam_Ok() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(harvestHandle("50.000"));
        when(plantDetailsMapper.selectCount(any())).thenReturn(1L);

        // teamIds=null 模拟 mp 完全不传班组字段（比空数组更极端，服务端不得 NPE）
        Long handleId = service.submitHarvest(harvestBo("0", 1, null));
        assertThat(handleId).isEqualTo(70001L);

        ArgumentCaptor<HandleRecord> recCap = ArgumentCaptor.forClass(HandleRecord.class);
        verify(recordMapper, times(1)).insert(recCap.capture());
        assertThat(recCap.getValue().getTeamId()).isNull();
        // 班组多选中间表一行都不写（0 kg 不进 row39 绩效聚合）
        verify(recordTeamMapper, never())
            .insert(any(org.dromara.djs.warehouse.veg.domain.HandleRecordTeam.class));
    }

    @Test
    @DisplayName("row28④：重量 > 0 + 不选绩效组 → 仍抛「请选择采摘班组」，不落库（原口径不放宽）")
    void testHarvest_PositiveWeight_NoTeam_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(harvestHandle("50.000"));
        when(plantDetailsMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.submitHarvest(harvestBo("12.500", 0, List.of())))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("请选择采摘班组");
        // 连 null 也一样拦
        assertThatThrownBy(() -> service.submitHarvest(harvestBo("12.500", 1, null)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("请选择采摘班组");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
    }

    @Test
    @DisplayName("row29①：处理重量 0 + 处理完成=是 → 收口成功（done），不写 0 kg 的入库流水 / 饲料台账")
    void testProcess_ZeroWeight_ProcessFinish_Ok() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        // picked=50 已全部处理完（handled=50），现场只差点「处理完成」→ 0 kg 收口
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "50.000", 1));
        when(plantingRecordMapper.advanceHandleStatus(anyLong(), any(), any(), anyLong())).thenReturn(1);

        // 去向③饲料：0 kg 不得写 feed_log
        service.submitProcess(processBo(3, "0", 1));

        ArgumentCaptor<HandleRecord> recCap = ArgumentCaptor.forClass(HandleRecord.class);
        verify(recordMapper, times(1)).insert(recCap.capture());
        assertThat(recCap.getValue().getRecordWeight()).isEqualByComparingTo("0");
        assertThat(recCap.getValue().getIsFinish()).isEqualTo(1);

        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        VegetableHandle upd = updCap.getValue();
        // 各重量加 0 后原值不变（不得变 NULL）
        assertThat(upd.getHandledWeight()).isEqualByComparingTo("50.000");
        assertThat(upd.getFeedWeight()).isEqualByComparingTo("0");
        assertThat(upd.getIsFinish()).isEqualTo(1);
        assertThat(upd.getHandleStatus()).isEqualTo("done");

        verify(feedLogMapper, never()).insert(any(org.dromara.djs.warehouse.veg.domain.FeedLog.class));
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(plantingRecordMapper).advanceHandleStatus(eq(60001L), eq("processing"), eq("done"), eq(9001L));
    }

    @Test
    @DisplayName("row29②：处理重量 0 + 处理完成=否 → 抛「必须打开地块是否处理完成」，不落库")
    void testProcess_ZeroWeight_NoFinish_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "10.000", 1));

        assertThatThrownBy(() -> service.submitProcess(processBo(2, "0", 0)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("必须打开「地块是否处理完成」");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
    }

    // -------- V6 row41：0 kg 收口时去向不必填（有货仍必填）--------

    @Test
    @DisplayName("row41①：处理重量 0 + 处理完成=是 + 不选去向 → 收口成功，handle_target 落 NULL，三个重量桶都不动")
    void testProcess_ZeroWeight_NoTarget_Ok() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        // picked=50 已全部处理完（handled=50 全记入库），只差点「处理完成」→ 0 kg 收口且懒得选去向
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "50.000", 1));
        when(plantingRecordMapper.advanceHandleStatus(anyLong(), any(), any(), anyLong())).thenReturn(1);

        service.submitProcess(processBo(null, "0", 1));

        ArgumentCaptor<HandleRecord> recCap = ArgumentCaptor.forClass(HandleRecord.class);
        verify(recordMapper, times(1)).insert(recCap.capture());
        assertThat(recCap.getValue().getRecordWeight()).isEqualByComparingTo("0");
        assertThat(recCap.getValue().getHandleTarget()).isNull();
        assertThat(recCap.getValue().getIsFinish()).isEqualTo(1);

        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        VegetableHandle upd = updCap.getValue();
        // 未选去向 → 三个桶一个都不许动（也不得变 NULL）
        assertThat(upd.getStockInWeight()).isEqualByComparingTo("50.000");
        assertThat(upd.getSendPlatformWeight()).isEqualByComparingTo("0");
        assertThat(upd.getFeedWeight()).isEqualByComparingTo("0");
        assertThat(upd.getHandledWeight()).isEqualByComparingTo("50.000");
        assertThat(upd.getIsFinish()).isEqualTo(1);
        assertThat(upd.getHandleStatus()).isEqualTo("done");

        // 0 kg 不写任何下游台账
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(feedLogMapper, never()).insert(any(org.dromara.djs.warehouse.veg.domain.FeedLog.class));
        verify(plantingRecordMapper).advanceHandleStatus(eq(60001L), eq("processing"), eq("done"), eq(9001L));
    }

    @Test
    @DisplayName("row41②：0 kg + 不选去向 → 损耗照常按 picked−入库−月台−饲料 结算并双写 loss_flow（收口不得短路损耗）")
    void testProcess_ZeroWeight_NoTarget_StillSettlesLoss() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        // picked=50，只处理掉 30（全记入库），剩下 20 就是这块地的损耗
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "30.000", 1));
        when(plantingRecordMapper.advanceHandleStatus(anyLong(), any(), any(), anyLong())).thenReturn(1);

        service.submitProcess(processBo(null, "0", 1));

        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        assertThat(updCap.getValue().getLossWeight()).isEqualByComparingTo("20.000");

        // 统一损耗台账同样要落一条（loss > 0 时双写，见 Step 6.1）
        ArgumentCaptor<org.dromara.djs.warehouse.loss.domain.LossFlow> lossCap =
            ArgumentCaptor.forClass(org.dromara.djs.warehouse.loss.domain.LossFlow.class);
        verify(lossFlowService, times(1)).record(lossCap.capture());
        assertThat(lossCap.getValue().getLossType()).isEqualTo("veg_handle_loss");
        assertThat(lossCap.getValue().getLossWeight()).isEqualByComparingTo("20.000");
    }

    @Test
    @DisplayName("row41③：重量 > 0 + 不选去向 → 抛「请选择去向」，不落库（有货必须说清楚去哪）")
    void testProcess_PositiveWeight_NoTarget_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "10.000", 1));

        assertThatThrownBy(() -> service.submitProcess(processBo(null, "10.000", 0)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("请选择去向");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
    }

    @Test
    @DisplayName("row41④：0 kg + 不选去向 + 处理完成=否 → 仍抛「必须打开地块是否处理完成」（row29 口径不放宽）")
    void testProcess_ZeroWeight_NoTarget_NoFinish_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "10.000", 1));

        assertThatThrownBy(() -> service.submitProcess(processBo(null, "0", 0)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("必须打开「地块是否处理完成」");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
    }

    @Test
    @DisplayName("row41⑤：去向传了非法值（0 kg 也一样）→ 抛「处理目标非法」，不落库")
    void testProcess_IllegalTarget_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "10.000", 1));

        assertThatThrownBy(() -> service.submitProcess(processBo(9, "0", 1)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("处理目标非法");
        assertThatThrownBy(() -> service.submitProcess(processBo(9, "5.000", 0)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("处理目标非法");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
    }

    // -------- 采摘明细只读列表（FIX-ADMIN-0721） --------

    @Test
    @DisplayName("采摘明细 happy：分页查询透传 mapper 固定行，query 原样下传")
    void testQueryPickDetailPage_HappyPath() {
        PickDetailVo row = new PickDetailVo();
        row.setPickDate(LocalDate.of(2026, 7, 21));
        row.setCropName("小白菜");
        row.setPlotCode("A-01");
        row.setPickWeight(new BigDecimal("12.345"));
        row.setTeamId(30001L);
        row.setTeamName("采摘一组");
        Page<PickDetailVo> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(recordMapper.selectPickDetailPage(any(), any(PickDetailQuery.class))).thenReturn(page);

        PickDetailQuery q = new PickDetailQuery();
        q.setCropName("白菜");
        q.setTeamId(30001L);
        TableDataInfo<PickDetailVo> result = service.queryPickDetailPage(q, new PageQuery(1, 10));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getCropName()).isEqualTo("小白菜");
        assertThat(result.getRows().get(0).getPlotCode()).isEqualTo("A-01");
        assertThat(result.getRows().get(0).getPickWeight()).isEqualByComparingTo("12.345");
        verify(recordMapper).selectPickDetailPage(any(), eq(q));
    }

}
