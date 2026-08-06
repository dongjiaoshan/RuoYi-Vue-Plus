package org.dromara.djs.warehouse.vegout.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.vegout.domain.bo.VegOutSubmitBo;
import org.dromara.djs.warehouse.vegout.domain.query.VegOutQuery;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutBatchVo;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutCandidateVo;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutDetailVo;
import org.dromara.djs.warehouse.vegout.service.IVegOutService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 毛菜间出库（admin row185 单条内部处理 + row187 批量出库管理）。
 *
 * @author djs
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/warehouse/veg-out")
public class VegOutController extends BaseController {

    private final IVegOutService vegOutService;

    /**
     * 新增抽屉左侧可选产品：毛菜鲜品库里库存 &gt; 0 的果蔬行（含库存重量与地块编号）。
     */
    @SaCheckPermission("djs:warehouse:vegOut:query")
    @GetMapping("/candidates")
    public R<List<VegOutCandidateVo>> candidates(@RequestParam(required = false) String productName) {
        return R.ok(vegOutService.listCandidates(productName));
    }

    /**
     * row187 批量出库：多产品一单，生成出库单号进列表。
     */
    @SaCheckPermission("djs:warehouse:vegOut:add")
    @Log(title = "仓库-毛菜间出库", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/batch")
    public R<String> batchOut(@Valid @RequestBody VegOutSubmitBo bo) {
        return R.ok("出库成功", vegOutService.submit(bo, true));
    }

    /**
     * row185 单条内部处理：从库存查询行发起，去向限「果蔬月台 / 饲料饲喂」。
     * 不生成出库单号（不进 row187 列表），只落出库记录与对应下游台账。
     */
    @SaCheckPermission("djs:warehouse:stock:out")
    @Log(title = "仓库-产品内部处理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/internal-handle")
    public R<Void> internalHandle(@Valid @RequestBody VegOutSubmitBo bo) {
        vegOutService.submit(bo, false);
        return R.ok();
    }

    /**
     * row187 出库单列表（按出库单号聚合：品类数 / 总重 / 操作人）。
     */
    @SaCheckPermission("djs:warehouse:vegOut:list")
    @GetMapping("/list")
    public TableDataInfo<VegOutBatchVo> list(VegOutQuery query, PageQuery pageQuery) {
        return vegOutService.queryBatchPage(query, pageQuery);
    }

    /**
     * V6 row31 出库单列表导出：按当前筛选条件（出库日期区间 / 出库去向 / 操作人）导出全量，不分页。
     *
     * <p>列与页面表格逐列一致（不含「操作」列）：出库单号 / 出库日期 / 出库去向 / 出库果蔬品类数 /
     * 出库果蔬重量(kg) / 出库金额(元) / 出库操作人。</p>
     *
     * @param query    查询条件
     * @param response HTTP 响应
     */
    @SaCheckPermission("djs:warehouse:vegOut:export")
    @Log(title = "仓库-毛菜间出库管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(VegOutQuery query, HttpServletResponse response) {
        List<VegOutBatchVo> list = vegOutService.queryBatchList(query);
        ExcelUtil.exportExcel(list, "毛菜间出库管理", VegOutBatchVo.class, response);
    }

    /**
     * row187 出库单明细（详情弹框，可按产品名筛）。
     */
    @SaCheckPermission("djs:warehouse:vegOut:query")
    @GetMapping("/detail")
    public R<List<VegOutDetailVo>> detail(@RequestParam String batchNo,
                                          @RequestParam(required = false) String productName) {
        return R.ok(vegOutService.queryBatchDetail(batchNo, productName));
    }

    /**
     * V6 row30 出库明细导出：导出这张出库单的产品明细，尊重弹框内的产品名筛选。
     *
     * <p>列与详情弹框逐列一致：产品名称 / 规格 / 出库量 / 出库单价(元) / 出库总价(元)。</p>
     *
     * @param batchNo     出库单号
     * @param productName 产品名称（模糊，可空）
     * @param response    HTTP 响应
     */
    @SaCheckPermission("djs:warehouse:vegOut:export")
    @Log(title = "仓库-毛菜间出库明细", businessType = BusinessType.EXPORT)
    @PostMapping("/detail/export")
    public void exportDetail(@RequestParam String batchNo,
                             @RequestParam(required = false) String productName,
                             HttpServletResponse response) {
        List<VegOutDetailVo> list = vegOutService.queryBatchDetailForExport(batchNo, productName);
        ExcelUtil.exportExcel(list, "出库明细_" + batchNo, VegOutDetailVo.class, response);
    }

}
