package org.dromara.djs.warehouse.selfcheck.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.selfcheck.domain.bo.ProductInboundBo;
import org.dromara.djs.warehouse.selfcheck.domain.bo.ProductOutboundBo;
import org.dromara.djs.warehouse.selfcheck.domain.bo.StockCheckEntryBo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.CheckRecordVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.InoutFlowVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.PendingCheckVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.StockStoreEntryVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.StoreProductVo;
import org.dromara.djs.warehouse.selfcheck.domain.vo.WhiteBarStockVo;
import org.dromara.djs.warehouse.selfcheck.service.IStockSelfService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库存盘点自助子系统 mp 端 Controller（SELFCHECK，原型「分拣发货 / 库存盘点」18 屏）。
 *
 * <p>9 端点（6 读 + 3 写）：</p>
 * <ul>
 *   <li>{@code GET  /applet/warehouse/stock/storeEntries}  各库入口聚合</li>
 *   <li>{@code GET  /applet/warehouse/stock/storeProducts} 库详情标准库产品列表</li>
 *   <li>{@code GET  /applet/warehouse/stock/whiteBarStocks} 白条库（半只逐条，对齐分割白条领用）</li>
 *   <li>{@code GET  /applet/warehouse/stock/pendingChecks} 待盘点产品列表</li>
 *   <li>{@code GET  /applet/warehouse/stock/checkRecords}  盘点记录分页</li>
 *   <li>{@code GET  /applet/warehouse/stock/inoutFlows}    进出库流水分页</li>
 *   <li>{@code POST /applet/warehouse/stock/inbound}       产品入库</li>
 *   <li>{@code POST /applet/warehouse/stock/outbound}      产品出库</li>
 *   <li>{@code POST /applet/warehouse/stock/checkSubmit}   盘点录入提交</li>
 * </ul>
 *
 * <p>与 admin 盘点单（{@code /applet/warehouse/check}）解耦：本子系统是工人各库自助查看 + 录入。
 * {@code @SaCheckLogin} 走 sa-token 真路（mp V1 mock 登录已发真 token，参 ADR-0003）。</p>
 *
 * @author djs
 * @since SELFCHECK
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/warehouse/stock")
public class AppletStockSelfController extends BaseController {

    private final IStockSelfService stockSelfService;

    /**
     * 各库入口聚合（库存盘点首页）。
     */
    @SaCheckLogin
    @GetMapping("/storeEntries")
    public R<List<StockStoreEntryVo>> storeEntries() {
        return R.ok(stockSelfService.listStoreEntries());
    }

    /**
     * 库详情标准库产品列表。
     *
     * @param locationId 库位 ID（snowflake string）
     * @param keyword    产品名关键字（可空）
     * @param sort       排序 stock_asc / stock_desc（默认 desc）
     */
    @SaCheckLogin
    @GetMapping("/storeProducts")
    public R<List<StoreProductVo>> storeProducts(@RequestParam String locationId,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String sort) {
        return R.ok(stockSelfService.listStoreProducts(locationId, keyword, sort));
    }

    /**
     * 白条库列表（按半只/半扇逐燎毛产出行，取数完全对齐 admin 分割白条领用；locationId 接收但不参与过滤，白条是逻辑库）。
     */
    @SaCheckLogin
    @GetMapping("/whiteBarStocks")
    public R<List<WhiteBarStockVo>> whiteBarStocks(@RequestParam(required = false) String locationId) {
        return R.ok(stockSelfService.listWhiteBarStocks(locationId));
    }

    /**
     * 待盘点产品列表（库存盘点 tab）。
     */
    @SaCheckLogin
    @GetMapping("/pendingChecks")
    public R<List<PendingCheckVo>> pendingChecks(@RequestParam String locationId,
                                                 @RequestParam(required = false) String keyword) {
        return R.ok(stockSelfService.listPendingChecks(locationId, keyword));
    }

    /**
     * 盘点记录分页（盘点记录 tab）。
     *
     * @param checkResult 盘点结果 normal / loss / abnormal（可空）
     */
    @SaCheckLogin
    @GetMapping("/checkRecords")
    public TableDataInfo<CheckRecordVo> checkRecords(@RequestParam String locationId,
                                                     @RequestParam(required = false) String checkDate,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String checkResult,
                                                     PageQuery pageQuery) {
        return stockSelfService.pageCheckRecords(locationId, checkDate, keyword, checkResult, pageQuery);
    }

    /**
     * 进出库流水分页（入库记录 / 出库记录 tab）。
     *
     * @param direction in 入库 / out 出库
     */
    @SaCheckLogin
    @GetMapping("/inoutFlows")
    public TableDataInfo<InoutFlowVo> inoutFlows(@RequestParam String direction,
                                                 @RequestParam String locationId,
                                                 @RequestParam(required = false) String startDate,
                                                 @RequestParam(required = false) String endDate,
                                                 @RequestParam(required = false) String keyword,
                                                 PageQuery pageQuery) {
        return stockSelfService.pageInoutFlows(direction, locationId, startDate, endDate, keyword, pageQuery);
    }

    /**
     * 产品入库 → 写 stock_flow（IN）+ 加库存。
     */
    @SaCheckLogin
    @PostMapping("/inbound")
    public R<Long> inbound(@Valid @RequestBody ProductInboundBo bo) {
        return R.ok(stockSelfService.inbound(bo));
    }

    /**
     * 产品出库 → 写 stock_flow（OT）+ 扣库存。
     */
    @SaCheckLogin
    @PostMapping("/outbound")
    public R<Long> outbound(@Valid @RequestBody ProductOutboundBo bo) {
        return R.ok(stockSelfService.outbound(bo));
    }

    /**
     * 盘点录入提交 → 写盘点流水留痕 + 回写库存。
     */
    @SaCheckLogin
    @PostMapping("/checkSubmit")
    public R<Long> checkSubmit(@Valid @RequestBody StockCheckEntryBo bo) {
        return R.ok(stockSelfService.checkSubmit(bo));
    }

}
