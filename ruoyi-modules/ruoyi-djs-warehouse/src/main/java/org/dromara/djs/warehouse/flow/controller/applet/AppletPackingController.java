package org.dromara.djs.warehouse.flow.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.flow.domain.bo.PackCheckBo;
import org.dromara.djs.warehouse.flow.domain.bo.PackInBo;
import org.dromara.djs.warehouse.flow.domain.bo.PackOutBo;
import org.dromara.djs.warehouse.flow.domain.query.StockFlowQuery;
import org.dromara.djs.warehouse.flow.domain.vo.PackingHomeVo;
import org.dromara.djs.warehouse.flow.domain.vo.PackingItemVo;
import org.dromara.djs.warehouse.flow.domain.vo.StockFlowVo;
import org.dromara.djs.warehouse.flow.service.IPackingFlowService;
import org.dromara.djs.warehouse.flow.service.IStockFlowService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 包材库管理 mp 端 Controller（D11 WMS-FLOW-001 查询 + FIX-WMS-MP-MATISSUE-001 per 项 4 动作写端点）。
 *
 * <p>查询端点（应用 path 范式 {@code /applet/warehouse/packing/*}）：</p>
 * <ul>
 *   <li>{@code GET /applet/warehouse/packing/home} 今日总览 KPI</li>
 *   <li>{@code GET /applet/warehouse/packing/list?sortBy=stock|name} 包材列表（belong_type='package' 强制 eq）</li>
 *   <li>{@code GET /applet/warehouse/packing/detail/{productId}} 包材详情（该产品流水分页）</li>
 *   <li>{@code GET /applet/warehouse/packing/records/{productId}?dateFrom=&dateTo=} 进出库记录
 *       （按 productId + 可选日期区间分页，原型图54「进出库记录」动作）</li>
 * </ul>
 *
 * <p>写端点（FIX-WMS-MP-MATISSUE-001，原型图54 per 项 4 动作落地，同事务写流水 + 改库存）：</p>
 * <ul>
 *   <li>{@code POST /applet/warehouse/packing/in}    产品入库（purchase_in/IN）</li>
 *   <li>{@code POST /applet/warehouse/packing/out}   产品出库（pick_out/OT，行锁扣减）</li>
 *   <li>{@code POST /applet/warehouse/packing/check} 库存盘点（差异写 check_in/check_out + 校准库存）</li>
 * </ul>
 *
 * <p>{@code @SaCheckLogin} 走 sa-token 真路（mp V1 mock 登录已发真 token，ADR-0003）。</p>
 *
 * @author djs
 * @since WMS-FLOW-001（FIX-WMS-MP-MATISSUE-001 扩写端点）
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/warehouse/packing")
public class AppletPackingController extends BaseController {

    private final IStockFlowService stockFlowService;
    private final IPackingFlowService packingFlowService;

    @SaCheckLogin
    @GetMapping("/home")
    public R<PackingHomeVo> home() {
        return R.ok(stockFlowService.queryPackingHome());
    }

    @SaCheckLogin
    @GetMapping("/list")
    public R<List<PackingItemVo>> list(@RequestParam(required = false) String sortBy) {
        return R.ok(stockFlowService.queryPackingList(sortBy));
    }

    @SaCheckLogin
    @GetMapping("/detail/{productId}")
    public TableDataInfo<StockFlowVo> detail(@NotNull @PathVariable Long productId, PageQuery pageQuery) {
        return stockFlowService.queryPackingDetail(productId, pageQuery);
    }

    /**
     * 进出库记录（原型图54「进出库记录」动作）：按 productId + 可选业务日期区间分页。
     *
     * <p>复用 {@link IStockFlowService#queryPageList}（只读），强制 productId 过滤，
     * {@code dateFrom/dateTo} 透传给 StockFlowQuery 做时间区间筛选。</p>
     */
    @SaCheckLogin
    @GetMapping("/records/{productId}")
    public TableDataInfo<StockFlowVo> records(@NotNull @PathVariable Long productId,
                                              StockFlowQuery query, PageQuery pageQuery) {
        if (query == null) {
            query = new StockFlowQuery();
        }
        query.setProductId(productId);
        return stockFlowService.queryPageList(query, pageQuery);
    }

    @SaCheckLogin
    @PostMapping("/in")
    public R<Long> packIn(@Valid @RequestBody PackInBo bo) {
        return R.ok(packingFlowService.packIn(bo));
    }

    @SaCheckLogin
    @PostMapping("/out")
    public R<Long> packOut(@Valid @RequestBody PackOutBo bo) {
        return R.ok(packingFlowService.packOut(bo));
    }

    @SaCheckLogin
    @PostMapping("/check")
    public R<Long> packCheck(@Valid @RequestBody PackCheckBo bo) {
        return R.ok(packingFlowService.packCheck(bo));
    }

}
