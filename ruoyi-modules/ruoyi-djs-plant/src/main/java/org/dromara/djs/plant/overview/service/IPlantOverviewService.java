package org.dromara.djs.plant.overview.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.overview.domain.vo.CropDetailVo;
import org.dromara.djs.plant.overview.domain.vo.PlantOverviewSummaryVo;

import java.util.List;

/**
 * 种植总览 Service（FIX-PLT-AD-OVERVIEW-001）。
 *
 * <p>纯只读聚合：summary（5 产量 KPI 吨 + 作物卡片 kg）、cropDetail（按 cropId 逐地块明细分页）、
 * export（按 cropId 导出明细）。无写操作。</p>
 *
 * @author djs
 * @since FIX-PLT-AD-OVERVIEW-001
 */
public interface IPlantOverviewService {

    /**
     * 种植总览汇总：5 产量 KPI（吨）+ 作物卡片列表（kg）。
     *
     * @return 汇总 VO（无数据时各 KPI 为 0、crops 空列表）
     */
    PlantOverviewSummaryVo getSummary();

    /**
     * 按作物分页查询逐地块明细（14 列）。
     *
     * @param cropId    作物 id
     * @param pageQuery 分页参数
     * @return 明细分页
     */
    TableDataInfo<CropDetailVo> getCropDetailPage(Long cropId, PageQuery pageQuery);

    /**
     * 按作物查询逐地块明细全量（导出用）。
     *
     * @param cropId 作物 id
     * @return 全部明细行
     */
    List<CropDetailVo> getCropDetailList(Long cropId);

    /**
     * 作物名（导出文件名 / 详情标题）。
     *
     * @param cropId 作物 id
     * @return 作物名，无则空串
     */
    String getCropName(Long cropId);
}
