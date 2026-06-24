package org.dromara.djs.warehouse.check.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.check.domain.StockCheckRecord;
import org.dromara.djs.warehouse.check.domain.bo.StockCheckCreateBo;
import org.dromara.djs.warehouse.check.domain.bo.StockCheckLineBo;
import org.dromara.djs.warehouse.check.domain.query.StockCheckQuery;
import org.dromara.djs.warehouse.check.domain.vo.StockCheckHeaderVo;
import org.dromara.djs.warehouse.check.domain.vo.StockCheckRecordVo;
import org.dromara.djs.warehouse.check.mapper.StockCheckRecordMapper;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
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
 * 库存盘点 Service 实现（WMS-STOCK-001）。
 *
 * <h3>盘点单数据模型（单表 header + line）</h3>
 * <p>同一盘点单的 header 行（{@code isHeader=1}）+ 各实盘 line 行（{@code isHeader=0}）共用 {@code checkId}。
 * header 承载 {@code checkStatus} + 库位锁；line 承载每个产品的系统量 / 实盘量 / 差异。</p>
 *
 * <h3>库位级业务锁</h3>
 * <p>{@link #createCheck} 把 header 置 {@code in_progress} 即锁住 {@code locationId}；
 * stock_flow 写入入口（{@code MatFlowServiceImpl} / {@code PigBurnRecordServiceImpl}）调
 * {@link #assertLocationUnlocked} 后端双保险拒绝出入库。{@link #completeCheck} / {@link #cancelCheck}
 * 置 {@code completed} 解锁。</p>
 *
 * <h3>完成盘点跨表事务（completeCheck 核心风险）</h3>
 * <p>单 {@code @Transactional} 跨 N 步：对每条 line 按 {@code diffStock} 正负写流水
 * （盘盈 {@code check_in/IN} / 盘亏 {@code check_out/OT}）+ 回写 location_stock 至实盘量
 * + 刷新 latest_check_time；最后 header status='completed' 解锁。任一异常整体回滚。</p>
 *
 * @author djs
 * @since WMS-STOCK-001
 */
@Slf4j
@Service
public class StockCheckServiceImpl
    extends DjsBaseServiceImpl<StockCheckRecordMapper, StockCheckRecord>
    implements IStockCheckService {

    /**
     * 盘点单状态字典 {@code djs_check_status} 值。
     */
    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_IN_PROGRESS = "in_progress";
    private static final String STATUS_DONE = "completed";

    /**
     * 出入库方向（DDL CHAR(3)）。
     */
    private static final String INOUT_IN = "IN";
    private static final String INOUT_OUT = "OT";

    /**
     * 盘点差异流水类型（{@code djs_flow_type}：盘盈入库 / 盘亏出库，已由 WMS-MAT-001 seed）。
     */
    private static final String FLOW_CHECK_IN = "check_in";
    private static final String FLOW_CHECK_OUT = "check_out";
    /** 盘点异常出库（FIX-WMS-FLOWDICT-001，盘亏且 check_result=异常(2)）。 */
    private static final String FLOW_CHECK_ABNORMAL_OUT = "check_abnormal_out";
    /** 出库去向：盘点计损（FIX-WMS-FLOWDICT-001，盘点计损 / 异常出库回填）。 */
    private static final String STOCK_OUT_DEST_CHECK_LOSS = "check_loss";

    /**
     * 盘点结果字典 {@code djs_check_result}：1=正常 / 2=异常 / 3=计损。
     */
    private static final int RESULT_NORMAL = 1;
    private static final int RESULT_ABNORMAL = 2;

    private final LocationStockMapper locationStockMapper;
    private final StockFlowMapper stockFlowMapper;
    private final LocationInfoMapper locationInfoMapper;
    private final ProductInfoMapper productInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final ILossFlowService lossFlowService;

    public StockCheckServiceImpl(StockCheckRecordMapper baseMapper,
                                 LocationStockMapper locationStockMapper,
                                 StockFlowMapper stockFlowMapper,
                                 LocationInfoMapper locationInfoMapper,
                                 ProductInfoMapper productInfoMapper,
                                 IBizCodeGenerator bizCodeGenerator,
                                 ILossFlowService lossFlowService) {
        super(baseMapper);
        this.locationStockMapper = locationStockMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.locationInfoMapper = locationInfoMapper;
        this.productInfoMapper = productInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.lossFlowService = lossFlowService;
    }

    // ============================ admin 盘点单管理 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCheck(StockCheckCreateBo bo) {
        // 1. 库位存在性校验
        LocationInfo location = locationInfoMapper.selectById(bo.getLocationId());
        if (location == null) {
            throw new ServiceException("库位不存在或已删除：" + bo.getLocationId());
        }
        // 2. 同库位互斥：已有进行中盘点单 → 拒绝（一库位同时只允许一张）
        if (baseMapper.countActiveByLocation(bo.getLocationId()) > 0) {
            throw new ServiceException("该库位已有进行中的盘点单，请先完成或取消后再新建");
        }
        // 3. INSERT header（in_progress 锁库位）
        StockCheckRecord header = new StockCheckRecord();
        header.setCheckId(generateCheckId());
        header.setLocationId(bo.getLocationId());
        header.setProductId(0L);
        header.setProductName("");
        header.setProductUnit("");
        header.setSysStock(BigDecimal.ZERO);
        header.setCheckStock(BigDecimal.ZERO);
        header.setDiffStock(BigDecimal.ZERO);
        header.setCheckResultType(RESULT_NORMAL);
        header.setCheckDate(bo.getCheckDate());
        header.setCheckStatus(STATUS_IN_PROGRESS);
        header.setIsHeader(1);
        baseMapper.insert(header);
        return header.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeCheck(Long id) {
        StockCheckRecord header = requireHeader(id);
        if (!STATUS_IN_PROGRESS.equals(header.getCheckStatus())) {
            throw new ServiceException("盘点单非进行中状态，无法完成（当前=" + header.getCheckStatus() + "）");
        }
        Long userId = LoginHelper.getUserId();

        // 1. 取本单所有 line
        List<StockCheckRecord> lines = baseMapper.selectList(
            new LambdaQueryWrapper<StockCheckRecord>()
                .eq(StockCheckRecord::getCheckId, header.getCheckId())
                .eq(StockCheckRecord::getIsHeader, 0));

        // 2. 逐 line 写差异流水 + 回写库存
        for (StockCheckRecord line : lines) {
            BigDecimal diff = line.getDiffStock() == null ? BigDecimal.ZERO : line.getDiffStock();
            // 2a. 差异 != 0 → 写流水（盘盈 check_in / 盘亏 check_out）
            if (diff.compareTo(BigDecimal.ZERO) != 0) {
                writeDiffFlow(header, line, diff, userId);
            }
            // 2b. 回写 location_stock 至实盘绝对值 + 刷新 latest_check_time + check_result
            int affected = locationStockMapper.setStockAfterCheck(
                line.getLocationId(), line.getProductId(),
                line.getCheckStock(), line.getCheckResultType(), userId);
            if (affected == 0) {
                // location_stock 无对应行（盘点的产品此前从未有库存记录）→ 兜底 INSERT 一条新库存行
                insertStockRow(line, userId);
            }
        }

        // 3. header status='completed' 解锁
        header.setCheckStatus(STATUS_DONE);
        baseMapper.updateById(header);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelCheck(Long id) {
        StockCheckRecord header = requireHeader(id);
        if (STATUS_DONE.equals(header.getCheckStatus())) {
            throw new ServiceException("盘点单已完成，无法取消");
        }
        // 取消 = 解锁不回写：header 直接置 completed（解除库位锁），line 留痕不写流水
        header.setCheckStatus(STATUS_DONE);
        baseMapper.updateById(header);
    }

    @Override
    public TableDataInfo<StockCheckHeaderVo> queryHeaderPage(StockCheckQuery query, PageQuery pageQuery) {
        // header 维度分页（只查 is_header=1）—— mp overview「进行中盘点单」走此路（check_record 模型，保留）
        LambdaQueryWrapper<StockCheckRecord> w = new LambdaQueryWrapper<StockCheckRecord>()
            .eq(StockCheckRecord::getIsHeader, 1);
        if (query != null) {
            w.eq(query.getCheckId() != null && !query.getCheckId().isBlank(),
                    StockCheckRecord::getCheckId, query.getCheckId())
                .eq(query.getLocationId() != null, StockCheckRecord::getLocationId, query.getLocationId())
                .eq(query.getCheckStatus() != null && !query.getCheckStatus().isBlank(),
                    StockCheckRecord::getCheckStatus, query.getCheckStatus())
                .ge(query.getCheckDateFrom() != null, StockCheckRecord::getCheckDate, query.getCheckDateFrom())
                .le(query.getCheckDateTo() != null, StockCheckRecord::getCheckDate, query.getCheckDateTo());
            if (query.getCheckByName() != null && !query.getCheckByName().isBlank()) {
                List<Long> userIds = baseMapper.selectUserIdsByNickName(query.getCheckByName().trim());
                if (userIds.isEmpty()) {
                    Page<StockCheckHeaderVo> empty = pageQuery.build();
                    empty.setTotal(0);
                    return TableDataInfo.build(empty);
                }
                w.in(StockCheckRecord::getCreateBy, userIds);
            }
        }
        w.orderByDesc(StockCheckRecord::getCheckDate).orderByDesc(StockCheckRecord::getId);

        Page<StockCheckRecord> page = baseMapper.selectPage(pageQuery.build(), w);
        List<StockCheckHeaderVo> vos = page.getRecords().stream().map(this::toHeaderVo).toList();
        fillHeaderAggregates(vos);
        fillHeaderLocationNames(vos);

        Page<StockCheckHeaderVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(vos);
        return TableDataInfo.build(voPage);
    }

    @Override
    public TableDataInfo<StockCheckHeaderVo> queryFlowCheckRecordPage(StockCheckQuery query, PageQuery pageQuery) {
        // admin 盘点记录页只读：统一从 stock_flow 盘点流水聚合（覆盖 mp 工人自助盘点 + admin 完成盘点
        // 两路来源，修「mp 提交的盘点在 admin 列表查不到」）。盘点单 = 日期 + 库位 + 盘点人 聚合行。
        Long locationId = query != null ? query.getLocationId() : null;
        String fromStr = query != null ? formatDateTime(query.getCheckDateFrom()) : null;
        String toStr = query != null ? formatDateTime(query.getCheckDateTo()) : null;

        // 盘点人筛选：姓名 → user_id 反查（命中为空时返回空集，避免漏过滤）
        List<Long> operatorIds = null;
        if (query != null && query.getCheckByName() != null && !query.getCheckByName().isBlank()) {
            operatorIds = baseMapper.selectUserIdsByNickName(query.getCheckByName().trim());
            if (operatorIds.isEmpty()) {
                Page<StockCheckHeaderVo> empty = pageQuery.build();
                empty.setTotal(0);
                return TableDataInfo.build(empty);
            }
        }

        IPage<StockCheckHeaderVo> page = baseMapper.selectFlowCheckHeaderPage(
            pageQuery.build(), locationId, fromStr, toStr, operatorIds);
        List<StockCheckHeaderVo> vos = page.getRecords();
        // 聚合行无 check_record 单状态，统一置 completed（只读、已落账）
        vos.forEach(v -> v.setCheckStatus(STATUS_DONE));
        fillHeaderLocationNames(vos);
        return TableDataInfo.build(page);
    }

    @Override
    public List<StockCheckRecordVo> queryLineList(StockCheckQuery query) {
        // admin 盘点记录详情钻取：checkId = 合成业务码 yyyyMMdd-locationId-operatorId → 拆解后读该组盘点流水
        if (query == null || query.getCheckId() == null || query.getCheckId().isBlank()) {
            return List.of();
        }
        String[] parts = query.getCheckId().split("-");
        if (parts.length != 3) {
            return List.of();
        }
        Long lineLocationId;
        Long operatorId;
        try {
            lineLocationId = Long.valueOf(parts[1]);
            operatorId = Long.valueOf(parts[2]);
        } catch (NumberFormatException e) {
            return List.of();
        }
        List<StockCheckRecordVo> list = baseMapper.selectFlowCheckLines(parts[0], lineLocationId, operatorId);
        fillLineLocationNames(list);
        fillLineLossWeight(list);
        return list;
    }

    @Override
    public List<StockCheckHeaderVo> exportHeaderList(StockCheckQuery query) {
        Long locationId = query != null ? query.getLocationId() : null;
        String fromStr = query != null ? formatDateTime(query.getCheckDateFrom()) : null;
        String toStr = query != null ? formatDateTime(query.getCheckDateTo()) : null;

        List<Long> operatorIds = null;
        if (query != null && query.getCheckByName() != null && !query.getCheckByName().isBlank()) {
            operatorIds = baseMapper.selectUserIdsByNickName(query.getCheckByName().trim());
            if (operatorIds.isEmpty()) {
                return List.of();
            }
        }

        // 不分页导出：单页装下全部命中行
        IPage<StockCheckHeaderVo> page = baseMapper.selectFlowCheckHeaderPage(
            new Page<>(1, Integer.MAX_VALUE), locationId, fromStr, toStr, operatorIds);
        List<StockCheckHeaderVo> vos = page.getRecords();
        vos.forEach(v -> v.setCheckStatus(STATUS_DONE));
        fillHeaderLocationNames(vos);
        return vos;
    }

    // ============================ mp 实盘录入 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitLine(StockCheckLineBo bo) {
        // 1. 找 header（用 checkId）→ 校验 in_progress
        StockCheckRecord header = baseMapper.selectOne(
            new LambdaQueryWrapper<StockCheckRecord>()
                .eq(StockCheckRecord::getCheckId, bo.getCheckId())
                .eq(StockCheckRecord::getIsHeader, 1)
                .last("LIMIT 1"));
        if (header == null) {
            throw new ServiceException("盘点单不存在：" + bo.getCheckId());
        }
        if (!STATUS_IN_PROGRESS.equals(header.getCheckStatus())) {
            throw new ServiceException("盘点单非进行中状态，无法录入实盘（当前=" + header.getCheckStatus() + "）");
        }
        Long locationId = header.getLocationId();

        // 2. 产品 + 系统现量
        ProductInfo product = productInfoMapper.selectById(bo.getProductId());
        if (product == null) {
            throw new ServiceException("产品不存在或已删除：" + bo.getProductId());
        }
        BigDecimal sysStock = currentStock(locationId, bo.getProductId());
        BigDecimal diff = bo.getCheckStock().subtract(sysStock);
        Integer resultType = bo.getCheckResultType() != null
            ? bo.getCheckResultType()
            : (diff.compareTo(BigDecimal.ZERO) == 0 ? RESULT_NORMAL : RESULT_ABNORMAL);

        // 3. upsert：同 checkId + locationId + productId 已有 line → update；否则 insert
        StockCheckRecord existing = baseMapper.selectOne(
            new LambdaQueryWrapper<StockCheckRecord>()
                .eq(StockCheckRecord::getCheckId, bo.getCheckId())
                .eq(StockCheckRecord::getIsHeader, 0)
                .eq(StockCheckRecord::getLocationId, locationId)
                .eq(StockCheckRecord::getProductId, bo.getProductId())
                .last("LIMIT 1"));

        StockCheckRecord line = existing != null ? existing : new StockCheckRecord();
        line.setCheckId(bo.getCheckId());
        line.setLocationId(locationId);
        line.setProductId(bo.getProductId());
        line.setProductName(product.getProductName());
        line.setProductUnit(product.getProductUnit());
        line.setSysStock(sysStock);
        line.setCheckStock(bo.getCheckStock());
        line.setDiffStock(diff);
        line.setCheckResultType(resultType);
        line.setDiffReason(bo.getDiffReason());
        line.setCheckBy(LoginHelper.getUserId());
        line.setCheckDate(header.getCheckDate());
        line.setCheckStatus(STATUS_IN_PROGRESS);
        line.setIsHeader(0);
        if (existing != null) {
            baseMapper.updateById(line);
        } else {
            baseMapper.insert(line);
        }
        return line.getId();
    }

    @Override
    public void removeLine(Long id) {
        StockCheckRecord line = baseMapper.selectById(id);
        if (line == null || line.getIsHeader() != null && line.getIsHeader() == 1) {
            throw new ServiceException("盘点明细不存在或不可删除");
        }
        StockCheckRecord header = baseMapper.selectOne(
            new LambdaQueryWrapper<StockCheckRecord>()
                .eq(StockCheckRecord::getCheckId, line.getCheckId())
                .eq(StockCheckRecord::getIsHeader, 1)
                .last("LIMIT 1"));
        if (header != null && STATUS_DONE.equals(header.getCheckStatus())) {
            throw new ServiceException("盘点单已完成，明细不可删除");
        }
        softDelete(List.of(id));
    }

    @Override
    public List<StockCheckRecordVo> queryLinesByCheckId(String checkId) {
        List<StockCheckRecordVo> list = baseMapper.selectVoList(
            new LambdaQueryWrapper<StockCheckRecord>()
                .eq(StockCheckRecord::getCheckId, checkId)
                .eq(StockCheckRecord::getIsHeader, 0)
                .orderByDesc(StockCheckRecord::getId));
        fillLineLocationNames(list);
        fillLineProductCodes(list);
        fillLineLossWeight(list);
        return list;
    }

    // ============================ 业务锁 ============================

    @Override
    public boolean isLocationLocked(Long locationId) {
        if (locationId == null) {
            return false;
        }
        return baseMapper.countActiveByLocation(locationId) > 0;
    }

    @Override
    public void assertLocationUnlocked(Long locationId) {
        if (isLocationLocked(locationId)) {
            throw new ServiceException("该库位正在盘点中，已锁定，暂不可出入库（locationId=" + locationId + "）");
        }
    }

    // ============================ 内部辅助 ============================

    /**
     * 生成盘点单业务码 {@code C{yyyyMMdd}{seq5}}（protected 便于单测 stub）。
     */
    protected String generateCheckId() {
        return bizCodeGenerator.generate(BizCodeType.CHECK_NO, Map.of());
    }

    /**
     * 查 location + product 的系统现量（无库存行返 0）。
     */
    protected BigDecimal currentStock(Long locationId, Long productId) {
        LocationStock stock = locationStockMapper.selectOne(
            new LambdaQueryWrapper<LocationStock>()
                .eq(LocationStock::getLocationId, locationId)
                .eq(LocationStock::getProductId, productId)
                .last("LIMIT 1"));
        if (stock == null || stock.getProductStock() == null) {
            return BigDecimal.ZERO;
        }
        return stock.getProductStock();
    }

    /**
     * 写盘点差异流水（盘盈 check_in/IN，盘亏 check_out/OT）。
     */
    private void writeDiffFlow(StockCheckRecord header, StockCheckRecord line, BigDecimal diff, Long userId) {
        boolean surplus = diff.compareTo(BigDecimal.ZERO) > 0;
        StockFlow flow = new StockFlow();
        Map<String, Object> ctx = new HashMap<>(2);
        ctx.put("ioCode", surplus ? INOUT_IN : INOUT_OUT);
        flow.setFlowNo(bizCodeGenerator.generate(BizCodeType.STOCK_FLOW_NO, ctx));
        flow.setFlowDate(new Date());
        flow.setProductId(line.getProductId());
        flow.setWarehouseId(line.getLocationId());
        flow.setInoutType(surplus ? INOUT_IN : INOUT_OUT);
        // 盘盈 → check_in；盘亏按结果类型拆：异常(2) → check_abnormal_out，正常计损 → check_out（FIX-WMS-FLOWDICT-001）
        if (surplus) {
            flow.setFlowType(FLOW_CHECK_IN);
        } else {
            boolean abnormal = line.getCheckResultType() != null && line.getCheckResultType() == RESULT_ABNORMAL;
            flow.setFlowType(abnormal ? FLOW_CHECK_ABNORMAL_OUT : FLOW_CHECK_OUT);
            // 盘点计损 / 异常出库去向固定为盘点计损（前端只读不可改）
            flow.setStockOutDest(STOCK_OUT_DEST_CHECK_LOSS);
        }
        // 盘盈正、盘亏负；changeQuantity 取绝对值
        flow.setChangeNum(diff);
        flow.setChangeQuantity(diff.abs());
        flow.setOperatorId(userId);
        flow.setRemark("盘点差异 check_id=" + header.getCheckId());
        stockFlowMapper.insert(flow);

        // 盘亏（diff<0）双写统一损耗表：损耗量取 |diff_stock|，来源指向本盘点明细行 + 刚写入的盘点流水
        if (!surplus) {
            lossFlowService.record(
                "check_loss",
                line.getProductId(),
                diff.abs(),
                line.getLocationId(),
                userId,
                "check",
                line.getId(),
                flow.getId());
        }
    }

    /**
     * location_stock 无对应行时兜底 INSERT 一条新库存行（盘点首次为该 product 建账）。
     */
    private void insertStockRow(StockCheckRecord line, Long userId) {
        LocationStock stock = new LocationStock();
        stock.setLocationId(line.getLocationId());
        stock.setProductId(line.getProductId());
        stock.setProductName(line.getProductName());
        stock.setProductStock(line.getCheckStock());
        stock.setProductUnit(line.getProductUnit());
        stock.setIsEnd(0);
        stock.setLatestCheckTime(new Date());
        stock.setCheckResult(line.getCheckResultType());
        stock.setOperatorId(userId);
        locationStockMapper.insert(stock);
    }

    /**
     * 取 header 行，不存在或非 header 抛异常。
     */
    private StockCheckRecord requireHeader(Long id) {
        StockCheckRecord header = baseMapper.selectById(id);
        if (header == null || header.getIsHeader() == null || header.getIsHeader() != 1) {
            throw new ServiceException("盘点单不存在：" + id);
        }
        return header;
    }

    /**
     * 日期 → {@code yyyy-MM-dd HH:mm:ss} 字符串（聚合 SQL 的 BETWEEN 边界用）；null 返 null。
     */
    private static String formatDateTime(Date date) {
        return date == null ? null : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private StockCheckHeaderVo toHeaderVo(StockCheckRecord r) {
        StockCheckHeaderVo vo = new StockCheckHeaderVo();
        vo.setId(r.getId());
        vo.setCheckId(r.getCheckId());
        vo.setLocationId(r.getLocationId());
        vo.setCheckDate(r.getCheckDate());
        vo.setCheckStatus(r.getCheckStatus());
        // 盘点人 = 盘点单头发起人（create_by）→ @Translation 取昵称
        vo.setCheckBy(r.getCreateBy());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }

    /**
     * 批量填 header 的 lineCount + abnormalCount + diffSum（一次性查全部相关 line 内存聚合，避免 N+1）。
     */
    private void fillHeaderAggregates(List<StockCheckHeaderVo> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        List<String> checkIds = headers.stream()
            .map(StockCheckHeaderVo::getCheckId).filter(Objects::nonNull).distinct().toList();
        if (checkIds.isEmpty()) {
            return;
        }
        List<StockCheckRecord> lines = baseMapper.selectList(
            new LambdaQueryWrapper<StockCheckRecord>()
                .in(StockCheckRecord::getCheckId, checkIds)
                .eq(StockCheckRecord::getIsHeader, 0));
        Map<String, List<StockCheckRecord>> byCheckId = lines.stream()
            .collect(Collectors.groupingBy(StockCheckRecord::getCheckId));
        for (StockCheckHeaderVo h : headers) {
            List<StockCheckRecord> ls = byCheckId.getOrDefault(h.getCheckId(), List.of());
            h.setLineCount(ls.size());
            long abnormal = ls.stream()
                .filter(l -> l.getCheckResultType() != null && l.getCheckResultType() == RESULT_ABNORMAL)
                .count();
            h.setAbnormalCount((int) abnormal);
            BigDecimal sum = ls.stream()
                .map(l -> l.getDiffStock() == null ? BigDecimal.ZERO : l.getDiffStock())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            h.setDiffSum(sum);
        }
    }

    private void fillHeaderLocationNames(List<StockCheckHeaderVo> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = locationNameMap(headers.stream()
            .map(StockCheckHeaderVo::getLocationId).filter(Objects::nonNull).distinct().toList());
        for (StockCheckHeaderVo h : headers) {
            if (h.getLocationId() != null) {
                h.setLocationName(nameMap.get(h.getLocationId()));
            }
        }
    }

    private void fillLineLocationNames(List<StockCheckRecordVo> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = locationNameMap(lines.stream()
            .map(StockCheckRecordVo::getLocationId).filter(Objects::nonNull).distinct().toList());
        for (StockCheckRecordVo l : lines) {
            if (l.getLocationId() != null) {
                l.setLocationName(nameMap.get(l.getLocationId()));
            }
        }
    }

    /**
     * 批量回填明细的产品代码（{@code productCode} = {@code ProductInfo.productId} 业务码）。
     */
    private void fillLineProductCodes(List<StockCheckRecordVo> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<Long> productIds = lines.stream()
            .map(StockCheckRecordVo::getProductId).filter(Objects::nonNull).distinct().toList();
        if (productIds.isEmpty()) {
            return;
        }
        Map<Long, String> codeMap = productInfoMapper.selectList(
                new LambdaQueryWrapper<ProductInfo>().in(ProductInfo::getId, productIds))
            .stream()
            .filter(p -> p.getProductId() != null)
            .collect(Collectors.toMap(ProductInfo::getId, ProductInfo::getProductId, (a, b) -> a));
        for (StockCheckRecordVo l : lines) {
            if (l.getProductId() != null) {
                l.setProductCode(codeMap.get(l.getProductId()));
            }
        }
    }

    /**
     * 派生明细的盘点计损量（{@code lossWeight}）：
     * 结果为正常（{@link #RESULT_NORMAL}）时按 {@code |sysStock - checkStock|} 计入计损，否则为 0
     * （异常差额走「差异」列，由前端按结果类型呈现）。
     */
    private void fillLineLossWeight(List<StockCheckRecordVo> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (StockCheckRecordVo l : lines) {
            if (l.getCheckResultType() != null && l.getCheckResultType() == RESULT_NORMAL
                && l.getSysStock() != null && l.getCheckStock() != null) {
                l.setLossWeight(l.getSysStock().subtract(l.getCheckStock()).abs());
            } else {
                l.setLossWeight(BigDecimal.ZERO);
            }
        }
    }

    private Map<Long, String> locationNameMap(List<Long> locationIds) {
        if (locationIds.isEmpty()) {
            return Map.of();
        }
        return locationInfoMapper.selectList(
                new LambdaQueryWrapper<LocationInfo>().in(LocationInfo::getId, locationIds))
            .stream()
            .collect(Collectors.toMap(LocationInfo::getId, LocationInfo::getLocationName, (a, b) -> a));
    }

}
