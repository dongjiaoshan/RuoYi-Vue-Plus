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
 * 耳号（EAR_NO）连号分配器（BRD-FIX-EARNO-001）。
 *
 * <h3>为什么不走通用 {@code IBizCodeGenerator.generateBatch(EAR_NO, ...)}</h3>
 * <p>EAR_NO 业务码格式是 <b>月级</b>（{@code {farmCode2}{barnCode2}{yyMM}{seq4}}），但通用编码生成器的
 * {@code t_md_biz_code_sequence} 是按 {@code daily_reset=1} 即 <b>日级</b>（{@code yyyyMMdd}）分桶递增。
 * 月内不同日各自从 seq=1 起算，拼出的耳号月份段相同 → 同月第二天引种就和第一天的耳号撞
 * {@code UNIQUE(tenant_id, ear_no, lifecycle_id, del_unique)} → {@code DuplicateKeyException} 500。</p>
 *
 * <p>本分配器把序号源改成 <b>DB max</b>（权威源）：在 Redisson 锁内 {@code SELECT MAX(ear_no)}
 * 同前缀（{@code farmCode2+barnCode2+yyMM}）现存耳号，解析末位序号 + 1 作为下一可用号，
 * N 头连号一次性算出。序号源即真实已落库耳号，不再依赖固定 seed / 日级计数器，月内连续引种不再撞号。</p>
 *
 * <h3>并发安全</h3>
 * <ol>
 *   <li><b>Redisson 锁</b>（{@link DjsRedisKey#BIZ_CODE_LOCK}，key 段含前缀 + yyMM）—— 同前缀同月串行化，
 *       避免两批并发各读到同一 max 拿到重号。复用通用编码生成器同一把锁桶。</li>
 *   <li><b>UNIQUE 兜底</b>—— 锁内对每个候选耳号显式 {@code existsEarNo} 探测；理论上锁 + 已提交 max
 *       已挡住同前缀重号，但若出现锁外脱锁写入（Redis 故障）造成候选已被占用，重新解析 max + 重试
 *       （≤ {@link #MAX_RETRY} 次），仍冲突抛 {@link ServiceException}，不让裸 {@code DuplicateKeyException} 冒成 500。</li>
 * </ol>
 *
 * @author djs
 * @since BRD-FIX-EARNO-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EarNoAllocator {

    /** 默认农场编码（V1 单农场场景，对应 tenant_id "1001"）。 */
    private static final String DEFAULT_FARM_CODE = "01";

    /** 序号位数（与现存耳号一致 —— 12 位 = farm2+barn2+yyMM4+seq4，月内单栋舍 ≤ 9999 头足够 V1）。 */
    private static final int SEQ_WIDTH = 4;

    /** 锁等待超时（秒）。 */
    private static final long LOCK_WAIT_SECONDS = 10L;

    /** 锁持有超时（秒）—— 防节点崩溃锁不释放。 */
    private static final long LOCK_LEASE_SECONDS = 30L;

    /** UNIQUE 兜底重试次数（锁内候选已被占用时重新解析 max 再分配）。 */
    private static final int MAX_RETRY = 3;

    private final PigMapper pigMapper;

    private final RedissonClient redissonClient;

    /**
     * 分配 N 个同前缀同月连号耳号。
     *
     * @param farmCode 农场编码（V1 单农场，blank 时回落 "01"）
     * @param barnCode 栋舍编码（取前 2 位，blank 时回落 "00"）
     * @param count    分配数量；必须 &gt; 0
     * @return 长度为 count 的耳号列表，序号严格连续递增
     */
    public List<String> allocate(String farmCode, String barnCode, int count) {
        if (count <= 0) {
            throw new ServiceException("耳号分配数量必须大于 0，实际：" + count);
        }
        String farm2 = normalizeCode2(farmCode, DEFAULT_FARM_CODE);
        String barn2 = normalizeCode2(barnCode, "00");
        String yyMM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM"));
        String prefix = farm2 + barn2 + yyMM;

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
    public String allocateOne(String farmCode, String barnCode) {
        return allocate(farmCode, barnCode, 1).get(0);
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
                    log.warn("[BRD-FIX-EARNO-001] 耳号候选已占用，重新解析 max：earNo={} attempt={}", earNo, attempt);
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
     * 从 {@code maxEarNo} 截掉 {@code prefix} 头部，剩余末位连续数字段转 long（兼容旧 3 位 / 新 4 位）。
     * 解析不出数字（脏数据）时回落 0，让下一号从 1 起。
     */
    private long parseSeq(String maxEarNo, String prefix) {
        String tail = maxEarNo.length() > prefix.length() ? maxEarNo.substring(prefix.length()) : "";
        // 取尾部连续数字段（防止前缀长度对不齐时把非数字也带进来）
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
            log.warn("[BRD-FIX-EARNO-001] 解析耳号序号失败，回落 0：maxEarNo={} prefix={}", maxEarNo, prefix);
            return 0L;
        }
    }

    /** 序号补零到 {@link #SEQ_WIDTH} 位。 */
    private String pad(long seq) {
        return String.format("%0" + SEQ_WIDTH + "d", seq);
    }

    /** 归一化为 2 位编码段（截断 / 左侧补 0），blank 时回落 fallback。 */
    private String normalizeCode2(String raw, String fallback) {
        if (StringUtils.isBlank(raw)) {
            return fallback;
        }
        if (raw.length() >= 2) {
            return raw.substring(0, 2);
        }
        return String.format("%2s", raw).replace(' ', '0');
    }
}
