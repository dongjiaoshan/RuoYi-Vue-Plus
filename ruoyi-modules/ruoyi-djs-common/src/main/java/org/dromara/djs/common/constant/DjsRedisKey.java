package org.dromara.djs.common.constant;

/**
 * djs 业务 Redis Key 命名规范常量。
 *
 * <h3>关键约束</h3>
 * <ul>
 *   <li>所有跨"农场"维度隔离的 key 必须含 {@code {farmId}} 段（对应 sys_user.current_farm_id /
 *       业务表 tenant_id）。V1 全部用 "1001"，V2 启用多农场时不会出现跨农场跳号 / 缓存击穿。</li>
 *   <li>命名格式：{@code djs:<域>:<resource>:<farmId>:<biz>}，冒号分隔，全小写。</li>
 *   <li>{farmId} 段固定放在 "资源标识" 之后、"业务标识" 之前，便于按农场批量 SCAN/DEL。</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 *   String key = DjsRedisKey.EAR_TAG_SEQ.formatted(farmId, "260403");
 *   // → "djs:breed:ear_tag_seq:1001:260403"
 * </pre>
 *
 * <p>参考：doc/05 §4.3.1 / §6.4 "业务编码 Redis key 带 farmId 段"。</p>
 *
 * @author djs
 * @since SYS-INFRA-007
 */
public final class DjsRedisKey {

    private DjsRedisKey() {
    }

    /** 全局前缀。 */
    public static final String DJS_PREFIX = "djs:";

    // ---------------- 业务编码序列号（SYS-INFRA-004 / 005 会实际使用） ----------------

    /**
     * 耳号简版序列（按"农场 + 出生日"分桶递增）。
     * <p>format args: farmId, yyMMdd</p>
     * 例：{@code djs:breed:ear_tag_seq:1001:260403}
     */
    public static final String EAR_TAG_SEQ = DJS_PREFIX + "breed:ear_tag_seq:%s:%s";

    /**
     * 出入库流水号序列（按"农场 + yyyyMMdd"分桶）。
     * <p>format args: farmId, yyyyMMdd</p>
     */
    public static final String FLOW_NO_SEQ = DJS_PREFIX + "wms:flow_no_seq:%s:%s";

    /**
     * 加工生产编码序列（按"农场 + yyMMdd"分桶）。
     * <p>format args: farmId, yyMMdd</p>
     */
    public static final String PRODUCE_NO_SEQ = DJS_PREFIX + "wms:produce_no_seq:%s:%s";

    /**
     * 供应商编码序列（按农场分桶，全局递增至 4 位数字）。
     * <p>format args: farmId</p>
     */
    public static final String SUPPLIER_NO_SEQ = DJS_PREFIX + "md:supplier_no_seq:%s";

    /**
     * 会员编码序列（按农场分桶，全局递增至 5 位数字）。
     * <p>format args: farmId</p>
     */
    public static final String MEMBER_NO_SEQ = DJS_PREFIX + "str:member_no_seq:%s";

    /**
     * 业务编码生成器分布式锁（SYS-INFRA-004 通用编码生成器，所有编码类型共用一把锁桶）。
     * <p>锁的最细粒度由 {@code (tenantId, codeType, seqDate)} 组成，已包含农场维度。
     * 锁名结构：{@code djs:bizcode:lock:<tenantId>:<codeType>:<seqDate>}。</p>
     * <p>format args: 单个完整 lockKey 后缀（由调用方拼接 tenantId/codeType/seqDate）</p>
     */
    public static final String BIZ_CODE_LOCK = DJS_PREFIX + "bizcode:lock:%s";
}
