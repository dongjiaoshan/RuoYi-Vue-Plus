package org.dromara.djs.store.trace.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.store.trace.domain.bo.StoreTraceOnsiteBo;
import org.dromara.djs.store.trace.domain.vo.StoreOnsiteCodeVo;
import org.dromara.djs.store.trace.domain.vo.StorePackProductVo;
import org.dromara.djs.store.trace.domain.vo.TraceablePigVo;
import org.dromara.djs.store.trace.service.IStoreTraceService;
import org.dromara.djs.warehouse.trace.domain.query.TraceCodeQuery;
import org.dromara.djs.warehouse.trace.domain.vo.TraceCodeDetailVo;
import org.dromara.djs.warehouse.trace.domain.vo.TraceCodeListVo;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门店猪肉现场生码 Controller（STORE-TRACE-ONSITE-001，admin only）。
 *
 * <p>原型「猪肉追溯码管理」卡片式现场生码：选已出栏猪只 → 选零售部位 → 录重量 → 生码打印。
 * 追溯表归 warehouse、猪只表归 breed，本 Controller 走门店域写端点（ADR-0015），service 纯编排两域。</p>
 *
 * <p>已生成猪肉追溯码管理（ADMIN-STORE 91-1，对齐果蔬追溯码管理范式）：{@code /code/list} 已生码列表 /
 * {@code /code/{id}} 详情 / {@code /code/batch} 补打取数，全部只读，恒 {@code codeType=pork}，委托仓库追溯
 * 只读 service（追溯表归 warehouse，门店侧不反向写）。</p>
 *
 * <p>权限串：picker / 生码 / 已生码列表 / 详情走 {@code djs:store:trace:gen}；打印 / 补打走
 * {@code djs:store:trace:print}（前端 jsPDF 出码按钮门控）。</p>
 *
 * @author djs
 * @since STORE-TRACE-ONSITE-001
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/store/trace")
public class StoreTraceController extends BaseController {

    private final IStoreTraceService storeTraceService;

    /**
     * 可追溯猪只 picker 分页列表（已出栏育肥猪：耳号 / 性别 / 品种 / 日龄）。
     */
    @SaCheckPermission("djs:store:trace:gen")
    @GetMapping("/pig/list")
    public TableDataInfo<TraceablePigVo> pigList(PageQuery pageQuery) {
        return storeTraceService.listTraceablePigs(pageQuery);
    }

    /**
     * 门店猪肉打包可选产品列表（docx 取数：workshop=门店打包间(5) 且 product_material ∈ 字典
     * {@code djs_pork_return_product}）。前端 PorkTracePanel 产品卡数据源；空则前端回退部位字典。
     */
    @SaCheckPermission("djs:store:trace:gen")
    @GetMapping("/products")
    public R<List<StorePackProductVo>> packProducts() {
        return R.ok(storeTraceService.listPackProducts());
    }

    /**
     * 现场按需生码（猪只 + 部位/产品 + 重量 → 生成 pork 追溯码 + 门店生产编码，供前端打印）。
     *
     * <p>返回 {@link StoreOnsiteCodeVo}：{@code produceCode} 追溯码（二维码用）+ {@code productionCode}
     * 门店生产编码（{@code <生产标识码>YYMMDD####}，标签「生产编码」文本展示，row84）。</p>
     */
    @SaCheckPermission("djs:store:trace:gen")
    @Log(title = "门店现场生码", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/gen")
    public R<StoreOnsiteCodeVo> gen(@Valid @RequestBody StoreTraceOnsiteBo bo) {
        return R.ok("生码成功", storeTraceService.genOnsiteCode(bo));
    }

    /**
     * 已生成猪肉追溯码分页列表（ADMIN-STORE 91-1，恒 codeType=pork）。
     *
     * <p>对齐果蔬追溯码管理列表范式（只读）。委托仓库追溯只读 service，门店侧不反向写 trace 表。</p>
     */
    @SaCheckPermission("djs:store:trace:gen")
    @GetMapping("/code/list")
    public TableDataInfo<TraceCodeListVo> codeList(TraceCodeQuery query, PageQuery pageQuery) {
        return storeTraceService.listPorkTrace(query, pageQuery);
    }

    /**
     * 已生成猪肉追溯码详情（主表 + 关联 + 完整事件时间轴，只读）。
     */
    @SaCheckPermission("djs:store:trace:gen")
    @GetMapping("/code/{id}")
    public R<TraceCodeDetailVo> codeDetail(@PathVariable Long id) {
        return R.ok(storeTraceService.getPorkTraceDetail(id));
    }

    /**
     * 补打取数：按勾选 ID 批量拉详情（含事件链），供前端 jsPDF 批量补打追溯码。
     *
     * <p>POST 仅因入参 id 列表体大，非数据写入（追溯链不可篡改，门店侧纯只读）。</p>
     */
    @SaCheckPermission("djs:store:trace:print")
    @PostMapping("/code/batch")
    public R<List<TraceCodeDetailVo>> codeBatch(@RequestBody List<Long> ids) {
        return R.ok(storeTraceService.batchPorkTraceDetail(ids));
    }
}
