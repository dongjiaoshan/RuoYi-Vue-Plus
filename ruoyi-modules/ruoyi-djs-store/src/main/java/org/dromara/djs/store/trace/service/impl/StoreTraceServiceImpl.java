package org.dromara.djs.store.trace.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.core.domain.vo.PigAvailableVo;
import org.dromara.djs.breed.core.service.IPigQueryService;
import org.dromara.djs.store.trace.domain.bo.StoreTraceOnsiteBo;
import org.dromara.djs.store.trace.domain.vo.TraceablePigVo;
import org.dromara.djs.store.trace.service.IStoreTraceService;
import org.dromara.djs.warehouse.trace.service.ITraceService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 门店现场生码服务实现（STORE-TRACE-ONSITE-001）。
 *
 * <p>纯 orchestration：picker 委托养殖 {@link IPigQueryService}，生码委托仓库 {@link ITraceService}。
 * 不持有任何 trace / pig 表 mapper（trace 表归 warehouse，pig 表归 breed），门店模块只编排不直读。</p>
 *
 * @author djs
 * @since STORE-TRACE-ONSITE-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreTraceServiceImpl implements IStoreTraceService {

    private final IPigQueryService pigQueryService;
    private final ITraceService traceService;

    @Override
    public TableDataInfo<TraceablePigVo> listTraceablePigs(PageQuery pageQuery) {
        TableDataInfo<PigAvailableVo> src = pigQueryService.listTraceablePigs(pageQuery);
        List<PigAvailableVo> srcRows = src.getRows();
        // 显式字段映射（养殖 PigAvailableVo → 门店 TraceablePigVo，同名字段 earNo/pigSex/pigBreedLabel/ageDays）。
        // 不用 MapstructUtils.convert：两域 VO 间无 @AutoMapper 注册，linpeilie 找不到 converter 会抛 ConvertException。
        List<TraceablePigVo> rows = (srcRows == null ? List.<PigAvailableVo>of() : srcRows).stream()
            .map(p -> {
                TraceablePigVo v = new TraceablePigVo();
                v.setEarNo(p.getEarNo());
                v.setPigSex(p.getPigSex());
                v.setPigBreedLabel(p.getPigBreedLabel());
                v.setAgeDays(p.getAgeDays());
                return v;
            })
            .toList();
        TableDataInfo<TraceablePigVo> out = TableDataInfo.build();
        out.setRows(rows);
        out.setTotal(src.getTotal());
        return out;
    }

    @Override
    public String genOnsiteCode(StoreTraceOnsiteBo bo) {
        String produceCode = traceService.genPorkOnsiteCode(bo.getEarNo(), bo.getCutLabel(), bo.getWeight());
        log.info("[STORE-TRACE-ONSITE-001] store onsite gen produceCode={} earNo={} cut={}",
            produceCode, bo.getEarNo(), bo.getCutLabel());
        return produceCode;
    }
}
