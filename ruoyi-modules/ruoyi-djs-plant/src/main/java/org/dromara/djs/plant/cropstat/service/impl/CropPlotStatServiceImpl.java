package org.dromara.djs.plant.cropstat.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.djs.plant.cropstat.domain.vo.CropPlotStatVo;
import org.dromara.djs.plant.cropstat.mapper.CropPlotStatMapper;
import org.dromara.djs.plant.cropstat.service.ICropPlotStatService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 作物「在田地块」聚合统计 Service 实现。
 *
 * <p>纯只读聚合，统计口径全部下推 SQL（见 {@link CropPlotStatMapper#selectPlotStat()}）；
 * 此处只做空值归一，保证前端拿到的 {@code remainPlotCount} / {@code expectYield} 不为 null。</p>
 *
 * @author djs
 */
@Service
@RequiredArgsConstructor
public class CropPlotStatServiceImpl implements ICropPlotStatService {

    private final CropPlotStatMapper cropPlotStatMapper;

    @Override
    public List<CropPlotStatVo> listPlotStat() {
        List<CropPlotStatVo> list = cropPlotStatMapper.selectPlotStat();
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        for (CropPlotStatVo vo : list) {
            if (vo.getRemainPlotCount() == null) {
                vo.setRemainPlotCount(0);
            }
            if (vo.getExpectYield() == null) {
                vo.setExpectYield(BigDecimal.ZERO);
            }
        }
        return list;
    }
}
