package org.dromara.djs.common.validate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link BizReferenceChecker} 默认实现（基于 JdbcTemplate）。
 *
 * <p>启动期：业务模块的 {@code @PostConstruct} 调 {@link #register} 注册引用关系。
 * 运行期：主数据软删前调 {@link #isReferenced} 反查。</p>
 *
 * <p>SQL 形式（避免 N+1，单表单查）：
 * {@code SELECT 1 FROM <ref_table> WHERE <ref_col>=? AND del_flag='0' LIMIT 1}。</p>
 *
 * <p>{@code del_flag='0'} 过滤是因为 djs 业务表统一软删模型——已软删的引用记录不应阻止主数据软删。
 * 表名 / 列名是注册期传入的常量字符串（不接受用户输入），SQL 注入风险可控。</p>
 *
 * <p>反查前先经 information_schema 确认引用表存在：注册的表可能被后续迁移 DROP，
 * 缺表时跳过该条检查并 warn，避免主数据删除因裸 SQL 打到不存在的表而 500。</p>
 *
 * @author djs
 * @since BRD-EVENT-001
 */
@Slf4j
@Component
public class BizReferenceCheckerImpl implements BizReferenceChecker {

    /** sourceTable -> (refTable, refCol) 列表。 */
    private final Map<String, List<Reference>> registry = new ConcurrentHashMap<>();

    /** 已确认存在的引用表缓存（只缓存"存在"，缺表每次重查——表补建后无需重启即恢复反查）。 */
    private final Set<String> existingTables = ConcurrentHashMap.newKeySet();

    private final JdbcTemplate jdbcTemplate;

    public BizReferenceCheckerImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void register(String sourceTable, String refTable, String refCol) {
        validateIdent(sourceTable);
        validateIdent(refTable);
        validateIdent(refCol);
        Reference ref = new Reference(refTable, refCol);
        registry.compute(sourceTable, (k, list) -> {
            List<Reference> existing = list != null ? list : new CopyOnWriteArrayList<>();
            if (!existing.contains(ref)) {
                existing.add(ref);
                log.info("[BizReferenceChecker] register {} <- {}.{}", sourceTable, refTable, refCol);
            }
            return existing;
        });
    }

    @Override
    public boolean isReferenced(String sourceTable, Long id) {
        if (id == null) {
            return false;
        }
        List<Reference> refs = registry.getOrDefault(sourceTable, Collections.emptyList());
        for (Reference ref : refs) {
            if (!tableExists(ref.refTable)) {
                log.warn("[BizReferenceChecker] skip check {} <- {}.{}: ref table not exists",
                    sourceTable, ref.refTable, ref.refCol);
                continue;
            }
            String sql = "SELECT 1 FROM " + ref.refTable
                + " WHERE " + ref.refCol + "=? AND del_flag='0' LIMIT 1";
            List<Integer> rows = jdbcTemplate.queryForList(sql, Integer.class, id);
            if (!rows.isEmpty()) {
                log.info("[BizReferenceChecker] {} id={} referenced by {}.{}",
                    sourceTable, id, ref.refTable, ref.refCol);
                return true;
            }
        }
        return false;
    }

    /**
     * 引用表存在性守卫：注册的表可能被后续迁移 DROP，反查前经 information_schema 确认，
     * 缺表跳过（调用方 warn），防止裸 SQL 打到不存在的表抛 BadSqlGrammarException。
     */
    private boolean tableExists(String table) {
        if (existingTables.contains(table)) {
            return true;
        }
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
            Integer.class, table);
        boolean exists = count != null && count > 0;
        if (exists) {
            existingTables.add(table);
        }
        return exists;
    }

    /**
     * 简单标识符校验（防注入）：表名 / 列名只允许 [a-z0-9_]，长度 ≤ 64。
     */
    private void validateIdent(String name) {
        if (name == null || name.isBlank() || name.length() > 64) {
            throw new IllegalArgumentException("invalid identifier: " + name);
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_'
                || (c >= 'A' && c <= 'Z');
            if (!ok) {
                throw new IllegalArgumentException("invalid identifier: " + name);
            }
        }
    }

    /** 调试 / 测试用：返回当前注册的所有引用关系（unmodifiable）。 */
    public List<String> dumpRegistered() {
        List<String> out = new ArrayList<>();
        registry.forEach((src, refs) -> {
            for (Reference r : refs) {
                out.add(src + " <- " + r.refTable + "." + r.refCol);
            }
        });
        return out;
    }

    /** 内部记录：被引用关系。 */
    private record Reference(String refTable, String refCol) {
    }
}
