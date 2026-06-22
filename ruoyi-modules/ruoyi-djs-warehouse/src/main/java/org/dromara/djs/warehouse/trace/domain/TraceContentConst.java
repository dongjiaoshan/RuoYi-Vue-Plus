package org.dromara.djs.warehouse.trace.domain;

/**
 * 追溯事件类型常量（TRC-CORE-001），对齐字典 {@code djs_trace_content} + DDL
 * {@code t_warehouse_trace_event.trace_content VARCHAR(32)}。
 *
 * <p>V1 7 种事件（doc/11 §4.2 + DDL 实证）：各工序完成时 hook 调
 * {@link org.dromara.djs.warehouse.trace.service.ITraceService#recordEvent} 写入。
 * 用常量代替散字符串字面量，避免 hook 点拼错。</p>
 *
 * @author djs
 * @since TRC-CORE-001
 */
public final class TraceContentConst {

    /** 出栏上市（出栏工序）。 */
    public static final String MARKETING = "marketing";

    /** 燎毛（燎毛工序）。 */
    public static final String SINGE = "singe";

    /** 屠宰分割（分割工序）。 */
    public static final String SLAUGHTER = "slaughter";

    /** 排酸（分割排酸工序）。 */
    public static final String ACID = "acid";

    /** 入库（打包入库工序）。 */
    public static final String IN_STOCK = "in_stock";

    /** 发货（发货确认工序）。 */
    public static final String SHIP = "ship";

    /** 到店（门店签收工序）。 */
    public static final String ARRIVAL = "arrival";

    // ===== 果蔬链合成工序节点（非 trace_event 落地，veg 聚合时按上游业务表时间补造，见 fillVeg）=====

    /** 播种（果蔬链：种植起始）。 */
    public static final String SOWING = "sowing";

    /** 采收（果蔬链：采摘起始）。 */
    public static final String HARVEST = "harvest";

    /** 毛菜处理（果蔬链：毛菜处理工序）。 */
    public static final String VEG_HANDLE = "veg_handle";

    /** 打包（果蔬链：成品打包工序）。 */
    public static final String PACK = "pack";

    private TraceContentConst() {
    }
}
