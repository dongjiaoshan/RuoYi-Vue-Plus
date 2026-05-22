package org.dromara.djs.breed.farm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.farm.domain.Barn;
import org.dromara.djs.breed.farm.domain.bo.BarnBo;
import org.dromara.djs.breed.farm.domain.query.BarnQuery;
import org.dromara.djs.breed.farm.domain.vo.BarnVo;
import org.dromara.djs.breed.farm.mapper.BarnMapper;
import org.dromara.djs.breed.farm.mapper.PigReferenceCheckMapper;
import org.dromara.djs.breed.farm.service.IBarnService;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.springframework.stereotype.Service;

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

    private final PigReferenceCheckMapper pigReferenceCheckMapper;

    public BarnServiceImpl(BarnMapper baseMapper, PigReferenceCheckMapper pigReferenceCheckMapper) {
        super(baseMapper);
        this.pigReferenceCheckMapper = pigReferenceCheckMapper;
    }

    @Override
    public TableDataInfo<BarnVo> queryPageList(BarnQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<Barn> wrapper = buildQueryWrapper(query);
        Page<BarnVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        return TableDataInfo.build(page);
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
        return softDelete(ids, Barn::new);
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
