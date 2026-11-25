package org.dromara.djs.plant.indicator.service;

import org.dromara.djs.plant.indicator.domain.ProductionIndicator;
import org.dromara.djs.plant.indicator.domain.ProductionIndicator.Annual;
import org.dromara.djs.plant.indicator.domain.ProductionIndicator.Daily;
import org.dromara.djs.plant.indicator.domain.ProductionIndicator.IdleTotals;
import org.dromara.djs.plant.indicator.domain.ProductionIndicator.Monthly;
import org.dromara.djs.plant.indicator.mapper.PlantPlotSnapshotMapper.Totals;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.DailyPerformance;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.Detail;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.FarmEvent;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.Inbound;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.Member;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.Performance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 为一个自然年计算各统计期。三个频次使用同一份源数据和同一套历史配对。 */
final class ProductionIndicatorCalculator {

    private final LocalDate from;
    private final LocalDate until;
    private final int year;
    private final Bucket annual = new Bucket();
    private final Map<YearMonth, Bucket> months = new HashMap<>();
    private final Map<LocalDate, Bucket> days = new HashMap<>();
    private final Map<Long, LocalDate> rotationDates = new HashMap<>();
    private List<Member> members = List.of();

    ProductionIndicatorCalculator(LocalDate from, LocalDate until, List<Detail> details,
                                  List<FarmEvent> events, List<Inbound> inbound, List<Performance> performance) {
        this.from = from;
        this.until = until;
        this.year = from.getYear();
        addDetails(details, events);
        Map<Long, List<Detail>> byPlot = details.stream().filter(d -> d.plotId() != null)
            .collect(Collectors.groupingBy(Detail::plotId, Collectors.toCollection(ArrayList::new)));
        addFarmEvents(events, byPlot);
        for (Inbound flow : inbound) {
            Long crop = flow.cropId();
            if (crop == null && flow.harvestBatchId() == null) {
                crop = inferInboundCrop(flow.date(), byPlot.getOrDefault(flow.plotId(), List.of()));
            }
            for (Bucket bucket : buckets(flow.date())) {
                bucket.inbound(flow, crop);
            }
        }
        for (Performance entry : performance) {
            month(YearMonth.parse(entry.statMonth())).performance(entry);
            annual.performance(entry);
        }
    }

    void addDockReceipts(List<DockFirstInbound.Result> receipts) {
        for (var receipt : receipts) {
            Inbound inbound = receipt.inbound();
            for (Bucket bucket : buckets(inbound.date())) {
                if (receipt.unmatched()) {
                    bucket.dockUnmatched++;
                }
                if (inbound.weight().signum() > 0) {
                    bucket.inbound(inbound, inbound.cropId());
                }
            }
        }
    }

    void addDailyPerformance(List<DailyPerformance> estimates, List<Member> roster) {
        members = roster;
        Map<Long, List<Member>> byTeam = roster.stream().collect(Collectors.groupingBy(Member::teamId));
        for (DailyPerformance entry : estimates) {
            var day = day(entry.date());
            day.performanceEstimate = day.performanceEstimate.add(entry.amount());
            byTeam.getOrDefault(entry.teamId(), List.of()).stream().filter(m -> m.onRoster(entry.date()))
                .map(Member::userId).forEach(day.estimatedMembers::add);
        }
    }

    private void addDetails(List<Detail> details, List<FarmEvent> events) {
        Set<PlanPlotCrop> transplantSources = events.stream().filter(e -> "transplant".equals(e.type()))
            .map(e -> new PlanPlotCrop(e.planId(), e.plotId(), e.cropId()))
            .collect(Collectors.toSet());
        Map<Long, BigDecimal> losses = disasterLosses(details, events);
        for (Detail detail : details) {
            boolean transplantClosure = transplantSources.contains(
                new PlanPlotCrop(detail.planId(), detail.plotId(), detail.cropId()));
            addDetail(detail, transplantClosure, losses.getOrDefault(detail.id(), BigDecimal.ZERO));
        }
    }

