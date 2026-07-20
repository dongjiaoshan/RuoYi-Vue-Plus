package org.dromara.djs.warehouse.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.service.IProductDisplayNameResolver;
import org.dromara.djs.warehouse.trace.domain.TraceCode;
import org.dromara.djs.warehouse.trace.domain.TraceCodeTypeConst;
import org.dromara.djs.warehouse.trace.domain.TraceContentConst;
import org.dromara.djs.warehouse.trace.domain.TraceEvent;
import org.dromara.djs.warehouse.trace.domain.query.TraceCodeQuery;
import org.dromara.djs.warehouse.trace.domain.vo.TraceCodeDetailVo;
import org.dromara.djs.warehouse.trace.domain.vo.TraceCodeListVo;
import org.dromara.djs.warehouse.trace.domain.vo.TraceEventVo;
import org.dromara.djs.warehouse.trace.mapper.TraceCodeMapper;
import org.dromara.djs.warehouse.trace.mapper.TraceEventMapper;
import org.dromara.djs.warehouse.trace.mapper.TraceFarmNameMapper;
import org.dromara.djs.warehouse.trace.service.ITraceCodeAdminService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 追溯码管理 admin Service 实现（TRC-ADMIN-001，admin only 无 mp）。
 *
 * <h3>追溯链不可篡改</h3>
 * <p>本实现纯只读：列表 / 详情 / 事件链 / 导出 / 批量取数。{@code extends DjsBaseServiceImpl}
 * 走基类范式（拿 baseMapper / 租户 helper），但<b>不调</b> {@code softDelete / deleteWithValidByIds}
 * （追溯码不删、事件不可篡改，doc/10 §F-TRC-01）。</p>
 *
 * <h3>JOIN 展示名（内存 fill 范式）</h3>
 * <p>主表 {@code t_warehouse_trace_code} 只存 FK（product_id / store_id / plot_id / farm_id），名字非冗余列，
 * 用 {@code selectVoPage / selectVoList} 拿主表行后批量查关联表内存填名（避免 N+1）。
 * {@code productName} 模糊筛选在内存 JOIN 后过滤（主表无 product_name 列）。
 * farm 名字走 {@link TraceFarmNameMapper}（sys_farm 无 djs 实体）。</p>
 *
 * @author djs
 * @since TRC-ADMIN-001
 */
