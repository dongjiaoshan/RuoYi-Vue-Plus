package org.dromara.djs.breed.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.breed.production.domain.ProductionCycleConfig;
import org.dromara.djs.breed.production.domain.bo.ProductionCycleConfigBo;
import org.dromara.djs.breed.production.domain.query.ProductionCycleConfigQuery;
import org.dromara.djs.breed.production.domain.vo.ProductionCycleConfigVo;
import org.dromara.djs.breed.production.mapper.ProductionCycleConfigMapper;
import org.dromara.djs.breed.production.service.IProductionCycleConfigService;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产周期配置 Service 实现（BRD-MD-003 Tab1）。
 *
 * <p>下游 BRD-CORE-001 状态机 / BRD-EVENT-* 列表过滤通过 {@link #getValue(String)} 读取生效值
 * （优先 {@code customValue}，无则 {@code defaultValue}）。配置只决定"建议时间"，
 * <b>不会自动 trigger</b>状态转换（v1.2 客户决策无定时任务）。</p>
 *
 * <p>软删走 {@link DjsBaseServiceImpl#softDelete}，参考 BRD-MD-001 范式（D03 教训）。</p>
 *
 * @author djs
 * @since BRD-MD-003
 */
@Slf4j
@Service
public class ProductionCycleConfigServiceImpl
    extends DjsBaseServiceImpl<ProductionCycleConfigMapper, ProductionCycleConfig>
    implements IProductionCycleConfigService {

    public ProductionCycleConfigServiceImpl(ProductionCycleConfigMapper baseMapper) {
        super(baseMapper);
    }

    @Override
    public TableDataInfo<ProductionCycleConfigVo> queryPageList(ProductionCycleConfigQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<ProductionCycleConfig> wrapper = buildQueryWrapper(query);
        Page<ProductionCycleConfigVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        return TableDataInfo.build(page);
    }

    @Override
    public List<ProductionCycleConfigVo> queryList(ProductionCycleConfigQuery query) {
        return baseMapper.selectVoList(buildQueryWrapper(query));
    }

    @Override
    public ProductionCycleConfigVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public int insertByBo(ProductionCycleConfigBo bo) {
        ProductionCycleConfig entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("生产周期配置入参转换失败");
        }
        return baseMapper.insert(entity);
    }

    @Override
    public int updateByBo(ProductionCycleConfigBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("生产周期配置 ID 不能为空");
        }
        ProductionCycleConfig exists = baseMapper.selectById(bo.getId());
        if (exists == null) {
            throw new ServiceException("生产周期配置不存在或已删除：" + bo.getId());
        }
        ProductionCycleConfig entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("生产周期配置入参转换失败");
        }
        return baseMapper.updateById(entity);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        // v1 暂无外部下游引用记录（getValue 直接按 key 查，不持 id），可直接软删。
        // 若后续状态机改为按 id 缓存，需在此校验是否被引用。
        return softDelete(ids);
    }

    @Override
    public Integer getValue(String configKey) {
        if (StringUtils.isBlank(configKey)) {
            return null;
        }
        ProductionCycleConfig row = baseMapper.selectOne(new LambdaQueryWrapper<ProductionCycleConfig>()
            .eq(ProductionCycleConfig::getConfigKey, configKey)
            .last("LIMIT 1"));
        if (row == null) {
            return null;
        }
        return effectiveValue(row);
    }

    /**
     * 生效值 = custom_value（客户自定义，含显式 0）优先；custom_value 为 null（未定制）时回退 default_value。
     *
     * <p>各「天数」周期配置（各类到配种间隔 / 妊娠 / 哺乳）允许客户显式配 0（如后备可立即配种），
     * 0 是有效自定义值、按 0 生效；只有 null 才表示「未定制、用行业默认」。</p>
     */
    private Integer effectiveValue(ProductionCycleConfig row) {
        Integer custom = row.getCustomValue();
        return custom != null ? custom : row.getDefaultValue();
    }

    @Override
    public Map<String, Integer> getValuesByKeys(Collection<String> configKeys) {
        if (configKeys == null || configKeys.isEmpty()) {
            return new HashMap<>();
        }
        List<ProductionCycleConfig> rows = baseMapper.selectList(new LambdaQueryWrapper<ProductionCycleConfig>()
            .in(ProductionCycleConfig::getConfigKey, configKeys));
        Map<String, Integer> result = new HashMap<>(rows.size());
        for (ProductionCycleConfig row : rows) {
            result.put(row.getConfigKey(), effectiveValue(row));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchSaveValues(Map<String, Integer> values,
                               Map<String, Integer> defaultValues,
                               Map<String, String> descriptions) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        Map<String, Integer> defaults = defaultValues != null ? defaultValues : new HashMap<>();
        Map<String, String> descs = descriptions != null ? descriptions : new HashMap<>();
        int count = 0;
        for (Map.Entry<String, Integer> e : values.entrySet()) {
            String key = e.getKey();
            if (StringUtils.isBlank(key)) {
                continue;
            }
            Integer customValue = e.getValue();
            ProductionCycleConfig exists = baseMapper.selectOne(new LambdaQueryWrapper<ProductionCycleConfig>()
                .eq(ProductionCycleConfig::getConfigKey, key)
                .last("LIMIT 1"));
            if (exists != null) {
                exists.setCustomValue(customValue);
                count += baseMapper.updateById(exists);
            } else {
                ProductionCycleConfig row = new ProductionCycleConfig();
                row.setConfigKey(key);
                // 新落库行：default_value 取入参兜底（缺则用 customValue / 0），custom_value 存客户值
                row.setDefaultValue(defaults.getOrDefault(key, customValue != null ? customValue : 0));
                row.setCustomValue(customValue);
                row.setUnit("天");
                row.setDescription(descs.get(key));
                row.setDelFlag("0");
                row.setDelUnique(0L);
                count += baseMapper.insert(row);
            }
        }
        return count;
    }

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus。
     */
    protected ProductionCycleConfig toEntity(ProductionCycleConfigBo bo) {
        return MapstructUtils.convert(bo, ProductionCycleConfig.class);
    }

    /**
     * 构造查询条件：configKey eq + description like。
     */
    private LambdaQueryWrapper<ProductionCycleConfig> buildQueryWrapper(ProductionCycleConfigQuery query) {
        LambdaQueryWrapper<ProductionCycleConfig> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByAsc(ProductionCycleConfig::getId);
        }
        wrapper.eq(StringUtils.isNotBlank(query.getConfigKey()), ProductionCycleConfig::getConfigKey, query.getConfigKey())
            .like(StringUtils.isNotBlank(query.getDescription()), ProductionCycleConfig::getDescription, query.getDescription())
            .orderByAsc(ProductionCycleConfig::getId);
        return wrapper;
    }

}
