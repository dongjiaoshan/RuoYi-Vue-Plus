package org.dromara.djs.breed.farm.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.farm.domain.FarmInfo;
import org.dromara.djs.breed.farm.domain.bo.FarmContactBo;
import org.dromara.djs.breed.farm.domain.vo.FarmInfoVo;
import org.dromara.djs.breed.farm.mapper.FarmInfoMapper;
import org.dromara.djs.breed.farm.service.IFarmInfoService;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 农场主数据 Service 实现（BRD-MD-002）。
 *
 * <p>V1 单农场 hardcode {@code id=1001}（参 ADR-0001）。本服务<b>不</b>继承
 * {@link org.dromara.djs.common.base.DjsBaseServiceImpl}：</p>
 * <ul>
 *   <li>无 list / add / remove 端点，不需要软删；</li>
 *   <li>仅靠 {@code Mapper#updateById} 走"只更新非 null 字段"语义
 *       （MP {@code FieldStrategy.NOT_NULL}），把 farm_code / farm_name / farm_status
 *       自动排除在 UPDATE 之外。</li>
 * </ul>
 *
 * @author djs
 * @since BRD-MD-002
 */
@Slf4j
@Service
public class FarmInfoServiceImpl implements IFarmInfoService {

    /**
     * V1 单农场固定 ID（参 ADR-0001 多农场延期决策；与
     * {@code sys_farm} seed 行 / 业务表 {@code tenant_id='1001'} 严格对齐）。
     */
    public static final Long V1_DEFAULT_FARM_ID = 1001L;

    private final FarmInfoMapper farmInfoMapper;

    public FarmInfoServiceImpl(FarmInfoMapper farmInfoMapper) {
        this.farmInfoMapper = farmInfoMapper;
    }

    @Override
    public FarmInfoVo queryCurrent() {
        return farmInfoMapper.selectVoById(V1_DEFAULT_FARM_ID);
    }

    @Override
    public int updateContact(FarmContactBo bo) {
        if (bo == null || bo.getId() == null) {
            throw new ServiceException("农场 ID 不能为空");
        }
        if (!Objects.equals(bo.getId(), V1_DEFAULT_FARM_ID)) {
            // V1 ADR-0001：admin 不允许编辑非默认农场（V2 多农场启用后再放开）
            throw new ServiceException("V1 仅允许编辑默认农场（id=1001）");
        }
        FarmInfo exists = farmInfoMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("农场数据未初始化（sys_farm id=1001 不存在），请联系运维补 seed");
        }

        // 只更新 contact 三字段：手动构造 entity，farm_code / farm_name / farm_status
        // 保持 null → MP @FieldStrategy(NOT_NULL) 自动从 UPDATE SET 子句剥离。
        FarmInfo update = new FarmInfo();
        update.setId(bo.getId());
        update.setContactName(bo.getContactName());
        update.setContactPhone(bo.getContactPhone());
        update.setAddress(bo.getAddress());
        return farmInfoMapper.updateById(update);
    }

}
