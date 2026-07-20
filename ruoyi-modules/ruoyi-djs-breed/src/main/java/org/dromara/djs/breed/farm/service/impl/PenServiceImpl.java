package org.dromara.djs.breed.farm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.domain.bo.PenBo;
import org.dromara.djs.breed.farm.domain.query.PenQuery;
import org.dromara.djs.breed.farm.domain.vo.PenDetailVo;
import org.dromara.djs.breed.farm.domain.vo.PenVo;
import org.dromara.djs.breed.farm.mapper.FarmStatMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.dromara.djs.breed.farm.mapper.PigReferenceCheckMapper;
import org.dromara.djs.breed.farm.service.IPenService;
import org.dromara.djs.breed.farm.service.PenCapacityChecker;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 栏位 Service 实现（BRD-MD-002）。
 *
 * <p>软删走基类 {@link DjsBaseServiceImpl#softDelete}（D03 教训）。</p>
 *
 * <p>删除约束：删栏位前查 {@code t_farm_pig_info WHERE pen_id=? AND del_flag='0'}
 * —— 有未删猪只则拒绝。栏位级单独校验（栋舍级在 BarnServiceImpl 已校验，但栏位删除
 * 不一定连带删栋舍，必须独立校验一次）。</p>
 *
 * @author djs
 * @since BRD-MD-002
 */
@Slf4j
@Service
public class PenServiceImpl extends DjsBaseServiceImpl<PenMapper, Pen> implements IPenService {

    private final PigReferenceCheckMapper pigReferenceCheckMapper;
    private final FarmStatMapper farmStatMapper;
    private final PenCapacityChecker penCapacityChecker;

    public PenServiceImpl(PenMapper baseMapper,
                          PigReferenceCheckMapper pigReferenceCheckMapper,
                          FarmStatMapper farmStatMapper,
                          PenCapacityChecker penCapacityChecker) {
        super(baseMapper);
        this.pigReferenceCheckMapper = pigReferenceCheckMapper;
        this.farmStatMapper = farmStatMapper;
        this.penCapacityChecker = penCapacityChecker;
    }

    @Override
    public List<PenDetailVo> queryDetailByBarn(Long barnId, String penType) {
        if (barnId == null || StringUtils.isBlank(penType)) {
            return List.of();
        }
        return farmStatMapper.selectPenDetail(barnId, penType);
    }

    @Override
    public TableDataInfo<PenVo> queryPageList(PenQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<Pen> wrapper = buildQueryWrapper(query);
        Page<PenVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        enrichCapacity(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<PenVo> queryList(PenQuery query) {
        List<PenVo> rows = baseMapper.selectVoList(buildQueryWrapper(query));
        enrichCapacity(rows);
        return rows;
    }

    @Override
    public PenVo queryById(Long id) {
        PenVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            enrichCapacity(List.of(vo));
        }
        return vo;
    }

    /**
     * 列表/详情 VO 填容量口径字段（row59）：capacity 按 pen_type 推导（覆盖 DB capacity 列），
     * usedCount 实时统计该栏在栏头数（非终止态 + 排除仔猪）。
     */
    private void enrichCapacity(List<PenVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (PenVo vo : rows) {
            vo.setCapacity(penCapacityChecker.capacityOf(vo.getPenType()));
            vo.setUsedCount(penCapacityChecker.usedCount(vo.getId()));
        }
    }

    @Override
    public int insertByBo(PenBo bo) {
        Pen entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("栏位入参转换失败");
        }
        if (entity.getPenStatus() == null) {
            entity.setPenStatus(1);
        }
        if (entity.getCurrentCount() == null) {
            entity.setCurrentCount(0);
        }
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(PenBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("栏位 ID 不能为空");
        }
        Pen exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("栏位不存在或已删除：" + bo.getId());
        }
        Pen entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("栏位入参转换失败");
        }
        // pen_code / barn_id 不允许通过编辑端点修改
        entity.setPenCode(exists.getPenCode());
        entity.setBarnId(exists.getBarnId());
        // current_count 由业务事件维护
        entity.setCurrentCount(exists.getCurrentCount());
        return baseMapper.updateById(entity);
    }

    protected Pen toEntity(PenBo bo) {
        return MapstructUtils.convert(bo, Pen.class);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        for (Long id : ids) {
            long pigCount = pigReferenceCheckMapper.countActiveByPenId(id);
            if (pigCount > 0) {
                Pen pen = baseMapper.selectById(id);
                String name = pen == null ? String.valueOf(id) : pen.getPenName();
                throw new ServiceException(
                    "栏位「" + name + "」下仍有 " + pigCount + " 头存栏猪只，请先转栏或处理后再删除");
            }
        }
        return softDelete(ids);
    }

    /**
     * 构造查询条件。<b>不写 {@code .eq("del_flag", "0")}</b> —— MP @TableLogic 自动加。
     */
    private LambdaQueryWrapper<Pen> buildQueryWrapper(PenQuery query) {
        LambdaQueryWrapper<Pen> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(Pen::getId);
        }
        wrapper.eq(Objects.nonNull(query.getBarnId()), Pen::getBarnId, query.getBarnId())
            .eq(StringUtils.isNotBlank(query.getPenCode()), Pen::getPenCode, query.getPenCode())
            .like(StringUtils.isNotBlank(query.getPenName()), Pen::getPenName, query.getPenName())
            .eq(StringUtils.isNotBlank(query.getPenType()), Pen::getPenType, query.getPenType())
            .eq(Objects.nonNull(query.getPenStatus()), Pen::getPenStatus, query.getPenStatus())
            .orderByDesc(Pen::getId);
        return wrapper;
    }

}