    private static Map<Long, BigDecimal> disasterLosses(List<Detail> details, List<FarmEvent> events) {
        Map<PlanPlotCrop, Detail> byBatch = details.stream().filter(d -> d.planId() != null)
            .collect(Collectors.toMap(d -> new PlanPlotCrop(d.planId(), d.plotId(), d.cropId()), d -> d,
                (first, second) -> first.id() > second.id() ? first : second));
        Map<PlotCrop, List<Detail>> byCrop = details.stream().filter(d -> d.plantBegin() != null)
            .collect(Collectors.groupingBy(d -> new PlotCrop(d.plotId(), d.cropId())));
        Map<Long, Detail> byId = details.stream().collect(Collectors.toMap(Detail::id, d -> d));
        Map<Long, BigDecimal> losses = new HashMap<>();
        Comparator<Detail> byPlantStart = Comparator.comparing(Detail::plantBegin).thenComparingLong(Detail::id);
        for (FarmEvent event : events) {
            if (!"disaster".equals(event.type()) || event.lossYield() == null) {
                continue;
            }
            Detail detail;
            if (event.planId() != null) {
                detail = byBatch.get(new PlanPlotCrop(event.planId(), event.plotId(), event.cropId()));
            } else if (event.detailId() != null) {
                detail = byId.get(event.detailId());
            } else {
                detail = byCrop.getOrDefault(new PlotCrop(event.plotId(), event.cropId()), List.of()).stream()
                    .filter(d -> !d.plantBegin().isAfter(event.date()))
                    .max(byPlantStart).orElse(null);
            }
            if (samePlot(event, detail) && Objects.equals(event.cropId(), detail.cropId())) {
                losses.merge(detail.id(), event.lossYield(), BigDecimal::add);
            }
        }
        return losses;
    }

    private static boolean isActualHarvest(FarmEvent event) {
        return "harvest".equals(event.type()) || "harvest_activity".equals(event.type());
    }

    private record PlotCrop(Long plotId, Long cropId) {
    }

    private record PlanPlotCrop(Long planId, Long plotId, Long cropId) {
    }

    private void addDetail(Detail d, boolean transplantClosure, BigDecimal disasterLoss) {
        if (inYear(d.plantEnd())) {
            for (Bucket bucket : buckets(d.plantEnd())) {
                bucket.plant(d);
            }
        }
        if (inYear(d.harvestEnd()) && !transplantClosure) {
            for (Bucket bucket : buckets(d.harvestEnd())) {
                bucket.harvest(d.plotId());
            }
            month(YearMonth.from(d.harvestEnd())).addYield(d, disasterLoss);
        }
        if (Objects.equals(d.planYear(), year)) {
            annual.plan(inYear(d.plantEnd()));
            if (d.planMonth() != null && d.planMonth() >= 1 && d.planMonth() <= 12) {
                YearMonth period = YearMonth.of(year, d.planMonth());
                month(period).plan(inYear(d.plantEnd()) && period.equals(YearMonth.from(d.plantEnd())));
            }
        }
    }

