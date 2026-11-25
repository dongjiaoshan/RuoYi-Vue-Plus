package org.dromara.djs.breed.dashboard.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.djs.breed.dashboard.mapper.ProductionIndicatorMapper;
import org.dromara.djs.breed.dashboard.mapper.ProductionIndicatorMapper.GroupChange;
import org.dromara.djs.breed.dashboard.mapper.ProductionIndicatorMapper.GroupMember;
import org.dromara.djs.breed.dashboard.mapper.ProductionIndicatorMapper.IndividualWeaning;
import org.dromara.djs.breed.dashboard.mapper.ProductionIndicatorMapper.MarketingEvent;
import org.dromara.djs.breed.dashboard.mapper.ProductionIndicatorMapper.TypeChange;
import org.dromara.djs.breed.dashboard.mapper.ProductionIndicatorMapper.TerminalEvent;
import org.dromara.djs.breed.dashboard.mapper.ProductionIndicatorMapper.WeanedLitter;
import org.dromara.djs.breed.dashboard.mapper.ProductionIndicatorMapper.WeaningBatchRow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 将 V1.1 的事件明细统一归期、配对及去重，供养殖日月年聚合使用。 */
@Component
@RequiredArgsConstructor
class ProductionIndicatorCalculator {

    private static final String SOW = "sow";
    private static final String FATTENING = "fattening";
    private static final String PIGLET = "piglet";

    private final ProductionIndicatorMapper mapper;

    Map<String, Object> farrowTotals(String tenantId, LocalDateTime from, LocalDateTime to) {
        return mapper.farrowTotals(tenantId, from, to);
    }

    Map<String, Object> dailyAbnormalTotals(String tenantId, LocalDateTime from, LocalDateTime to) {
        return mapper.dailyAbnormalTotals(tenantId, from, to);
    }

    int countIntroducedReserve(String tenantId, LocalDate day) {
        return mapper.countIntroducedReserve(tenantId, day);
    }

    Map<String, Object> feedTotals(String tenantId, LocalDateTime from, LocalDateTime to) {
        return mapper.feedTotals(tenantId, from, to);
    }

    int countDeathCull(String tenantId, LocalDateTime from, LocalDateTime to) {
        return mapper.countDeathCull(tenantId, from, to);
    }

    int countPregnancyLoss(String tenantId, LocalDateTime from, LocalDateTime to) {
        return mapper.countPregnancyLoss(tenantId, from, to);
    }

    static BigDecimal dailyAverage(BigDecimal sum, int validCount, int scale) {
        return validCount == 0 ? BigDecimal.ZERO.setScale(scale)
            : sum.divide(BigDecimal.valueOf(validCount), scale, RoundingMode.HALF_UP);
    }

    int firstReserveBreeding(String tenantId, LocalDateTime from, LocalDateTime to) {
        return mapper.countFirstReserveBreeding(tenantId, from, to);
    }

    Map<String, Object> pigletMeasures(String tenantId, LocalDateTime from, LocalDateTime to) {
        return mapper.weanedPigletMeasures(tenantId, from, to);
    }

    int weaningSowCount(String tenantId, LocalDateTime from, LocalDateTime to) {
        return mapper.weanedLitters(tenantId, from, to).size();
    }

    int lactationDaysByFarrowMonth(String tenantId, LocalDateTime from, LocalDateTime to, LocalDateTime asOf) {
        return mapper.sumLactationDaysByFarrow(tenantId, from, to, asOf);
    }

