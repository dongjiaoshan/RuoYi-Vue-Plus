package org.dromara.djs.warehouse.tare.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.warehouse.tare.domain.TareSpec;
import org.dromara.djs.warehouse.tare.domain.bo.TareSpecBo;
import org.dromara.djs.warehouse.tare.domain.vo.TareOptionVo;
import org.dromara.djs.warehouse.tare.domain.vo.TareSpecVo;
import org.dromara.djs.warehouse.tare.mapper.TareSpecMapper;
import org.dromara.djs.warehouse.tare.service.ITareSpecService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 去皮规格 Service 实现（V7-R2 维护 / V7-R4 称重页取用）。
 *
 * @author djs
 * @since V7-R2
 */
@Service
public class TareSpecServiceImpl extends DjsBaseServiceImpl<TareSpecMapper, TareSpec> implements ITareSpecService {

    static final String DICT_TARE_POSITION = "djs_tare_position";
    private static final int YES = 1;

    private final DictService dictService;

    public TareSpecServiceImpl(TareSpecMapper baseMapper, DictService dictService) {
        super(baseMapper);
        this.dictService = dictService;
    }

    @Override
    public TableDataInfo<TareSpecVo> queryPageList(String specName, PageQuery pageQuery) {
        LambdaQueryWrapper<TareSpec> wrapper = new LambdaQueryWrapper<TareSpec>()
            .like(StringUtils.isNotBlank(specName), TareSpec::getSpecName, StringUtils.trim(specName))
            .orderByDesc(TareSpec::getUpdateTime).orderByDesc(TareSpec::getId);
        Page<TareSpecVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        return TableDataInfo.build(page);
    }

    @Override
    public TareSpecVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public int insertByBo(TareSpecBo bo) {
        TareSpec entity = toEntity(bo);
        requireUniqueName(entity.getSpecName(), null);
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(TareSpecBo bo) {
        if (bo.getId() == null || baseMapper.selectById(bo.getId()) == null) {
            throw new ServiceException("去皮规格不存在或已删除，请刷新列表");
        }
        TareSpec entity = toEntity(bo);
        entity.setId(bo.getId());
        requireUniqueName(entity.getSpecName(), bo.getId());
        return baseMapper.updateById(entity);
    }

    @Override
    public int deleteByIds(Collection<Long> ids) {
        return softDelete(ids);
    }

    @Override
    public List<TareOptionVo> listOptions(String position) {
        if (StringUtils.isBlank(position) || !dictService.getAllDictByDictType(DICT_TARE_POSITION).containsKey(position)) {
            throw new ServiceException("无效的去皮位置");
        }
        List<TareSpec> specs = baseMapper.selectList(new LambdaQueryWrapper<TareSpec>()
            .apply("FIND_IN_SET({0}, positions) > 0", position));
        Comparator<TareSpec> newestFirst = Comparator
            .comparing((TareSpec s) -> lastChanged(s), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TareSpec::getId, Comparator.reverseOrder());
        TareSpec chosen = specs.stream().filter(s -> Objects.equals(s.getIsDefault(), YES))
            .min(newestFirst).orElse(null);
        List<TareOptionVo> options = new ArrayList<>(specs.size());
        if (chosen != null) {
            options.add(new TareOptionVo(chosen.getId(), chosen.getSpecName(), chosen.getTareWeight(), true));
        }
        specs.stream().filter(s -> s != chosen).sorted(newestFirst)
            .forEach(s -> options.add(new TareOptionVo(s.getId(), s.getSpecName(), s.getTareWeight(), false)));
        return options;
    }

    /** 新建后从未改过的行 update_time 也由 MetaObjectHandler 填好；兜底用 create_time。 */
    private static Date lastChanged(TareSpec spec) {
        return spec.getUpdateTime() != null ? spec.getUpdateTime() : spec.getCreateTime();
    }

    private TareSpec toEntity(TareSpecBo bo) {
        Map<String, String> allowed = dictService.getAllDictByDictType(DICT_TARE_POSITION);
        Set<String> picked = new LinkedHashSet<>();
        for (String p : bo.getPositions()) {
            String v = StringUtils.trim(p);
            if (!allowed.containsKey(v)) {
                throw new ServiceException("无效的可选位置：" + p);
            }
            picked.add(v);
        }
        // 按字典顺序落库，列表展示不随勾选先后变化
        List<String> positions = allowed.keySet().stream().filter(picked::contains).toList();
        if (bo.getIsDefault() == null || (bo.getIsDefault() != YES && bo.getIsDefault() != 0)) {
            throw new ServiceException("请选择是否默认");
        }
        TareSpec entity = new TareSpec();
        entity.setSpecName(StringUtils.trim(bo.getSpecName()));
        entity.setTareWeight(bo.getTareWeight());
        entity.setIsDefault(bo.getIsDefault());
        entity.setPositions(String.join(",", positions));
        return entity;
    }

    private void requireUniqueName(String specName, Long selfId) {
        boolean taken = baseMapper.exists(new LambdaQueryWrapper<TareSpec>()
            .eq(TareSpec::getSpecName, specName)
            .ne(selfId != null, TareSpec::getId, selfId));
        if (taken) {
            throw new ServiceException("规格名称「" + specName + "」已存在");
        }
    }

}