    private void addFarmEvents(List<FarmEvent> events, Map<Long, List<Detail>> byPlot) {
        Map<Long, Detail> byId = byPlot.values().stream().flatMap(List::stream)
            .collect(Collectors.toMap(Detail::id, d -> d));
        // 精确关联优先。跨月/年回放不能抢占后续已有明确归属的茬次。
        Set<Long> reserved = events.stream().filter(e -> "rotation".equals(e.type()) && e.detailId() != null)
            .filter(e -> samePlot(e, byId.get(e.detailId())))
            .map(FarmEvent::detailId).collect(Collectors.toCollection(HashSet::new));
        for (FarmEvent event : events) {
            LocalDate date = event.date();
            List<Bucket> buckets = inYear(date) ? buckets(date) : List.of();
            if ("disaster".equals(event.type())) {
                buckets.forEach(bucket -> bucket.disaster(event));
                continue;
            }
            if (isActualHarvest(event)) {
                continue;
            }
            buckets.forEach(bucket -> bucket.farm(event.area()));
            if (!"rotation".equals(event.type()) || date == null) {
                continue;
            }
            Detail target = event.detailId() == null
                ? inferRotation(event, byPlot.getOrDefault(event.plotId(), List.of()), reserved)
                : byId.get(event.detailId());
            Integer waiting = null;
            BigDecimal area = event.area();
            if (samePlot(event, target)) {
                if (rotationDates.putIfAbsent(target.id(), date) != null) {
                    continue; // 同一明细的重复退茬单只算一次。
                }
                if (target.harvestEnd() != null && !target.harvestEnd().isAfter(date)) {
                    waiting = Math.toIntExact(ChronoUnit.DAYS.between(target.harvestEnd(), date));
                }
                if (area == null) {
                    area = target.area();
                }
            }
            for (Bucket bucket : buckets) {
                bucket.rotation(event.plotId(), area, waiting);
            }
        }
    }

    private Detail inferRotation(FarmEvent event, List<Detail> candidates, Set<Long> reserved) {
        List<Detail> available = candidates.stream()
            .filter(d -> d.harvestEnd() != null && !d.harvestEnd().isAfter(event.date()))
            .filter(d -> !reserved.contains(d.id()) && !rotationDates.containsKey(d.id()))
            .sorted(Comparator.comparing(Detail::harvestEnd).reversed())
            .limit(2).toList();
        if (available.isEmpty() || (available.size() == 2
            && available.get(0).harvestEnd().equals(available.get(1).harvestEnd()))) {
            return null;
        }
        return available.getFirst();
    }

    private Long inferInboundCrop(LocalDate date, List<Detail> candidates) {
        List<Long> crops = candidates.stream()
            .filter(d -> d.plantEnd() != null && !d.plantEnd().isAfter(date))
            .filter(d -> {
                LocalDate rotation = rotationDates.get(d.id());
                LocalDate activeThrough = rotation == null ? d.harvestEnd() : rotation;
                return activeThrough == null || !activeThrough.isBefore(date);
            })
            .map(Detail::cropId).filter(Objects::nonNull).distinct().limit(2).toList();
        return crops.size() == 1 ? crops.getFirst() : null;
    }

    private static boolean samePlot(FarmEvent event, Detail detail) {
        return detail != null && Objects.equals(event.plotId(), detail.plotId());
    }

    private boolean inYear(LocalDate date) {
        return date != null && !date.isBefore(from) && date.isBefore(until);
    }

    private Bucket month(YearMonth period) {
        return months.computeIfAbsent(period, key -> new Bucket());
    }

    private Bucket day(LocalDate date) {
        return days.computeIfAbsent(date, key -> new Bucket());
    }

    private List<Bucket> buckets(LocalDate date) {
        return List.of(day(date), month(YearMonth.from(date)), annual);
    }

    Daily daily(LocalDate date, Totals snapshot) {
        Bucket b = day(date);
        return new Daily(date, sumAreas(b.plantedByPlot), b.plantedCrops.size(), b.plantedByPlot.size(),
            b.farmWorkCount, b.farmWorkArea, b.harvestPlots.size(), b.inboundWeight, b.inboundCrops.size(),
            b.inboundPlots.size(), b.inboundUnmatched, b.rotationByPlot.size(), sumAreas(b.rotationByPlot),
            b.rotationWaitDays, b.rotationWaitCount, b.rotationUnmatched, b.disasterPlots.size(),
            ProductionIndicator.divide(b.performanceEstimate, b.estimatedMembers.size(), 2), b.dockUnmatched,
            snapshot == null ? null : snapshot.idlePlotCount(),
            snapshot == null ? null : snapshot.idleArea(),
            snapshot == null ? null : snapshot.inFieldArea(),
            snapshot == null ? null : snapshot.inFieldCropCount(),
            snapshot == null ? null : snapshot.inFieldPlotCount(),
            snapshot == null ? null : snapshot.totalPlotCount(),
            snapshot == null ? null : snapshot.overdueHarvestPlotCount(),
            snapshot == null ? null : snapshot.pendingRotationPlotCount());
    }

