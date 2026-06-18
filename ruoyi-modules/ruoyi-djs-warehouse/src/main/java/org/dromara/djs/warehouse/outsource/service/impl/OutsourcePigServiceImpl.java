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
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.supplier.domain.Supplier;
import org.dromara.djs.common.supplier.mapper.SupplierMapper;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
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

    /**
     * bar_info.status 初始态：待燎毛（字典 {@code djs_bar_status}）。
     */
    private static final String BAR_STATUS_PENDING_SINGE = "pending_singe";

    /**
     * bar_info.in_method=1 燎毛间（字典 {@code djs_bar_in_method}；与自养出栏路径一致）。
     */
    private static final Integer IN_METHOD_SINGE_ROOM = 1;

    private final SupplierMapper supplierMapper;
    private final BarInfoMapper barInfoMapper;
    private final IBizCodeGenerator bizCodeGenerator;

    public OutsourcePigServiceImpl(OutsourcePigMapper baseMapper,
                                   SupplierMapper supplierMapper,
                                   BarInfoMapper barInfoMapper,
                                   IBizCodeGenerator bizCodeGenerator) {
        super(baseMapper);
        this.supplierMapper = supplierMapper;
        this.barInfoMapper = barInfoMapper;
        this.bizCodeGenerator = bizCodeGenerator;
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
        int rows = baseMapper.insert(entity);
        // 外购猪走燎毛：镜像 PigMarketingEventListener 补写一行外购 bar_info（status=pending_singe），
        // 否则 mp 待燎毛列表（queryPendingBars 唯一数据源是 bar_info）查不到外购猪。
        createOutsourceBar(bo);
        return rows;
    }

    /**
     * 外购猪入库时镜像自养出栏路径补写一行外购白条（{@code t_warehouse_bar_info}）。
     *
     * <p>字段口径与自养 {@link org.dromara.djs.warehouse.cross.listener.PigMarketingEventListener#onPigMarketing}
     * 对齐：{@code status=pending_singe} / {@code inMethod=1}（燎毛间）/ {@code barId} 走
     * {@link BizCodeType#BAR_NO}；外购列 {@code supplierId / buyDate / buyWeight} 回填。
     * 外购无耳号，{@code earNo} 留空（NULL）；{@code pigMarkNo}（猪只标识号）仅填 {@code markId}，
     * 外购白条靠 {@code supplierId} 非空区分通用白条。</p>
     *
     * <p>补 bar 失败不应阻塞外购登记主流程（外购台账已写成功），故 swallow + log（同 listener 失败策略）。</p>
     */
    private void createOutsourceBar(OutsourcePigBo bo) {
        try {
            BarInfo bar = new BarInfo();
            bar.setBarId(bizCodeGenerator.generate(BizCodeType.BAR_NO, Map.of()));
            // 外购无耳号：earNo 留空（NULL），pigMarkNo 仅填 markId，靠 supplierId 区分通用白条
            bar.setMarkId(bo.getPigMarkNo());
            bar.setMarketingWeight(bo.getPigWeight());
            bar.setStatus(BAR_STATUS_PENDING_SINGE);
            bar.setInMethod(IN_METHOD_SINGE_ROOM);
            bar.setSupplierId(bo.getSupplierId());
            bar.setBuyDate(bo.getPurchaseDate());
            bar.setBuyWeight(bo.getPigWeight());
            // tenant_id / 6 个审计字段由 MetaObjectHandler 自动 fill；del_unique=0 由 DDL DEFAULT
            barInfoMapper.insert(bar);
            log.info("[DJS-FIX-WMS-RALN] 外购猪 bar_info 创建成功 barId={} pigMarkNo={} supplierId={}",
                bar.getBarId(), bo.getPigMarkNo(), bo.getSupplierId());
        } catch (Exception e) {
            log.error("[DJS-FIX-WMS-RALN] 外购猪自动创建 bar_info 失败 pigMarkNo={} supplierId={}: {}",
                bo.getPigMarkNo(), bo.getSupplierId(), e.getMessage(), e);
        }
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
