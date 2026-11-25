package org.dromara.djs.plant.indicator.service;

import cn.hutool.core.collection.ListUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.plant.indicator.mapper.PlantPlotSnapshotMapper;
import org.dromara.djs.plant.indicator.mapper.PlantPlotSnapshotMapper.CapturedPlot;
import org.dromara.djs.plant.indicator.mapper.PlantPlotSnapshotMapper.CurrentPlotRow;
import org.dromara.djs.plant.indicator.mapper.PlantPlotSnapshotMapper.Totals;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.InstantSource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class PlotSnapshotService {

    private static final ZoneId SHANGHAI = ZoneId.of(ProductionIndicatorService.BUSINESS_ZONE);

    private final PlantPlotSnapshotMapper mapper;
    private final InstantSource time;

    @Autowired
    public PlotSnapshotService(PlantPlotSnapshotMapper mapper) {
        this(mapper, InstantSource.system());
    }

    public PlotSnapshotService(PlantPlotSnapshotMapper mapper, InstantSource time) {
        this.mapper = mapper;
        this.time = time;
    }

    public LocalDate today() {
        return LocalDate.ofInstant(time.instant(), SHANGHAI);
    }

    @Transactional(rollbackFor = Exception.class)
    public void capture(LocalDate date) {
        LocalDate businessDate = today();
        if (date != null && date.isBefore(businessDate)) {
            // 由 DjsJobRunner 将跳过原因写入任务审计。
            throw new ServiceException("过去日期不补拍快照，已跳过");
        }
        if (!businessDate.equals(date)) {
            return;
        }
        String tenantId = ProductionIndicatorService.currentTenant();
        // 日期行串行化同一天的重拍，并区分「成功采集、零有效地块」与「未采集」。
        mapper.lockDay(IdWorker.getId(), tenantId, date);
        if (!today().equals(date)) {
            throw new ServiceException("已跨业务日，取消收工快照");
        }
        var rows = mapper.selectCurrentPlots(tenantId);
        if (!today().equals(date)) {
            throw new ServiceException("已跨业务日，取消收工快照");
        }

        Map<Long, PlotState> states = new HashMap<>();
        for (CurrentPlotRow row : rows) {
            states.computeIfAbsent(row.plotId(), ignored -> new PlotState(row)).addRound(row, date);
        }
        var plots = states.values().stream().map(PlotState::toSnapshot).toList();
        mapper.deleteDay(tenantId, date);
        for (var batch : ListUtil.partition(plots, 250)) {
            mapper.insertPlots(tenantId, date, batch);
        }
    }

    NavigableMap<LocalDate, Totals> totals(String tenantId, LocalDate from, LocalDate to) {
        NavigableMap<LocalDate, Totals> snapshots = new TreeMap<>();
        for (Totals row : mapper.selectTotals(tenantId, from, to)) {
            snapshots.put(row.snapDate(), row);
        }
        return snapshots;
    }

    private static final class PlotState {
        private final CurrentPlotRow plot;
        private final Set<Long> cropIds = new TreeSet<>();
        private boolean inField;
        private boolean pendingRotation;
        private boolean overdue;

        private PlotState(CurrentPlotRow plot) {
            this.plot = plot;
        }

        private void addRound(CurrentPlotRow round, LocalDate date) {
            String harvestStatus = round.harvestStatus();
            if ("completed".equals(harvestStatus)) {
                pendingRotation = true;
            }
            if ("pending".equals(harvestStatus) || "picking".equals(harvestStatus)) {
                inField = true;
                if (round.cropId() != null) {
                    cropIds.add(round.cropId());
                }
                overdue |= round.lastHarvestDate() != null && round.lastHarvestDate().isBefore(date);
            }
        }

        private CapturedPlot toSnapshot() {
            String crops = cropIds.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
            return new CapturedPlot(IdWorker.getId(), plot.plotId(), plot.zoneId(), plot.area(), plot.plotStatus() == 1,
                inField, pendingRotation, overdue, crops);
        }
    }
}
