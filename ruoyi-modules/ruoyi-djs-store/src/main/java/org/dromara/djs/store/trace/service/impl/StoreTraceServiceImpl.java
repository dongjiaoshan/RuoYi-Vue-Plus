package org.dromara.djs.store.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.vo.PigAvailableVo;
import org.dromara.djs.breed.core.service.IPigQueryService;
import org.dromara.djs.store.trace.domain.bo.StoreTraceOnsiteBo;
import org.dromara.djs.store.trace.domain.vo.StorePackProductVo;
import org.dromara.djs.store.trace.domain.vo.TraceablePigVo;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.store.trace.service.IStoreTraceService;
import org.dromara.djs.warehouse.cross.domain.vo.TodayBarVo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.trace.domain.query.TraceCodeQuery;
import org.dromara.djs.warehouse.trace.domain.vo.TraceCodeDetailVo;
import org.dromara.djs.warehouse.trace.domain.vo.TraceCodeListVo;
import org.dromara.djs.warehouse.trace.service.ITraceCodeAdminService;
import org.dromara.djs.warehouse.trace.service.ITraceService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 门店现场生码服务实现（STORE-TRACE-ONSITE-001）。
 *
 * <p>纯 orchestration：picker 委托养殖 {@link IPigQueryService}，生码委托仓库 {@link ITraceService}，
 * 已生码列表 / 详情 / 补打取数委托仓库 {@link ITraceCodeAdminService}。
 * 不持有任何 trace / pig 表 mapper（trace 表归 warehouse，pig 表归 breed），门店模块只编排不直读。</p>
 *
 * @author djs
 * @since STORE-TRACE-ONSITE-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreTraceServiceImpl implements IStoreTraceService {

    /** 门店追溯码恒为猪肉业态（已生码列表 / 详情口径固定 pork）。 */
    private static final String CODE_TYPE_PORK = "pork";
    /** 门店猪肉打包字典：value=产品业务码，命中产品作为打包产品的原材料（docx 取数第①步）。 */
    private static final String DICT_PORK_RETURN_PRODUCT = "djs_pork_return_product";
    /** 生产车间「门店打包间」值（docx 取数第②步：product_workshop=5）。 */
    private static final Integer WORKSHOP_STORE_PACK = 5;
    /** 猪肉业态。 */
    private static final String BELONG_TYPE_PORK = "pork";

    private final IPigQueryService pigQueryService;
    private final ITraceService traceService;
    private final ITraceCodeAdminService traceCodeAdminService;
    private final BarInfoMapper barInfoMapper;
    private final ProductInfoMapper productInfoMapper;
    private final DictService dictService;

    /**
     * 可追溯 picker = 当天入库白条（FIX-STORE-TRACE-BAR-001 测试问题 158）。
     *
     * <p>口径：「{@code t_warehouse_bar_info.status='in_stock'} 且 {@code DATE(in_time)=CURDATE()}」
     * 的白条（含外购）：先按白条过滤（warehouse {@link BarInfoMapper}），再按白条耳号 enrich 猪只
     * 性别 / 品种品系 / 日龄（breed {@link IPigQueryService#listPigInfoByEarNos}，additive 只读方法，
     * <b>不</b>改 breed 共享分页选猪 mapper，避免跨域污染）。</p>
     *
     * <p><b>「今日发货到门店白条」口径待补（S-C c1 链路评估结论）</b>：方案理想口径是
     * 「shipment⋈bar（product 粒度，shipDate=今日 + storeId 不空）」，但当前 schema 下 shipment 到 bar
     * 的唯一桥接是 {@code t_warehouse_product_production.whiteBarId/earNo}（冗余 FK），且该链路仅在
     * <b>分割打包</b>路径回填——白条<b>整只</b>发货（{@code submitWhiteBarOut → updateStatusToShipOut}）
     * 不保证产生带 earNo 的 product_production 行，现网 earNo/whiteBarId 多为 NULL。强行改成
     * {@code bar ⋈ product_production ⋈ shipment} 的 INNER JOIN 会因 FK 大面积 NULL 而 picker 取空 /
     * 漏白条，比现状「当天 in_stock 白条」更差。故 c1 轻量方案<b>保留当天入库白条口径</b>，
     * shipment⋈bar 干净链路待 product_production.earNo/whiteBarId 回填完善后再切（见 blockers「链路待补」）。</p>
     *
     * <p>外购白条无耳号或耳号无猪档案时，{@code pigSex/pigBreedLabel/ageDays} 留 null；
     * chip 主显值 {@code earNo} 为空时回退用 {@code barId}（保证选择器有可点项）。
     * 当天无白条入库 → 空结果，属正常态（前端显示「暂无当天可追溯白条」），非 bug。</p>
     */
    @Override
    public TableDataInfo<TraceablePigVo> listTraceablePigs(PageQuery pageQuery) {
        PageQuery pq = pageQuery != null ? pageQuery : new PageQuery(1, 10);
        IPage<TodayBarVo> barPage = barInfoMapper.selectTodayInStockBarPage(pq.build());
        List<TodayBarVo> bars = barPage.getRecords();
        if (bars == null || bars.isEmpty()) {
            TableDataInfo<TraceablePigVo> empty = TableDataInfo.build();
            empty.setRows(List.of());
            empty.setTotal(barPage.getTotal());
            return empty;
        }

        // 按白条耳号批量 enrich 猪只信息（earNo → PigAvailableVo），additive 跨域只读
        Set<String> earNos = bars.stream()
            .map(TodayBarVo::getEarNo)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, PigAvailableVo> pigByEarNo = earNos.isEmpty() ? Map.of()
            : pigQueryService.listPigInfoByEarNos(earNos).stream()
                .filter(p -> StringUtils.isNotBlank(p.getEarNo()))
                .collect(Collectors.toMap(PigAvailableVo::getEarNo, Function.identity(), (a, b) -> a));

        List<TraceablePigVo> rows = bars.stream()
            .filter(Objects::nonNull)
            .map(bar -> {
                TraceablePigVo v = new TraceablePigVo();
                // chip 主键：耳号优先，外购无耳号时回退白条编号，保证选择器有可点值
                String chipKey = StringUtils.isNotBlank(bar.getEarNo()) ? bar.getEarNo() : bar.getBarId();
                v.setEarNo(chipKey);
                PigAvailableVo pig = pigByEarNo.get(bar.getEarNo());
                if (pig != null) {
                    v.setPigSex(pig.getPigSex());
                    v.setPigBreedLabel(pig.getPigBreedLabel());
                    v.setAgeDays(pig.getAgeDays());
                }
                return v;
            })
            .toList();

        TableDataInfo<TraceablePigVo> out = TableDataInfo.build();
        out.setRows(rows);
        out.setTotal(barPage.getTotal());
        return out;
    }

    /**
     * 门店现场按需生码（S-C c1 方案：部位字典驱动，<b>不</b>扣白条库存）。
     *
     * <p>部位卡口径：{@code bo.cutLabel} 来自字典 {@code djs_pork_cut_product}（5 部位），生码委托仓库域
     * {@link ITraceService#genPorkOnsiteCode}，与门店打包间（{@code djs_product_workshop=5}）口径一致——
     * 门店打包间属猪肉处理点，现场对当天入库白条按部位生追溯码，<b>纯生码不联动库存出入</b>。</p>
     *
     * <p><b>c2 不在本轮（保留现状）</b>：客户若要「打包扣白条库存 / 部位升级为 product 驱动」（即把 5 部位
     * seed 成 product、PorkTracePanel 改产品驱动 + 生码同步扣 {@code t_warehouse_bar_info} 库存）才上 c2，
     * 本轮仅保留 djs_pork_cut_product 字典驱动 + {@code genPorkOnsiteCode} 现状，不改 product 驱动。</p>
     */
    /**
     * 门店猪肉打包可选产品（docx 两步取数）：① 字典 {@code djs_pork_return_product} value(业务码) → 产品 id 集；
     * ② {@code product_workshop=5（门店打包间）} 且 {@code product_material ∈ id 集} 的猪肉产品。
     *
     * <p>空字典 / 无 workshop=5 产品 → 空 List（前端 PorkTracePanel 回退用部位字典 {@code djs_pork_cut_product}）。</p>
     */
    @Override
    public List<StorePackProductVo> listPackProducts() {
        // ① 字典业务码 → 产品 id 集（原材料/白条 id，门店打包产品 product_material 指向它们）
        Map<String, String> dict = dictService.getAllDictByDictType(DICT_PORK_RETURN_PRODUCT);
        if (dict == null || dict.isEmpty()) {
            log.warn("[STORE-TRACE-PACK] 字典 {} 为空，门店打包产品回退为空", DICT_PORK_RETURN_PRODUCT);
            return List.of();
        }
        List<String> codes = dict.keySet().stream().filter(StringUtils::isNotBlank).distinct().toList();
        List<Long> materialIds = codes.isEmpty() ? List.of()
            : productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                    .in(ProductInfo::getProductId, codes).select(ProductInfo::getId))
                .stream().map(ProductInfo::getId).filter(Objects::nonNull).distinct().toList();
        if (materialIds.isEmpty()) {
            return List.of();
        }
        // ② workshop=5 + belong_type=pork + product_material ∈ id 集 的猪肉产品
        return productInfoMapper.selectList(new LambdaQueryWrapper<ProductInfo>()
                .eq(ProductInfo::getProductWorkshop, WORKSHOP_STORE_PACK)
                .eq(ProductInfo::getBelongType, BELONG_TYPE_PORK)
                .in(ProductInfo::getProductMaterial, materialIds))
            .stream().map(p -> {
                StorePackProductVo vo = new StorePackProductVo();
                vo.setProductId(p.getId());
                vo.setProductCode(p.getProductId());
                vo.setProductName(p.getProductName());
                vo.setProductSpec(p.getProductSpec());
                vo.setProductThumb(p.getProductThumb());
                vo.setImageOssId(p.getImageOssId());
                return vo;
            }).toList();
    }

    @Override
    public String genOnsiteCode(StoreTraceOnsiteBo bo) {
        String produceCode = traceService.genPorkOnsiteCode(bo.getEarNo(), bo.getCutLabel(), bo.getWeight());
        log.info("[STORE-TRACE-ONSITE-001] store onsite gen produceCode={} earNo={} cut={}",
            produceCode, bo.getEarNo(), bo.getCutLabel());
        return produceCode;
    }

    @Override
    public TableDataInfo<TraceCodeListVo> listPorkTrace(TraceCodeQuery query, PageQuery pageQuery) {
        TraceCodeQuery q = query == null ? new TraceCodeQuery() : query;
        // 门店端恒 pork，忽略前端可能传入的其它 codeType
        q.setCodeType(CODE_TYPE_PORK);
        return traceCodeAdminService.queryPage(q, pageQuery);
    }

    @Override
    public TraceCodeDetailVo getPorkTraceDetail(Long id) {
        return traceCodeAdminService.getDetail(id);
    }

    @Override
    public List<TraceCodeDetailVo> batchPorkTraceDetail(List<Long> ids) {
        return traceCodeAdminService.batchDetail(ids);
    }
}
