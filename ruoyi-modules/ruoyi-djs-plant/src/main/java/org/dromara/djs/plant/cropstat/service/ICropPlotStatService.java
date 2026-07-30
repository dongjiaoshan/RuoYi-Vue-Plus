package org.dromara.djs.plant.cropstat.service;

import org.dromara.djs.plant.cropstat.domain.vo.CropPlotStatVo;

import java.util.List;

/**
 * 作物「在田地块」聚合统计 Service。
 *
 * @author djs
 */
public interface ICropPlotStatService {

    /**
     * 按作物聚合在田地块统计（剩余地块 / 预计产量 / 最早·最晚可采摘日期）。
     *
     * @return 每作物一行，无在田地块的作物不出现
     */
    List<CropPlotStatVo> listPlotStat();
}