    Weaning weaningForMonth(String tenantId, LocalDateTime from, LocalDateTime to, LocalDateTime asOf) {
        List<WeanedLitter> litters = mapper.weanedLitters(tenantId, from, to);
        if (litters.isEmpty()) {
            return new Weaning(0, 0, 0, 0, 0, List.of(), Map.of());
        }
        Set<Long> litterIds = litters.stream().map(WeanedLitter::farrowId).collect(Collectors.toSet());
        Set<Long> fostered = new HashSet<>();
        Set<Long> withPiglets = new HashSet<>();
        Map<Long, LocalDate> lactationThrough = new HashMap<>();
        litters.forEach(litter -> lactationThrough.put(litter.farrowId(), litter.weanDate()));
        int birthLitterPiglets = 0;
        for (WeaningBatchRow row : mapper.weaningBatches(tenantId, litterIds, asOf)) {
            int piglets = row.detailId() == null ? row.recordedCount() : 1;
            if (piglets > 0) {
                withPiglets.add(row.hostFarrowId());
            }
            Long birthFarrow = row.birthFarrowId() == null ? row.hostFarrowId() : row.birthFarrowId();
            if (birthFarrow == null) {
                continue;
            }
            if (!Objects.equals(birthFarrow, row.hostFarrowId())) {
                fostered.add(birthFarrow);
                fostered.add(row.hostFarrowId());
            }
            // D-0215：按生母实际断奶月收口，后续批次仍归生母窝。
            if (litterIds.contains(birthFarrow)) {
                birthLitterPiglets += piglets;
                if (piglets > 0) {
                    LocalDate batchDay = row.weaningDate().toLocalDate();
                    lactationThrough.computeIfPresent(birthFarrow, (id, lastDay) ->
                        batchDay.isAfter(lastDay) ? batchDay : lastDay);
                }
            }
        }
        int normal = 0;
        int lactationDays = 0;
        for (WeanedLitter litter : litters) {
            if (withPiglets.contains(litter.farrowId()) && !fostered.contains(litter.farrowId())) {
                normal++;
            }
            lactationDays += (int) ChronoUnit.DAYS.between(litter.farrowDate(), litter.weanDate());
        }
        return new Weaning(litters.size(), normal, lactationDays, litters.size(), birthLitterPiglets, litters, lactationThrough);
    }

    DailyGroups dailyGroups(String tenantId, LocalDate day) {
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay();
        Set<Long> sows = new HashSet<>();
        Set<Long> fattening = new HashSet<>();
        Set<Long> newFattening = new HashSet<>();
        int endFattening = 0;
        int endPiglets = 0;
        List<GroupMember> members = mapper.endGroupMembers(tenantId, day);
        List<GroupChange> changes = mapper.groupChanges(tenantId, from, to);
        Map<Long, IndividualWeaning> weanings = mapper.individualWeanings(tenantId, day).stream()
            .collect(Collectors.toMap(IndividualWeaning::pigId, weaning -> weaning));
        Set<Long> replayIds = changes.stream().map(GroupChange::pigId).collect(Collectors.toSet());
        replayIds.addAll(weanings.keySet());
        for (GroupMember member : members) {
            if (needsIndividualType(member, day, effectiveWeanDate(weanings, member.pigId(), member.weanDate()))) {
                replayIds.add(member.pigId());
            }
        }
        var histories = typeHistories(tenantId, replayIds);
        for (GroupMember member : members) {
            LocalDate weanDate = effectiveWeanDate(weanings, member.pigId(), member.weanDate());
            String type = needsIndividualType(member, day, weanDate)
                ? typeBefore(histories.getOrDefault(member.pigId(), List.of()), Long.MAX_VALUE, to.minusNanos(1),
                    null, member.currentPigType(), weanDate)
                : member.pigType();
            if (SOW.equals(type)) {
                sows.add(member.pigId());
            } else if (FATTENING.equals(type)) {
                fattening.add(member.pigId());
                endFattening++;
            } else if (PIGLET.equals(type)) {
                endPiglets++;
            }
        }
        for (GroupChange change : changes) {
            long pigId = change.pigId();
            String before = typeBefore(histories.getOrDefault(pigId, List.of()), change.id(), change.changeTime(),
                change.oldPigType(), change.currentPigType(), effectiveWeanDate(weanings, pigId, change.weanDate()));
            String after = change.newPigType() == null ? before : change.newPigType();
            addGroup(sows, fattening, pigId, before, change.oldStatus());
            addGroup(sows, fattening, pigId, after, change.newStatus());
            boolean directEntry = "INTRO".equals(change.eventType()) && change.oldStatus() == null
                && change.oldPigType() == null;
            if (FATTENING.equals(after) && !"END".equals(change.newStatus())
                && (!FATTENING.equals(before) || directEntry)) {
                newFattening.add(pigId);
            }
        }
        for (var weaning : weanings.values()) {
            if (!day.equals(weaning.weanDate())) {
                continue;
            }
            String before = typeBefore(histories.getOrDefault(weaning.pigId(), List.of()), Long.MAX_VALUE,
                from.minusNanos(1), null, weaning.currentPigType(), day);
            if (PIGLET.equals(before)) {
                newFattening.add(weaning.pigId());
            }
        }
        return new DailyGroups(sows.size(), fattening.size(), endFattening, endPiglets, newFattening.size());
    }

