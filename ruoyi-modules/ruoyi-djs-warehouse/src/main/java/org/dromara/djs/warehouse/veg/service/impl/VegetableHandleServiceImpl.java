package org.dromara.djs.warehouse.veg.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.veg.domain.HandleRecord;
import org.dromara.djs.warehouse.veg.domain.PlantingRecord;
import org.dromara.djs.warehouse.veg.domain.VegetableHandle;
import org.dromara.djs.warehouse.veg.domain.bo.HandleRecordSubmitBo;
import org.dromara.djs.warehouse.veg.domain.query.VegHandleQuery;
import org.dromara.djs.warehouse.veg.domain.vo.HandleRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.PendingPlantingRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegetableHandleVo;
import org.dromara.djs.warehouse.veg.mapper.HandleRecordMapper;
import org.dromara.djs.warehouse.veg.mapper.PlantingRecordMapper;
import org.dromara.djs.warehouse.veg.mapper.VegetableHandleMapper;
import org.dromara.djs.warehouse.veg.service.IVegetableHandleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 毛菜处理 Service 实现（WMS-VEG-001）。
 *
 * <h3>事务一致性</h3>
 * <p>{@link #submitHandleRecord} 同事务依次：</p>
 * <ol>
 *   <li>校验 planting_record 存在 + handle_status ∈ {pending, processing}</li>
 *   <li>校验 record_type=2 时 handle_target 必填；handle_target=1 时 location_id 必填且 location 存在</li>
 *   <li>找到 / 创建 vegetable_handle 汇总行（按 planting_record_id 唯一）</li>
 *   <li>INSERT handle_record</li>
 *   <li>聚合 UPDATE vegetable_handle 各重量字段</li>
 *   <li>若 handle_target=1 → INSERT stock_flow（flow_type=veg_stock_in, inout_type=IN）</li>
 *   <li>同步 planting_record.handle_status pending → processing；若 bo.isFinish=1 → 推 done</li>
 * </ol>
 *
 * <h3>并发安全</h3>
 * <p>同一 planting_record 并发写入：MySQL InnoDB REPEATABLE_READ + 事务内 SELECT + 后续 UPDATE 同行
 * 通过行锁串行化。UNIQUE 维度由业务上"一个 planting_record 对应一个 handle 汇总"约束，
 * 由 {@link VegetableHandleMapper#selectByPlantingRecordId} + 事务隔离保证。</p>
 *
 * <h3>损耗计算</h3>
 * <p>{@code loss = picked - handled - feed}（doc/11 §2.12 R15 业务规则）。负值取 0 并 WARN log。</p>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Slf4j
@Service
public class VegetableHandleServiceImpl
    extends DjsBaseServiceImpl<VegetableHandleMapper, VegetableHandle>
    implements IVegetableHandleService {

    /**
     * djs_veg_handle_status 取值。
     */
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_DONE = "done";

    /**
     * djs_record_type 取值。
     */
    private static final int RECORD_TYPE_PICK = 1;
    private static final int RECORD_TYPE_HANDLE = 2;

    /**
     * djs_handle_target 取值。
     */
    private static final int HANDLE_TARGET_STOCK_IN = 1;
    private static final int HANDLE_TARGET_PLATFORM = 2;
    private static final int HANDLE_TARGET_FEED = 3;

    /**
     * stock_flow.flow_type 蔬菜入库。
     */
    private static final String FLOW_TYPE_VEG_STOCK_IN = "veg_stock_in";

    /**
     * stock_flow.inout_type CHAR(3) IN=入库。
     */
    private static final String INOUT_IN = "IN";

    private final HandleRecordMapper handleRecordMapper;
    private final PlantingRecordMapper plantingRecordMapper;
    private final StockFlowMapper stockFlowMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;

    public VegetableHandleServiceImpl(VegetableHandleMapper baseMapper,
                                      HandleRecordMapper handleRecordMapper,
                                      PlantingRecordMapper plantingRecordMapper,
                                      StockFlowMapper stockFlowMapper,
                                      LocationInfoMapper locationInfoMapper,
                                      IBizCodeGenerator bizCodeGenerator) {
        super(baseMapper);
        this.handleRecordMapper = handleRecordMapper;
        this.plantingRecordMapper = plantingRecordMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitHandleRecord(HandleRecordSubmitBo bo) {
        Long userId = LoginHelper.getUserId();
        Date now = new Date();

        // Step 1：校验 planting_record 存在 + handle_status 合法
        PlantingRecord planting = plantingRecordMapper.selectById(bo.getPlantingRecordId());
        if (planting == null) {
            throw new ServiceException("种植记录不存在：" + bo.getPlantingRecordId());
        }
        if (STATUS_DONE.equals(planting.getHandleStatus())) {
            throw new ServiceException("该种植记录已处理完成，不能再录入");
        }

        // Step 2：参数校验（record_type=2 必须有 handle_target；target=1 必须有 location）
        Integer recordType = bo.getRecordType();
        if (recordType == null || (recordType != RECORD_TYPE_PICK && recordType != RECORD_TYPE_HANDLE)) {
            throw new ServiceException("记录类型非法（必须 1 采收 / 2 处理）");
        }
        Integer handleTarget = bo.getHandleTarget();
        if (recordType == RECORD_TYPE_HANDLE) {
            if (handleTarget == null) {
                throw new ServiceException("处理录入必须指定处理目标（1=入库 / 2=月台 / 3=饲料）");
            }
            if (handleTarget != HANDLE_TARGET_STOCK_IN
                && handleTarget != HANDLE_TARGET_PLATFORM
                && handleTarget != HANDLE_TARGET_FEED) {
                throw new ServiceException("处理目标非法：" + handleTarget);
            }
            if (handleTarget == HANDLE_TARGET_STOCK_IN && bo.getLocationId() == null) {
                throw new ServiceException("入库需指定库位");
            }
        }

        BigDecimal weight = bo.getRecordWeight();
        if (weight == null || weight.signum() <= 0) {
            throw new ServiceException("本次重量必须 > 0");
        }

        // Step 3：找到 / 创建 vegetable_handle 汇总
        VegetableHandle handle = baseMapper.selectByPlantingRecordId(planting.getId());
        boolean isNew = (handle == null);
        if (isNew) {
            if (recordType != RECORD_TYPE_PICK) {
                throw new ServiceException("首次录入必须是采收记录（record_type=1）");
            }
            handle = new VegetableHandle();
            handle.setPlantingRecordId(planting.getId());
            handle.setPlotId(planting.getPlotId());
            handle.setCropId(planting.getCropId());
            handle.setPickStartTime(now);
            handle.setPickedWeight(BigDecimal.ZERO);
            handle.setHandledWeight(BigDecimal.ZERO);
            handle.setFeedWeight(BigDecimal.ZERO);
            handle.setSendPlatformWeight(BigDecimal.ZERO);
            handle.setStockInWeight(BigDecimal.ZERO);
            handle.setLossWeight(BigDecimal.ZERO);
            handle.setIsWeighed(2);
            handle.setIsFinish(2);
            handle.setHandleStatus(STATUS_PROCESSING);
            baseMapper.insert(handle);
        }

        // Step 4：校验 location（如有 target=1）
        if (recordType == RECORD_TYPE_HANDLE && handleTarget == HANDLE_TARGET_STOCK_IN) {
            LocationInfo loc = locationInfoMapper.selectById(bo.getLocationId());
            if (loc == null) {
                throw new ServiceException("入库库位不存在：" + bo.getLocationId());
            }
        }

        // Step 5：INSERT handle_record
        HandleRecord record = new HandleRecord();
        record.setHandleId(handle.getId());
        record.setPlotId(planting.getPlotId());
        record.setCropId(planting.getCropId());
        record.setRecordType(recordType);
        record.setRecordWeight(weight);
        record.setHandleTarget(handleTarget);
        record.setLocationId(bo.getLocationId());
        record.setHandleUser(userId);
        record.setHandleTime(now);
        record.setProofOssIds(bo.getProofOssIds());
        record.setRemark(bo.getRemark());
        record.setIsFinish(bo.getIsFinish() == null ? 2 : bo.getIsFinish());
        handleRecordMapper.insert(record);

        // Step 6：聚合 UPDATE vegetable_handle
        VegetableHandle delta = new VegetableHandle();
        BigDecimal picked = nullSafe(handle.getPickedWeight());
        BigDecimal handled = nullSafe(handle.getHandledWeight());
        BigDecimal feed = nullSafe(handle.getFeedWeight());
        BigDecimal sendPlatform = nullSafe(handle.getSendPlatformWeight());
        BigDecimal stockIn = nullSafe(handle.getStockInWeight());

        if (recordType == RECORD_TYPE_PICK) {
            picked = picked.add(weight);
        } else {
            // RECORD_TYPE_HANDLE
            if (handleTarget == HANDLE_TARGET_STOCK_IN) {
                stockIn = stockIn.add(weight);
                handled = handled.add(weight);
            } else if (handleTarget == HANDLE_TARGET_PLATFORM) {
                sendPlatform = sendPlatform.add(weight);
                handled = handled.add(weight);
            } else {
                // FEED
                feed = feed.add(weight);
            }
        }

        BigDecimal loss = picked.subtract(handled).subtract(feed);
        if (loss.signum() < 0) {
            log.warn("loss_weight 负值（picked={} handled={} feed={}），归零处理 handleId={}",
                picked, handled, feed, handle.getId());
            loss = BigDecimal.ZERO;
        }
        loss = loss.setScale(3, RoundingMode.HALF_UP);

        delta.setId(handle.getId());
        delta.setPickedWeight(picked);
        delta.setHandledWeight(handled);
        delta.setFeedWeight(feed);
        delta.setSendPlatformWeight(sendPlatform);
        delta.setStockInWeight(stockIn);
        delta.setLossWeight(loss);

        if (STATUS_PENDING.equals(handle.getHandleStatus())) {
            delta.setHandleStatus(STATUS_PROCESSING);
        }
        if (bo.getIsFinish() != null && bo.getIsFinish() == 1) {
            delta.setIsFinish(1);
            delta.setHandleStatus(STATUS_DONE);
            delta.setPickEndTime(now);
        }
        baseMapper.updateById(delta);

        // Step 7：条件 INSERT stock_flow（仅入库）
        if (recordType == RECORD_TYPE_HANDLE && handleTarget == HANDLE_TARGET_STOCK_IN) {
            insertVegStockInFlow(handle, planting, bo.getLocationId(), weight, userId, now);
        }

        // Step 8：同步 planting_record.handle_status
        if (STATUS_PENDING.equals(planting.getHandleStatus())) {
            plantingRecordMapper.advanceHandleStatus(
                planting.getId(), STATUS_PENDING, STATUS_PROCESSING, userId);
        }
        if (bo.getIsFinish() != null && bo.getIsFinish() == 1) {
            plantingRecordMapper.advanceHandleStatus(
                planting.getId(), STATUS_PROCESSING, STATUS_DONE, userId);
        }

        return handle.getId();
    }

    private void insertVegStockInFlow(VegetableHandle handle, PlantingRecord planting,
                                      Long locationId, BigDecimal weight, Long userId, Date now) {
        StockFlow flow = new StockFlow();
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", INOUT_IN);
        flow.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx));
        flow.setFlowDate(now);
        // D9 closing _open-issues #18 决策 d：首选 planting_record.product_id（PLT-PICK-001/WMS-VEG-001 写入）；
        // 兜底 handle.product_id（极少数 handle 直建场景）；都 null 则 0 + warning log（V1 蔬菜业态产品仅 1 种"蔬菜"，
        // 0 兜底不影响 admin 列表显示，仅在 product 维度聚合查询会漏掉，由 PLT-PICK-001 落地后自然消除）。
        Long resolvedProductId = planting.getProductId();
        if (resolvedProductId == null) {
            resolvedProductId = handle.getProductId();
        }
        if (resolvedProductId == null) {
            log.warn("stock_flow.product_id 兜底为 0 — plantingRecordId={} cropId={} crop={}（PLT-PICK-001 落地后应自然消除）",
                planting.getId(), planting.getCropId(), planting.getCropName());
            resolvedProductId = 0L;
        }
        flow.setProductId(resolvedProductId);
        flow.setWarehouseId(locationId);
        flow.setInoutType(INOUT_IN);
        flow.setFlowType(FLOW_TYPE_VEG_STOCK_IN);
        flow.setChangeNum(weight);
        flow.setChangeQuantity(weight);
        flow.setPlotId(planting.getPlotId());
        flow.setOperatorId(userId);
        flow.setRemark("蔬菜处理入库 plantingRecordId=" + planting.getId() + " crop=" + planting.getCropName());
        stockFlowMapper.insert(flow);
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    @Override
    public List<PendingPlantingRecordVo> listPending() {
        return plantingRecordMapper.selectPendingList();
    }

    @Override
    public TableDataInfo<VegetableHandleVo> queryPageList(VegHandleQuery query, PageQuery pageQuery) {
        Page<VegetableHandleVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(query));
        fillPlantingNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<VegetableHandleVo> queryList(VegHandleQuery query) {
        List<VegetableHandleVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillPlantingNames(list);
        return list;
    }

    @Override
    public VegetableHandleVo queryById(Long id) {
        VegetableHandleVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillPlantingNames(List.of(vo));
        }
        return vo;
    }

    @Override
    public List<HandleRecordVo> listRecords(Long handleId) {
        return handleRecordMapper.selectVoList(
            new LambdaQueryWrapper<HandleRecord>()
                .eq(HandleRecord::getHandleId, handleId)
                .orderByAsc(HandleRecord::getHandleTime));
    }

    @Override
    public TableDataInfo<HandleRecordVo> myRecords(PageQuery pageQuery) {
        Long userId = LoginHelper.getUserId();
        Page<HandleRecordVo> page = handleRecordMapper.selectVoPage(pageQuery.build(),
            new LambdaQueryWrapper<HandleRecord>()
                .eq(HandleRecord::getHandleUser, userId)
                .orderByDesc(HandleRecord::getHandleTime));
        return TableDataInfo.build(page);
    }

    /**
     * 用 planting_record 冗余 plot_name / crop_name 回填 VO（避免跨模块依赖 plant）。
     */
    private void fillPlantingNames(List<VegetableHandleVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> plantingIds = records.stream()
            .map(VegetableHandleVo::getPlantingRecordId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (plantingIds.isEmpty()) {
            return;
        }
        List<PlantingRecord> plantings = plantingRecordMapper.selectList(
            new LambdaQueryWrapper<PlantingRecord>().in(PlantingRecord::getId, plantingIds));
        Map<Long, PlantingRecord> map = plantings.stream()
            .collect(Collectors.toMap(PlantingRecord::getId, p -> p, (a, b) -> a));
        for (VegetableHandleVo vo : records) {
            if (vo.getPlantingRecordId() != null) {
                PlantingRecord p = map.get(vo.getPlantingRecordId());
                if (p != null) {
                    vo.setPlotName(p.getPlotName());
                    vo.setCropName(p.getCropName());
                }
            }
        }
    }

    private LambdaQueryWrapper<VegetableHandle> buildQueryWrapper(VegHandleQuery query) {
        LambdaQueryWrapper<VegetableHandle> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(VegetableHandle::getId);
        }
        wrapper.eq(query.getPlotId() != null, VegetableHandle::getPlotId, query.getPlotId())
            .eq(query.getCropId() != null, VegetableHandle::getCropId, query.getCropId())
            .eq(query.getPlantingRecordId() != null, VegetableHandle::getPlantingRecordId, query.getPlantingRecordId())
            .eq(query.getHandleStatus() != null && !query.getHandleStatus().isBlank(),
                VegetableHandle::getHandleStatus, query.getHandleStatus())
            .ge(query.getPickStartTimeFrom() != null, VegetableHandle::getPickStartTime, query.getPickStartTimeFrom())
            .le(query.getPickStartTimeTo() != null, VegetableHandle::getPickStartTime, query.getPickStartTimeTo())
            .orderByDesc(VegetableHandle::getId);
        return wrapper;
    }

}
