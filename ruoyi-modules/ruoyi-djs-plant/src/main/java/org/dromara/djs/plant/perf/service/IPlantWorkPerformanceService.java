package org.dromara.djs.plant.perf.service;

import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.perf.domain.query.PlantWorkPerformanceQuery;
import org.dromara.djs.plant.perf.domain.vo.PerfListRow;
import org.dromara.djs.plant.perf.domain.vo.PlantWorkPerformanceVo;

import java.util.List;

/**
 * 班组绩效结算 Service（PLT-PERF-001 + rework 134/135）。
 *
 * @author djs
 * @since PLT-PERF-001
 */
public interface IPlantWorkPerformanceService {

    /**
     * 分页查询绩效列表（班组 × 月聚合，含 teamName enrich + farmCount 批量补）。
     */
    TableDataInfo<PerfListRow> queryPageList(PlantWorkPerformanceQuery query, PageQuery pageQuery);

    /**
     * 列表查询（不分页，导出用；班组 × 月聚合行，与主列表一致）。
     */
    List<PerfListRow> queryList(PlantWorkPerformanceQuery query);

    /**
     * 详情按作物分行：查某 班组 × 月 下全部作物绩效行（产量绩效 tab，含 cropName enrich）。
     *
     * @param teamId    班组 ID
     * @param statMonth 统计月份 yyyy-MM
     * @return 逐作物绩效行
     */
    List<PlantWorkPerformanceVo> queryCropRows(Long teamId, String statMonth);

    /**
     * 手动生成指定月份的班组绩效结算（幂等）。
     *
     * <p>步骤：1. 软删该月已有结算行（del_flag '0' → '2'）避免重复累加；
     * 2. 按 班组 × 作物 聚合 details.actual_yield（公斤）；
     * 3. 逐组读 crop.pick_unit_price（元/斤）作单价快照，
     * 金额 = 采摘量(公斤) × 单价(元/斤) × 2（公斤→斤换算）；4. 批量 INSERT。</p>
     *
     * @param statMonth 统计月份（"yyyy-MM"）
     * @return 新生成的结算行数（= 聚合分组数）
     */
    int generate(String statMonth);

    /**
     * 导出某 班组 × 月 绩效详情（双 sheet Excel，与详情抽屉两 tab 同口径）。
     *
     * <p>sheet1「产量绩效」= {@link #queryCropRows} 逐作物行；
     * sheet2「农事记录」= 该班组该月 t_plant_farm_records 全量（farm_by = teamId，
     * farm_date ∈ statMonth 整月）。文件名「绩效详情_&lt;statMonth&gt;.xlsx」。</p>
     *
     * @param teamId    班组 ID
     * @param statMonth 统计月份 yyyy-MM
     * @param response  响应体（附件流写出）
     */
    void exportDetail(Long teamId, String statMonth, HttpServletResponse response);
}
