package org.dromara.djs.common.encoder;

import java.util.List;
import java.util.Map;

/**
 * 业务编码生成器统一入口（SYS-INFRA-004）。
 *
 * <p>所有需要"按规则拼接 + 全局唯一 + 高并发安全"的业务编码（耳号 / 追溯码 / 需求单号 / 发货单号 /
 * 加工包装单号 / 出入库流水号）都走本接口，避免每个业务模块各写一遍格式化 + 锁。</p>
 *
 * <h3>调用契约</h3>
 * <ol>
 *   <li>{@link #generate(BizCodeType, Map)} 单条生成（典型场景：单头猪只引种 / 单笔出库流水）</li>
 *   <li>{@link #generateBatch(BizCodeType, Map, int)} 批量生成（典型场景：分娩仔猪一次贴 N 个耳号），
 *       内部一次锁 + 一次步长 N 的原子 INCRBY，性能远好于循环调用 generate</li>
 * </ol>
 *
 * <h3>context 占位符（实现见 {@link BizCodeGeneratorImpl#format}）</h3>
 * <p>{@code pattern} 支持的占位符：</p>
 * <ul>
 *   <li>{@code {farmCode2}}    — 农场编码 2 位，从 {@code context.farmCode} 取；V1 默认 "01"</li>
 *   <li>{@code {barnCode2}}    — 栋舍编码 2 位，从 {@code context.barnCode} 取</li>
 *   <li>{@code {productCode2}} — 产品类型 2 位，从 {@code context.productCode} 取（追溯码用）</li>
 *   <li>{@code {bizCode2}}     — 业态 2 位（餐饮 RT / 商超 ST / 农贸 NM ...），从 {@code context.bizCode} 取</li>
 *   <li>{@code {ioCode2}}      — 出入库方向，从 {@code context.ioCode} 取（IN / OT）</li>
 *   <li>{@code {yyMM}}         — 当前年月 4 位</li>
 *   <li>{@code {yyyyMMdd}}     — 当前年月日 8 位</li>
 *   <li>{@code {dailySeq4}}    — 当日序号补零 4 位（按 daily_reset 重置）</li>
 *   <li>{@code {seq4}}         — 序号补零 4 位（由 {@code daily_reset} 决定是否每日重置）</li>
 *   <li>{@code {seq6}}         — 序号补零 6 位（同上）</li>
 * </ul>
 *
 * @author djs
 * @since SYS-INFRA-004
 */
public interface IBizCodeGenerator {

    /**
     * 生成单条业务编码。
     *
     * @param type    编码类型
     * @param context 上下文参数（按 pattern 占位符所需字段提供，缺字段时占位符原样保留并 warn 日志）
     * @return 拼装后的唯一编码
     */
    String generate(BizCodeType type, Map<String, Object> context);

    /**
     * 批量生成 N 个连续编码（一次锁 + 一次步长 N 的 INCRBY）。
     *
     * @param type    编码类型
     * @param context 上下文参数
     * @param count   生成数量；必须 &gt; 0
     * @return 长度为 count 的编码列表，序号严格连续
     */
    List<String> generateBatch(BizCodeType type, Map<String, Object> context, int count);
}
