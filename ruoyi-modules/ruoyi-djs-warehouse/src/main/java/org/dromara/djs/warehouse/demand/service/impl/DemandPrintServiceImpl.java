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
import java.util.List;

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
        DemandPrintDetailVo vo = new DemandPrintDetailVo();
        vo.setDemandDate(demandDate);
        vo.setStoreId(storeId);
        vo.setStoreName(demandPrintMapper.selectStoreName(storeId));
        vo.setItems(items);
        // 备注拆行不能使同一产品重复计为多个品类，与列表 COUNT(DISTINCT product_id) 一致。
        vo.setCategoryCount((int) items.stream().map(DemandPrintItemVo::getProductId).distinct().count());
        return vo;
    }
}
