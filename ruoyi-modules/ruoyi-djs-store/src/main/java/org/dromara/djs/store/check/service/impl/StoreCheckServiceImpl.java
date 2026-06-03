package org.dromara.djs.store.check.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.mapper.DjsUserExtMapper;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.store.check.domain.StoreCheckRecord;
import org.dromara.djs.store.check.domain.bo.StoreCheckCreateBo;
import org.dromara.djs.store.check.domain.bo.StoreCheckLineBo;
import org.dromara.djs.store.check.domain.query.StoreCheckQuery;
import org.dromara.djs.store.check.domain.vo.StoreCheckHeaderVo;
import org.dromara.djs.store.check.domain.vo.StoreCheckRecordVo;
import org.dromara.djs.store.check.mapper.StoreCheckRecordMapper;
import org.dromara.djs.store.check.service.IStoreCheckService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 门店盘点 Service 实现（STR-STOCK-001）。
 *
 * <h3>盘点单数据模型（单表 header + line）</h3>
 * <p>同一盘点单的 header 行（{@code isHeader=1}）+ 各实盘 line 行（{@code isHeader=0}）共用 {@code checkId}。
 * header 承载 {@code checkStatus} + store 维度锁；line 承载每个产品的系统量 / 实盘量 / 差异。
 * 镜像 WMS-STOCK-001，去 location 加 store，admin only 无 mp。</p>
 *
 * <h3>store + product 维度业务锁</h3>
 * <p>{@link #createCheck} 把 header 置 {@code in_progress} 即锁住该 {@code storeId}；门店销售出库写入入口
 * （STR-OP-001 {@code StoreSaleRecordServiceImpl}）调 {@link #assertProductUnlocked} 后端双保险拒绝出库。
 * {@link #completeCheck} / {@link #cancelCheck} 置 {@code completed} 解锁。</p>
 *
 * <h3>完成盘点口径（与 WMS 不同）</h3>
 * <p>门店 V1 无独立库存表，{@link #completeCheck} 仅把 header status='completed' 解锁；差异已在
 * {@link #submitLine} 录入时算好落 line，不回写实物库存、不写库存流水。库存回写 hook 留 followup。</p>
 *
 * @author djs
 * @since STR-STOCK-001
 */
@Slf4j
@Service
public class StoreCheckServiceImpl
    extends DjsBaseServiceImpl<StoreCheckRecordMapper, StoreCheckRecord>
    implements IStoreCheckService {

    /**
     * 盘点单状态字典 {@code djs_check_status} 值。
     */
    private static final String STATUS_IN_PROGRESS = "in_progress";
    private static final String STATUS_COMPLETED = "completed";

    /**
     * 盘点结果字典 {@code djs_check_result}：1=正常 / 2=异常 / 3=计损。
     */
    private static final int RESULT_NORMAL = 1;
    private static final int RESULT_ABNORMAL = 2;

    /**
     * 默认租户（V1 单租户；无登录上下文时兜底）。
     */
    private static final String DEFAULT_TENANT = "1001";

    private final StoreMapper storeMapper;
    private final ProductInfoMapper productInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;
    private final DjsUserExtMapper djsUserExtMapper;

    public StoreCheckServiceImpl(StoreCheckRecordMapper baseMapper,
                                 StoreMapper storeMapper,
                                 ProductInfoMapper productInfoMapper,
                                 IBizCodeGenerator bizCodeGenerator,
                                 DjsUserExtMapper djsUserExtMapper) {
        super(baseMapper);
        this.storeMapper = storeMapper;
        this.productInfoMapper = productInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
        this.djsUserExtMapper = djsUserExtMapper;
    }

    // ============================ admin 盘点单管理 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCheck(StoreCheckCreateBo bo) {
        // 1. 门店存在性校验
        Store store = storeMapper.selectById(bo.getStoreId());
        if (store == null) {
            throw new ServiceException("门店不存在或已删除：" + bo.getStoreId());
        }
        // 2. 同门店互斥：已有进行中盘点单 → 拒绝（一门店同时只允许一张）
        if (baseMapper.countActive(bo.getStoreId(), null, currentTenantId()) > 0) {
            throw new ServiceException("该门店已有进行中的盘点单，请先完成或取消后再新建");
        }
        // 3. INSERT header（in_progress 锁门店）
        StoreCheckRecord header = new StoreCheckRecord();
        header.setCheckId(generateCheckId());
        header.setStoreId(bo.getStoreId());
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
        header.setRemark(bo.getRemark());
        baseMapper.insert(header);
        return header.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeCheck(Long id) {
        StoreCheckRecord header = requireHeader(id);
        if (!STATUS_IN_PROGRESS.equals(header.getCheckStatus())) {
            throw new ServiceException("盘点单非进行中状态，无法完成（当前=" + header.getCheckStatus() + "）");
        }
        // 门店 V1 无独立库存表：差异已在录入时算好落 line，完成仅置 completed 解锁，不回写实物库存、不写流水
        header.setCheckStatus(STATUS_COMPLETED);
        baseMapper.updateById(header);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelCheck(Long id) {
        StoreCheckRecord header = requireHeader(id);
        if (STATUS_COMPLETED.equals(header.getCheckStatus())) {
            throw new ServiceException("盘点单已完成，无法取消");
        }
        // 取消 = 解锁不回写：header 直接置 completed（解除门店锁），line 留痕
        header.setCheckStatus(STATUS_COMPLETED);
        baseMapper.updateById(header);
    }

    @Override
    public TableDataInfo<StoreCheckHeaderVo> queryHeaderPage(StoreCheckQuery query, PageQuery pageQuery) {
        // header 维度分页（只查 is_header=1）
        LambdaQueryWrapper<StoreCheckRecord> w = new LambdaQueryWrapper<StoreCheckRecord>()
            .eq(StoreCheckRecord::getIsHeader, 1);
        if (query != null) {
            w.eq(query.getCheckId() != null && !query.getCheckId().isBlank(),
                    StoreCheckRecord::getCheckId, query.getCheckId())
                .eq(query.getStoreId() != null, StoreCheckRecord::getStoreId, query.getStoreId())
                .eq(query.getCheckStatus() != null && !query.getCheckStatus().isBlank(),
                    StoreCheckRecord::getCheckStatus, query.getCheckStatus())
                .ge(query.getCheckDateFrom() != null, StoreCheckRecord::getCheckDate, query.getCheckDateFrom())
                .le(query.getCheckDateTo() != null, StoreCheckRecord::getCheckDate, query.getCheckDateTo());
        }
        w.orderByDesc(StoreCheckRecord::getCheckDate).orderByDesc(StoreCheckRecord::getId);

        Page<StoreCheckRecord> page = baseMapper.selectPage(pageQuery.build(), w);
        List<StoreCheckHeaderVo> vos = page.getRecords().stream().map(this::toHeaderVo).toList();
        fillHeaderAggregates(vos);
        fillHeaderStoreNames(vos);

        Page<StoreCheckHeaderVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(vos);
        return TableDataInfo.build(voPage);
    }

    @Override
    public List<StoreCheckRecordVo> queryLineList(StoreCheckQuery query) {
        LambdaQueryWrapper<StoreCheckRecord> w = new LambdaQueryWrapper<StoreCheckRecord>()
            .eq(StoreCheckRecord::getIsHeader, 0);
        if (query != null) {
            w.eq(query.getCheckId() != null && !query.getCheckId().isBlank(),
                    StoreCheckRecord::getCheckId, query.getCheckId())
                .eq(query.getStoreId() != null, StoreCheckRecord::getStoreId, query.getStoreId());
        }
        w.orderByDesc(StoreCheckRecord::getId);
        List<StoreCheckRecordVo> list = baseMapper.selectVoList(w);
        fillLineStoreNames(list);
        fillLineCheckByNames(list);
        return list;
    }

    @Override
    public List<StoreCheckRecordVo> queryLinesByCheckId(String checkId) {
        List<StoreCheckRecordVo> list = baseMapper.selectVoList(
            new LambdaQueryWrapper<StoreCheckRecord>()
                .eq(StoreCheckRecord::getCheckId, checkId)
                .eq(StoreCheckRecord::getIsHeader, 0)
                .orderByDesc(StoreCheckRecord::getId));
        fillLineStoreNames(list);
        fillLineCheckByNames(list);
        return list;
    }

    // ============================ 实盘录入（admin 详情页）============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitLine(StoreCheckLineBo bo) {
        // 1. 找 header（用 checkId）→ 校验 in_progress
        StoreCheckRecord header = baseMapper.selectOne(
            new LambdaQueryWrapper<StoreCheckRecord>()
                .eq(StoreCheckRecord::getCheckId, bo.getCheckId())
                .eq(StoreCheckRecord::getIsHeader, 1)
                .last("LIMIT 1"));
        if (header == null) {
            throw new ServiceException("盘点单不存在：" + bo.getCheckId());
        }
        if (!STATUS_IN_PROGRESS.equals(header.getCheckStatus())) {
            throw new ServiceException("盘点单非进行中状态，无法录入实盘（当前=" + header.getCheckStatus() + "）");
        }
        Long storeId = header.getStoreId();

        // 2. 产品 + 系统现量（门店 V1 无独立库存表 → sysStock 默认 0，盘点即首次建账）
        ProductInfo product = productInfoMapper.selectById(bo.getProductId());
        if (product == null) {
            throw new ServiceException("产品不存在或已删除：" + bo.getProductId());
        }
        BigDecimal sysStock = currentStock(storeId, bo.getProductId());
        BigDecimal diff = bo.getCheckStock().subtract(sysStock);
        Integer resultType = bo.getCheckResultType() != null
            ? bo.getCheckResultType()
            : (diff.compareTo(BigDecimal.ZERO) == 0 ? RESULT_NORMAL : RESULT_ABNORMAL);

        // 3. upsert：同 checkId + storeId + productId 已有 line → update；否则 insert
        StoreCheckRecord existing = baseMapper.selectOne(
            new LambdaQueryWrapper<StoreCheckRecord>()
                .eq(StoreCheckRecord::getCheckId, bo.getCheckId())
                .eq(StoreCheckRecord::getIsHeader, 0)
                .eq(StoreCheckRecord::getStoreId, storeId)
                .eq(StoreCheckRecord::getProductId, bo.getProductId())
                .last("LIMIT 1"));

        StoreCheckRecord line = existing != null ? existing : new StoreCheckRecord();
        line.setCheckId(bo.getCheckId());
        line.setStoreId(storeId);
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
        StoreCheckRecord line = baseMapper.selectById(id);
        if (line == null || (line.getIsHeader() != null && line.getIsHeader() == 1)) {
            throw new ServiceException("盘点明细不存在或不可删除");
        }
        StoreCheckRecord header = baseMapper.selectOne(
            new LambdaQueryWrapper<StoreCheckRecord>()
                .eq(StoreCheckRecord::getCheckId, line.getCheckId())
                .eq(StoreCheckRecord::getIsHeader, 1)
                .last("LIMIT 1"));
        if (header != null && STATUS_COMPLETED.equals(header.getCheckStatus())) {
            throw new ServiceException("盘点单已完成，明细不可删除");
        }
        softDelete(List.of(id));
    }

    // ============================ 业务锁 ============================

    @Override
    public boolean isProductLocked(Long storeId, Long productId) {
        if (storeId == null) {
            return false;
        }
        return baseMapper.countActive(storeId, productId, currentTenantId()) > 0;
    }

    @Override
    public void assertProductUnlocked(Long storeId, Long productId) {
        if (isProductLocked(storeId, productId)) {
            throw new ServiceException("该门店正在盘点中，已锁定，暂不可出库（storeId=" + storeId + "）");
        }
    }

    // ============================ 内部辅助 ============================

    /**
     * 生成门店盘点单业务码 {@code SC{yyyyMMdd}{seq5}}（protected 便于单测 stub）。
     */
    protected String generateCheckId() {
        return bizCodeGenerator.generate(BizCodeType.STORE_CHECK_NO, Map.of());
    }

    /**
     * 查门店 + 产品的系统现量。门店 V1 无独立库存表 → 返 0（盘点即首次建账）。
     */
    protected BigDecimal currentStock(Long storeId, Long productId) {
        return BigDecimal.ZERO;
    }

    /**
     * 当前租户 ID（无登录上下文兜底默认租户）。
     */
    private String currentTenantId() {
        String t = TenantHelper.getTenantId();
        return (t == null || t.isBlank()) ? DEFAULT_TENANT : t;
    }

    /**
     * 取 header 行，不存在或非 header 抛异常。
     */
    private StoreCheckRecord requireHeader(Long id) {
        StoreCheckRecord header = baseMapper.selectById(id);
        if (header == null || header.getIsHeader() == null || header.getIsHeader() != 1) {
            throw new ServiceException("盘点单不存在：" + id);
        }
        return header;
    }

    private StoreCheckHeaderVo toHeaderVo(StoreCheckRecord r) {
        StoreCheckHeaderVo vo = new StoreCheckHeaderVo();
        vo.setId(r.getId());
        vo.setCheckId(r.getCheckId());
        vo.setStoreId(r.getStoreId());
        vo.setCheckDate(r.getCheckDate());
        vo.setCheckStatus(r.getCheckStatus());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }

    /**
     * 批量填 header 的 lineCount + diffSum（一次性查全部相关 line 内存聚合，避免 N+1）。
     */
    private void fillHeaderAggregates(List<StoreCheckHeaderVo> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        List<String> checkIds = headers.stream()
            .map(StoreCheckHeaderVo::getCheckId).filter(Objects::nonNull).distinct().toList();
        if (checkIds.isEmpty()) {
            return;
        }
        List<StoreCheckRecord> lines = baseMapper.selectList(
            new LambdaQueryWrapper<StoreCheckRecord>()
                .in(StoreCheckRecord::getCheckId, checkIds)
                .eq(StoreCheckRecord::getIsHeader, 0));
        Map<String, List<StoreCheckRecord>> byCheckId = lines.stream()
            .collect(Collectors.groupingBy(StoreCheckRecord::getCheckId));
        for (StoreCheckHeaderVo h : headers) {
            List<StoreCheckRecord> ls = byCheckId.getOrDefault(h.getCheckId(), List.of());
            h.setLineCount(ls.size());
            BigDecimal sum = ls.stream()
                .map(l -> l.getDiffStock() == null ? BigDecimal.ZERO : l.getDiffStock())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            h.setDiffSum(sum);
        }
    }

    private void fillHeaderStoreNames(List<StoreCheckHeaderVo> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = storeNameMap(headers.stream()
            .map(StoreCheckHeaderVo::getStoreId).filter(Objects::nonNull).distinct().toList());
        for (StoreCheckHeaderVo h : headers) {
            if (h.getStoreId() != null) {
                h.setStoreName(nameMap.get(h.getStoreId()));
            }
        }
    }

    private void fillLineStoreNames(List<StoreCheckRecordVo> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = storeNameMap(lines.stream()
            .map(StoreCheckRecordVo::getStoreId).filter(Objects::nonNull).distinct().toList());
        for (StoreCheckRecordVo l : lines) {
            if (l.getStoreId() != null) {
                l.setStoreName(nameMap.get(l.getStoreId()));
            }
        }
    }

    /**
     * 回填盘点人姓名（{@code checkBy} → {@code checkByName}）。
     *
     * <p>VO 上的 {@code @Translation} 仅在 Jackson 序列化期生效（admin 列表 / lines JSON 接口靠它）；
     * Excel 导出走 FastExcel 不经 Jackson，{@code checkByName} 恒 null。故 service 层批量查
     * {@code sys_user} 的 nick_name 显式回填，导出路径才能显示人名。守卫 {@code getCheckByName() == null}
     * 不覆盖已被 @Translation 填好的值。</p>
     */
    private void fillLineCheckByNames(List<StoreCheckRecordVo> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<Long> ids = lines.stream()
            .map(StoreCheckRecordVo::getCheckBy).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = djsUserExtMapper.selectNickNamesByIds(ids).stream()
            .collect(Collectors.toMap(
                r -> ((Number) r.get("userId")).longValue(),
                r -> String.valueOf(r.get("nickName")),
                (a, b) -> a));
        for (StoreCheckRecordVo l : lines) {
            if (l.getCheckBy() != null && l.getCheckByName() == null) {
                l.setCheckByName(nameMap.get(l.getCheckBy()));
            }
        }
    }

    private Map<Long, String> storeNameMap(List<Long> storeIds) {
        if (storeIds.isEmpty()) {
            return Map.of();
        }
        return storeMapper.selectList(
                new LambdaQueryWrapper<Store>().in(Store::getId, storeIds))
            .stream()
            .collect(Collectors.toMap(Store::getId, Store::getStoreName, (a, b) -> a));
    }

}