@Slf4j
@Service
public class TraceCodeAdminServiceImpl
    extends DjsBaseServiceImpl<TraceCodeMapper, TraceCode>
    implements ITraceCodeAdminService {

    /**
     * 默认租户（V1 单租户；无登录上下文时兜底，farm 自定义 SQL 用）。
     */
    private static final String DEFAULT_TENANT = "1001";

    /**
     * 生产编号尾部连续数字（序号）匹配，如 {@code 260301003} → 末 4 位序号 / {@code 260312Z0001} → 0001。
     */
    private static final Pattern SERIAL_TAIL = Pattern.compile("(\\d+)$");

    private final TraceEventMapper traceEventMapper;
    private final ProductInfoMapper productInfoMapper;
    private final StoreMapper storeMapper;
    private final PlotInfoMapper plotInfoMapper;
    private final TraceFarmNameMapper traceFarmNameMapper;
    private final ProductProductionMapper productProductionMapper;
    private final IProductDisplayNameResolver displayNameResolver;

    public TraceCodeAdminServiceImpl(TraceCodeMapper baseMapper,
                                     TraceEventMapper traceEventMapper,
                                     ProductInfoMapper productInfoMapper,
                                     StoreMapper storeMapper,
                                     PlotInfoMapper plotInfoMapper,
                                     TraceFarmNameMapper traceFarmNameMapper,
                                     ProductProductionMapper productProductionMapper,
                                     IProductDisplayNameResolver displayNameResolver) {
        super(baseMapper);
        this.traceEventMapper = traceEventMapper;
        this.productInfoMapper = productInfoMapper;
        this.storeMapper = storeMapper;
        this.plotInfoMapper = plotInfoMapper;
        this.traceFarmNameMapper = traceFarmNameMapper;
        this.productProductionMapper = productProductionMapper;
        this.displayNameResolver = displayNameResolver;
    }

    // ============================ 列表 ============================

    @Override
    public TableDataInfo<TraceCodeListVo> queryPage(TraceCodeQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<TraceCode> w = buildWrapper(query);
        Page<TraceCode> page = baseMapper.selectPage(pageQuery.build(), w);
        List<TraceCodeListVo> vos = page.getRecords().stream().map(this::toListVo).toList();
        fillRelations(vos);
        fillOnsiteDerivedFields(vos);

        // productName 模糊筛选（主表无该列，JOIN 后内存过滤）
        List<TraceCodeListVo> filtered = filterByProductName(vos, query);

        Page<TraceCodeListVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        // 内存过滤会让本页行数小于 total，仅影响展示密度（V1 可接受）；total 仍为主表条件总数
        voPage.setRecords(filtered);
        return TableDataInfo.build(voPage);
    }

    // ============================ 详情 ============================

    @Override
    public TraceCodeDetailVo getDetail(Long id) {
        TraceCode code = baseMapper.selectById(id);
        if (code == null) {
            throw new ServiceException("追溯码不存在或已删除：" + id);
        }
        TraceCodeDetailVo detail = new TraceCodeDetailVo();
        copyCodeFields(code, detail);
        fillRelations(List.of(detail));
        detail.setEvents(getEvents(code.getProduceCode()));
        return detail;
    }

    @Override
    public List<TraceEventVo> getEvents(String produceCode) {
        if (produceCode == null || produceCode.isBlank()) {
            return Collections.emptyList();
        }
        return traceEventMapper.selectVoList(
            new LambdaQueryWrapper<TraceEvent>()
                .eq(TraceEvent::getProduceCode, produceCode)
                .orderByAsc(TraceEvent::getTraceTime)
                .orderByAsc(TraceEvent::getId));
    }

    // ============================ 导出 ============================

    @Override
    public List<TraceCodeListVo> export(TraceCodeQuery query) {
        List<TraceCode> list = baseMapper.selectList(buildWrapper(query));
        List<TraceCodeListVo> vos = list.stream().map(this::toListVo).toList();
        fillRelations(vos);
        return filterByProductName(vos, query);
    }

    // ============================ 批量取数（jsPDF 出码）============================

    @Override
    public List<TraceCodeDetailVo> batchDetail(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<TraceCode> codes = baseMapper.selectByIds(ids);
        if (codes.isEmpty()) {
            return Collections.emptyList();
        }
        List<TraceCodeDetailVo> details = codes.stream().map(c -> {
            TraceCodeDetailVo d = new TraceCodeDetailVo();
            copyCodeFields(c, d);
            return d;
        }).collect(Collectors.toList());
        fillRelations(details);

        // 批量取各码的事件链（一次查全部，按 produceCode 内存分组，避免 N+1）
        List<String> produceCodes = codes.stream()
            .map(TraceCode::getProduceCode).filter(Objects::nonNull).distinct().toList();
        if (!produceCodes.isEmpty()) {
            List<TraceEventVo> allEvents = traceEventMapper.selectVoList(
                new LambdaQueryWrapper<TraceEvent>()
                    .in(TraceEvent::getProduceCode, produceCodes)
                    .orderByAsc(TraceEvent::getTraceTime)
                    .orderByAsc(TraceEvent::getId));
            Map<String, List<TraceEventVo>> byCode = allEvents.stream()
                .collect(Collectors.groupingBy(TraceEventVo::getProduceCode));
            for (TraceCodeDetailVo d : details) {
                d.setEvents(byCode.getOrDefault(d.getProduceCode(), List.of()));
            }
        }
        return details;
    }

    // ============================ 内部辅助 ============================

    /** 门店现场生码 remark 前缀（{@code TraceServiceImpl.buildOnsiteRemark} 写入），区分仓库/门店生成来源。 */
    private static final String ONSITE_REMARK_PREFIX = "现场生码";
    /** 生成来源：仓库（打包/发货产出）。 */
    private static final String SOURCE_WAREHOUSE = "warehouse";
    /** 生成来源：门店现场分割打包。 */
    private static final String SOURCE_STORE = "store";
    /** 白条产品业态（{@code t_warehouse_product_info.belong_type}，字典 djs_belong_type）；猪肉追溯列表排除。 */
    private static final String WHITE_BAR_BELONG_TYPE = "white_bar";

    private LambdaQueryWrapper<TraceCode> buildWrapper(TraceCodeQuery query) {
        LambdaQueryWrapper<TraceCode> w = new LambdaQueryWrapper<>();
        if (query != null) {
            w.eq(query.getCodeType() != null && !query.getCodeType().isBlank(),
                    TraceCode::getCodeType, query.getCodeType())
                .like(query.getProduceCode() != null && !query.getProduceCode().isBlank(),
                    TraceCode::getProduceCode, query.getProduceCode())
                .eq(query.getPigEarNo() != null && !query.getPigEarNo().isBlank(),
                    TraceCode::getPigEarNo, query.getPigEarNo())
                .ge(query.getBeginDate() != null, TraceCode::getCreateTime, query.getBeginDate())
                .le(query.getEndDate() != null, TraceCode::getCreateTime, query.getEndDate());
            applySourceFilter(w, query);
            applyArrivalDateFilter(w, query);
            applyVegShipFilter(w, query);
            applyPorkExcludeWhiteBarFilter(w, query);
        }
        w.orderByDesc(TraceCode::getCreateTime).orderByDesc(TraceCode::getId);
        return w;
    }

    /**
     * 果蔬追溯码管理：只显示「已发货」的追溯码（DENGBO row139）。
     *
     * <p>发货时间 = trace_event 的 {@code ship} 事件时间（列表 {@code shipTime} 列同源，
     * 见 {@link #fillTraceTimes}），主表无发货时间列。仅在果蔬 tab（{@code codeType=veg}）生效——
     * 果蔬追溯码走仓库发货月台确认发货后才应展示，未发货的中间态码不列。猪肉 / 礼盒 tab 不加此约束。</p>
     *
     * <p>先查有 ship 事件的 produce_code 集合再 in 主表（DB 层过滤，不破坏分页 total）；无任何 ship 事件
     * 时强制空结果（{@code 1 = 0}），不退化成全量。</p>
     */
    private void applyVegShipFilter(LambdaQueryWrapper<TraceCode> w, TraceCodeQuery query) {
        if (!TraceCodeTypeConst.VEG.equals(query.getCodeType())) {
            return;
        }
        List<TraceEvent> shipEvents = traceEventMapper.selectList(
            new LambdaQueryWrapper<TraceEvent>()
                .select(TraceEvent::getProduceCode)
                .eq(TraceEvent::getTraceContent, TraceContentConst.SHIP));
        List<String> shippedCodes = (shipEvents == null ? Collections.<TraceEvent>emptyList() : shipEvents).stream()
            .map(TraceEvent::getProduceCode).filter(Objects::nonNull).distinct().toList();
        if (shippedCodes.isEmpty()) {
            w.apply("1 = 0");
            return;
        }
        w.in(TraceCode::getProduceCode, shippedCodes);
    }

    /**
     * 猪肉追溯码管理：列表不显示白条产品（DENGBO row140）。
     *
     * <p>白条 = 产品主数据 {@code t_warehouse_product_info.belong_type='white_bar'}（追溯码主表无 belong_type 列，
     * 按 {@code product_id} 反查产品业态）。仅在猪肉 tab（{@code codeType=pork}）生效——白条是猪肉链的中间产物，
     * 追溯码列表只展示最终肉品，白条产品码不列。果蔬 / 礼盒 tab 不加此约束。</p>
     *
     * <p>查 belong_type='white_bar' 的产品 id 集合，主表排除；无白条产品时不加条件（全量放行，不误伤）。</p>
     *
     * <p><b>门店现场码 product_id=NULL 必须显式放行</b>（DENGBO-R34）：门店现做码「纯生码不查 product_info」
     * 故 {@code product_id} 为 NULL。SQL 里 {@code NULL NOT IN (...)} 结果为 NULL（WHERE 当假）→ 裸 notIn 会
     * 把所有门店现场码一并排除，导致门店打包生码后在猪肉追溯码管理里恒不可见。故改为
     * {@code (product_id IS NULL OR product_id NOT IN (...))}，仅排白条产品、放行 NULL。</p>
     */
    private void applyPorkExcludeWhiteBarFilter(LambdaQueryWrapper<TraceCode> w, TraceCodeQuery query) {
        if (!TraceCodeTypeConst.PORK.equals(query.getCodeType())) {
            return;
        }
        List<ProductInfo> whiteBarProducts = productInfoMapper.selectList(
            new LambdaQueryWrapper<ProductInfo>()
                .select(ProductInfo::getId)
                .eq(ProductInfo::getBelongType, WHITE_BAR_BELONG_TYPE));
        List<Long> whiteBarProductIds = (whiteBarProducts == null ? Collections.<ProductInfo>emptyList() : whiteBarProducts).stream()
            .map(ProductInfo::getId).filter(Objects::nonNull).distinct().toList();
        if (whiteBarProductIds.isEmpty()) {
            return;
        }
        w.and(sub -> sub.isNull(TraceCode::getProductId)
            .or().notIn(TraceCode::getProductId, whiteBarProductIds));
    }

    /**
     * 生成来源过滤：{@code store}=门店现场生码（remark LIKE '现场生码%'）；
     * {@code warehouse}=仓库生码（remark 为空或非「现场生码」前缀）。空 = 不过滤（仓库 + 门店都列）。
     */
    private void applySourceFilter(LambdaQueryWrapper<TraceCode> w, TraceCodeQuery query) {
        String src = query.getSource();
        if (src == null || src.isBlank()) {
            return;
        }
        if (SOURCE_STORE.equals(src)) {
            w.likeRight(TraceCode::getRemark, ONSITE_REMARK_PREFIX);
        } else if (SOURCE_WAREHOUSE.equals(src)) {
            w.and(qw -> qw.isNull(TraceCode::getRemark).or().notLikeRight(TraceCode::getRemark, ONSITE_REMARK_PREFIX));
        }
    }

    /**
     * 门店现场生码行（{@code source=store}）产品名 + 实际重量回填（列表展示）。
     *
     * <p>门店现场生码「纯生码不联动库存」——不建 {@code product_production}（否则会被仓库打包来源
     * {@code listSourceForMeat} 等误捞），故 product_id / 产出记录均空，{@code 产品 / 产品规格 / 实际重量 /
     * 生产编号} 列在仓库流口径下取不到值。但产品名 + 重量已落在 remark「现场生码 部位=X 重量=Ykg」，
     * 这里从 remark 回填 {@code productName + actualWeight}，避免门店行这两列空白（备注里有、列里却空的割裂感）。
     * 到店日期 / 生产编号 / 产品规格 是仓库发货流字段，门店现做码本无，按现状留空。</p>
     */
    private void fillOnsiteDerivedFields(List<? extends TraceCodeListVo> vos) {
        if (vos == null) {
            return;
        }
        for (TraceCodeListVo vo : vos) {
            if (!SOURCE_STORE.equals(vo.getSource())) {
                continue;
            }
            String remark = vo.getRemark();
            if (remark == null || remark.isBlank()) {
                continue;
            }
            // 产品名 ← remark「部位=X」（截到「重量=」前）
            if (vo.getProductName() == null || vo.getProductName().isBlank()) {
                int pi = remark.indexOf("部位=");
                if (pi >= 0) {
                    int wi = remark.indexOf("重量=", pi);
                    String part = (wi > pi ? remark.substring(pi + 3, wi) : remark.substring(pi + 3)).trim();
                    if (!part.isEmpty()) {
                        vo.setProductName(part);
                    }
                }
            }
            // 实际重量 ← remark「重量=Ykg」（剥非数字字符）
            if (vo.getActualWeight() == null) {
                int wi = remark.indexOf("重量=");
                if (wi >= 0) {
                    String num = remark.substring(wi + 3).replaceAll("[^0-9.]", "");
                    if (!num.isEmpty()) {
                        try {
                            vo.setActualWeight(new java.math.BigDecimal(num));
                        } catch (NumberFormatException ignore) {
                            // 非法重量串忽略，保持空
                        }
                    }
                }
            }
        }
    }

    /**
     * 按「到店日期」过滤（果蔬追溯码管理默认显示当天到店）。
     *
     * <p>到店日期 = trace_event 的 ARRIVAL 事件 trace_time，非主表 create_time（生成时间）。
     * 先查命中区间的 ARRIVAL 事件 produce_code 集合，再 in 主表过滤；DB 层先过滤维度正确，
     * 避免「昨天生成今天到店」的码被生成时间区间漏掉。区间内无任何 arrival 事件时强制空结果
     * （in 空集合恒 false），不退化成全量。</p>
     */
    private void applyArrivalDateFilter(LambdaQueryWrapper<TraceCode> w, TraceCodeQuery query) {
        if (query.getArrivalBeginDate() == null && query.getArrivalEndDate() == null) {
            return;
        }
        LambdaQueryWrapper<TraceEvent> ew = new LambdaQueryWrapper<TraceEvent>()
            .select(TraceEvent::getProduceCode)
            .eq(TraceEvent::getTraceContent, TraceContentConst.ARRIVAL)
            .ge(query.getArrivalBeginDate() != null,
                TraceEvent::getTraceTime, toLocalDateTime(query.getArrivalBeginDate()))
            .le(query.getArrivalEndDate() != null,
                TraceEvent::getTraceTime, toLocalDateTime(query.getArrivalEndDate()));
        List<String> arrivalCodes = traceEventMapper.selectList(ew).stream()
            .map(TraceEvent::getProduceCode).filter(Objects::nonNull).distinct().toList();
        if (arrivalCodes.isEmpty()) {
            // 区间内无到店事件 → 列表为空（恒 false 条件）
            w.apply("1 = 0");
            return;
        }
        w.in(TraceCode::getProduceCode, arrivalCodes);
    }

    private java.time.LocalDateTime toLocalDateTime(Date d) {
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * 主表行 → 列表 VO（仅主表字段，关联名字由 {@link #fillRelations} 补）。
     */
    private TraceCodeListVo toListVo(TraceCode c) {
        TraceCodeListVo vo = new TraceCodeListVo();
        copyCodeFields(c, vo);
        return vo;
    }

    /**
     * 拷主表字段到 VO（list / detail 共用，避免漏字段）。
     */
    private void copyCodeFields(TraceCode c, TraceCodeListVo vo) {
        vo.setId(c.getId());
        vo.setProduceCode(c.getProduceCode());
        // DENGBO-ROW84：门店现做码持久化的生产编码（仓库码 / 旧门店码为 NULL）
        vo.setProductionCode(c.getProductionCode());
        vo.setCodeType(c.getCodeType());
        // 生成来源：remark「现场生码」前缀 = 门店现场分割打包；否则 = 仓库（打包/发货产出）
        vo.setSource(c.getRemark() != null && c.getRemark().startsWith(ONSITE_REMARK_PREFIX)
            ? SOURCE_STORE : SOURCE_WAREHOUSE);
        vo.setProductId(c.getProductId());
        // DENGBO-R16：优先展示生码时定格的展示名（果蔬按有机证书取产品名 / 别名）。旧码该列为 NULL，
        // 由 fillProducts 兜底 JOIN 产品当前名。
        vo.setProductName(c.getTraceDisplayName());
        vo.setPigEarNo(c.getPigEarNo());
        vo.setPlotId(c.getPlotId());
        vo.setPlantDays(c.getPlantDays());
        vo.setHarvestDate(c.getHarvestDate());
        vo.setCropCertId(c.getCropCertId());
        vo.setPlotCertId(c.getPlotCertId());
        vo.setStoreId(c.getStoreId());
        vo.setFarmId(c.getFarmId());
        vo.setQrOssId(c.getQrOssId());
        vo.setRemark(c.getRemark());
        vo.setCreateBy(c.getCreateBy());
        vo.setCreateTime(c.getCreateTime());
    }

    /**
     * 批量填 product / store / plot / farm 展示名（一次性查关联表内存 JOIN，避免 N+1）。
     */
    private void fillRelations(List<? extends TraceCodeListVo> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        fillProducts(vos);
        fillStoreNames(vos);
        fillPlotNames(vos);
        fillFarmNames(vos);
        fillProductionFields(vos);
        fillTraceTimes(vos);
    }

    /**
     * 填 果蔬追溯码管理 原型列：生产编号 / 序号 / 实际重量 / 采摘时间。
     *
     * <p>按 {@code produce_code} 反查 {@code t_warehouse_product_production}（产出记录 {@code trace_code}
     * 字段回填了追溯码），一次性批量查后内存 JOIN（避免 N+1）。一个追溯码对应一条产出记录（打包入库时
     * genCode 回填 production.traceCode）；多条取最新。</p>
     */
    private void fillProductionFields(List<? extends TraceCodeListVo> vos) {
        List<String> codes = vos.stream()
            .map(TraceCodeListVo::getProduceCode).filter(Objects::nonNull).distinct().toList();
        if (codes.isEmpty()) {
            return;
        }
        // produce_code → 最新一条产出记录（id 大者优先）
        List<ProductProduction> rows = productProductionMapper.selectList(
            new LambdaQueryWrapper<ProductProduction>()
                .in(ProductProduction::getTraceCode, codes)
                .orderByDesc(ProductProduction::getId));
        Map<String, ProductProduction> byCode = rows == null ? Map.of() : rows.stream()
            .collect(Collectors.toMap(ProductProduction::getTraceCode, p -> p, (a, b) -> a));
        for (TraceCodeListVo vo : vos) {
            ProductProduction p = vo.getProduceCode() == null ? null : byCode.get(vo.getProduceCode());
            if (p != null) {
                vo.setProduceNo(p.getProduceNo());
                vo.setSerialNo(p.getProductSort() != null ? p.getProductSort() : parseSerial(p.getProduceNo()));
                // 实际重量：优先 productWeight（成品净重），缺则 produceQuantity
                vo.setActualWeight(p.getProductWeight() != null ? p.getProductWeight() : p.getProduceQuantity());
                // 采摘时间：产出记录 produceTime（时刻）优先，缺则 produceDate
                vo.setPickTime(p.getProduceTime() != null ? p.getProduceTime() : p.getProduceDate());
            }
            // DENGBO-ROW84：门店现做码无产出记录（produceNo 反查空），用持久化的生产编码兜底填「生产编号 / 序号」，
            //   让补打（重打印）列表显示生产编码（仓库码有 produceNo 时不覆盖）。
            if (StringUtils.isBlank(vo.getProduceNo()) && StringUtils.isNotBlank(vo.getProductionCode())) {
                vo.setProduceNo(vo.getProductionCode());
                if (vo.getSerialNo() == null) {
                    vo.setSerialNo(parseSerial(vo.getProductionCode()));
                }
            }
        }
    }

    /**
     * 填 到店日期 / 月台接收时间 / 发货时间：按 {@code produce_code} 批量查 trace_event，
     * 取 arrival / in_stock / ship 三类事件各自最新一条的时间（无对应事件则该字段空，不造假）。
     */
    private void fillTraceTimes(List<? extends TraceCodeListVo> vos) {
        List<String> codes = vos.stream()
            .map(TraceCodeListVo::getProduceCode).filter(Objects::nonNull).distinct().toList();
        if (codes.isEmpty()) {
            return;
        }
        List<TraceEvent> events = traceEventMapper.selectList(
            new LambdaQueryWrapper<TraceEvent>()
                .in(TraceEvent::getProduceCode, codes)
                .in(TraceEvent::getTraceContent, List.of(
                    TraceContentConst.ARRIVAL, TraceContentConst.IN_STOCK, TraceContentConst.SHIP))
                .orderByAsc(TraceEvent::getTraceTime).orderByAsc(TraceEvent::getId));
        if (events == null || events.isEmpty()) {
            return;
        }
        // produceCode → content → 最新事件时间（升序遍历后者覆盖前者 = 取最新）
        Map<String, Map<String, Date>> byCode = new java.util.HashMap<>();
        for (TraceEvent e : events) {
            if (e.getTraceTime() == null) {
                continue;
            }
            byCode.computeIfAbsent(e.getProduceCode(), k -> new java.util.HashMap<>())
                .put(e.getTraceContent(), toDate(e.getTraceTime()));
        }
        for (TraceCodeListVo vo : vos) {
            Map<String, Date> m = vo.getProduceCode() == null ? null : byCode.get(vo.getProduceCode());
            if (m == null) {
                continue;
            }
            Date arrival = m.get(TraceContentConst.ARRIVAL);
            vo.setPlatformReceiveTime(m.get(TraceContentConst.IN_STOCK));
            vo.setShipTime(m.get(TraceContentConst.SHIP));
            if (arrival != null) {
                vo.setArrivalDate(toLocalDate(arrival));
            }
        }
    }

    /**
     * 生产编号尾部连续数字 → 序号 Integer；无尾部数字 / 溢出返 null。
     */
    private Integer parseSerial(String produceNo) {
        if (produceNo == null) {
            return null;
        }
        Matcher m = SERIAL_TAIL.matcher(produceNo);
        if (!m.find()) {
            return null;
        }
        try {
            return Integer.valueOf(m.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Date toDate(java.time.LocalDateTime ldt) {
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    private LocalDate toLocalDate(Date d) {
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private void fillProducts(List<? extends TraceCodeListVo> vos) {
        List<Long> ids = vos.stream()
            .map(TraceCodeListVo::getProductId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, ProductInfo> map = productInfoMapper.selectByIds(ids).stream()
            .collect(Collectors.toMap(ProductInfo::getId, p -> p, (a, b) -> a));
        // DENGBO-R16 / row26：旧码 trace_display_name 为 NULL 时的兜底名，也要按「有机证书有效性」解析
        //（果蔬无有效有机证书 → 取产品别名），与生码时定格口径一致，避免旧码在追溯列表/追溯码打印显裸产品名。
        Map<Long, String> resolvedNames = displayNameResolver.resolveDisplayNames(ids);
        for (TraceCodeListVo vo : vos) {
            ProductInfo p = vo.getProductId() == null ? null : map.get(vo.getProductId());
            if (p != null) {
                // 仅在 copyCodeFields 未从 trace_display_name 定格出名（旧码 NULL）时兜底，不覆盖已定格的快照名。
                if (StringUtils.isBlank(vo.getProductName())) {
                    String resolved = resolvedNames.get(vo.getProductId());
                    vo.setProductName(StringUtils.isNotBlank(resolved) ? resolved : p.getProductName());
                }
                vo.setProductSpec(p.getProductSpec());
                vo.setProductImg(p.getProductImg());
            }
        }
    }

    private void fillStoreNames(List<? extends TraceCodeListVo> vos) {
        List<Long> ids = vos.stream()
            .map(TraceCodeListVo::getStoreId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> map = storeMapper.selectList(
                new LambdaQueryWrapper<Store>().in(Store::getId, ids)).stream()
            .collect(Collectors.toMap(Store::getId, Store::getStoreName, (a, b) -> a));
        for (TraceCodeListVo vo : vos) {
            if (vo.getStoreId() != null) {
                vo.setStoreName(map.get(vo.getStoreId()));
            }
        }
    }

    private void fillPlotNames(List<? extends TraceCodeListVo> vos) {
        List<Long> ids = vos.stream()
            .map(TraceCodeListVo::getPlotId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> map = plotInfoMapper.selectList(
                new LambdaQueryWrapper<PlotInfo>().in(PlotInfo::getId, ids)).stream()
            .collect(Collectors.toMap(PlotInfo::getId, PlotInfo::getPlotCode, (a, b) -> a));
        for (TraceCodeListVo vo : vos) {
            if (vo.getPlotId() != null) {
                vo.setPlotName(map.get(vo.getPlotId()));
            }
        }
    }

    private void fillFarmNames(List<? extends TraceCodeListVo> vos) {
        List<Long> ids = vos.stream()
            .map(TraceCodeListVo::getFarmId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> map = traceFarmNameMapper.selectFarmNames(ids, currentTenantId()).stream()
            .collect(Collectors.toMap(
                m -> ((Number) m.get("id")).longValue(),
                m -> String.valueOf(m.get("farmName")),
                (a, b) -> a));
        for (TraceCodeListVo vo : vos) {
            if (vo.getFarmId() != null) {
                vo.setFarmName(map.get(vo.getFarmId()));
            }
        }
    }

    /**
     * productName 模糊筛选（主表无该列，JOIN 后内存过滤）。
     */
    private List<TraceCodeListVo> filterByProductName(List<TraceCodeListVo> vos, TraceCodeQuery query) {
        if (query == null || query.getProductName() == null || query.getProductName().isBlank()) {
            return vos;
        }
        String kw = query.getProductName().trim();
        return vos.stream()
            .filter(v -> v.getProductName() != null && v.getProductName().contains(kw))
            .collect(Collectors.toList());
    }

    /**
     * 当前租户 ID（无登录上下文兜底默认租户），供 farm 自定义 SQL 显式带 tenant_id。
     */
    private String currentTenantId() {
        String t = TenantHelper.getTenantId();
        return (t == null || t.isBlank()) ? DEFAULT_TENANT : t;
    }

}