    private static LocalDate effectiveWeanDate(Map<Long, IndividualWeaning> weanings, long pigId, LocalDate savedDate) {
        IndividualWeaning corrected = weanings.get(pigId);
        if (corrected == null) {
            return savedDate;
        }
        // 已撤销的断奶按尚未发生处理；显式类型变更仍能独立证明已转育肥。
        return corrected.weanDate() == null ? LocalDate.MAX : corrected.weanDate();
    }

    private static boolean needsIndividualType(GroupMember member, LocalDate day, LocalDate weanDate) {
        return weanDate != null
            && (PIGLET.equals(member.pigType())
                || FATTENING.equals(member.pigType()) && day.isBefore(weanDate));
    }

    Marketing marketing(String tenantId, LocalDateTime from, LocalDateTime to) {
        List<MarketingEvent> events = mapper.marketingEvents(tenantId, from, to);
        var histories = typeHistories(tenantId, events.stream().map(MarketingEvent::pigId).collect(Collectors.toSet()));
        int count = 0;
        BigDecimal weight = BigDecimal.ZERO;
        var age = new AgeTotals();
        BigDecimal netGain = BigDecimal.ZERO;
        int gainDays = 0;
        for (MarketingEvent event : events) {
            String type = typeBefore(histories.getOrDefault(event.pigId(), List.of()), event.eventId(), event.changeTime(),
                event.oldPigType(), event.currentPigType(), event.weanDate());
            if (FATTENING.equals(type)) {
                count++;
                if (event.weight() != null) {
                    weight = weight.add(event.weight());
                }
                age.add(event.birthDate(), event.marketingDate());
                if (event.weanDate() != null && !event.weanDate().isAfter(event.marketingDate())
                    && event.weight() != null && event.weanWeight() != null && event.weanWeight().signum() > 0) {
                    netGain = netGain.add(event.weight().subtract(event.weanWeight()));
                    gainDays += (int) ChronoUnit.DAYS.between(event.weanDate(), event.marketingDate()) + 1;
                }
            }
        }
        return new Marketing(count, weight, age, netGain, gainDays);
    }

    private Map<Long, List<TypeChange>> typeHistories(String tenantId, Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Long> pigIds = new ArrayList<>(ids);
        Map<Long, List<TypeChange>> history = new HashMap<>();
        // 年度可能涉及大量出栏，限定每次 IN 参数数，按猪只取其完整且有序的类型变更链。
        for (int from = 0; from < pigIds.size(); from += 1000) {
            List<Long> batch = pigIds.subList(from, Math.min(from + 1000, pigIds.size()));
            for (TypeChange change : mapper.typeChanges(tenantId, batch)) {
                history.computeIfAbsent(change.pigId(), ignored -> new ArrayList<>()).add(change);
            }
        }
        return history;
    }

