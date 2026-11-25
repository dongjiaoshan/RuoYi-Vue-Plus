package org.dromara.djs.plant.indicator.service;

import org.dromara.djs.plant.indicator.domain.ProductionIndicator.Annual;
import org.dromara.djs.plant.indicator.domain.ProductionIndicator.Daily;
import org.dromara.djs.plant.indicator.domain.ProductionIndicator.IdleTotals;
import org.dromara.djs.plant.indicator.domain.ProductionIndicator.Monthly;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.Detail;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.FarmEvent;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.Inbound;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.Performance;

import java.math.BigDecimal;
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

    ProductionIndicatorCalculator(LocalDate from, LocalDate until, List<Detail> details,
                                  List<FarmEvent> events, List<Inbound> inbound, List<Performance> performance) {
        this.from = from;
        this.until = until;
        this.year = from.getYear();
        for (Detail detail : details) {
            addDetail(detail);
        }
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

    private void addDetail(Detail d) {
        if (inYear(d.plantEnd())) {
            for (Bucket bucket : buckets(d.plantEnd())) {
                bucket.plant(d);
            }
        }
        if (inYear(d.harvestEnd())) {
            for (Bucket bucket : buckets(d.harvestEnd())) {
                bucket.harvest(d.plotId());
            }
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
                buckets.forEach(bucket -> bucket.disaster(event.plotId()));
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

    Daily daily(LocalDate date) {
        Bucket b = day(date);
        return new Daily(date, sumAreas(b.plantedByPlot), b.plantedCrops.size(), b.plantedByPlot.size(),
            b.farmWorkCount, b.farmWorkArea, b.harvestPlots.size(), b.inboundWeight, b.inboundCrops.size(),
            b.inboundPlots.size(), b.inboundUnmatched, b.rotationByPlot.size(), sumAreas(b.rotationByPlot),
            b.rotationWaitDays, b.rotationWaitCount, b.rotationUnmatched, b.disasterPlots.size());
    }

    Monthly monthly(YearMonth period) {
        Bucket b = month(period);
        return new Monthly(period.atDay(1), b.plantedArea, b.plantedCrops.size(), b.plantedRounds,
            b.planCount, b.planCompleted, b.farmWorkCount, b.farmWorkArea, b.harvestRounds,
            b.inboundWeight, b.inboundCrops.size(), b.inboundPlots.size(), b.inboundUnmatched,
            b.rotationRounds, b.rotationArea, b.rotationUnmatched, b.performanceAmount);
    }

    Annual annual(IdleTotals idle) {
        Bucket b = annual;
        return new Annual(year, b.plantedArea, b.planCount, b.planCompleted, b.inboundWeight,
            b.disasterRecords, b.rotationRounds, b.performanceAmount, b.performanceThrough,
            idle.days(), idle.intervals());
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
        private int rotationRounds;
        private BigDecimal rotationArea = BigDecimal.ZERO;
        private int rotationWaitDays;
        private int rotationWaitCount;
        private int rotationUnmatched;
        private int disasterRecords;
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

        private void disaster(Long plotId) {
            if (plotId != null) {
                disasterPlots.add(plotId);
                disasterRecords++;
            }
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
