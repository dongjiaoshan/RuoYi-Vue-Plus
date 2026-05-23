package org.dromara.djs.breed.pig.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.common.enums.PigStatusEnum;
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
    public PigInfo getById(Long pigId) {
        if (pigId == null) {
            log.warn("[pig-info-service] 猪只ID为空");
            throw new ServiceException("猪只ID不能为空");
        }
        return pigInfoMapper.selectByIdWithBarn(pigId);
    }

    @Override
    public int updateStatusToDeath(PigInfo pigInfo) {
        if (pigInfo == null) {
            log.warn("[pig-info-service] 猪只信息为空");
            throw new ServiceException("猪只信息不能为空");
        }

        Long pigId = pigInfo.getId();

        // 设置更新字段（使用枚举类）
        pigInfo.setCurrentStatus(PigStatusEnum.D.getCode());
        pigInfo.setEndReason(PigStatusEnum.D.getCode());
        pigInfo.setStatusStartedAt(LocalDateTime.now());

        // MyBatis-Plus 乐观锁插件自动处理 version
        int result = pigInfoMapper.updateById(pigInfo);

        if (result == 0) {
            log.warn("[pig-info-service] 乐观锁更新失败，可能存在并发修改 pigId={}", pigId);
            throw new ServiceException("数据更新失败，请刷新后重试");
        }

        log.info("[pig-info-service] 更新猪只状态为死亡 pigId={} result={}", pigId, result);
        return result;
    }

}
