package org.dromara.djs.breed.farm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.Pen;
import org.dromara.djs.breed.farm.domain.bo.BarnBo;
import org.dromara.djs.breed.farm.domain.bo.BarnCreateBo;
import org.dromara.djs.breed.farm.domain.query.BarnQuery;
import org.dromara.djs.breed.farm.domain.vo.BarnVo;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.FarmStatMapper;
import org.dromara.djs.breed.farm.mapper.PenMapper;
import org.dromara.djs.breed.farm.mapper.PigReferenceCheckMapper;
import org.dromara.djs.breed.farm.service.IBarnService;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 栋舍 Service 实现（BRD-MD-002）。
 *
 * <p>软删走基类 {@link DjsBaseServiceImpl#softDelete}（D03 教训：MP @TableLogic 会剥离
 * entity.delFlag，必须用 wrapper.setSql 强写）。</p>
 *
 * <p>删除约束（doc/06 BRD-MD-002 实现思路 #4）：删栋舍前查
 * {@code t_farm_pig_info WHERE barn_id=? AND del_flag='0'} —— 有未删猪只则拒绝。</p>
 *
 * @author djs
 * @since BRD-MD-002
 */
@Slf4j
@Service
public class BarnServiceImpl extends DjsBaseServiceImpl<BarnMapper, Barn> implements IBarnService {

    /** 栏位类型字典值（djs_pen_type）：大栏 / 限位栏 / 产床 / 散栏 / 保育栏。 */
    private static final String PEN_TYPE_BIG = "big";
    private static final String PEN_TYPE_STALL = "stall";
    private static final String PEN_TYPE_FARROW = "farrow";
    private static final String PEN_TYPE_SCATTER = "scatter";
    private static final String PEN_TYPE_NURSERY_PEN = "nursery_pen";

    private final PigReferenceCheckMapper pigReferenceCheckMapper;
    private final PenMapper penMapper;
    private final FarmStatMapper farmStatMapper;

    public BarnServiceImpl(BarnMapper baseMapper,
                           PigReferenceCheckMapper pigReferenceCheckMapper,
                           PenMapper penMapper,
                           FarmStatMapper farmStatMapper) {
        super(baseMapper);
        this.pigReferenceCheckMapper = pigReferenceCheckMapper;
        this.penMapper = penMapper;
        this.farmStatMapper = farmStatMapper;
    }

    @Override
    public TableDataInfo<BarnVo> queryPageList(BarnQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<Barn> wrapper = buildQueryWrapper(query);
        Page<BarnVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        enrichCounts(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 富集列表的三类栏位数量 + 当前存栏（实时统计，非表字段）。
     */
    private void enrichCounts(List<BarnVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (BarnVo vo : records) {
            Long id = vo.getId();
            vo.setBigPenCount(farmStatMapper.countPenByType(id, PEN_TYPE_BIG));
            vo.setLimitPenCount(farmStatMapper.countPenByType(id, PEN_TYPE_STALL));
            vo.setBedCount(farmStatMapper.countPenByType(id, PEN_TYPE_FARROW));
            vo.setScatterPenCount(farmStatMapper.countPenByType(id, PEN_TYPE_SCATTER));
            vo.setNurseryPenCount(farmStatMapper.countPenByType(id, PEN_TYPE_NURSERY_PEN));
            vo.setLiveCount(farmStatMapper.countLivePigByBarn(id));
        }
    }

    @Override
    public List<BarnVo> queryList(BarnQuery query) {
        return baseMapper.selectVoList(buildQueryWrapper(query));
    }

    @Override
    public BarnVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public int insertByBo(BarnBo bo) {
        Barn entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("栋舍入参转换失败");
        }
        if (entity.getBarnStatus() == null) {
            entity.setBarnStatus(1);
        }
        if (entity.getCurrentCount() == null) {
            entity.setCurrentCount(0);
        }
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createWithPens(BarnCreateBo bo) {
        // 1. 建栋舍（编码自动生成 "B"+雪花 id，不暴露给用户；与原型新建弹窗无编码字段一致）
        Long barnId = IdWorker.getId();
        Barn barn = new Barn();
        barn.setId(barnId);
        barn.setBarnCode("B" + barnId);
        barn.setBarnName(bo.getBarnName());
        barn.setBarnType(bo.getBarnType());
        barn.setBarnStatus(1);
        barn.setCurrentCount(0);
        barn.setRemark(bo.getRemark());
        baseMapper.insert(barn);

        // 2. 按五类数量批量生成栏位（编号连号「1栏…N栏」，pen_code 类型前缀 + 序号保证栏内唯一）
        generatePens(barnId, PEN_TYPE_BIG, "DL", bo.getBigPenCount());
        generatePens(barnId, PEN_TYPE_STALL, "XW", bo.getLimitPenCount());
        generatePens(barnId, PEN_TYPE_FARROW, "CC", bo.getBedCount());
        generatePens(barnId, PEN_TYPE_SCATTER, "SL", bo.getScatterPenCount());
        generatePens(barnId, PEN_TYPE_NURSERY_PEN, "BY", bo.getNurseryPenCount());
        return barnId;
    }

    /**
     * 生成某栋舍下 count 个指定类型栏位。penName=「{seq}栏」，penCode=「{prefix}-{seq}」。
     * 多头栏（大栏 / 散栏 / 保育栏）容量不限（null），限位栏 / 产床单头（capacity=1）。
     */
    private void generatePens(Long barnId, String penType, String codePrefix, Integer count) {
        if (count == null || count <= 0) {
            return;
        }
        boolean multiHead = PEN_TYPE_BIG.equals(penType)
            || PEN_TYPE_SCATTER.equals(penType)
            || PEN_TYPE_NURSERY_PEN.equals(penType);
        Integer capacity = multiHead ? null : 1;
        for (int i = 1; i <= count; i++) {
            Pen pen = new Pen();
            pen.setBarnId(barnId);
            pen.setPenCode(codePrefix + "-" + i);
            pen.setPenName(i + "栏");
            pen.setPenType(penType);
            pen.setCapacity(capacity);
            pen.setCurrentCount(0);
            pen.setPenStatus(1);
            penMapper.insert(pen);
        }
    }

    @Override
    public int updateByBo(BarnBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("栋舍 ID 不能为空");
        }
        Barn exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("栋舍不存在或已删除：" + bo.getId());
        }
        Barn entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("栋舍入参转换失败");
        }
        // barn_code 不允许通过编辑端点修改（与门店 / 育种范式一致）
        entity.setBarnCode(exists.getBarnCode());
        // current_count 由业务事件维护，admin 编辑不覆盖
        entity.setCurrentCount(exists.getCurrentCount());
        return baseMapper.updateById(entity);
    }

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus。protected 便于单测覆盖（参 StoreServiceImpl）。
     */
    protected Barn toEntity(BarnBo bo) {
        return MapstructUtils.convert(bo, Barn.class);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // 前置校验：每个 barn_id 下不能有未删的猪只（doc/06 实现思路 #4）
        for (Long id : ids) {
            long pigCount = pigReferenceCheckMapper.countActiveByBarnId(id);
            if (pigCount > 0) {
                Barn barn = baseMapper.selectById(id);
                String name = barn == null ? String.valueOf(id) : barn.getBarnName();
                throw new ServiceException(
                    "栋舍「" + name + "」下仍有 " + pigCount + " 头存栏猪只，请先转栏或处理后再删除");
            }
        }
        return softDelete(ids);
    }

    /**
     * 构造查询条件：barnCode eq / barnName like / barnType eq / barnStatus eq，按 id 倒序。
     *
     * <p>软删过滤由 MP {@code @TableLogic} 自动注入 WHERE del_flag='0'，<b>不要</b>
     * 手写 {@code .eq("del_flag", "0")} —— 否则会出现 SQL 里 del_flag = '0' AND del_flag = '0' 重复。</p>
     */
    private LambdaQueryWrapper<Barn> buildQueryWrapper(BarnQuery query) {
        LambdaQueryWrapper<Barn> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(Barn::getId);
        }
        wrapper.eq(StringUtils.isNotBlank(query.getBarnCode()), Barn::getBarnCode, query.getBarnCode())
            .like(StringUtils.isNotBlank(query.getBarnName()), Barn::getBarnName, query.getBarnName())
            .eq(StringUtils.isNotBlank(query.getBarnType()), Barn::getBarnType, query.getBarnType())
            .eq(Objects.nonNull(query.getBarnStatus()), Barn::getBarnStatus, query.getBarnStatus())
            .orderByDesc(Barn::getId);
        return wrapper;
    }

}
