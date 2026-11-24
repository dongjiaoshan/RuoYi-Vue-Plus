package org.dromara.djs.warehouse.demand.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.demand.domain.query.DemandPrintQuery;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintDetailVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintItemVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintSheetVo;
import org.dromara.djs.warehouse.demand.mapper.DemandPrintMapper;
import org.dromara.djs.warehouse.demand.service.IDemandPrintService;
import org.springframework.stereotype.Service;

import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintSummaryVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintSummaryItemVo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 需求单打印服务实现（V7-R8）。
 *
 * <p>只读聚合，不改任何需求状态：打印后「是否已备完货」由仓库在纸面上手填登记（甲方原文），系统不回写。</p>
 *
 * @author djs
 * @since V7-R8
 */
@Service
@RequiredArgsConstructor
public class DemandPrintServiceImpl implements IDemandPrintService {

    private final DemandPrintMapper demandPrintMapper;

    @Override
    public TableDataInfo<DemandPrintSheetVo> querySheetPage(DemandPrintQuery query, PageQuery pageQuery) {
        if (query == null || query.getDemandDate() == null) throw new ServiceException("请选择需求日期", 400);
        DemandPrintQuery q = query;
        Page<DemandPrintSheetVo> page = demandPrintMapper.selectSheetPage(
            pageQuery.build(), q.getDemandDate(), q.getStoreIds());
        return TableDataInfo.build(page);
    }

    @Override
    public DemandPrintDetailVo getSheetDetail(LocalDate demandDate, Long storeId) {
        List<DemandPrintItemVo> items = demandPrintMapper.selectSheetItems(demandDate, storeId);
        DemandPrintDetailVo vo = new DemandPrintDetailVo();
        vo.setDemandDate(demandDate);
        vo.setStoreId(storeId);
        vo.setStoreName(demandPrintMapper.selectStoreName(storeId));
        vo.setItems(groupByRemark(items));
        // 备注拆行不能使同一产品重复计为多个品类，与列表 COUNT(DISTINCT product_id) 一致。
        vo.setCategoryCount((int) items.stream().map(DemandPrintItemVo::getProductId).distinct().count());
        return vo;
    }

    /** 备注组按去重产品数降序，组内沿用 Mapper 的产品顺序；同品类数保持原先稳定次序。 */
    private static List<DemandPrintItemVo> groupByRemark(List<DemandPrintItemVo> items) {
        Map<String, List<DemandPrintItemVo>> groups = new LinkedHashMap<>();
        for (DemandPrintItemVo item : items) {
            // Mapper 已按 TRIM + 二进制排序规则归一备注；此处不再改变大小写或完整文字。
            String remark = item.getDemandRemark() == null ? "" : item.getDemandRemark();
            groups.computeIfAbsent(remark, key -> new ArrayList<>()).add(item);
        }
        return groups.values().stream()
            .sorted(Comparator.<List<DemandPrintItemVo>>comparingLong(group -> group.stream()
                .map(DemandPrintItemVo::getProductId).distinct().count()).reversed())
            .flatMap(List::stream)
            .toList();
    }

    @Override
    public DemandPrintSummaryVo getSummary(LocalDate demandDate, List<Long> storeIds) {
        if (demandDate == null) throw new ServiceException("请选择需求日期", 400);
        Map<String, SummaryGroup> groups = new LinkedHashMap<>();
        for (DemandPrintSummaryItemVo row : demandPrintMapper.selectSummaryItems(demandDate, storeIds)) {
            String name = text(row.getProductName());
            String unit = "white_bar".equals(row.getBelongType()) ? "头" : normalizedUnit(row.getProductUnit());
            SummaryGroup group = groups.computeIfAbsent(name, key -> new SummaryGroup(key, unit));
            if (!unit.equals(group.item.getProductUnit())) {
                throw new ServiceException("产品“" + name + "”存在不同单位的已确认需求，无法合并汇总，请先统一计量单位", 400);
            }
            group.item.setDemandQuantity(group.item.getDemandQuantity().add(
                row.getDemandQuantity() == null ? BigDecimal.ZERO : row.getDemandQuantity()));
            if (!text(row.getProductSpec()).isEmpty()) group.specs.add(text(row.getProductSpec()));
            if (!text(row.getRawMaterialName()).isEmpty()) group.materials.add(text(row.getRawMaterialName()));
        }
        List<DemandPrintSummaryItemVo> items = new ArrayList<>();
        for (SummaryGroup group : groups.values()) {
            group.item.setProductSpec(String.join("；", group.specs));
            group.item.setRawMaterialName(String.join("；", group.materials));
            items.add(group.item);
        }
        DemandPrintSummaryVo result = new DemandPrintSummaryVo();
        result.setDemandDate(demandDate);
        result.setItems(items);
        return result;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizedUnit(String value) {
        String unit = text(value);
        return "kg".equals(unit.toLowerCase(Locale.ROOT)) || "公斤".equals(unit) ? "kg" : unit;
    }

    private static class SummaryGroup {
        private final DemandPrintSummaryItemVo item = new DemandPrintSummaryItemVo();
        private final Set<String> specs = new TreeSet<>();
        private final Set<String> materials = new TreeSet<>();

        SummaryGroup(String name, String unit) {
            item.setProductName(name);
            item.setProductUnit(unit);
            item.setDemandQuantity(BigDecimal.ZERO);
        }
    }

}
