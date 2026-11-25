package org.dromara.djs.plant.plot.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.plant.plot.mapper.PlotIdleIntervalMapper;
import org.dromara.djs.plant.plot.mapper.PlotIdleIntervalMapper.PlotState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PlotIdleIntervalService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int IDLE = 1;

    private final PlotIdleIntervalMapper intervals;

    /** 业务写地块后在原事务内同步，锁定读取确保使用实际落库状态，避免旧快照和并发开段。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAfterPlotWrite(Long plotId, LocalDate businessDay, String source) {
        recordAfterPlotWrite(List.of(plotId), businessDay, source);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAfterPlotWrite(Collection<Long> plotIds, LocalDate businessDay, String source) {
        if (plotIds.isEmpty()) {
            return;
        }
        LocalDate day = businessDay == null ? LocalDate.now(BUSINESS_ZONE) : businessDay;
        Long userId = LoginHelper.getUserId();
        for (PlotState plot : intervals.lockPlots(plotIds)) {
            if (Objects.equals(plot.status(), IDLE) && "0".equals(plot.delFlag())) {
                openIfNeeded(plot, day, source, userId);
            } else if (plot.idleOpenIntervalId() != null) {
                intervals.close(plot, day, source, userId);
            }
        }
    }

    private void openIfNeeded(PlotState plot, LocalDate day, String source, Long userId) {
        if (plot.idleOpenIntervalId() != null) {
            return;
        }
        // 补录不可与已结束的空闲段重叠。
        if (plot.idleLastEndedOn() != null && day.isBefore(plot.idleLastEndedOn())) {
            day = plot.idleLastEndedOn();
        }
        long intervalId = IdWorker.getId();
        intervals.open(intervalId, plot, day, source, LoginHelper.getDeptId(), userId);
        intervals.setOpenInterval(plot.id(), intervalId);
    }
}
