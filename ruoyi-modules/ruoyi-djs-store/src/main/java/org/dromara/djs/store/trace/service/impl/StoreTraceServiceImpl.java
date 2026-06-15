package org.dromara.djs.store.trace.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.vo.PigAvailableVo;
import org.dromara.djs.breed.core.service.IPigQueryService;
import org.dromara.djs.store.trace.domain.bo.StoreTraceOnsiteBo;
import org.dromara.djs.store.trace.domain.vo.TraceablePigVo;
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

    private final IPigQueryService pigQueryService;
    private final ITraceService traceService;
    private final ITraceCodeAdminService traceCodeAdminService;
    private final BarInfoMapper barInfoMapper;

    /**
     * 可追溯 picker = 当天确认收货白条（FIX-STORE-TRACE-BAR-001 测试问题 158）。
     *
     * <p>口径改为「{@code t_warehouse_bar_info.status='in_stock'} 且 {@code DATE(in_time)=CURDATE()}」
     * 的白条（含外购）：先按白条过滤（warehouse {@link BarInfoMapper}），再按白条耳号 enrich 猪只
     * 性别 / 品种品系 / 日龄（breed {@link IPigQueryService#listPigInfoByEarNos}，additive 只读方法，
     * <b>不</b>改 breed 共享分页选猪 mapper，避免跨域污染）。</p>
     *
     * <p>外购白条无耳号或耳号无猪档案时，{@code pigSex/pigBreedLabel/ageDays} 留 null；
     * chip 主显值 {@code earNo} 为空时回退用 {@code barId}（保证选择器有可点项）。
     * 当天无白条入库 → 空结果，属正常态（前端显示「暂无当天确认收货白条」），非 bug。</p>
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
