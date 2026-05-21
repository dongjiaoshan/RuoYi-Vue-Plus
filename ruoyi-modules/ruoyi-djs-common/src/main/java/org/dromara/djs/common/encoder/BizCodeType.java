package org.dromara.djs.common.encoder;

/**
 * 业务编码类型枚举（SYS-INFRA-004）。
 *
 * <p>每个枚举值对应 {@code t_md_biz_code_rule.code_type} 一条规则记录，控制：</p>
 * <ul>
 *   <li>编码格式串 {@code pattern}（含 {@link BizCodeGeneratorImpl} 支持的占位符）</li>
 *   <li>是否每日重置序号 {@code daily_reset}</li>
 *   <li>固定前缀 {@code prefix} / 序号位数 {@code seq_length}</li>
 * </ul>
 *
 * <p>对应规则种子在 {@code script/sql/djs/V202605210900__SYS-INFRA-004-biz-code-rules.sql}。</p>
 *
 * @author djs
 * @since SYS-INFRA-004
 */
public enum BizCodeType {

    /**
     * 耳号简版（每日重置），格式 {@code {farmCode2}{barnCode2}{yyMM}{dailySeq4}}。
     * 例：{@code 01A1260520001}。
     */
    EAR_NO,

    /**
     * 追溯码（终生递增不重置），格式 {@code T{yyyyMMdd}{productCode2}{seq6}}。
     * 例：{@code T260520PG000001}。
     */
    TRACE_CODE,

    /**
     * 需求单号（每日重置），格式 {@code D{yyyyMMdd}{bizCode2}{seq4}}。
     * 例：{@code D260520ST0001}。
     */
    DEMAND_NO,

    /**
     * 发货单号（每日重置），格式 {@code S{yyyyMMdd}{seq4}}。
     * 例：{@code S2605200001}。
     */
    SHIP_NO,

    /**
     * 加工包装单号（每日重置），格式 {@code P{yyyyMMdd}{seq4}}。
     * 例：{@code P2605200001}。
     */
    PACK_NO,

    /**
     * 出入库流水号（每日重置），格式 {@code F{yyyyMMdd}{ioCode2}{seq4}}。
     * 例：{@code F260520IN0001}。
     */
    STOCK_FLOW_NO,

    /**
     * 人员编号（终生递增），格式 {@code M{seq4}}。例：{@code M0001}。
     * 农场员工流动有限，4 位序号上限 9999 足够 V1。
     */
    MEMBER_NO,

    /**
     * 门店编码（终生递增），格式 {@code ST{seq4}}。例：{@code ST0001}。
     */
    STORE_CODE,

    /**
     * 供应商编码（终生递增），格式 {@code G{seq4}}。例：{@code G0001}。
     * 对齐 {@code t_md_supplier.supplier_code} 注释「G0001 风格」。
     */
    SUPPLIER_CODE
}
