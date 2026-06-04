package org.dromara.djs.warehouse.trace.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.trace.domain.query.TraceCodeQuery;
import org.dromara.djs.warehouse.trace.domain.vo.TraceCodeDetailVo;
import org.dromara.djs.warehouse.trace.domain.vo.TraceCodeListVo;
import org.dromara.djs.warehouse.trace.domain.vo.TraceEventVo;

import java.util.List;

/**
 * 追溯码管理 admin Service（TRC-ADMIN-001，admin only 无 mp）。
 *
 * <p><b>追溯链不可篡改</b>：本接口纯只读——列表 / 详情 / 事件链 / 导出 / 批量取数，
 * <b>无任何</b>追溯码或事件的增 / 删 / 改方法（doc/10 §F-TRC-01）。</p>
 *
 * @author djs
 * @since TRC-ADMIN-001
 */
public interface ITraceCodeAdminService {

    /**
     * 追溯码分页列表。按 {@code codeType}（pork/veg/gift）/ {@code produceCode} / {@code productName}
     * （JOIN product 后过滤）/ {@code pigEarNo} / 生成时间范围过滤；JOIN 出 product / store / farm / plot 展示名。
     */
    TableDataInfo<TraceCodeListVo> queryPage(TraceCodeQuery query, PageQuery pageQuery);

    /**
     * 追溯码详情：主表全字段 + JOIN 关联 + 完整事件时间轴（按 trace_time 升序）。
     */
    TraceCodeDetailVo getDetail(Long id);

    /**
     * 按 {@code produceCode} 查事件流水（trace_time 升序，供前端单独刷新时间轴）。
     */
    List<TraceEventVo> getEvents(String produceCode);

    /**
     * 不分页全量列表，供 Excel 导出。
     */
    List<TraceCodeListVo> export(TraceCodeQuery query);

    /**
     * 按勾选 id 批量拉详情（含事件），供前端 jsPDF 批量出码一次取数。
     */
    List<TraceCodeDetailVo> batchDetail(List<Long> ids);

}