    Monthly monthly(YearMonth period, Totals snapshot) {
        Bucket b = month(period);
        return new Monthly(period.atDay(1), b.plantedArea, b.plantedCrops.size(), b.plantedRounds,
            b.planCount, b.planCompleted, b.farmWorkCount, b.farmWorkArea, b.harvestRounds,
            b.inboundWeight, b.inboundCrops.size(), b.inboundPlots.size(), b.inboundUnmatched,
            b.rotationRounds, b.rotationArea, b.rotationUnmatched, b.performanceAmount, b.yieldCompletionPercent(),
            b.rotationAverageDays(), b.disasterPlots.size(), performancePerPerson(b, period.atEndOfMonth()),
            b.disasterLossArea.setScale(3, RoundingMode.HALF_UP), b.disasterLossYield, b.dockUnmatched,
            snapshot == null ? null : snapshot.idlePlotCount(),
            snapshot == null ? null : ProductionIndicator.divide(
                BigDecimal.valueOf(100L * (snapshot.totalPlotCount() - snapshot.idlePlotCount())),
                snapshot.totalPlotCount(), 2),
            snapshot == null ? null : snapshot.overdueHarvestPlotCount(),
            snapshot == null ? null : snapshot.inFieldArea(),
            snapshot == null ? null : snapshot.inFieldPlotCount(),
            snapshot == null ? null : snapshot.inFieldCropCount(),
            snapshot == null ? null : snapshot.idleArea(),
            snapshot == null ? null : snapshot.pendingRotationPlotCount());
    }

    Annual annual(IdleTotals idle, Totals snapshot, int sameDayPlantings) {
        Bucket b = annual;
        return new Annual(year, b.plantedArea, b.planCount, b.planCompleted, b.inboundWeight,
            b.disasterRecords, b.rotationRounds, b.performanceAmount, b.performanceThrough,
            idle.days(), idle.intervals(), b.rotationAverageDays(),
            performancePerPerson(b, from.plusYears(1).minusDays(1)), b.dockUnmatched,
            snapshot == null ? null : ProductionIndicator.divide(
                BigDecimal.valueOf((long) b.plantedRounds + sameDayPlantings), snapshot.totalPlotCount(), 2));
    }

    private BigDecimal performancePerPerson(Bucket b, LocalDate end) {
        LocalDate rosterDate = end.isBefore(until) ? end : until.minusDays(1);
        int count = Math.toIntExact(members.stream().filter(m -> m.onRoster(rosterDate))
            .map(Member::userId).distinct().count());
        return ProductionIndicator.divide(b.performanceAmount, count, 2);
    }

