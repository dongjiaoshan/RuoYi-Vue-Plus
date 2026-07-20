package org.dromara.djs.warehouse.trace.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 追溯码详情聚合 VO（TRC-ADMIN-001，admin only）。
 *
 * <p>继承 {@link TraceCodeListVo} 的主表全字段 + JOIN 展示名，再内嵌完整事件时间轴
 * {@code events}（{@code t_warehouse_trace_event}，按 {@code trace_time} 升序，7-9 节点
 * driven by 实际 event 行，前端不硬编码节点数）。批量打印 {@code batchDetail} 也复用本 VO
 * （前端 jsPDF 直接取携带的展示字段）。</p>
 *
 * @author djs
 * @since TRC-ADMIN-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TraceCodeDetailVo extends TraceCodeListVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 完整事件时间轴（按 trace_time 升序）。追溯链不可篡改，admin 端只读。
     */
    private List<TraceEventVo> events;

}
