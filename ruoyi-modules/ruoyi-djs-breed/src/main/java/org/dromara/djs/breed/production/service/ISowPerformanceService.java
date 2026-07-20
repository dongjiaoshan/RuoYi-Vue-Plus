package org.dromara.djs.breed.production.service;

import org.dromara.djs.breed.production.domain.vo.SowPerformanceVo;

import java.util.List;

/**
 * 母猪生产指标 Service（BRD-LIST-001 详情 tab2 数据源；只读 SELECT，
 * 数据由 {@code BRD-DASH-001} 定时任务回写）。
 *
 * @author djs
 * @since BRD-LIST-001
 */
public interface ISowPerformanceService {

    /**
     * 按 pigId 查所有胎次的生产指标（按 parity DESC 排）。
     *
     * @param pigId 母猪主键
     * @return 该母猪所有胎次的生产指标；无数据返空 list（不抛异常）
     */
    List<SowPerformanceVo> listByPigId(Long pigId);
}
