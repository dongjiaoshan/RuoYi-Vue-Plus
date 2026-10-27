package org.dromara.djs.warehouse.demand.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.demand.domain.query.DemandPrintQuery;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintDetailVo;
import org.dromara.djs.warehouse.demand.domain.vo.DemandPrintSheetVo;

import java.time.LocalDate;

/**
 * 需求单打印服务（V7-R8）。
 *
 * @author djs
 * @since V7-R8
 */
public interface IDemandPrintService {

    /**
     * 订货单列表（需求日期 × 门店）。
     *
     * @param query     日期区间 + 门店
     * @param pageQuery 分页
     * @return 分页结果
     */
    TableDataInfo<DemandPrintSheetVo> querySheetPage(DemandPrintQuery query, PageQuery pageQuery);

    /**
     * 某门店某日订货单打印明细。
     *
     * @param demandDate 需求日期
     * @param storeId    门店
     * @return 抬头 + 明细（该日该店无有效需求时 items 为空、categoryCount=0）
     */
    DemandPrintDetailVo getSheetDetail(LocalDate demandDate, Long storeId);
}
