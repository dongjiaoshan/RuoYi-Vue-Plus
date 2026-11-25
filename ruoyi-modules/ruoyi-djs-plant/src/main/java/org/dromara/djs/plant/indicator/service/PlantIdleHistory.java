package org.dromara.djs.plant.indicator.service;

import org.dromara.djs.plant.indicator.domain.ProductionIndicator.IdleTotals;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.IdleInterval;
import org.dromara.djs.plant.indicator.mapper.ProductionIndicatorSourceMapper.IdleTransition;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

final class PlantIdleHistory {

    private static final LocalDate UNKNOWN_START = LocalDate.of(2026, 8, 1);

    private PlantIdleHistory() {
    }

    static IdleTotals calculate(LocalDate from, LocalDate until, List<IdleInterval> recorded,
                                List<IdleTransition> transitions) {
        Map<Long, PlotHistory> plots = new HashMap<>();
        for (IdleInterval interval : recorded) {
            plots.computeIfAbsent(interval.plotId(), ignored -> new PlotHistory()).recorded.add(interval);
        }
        for (IdleTransition transition : transitions) {
            var plot = plots.computeIfAbsent(transition.plotId(), ignored -> new PlotHistory());
            (transition.rotation() ? plot.rotations : plot.planted).add(transition.date());
        }
        long days = 0;
        int intervals = 0;
        for (PlotHistory plot : plots.values()) {
            for (Range range : plot.ranges()) {
                LocalDate start = range.start().isBefore(from) ? from : range.start();
                LocalDate end = range.end() == null || range.end().isAfter(until) ? until : range.end();
                if (start.isBefore(end)) {
                    days += ChronoUnit.DAYS.between(start, end);
                    intervals++;
                }
            }
        }
        return new IdleTotals(days, intervals);
    }

    private record Range(LocalDate start, LocalDate end) {
        private boolean overlaps(Range other) {
            return (end == null || other.start.isBefore(end))
                && (other.end == null || start.isBefore(other.end));
        }
    }

    private static final class PlotHistory {
        private final List<IdleInterval> recorded = new ArrayList<>();
        private final TreeSet<LocalDate> rotations = new TreeSet<>();
        private final TreeSet<LocalDate> planted = new TreeSet<>();

        private List<Range> ranges() {
            List<Range> ranges = new ArrayList<>();
            for (IdleInterval interval : recorded) {
                LocalDate start = interval.start();
                if ("history_init".equals(interval.source()) && UNKNOWN_START.equals(start)) {
                    LocalDate rotation = rotations.floor(start);
                    LocalDate lastPlant = planted.floor(start);
                    if (rotation != null && (lastPlant == null || rotation.isAfter(lastPlant))) {
                        start = rotation;
                    }
                }
                ranges.add(new Range(start, interval.end()));
            }
            // 同一次恢复前的多张退茬单只补一个闭合段。有区间记录时保留记录的恢复日。
            for (LocalDate rotation : rotations) {
                LocalDate end = planted.ceiling(rotation);
                if (end != null && rotation.isBefore(end)) {
                    Range candidate = new Range(rotation, end);
                    if (ranges.stream().noneMatch(range -> range.start().equals(rotation) || candidate.overlaps(range))) {
                        ranges.add(candidate);
                    }
                }
            }
            return ranges;
        }
    }
}
