package org.dromara.djs.warehouse.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.trace.domain.TraceCode;
import org.dromara.djs.warehouse.trace.domain.TraceCodeTypeConst;
import org.dromara.djs.warehouse.trace.domain.TraceEvent;
import org.dromara.djs.warehouse.trace.mapper.TraceCodeMapper;
import org.dromara.djs.warehouse.trace.mapper.TraceEventMapper;
import org.dromara.djs.warehouse.trace.service.ITraceService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
 * <h3>猪肉链 genCode 缺口（V1 已知）</h3>
 * <p>WMS-PACK-001 的 4 个打包入口都是果蔬 / 礼盒业态，**无 pork pack 入口**，故 pork code_type 的
 * trace_code 当前**无调用方生成**；出栏 / 燎毛 / 分割工序只 recordEventByEarNo，反查 trace_code 必落空走
 * warn 跳过。genCode 仍实现 pork 分支（供未来调用），pork 链生成点待 Kevin / product 定（reports raise）。</p>
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

    private final TraceEventMapper traceEventMapper;
    private final ProductInfoMapper productInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;

    public TraceServiceImpl(TraceCodeMapper baseMapper,
                            TraceEventMapper traceEventMapper,
                            ProductInfoMapper productInfoMapper,
                            IBizCodeGenerator bizCodeGenerator) {
        super(baseMapper);
        this.traceEventMapper = traceEventMapper;
        this.productInfoMapper = productInfoMapper;
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

        log.info("[TRC-CORE-001] genCode produceCode={} codeType={} productId={} belongType={}",
            produceCode, codeType, productId, belongType);
        return produceCode;
    }

    @Override
    public void recordEvent(String produceCode, String traceContent) {
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
            insertTraceEvent(event);
            log.info("[TRC-CORE-001] recordEvent produceCode={} content={}", produceCode, traceContent);
        } catch (Exception e) {
            // 追溯写失败绝不拖垮主业务工序 → 仅 warn，不抛
            log.warn("[TRC-CORE-001] recordEvent failed (skipped) produceCode={} content={}: {}",
                produceCode, traceContent, e.getMessage());
        }
    }

    @Override
    public void recordEventByEarNo(String earNo, String traceContent) {
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
        recordEvent(produceCode, traceContent);
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
