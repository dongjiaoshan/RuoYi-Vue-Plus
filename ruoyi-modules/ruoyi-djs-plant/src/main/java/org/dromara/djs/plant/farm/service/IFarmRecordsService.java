package org.dromara.djs.plant.farm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.plant.farm.domain.bo.DisasterBatchBo;
import org.dromara.djs.plant.farm.domain.bo.DisasterRecordBo;
import org.dromara.djs.plant.farm.domain.bo.EmptyRecordBo;
import org.dromara.djs.plant.farm.domain.bo.GrowBatchBo;
import org.dromara.djs.plant.farm.domain.bo.HarvestWeightBo;
import org.dromara.djs.plant.farm.domain.bo.GrowRecordBo;
import org.dromara.djs.plant.farm.domain.bo.PlotPickStatusBo;
import org.dromara.djs.plant.farm.domain.bo.RotationRecordBo;
import org.dromara.djs.plant.farm.domain.bo.TransplantRecordBo;
import org.dromara.djs.plant.farm.domain.query.FarmRecordsQuery;
import org.dromara.djs.plant.farm.domain.vo.DispatchSummaryVo;
import org.dromara.djs.plant.farm.domain.vo.FarmCropPlotVo;
import org.dromara.djs.plant.farm.domain.vo.FarmCropTargetCardVo;
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

    /**
     * 生长类农事「作物 → 多地块」批量提交（FIX-PLT-MP-WORK-BATCH-001 #2）。
     *
     * <p>单事务循环复用现有单条插入逻辑，覆盖 6 类生长活动
     * （{@code water_fertilize/irrigation/weed/pest_control/pruning}）+ 退茬（{@code rotation}）。
     * farmType=rotation 时每条按退茬副作用处理（plot_status=1 + plant_details completed）。</p>
     *
     * @param bo 批量入参（farmType + N 个地块三元组 + 统一日期/班组/凭证/备注）
     * @return 成功 INSERT 的农事记录行数
     */
    int submitGrowBatch(GrowBatchBo bo);

    /**
     * 灾害「作物 → 多地块」批量提交（FIX-PLT-MP-WORK-BATCH-001 灾害批量，#2）。
     *
     * <p>单事务循环复用单条灾害插入逻辑：每地块 INSERT 一行 disaster 记录（统一 disasterType/lossRate +
     * 各自 lossYield）+ 累加对应 {@code plant_details.loss_yield}。lossRate≥30 时该行置预警。</p>
     *
     * @param bo 批量入参（N 个地块三元组 + 统一灾害类型/损失率/日期/班组）
     * @return 成功 INSERT 的灾害记录行数
     */
    int submitDisasterBatch(DisasterBatchBo bo);

    /**
     * 采摘活动管理「采摘重量录入」（FIX-PLT-MP-HARVEST-001 #3=a）。
     *
     * <p>累加对应 {@code plant_details.actual_yield}（按 plantId+plotId+cropId 定位未结束明细，
     * 无则取最新一条），并 INSERT 一行 {@code t_plant_farm_records}（farm_type=harvest_activity，
     * 携带 harvest_weight）。采收 tab 已去重量，本端点为采摘重量唯一录入口。</p>
     *
     * @param bo 采摘重量录入入参
     * @return 新增农事记录 id
     */
    Long submitHarvestWeight(HarvestWeightBo bo);

    /**
     * 农事多选页候选地块（FIX-PLT-MP-WORK-BATCH-001 #2）。
     *
     * <p>按作物列出可批量录入的种植地块（每行 plotCode + lastFarmDate + intervalDays + plantStatus）。
     * farmType=rotation（退茬）只列 {@code plant_status='completed'} 的地块；其余列 ongoing 地块。</p>
     *
     * @param cropId   作物 id
     * @param farmType 农事类型（决定过滤口径 + 计算间隔的"上次同类农事"）
     * @return 候选地块卡列表（无数据返空列表）
     */
    List<FarmCropPlotVo> listCropPlots(Long cropId, String farmType);

    /**
     * 工种列表页「作物目标卡」聚合（FIX-PLT-MP-CROPSEL-001）。
     *
     * <p>种植类工种作业 tab 的作物卡数据源：只列「已种植」作物，按工种状态规则过滤后聚合每个作物的
     * 可操作地块去重数 + 最近同工种农事日期，并回填作物图（IMG-LIB-001 resolver）。</p>
     *
     * <p>工种 → 状态规则（{@code resolveStatusFilter} 一处定义）：rotation→{@code harvest_status='completed'}；
     * transplant→{@code plant_status='ongoing'} 且 {@code plot_type='nursery'}；其余生长→{@code plant_status='ongoing'}。</p>
     *
     * @param farmType 农事类型（决定状态规则 + 最近同工种农事日期口径）
     * @param zoneId   片区 id（可空，非空时只统计该片区下地块）
     * @param plotCode 地块编号（可空，非空时只统计该地块）
     * @return 作物目标卡列表（无数据返空列表）
     */
    List<FarmCropTargetCardVo> listCropTargetCards(String farmType, Long zoneId, String plotCode);

    /**
     * 土地采摘状态调整（FIX-PLT-MP-HARVEST-001 #3，HARVEST 富页）。
     *
     * <p>手动切换地块采摘状态（采摘中 ↔ 采摘完成），仅状态调整不录重量（重量录入权威源为采收 tab）。
     * 更新 {@code plant_details.harvest_status} + 写一条 {@code t_plant_farm_records}（farm_type=harvest_activity）留痕。
     * 切到"采摘完成"时回填 {@code end_harvestdate}。</p>
     *
     * @param bo 状态调整入参（plotId/cropId/pickStatus/adjustDate/teamId）
     * @return 受影响的明细行数
     */
    int adjustPlotPickStatus(PlotPickStatusBo bo);

    /** mp 中央分发台聚合：今日 12 类「已处理（当日去重地块数）+ 待处理（空地池）」两套数。 */
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
