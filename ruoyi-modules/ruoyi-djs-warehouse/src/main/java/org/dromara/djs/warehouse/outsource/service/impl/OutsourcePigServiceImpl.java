package org.dromara.djs.warehouse.outsource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.base.DjsBaseServiceImpl;
import org.dromara.djs.common.supplier.domain.Supplier;
import org.dromara.djs.common.supplier.mapper.SupplierMapper;
import org.dromara.djs.warehouse.outsource.domain.OutsourcePig;
import org.dromara.djs.warehouse.outsource.domain.bo.OutsourcePigBo;
import org.dromara.djs.warehouse.outsource.domain.query.OutsourcePigQuery;
import org.dromara.djs.warehouse.outsource.domain.vo.OutsourcePigVo;
import org.dromara.djs.warehouse.outsource.mapper.OutsourcePigMapper;
import org.dromara.djs.warehouse.outsource.service.IOutsourcePigService;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 外购猪只 Service 实现（DJS-FIX-WMS-RALN）。
 *
 * <p>列表 VO 的 supplierName 由本类按 supplierId 批量回填（一次 selectBatchIds，避免 N+1）；
 * buyerName 走 VO 上的 {@code @Translation} 注解翻译。软删走基类 {@link DjsBaseServiceImpl#softDelete}。</p>
 *
 * @author djs
 * @since DJS-FIX-WMS-RALN
 */
@Slf4j
@Service
public class OutsourcePigServiceImpl extends DjsBaseServiceImpl<OutsourcePigMapper, OutsourcePig> implements IOutsourcePigService {

    private final SupplierMapper supplierMapper;

    public OutsourcePigServiceImpl(OutsourcePigMapper baseMapper, SupplierMapper supplierMapper) {
        super(baseMapper);
        this.supplierMapper = supplierMapper;
    }

    @Override
    public TableDataInfo<OutsourcePigVo> queryPageList(OutsourcePigQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<OutsourcePig> wrapper = buildQueryWrapper(query);
        Page<OutsourcePigVo> page = baseMapper.selectVoPage(pageQuery.build(), wrapper);
        fillSupplierName(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<OutsourcePigVo> queryList(OutsourcePigQuery query) {
        List<OutsourcePigVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillSupplierName(list);
        return list;
    }

    @Override
    public OutsourcePigVo queryById(Long id) {
        OutsourcePigVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillSupplierName(List.of(vo));
        }
        return vo;
    }

    @Override
    public int insertByBo(OutsourcePigBo bo) {
        OutsourcePig entity = toEntity(bo);
        if (entity == null) {
            throw new ServiceException("外购猪只入参转换失败");
        }
        // tenant_id / create_by 等不显式赋，走 MetaObjectHandler.insertFill
        return baseMapper.insert(entity);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return softDelete(ids);
    }

    /**
     * 按 supplierId 批量回填 supplierName（一次 selectBatchIds）。
     */
    private void fillSupplierName(List<OutsourcePigVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> supplierIds = rows.stream()
            .map(OutsourcePigVo::getSupplierId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (supplierIds.isEmpty()) {
            return;
        }
        List<Supplier> suppliers = supplierMapper.selectBatchIds(supplierIds);
        Map<Long, String> nameMap = new HashMap<>(suppliers.size());
        for (Supplier s : suppliers) {
            nameMap.put(s.getId(), s.getSupplierName());
        }
        for (OutsourcePigVo vo : rows) {
            if (vo.getSupplierId() != null) {
                vo.setSupplierName(nameMap.get(vo.getSupplierId()));
            }
        }
    }

    /**
     * BO → Entity 转换钩子；走 MapStruct-Plus。
     *
     * <p>protected 方便 Mockito 单测覆盖。</p>
     */
    protected OutsourcePig toEntity(OutsourcePigBo bo) {
        return MapstructUtils.convert(bo, OutsourcePig.class);
    }

    private LambdaQueryWrapper<OutsourcePig> buildQueryWrapper(OutsourcePigQuery query) {
        LambdaQueryWrapper<OutsourcePig> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(OutsourcePig::getId);
        }
        wrapper.eq(query.getSupplierId() != null, OutsourcePig::getSupplierId, query.getSupplierId())
            .eq(StringUtils.isNotBlank(query.getBuyer()), OutsourcePig::getBuyer, query.getBuyer());
        applyDayEq(wrapper, OutsourcePig::getPurchaseDate, query.getPurchaseDate());
        applyDayEq(wrapper, OutsourcePig::getArriveTime, query.getArriveTime());
        wrapper.orderByDesc(OutsourcePig::getId);
        return wrapper;
    }

    /**
     * 按天匹配：[当天 00:00:00, 次日 00:00:00) 区间（兼容 date / datetime 列）。
     */
    private void applyDayEq(LambdaQueryWrapper<OutsourcePig> wrapper,
                            SFunction<OutsourcePig, ?> column,
                            Date day) {
        if (day == null) {
            return;
        }
        Calendar c = Calendar.getInstance();
        c.setTime(day);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        Date start = c.getTime();
        c.add(Calendar.DAY_OF_MONTH, 1);
        Date end = c.getTime();
        wrapper.ge(column, start).lt(column, end);
    }

}
