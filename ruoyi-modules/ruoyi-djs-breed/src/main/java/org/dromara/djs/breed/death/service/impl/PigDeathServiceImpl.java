package org.dromara.djs.breed.death.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.breed.common.enums.PigStatusEnum;
import org.dromara.djs.breed.death.domain.PigDeath;
import org.dromara.djs.breed.death.domain.bo.PigDeathBo;
import org.dromara.djs.breed.death.domain.query.PigDeathQuery;
import org.dromara.djs.breed.death.domain.vo.PigDeathVo;
import org.dromara.djs.breed.death.mapper.PigDeathMapper;
import org.dromara.djs.breed.death.service.IPigDeathService;
import org.dromara.djs.breed.pig.domain.PigInfo;
import org.dromara.djs.breed.pig.service.IPigInfoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 猪只死亡记录Service实现。
 *
 * @author djs
 * @since BRD-MD-003
 */
@Slf4j
@Service
public class PigDeathServiceImpl implements IPigDeathService {

    private final PigDeathMapper baseMapper;
    private final IPigInfoService pigInfoService;

    public PigDeathServiceImpl(PigDeathMapper baseMapper, IPigInfoService pigInfoService) {
        this.baseMapper = baseMapper;
        this.pigInfoService = pigInfoService;
    }

    @Override
    public TableDataInfo<PigDeathVo> queryPageList(PigDeathQuery query, PageQuery pageQuery) {
        return TableDataInfo.build(baseMapper.selectVoPage(pageQuery.build(), query));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitDeathInfo(PigDeathBo bo) {
        log.info("[death-service] 开始处理死亡信息提交 pigId={}", bo.getPigId());

        // 1. 通过 pigId 查询猪只信息
        PigInfo pigInfo = pigInfoService.getById(bo.getPigId());

        // 2. 状态校验（终态检查）
        validatePigNotEnded(pigInfo);

        // 3. 检查是否已存在死亡记录
        validateNoExistingDeath(pigInfo.getId());

        // 4. 构建并保存死亡记录（猪只信息从 pigInfo 获取）
        PigDeath death = buildPigDeath(bo, pigInfo);
        int result = baseMapper.insert(death);
        log.info("[death-service] 死亡记录保存成功 deathId={} pigId={}", death.getId(), pigInfo.getId());

        // 5. 更新猪只状态为死亡（MyBatis-Plus 乐观锁自动处理，直接传入对象避免重复查询）
        pigInfoService.updateStatusToDeath(pigInfo);
        log.info("[death-service] 猪只状态更新为死亡 pigId={}", pigInfo.getId());

        return result;
    }

    /**
     * 校验猪只未终结
     */
    private void validatePigNotEnded(PigInfo pigInfo) {
        if (pigInfo == null) {
            throw new ServiceException("猪只不存在");
        }

        // 租户隔离校验（防御性校验）
        String currentTenantId = LoginHelper.getTenantId();
        if (!currentTenantId.equals(pigInfo.getTenantId())) {
            log.warn("[death-service] 租户隔离校验失败 currentTenantId={} pigTenantId={}",
                    currentTenantId, pigInfo.getTenantId());
            throw new ServiceException("无权操作其他租户数据");
        }

        // 终态校验（使用枚举类）
        String currentStatus = pigInfo.getCurrentStatus();
        if (PigStatusEnum.isEndStatus(currentStatus)) {
            PigStatusEnum statusEnum = PigStatusEnum.fromCode(currentStatus);
            String statusDesc = statusEnum != null ? statusEnum.getDesc() : "终结";
            log.warn("[death-service] 猪只已{}，无法提交死亡信息 pigId={}", statusDesc, pigInfo.getId());
            throw new ServiceException("猪只已" + statusDesc + "，无法提交死亡信息");
        }
    }

    /**
     * 校验不存在重复死亡记录
     */
    private void validateNoExistingDeath(Long pigId) {
        PigDeath existingDeath = baseMapper.selectByPigId(pigId);
        if (existingDeath != null) {
            log.warn("[death-service] 猪只已存在死亡记录 pigId={}", pigId);
            throw new ServiceException("该猪只已存在死亡记录，无法重复提交");
        }
    }

    /**
     * 构建死亡记录实体
     * <p>猪只基础信息从 pigInfo 查询获取，确保数据一致性。</p>
     */
    private PigDeath buildPigDeath(PigDeathBo bo, PigInfo pigInfo) {
        PigDeath death = new PigDeath();
        death.setPigId(pigInfo.getId());
        death.setEarNo(pigInfo.getEarNo());  // 从猪只信息获取耳号简版
        death.setDeathDate(bo.getDeathDate());
        death.setDeathPigType(pigInfo.getPigType());  // 从猪只信息获取猪只类型
        death.setDeathKind(bo.getDeathKind());
        death.setDeathReason(bo.getDeathReason());
        death.setDeathDest(bo.getDeathDest());
        death.setDeathWeight(bo.getDeathWeight());
        death.setOssIds(convertOssIdsToString(bo.getOssIds()));
        death.setOperatorId(bo.getOperatorId());
        death.setBarnName(pigInfo.getBarnName());  // 从猪只信息获取栋舍名称
        death.setPenName(pigInfo.getPenName());  // 从猪只信息获取栏位名称
        death.setRemark(bo.getRemark());
        return death;
    }

    /**
     * 将OSS IDs列表转换为逗号分隔字符串。
     */
    private String convertOssIdsToString(List<String> ossIds) {
        if (ossIds == null || ossIds.isEmpty()) {
            return null;
        }
        return String.join(",", ossIds);
    }

}
