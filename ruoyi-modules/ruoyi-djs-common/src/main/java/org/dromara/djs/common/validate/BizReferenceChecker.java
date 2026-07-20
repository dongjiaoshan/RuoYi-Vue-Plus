package org.dromara.djs.common.validate;

/**
 * 通用业务引用校验服务（D02 SYS-MD-001 _open-issues #12 触发，D5 BRD-EVENT-001 落地）。
 *
 * <p>用法：主数据模块（{@code Supplier / Store / Person / Breed / Medicine / ...}）的
 * {@code deleteWithValidByIds} 软删前调 {@link #isReferenced} 反查是否被业务事件表引用，
 * 命中则抛 {@code ServiceException} 阻止软删（避免业务侧出现"主数据 detail 不见但流水还指着"
 * 的悬空引用）。</p>
 *
 * <p>各业务模块启动时（{@code @PostConstruct} 或 {@code @EventListener(ApplicationStartedEvent)}）
 * 显式调 {@link #register} 注册自己的引用关系，例：</p>
 *
 * <pre>{@code
 *   // BRD-EVENT-001 PigIntroServiceImpl#postConstruct
 *   bizReferenceChecker.register("t_md_supplier", "t_farm_pig_introduce", "supplier_id");
 *   bizReferenceChecker.register("t_md_supplier", "t_farm_pig_info", "supplier_id");
 * }</pre>
 *
 * <p>实现侧（{@code BizReferenceCheckerImpl}）基于 {@code JdbcTemplate} 走
 * {@code SELECT EXISTS(SELECT 1 FROM <ref_table> WHERE <ref_col>=? AND del_flag='0' LIMIT 1)}。
 * 不走 MP 是为了避开多租户拦截器（删主数据时本身就在租户上下文里，无需重复 inject）。</p>
 *
 * <p>线程安全：{@code register} 仅在启动期调用一次（{@code ConcurrentMap} 兜底）；
 * {@code isReferenced} 高频读，{@link java.util.concurrent.ConcurrentMap#get} 无锁。</p>
 *
 * @author djs
 * @since BRD-EVENT-001
 */
public interface BizReferenceChecker {

    /**
     * 反查 {@code sourceTable.id} 是否被任何已注册的下游 {@code (refTable, refCol)} 引用。
     *
     * @param sourceTable 主数据表名（如 {@code t_md_supplier}）
     * @param id          主键 ID
     * @return {@code true}=有引用（应阻止软删）；{@code false}=无引用
     */
    boolean isReferenced(String sourceTable, Long id);

    /**
     * 注册一条引用关系（{@code sourceTable.id ← refTable.refCol}）。
     *
     * <p>幂等：同一 (source, ref, col) 重复注册不会抛错也不会重复存储。</p>
     *
     * @param sourceTable 被引用的主数据表
     * @param refTable    业务事件 / 流水表
     * @param refCol      业务表中存储外键的列名
     */
    void register(String sourceTable, String refTable, String refCol);
}
