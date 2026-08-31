package org.dromara.djs.plant.demand.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.util.I18nMessages;
import org.dromara.djs.plant.demand.domain.CropDemand;
import org.dromara.djs.plant.demand.domain.bo.CropDemandBo;
import org.dromara.djs.plant.demand.domain.bo.CropDemandReplyBo;
import org.dromara.djs.plant.demand.domain.query.CropDemandQuery;
import org.dromara.djs.plant.demand.domain.vo.CropDemandVo;
import org.dromara.djs.plant.demand.mapper.CropDemandMapper;
import org.dromara.djs.plant.demand.service.ICropDemandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;

/**
 * 作物需求 Service 实现（V6-R152 / V6-R153）。
 *
 * <p>状态两态：{@code pending} 待回复 → {@code replied} 已回复。回复端点幂等——已回复再调
 * 只覆盖回复内容 / 时间 / 人，状态保持 {@code replied}。</p>
 *
 * @author djs
 * @since V6-R152
 */
@Service
public class CropDemandServiceImpl extends DjsBaseServiceImpl<CropDemandMapper, CropDemand>
    implements ICropDemandService {

    /** 待回复。 */
    public static final String STATUS_PENDING = "pending";

    /** 已回复。 */
    public static final String STATUS_REPLIED = "replied";

    public CropDemandServiceImpl(CropDemandMapper baseMapper) {
        super(baseMapper);
    }

    @Override
    public TableDataInfo<CropDemandVo> queryPageList(CropDemandQuery query, PageQuery pageQuery) {
        Page<CropDemandVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(query));
        return TableDataInfo.build(page);
    }

    @Override
    public CropDemandVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(CropDemandBo bo) {
        CropDemand entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException(I18nMessages.t("plant.demand.convert.failed"));
        }
        // 需求日期 / 状态 / 回复三字段一律服务端定，前端传了也不认
        entity.setId(null);
        entity.setDemandDate(LocalDate.now());
        entity.setDemandStatus(STATUS_PENDING);
        entity.setReplyContent(null);
        entity.setReplyTime(null);
        entity.setReplyBy(null);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reply(CropDemandReplyBo bo) {
        CropDemand exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException(I18nMessages.t("plant.demand.not_exist", bo.getId()));
        }
        CropDemand update = new CropDemand();
        update.setId(exists.getId());
        update.setReplyContent(bo.getReplyContent());
        update.setReplyTime(new Date());
        update.setReplyBy(currentUserIdSafe());
        update.setDemandStatus(STATUS_REPLIED);
        return baseMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return 0;
        }
        Long currentUserId = currentUserIdSafe();
        // 越权防线在服务端：前端隐藏按钮只是体验，不能当权限
        for (Long id : ids) {
            CropDemand exists = baseMapper.selectById(id);
            if (exists == null) {
                throw new ServiceException(I18nMessages.t("plant.demand.not_exist", id));
            }
            if (!Objects.equals(exists.getCreateBy(), currentUserId)) {
                throw new ServiceException(I18nMessages.t("plant.demand.delete.not_owner"));
            }
        }
        return softDelete(ids);
    }

    protected CropDemand toEntity(CropDemandBo bo) {
        return MapstructUtils.convert(bo, CropDemand.class);
    }

    private LambdaQueryWrapper<CropDemand> buildQueryWrapper(CropDemandQuery query) {
        LambdaQueryWrapper<CropDemand> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(CropDemand::getDemandDate).orderByDesc(CropDemand::getId);
        }
        wrapper.like(StringUtils.isNotBlank(query.getDemandContent()), CropDemand::getDemandContent, query.getDemandContent())
            .eq(StringUtils.isNotBlank(query.getDemandCategory()), CropDemand::getDemandCategory, query.getDemandCategory())
            .eq(StringUtils.isNotBlank(query.getDemandStatus()), CropDemand::getDemandStatus, query.getDemandStatus())
            .ge(query.getBeginDate() != null, CropDemand::getDemandDate, query.getBeginDate())
            .le(query.getEndDate() != null, CropDemand::getDemandDate, query.getEndDate())
            .orderByDesc(CropDemand::getDemandDate)
            .orderByDesc(CropDemand::getId);
        return wrapper;
    }
}
