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
     *
     * @deprecated 格式 {@code P{yyyyMMdd}{seq4}} 不匹配 doc/11 §2.6 R7 产品生产编号规则
     * （{@code YYMMDD + 业态前缀(Z/G/B/H/D/L) + 4 位}）。产品生产编号统一改走 {@link #PRODUCE_NO}。
     * 枚举值保留不删（避免历史数据 / 已落地 seed 行引用炸），但不再用于新生成。
     */
    @Deprecated
    PACK_NO,

    /**
     * 产品生产编号（每日重置），格式 {@code {yyMMdd}{seq4}}。
     * 例：{@code 2605120001}。
     *
     * <p>全部打包/出库生码共用一个每日计数器（{@code daily_reset=1}，当日从 0001 起算），
     * 不分业态前缀。追溯标签「生产编码」即取此值。</p>
     *
     * <p>走 {@link IBizCodeGenerator} 的 Redisson 锁 + 序号表 UNIQUE 双保护。</p>
     */
    PRODUCE_NO,

    /**
     * 门店退货单号（每日重置），格式 {@code RET{yyyyMMdd}{seq4}}。
     * 例：{@code RET202606130001}。
     *
     * <p>D11 closing 统一治理：替代原 WMS-SHIP-001 ReturnProductServiceImpl inline
     * {@code selectMaxReturnSeqByDate + 应用层自增}，走 {@link IBizCodeGenerator}
     * 的 Redisson 锁 + 序号表 UNIQUE 双保护，与 BURN_NO / CUT_NO / BAR_NO 范式一致。</p>
     */
    RETURN_NO,

    /**
     * 出入库流水号（每日重置），格式 {@code F{yyyyMMdd}{ioCode2}{seq4}}。
     * 例：{@code F260520IN0001}。
     */
    STOCK_FLOW_NO,

    /**
     * 会员编号（终生递增），格式 {@code 1{seq4}}。例：{@code 10001}。
     * 门店会员规模有限，4 位序号（10001-19999 段）足够 V1。
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
    SUPPLIER_CODE,

    /**
     * 引种单号（每日重置），格式 {@code INT{yyyyMMdd}{seq4}}。
     * 例：{@code INT260526001}（4 位序号当日重置）。
     */
    INTRO_NO,

    /**
     * 燎毛工序业务码（每日重置），格式 {@code BURN{yyMMdd}{seq4}}。
     * 例：{@code BURN2606080001}（D9 closing Group B 集中治理，原 PigBurnRecordServiceImpl inline 实现迁入）。
     */
    BURN_NO,

    /**
     * 分割工序业务码（每日重置），格式 {@code CUT{yyMMdd}{seq4}}。
     * 例：{@code CUT2606080001}（D9 closing Group B 集中治理，原 PigCutRecordServiceImpl inline 实现迁入）。
     */
    CUT_NO,

    /**
     * 种植计划业务码（按年重置），格式 {@code PLAN-{yyyy}-{seq3}}。
     * 例：{@code PLAN-2026-001}（D9 closing Group B 集中治理，原 PlantPlanServiceImpl inline 实现迁入）。
     */
    PLAN_NO,

    /**
     * 白条业务码（每日重置），格式 {@code BAR{yyMMdd}{seq4}}。
     * 例：{@code BAR2606040001}（CROSS-FLOW-001 D10 listener 创建白条时使用）。
     * 与 BURN_NO / CUT_NO 范式一致，统一走 BizCodeService Redisson 锁 + 序号表 UNIQUE 双保护。
     */
    BAR_NO,

    /**
     * 盘点单业务码（每日重置），格式 {@code C{yyyyMMdd}{seq5}}。
     * 例：{@code C2026061300001}（WMS-STOCK-001 admin 新建盘点单时使用）。
     * 与 BURN_NO / CUT_NO / BAR_NO 范式一致，统一走 BizCodeService Redisson 锁 + 序号表 UNIQUE 双保护。
     */
    CHECK_NO
}
