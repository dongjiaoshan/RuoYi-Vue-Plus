package org.dromara.djs.breed.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.djs.breed.production.domain.SowPerformance;
import org.dromara.djs.breed.production.domain.vo.SowPerformanceVo;
import org.dromara.djs.breed.production.mapper.SowPerformanceMapper;
import org.dromara.djs.breed.production.service.ISowPerformanceService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 母猪生产指标 Service 实现（BRD-LIST-001 详情 tab2，只读）。
 *
 * @author djs
 * @since BRD-LIST-001
 */
@Service
@RequiredArgsConstructor
public class SowPerformanceServiceImpl implements ISowPerformanceService {

    private final SowPerformanceMapper sowPerformanceMapper;

    @Override
    public List<SowPerformanceVo> listByPigId(Long pigId) {
        if (pigId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<SowPerformance> w = new LambdaQueryWrapper<SowPerformance>()
            .eq(SowPerformance::getPigId, pigId)
            .orderByDesc(SowPerformance::getParity);
        return sowPerformanceMapper.selectVoList(w);
    }
}
