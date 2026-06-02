package org.dromara.djs.warehouse.burn.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.breed.core.service.IPigQueryService;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.burn.domain.PigBurnRecord;
import org.dromara.djs.warehouse.burn.domain.bo.PigBurnRecordBo;
import org.dromara.djs.warehouse.burn.domain.query.PigBurnRecordQuery;
import org.dromara.djs.warehouse.burn.domain.vo.PigBurnRecordVo;
import org.dromara.djs.warehouse.burn.mapper.PigBurnRecordMapper;
import org.dromara.djs.warehouse.burn.service.IPigBurnRecordService;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.trace.domain.TraceContentConst;
import org.dromara.djs.warehouse.trace.service.ITraceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 燎毛工序记录 Service 实现（WMS-PIG-001）。
 *
 * <h3>跨表事务一致性（本 ticket 核心风险）</h3>
 * <ul>
 *   <li>{@link #submitBurnRecord} 单 {@code @Transactional} 跨 4 步：
 *       校验 → INSERT burn_record → UPDATE location_stock（行锁扣减）→ INSERT stock_flow。
 *       任一 RuntimeException / ServiceException 触发整体回滚。</li>
 *   <li>幂等键：{@code burn_id} UNIQUE (tenant_id, burn_id, del_unique)。
 *       并发抢同一序号的极小概率场景 → SQLIntegrityConstraintViolationException → 事务回滚 → mp 端重试。</li>
 *   <li>库存扣减用 {@code UPDATE ... WHERE product_stock >= deductQty}（行锁 + 数量校验）；
 *       并发提交（同 ear_no 两次燎毛）只有一次 affectedRows > 0，另一次抛"库存不足"回滚。</li>
 * </ul>
 *
 * <h3>burn_id 生成（inline）</h3>
 * <p>格式 {@code BURN+yyMMdd+4 位序号}（日内重置）。SYS-INFRA-004 BizCodeType 没有 BURN 类型，
 * 暂 inline {@link #generateBurnId} 用 {@code SELECT MAX(burn_id) LIKE 'BURN{yyMMdd}%'} 推断下一序号。
 * 单 ticket 写入量小 + 事务串行 + UNIQUE 兜底足够；如后续需要更高并发，再走 BizCodeGenerator
 * 增加 {@code BURN_NO} 类型（标记 D9 follow-up）。</p>
 *
 * @author djs
 * @since WMS-PIG-001
 */
@Slf4j
@Service
public class PigBurnRecordServiceImpl
    extends DjsBaseServiceImpl<PigBurnRecordMapper, PigBurnRecord>
    implements IPigBurnRecordService {

    /**
     * 字典 djs_burn_status 值：已完成（已扣库存）。
     */
    private static final String STATUS_DONE = "done";

    /**
     * 出库子类型：屠宰燎毛（{@code t_warehouse_stock_flow.flow_type}）。
     */
    private static final String FLOW_TYPE_SLAUGHTER_BURN = "slaughter_burn";

    /**
     * 出入库方向：出库（{@code t_warehouse_stock_flow.inout_type} CHAR(3)）。
     */
    private static final String INOUT_OUT = "OT";

    /**
     * 已出栏状态码（{@code t_farm_pig_info.current_status}）。
     */
    private static final String PIG_STATUS_END = "END";

    /**
     * 白条产品业务码（D08-CLOSING 白条主数据 seed）；屠宰燎毛出库写 stock_flow 必引用此 product_id。
     * 字面量与 {@code V202606061060__D08-CLOSING-seed-white-bar-product.sql} 一致。
     */
    private static final String WHITE_BAR_PRODUCT_BIZ_CODE = "PROD-WHITE-BAR-01";

    private final LocationStockMapper locationStockMapper;
    private final StockFlowMapper stockFlowMapper;
    private final IPigQueryService pigQueryService;
    private final LocationInfoMapper locationInfoMapper;
    private final ProductInfoMapper productInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final IStockCheckService stockCheckService;
    private final ITraceService traceService;

    public PigBurnRecordServiceImpl(PigBurnRecordMapper baseMapper,
                                    LocationStockMapper locationStockMapper,
                                    StockFlowMapper stockFlowMapper,
                                    IPigQueryService pigQueryService,
                                    LocationInfoMapper locationInfoMapper,
                                    ProductInfoMapper productInfoMapper,
                                    IBizCodeGenerator bizCodeGenerator,
                                    IStockCheckService stockCheckService,
                                    ITraceService traceService) {
        super(baseMapper);
        this.locationStockMapper = locationStockMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.pigQueryService = pigQueryService;
        this.locationInfoMapper = locationInfoMapper;
        this.productInfoMapper = productInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.stockCheckService = stockCheckService;
        this.traceService = traceService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitBurnRecord(PigBurnRecordBo bo) {
        // ---------- Step 1：校验 earNo 出栏 ----------
        String pigStatus = pigQueryService.selectCurrentStatusByEarNo(bo.getEarNo());
        if (pigStatus == null) {
            throw new ServiceException("耳号未找到或猪只已删除：" + bo.getEarNo());
        }
        if (!PIG_STATUS_END.equals(pigStatus)) {
            throw new ServiceException("猪只未出栏（current_status=" + pigStatus + "），无法燎毛");
        }

        // ---------- Step 2：生成 burn_id + 计算 loss + INSERT 燎毛记录 ----------
        BigDecimal arriveWeight = bo.getArriveWeight();
        BigDecimal burnWeight = bo.getBurnWeight();
        BigDecimal lossWeight = arriveWeight.subtract(burnWeight);
        if (lossWeight.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("燎毛后重量不能大于到场重量");
        }

        PigBurnRecord record = toEntity(bo);
        if (record == null) {
            throw new ServiceException("燎毛记录入参转换失败");
        }
        record.setBurnId(generateBurnId());
        record.setLossWeight(lossWeight);
        record.setBurnStatus(STATUS_DONE);
        record.setOperatorId(LoginHelper.getUserId());
        baseMapper.insert(record);

        // ---------- Step 3：扣减白条库存（行锁 + 数量校验） ----------
        // 库位级业务锁（WMS-STOCK-001）：盘点进行中的库位禁出入库（后端双保险）
        stockCheckService.assertLocationUnlocked(bo.getLocationId());
        Long operatorId = LoginHelper.getUserId();
        int affected = locationStockMapper.deductByEarNo(
            bo.getLocationId(), bo.getEarNo(), burnWeight, operatorId);
        if (affected == 0) {
            // 关键：affectedRows==0 表示 (1) 库存不足 / (2) ear_no/location_id 不匹配 / (3) 已软删
            // 三种情况合并抛出，由 mp 端提示用户检查"是否已建白条记录 / 数量是否够"
            throw new ServiceException("白条库存不足或耳号/库位不匹配（确认 CROSS-FLOW-001 已建白条入库记录）");
        }

        // ---------- Step 4：写出库流水 ----------
        StockFlow flow = new StockFlow();
        Map<String, Object> flowCtx = new HashMap<>(2);
        flowCtx.put("ioCode", "OT");
        flow.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, flowCtx));
        flow.setFlowDate(bo.getBurnTime());
        // 白条产品主数据 seed（V202606061060 PROD-WHITE-BAR-01）；屠宰燎毛出库的 product 维度固定指向"白条·猪肉"。
        flow.setProductId(resolveWhiteBarProductId());
        flow.setWarehouseId(bo.getLocationId());
        flow.setInoutType(INOUT_OUT);
        flow.setFlowType(FLOW_TYPE_SLAUGHTER_BURN);
        flow.setChangeNum(burnWeight);
        flow.setChangeQuantity(burnWeight);
        flow.setEarNo(bo.getEarNo());
        flow.setOperatorId(operatorId);
        flow.setRemark("燎毛工序 burn_id=" + record.getBurnId());
        stockFlowMapper.insert(flow);

        // TRC-CORE-001：燎毛追溯事件（按耳号反查 trace_code；猪肉链当前无生成入口 → warn 跳过，不拖垮燎毛事务）
        traceService.recordEventByEarNo(bo.getEarNo(), TraceContentConst.SINGE);

        return record.getId();
    }

    @Override
    public TableDataInfo<PigBurnRecordVo> queryPageList(PigBurnRecordQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<PigBurnRecord> wrapper = buildQueryWrapper(query);
        Page<PigBurnRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillLocationNames(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<PigBurnRecordVo> queryList(PigBurnRecordQuery query) {
        List<PigBurnRecordVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillLocationNames(list);
        return list;
    }

    @Override
    public PigBurnRecordVo queryById(Long id) {
        PigBurnRecordVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillLocationNames(List.of(vo));
        }
        return vo;
    }

    /**
     * BO → Entity 转换钩子（MapStruct-Plus）。
     *
     * <p>protected 方便单测覆盖。</p>
     */
    protected PigBurnRecord toEntity(PigBurnRecordBo bo) {
        return MapstructUtils.convert(bo, PigBurnRecord.class);
    }

    /**
     * 生成 burn_id：{@code BURN+yyMMdd+4 位}。
     *
     * <p>D9 closing Group B 迁入 {@link IBizCodeGenerator}（BizCodeType.BURN_NO，
     * seed 在 V202606071600）—— Redisson 分布式锁 + 序号表 UNIQUE 双保护，
     * 取代原 SELECT MAX inline 实现。</p>
     *
     * <p>protected 方便单测 stub 固定返回值。</p>
     */
    protected String generateBurnId() {
        return bizCodeGenerator.generate(BizCodeType.BURN_NO, Map.of());
    }

    /**
     * 解析白条产品主数据 id（业务码 {@link #WHITE_BAR_PRODUCT_BIZ_CODE}）。
     *
     * <p>protected 方便单测 stub 固定返回值（避开真实 mapper 查询）。
     * seed 由 {@code V202606061060__D08-CLOSING-seed-white-bar-product.sql} 保证；
     * 查不到表示 DB 未灌 seed → 抛 {@link ServiceException} 直接打回。</p>
     */
    protected Long resolveWhiteBarProductId() {
        ProductInfo whiteBar = productInfoMapper.selectOne(
            new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getProductId, WHITE_BAR_PRODUCT_BIZ_CODE)
                .last("LIMIT 1"));
        if (whiteBar == null || whiteBar.getId() == null) {
            throw new ServiceException(
                "白条产品主数据缺失（product_id=" + WHITE_BAR_PRODUCT_BIZ_CODE
                    + "），请确认 V202606061060 seed 已执行");
        }
        return whiteBar.getId();
    }

    /**
     * 批量回填库位名（避免 N+1）。
     */
    private void fillLocationNames(List<PigBurnRecordVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> locationIds = records.stream()
            .map(PigBurnRecordVo::getLocationId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (locationIds.isEmpty()) {
            return;
        }
        List<LocationInfo> locations = locationInfoMapper.selectList(
            new LambdaQueryWrapper<LocationInfo>().in(LocationInfo::getId, locationIds));
        Map<Long, String> nameMap = locations.stream()
            .collect(Collectors.toMap(LocationInfo::getId, LocationInfo::getLocationName, (a, b) -> a));
        for (PigBurnRecordVo vo : records) {
            if (vo.getLocationId() != null) {
                vo.setLocationName(nameMap.get(vo.getLocationId()));
            }
        }
    }

    private LambdaQueryWrapper<PigBurnRecord> buildQueryWrapper(PigBurnRecordQuery query) {
        LambdaQueryWrapper<PigBurnRecord> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(PigBurnRecord::getId);
        }
        wrapper.eq(StringUtils.isNotBlank(query.getEarNo()), PigBurnRecord::getEarNo, query.getEarNo())
            .eq(StringUtils.isNotBlank(query.getBurnId()), PigBurnRecord::getBurnId, query.getBurnId())
            .eq(StringUtils.isNotBlank(query.getBurnStatus()), PigBurnRecord::getBurnStatus, query.getBurnStatus())
            .eq(query.getOperatorId() != null, PigBurnRecord::getOperatorId, query.getOperatorId())
            .ge(query.getBurnTimeFrom() != null, PigBurnRecord::getBurnTime, query.getBurnTimeFrom())
            .le(query.getBurnTimeTo() != null, PigBurnRecord::getBurnTime, query.getBurnTimeTo())
            .orderByDesc(PigBurnRecord::getId);
        return wrapper;
    }

}
