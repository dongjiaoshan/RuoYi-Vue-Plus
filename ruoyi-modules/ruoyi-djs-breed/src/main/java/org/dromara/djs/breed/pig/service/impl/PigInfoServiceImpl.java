package org.dromara.djs.breed.pig.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.pig.domain.PigInfo;
import org.dromara.djs.breed.pig.domain.vo.PigInfoVo;
import org.dromara.djs.breed.pig.mapper.PigInfoMapper;
import org.dromara.djs.breed.pig.service.IPigInfoService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 猪只基础信息Service实现。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Slf4j
@Service
public class PigInfoServiceImpl implements IPigInfoService {

    private final PigInfoMapper pigInfoMapper;

    public PigInfoServiceImpl(PigInfoMapper pigInfoMapper) {
        this.pigInfoMapper = pigInfoMapper;
    }

    @Override
    public PigInfoVo queryByEarTag(String earTag) {
        if (earTag == null || earTag.isBlank()) {
            log.warn("[pig-info-service] 耳号为空");
            throw new ServiceException("耳号不能为空");
        }
        PigInfoVo vo = pigInfoMapper.selectVoByEarTag(earTag);
        if (vo == null) {
            log.warn("[pig-info-service] 耳号不存在 earTag={}", earTag);
            throw new ServiceException("耳号不存在");
        }
        return vo;
    }

    @Override
    public PigInfo getByEarTag(String earTag) {
        if (earTag == null || earTag.isBlank()) {
            log.warn("[pig-info-service] 耳号为空");
            throw new ServiceException("耳号不能为空");
        }
        return pigInfoMapper.selectByEarTag(earTag);
    }

    @Override
    public int updateStatusToDeath(Long pigId) {
        if (pigId == null) {
            log.warn("[pig-info-service] 猪只ID为空");
            throw new ServiceException("猪只ID不能为空");
        }
        LambdaUpdateWrapper<PigInfo> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PigInfo::getId, pigId)
                .eq(PigInfo::getDelFlag, "0");
        PigInfo updateEntity = new PigInfo();
        updateEntity.setCurrentStatus("END");
        updateEntity.setEndReason("DEAD");
        updateEntity.setStatusStartedAt(LocalDateTime.now());
        int result = pigInfoMapper.update(updateEntity, wrapper);
        log.info("[pig-info-service] 更新猪只状态为死亡 pigId={} result={}", pigId, result);
        return result;
    }

}
