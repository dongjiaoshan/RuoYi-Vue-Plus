package org.dromara.djs.warehouse.veg.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.veg.domain.bo.HandleRecordSubmitBo;
import org.dromara.djs.warehouse.veg.domain.bo.HarvestSubmitBo;
import org.dromara.djs.warehouse.veg.domain.bo.PickActivitySubmitBo;
import org.dromara.djs.warehouse.veg.domain.bo.PickDestSubmitBo;
import org.dromara.djs.warehouse.veg.domain.bo.ProcessSubmitBo;
import org.dromara.djs.warehouse.veg.domain.query.VegHandleQuery;
import org.dromara.djs.warehouse.veg.domain.vo.HandleRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.PendingPlantingRecordVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegCropVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegPlotDetailVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegetableHandleVo;

import java.util.List;

/**
 * 毛菜处理 Service（WMS-VEG-001）。
 *
 * @author djs
 * @since WMS-VEG-001
 */
public interface IVegetableHandleService {

    /**
     * mp 提交处理记录（事务一致）。
     *
     * @return vegetable_handle.id（汇总主键，前端进入"我的"列表时回展）
     */
    Long submitHandleRecord(HandleRecordSubmitBo bo);

    /**
     * mp 待处理 planting_record 列表（最近 50 条 handle_status != 'done'）。
     */
    List<PendingPlantingRecordVo> listPending();

    /**
     * mp 毛菜处理菜品列表（按 crop 聚合 4 重量）。
     */
    List<VegCropVo> listCrops();

    /**
     * mp 某菜品下地块明细列表。
     *
     * @param cropId 作物 ID
     */
    List<VegPlotDetailVo> listPlotsByCrop(Long cropId);

    /**
     * mp 采摘重量录入（事务一致）。record_type=1，聚合 picked_weight，weighFinish=1 时置 is_weighed=1。
     *
     * @return vegetable_handle.id（汇总主键）
     */
    Long submitHarvest(HarvestSubmitBo bo);

    /**
     * mp 果蔬处理录入（事务一致）。record_type=2，按 handle_target 分流聚合，processFinish=1 时推 done。
     *
     * @return vegetable_handle.id（汇总主键）
     */
    Long submitProcess(ProcessSubmitBo bo);

    /**
     * 采摘去向仓库入账（DENGBO-R4 决策 A，跨模块入口）。
     *
     * <p>采摘活动「非销售去向」直写仓库台账，复用毛菜处理写入机制：</p>
     * <ul>
     *   <li>{@code veg_fresh}（毛菜保鲜室）= 入库：stock_flow(veg_stock_in) + location_stock(按 plot 篮)</li>
     *   <li>{@code platform}（果蔬月台）= 月台暂存：无库存表写入（毛菜处理月台亦仅汇总，无独立流水），仅记日志</li>
     *   <li>{@code loss}（损耗）= loss_flow(veg_handle_loss)</li>
     *   <li>{@code feed}（饲料饲喂）= feed_log(feed_type=veg_handle)</li>
     * </ul>
     *
     * <p>不写 {@code vegetable_handle} 汇总行 / {@code handle_record}（那是毛菜处理状态机的私有产物，
     * 采摘去向不经其状态机）。{@code sale}（销售）不写仓库，调用方不应传 sale 进来。</p>
     *
     * <p>本方法在 warehouse 模块（warehouse → plant 单向依赖），由编排方（mp 采摘去向端点）
     * 调用前置 plant 端写 activity per-event 行 + actual_yield 分摊。</p>
     *
     * @param bo 采摘去向入账入参（cropId/plotId/productId/dest/weight/recorderId）
     */
    void recordPickDestination(PickDestSubmitBo bo);

    /**
     * 采摘去向录入编排入口（DENGBO-R4 决策 A，mp 采摘活动录入弹窗，同事务）。
     *
     * <p>warehouse → plant 单向依赖，编排放 warehouse 侧：</p>
     * <ol>
     *   <li>plant：{@code IPlantActivityService.recordPickActivity}（activity per-event 行 + 非销售累加地块产量）</li>
     *   <li>非销售去向：{@link #recordPickDestination}（按去向写仓库台账）</li>
     * </ol>
     *
     * @param bo 采摘去向编排入参
     * @return 新 activity 行 id（plant 侧）
     */
    Long submitPickActivity(PickActivitySubmitBo bo);

    /**
     * admin 列表（分页）。
     */
    TableDataInfo<VegetableHandleVo> queryPageList(VegHandleQuery query, PageQuery pageQuery);

    /**
     * admin 列表（导出，不分页）。
     */
    List<VegetableHandleVo> queryList(VegHandleQuery query);

    /**
     * admin 详情。
     */
    VegetableHandleVo queryById(Long id);

    /**
     * 详情下钻 handle_record 列表（admin / mp 通用）。
     */
    List<HandleRecordVo> listRecords(Long handleId);

    /**
     * mp "我的处理记录"（按 handle_user = current）。
     */
    TableDataInfo<HandleRecordVo> myRecords(PageQuery pageQuery);

}