    private static BigDecimal sumAreas(Map<Long, BigDecimal> areas) {
        return areas.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static final class Bucket {
        private final Map<Long, BigDecimal> plantedByPlot = new HashMap<>();
        private final Map<Long, BigDecimal> rotationByPlot = new HashMap<>();
        private final Set<Long> plantedCrops = new HashSet<>();
        private final Set<Long> harvestPlots = new HashSet<>();
        private final Set<Long> inboundCrops = new HashSet<>();
        private final Set<Long> inboundPlots = new HashSet<>();
        private final Set<Long> disasterPlots = new HashSet<>();
        private BigDecimal plantedArea = BigDecimal.ZERO;
        private int plantedRounds;
        private int planCount;
        private int planCompleted;
        private int farmWorkCount;
        private BigDecimal farmWorkArea = BigDecimal.ZERO;
        private int harvestRounds;
        private BigDecimal inboundWeight = BigDecimal.ZERO;
        private int inboundUnmatched;
        private int dockUnmatched;
        private int rotationRounds;
        private BigDecimal rotationArea = BigDecimal.ZERO;
        private int rotationWaitDays;
        private int rotationWaitCount;
        private int rotationUnmatched;
        private int disasterRecords;
        private BigDecimal disasterLossArea = BigDecimal.ZERO;
        private BigDecimal disasterLossYield = BigDecimal.ZERO;
        private BigDecimal actualYield;
        private BigDecimal netExpectedYield = BigDecimal.ZERO;
        private final Set<Long> estimatedMembers = new HashSet<>();
        private BigDecimal performanceEstimate = BigDecimal.ZERO;
        private BigDecimal performanceAmount = BigDecimal.ZERO;
        private String performanceThrough;

        private void plant(Detail detail) {
            if (detail.cropId() != null) {
                plantedCrops.add(detail.cropId());
            }
            if (detail.plotId() != null) {
                BigDecimal area = zeroIfNull(detail.area());
                plantedByPlot.merge(detail.plotId(), area, BigDecimal::max);
                plantedArea = plantedArea.add(area);
                plantedRounds++;
            }
        }

        private void harvest(Long plotId) {
            if (plotId != null) {
                harvestPlots.add(plotId);
                harvestRounds++;
            }
        }

        private void plan(boolean completed) {
            planCount++;
            if (completed) {
                planCompleted++;
            }
        }

        private void farm(BigDecimal area) {
            farmWorkCount++;
            farmWorkArea = farmWorkArea.add(zeroIfNull(area));
        }

        private void inbound(Inbound flow, Long cropId) {
            inboundWeight = inboundWeight.add(flow.weight());
            if (flow.plotId() != null) {
                inboundPlots.add(flow.plotId());
            }
            if (cropId == null) {
                inboundUnmatched++;
            } else {
                inboundCrops.add(cropId);
            }
        }

        private void rotation(Long plotId, BigDecimal area, Integer waiting) {
            if (plotId != null) {
                BigDecimal workArea = zeroIfNull(area);
                rotationByPlot.merge(plotId, workArea, BigDecimal::max);
                rotationArea = rotationArea.add(workArea);
                rotationRounds++;
            }
            if (waiting == null) {
                rotationUnmatched++;
            } else {
                rotationWaitDays += waiting;
                rotationWaitCount++;
            }
        }

        private BigDecimal rotationAverageDays() {
            return ProductionIndicator.divide(BigDecimal.valueOf(rotationWaitDays), rotationRounds, 1);
        }

        private void disaster(FarmEvent event) {
            if (event.plotId() != null) {
                disasterPlots.add(event.plotId());
                disasterRecords++;
            }
            disasterLossArea = disasterLossArea.add(zeroIfNull(event.area())
                .multiply(zeroIfNull(event.lossRate())).movePointLeft(2));
            disasterLossYield = disasterLossYield.add(zeroIfNull(event.lossYield()));
        }

        private void addYield(Detail detail, BigDecimal disasterLoss) {
            if (detail.actualYield() == null) {
                return;
            }
            netExpectedYield = netExpectedYield.add(zeroIfNull(detail.expectedYield())
                .subtract(disasterLoss).max(BigDecimal.ZERO));
            actualYield = zeroIfNull(actualYield).add(detail.actualYield());
        }

        private BigDecimal yieldCompletionPercent() {
            return actualYield == null || netExpectedYield.signum() == 0 ? null
                : actualYield.movePointRight(2).divide(netExpectedYield, 2, RoundingMode.HALF_UP);
        }

        private void performance(Performance entry) {
            performanceAmount = performanceAmount.add(entry.amount());
            if (performanceThrough == null || performanceThrough.compareTo(entry.statMonth()) < 0) {
                performanceThrough = entry.statMonth();
            }
        }

        private static BigDecimal zeroIfNull(BigDecimal number) {
            return number == null ? BigDecimal.ZERO : number;
        }
    }
}
