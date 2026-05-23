package org.dromara.djs.breed.death.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
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
    public PigDeathVo queryById(Long id) {
        if (id == null) {
            throw new ServiceException("死亡记录ID不能为空");
        }
        PigDeathVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            convertOssIdsToList(vo);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitDeathInfo(PigDeathBo bo) {
        log.info("[death-service] 开始处理死亡信息提交 earNo={}", bo.getEarNo());

        // 1. 校验耳号合法性
        PigInfo pigInfo = pigInfoService.getByEarTag(bo.getEarNo());
        if (pigInfo == null) {
            log.warn("[death-service] 耳号不存在 earNo={}", bo.getEarNo());
            throw new ServiceException("耳号不存在");
        }

        // 2. 租户隔离校验
        String currentTenantId = LoginHelper.getTenantId();
        if (!currentTenantId.equals(pigInfo.getTenantId())) {
            log.warn("[death-service] 租户隔离校验失败 currentTenantId={} pigTenantId={}",
                    currentTenantId, pigInfo.getTenantId());
            throw new ServiceException("无权操作其他租户数据");
        }

        // 3. 校验猪只状态（END 表示已终结，不能再操作）
        String currentStatus = pigInfo.getCurrentStatus();
        if ("END".equals(currentStatus)) {
            log.warn("[death-service] 猪只已终结 pigId={}", pigInfo.getId());
            throw new ServiceException("猪只已终结，无法提交死亡信息");
        }

        // 4. 检查是否已存在死亡记录
        PigDeath existingDeath = baseMapper.selectByPigId(pigInfo.getId());
        if (existingDeath != null) {
            log.warn("[death-service] 猪只已存在死亡记录 pigId={}", pigInfo.getId());
            throw new ServiceException("该猪只已存在死亡记录，无法重复提交");
        }

        // 5. 构建死亡记录实体
        PigDeath death = new PigDeath();
        death.setPigId(pigInfo.getId());
        death.setEarNo(bo.getEarNo());
        death.setDeathDate(bo.getDeathDate());
        death.setDeathPigType(bo.getDeathPigType());
        death.setDeathKind(bo.getDeathKind());
        death.setDeathReason(bo.getDeathReason());
        death.setDeathDest(bo.getDeathDest());
        death.setDeathWeight(bo.getDeathWeight());
        death.setOssIds(convertOssIdsToString(bo.getOssIds()));
        death.setOperatorId(bo.getOperatorId());
        death.setBarnName(bo.getBarnName());
        death.setPenName(bo.getPenName());
        death.setRemark(bo.getRemark());

        // 6. 保存死亡记录
        int result = baseMapper.insert(death);
        log.info("[death-service] 死亡记录保存成功 deathId={} pigId={}", death.getId(), pigInfo.getId());

        // 7. 更新猪只状态为死亡
        pigInfoService.updateStatusToDeath(pigInfo.getId());
        log.info("[death-service] 猪只状态更新为死亡 pigId={}", pigInfo.getId());

        return result;
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        log.info("[death-service] 删除死亡记录 ids={}", ids);
        return softDelete(ids);
    }

    @Override
    public PigDeath getByPigId(Long pigId) {
        return baseMapper.selectByPigId(pigId);
    }

    /**
     * 软删：循环 wrapper-only update，逐个 id 显式 set
     * {@code del_flag='1'} / {@code update_by} / {@code update_time}。
     *
     * <p>注：t_farm_pig_death 表无 del_unique 字段，无需设置。</p>
     *
     * @param ids 待软删的主键集合（空 / null 直接返 0）
     * @return 实际受影响行数（DB 不存在或已软删的 id 不计入）
     */
    private int softDelete(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return 0;
        }
        Long updateBy = currentUserIdSafe();
        Date now = new Date();
        int count = 0;
        for (Long id : ids) {
            UpdateWrapper<PigDeath> wrapper = Wrappers.<PigDeath>update()
                .eq("id", id)
                .eq("del_flag", "0")
                .set("del_flag", "1")
                .set("update_by", updateBy)
                .set("update_time", now);
            count += baseMapper.update(null, wrapper);
        }
        return count;
    }

    /**
     * 安全获取当前用户 ID。
     */
    private Long currentUserIdSafe() {
        try {
            Long id = LoginHelper.getUserId();
            return id != null ? id : 0L;
        } catch (Exception e) {
            return 0L;
        }
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

    /**
     * 将逗号分隔的OSS IDs字符串转换为列表。
     */
    private void convertOssIdsToList(PigDeathVo vo) {
        if (vo != null && StringUtils.isNotBlank(vo.getOssIdsStr())) {
            String[] ossIdArray = vo.getOssIdsStr().split(",");
            vo.setOssIds(Arrays.asList(ossIdArray));
        } else {
            vo.setOssIds(Collections.emptyList());
        }
    }

}
