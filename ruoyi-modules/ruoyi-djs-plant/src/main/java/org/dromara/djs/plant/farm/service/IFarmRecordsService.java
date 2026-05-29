package org.dromara.djs.plant.farm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.farm.domain.bo.DisasterRecordBo;
import org.dromara.djs.plant.farm.domain.bo.EmptyRecordBo;
import org.dromara.djs.plant.farm.domain.bo.GrowRecordBo;
import org.dromara.djs.plant.farm.domain.bo.RotationRecordBo;
import org.dromara.djs.plant.farm.domain.bo.TransplantRecordBo;
import org.dromara.djs.plant.farm.domain.query.FarmRecordsQuery;
import org.dromara.djs.plant.farm.domain.vo.DispatchSummaryVo;
import org.dromara.djs.plant.farm.domain.vo.FarmRecordsVo;

import java.util.List;

/**
 * 农事记录 Service（PLT-WORK-001）。
 *
 * <p>核心职责：5 类提交（空地 / 生长 / 灾害 / 移栽 / 退茬）+ admin 只读列表 + mp 中央分发台聚合。
 * 灾害提交触发 {@code plant_details.loss_yield} 累加；退茬提交触发 {@code plot_info.plot_status=1} +
 * {@code plant_details.plant_status='completed'}。</p>
 *
 * @author djs
 * @since PLT-WORK-001
 */
public interface IFarmRecordsService {

    /** 空地阶段提交（farm_type ∈ tillage_break / tillage_prepare / fertilize）。 */
    Long submitEmpty(EmptyRecordBo bo);

    /** 生长阶段提交（farm_type ∈ water_fertilize / irrigation / weed / pest_control / pruning / harvest_activity）。 */
    Long submitGrow(GrowRecordBo bo);

    /** 灾害提交 — 独立画布；触发 plant_details.loss_yield 累加。 */
    Long submitDisaster(DisasterRecordBo bo);

    /** 移栽提交 — 独立画布；transplantPercent 业务规则 ≤60，BO 已 @Max 校验。 */
    Long submitTransplant(TransplantRecordBo bo);

    /** 退茬提交 — 生长阶段；触发 plot_info.plot_status=1 + plant_details 完结。 */
    Long submitRotation(RotationRecordBo bo);

    /** mp 中央分发台聚合：今日 12 类各自记录条数。 */
    DispatchSummaryVo dispatchSummary();

    /** mp 我的记录（按 operatorId 注入查询，时间倒序）。 */
    TableDataInfo<FarmRecordsVo> myRecords(FarmRecordsQuery query, PageQuery pageQuery);

    /** admin 分页列表。 */
    TableDataInfo<FarmRecordsVo> queryPageList(FarmRecordsQuery query, PageQuery pageQuery);

    /** admin 导出列表。 */
    List<FarmRecordsVo> queryList(FarmRecordsQuery query);

    /** admin 详情。 */
    FarmRecordsVo queryById(Long id);
}
