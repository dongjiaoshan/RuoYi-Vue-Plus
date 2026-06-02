package org.dromara.djs.warehouse.trace.service;

/**
 * 追溯码生成 + 事件流水核心服务（TRC-CORE-001）。
 *
 * <p>本 service **无 HTTP 端点**（追溯码查询走 TRC-ADMIN-001 D14），只提供供上游工序内部调用的方法。
 * 写在 warehouse 模块（{@code org.dromara.djs.warehouse.trace}）——两张追溯表 + 所有写码 hook 都在 warehouse，
 * 避免 warehouse→store 反向依赖。</p>
 *
 * @author djs
 * @since TRC-CORE-001
 */
public interface ITraceService {

    /**
     * 生成追溯码：判业态 → {@code BizCodeType.TRACE_CODE} 生成 → INSERT trace_code → 返回 produce_code。
     *
     * <p>WMS-PACK-001 的 4 个打包入口（veg/gift/dry/celery）打包入库后调用，回填
     * {@code ProductProduction.traceCode}。按业态填链路字段（猪肉链 pig_ear_no / 果蔬链
     * plot_id 等），表无 name/weight/store_address 冗余列故不存名重。</p>
     *
     * @param productId 产品 FK（{@code t_warehouse_product_info.id}，用于判业态 belong_type）
     * @param pigEarNo  猪只耳号（猪肉链填，果蔬 / 礼盒传 null）
     * @param plotId    来源地块 FK（果蔬链填，猪肉 / 礼盒传 null）
     * @return 生成的追溯码 produce_code
     */
    String genCode(Long productId, String pigEarNo, Long plotId);

    /**
     * 写一条事件流水：INSERT trace_event（trace_content + trace_time=now + operator_id）。immutable。
     *
     * <p>各工序完成 hook 调用。{@code produceCode} 为空 → 仅 warn 日志跳过（追溯写失败不拖垮主业务，
     * 绝不抛异常）。</p>
     *
     * @param produceCode  追溯码（空 → 跳过）
     * @param traceContent 事件类型（{@link org.dromara.djs.warehouse.trace.domain.TraceContentConst} 常量）
     */
    void recordEvent(String produceCode, String traceContent);

    /**
     * 按猪只耳号反查追溯码后写事件流水（猪肉链工序便捷封装）。
     *
     * <p>出栏 / 燎毛 / 分割工序只有 {@code ear_no}、无 produce_code 上下文，本方法先按
     * {@code trace_code.pig_ear_no} 反查 produce_code 再 {@link #recordEvent}。反查不到 →
     * warn 日志跳过，**不抛异常**（V1 容错；猪肉链 trace_code 当前无生成入口，反查必落空属预期，
     * 见 reports pork genCode 缺口 raise）。</p>
     *
     * @param earNo        猪只耳号（空 → 跳过）
     * @param traceContent 事件类型
     */
    void recordEventByEarNo(String earNo, String traceContent);

}
