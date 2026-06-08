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
 * 耳号（EAR_NO）连号分配器（ADR-0011 客户权威 14 位格式）。
 *
 * <h3>格式</h3>
 * <p>14 位纯数字 = {@code {品系1}{品种2}{公母1}{出生yyMMdd6}{当天序号4}}（客户权威编码表）：</p>
 * <ul>
 *   <li>品系码 = {@code djs_pig_strain} 字典 dict_value（1 位，1-5）；</li>
 *   <li>品种码 = {@code djs_pig_breed} 字典 dict_value（定长 2 位，01-06）；</li>
 *   <li>公母码 = {@code M→1 / F→2}（业务恒定，唯一允许硬编码的映射）；</li>
 *   <li>出生年月日 = {@code yyMMdd}（猪只出生日，非系统当前日）；</li>
 *   <li>序号 = 4 位补零，<b>当天级</b>同前缀 max+1，每天从 0001 起重新编号。</li>
 * </ul>
 * <p>前缀 = {@code 品系1 + 品种2 + 公母1 + yyMMdd6} = 定长 10 位 → 总长固定 14。</p>
 *
 * <h3>序号源 = DB max（权威源）</h3>
 * <p>在 Redisson 锁内 {@code SELECT MAX(ear_no)} 同前缀现存耳号（{@code likeRight}），解析末位 4 位序号 + 1
 * 作为下一可用号，N 头连号一次性算出。前缀含出生日 yyMMdd → 天级桶天然隔离，同前缀第二天从 0001 起不撞。
 * 旧 12 位历史耳号（{@code {农场2}{栋舍2}{yyMM4}{seq4}}）前缀组成不同，新前缀匹配不到，新旧隔离无撞号（ADR-0011 §2.7）。</p>
 *
 * <h3>并发安全</h3>
 * <ol>
 *   <li><b>Redisson 锁</b>（key 段含新前缀）—— 同前缀串行化，避免两批并发各读到同一 max 拿到重号。</li>
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

    /** 序号位数（耳号第 11-14 位，当天单前缀 ≤ 9999 头足够）。 */
    private static final int SEQ_WIDTH = 4;

    /** 品种码定长（耳号第 2-3 位，客户码表 01-06）。 */
    private static final int BREED_CODE_WIDTH = 2;

    /** 出生日期段格式（耳号第 5-10 位）。 */
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
     * 分配 N 个同前缀连号耳号（同品系 + 同品种 + 同公母 + 同出生日）。
     *
     * @param strainCode 品系码（{@code djs_pig_strain} dict_value，1 位）；不可空
     * @param breedCode  品种码（{@code djs_pig_breed} dict_value，2 位）；不可空
     * @param pigSex     性别（{@code M} 公 / {@code F} 母）；不可空
     * @param birthDate  出生日期（耳号 yyMMdd 段）；不可空
     * @param count      分配数量；必须 &gt; 0
     * @return 长度为 count 的耳号列表，序号严格连续递增
     */
    public List<String> allocate(String strainCode, String breedCode, String pigSex, LocalDate birthDate, int count) {
        if (count <= 0) {
            throw new ServiceException("耳号分配数量必须大于 0，实际：" + count);
        }
        String prefix = buildPrefix(strainCode, breedCode, pigSex, birthDate);

        String lockKey = String.format(DjsRedisKey.BIZ_CODE_LOCK, "ear_no:" + prefix);
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new ServiceException("耳号分配抢锁超时：" + lockKey);
            }
            return allocateLocked(prefix, count);
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
     * 组装耳号前缀（定长 10 位）= 品系1 + 品种2 + 公母1 + yyMMdd6。
     * <p>位码取自字典 dict_value（前端选品种/品系直接传 dict_value，即位码本身，ADR-0011 §2.2）。</p>
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
        String strain1 = strainCode.trim();
        String breed2 = padLeftZero(breedCode.trim(), BREED_CODE_WIDTH);
        String sex1 = sexCode(pigSex);
        String yyMMdd = birthDate.format(BIRTH_FMT);
        return strain1 + breed2 + sex1 + yyMMdd;
    }

    /** 公母码映射（业务恒定，唯一允许硬编码）：M→1 公 / F→2 母；其余拒绝，不默认未约定值。 */
    private String sexCode(String pigSex) {
        if ("M".equals(pigSex)) {
            return "1";
        }
        if ("F".equals(pigSex)) {
            return "2";
        }
        throw new ServiceException("耳号生成失败：未知性别（仅支持 M 公 / F 母），实际：" + pigSex);
    }

    /**
     * 锁内：DB max + 1 推算连号，含 UNIQUE 兜底重试。
     */
    private List<String> allocateLocked(String prefix, int count) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            long nextSeq = resolveNextSeq(prefix);
            List<String> candidates = new ArrayList<>(count);
            boolean conflict = false;
            for (int i = 0; i < count; i++) {
                String earNo = prefix + pad(nextSeq + i);
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
     * 同前缀（10 位）下一可用序号 = {@code MAX(ear_no)} 末位 seq + 1；无现存号则 1。
     * <p>供 service 端"用户首号下限校验"复用（甲方：用户填的数量编号不得小于后台返回的最小可用号），
     * 不重写 SQL。仅读不锁——真正分配仍走 {@link #allocate} 的 Redisson 锁 + UNIQUE 兜底。</p>
     *
     * @param prefix 耳号前缀（品系1 + 品种2 + 公母1 + yyMMdd6，定长 10 位）
     * @return 下一可用 4 位序号的数值（1-based）
     */
    public long nextSeqForPrefix(String prefix) {
        return resolveNextSeq(prefix);
    }

    /**
     * 解析同前缀现存耳号的下一可用序号：{@code MAX(ear_no)} 末位 seq + 1；无则从 1 起。
     */
    private long resolveNextSeq(String prefix) {
        String maxEarNo = pigMapper.selectMaxEarNoByPrefix(prefix);
        if (StringUtils.isBlank(maxEarNo)) {
            return 1L;
        }
        return parseSeq(maxEarNo, prefix) + 1L;
    }

    /**
     * 从 {@code maxEarNo} 截掉定长前缀，剩余末位连续数字段转 long。
     * 解析不出数字（脏数据）时回落 0，让下一号从 1 起。
     */
    private long parseSeq(String maxEarNo, String prefix) {
        String tail = maxEarNo.length() > prefix.length() ? maxEarNo.substring(prefix.length()) : "";
        // 取尾部连续数字段（防止脏数据把非数字带进来）
        int start = tail.length();
        while (start > 0 && Character.isDigit(tail.charAt(start - 1))) {
            start--;
        }
        String seqPart = tail.substring(start);
        if (seqPart.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(seqPart);
        } catch (NumberFormatException e) {
            log.warn("[ADR-0011] 解析耳号序号失败，回落 0：maxEarNo={} prefix={}", maxEarNo, prefix);
            return 0L;
        }
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
