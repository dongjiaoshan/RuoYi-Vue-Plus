package org.dromara.djs.warehouse.demand.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.demand.domain.query.DemandPrintQuery;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintDetailVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintItemVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintSheetVo;
import org.dromara.djs.warehouse.demand.mapper.DemandPrintMapper;
import org.dromara.djs.warehouse.demand.service.IDemandPrintService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        DemandPrintQuery q = query == null ? new DemandPrintQuery() : query;
        Page<DemandPrintSheetVo> page = demandPrintMapper.selectSheetPage(
            pageQuery.build(), q.getBeginDate(), q.getEndDate(), q.getStoreIds());
        return TableDataInfo.build(page);
    }

    @Override
    public DemandPrintDetailVo getSheetDetail(LocalDate demandDate, Long storeId) {
        List<DemandPrintItemVo> items = demandPrintMapper.selectSheetItems(demandDate, storeId);
        Map<Long, Set<String>> remarks = new HashMap<>();
        for (DemandPrintItemVo row : demandPrintMapper.selectSheetRemarks(demandDate, storeId)) {
            String remark = row.getDemandRemark();
            if (remark != null && !remark.isBlank()) {
                remarks.computeIfAbsent(row.getProductId(), key -> new LinkedHashSet<>()).add(remark.trim());
            }
        }
        for (DemandPrintItemVo item : items) {
            Set<String> productRemarks = remarks.get(item.getProductId());
            item.setDemandRemark(productRemarks == null ? null : String.join("；", productRemarks));
        }
        DemandPrintDetailVo vo = new DemandPrintDetailVo();
        vo.setDemandDate(demandDate);
        vo.setStoreId(storeId);
        vo.setStoreName(demandPrintMapper.selectStoreName(storeId));
        vo.setItems(items);
        // 品类总数直接取明细行数：明细已按 product_id 合并，行数即去重产品数，与列表 COUNT(DISTINCT) 同口径
        vo.setCategoryCount(items.size());
        return vo;
    }
}
