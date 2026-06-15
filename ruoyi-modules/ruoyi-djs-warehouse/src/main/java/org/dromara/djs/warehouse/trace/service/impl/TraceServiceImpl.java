package org.dromara.djs.warehouse.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.trace.domain.TraceCode;
import org.dromara.djs.warehouse.trace.domain.TraceCodeTypeConst;
import org.dromara.djs.warehouse.trace.domain.TraceContentConst;
import org.dromara.djs.warehouse.trace.domain.TraceEvent;
import org.dromara.djs.warehouse.trace.mapper.TraceCodeMapper;
import org.dromara.djs.warehouse.trace.mapper.TraceEventMapper;
import org.dromara.djs.warehouse.trace.service.ITraceService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 追溯码生成 + 事件流水核心服务实现（TRC-CORE-001）。
 *
 * <h3>追溯码生成（genCode）</h3>
 * <p>查 {@link ProductInfo#getBelongType()} 判业态 → 映射 code_type(pork/veg/gift) + productCode2(PG/VG/GF)
 * → {@link IBizCodeGenerator} 用 {@link BizCodeType#TRACE_CODE} 生成（Redisson 锁 + 序号表 UNIQUE +
 * DB UNIQUE 三重并发保护）→ INSERT trace_code（按业态填链路字段，不冗余存名/重量）。</p>
 *
 * <h3>事件流水（recordEvent / recordEventByEarNo）immutable</h3>
 * <p>只 INSERT trace_event，**绝不 UPDATE / DELETE**。追溯写失败全程 try-catch 仅 warn 日志，
 * 不抛异常（追溯链允许部分缺失，绝不拖垮核心业务工序）。</p>
 *
 * <h3>猪肉链耳号事件回填（genCode 时序补偿）</h3>
 * <p>pork trace_code 在打包出库（{@code submitWhiteBarOut} → IN_STOCK 时刻）才生成，而出栏 / 燎毛 /
 * 屠宰 / 排酸 4 个上游耳号事件早于此发生——它们的实时 {@link #recordEventByEarNo} 调用因 code 尚未出生而
 * 反查落空跳过。为补齐链路，{@link #genCode} 在 pork code 出生后立即调 {@link #backfillEarNoEvents}：按耳号
 * 查 {@code t_warehouse_bar_info}（一头猪一行，沉淀全生命周期时间戳）重建这 4 个事件，**用各阶段真实时刻**
 * （marketing_time / in_time / out_time）而非当前时间写入 trace_event。幂等：同一 code 已存在这些事件则不重复写。
 * 至此一个 pork 码可串起 marketing → singe → slaughter → acid → in_stock → ship（6 事件）；arrival（到店）
 * 当前系统无触发动作，单独待产品拍板。</p>
 *
 * @author djs
 * @since TRC-CORE-001
 */
@Slf4j
@Service
public class TraceServiceImpl
    extends DjsBaseServiceImpl<TraceCodeMapper, TraceCode>
    implements ITraceService {

    /**
     * belong_type 归猪肉链（字典 djs_belong_type 实证值，对齐 V202605311100 + V202606071300 seed）。
     */
    private static final Set<String> PORK_BELONG_TYPES = Set.of("pork", "white_bar");

    /**
     * belong_type 归果蔬链。
     */
    private static final Set<String> VEG_BELONG_TYPES = Set.of("vegetable", "dry_good", "egg");

    /**
     * belong_type 归礼盒。
     */
    private static final String GIFT_BELONG_TYPE = "gift_box";

    /**
     * 回填的 4 个上游耳号事件（按真实业务先后顺序）。{@link #backfillEarNoEvents} 据此查 bar_info 时间戳重建。
     */
    private static final Set<String> EAR_NO_BACKFILL_CONTENTS = Set.of(
        TraceContentConst.MARKETING, TraceContentConst.SINGE,
        TraceContentConst.SLAUGHTER, TraceContentConst.ACID);

    /**
     * {@code trace_event.event_data} JSON 里重量字段 key（追溯时间轴每节点重量）。
     * 序列化形如 {@code {"weight":"5.20"}}，前端时间轴节点据此展示「· 5.20kg」。
     */
    private static final String EVENT_DATA_WEIGHT_KEY = "weight";

    private final TraceEventMapper traceEventMapper;
    private final ProductInfoMapper productInfoMapper;
    private final BarInfoMapper barInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;

    public TraceServiceImpl(TraceCodeMapper baseMapper,
                            TraceEventMapper traceEventMapper,
                            ProductInfoMapper productInfoMapper,
                            BarInfoMapper barInfoMapper,
                            IBizCodeGenerator bizCodeGenerator) {
        super(baseMapper);
        this.traceEventMapper = traceEventMapper;
        this.productInfoMapper = productInfoMapper;
        this.barInfoMapper = barInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
    }

    @Override
    public String genCode(Long productId, String pigEarNo, Long plotId) {
        if (productId == null) {
            throw new ServiceException("生成追溯码失败：productId 为空");
        }
        // a. 查产品判业态
        ProductInfo product = productInfoMapper.selectById(productId);
        if (product == null) {
            throw new ServiceException("生成追溯码失败：产品不存在 productId=" + productId);
        }
        String belongType = product.getBelongType();

        // b. 业态 → code_type + productCode2
        String codeType = resolveCodeType(belongType);
        String productCode2 = resolveProductCode2(codeType);

        // c. 生成追溯业务码（占位符 {productCode2} 从 context.productCode 取，见 IBizCodeGenerator javadoc）
        String produceCode = bizCodeGenerator.generate(
            BizCodeType.TRACE_CODE, Map.of("productCode", productCode2));

        // d. 组装 trace_code（按业态填链路字段，互斥为 NULL；不存 name/weight/store_address）
        TraceCode traceCode = new TraceCode();
        traceCode.setProduceCode(produceCode);
        traceCode.setCodeType(codeType);
        traceCode.setProductId(productId);
        if (TraceCodeTypeConst.PORK.equals(codeType)) {
            traceCode.setPigEarNo(pigEarNo);
        } else if (TraceCodeTypeConst.VEG.equals(codeType)) {
            traceCode.setPlotId(plotId);
            // plant_days / havest_date / crop_cert_id / plot_cert_id：V1 上下文不直接可得，留 NULL
            // （果蔬采收链路明细 D14 admin 端 JOIN 地块 / 认证补全）
        }
        // gift：gift_components / qr_oss_id 写 NULL（V1 预留，V3 启用）
        // e. INSERT（不显式赋 tenant_id，走 InjectionMetaObjectHandler.insertFill）
        insertTraceCode(traceCode);

        // f. 猪肉链：code 出生晚于 4 个上游耳号事件（marketing/singe/slaughter/acid），
        //    实时 recordEventByEarNo 那时反查落空跳过 → 此处按耳号回填（真实时间戳）补齐链路。
        if (TraceCodeTypeConst.PORK.equals(codeType) && StringUtils.isNotBlank(pigEarNo)) {
            backfillEarNoEvents(produceCode, pigEarNo);
        }

        log.info("[TRC-CORE-001] genCode produceCode={} codeType={} productId={} belongType={}",
            produceCode, codeType, productId, belongType);
        return produceCode;
    }

    @Override
    public String genPorkOnsiteCode(String earNo, String cutLabel, java.math.BigDecimal weight) {
        if (StringUtils.isBlank(earNo)) {
            throw new ServiceException("现场生码失败：猪只耳号为空");
        }
        if (weight == null || weight.signum() <= 0) {
            throw new ServiceException("现场生码失败：产品重量需大于 0");
        }
        // a. 固定 pork 业态生码（零售部位非标准 SKU，不查 product_info）
        String produceCode = bizCodeGenerator.generate(
            BizCodeType.TRACE_CODE, Map.of("productCode", TraceCodeTypeConst.PRODUCT_CODE_PORK));

        // b. 组装 trace_code（部位 + 重量写 remark，表无 cut_part / weight 列）
        TraceCode traceCode = new TraceCode();
        traceCode.setProduceCode(produceCode);
        traceCode.setCodeType(TraceCodeTypeConst.PORK);
        traceCode.setPigEarNo(earNo);
        traceCode.setRemark(buildOnsiteRemark(cutLabel, weight));
        insertTraceCode(traceCode);

        // c. 写一条 in_stock 事件锚定现场生码时刻（operator=当前门店操作员）
        recordEvent(produceCode, TraceContentConst.IN_STOCK);

        // d. 按耳号回填上游 4 事件（marketing/singe/slaughter/acid，真实时间戳），补齐链路
        backfillEarNoEvents(produceCode, earNo);

        log.info("[STORE-TRACE-ONSITE-001] genPorkOnsiteCode produceCode={} earNo={} cut={} weight={}",
            produceCode, earNo, cutLabel, weight);
        return produceCode;
    }

    /**
     * 现场生码备注：部位 + 重量（trace_code 无专用列，落 remark）。
     */
    private String buildOnsiteRemark(String cutLabel, java.math.BigDecimal weight) {
        StringBuilder sb = new StringBuilder("现场生码");
        if (StringUtils.isNotBlank(cutLabel)) {
            sb.append(" 部位=").append(cutLabel);
        }
        sb.append(" 重量=").append(weight).append("kg");
        return sb.toString();
    }

    @Override
    public void recordEvent(String produceCode, String traceContent) {
        recordEvent(produceCode, traceContent, null);
    }

    @Override
    public void recordEvent(String produceCode, String traceContent, BigDecimal weight) {
        if (StringUtils.isBlank(produceCode)) {
            log.warn("[TRC-CORE-001] recordEvent skipped: empty produceCode, content={}", traceContent);
            return;
        }
        try {
            TraceEvent event = new TraceEvent();
            event.setProduceCode(produceCode);
            event.setTraceContent(traceContent);
            event.setTraceTime(LocalDateTime.now());
            event.setOperatorId(LoginHelper.getUserId());
            event.setEventData(buildWeightEventData(weight));
            insertTraceEvent(event);
            log.info("[TRC-CORE-001] recordEvent produceCode={} content={} weight={}",
                produceCode, traceContent, weight);
        } catch (Exception e) {
            // 追溯写失败绝不拖垮主业务工序 → 仅 warn，不抛
            log.warn("[TRC-CORE-001] recordEvent failed (skipped) produceCode={} content={}: {}",
                produceCode, traceContent, e.getMessage());
        }
    }

    @Override
    public void recordEventByEarNo(String earNo, String traceContent) {
        recordEventByEarNo(earNo, traceContent, null);
    }

    @Override
    public void recordEventByEarNo(String earNo, String traceContent, BigDecimal weight) {
        if (StringUtils.isBlank(earNo)) {
            log.warn("[TRC-CORE-001] recordEventByEarNo skipped: empty earNo, content={}", traceContent);
            return;
        }
        String produceCode = findProduceCodeByEarNo(earNo);
        if (produceCode == null) {
            // 猪肉链 trace_code 当前无生成入口，反查落空属 V1 预期 → warn 跳过
            log.warn("[TRC-CORE-001] recordEventByEarNo skipped: no trace_code for earNo={}, content={}",
                earNo, traceContent);
            return;
        }
        recordEvent(produceCode, traceContent, weight);
    }

    /**
     * 把节点重量序列化成 {@code event_data} JSON（{@code {"weight":"5.20"}}，Jackson 经 {@link JsonUtils}）。
     * {@code weight} 为 null → 返 null（event_data 保持 NULL，不写空对象）。重量用 {@code toPlainString}
     * 保留原始精度，避免科学计数法。
     */
    private String buildWeightEventData(BigDecimal weight) {
        if (weight == null) {
            return null;
        }
        return JsonUtils.toJsonString(Map.of(EVENT_DATA_WEIGHT_KEY, weight.toPlainString()));
    }

    /**
     * 猪肉链耳号事件回填：pork trace_code 出生时，按耳号查 {@code t_warehouse_bar_info}（一头猪一行，
     * 沉淀出栏/燎毛/分割全生命周期时间戳）重建 4 个上游事件（marketing/singe/slaughter/acid），用各阶段
     * 真实时刻而非当前时间写入 trace_event，补齐 code 出生前丢失的链路。
     *
     * <h3>时间戳来源（bar_info 列）</h3>
     * <ul>
     *   <li>marketing → {@code marketing_time}（出栏时刻）</li>
     *   <li>singe → {@code in_time}（燎毛入库时刻，cutDone 前置）</li>
     *   <li>slaughter / acid → {@code out_time}（分割出库即排酸完成时刻，二者同 cutDone 触发，
     *       slaughter 先于 acid 以 id 递增稳定排序）</li>
     * </ul>
     *
     * <p>容错：bar_info 查不到（外购无耳号 / 数据缺失）或某阶段时间戳为 NULL（工序尚未走到）→ 该事件跳过，
     * 不补造假数据。幂等：同一 produceCode 已存在某事件则不重复写（防 genCode 重入）。整体 try-catch swallow，
     * 回填失败绝不拖垮打包主事务（与 {@link #recordEvent} 容错策略一致）。protected 方便单测 stub。</p>
     *
     * @param produceCode 已生成的 pork 追溯码
     * @param earNo       猪只耳号
     */
    protected void backfillEarNoEvents(String produceCode, String earNo) {
        try {
            BarInfo bar = findBarByEarNo(earNo);
            if (bar == null) {
                log.warn("[TRC-CORE-001] backfill skipped: no bar_info for earNo={} produceCode={}",
                    earNo, produceCode);
                return;
            }
            Set<String> existing = findExistingContents(produceCode, EAR_NO_BACKFILL_CONTENTS);
            int written = 0;
            written += backfillOne(produceCode, TraceContentConst.MARKETING, bar.getMarketingTime(), existing);
            written += backfillOne(produceCode, TraceContentConst.SINGE, bar.getInTime(), existing);
            written += backfillOne(produceCode, TraceContentConst.SLAUGHTER, bar.getOutTime(), existing);
            written += backfillOne(produceCode, TraceContentConst.ACID, bar.getOutTime(), existing);
            log.info("[TRC-CORE-001] backfill ear-no events produceCode={} earNo={} written={}",
                produceCode, earNo, written);
        } catch (Exception e) {
            // 回填失败绝不拖垮打包主事务 → 仅 warn，不抛
            log.warn("[TRC-CORE-001] backfill ear-no events failed (skipped) produceCode={} earNo={}: {}",
                produceCode, earNo, e.getMessage());
        }
    }

    /**
     * 回填单个事件：时间戳非空且该事件尚未存在 → INSERT trace_event（真实时刻）。返回写入条数（0/1）。
     * 回填事件无登录上下文阶段性操作人（4 阶段操作人分散在 3 张源表，bar_info 不逐阶段存），operator_id 留 NULL。
     */
    private int backfillOne(String produceCode, String traceContent, Date eventTime, Set<String> existing) {
        if (eventTime == null || existing.contains(traceContent)) {
            return 0;
        }
        TraceEvent event = new TraceEvent();
        event.setProduceCode(produceCode);
        event.setTraceContent(traceContent);
        event.setTraceTime(toLocalDateTime(eventTime));
        insertTraceEvent(event);
        return 1;
    }

    /**
     * 按耳号查 bar_info（一头猪一行；多行取最新）。protected 方便单测 stub。
     */
    protected BarInfo findBarByEarNo(String earNo) {
        return barInfoMapper.selectOne(
            new LambdaQueryWrapper<BarInfo>()
                .eq(BarInfo::getEarNo, earNo)
                .orderByDesc(BarInfo::getId)
                .last("LIMIT 1"));
    }

    /**
     * 查某追溯码已存在的事件类型（限定在 candidates 内），用于回填幂等。protected 方便单测 stub。
     */
    protected Set<String> findExistingContents(String produceCode, Set<String> candidates) {
        List<TraceEvent> rows = traceEventMapper.selectList(
            new LambdaQueryWrapper<TraceEvent>()
                .select(TraceEvent::getTraceContent)
                .eq(TraceEvent::getProduceCode, produceCode)
                .in(TraceEvent::getTraceContent, new ArrayList<>(candidates)));
        Set<String> set = new HashSet<>();
        for (TraceEvent r : rows) {
            set.add(r.getTraceContent());
        }
        return set;
    }

    private LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * 业态 belong_type → code_type（pork/veg/gift）。未知业态默认归果蔬链（容错，不抛）。
     */
    protected String resolveCodeType(String belongType) {
        if (belongType == null) {
            log.warn("[TRC-CORE-001] belongType is null, default to veg");
            return TraceCodeTypeConst.VEG;
        }
        if (PORK_BELONG_TYPES.contains(belongType)) {
            return TraceCodeTypeConst.PORK;
        }
        if (GIFT_BELONG_TYPE.equals(belongType)) {
            return TraceCodeTypeConst.GIFT;
        }
        if (VEG_BELONG_TYPES.contains(belongType)) {
            return TraceCodeTypeConst.VEG;
        }
        log.warn("[TRC-CORE-001] unknown belongType={}, default to veg", belongType);
        return TraceCodeTypeConst.VEG;
    }

    /**
     * code_type → 追溯业务码占位符 productCode2（PG/VG/GF）。
     */
    protected String resolveProductCode2(String codeType) {
        return switch (codeType) {
            case TraceCodeTypeConst.PORK -> TraceCodeTypeConst.PRODUCT_CODE_PORK;
            case TraceCodeTypeConst.GIFT -> TraceCodeTypeConst.PRODUCT_CODE_GIFT;
            default -> TraceCodeTypeConst.PRODUCT_CODE_VEG;
        };
    }

    /**
     * 按耳号反查 produce_code（猪肉链）。查不到返 null。protected 方便单测 stub。
     */
    protected String findProduceCodeByEarNo(String earNo) {
        TraceCode tc = baseMapper.selectOne(
            new LambdaQueryWrapper<TraceCode>()
                .eq(TraceCode::getPigEarNo, earNo)
                .orderByDesc(TraceCode::getId)
                .last("LIMIT 1"));
        return tc == null ? null : tc.getProduceCode();
    }

    /**
     * INSERT trace_code（protected 方便单测 verify / stub）。
     */
    protected void insertTraceCode(TraceCode traceCode) {
        baseMapper.insert(traceCode);
    }

    /**
     * INSERT trace_event（protected 方便单测 verify / stub）。
     */
    protected void insertTraceEvent(TraceEvent event) {
        traceEventMapper.insert(event);
    }

}
