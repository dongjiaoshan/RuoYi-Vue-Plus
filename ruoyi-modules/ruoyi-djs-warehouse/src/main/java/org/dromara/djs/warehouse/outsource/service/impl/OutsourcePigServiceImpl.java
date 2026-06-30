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
import org.dromara.common.satoken.utils.LoginHelper;
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
import org.springframework.transaction.annotation.Transactional;

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
        fillArriveTimeFromBar(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<OutsourcePigVo> queryList(OutsourcePigQuery query) {
        List<OutsourcePigVo> list = baseMapper.selectVoList(buildQueryWrapper(query));
        fillSupplierName(list);
        fillArriveTimeFromBar(list);
        return list;
    }

    @Override
    public OutsourcePigVo queryById(Long id) {
        OutsourcePigVo vo = baseMapper.selectVoById(id);
        if (vo != null) {
            fillSupplierName(List.of(vo));
            fillArriveTimeFromBar(List.of(vo));
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
        // 回写 bar_id 到外购台账，建立单头精确关联（删除拦截据此反查白条状态）。
        createOutsourceBar(bo, entity.getId());
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
     * <p>补 bar 失败不应阻塞外购登记主流程（外购台账已写成功），故 swallow + log（同 listener 失败策略）。
     * 成功时把生成的 barId 回写到外购台账 {@code outsource_pig.bar_id}（删除拦截据此反查白条状态）。</p>
     *
     * @param bo            外购入参
     * @param outsourcePigId 已插入的外购台账主键（回写 bar_id 用）
     */
    private void createOutsourceBar(OutsourcePigBo bo, Long outsourcePigId) {
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
            // 回写 bar_id 到外购台账，建立单头精确关联
            OutsourcePig upd = new OutsourcePig();
            upd.setId(outsourcePigId);
            upd.setBarId(bar.getBarId());
            baseMapper.updateById(upd);
            log.info("[DJS-FIX-WMS-RALN] 外购猪 bar_info 创建成功 barId={} pigMarkNo={} supplierId={}",
                bar.getBarId(), bo.getPigMarkNo(), bo.getSupplierId());
        } catch (Exception e) {
            log.error("[DJS-FIX-WMS-RALN] 外购猪自动创建 bar_info 失败 pigMarkNo={} supplierId={}: {}",
                bo.getPigMarkNo(), bo.getSupplierId(), e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteWithValidByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // 删除拦截：外购猪只录入后已镜像一条白条（bar_info），若该白条已进入下游处理流程
        // （燎毛/分割/入库等，status != pending_singe），禁止删除外购台账（避免下游链路悬空）。
        List<OutsourcePig> pigs = baseMapper.selectByIds(ids);
        if (pigs != null) {
            for (OutsourcePig pig : pigs) {
                if (StringUtils.isBlank(pig.getBarId())) {
                    continue;
                }
                String barStatus = barInfoMapper.selectStatusByBarId(pig.getBarId());
                if (barStatus != null && !BAR_STATUS_PENDING_SINGE.equals(barStatus)) {
                    throw new ServiceException("该外购猪只已进入下游处理流程（燎毛/分割），不允许删除");
                }
            }
            // 校验通过：级联软删每条非空 barId 镜像出的待燎毛白条，避免孤儿行仍被 mp 燎毛列表查出。
            for (OutsourcePig pig : pigs) {
                if (StringUtils.isNotBlank(pig.getBarId())) {
                    barInfoMapper.softDeleteByBarIdIfPending(pig.getBarId(), LoginHelper.getUserId());
                }
            }
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
     * 外购到场时间回填（FIX-WMS-OUTSOURCE-001 行38）：到场时间 = 关联白条「燎毛间称重完成时刻」
     * （{@code bar_info.in_time}，精确到时分秒），取代原新增表单手填日期（整列恒 00:00:00 的根因）。
     *
     * <p>按 {@code outsource_pig.bar_id} 批量查关联白条 {@code in_time}（一次 IN 查询避免 N+1），
     * 命中且非空则覆盖 {@code arriveTime}；未燎毛称重（白条仍 pending_singe，{@code in_time} 为 NULL）
     * 或无关联白条时保留实体原值（兼容历史录入），前端按 datetime 渲染。</p>
     */
    private void fillArriveTimeFromBar(List<OutsourcePigVo> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<String> barIds = rows.stream()
            .map(OutsourcePigVo::getBarId)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toSet());
        if (barIds.isEmpty()) {
            return;
        }
        Map<String, Map<String, Object>> inTimeMap = barInfoMapper.selectInTimeByBarIds(barIds);
        if (inTimeMap == null || inTimeMap.isEmpty()) {
            return;
        }
        for (OutsourcePigVo vo : rows) {
            if (StringUtils.isBlank(vo.getBarId())) {
                continue;
            }
            Map<String, Object> row = inTimeMap.get(vo.getBarId());
            if (row == null) {
                continue;
            }
            Object inTime = row.get("inTime");
            if (inTime instanceof Date dt) {
                vo.setArriveTime(dt);
            } else if (inTime instanceof java.time.LocalDateTime ldt) {
                vo.setArriveTime(Date.from(ldt.atZone(java.time.ZoneId.systemDefault()).toInstant()));
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
        boolean hasSupplierIds = query.getSupplierIds() != null && !query.getSupplierIds().isEmpty();
        boolean hasBuyers = query.getBuyers() != null && !query.getBuyers().isEmpty();
        wrapper.in(hasSupplierIds, OutsourcePig::getSupplierId, query.getSupplierIds())
            .eq(!hasSupplierIds && query.getSupplierId() != null, OutsourcePig::getSupplierId, query.getSupplierId())
            .in(hasBuyers, OutsourcePig::getBuyer, query.getBuyers())
            .eq(!hasBuyers && StringUtils.isNotBlank(query.getBuyer()), OutsourcePig::getBuyer, query.getBuyer());
        applyDateRange(wrapper, OutsourcePig::getPurchaseDate,
            query.getPurchaseDateFrom(), query.getPurchaseDateTo());
        applyDayEq(wrapper, OutsourcePig::getArriveTime, query.getArriveTime());
        wrapper.orderByDesc(OutsourcePig::getId);
        return wrapper;
    }

    /**
     * 采购日期范围匹配：[From 00:00:00, To 次日 00:00:00)（兼容 date / datetime 列）。
     * 入参为 yyyy-MM-dd 字符串，空白边界单侧开放。
     */
    private void applyDateRange(LambdaQueryWrapper<OutsourcePig> wrapper,
                                SFunction<OutsourcePig, ?> column,
                                String from, String to) {
        Date start = parseDayStart(from);
        Date end = parseDayStart(to);
        if (end != null) {
            Calendar c = Calendar.getInstance();
            c.setTime(end);
            c.add(Calendar.DAY_OF_MONTH, 1);
            end = c.getTime();
        }
        wrapper.ge(start != null, column, start)
            .lt(end != null, column, end);
    }

    /**
     * yyyy-MM-dd → 当天 00:00:00 的 {@link Date}；空白 / 非法格式返回 null（边界开放）。
     */
    private Date parseDayStart(String day) {
        if (StringUtils.isBlank(day)) {
            return null;
        }
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd").parse(day.trim());
        } catch (java.text.ParseException ex) {
            log.warn("[DJS-FIX-WMS-RALN] 外购猪只采购日期范围非法 day=[{}]，忽略该边界", day);
            return null;
        }
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
