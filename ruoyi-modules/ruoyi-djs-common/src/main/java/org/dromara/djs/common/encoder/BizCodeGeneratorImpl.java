package org.dromara.djs.common.encoder;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.constant.DjsRedisKey;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * {@link IBizCodeGenerator} 默认实现（SYS-INFRA-004）。
 *
 * <h3>并发安全两道防线</h3>
 * <ol>
 *   <li><b>Redisson 分布式锁</b>（{@link DjsRedisKey#BIZ_CODE_LOCK}）—— 同一
 *       {@code (tenantId, codeType, seqDate)} 串行化序号分配，避免毫秒级并发拿到同一序号。</li>
 *   <li><b>序号表 UPDATE 兜底</b>—— MySQL 行级锁 {@code current_seq = current_seq + step} 是原子的；
 *       结合表上 {@code UNIQUE(tenant_id, code_type, seq_date, del_unique)}，
 *       即使分布式锁失效（Redis 故障）也不会写入重复号。</li>
 * </ol>
 *
 * <h3>序号行冷启动</h3>
 * <p>第一次某 {@code (codeType, seqDate)} 取号时表无对应行，按下列流程初始化：</p>
 * <ol>
 *   <li>{@code UPDATE current_seq + step} 返回 0 行 → 行不存在</li>
 *   <li>{@code INSERT current_seq = step}；如撞 UNIQUE（其他进程已抢先 INSERT）→ 捕 {@link DuplicateKeyException}</li>
 *   <li>重做 {@code UPDATE current_seq + step}</li>
 * </ol>
 *
 * @author djs
 * @since SYS-INFRA-004
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizCodeGeneratorImpl implements IBizCodeGenerator {

    /**
     * 默认农场编码（V1 单农场场景），对应 tenant_id "1001"。
     */
    private static final String DEFAULT_FARM_CODE = "01";

    /**
     * 锁等待超时（秒）—— 单条生成的预算。
     */
    private static final long LOCK_WAIT_SECONDS = 5L;

    /**
     * 批量生成锁等待超时（秒）—— 批量生成内部只多了一次 INCRBY，预算翻倍即可。
     */
    private static final long LOCK_WAIT_SECONDS_BATCH = 10L;

    /**
     * 锁持有超时（秒）—— 防止节点崩溃导致锁永不释放。
     */
    private static final long LOCK_LEASE_SECONDS = 30L;

    private final BizCodeRuleMapper ruleMapper;

    private final BizCodeSequenceMapper sequenceMapper;

    private final RedissonClient redissonClient;

    @Override
    public String generate(BizCodeType type, Map<String, Object> context) {
        List<String> batch = doGenerate(type, context, 1, LOCK_WAIT_SECONDS);
        return batch.get(0);
    }

    @Override
    public List<String> generateBatch(BizCodeType type, Map<String, Object> context, int count) {
        if (count <= 0) {
            throw new ServiceException("批量生成数量必须大于 0，实际：" + count);
        }
        return doGenerate(type, context, count, LOCK_WAIT_SECONDS_BATCH);
    }

    /**
     * 单条 + 批量统一入口：取规则 → 算 seqDate → 锁 → 分配序号 → 拼 N 个字符串。
     */
    private List<String> doGenerate(BizCodeType type, Map<String, Object> context, int count, long lockWaitSeconds) {
        BizCodeRule rule = loadRule(type);
        Map<String, Object> safeContext = context == null ? Map.of() : context;
        String seqDate = resolveSeqDate(rule, safeContext);

        String lockKey = String.format(DjsRedisKey.BIZ_CODE_LOCK,
            rule.getTenantId() + ":" + rule.getCodeType() + ":" + seqDate);
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            locked = lock.tryLock(lockWaitSeconds, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new ServiceException("业务编码生成器抢锁超时：" + lockKey);
            }
            long endSeq = allocateSequence(rule, seqDate, count);
            long startSeq = endSeq - count + 1;
            List<String> codes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                codes.add(format(rule, safeContext, startSeq + i));
            }
            return codes;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("业务编码生成被中断：" + type.name());
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 取规则；不存在或停用时抛业务异常。
     */
    private BizCodeRule loadRule(BizCodeType type) {
        BizCodeRule rule = ruleMapper.selectOne(
            new LambdaQueryWrapper<BizCodeRule>()
                .eq(BizCodeRule::getCodeType, type.name())
                .last("LIMIT 1"));
        if (rule == null) {
            throw new ServiceException("业务编码规则未配置：" + type.name());
        }
        if (!"0".equals(rule.getStatus())) {
            throw new ServiceException("业务编码规则已停用：" + type.name());
        }
        return rule;
    }

    /**
     * 计算序号周期串：
     * <ul>
     *   <li>{@code daily_reset = 1} → {@code yyyyMMdd}（每日重置）</li>
     *   <li>{@code daily_reset = 2} → {@code yyyy}（按年重置，PLAN_NO 使用）</li>
     *   <li>{@code daily_reset = 3} → {@code yyMMdd + context.prefix}（每日重置 + 按业态前缀分桶，
     *       PRODUCE_NO 使用：每业态前缀当日各自独立从 0001 起算）</li>
     *   <li>其他（含 0 / null）→ {@code ""}（终生递增）</li>
     * </ul>
     */
    private String resolveSeqDate(BizCodeRule rule, Map<String, Object> context) {
        Integer dailyReset = rule.getDailyReset();
        if (dailyReset == null) {
            return "";
        }
        if (dailyReset == 1) {
            return LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        }
        if (dailyReset == 2) {
            return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));
        }
        if (dailyReset == 3) {
            String yyMMdd = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
            Object prefix = context.get("prefix");
            if (prefix == null) {
                throw new ServiceException(
                    "业务编码规则 " + rule.getCodeType() + " 需要 context.prefix（daily_reset=3 按前缀分桶）");
            }
            return yyMMdd + prefix;
        }
        return "";
    }

    /**
     * 分配序号区间，返回区间末尾值（end = start + count - 1）。
     *
     * <p>实现流程见类 javadoc"序号行冷启动"段。</p>
     */
    private long allocateSequence(BizCodeRule rule, String seqDate, int count) {
        String codeType = rule.getCodeType();
        // 1) 尝试原子递增
        int affected = sequenceMapper.incrementSeq(codeType, seqDate, count);
        if (affected == 0) {
            // 2) 行不存在 → 初始化
            BizCodeSequence init = new BizCodeSequence();
            init.setCodeType(codeType);
            init.setSeqDate(seqDate);
            init.setCurrentSeq((long) count);
            try {
                sequenceMapper.insert(init);
                // INSERT 成功，本次序号就是末尾值 count
                return count;
            } catch (DuplicateKeyException e) {
                // 3) 撞 UNIQUE = 其他进程已抢先 INSERT，重做 UPDATE
                affected = sequenceMapper.incrementSeq(codeType, seqDate, count);
                if (affected == 0) {
                    // 极少见：被其他进程软删除，明确报错
                    throw new ServiceException(
                        "业务编码序号行 INSERT 后立即被删除：" + codeType + " / " + seqDate);
                }
            }
        }
        Long current = sequenceMapper.selectCurrentSeq(codeType, seqDate);
        if (current == null) {
            throw new ServiceException(
                "业务编码序号行读取失败：" + codeType + " / " + seqDate);
        }
        return current;
    }

    /**
     * 把 {@code pattern} 里的占位符按 context + seq 拼成最终字符串。
     *
     * <p>缺字段时占位符原样保留并 warn 日志，避免运行时抛异常打断业务（业务层 review 时能看到 warn）。</p>
     * <p>只在 pattern 里实际出现的占位符才会触发 context 字段查找，避免无关 WARN 噪音。</p>
     */
    String format(BizCodeRule rule, Map<String, Object> context, long seq) {
        String result = rule.getPattern();
        LocalDate today = LocalDate.now();

        // 日期占位符
        if (result.contains("{yyMM}")) {
            result = result.replace("{yyMM}", today.format(DateTimeFormatter.ofPattern("yyMM")));
        }
        if (result.contains("{yyMMdd}")) {
            result = result.replace("{yyMMdd}", today.format(DateTimeFormatter.ofPattern("yyMMdd")));
        }
        if (result.contains("{yyyyMMdd}")) {
            result = result.replace("{yyyyMMdd}", today.format(DateTimeFormatter.BASIC_ISO_DATE));
        }
        // {yyyy} D9 closing Group B 加入，PLAN_NO 使用
        if (result.contains("{yyyy}")) {
            result = result.replace("{yyyy}", today.format(DateTimeFormatter.ofPattern("yyyy")));
        }

        // {prefix} 业态前缀（PRODUCE_NO 使用），从 context.prefix 取原样字符串；缺则保留占位符并 warn
        if (result.contains("{prefix}")) {
            Object prefix = context.get("prefix");
            if (prefix == null) {
                log.warn("[djs-bizcode] 编码生成 context 缺字段 prefix，pattern={} rule={}",
                    rule.getPattern(), rule.getCodeType());
            } else {
                result = result.replace("{prefix}", prefix.toString());
            }
        }

        // 序号占位符（按位数补零）
        if (result.contains("{dailySeq4}")) {
            result = result.replace("{dailySeq4}", pad(seq, 4));
        }
        if (result.contains("{seq3}")) {
            result = result.replace("{seq3}", pad(seq, 3));
        }
        if (result.contains("{seq4}")) {
            result = result.replace("{seq4}", pad(seq, 4));
        }
        if (result.contains("{seq5}")) {
            result = result.replace("{seq5}", pad(seq, 5));
        }
        if (result.contains("{seq6}")) {
            result = result.replace("{seq6}", pad(seq, 6));
        }
        if (result.contains("{seq7}")) {
            result = result.replace("{seq7}", pad(seq, 7));
        }

        // 上下文 2 位占位符（按需查找，避免 pattern 没用到的字段触发 WARN）
        if (result.contains("{farmCode2}")) {
            result = result.replace("{farmCode2}", resolveCtx2(context, "farmCode", DEFAULT_FARM_CODE, rule));
        }
        if (result.contains("{barnCode2}")) {
            result = result.replace("{barnCode2}", resolveCtx2(context, "barnCode", null, rule));
        }
        if (result.contains("{productCode2}")) {
            result = result.replace("{productCode2}", resolveCtx2(context, "productCode", null, rule));
        }
        if (result.contains("{bizCode2}")) {
            result = result.replace("{bizCode2}", resolveCtx2(context, "bizCode", null, rule));
        }
        if (result.contains("{ioCode2}")) {
            result = result.replace("{ioCode2}", resolveCtx2(context, "ioCode", null, rule));
        }

        return result;
    }

    /**
     * 序号补零到指定位数。
     */
    private String pad(long seq, int width) {
        return String.format("%0" + width + "d", seq);
    }

    /**
     * 从 context 取 2 位编码段；缺则用 fallback；fallback 也无则保留占位符并 warn。
     */
    private String resolveCtx2(Map<String, Object> context, String key, String fallback, BizCodeRule rule) {
        Object val = context.get(key);
        if (val == null) {
            if (fallback != null) {
                return fallback;
            }
            log.warn("[djs-bizcode] 编码生成 context 缺字段 {}，pattern={} rule={}",
                key, rule.getPattern(), rule.getCodeType());
            return "{" + key + "2}";
        }
        String str = val.toString();
        if (str.length() == 2) {
            return str;
        }
        if (str.length() > 2) {
            return str.substring(0, 2);
        }
        // 不足 2 位：左侧补 0（与序号风格一致，避免位数错乱）
        return String.format("%2s", str).replace(' ', '0');
    }
}
