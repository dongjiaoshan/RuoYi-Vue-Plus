package org.dromara.djs.breed.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.common.constant.DjsRedisKey;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 耳号（EAR_NO）连号分配器（ADR-0011 客户权威带分隔符格式）。
 *
 * <h3>格式</h3>
 * <p>各段以 {@code -} 分隔 = {@code {品系}-{品种2}-{出生yyMMdd6}-{当天序号3}}（客户权威编码表，6/15 起耳号不再编码性别）：</p>
 * <ul>
 *   <li>品系码 = {@code djs_pig_strain} 字典 dict_value（原样取，支持 1-2 位）；</li>
 *   <li>品种码 = {@code djs_pig_breed} 字典 dict_value（定长 2 位，01-06）；</li>
 *   <li>出生年月日 = {@code yyMMdd}（猪只出生日，非系统当前日）；</li>
 *   <li>序号 = 3 位补零，<b>当天级全场 max+1（不分品系品种）</b>——同一天所有猪不分品系品种统一从 001 顺序递增，
 *       每天从 001 起重新编号（全号仍是 品系-品种2-yyMMdd-序号3，只是序号在全天范围内连续唯一）。</li>
 * </ul>
 * <p>前缀 = {@code 品系-品种2-yyMMdd6}（如 {@code 1-01-260609}）→ 拼上 {@code -序号3}（如 {@code 1-01-260609-001}）。
 * 性别不再入耳号（公母仔猪同序列连号），{@code pigSex} 参数保留仅为兼容老调用方，前缀组装不再使用。
 * 序号桶不再按前缀（品系×品种×日）切分，而是按<b>出生日</b>全场共享一个连号序列。</p>
 *
 * <h3>序号源 = DB max（权威源，按出生日段全场聚合）</h3>
 * <p>在 Redisson 锁内 {@code SELECT MAX(序号)} 同<b>出生日段</b>现存耳号（{@code ear_no REGEXP '-yyMMdd-数字结尾'}，
 * 倒数第二段 = 出生日），取末段序号 + 1 作为下一可用号，N 头连号一次性算出。出生日段精确匹配（日期段是倒数第二段），
 * 旧 12 位无分隔历史号天然不匹配，新旧隔离无撞号（ADR-0011 §2.7）。</p>
 *
 * <h3>并发安全</h3>
 * <ol>
 *   <li><b>Redisson 锁</b>（key 段含出生日 yyMMdd）—— 同一天全场串行化，避免跨前缀两批并发各读到同一 max 拿到重号。</li>
 *   <li><b>UNIQUE 兜底</b>—— 锁内对每个候选耳号显式 {@code existsEarNo} 探测；候选已被占用（脱锁写入 / 历史脏数据）
 *       则重新解析 max + 重试（≤ {@link #MAX_RETRY} 次），仍冲突抛 {@link ServiceException}，不让裸
 *       {@code DuplicateKeyException} 冒成 500。</li>
 * </ol>
 *
 * @author djs
 * @since ADR-0011（D12X-BRD-EARNO-FORMAT-001）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EarNoAllocator {

    /** 序号位数（耳号末段，当天单前缀 ≤ 999 头足够）。 */
    private static final int SEQ_WIDTH = 3;

    /** 品种码定长（耳号第 2 段，客户码表 01-06）。 */
    private static final int BREED_CODE_WIDTH = 2;

    /** 段间分隔符（客户权威格式：品系-品种-公母-出生日-序号）。 */
    private static final String SEG_SEP = "-";

    /** 出生日期段格式（耳号第 4 段）。 */
    private static final DateTimeFormatter BIRTH_FMT = DateTimeFormatter.ofPattern("yyMMdd");

    /** 锁等待超时（秒）。 */
    private static final long LOCK_WAIT_SECONDS = 10L;

    /** 锁持有超时（秒）—— 防节点崩溃锁不释放。 */
    private static final long LOCK_LEASE_SECONDS = 30L;

    /** UNIQUE 兜底重试次数（锁内候选已被占用时重新解析 max 再分配）。 */
    private static final int MAX_RETRY = 3;

    private final PigMapper pigMapper;

    private final RedissonClient redissonClient;

    /**
     * 分配 N 个连号耳号（同品系 + 同品种 + 同出生日；序号在当天全场范围内连续唯一）。
     *
     * @param strainCode 品系码（{@code djs_pig_strain} dict_value，1 位）；不可空
     * @param breedCode  品种码（{@code djs_pig_breed} dict_value，2 位）；不可空
     * @param pigSex     性别（{@code M} 公 / {@code F} 母）；保留仅为兼容老调用方，不参与组装
     * @param birthDate  出生日期（耳号 yyMMdd 段 + 序号桶维度）；不可空
     * @param count      分配数量；必须 &gt; 0
     * @return 长度为 count 的耳号列表，序号严格连续递增
     */
    public List<String> allocate(String strainCode, String breedCode, String pigSex, LocalDate birthDate, int count) {
        if (count <= 0) {
            throw new ServiceException("耳号分配数量必须大于 0，实际：" + count);
        }
        String prefix = buildPrefix(strainCode, breedCode, pigSex, birthDate);
        String dateSeg = birthDate.format(BIRTH_FMT);

        // 锁按出生日（同一天全场串行），避免跨前缀并发重号
        String lockKey = String.format(DjsRedisKey.BIZ_CODE_LOCK, "ear_no:" + dateSeg);
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new ServiceException("耳号分配抢锁超时：" + lockKey);
            }
            return allocateLocked(prefix, dateSeg, count);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("耳号分配被中断：" + prefix);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 分配单个耳号（单头引种用，内部走 {@link #allocate} 的 count=1 路径）。
     */
    public String allocateOne(String strainCode, String breedCode, String pigSex, LocalDate birthDate) {
        return allocate(strainCode, breedCode, pigSex, birthDate, 1).get(0);
    }

    /**
     * 组装耳号前缀（ADR-0011 2026-06-18 修订：外部引种重新编入性别段）。
     * <p>位码取自字典 dict_value（前端选品种/品系直接传 dict_value，即位码本身，ADR-0011 §2.2）。</p>
     * <ul>
     *   <li>{@code pigSex} 非空（外部引种，单批同性别）→ {@code 品系-品种2-性别1-yyMMdd6}（如 {@code 12-01-2-260618}）；
     *       性别码客户权威表：{@code 1=公(M) / 2=母(F)}。拼上 {@code -序号3} 即完整耳号（如 {@code 12-01-2-260618-001}）。</li>
     *   <li>{@code pigSex} 空（仔猪耳标批量，同批可混公母，走全场连号不编性别）→ 保持 {@code 品系-品种2-yyMMdd6} 旧格式，
     *       向后兼容、不影响未报问题的仔猪耳标特性。</li>
     * </ul>
     * <p>两种格式 yyMMdd 段都在倒数第二段、序号在末段，{@link PigMapper#selectMaxSeqByDateSegment} 的
     * {@code REGEXP '-yyMMdd-[0-9]+$'} 对两者都匹配，当天全场连号序列不受格式差异影响、无撞号。</p>
     */
    public String buildPrefix(String strainCode, String breedCode, String pigSex, LocalDate birthDate) {
        if (StringUtils.isBlank(strainCode)) {
            throw new ServiceException("耳号生成失败：品系码不能为空");
        }
        if (StringUtils.isBlank(breedCode)) {
            throw new ServiceException("耳号生成失败：品种码不能为空");
        }
        if (birthDate == null) {
            throw new ServiceException("耳号生成失败：出生日期不能为空");
        }
        String strain = strainCode.trim();
        String breed2 = padLeftZero(breedCode.trim(), BREED_CODE_WIDTH);
        String yyMMdd = birthDate.format(BIRTH_FMT);
        if (StringUtils.isNotBlank(pigSex)) {
            return strain + SEG_SEP + breed2 + SEG_SEP + sexCode(pigSex) + SEG_SEP + yyMMdd;
        }
        return strain + SEG_SEP + breed2 + SEG_SEP + yyMMdd;
    }

    /** 性别位码（客户权威耳号编码表：1=公 / 2=母；入参 M=公 / F=母）。 */
    private String sexCode(String pigSex) {
        String s = pigSex.trim().toUpperCase();
        if ("M".equals(s)) {
            return "1";
        }
        if ("F".equals(s)) {
            return "2";
        }
        throw new ServiceException("耳号生成失败：性别码非法（须 M 公 / F 母）：" + pigSex);
    }

    /**
     * 锁内：DB max + 1 推算连号，含 UNIQUE 兜底重试。
     * <p>序号源按<b>出生日段</b>全场 max（不分品系品种），候选耳号仍用各自 {@code prefix}（品系-品种-日）拼序号。</p>
     */
    private List<String> allocateLocked(String prefix, String dateSeg, int count) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            long nextSeq = resolveNextSeqByDate(dateSeg);
            List<String> candidates = new ArrayList<>(count);
            boolean conflict = false;
            for (int i = 0; i < count; i++) {
                String earNo = prefix + SEG_SEP + pad(nextSeq + i);
                // 兜底：候选若已被占用（脱锁写入 / 历史脏数据）→ 整批重算
                if (pigMapper.existsEarNo(earNo) != null) {
                    log.warn("[ADR-0011] 耳号候选已占用，重新解析 max：earNo={} attempt={}", earNo, attempt);
                    conflict = true;
                    break;
                }
                candidates.add(earNo);
            }
            if (!conflict) {
                return candidates;
            }
        }
        throw new ServiceException("ear_no.generate_conflict");
    }

    /**
     * 出生日全场下一可用序号 = {@code MAX(序号)} 同出生日段 + 1；当天无现存号则 1。
     * <p>供 service 端"用户首号下限校验" / 预览复用（甲方：用户填的数量编号不得小于后台返回的最小可用号）。
     * 仅读不锁——真正分配仍走 {@link #allocate} 的 Redisson 锁 + UNIQUE 兜底。</p>
     *
     * @param birthDate 出生日期；不可空
     * @return 下一可用 3 位序号的数值（1-based）
     */
    public long nextSeqForDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new ServiceException("耳号序号查询失败：出生日期不能为空");
        }
        return nextSeqForDateSegment(birthDate.format(BIRTH_FMT));
    }

    /**
     * 同出生日段（{@code yyMMdd} 字符串）下一可用序号 = {@code MAX(序号)} 同日段 + 1；当天无号则 1。
     * <p>供"用户首号下限校验"复用：用户首号里直接带 yyMMdd 段，按该段（而非转 LocalDate）取下限，
     * 与候选耳号同口径。仅读不锁。</p>
     *
     * @param dateSeg 出生日段（{@code yyMMdd}，6 位）
     * @return 下一可用 3 位序号的数值（1-based）
     */
    public long nextSeqForDateSegment(String dateSeg) {
        return resolveNextSeqByDate(dateSeg);
    }

    /**
     * 解析同出生日段现存耳号的下一可用序号：{@code MAX(序号)} 同日段 + 1；无则从 1 起。
     */
    private long resolveNextSeqByDate(String dateSeg) {
        Long maxSeq = pigMapper.selectMaxSeqByDateSegment(dateSeg);
        return (maxSeq == null ? 0L : maxSeq) + 1L;
    }

    /** 序号补零到 {@link #SEQ_WIDTH} 位。 */
    private String pad(long seq) {
        return String.format("%0" + SEQ_WIDTH + "d", seq);
    }

    /** 左侧补零到 width 位（已 ≥ width 则原样返回；客户码本身定长，仅防短码漏前导零）。 */
    private String padLeftZero(String raw, int width) {
        if (raw.length() >= width) {
            return raw;
        }
        return String.format("%" + width + "s", raw).replace(' ', '0');
    }
}
