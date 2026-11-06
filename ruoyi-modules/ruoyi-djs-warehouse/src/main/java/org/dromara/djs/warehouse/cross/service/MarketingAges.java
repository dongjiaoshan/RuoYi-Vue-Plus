package org.dromara.djs.warehouse.cross.service;

import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.breed.core.domain.vo.PigMarketingAgeVo;
import org.dromara.djs.breed.core.service.IPigQueryService;
import org.dromara.djs.warehouse.cross.domain.BarInfo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 白条 → 接收时日龄（V6 row145 白条领用卡 / V7 row9 猪只入库卡 / 白条分割管理卡共用）。
 *
 * <p>口径（D-0152 ②，甲方拍板「以接收的日期算」）：日龄 = 出栏冻结日龄 + (接收日 − 出栏日) 的自然日差。
 * 接收日 = 该白条 bar_info 最早一条燎毛入库流水的日期；尚未接收（无入库流水）先按今天（Asia/Shanghai）算，
 * 接收后即固定。出栏日与接收日都只取日期部分相减。</p>
 *
 * <p>基数取 {@code t_farm_pig_marketing.age_days}（出栏那刻冻结的快照，ADR-0017），不从出生日期现算 ——
 * 现算会在日后订正出生日期时回溯改写历史值，正是该 ADR 明确否掉的做法。</p>
 *
 * <p>配对键带上出栏时间是因为<b>耳标可回收复用</b>：{@code t_farm_pig_info} 的耳号并不唯一
 * （UNIQUE 含 {@code lifecycle_id}），同一个耳号在库里可能对应先后两头猪、两次出栏。
 * {@code bar.marketing_time} 是 {@code PigMarketingEventListener} 从出栏记录原样拷过来的，
 * 两者配对即锁定同一次出栏事件，不会串到上一轮那头猪。</p>
 */
public final class MarketingAges {

    private static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");

    private final Map<String, Integer> byMarketing;
    private final Map<Long, Date> receiveTimes;
    private final LocalDate today;

    private MarketingAges(Map<String, Integer> byMarketing, Map<Long, Date> receiveTimes, LocalDate today) {
        this.byMarketing = byMarketing;
        this.receiveTimes = receiveTimes;
        this.today = today;
    }

    /**
     * 一次批量查齐这批白条的出栏冻结日龄（无耳号的白条不查）。
     *
     * @param receiveTimes bar_info.id → 最早一条燎毛入库流水时间（调用方批量取，缺键 = 尚未接收）
     */
    public static MarketingAges load(IPigQueryService pigQueryService, Collection<BarInfo> bars,
                                     Map<Long, Date> receiveTimes) {
        LocalDate today = LocalDate.now(BIZ_ZONE);
        Map<Long, Date> receives = receiveTimes == null ? Map.of() : receiveTimes;
        Set<String> earNos = bars.stream()
            .map(BarInfo::getEarNo)
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .collect(Collectors.toSet());
        if (earNos.isEmpty()) {
            return new MarketingAges(Map.of(), receives, today);
        }
        Map<String, Integer> map = new HashMap<>();
        for (PigMarketingAgeVo vo : pigQueryService.listMarketingAgeByEarNos(earNos)) {
            if (StringUtils.isNotBlank(vo.getEarNo()) && vo.getMarketingDate() != null && vo.getAgeDays() != null) {
                map.putIfAbsent(key(vo.getEarNo(), vo.getMarketingDate()), vo.getAgeDays());
            }
        }
        return new MarketingAges(map, receives, today);
    }

    /** 该白条接收时日龄（接收时间取 load 传入的映射，今天取 load 当刻的 Asia/Shanghai 日期）。 */
    public Integer of(BarInfo bar) {
        return of(bar, receiveTimes.get(bar.getId()), today);
    }

    /**
     * 该白条接收时日龄；外购 / 无出栏记录 / 未冻结时为 null。
     *
     * @param receiveTime 该 bar_info 最早一条燎毛入库流水时间；null = 尚未接收，按 {@code today} 算
     * @param today       业务今天（Asia/Shanghai）
     */
    Integer of(BarInfo bar, Date receiveTime, LocalDate today) {
        Date marketingTime = bar.getMarketingTime();
        if (marketingTime == null) {
            return null;
        }
        Integer frozen = byMarketing.get(key(bar.getEarNo(),
            marketingTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()));
        if (frozen == null) {
            return null;
        }
        LocalDate marketingDay = marketingTime.toInstant().atZone(BIZ_ZONE).toLocalDate();
        LocalDate referenceDay = receiveTime != null ? receiveTime.toInstant().atZone(BIZ_ZONE).toLocalDate() : today;
        // 接收日早于出栏日只可能是脏数据：不倒扣，按出栏冻结值显示
        long elapsed = Math.max(0L, ChronoUnit.DAYS.between(marketingDay, referenceDay));
        return Math.toIntExact(frozen + elapsed);
    }

    private static String key(String earNo, LocalDateTime marketingDate) {
        return StringUtils.trimToEmpty(earNo) + '|' + marketingDate;
    }
}
