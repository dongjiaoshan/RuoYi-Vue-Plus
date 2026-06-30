package org.dromara.djs.warehouse.selfcheck.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.selfcheck.domain.bo.ProductInboundBo;
import org.dromara.djs.warehouse.selfcheck.domain.bo.ProductOutboundBo;
import org.dromara.djs.warehouse.selfcheck.domain.bo.StockCheckEntryBo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.CheckRecordVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.InoutFlowVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.PendingCheckVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.StockStoreEntryVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.StoreProductVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.WhiteBarStockVo;

import java.util.List;

/**
 * 库存盘点自助子系统 Service（SELFCHECK，mp「分拣发货 / 库存盘点」工人自助子系统）。
 *
 * <p>与 admin 盘点单模型（{@link org.dromara.djs.warehouse.check.service.IStockCheckService}）解耦：
 * 本子系统是工人在各库自助看库存 + 录入入库 / 出库 / 盘点（方案 A：每次盘点写流水留痕 + 回写库存）。</p>
 *
 * <p>写端点写前调 {@link org.dromara.djs.warehouse.check.service.IStockCheckService#assertLocationUnlocked}
 * 防锁库位出入库；库存增减 / 系统量复用 {@code LocationStockMapper} / {@code StockCheckServiceImpl} 既有方法。</p>
 *
 * @author djs
 * @since SELFCHECK
 */
public interface IStockSelfService {

    /**
     * 各库入口聚合列表（库存盘点首页）。
     */
    List<StockStoreEntryVo> listStoreEntries();

    /**
     * 库详情标准库产品列表。
     *
     * @param locationId 库位 ID（snowflake string）
     * @param keyword    产品名关键字（可空）
     * @param sort       排序 stock_asc / stock_desc（默认 desc）
     */
    List<StoreProductVo> listStoreProducts(String locationId, String keyword, String sort);

    /**
     * 白条库整只逐条列表（特殊形态；locationId 不参与过滤，白条是逻辑库）。
     *
     * @param locationId 库位 ID（接收但不过滤）
     */
    List<WhiteBarStockVo> listWhiteBarStocks(String locationId);

    /**
     * 待盘点产品列表（库存盘点 tab）。
     */
    List<PendingCheckVo> listPendingChecks(String locationId, String keyword);

    /**
     * 盘点记录分页（盘点记录 tab）。
     */
    TableDataInfo<CheckRecordVo> pageCheckRecords(String locationId, String checkDate,
                                                  String keyword, String checkResult, PageQuery pageQuery);

    /**
     * 进出库流水分页（入库记录 / 出库记录 tab）。
     *
     * @param direction in 入库 / out 出库
     */
    TableDataInfo<InoutFlowVo> pageInoutFlows(String direction, String locationId,
                                              String startDate, String endDate, String keyword, PageQuery pageQuery);

    /**
     * 产品入库：写 stock_flow（IN）+ 加库存。
     *
     * @return 新建 flow 行主键
     */
    Long inbound(ProductInboundBo bo);

    /**
     * 产品出库：写 stock_flow（OT）+ 扣库存（不足抛 ServiceException）。
     *
     * @return 新建 flow 行主键
     */
    Long outbound(ProductOutboundBo bo);

    /**
     * 盘点录入提交（方案 A）：写盘点流水留痕 + 回写库存至实盘绝对值。
     *
     * @return 新建 flow 行主键
     */
    Long checkSubmit(StockCheckEntryBo bo);

}