    private static String typeBefore(List<TypeChange> history, long eventId, LocalDateTime time,
                                     String eventOldType, String currentType, LocalDate weanDate) {
        if (eventOldType != null) {
            return eventOldType;
        }
        TypeChange prior = null;
        for (TypeChange change : history) {
            int byTime = change.changeTime().compareTo(time);
            if (byTime < 0 || byTime == 0 && change.id() < eventId) {
                if (change.newPigType() != null) {
                    prior = change;
                }
            } else if (change.oldPigType() != null) {
                return undoLaterType(change.oldPigType(), prior, time.toLocalDate(), weanDate);
            }
        }
        String type = prior != null ? prior.newPigType() : currentType;
        return undoLaterType(type, prior, time.toLocalDate(), weanDate);
    }

    private static String undoLaterType(String type, TypeChange prior, LocalDate eventDate, LocalDate weanDate) {
        // 逐头断奶翻育肥未经过状态机，个体断奶日补齐该段。已存在的更早育肥转群记录优先。
        if (weanDate != null && (prior == null || prior.changeTime().toLocalDate().isBefore(weanDate))) {
            if (FATTENING.equals(type) && eventDate.isBefore(weanDate)
                && (prior == null || !FATTENING.equals(prior.newPigType()))) {
                return PIGLET;
            }
            if (PIGLET.equals(type) && !eventDate.isBefore(weanDate)) {
                return FATTENING;
            }
        }
        return type;
    }

    private static void addGroup(Set<Long> sows, Set<Long> fattening, long pigId, String type, String status) {
        if ("END".equals(status)) {
            return;
        }
        if (FATTENING.equals(type)) {
            fattening.add(pigId);
        } else if (SOW.equals(type) && isProductionSowStatus(status)) {
            sows.add(pigId);
        }
    }

    Map<String, Object> firstBreedingMeasures(String tenantId, LocalDateTime from, LocalDateTime to) {
        return mapper.firstBreedingMeasures(tenantId, from, to);
    }

    int repeatBreedingCount(String tenantId, LocalDateTime from, LocalDateTime to) {
        return mapper.countRepeatBreeding(tenantId, from, to);
    }

    Map<String, Object> monthlyFarrowMeasures(String tenantId, LocalDateTime from, LocalDateTime to) {
        return mapper.monthlyFarrowMeasures(tenantId, from, to);
    }

    Map<String, Object> medicineTimes(String tenantId, LocalDateTime from, LocalDateTime to) {
        return mapper.medicineTimes(tenantId, from, to);
    }

    Map<String, Object> weaningTransferMeasures(String tenantId, LocalDateTime from, LocalDateTime to) {
        return mapper.weaningTransferMeasures(tenantId, from, to);
    }

    List<LocalDate> priorMaternalWeanDates(String tenantId, LocalDateTime from, LocalDateTime to,
                                         LocalDate windowStart, LocalDateTime cutoff) {
        return mapper.priorMaternalWeanDates(tenantId, from, to, windowStart, cutoff);
    }

