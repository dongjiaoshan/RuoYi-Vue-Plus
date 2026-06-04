package org.dromara.djs.plant.perf.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.perf.domain.query.PlantWorkPerformanceQuery;
import org.dromara.djs.plant.perf.domain.vo.PlantWorkPerformanceVo;

import java.util.List;

/**
 * 班组绩效结算 Service（PLT-PERF-001）。
 *
 * @author djs
 * @since PLT-PERF-001
 */
public interface IPlantWorkPerformanceService {

    /**
     * 分页查询绩效列表（含 teamName / cropName enrich）。
     */
    TableDataInfo<PlantWorkPerformanceVo> queryPageList(PlantWorkPerformanceQuery query, PageQuery pageQuery);

    /**
     * 列表查询（不分页，导出用）。
     */
    List<PlantWorkPerformanceVo> queryList(PlantWorkPerformanceQuery query);

    /**
     * 绩效行详情（产量绩效 tab 数据）。
     */
    PlantWorkPerformanceVo queryById(Long id);

    /**
     * 手动生成指定月份的班组绩效结算（幂等）。
     *
     * <p>步骤：1. 软删该月已有结算行（del_flag '0' → '2'）避免重复累加；
     * 2. 按 班组 × 作物 聚合 details.actual_yield；
     * 3. 逐组读 crop.pick_unit_price 作单价快照算金额；4. 批量 INSERT。</p>
     *
     * @param statMonth 统计月份（"yyyy-MM"）
     * @return 新生成的结算行数（= 聚合分组数）
     */
    int generate(String statMonth);
}
