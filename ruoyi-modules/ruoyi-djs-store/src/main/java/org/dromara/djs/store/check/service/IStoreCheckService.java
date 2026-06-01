package org.dromara.djs.store.check.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.store.check.domain.bo.StoreCheckCreateBo;
import org.dromara.djs.store.check.domain.bo.StoreCheckLineBo;
import org.dromara.djs.store.check.domain.query.StoreCheckQuery;
import org.dromara.djs.store.check.domain.vo.StoreCheckHeaderVo;
import org.dromara.djs.store.check.domain.vo.StoreCheckRecordVo;

import java.util.List;

/**
 * 门店盘点 Service（STR-STOCK-001）。
 *
 * <p>覆盖 admin 盘点单管理（新建锁门店 / 列表 / 详情逐项核对 / 完成差异处理 / 取消解锁）+
 * 详情页实盘录入（提交 line / 删）+ store+product 维度业务锁查询（{@link #isProductLocked}），
 * 锁查询暴露给门店销售出库入口（STR-OP-001）做后端双保险。</p>
 *
 * <p>与仓库盘点（WMS-STOCK-001）最大区别：admin only 无 mp；门店无独立库存表，完成盘点仅落
 * check_record 差异 + status='completed'，不回写实物库存（V1 保守口径）。</p>
 *
 * @author djs
 * @since STR-STOCK-001
 */
public interface IStoreCheckService {

    // ---------- admin 盘点单管理 ----------

    /**
     * 新建盘点单：INSERT header（{@code isHeader=1 / status='in_progress'}）→ 锁门店。
     *
     * <p>若该门店已有进行中盘点单 → 抛 ServiceException（一门店同时只允许一张盘点单）。</p>
     *
     * @return 新建 header 行主键
     */
    Long createCheck(StoreCheckCreateBo bo);

    /**
     * 完成盘点（admin）：各 line 差异已在录入时算好落库，本步仅把 header status='completed' → 解锁门店。
     *
     * <p>门店无独立库存表（V1），不回写实物库存、不写库存流水（与 WMS 不同）；差异仅留痕在
     * {@code t_store_check_record}。库存回写 hook 留 followup（见 reports raise）。</p>
     *
     * @param id header 行主键
     */
    void completeCheck(Long id);

    /**
     * 取消盘点（admin）：header status='completed' 仅解锁门店，明细 line 留痕不变。
     *
     * @param id header 行主键
     */
    void cancelCheck(Long id);

    /**
     * admin 盘点单列表（盘点单维度，header 聚合 + 盈亏计）。
     */
    TableDataInfo<StoreCheckHeaderVo> queryHeaderPage(StoreCheckQuery query, PageQuery pageQuery);

    /**
     * 盘点明细 line 列表（详情逐项核对 / 导出用，不分页）。
     */
    List<StoreCheckRecordVo> queryLineList(StoreCheckQuery query);

    /**
     * 按盘点单 {@code checkId} 查已录入的明细 line 列表（详情页用）。
     */
    List<StoreCheckRecordVo> queryLinesByCheckId(String checkId);

    // ---------- 实盘录入（admin 详情页，无 mp）----------

    /**
     * 提交 / 修改实盘明细 line：按 {@code checkId + storeId + productId} upsert，
     * 自动算 {@code sysStock}（门店该产品系统量，V1 门店无库存表默认 0）+ {@code diffStock}。
     *
     * <p>header 不是 in_progress（如已 completed）→ 抛 ServiceException 拒绝录入。</p>
     *
     * @return line 行主键
     */
    Long submitLine(StoreCheckLineBo bo);

    /**
     * 删除实盘明细 line（仅 in_progress 盘点单可删）。
     */
    void removeLine(Long id);

    // ---------- 业务锁查询（供门店销售出库入口 STR-OP-001 调用）----------

    /**
     * 门店该产品是否处于盘点锁定（该门店有进行中盘点单）。
     *
     * @param storeId   门店 ID
     * @param productId 产品 ID
     * @return true = 被锁
     */
    boolean isProductLocked(Long storeId, Long productId);

    /**
     * 断言门店该产品未被盘点锁定：被锁则抛 ServiceException（门店销售出库入口调用，后端双保险）。
     *
     * @param storeId   门店 ID
     * @param productId 产品 ID
     */
    void assertProductUnlocked(Long storeId, Long productId);

}