    MonthlyTerminals monthlyTerminals(String tenantId, LocalDateTime from, LocalDateTime to, Weaning weaning) {
        Map<Long, WeanedLitter> litters = weaning.litters().stream()
            .collect(Collectors.toMap(WeanedLitter::farrowId, litter -> litter));
        LocalDateTime scanFrom = weaning.litters().stream().map(litter -> litter.farrowDate().atStartOfDay())
            .min(LocalDateTime::compareTo).filter(date -> date.isBefore(from)).orElse(from);
        LocalDateTime scanTo = weaning.lactationThrough().values().stream().map(day -> day.plusDays(1).atStartOfDay())
            .max(LocalDateTime::compareTo).filter(date -> date.isAfter(to)).orElse(to);

        List<TerminalEvent> events = mapper.terminalEvents(tenantId, scanFrom, scanTo);
        var histories = typeHistories(tenantId, events.stream().map(TerminalEvent::pigId).collect(Collectors.toSet()));
        int pigletDeaths = 0;
        int sowDeaths = 0;
        int sowCulls = 0;
        int fatteningDeaths = 0;
        int fatteningCulls = 0;
        int lossCount = 0;
        int unlinked = 0;
        int paritySum = 0;
        int parityCount = 0;
        var deathAge = new AgeTotals();
        for (TerminalEvent event : events) {
            var history = histories.getOrDefault(event.pigId(), List.of());
            String type = terminalType(history, event);
            boolean inMonth = !event.eventAt().isBefore(from) && event.eventAt().isBefore(to);
            boolean death = "DIE".equals(event.eventType());
            if (PIGLET.equals(type)) {
                WeanedLitter birth = event.birthFarrowId() == null ? null : litters.get(event.birthFarrowId());
                LocalDate date = event.eventAt().toLocalDate();
                if (birth != null && !date.isBefore(birth.farrowDate())
                    && !date.isAfter(weaning.lactationThrough().get(birth.farrowId()))) {
                    lossCount++;
                }
                if (inMonth && event.birthFarrowId() == null) {
                    unlinked++;
                }
                if (inMonth && death) {
                    pigletDeaths++;
                    deathAge.add(event.birthDate(), date);
                }
            } else if (inMonth && FATTENING.equals(type)) {
                if (death) {
                    fatteningDeaths++;
                } else {
                    fatteningCulls++;
                }
            } else if (inMonth && SOW.equals(type) && isProductionSowStatus(event.oldStatus())) {
                if (death) {
                    sowDeaths++;
                } else {
                    sowCulls++;
                    if (event.parity() != null) {
                        paritySum += event.parity();
                        parityCount++;
                    }
                }
            }
        }
        int lossLiveBorn = weaning.litters().stream().mapToInt(WeanedLitter::liveBorn).sum();
        return new MonthlyTerminals(pigletDeaths, sowDeaths, sowCulls, fatteningDeaths, fatteningCulls,
            lossCount, lossLiveBorn, unlinked, deathAge, paritySum, parityCount);
    }

    private static String terminalType(List<TypeChange> history, TerminalEvent event) {
        if (event.oldPigType() != null) {
            return event.oldPigType();
        }
        // 优先沿类型流水回放，缺类型链时从事件前状态、死亡单冻结值、在场快照或出生窝取得锚点。
        String fallback = switch (Objects.toString(event.oldStatus(), "")) {
            case "HB", "PZ", "FM", "DN", "LC", "KH", "FQ" -> SOW;
            case "YF" -> FATTENING;
            default -> event.deathPigType() != null ? event.deathPigType()
                : event.snapshotPigType() != null ? event.snapshotPigType()
                : event.birthFarrowId() != null || event.weanDate() != null ? PIGLET : null;
        };
        return typeBefore(history, event.eventId(), event.changeTime(), null, fallback, event.weanDate());
    }

    private static boolean isProductionSowStatus(String status) {
        return status != null && !status.isEmpty() && !"HB".equals(status) && !"END".equals(status);
    }

    static final class AgeTotals {
        private int days;
        private int count;
        private int missing;

        void add(LocalDate birth, LocalDate date) {
            if (birth == null) {
                missing++;
            } else if (!birth.isAfter(date)) {
                days += (int) ChronoUnit.DAYS.between(birth, date);
                count++;
            }
        }

        int days() { return days; }
        int count() { return count; }
        int missing() { return missing; }
    }

    record MonthlyTerminals(int pigletDeaths, int sowDeaths, int sowCulls, int fatteningDeaths, int fatteningCulls,
                            int lossCount, int lossLiveBorn, int unlinked, AgeTotals deathAge,
                            int paritySum, int parityCount) {}

    record Weaning(int count, int normalCount, int lactationDays, int validLactationCount, int birthLitterPiglets,
                   List<WeanedLitter> litters, Map<Long, LocalDate> lactationThrough) {}

    record DailyGroups(int productionSowHeadDays, int fatteningHeadDays, int endFattening, int endPiglets, int newFattening) {}

    record Marketing(int count, BigDecimal weight, AgeTotals age, BigDecimal netGain, int gainDays) {}
}
