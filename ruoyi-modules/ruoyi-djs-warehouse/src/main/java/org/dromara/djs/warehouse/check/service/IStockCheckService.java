package org.dromara.djs.warehouse.check.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.check.domain.bo.StockCheckCreateBo;
import org.dromara.djs.warehouse.check.domain.bo.StockCheckLineBo;
import org.dromara.djs.warehouse.check.domain.query.StockCheckQuery;
import org.dromara.djs.warehouse.check.domain.vo.StockCheckHeaderVo;
import org.dromara.djs.warehouse.check.domain.vo.StockCheckRecordVo;

import java.util.List;

/**
 * 库存盘点 Service（WMS-STOCK-001）。
 *
 * <p>覆盖 admin 盘点单管理（新建锁库位 / 列表 / 完成回写解锁 / 取消解锁）+ mp 实盘录入
 * （提交 line / 改 / 删）+ 库位级业务锁查询（{@link #isLocationLocked}）。</p>
 *
 * @author djs
 * @since WMS-STOCK-001
 */
public interface IStockCheckService {

    // ---------- admin 盘点单管理 ----------

    /**
     * 新建盘点单：INSERT header（{@code isHeader=1 / status='in_progress'}）→ 锁库位。
     *
     * <p>若该库位已有进行中盘点单 → 抛 ServiceException（一库位同时只允许一张盘点单）。</p>
     *
     * @return 新建 header 行主键
     */
    Long createCheck(StockCheckCreateBo bo);

    /**
     * 完成盘点（admin）：按各 line {@code diffStock} 正负写流水（盘盈 check_in / 盘亏 check_out）
     * + 把 location_stock 回写至实盘量 + 刷新 latest_check_time + header status='completed' → 解锁库位。
     *
     * @param id header 行主键
     */
    void completeCheck(Long id);

    /**
     * 取消盘点（admin）：header status='completed' 但<b>不回写</b>库存、<b>不写流水</b>，仅解锁库位。
     *
     * @param id header 行主键
     */
    void cancelCheck(Long id);

    /**
     * 盘点单 header 列表（check_record 模型，header 聚合 + 盈亏计）。
     *
     * <p>供 mp overview「进行中盘点单」入口用（{@code checkStatus='in_progress'} 过滤）。
     * admin 盘点记录页改走 {@link #queryFlowCheckRecordPage}。</p>
     */
    TableDataInfo<StockCheckHeaderVo> queryHeaderPage(StockCheckQuery query, PageQuery pageQuery);

    /**
     * admin 盘点记录列表（只读）：统一从 {@code t_warehouse_stock_flow} 盘点流水
     * （{@code flow_type IN ('check_in','check_out')}）按「日期 + 库位 + 盘点人」聚合成盘点单行。
     *
     * <p>覆盖 mp 工人自助盘点 + admin 完成盘点两路来源，修「mp 提交的盘点在 admin 列表查不到」。</p>
     */
    TableDataInfo<StockCheckHeaderVo> queryFlowCheckRecordPage(StockCheckQuery query, PageQuery pageQuery);

    /**
     * admin 盘点记录明细 line 列表（详情钻取，不分页）：按合成 {@code checkId}
     * （{@code yyyyMMdd-locationId-operatorId}）读该组盘点流水。
     */
    List<StockCheckRecordVo> queryLineList(StockCheckQuery query);

    /**
     * 盘点记录列表导出（不分页，盘点单维度）：同 {@link #queryHeaderPage} 数据源
     * （stock_flow 盘点流水聚合），导出全部命中行。
     */
    List<StockCheckHeaderVo> exportHeaderList(StockCheckQuery query);

    // ---------- mp 实盘录入 ----------

    /**
     * 提交 / 修改实盘明细 line（mp）：按 {@code checkId + locationId + productId} upsert，
     * 自动算 {@code sysStock}（查 location_stock 现量）+ {@code diffStock}。
     *
     * <p>header 不是 in_progress（如已 completed）→ 抛 ServiceException 拒绝录入。</p>
     *
     * @return line 行主键
     */
    Long submitLine(StockCheckLineBo bo);

    /**
     * 删除实盘明细 line（mp；仅 in_progress 盘点单可删）。
     */
    void removeLine(Long id);

    /**
     * mp 盘点员查某盘点单已录入的 line 列表（按 checkId）。
     */
    List<StockCheckRecordVo> queryLinesByCheckId(String checkId);

    // ---------- 业务锁查询（供 stock_flow 写入入口调用）----------

    /**
     * 库位是否处于盘点锁定（有进行中盘点单）。
     *
     * @param locationId 库位 ID
     * @return true = 被锁
     */
    boolean isLocationLocked(Long locationId);

    /**
     * 断言库位未被盘点锁定：被锁则抛 ServiceException（出入库写入入口调用，后端双保险）。
     *
     * @param locationId 库位 ID
     */
    void assertLocationUnlocked(Long locationId);

}
