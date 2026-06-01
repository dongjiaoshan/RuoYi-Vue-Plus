package org.dromara.djs.warehouse.flow.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.flow.domain.vo.PackingHomeVo;
import org.dromara.djs.warehouse.flow.domain.vo.PackingItemVo;
import org.dromara.djs.warehouse.flow.domain.vo.StockFlowVo;
import org.dromara.djs.warehouse.flow.service.IStockFlowService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 包材库管理 mp 端 Controller（D11 WMS-FLOW-001，仅查询）。
 *
 * <p>3 端点（applet path 范式与 5/7 主流一致 {@code /applet/warehouse/packing/*}）：</p>
 * <ul>
 *   <li>{@code GET /applet/warehouse/packing/home} 今日总览 KPI
 *       （今日入库件数 / 今日领用件数 / 包材种类数 / 最近盘点日期）</li>
 *   <li>{@code GET /applet/warehouse/packing/list?sortBy=stock|name} 包材列表
 *       （belong_type='package' 后端强制 eq，每行：包材名 + 当前库存 + 上次盘点日期）</li>
 *   <li>{@code GET /applet/warehouse/packing/detail/{productId}} 包材详情
 *       （该产品流水分页，按 product_id 过滤）</li>
 * </ul>
 *
 * <p>写端点（入库 / 领用 / 退回）V1.x 落地，本 ticket 仅查询。{@code @SaCheckLogin}
 * 走 sa-token 真路（mp V1 mock 登录已发真 token，ADR-0003）。</p>
 *
 * @author djs
 * @since WMS-FLOW-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/applet/warehouse/packing")
public class AppletPackingController extends BaseController {

    private final IStockFlowService stockFlowService;

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

}
