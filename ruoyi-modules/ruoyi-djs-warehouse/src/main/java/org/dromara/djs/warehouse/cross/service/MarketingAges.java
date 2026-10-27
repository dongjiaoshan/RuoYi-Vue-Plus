package org.dromara.djs.warehouse.cross.service;

import org.dromara.common.core.utils.StringUtils;
import org.dromara.djs.breed.core.domain.vo.PigMarketingAgeVo;
import org.dromara.djs.breed.core.service.IPigQueryService;
import org.dromara.djs.warehouse.cross.domain.BarInfo;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 白条 → 出栏当时冻结日龄（V6 row145 白条领用卡 / V7 row9 猪只入库卡共用）。
 *
 * <p>取 {@code t_farm_pig_marketing.age_days}（出栏那刻冻结的快照，ADR-0017），不现算 ——
 * 现算会在日后订正出生日期时回溯改写历史值，正是该 ADR 明确否掉的做法。</p>
 *
 * <p>配对键带上出栏时间是因为<b>耳标可回收复用</b>：{@code t_farm_pig_info} 的耳号并不唯一
 * （UNIQUE 含 {@code lifecycle_id}），同一个耳号在库里可能对应先后两头猪、两次出栏。
 * {@code bar.marketing_time} 是 {@code PigMarketingEventListener} 从出栏记录原样拷过来的，
 * 两者配对即锁定同一次出栏事件，不会串到上一轮那头猪。</p>
 */
public final class MarketingAges {

    private final Map<String, Integer> byMarketing;

    private MarketingAges(Map<String, Integer> byMarketing) {
        this.byMarketing = byMarketing;
    }

    /** 一次批量查齐这批白条的出栏日龄（无耳号的白条不查）。 */
    public static MarketingAges load(IPigQueryService pigQueryService, Collection<BarInfo> bars) {
        Set<String> earNos = bars.stream()
            .map(BarInfo::getEarNo)
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .collect(Collectors.toSet());
        if (earNos.isEmpty()) {
            return new MarketingAges(Map.of());
        }
        Map<String, Integer> map = new HashMap<>();
        for (PigMarketingAgeVo vo : pigQueryService.listMarketingAgeByEarNos(earNos)) {
            if (StringUtils.isNotBlank(vo.getEarNo()) && vo.getMarketingDate() != null && vo.getAgeDays() != null) {
                map.putIfAbsent(key(vo.getEarNo(), vo.getMarketingDate()), vo.getAgeDays());
            }
        }
        return new MarketingAges(map);
    }

    /** 该白条出栏当时日龄；外购 / 无出栏记录 / 未冻结时为 null。 */
    public Integer of(BarInfo bar) {
        Date marketingTime = bar.getMarketingTime();
        if (marketingTime == null) {
            return null;
        }
        return byMarketing.get(key(bar.getEarNo(),
            marketingTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()));
    }

    private static String key(String earNo, LocalDateTime marketingDate) {
        return StringUtils.trimToEmpty(earNo) + '|' + marketingDate;
    }
}
