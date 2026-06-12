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
import org.dromara.djs.store.trace.domain.vo.TraceablePigVo;
import org.dromara.djs.store.trace.service.IStoreTraceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门店猪肉现场生码 Controller（STORE-TRACE-ONSITE-001，admin only）。
 *
 * <p>原型「猪肉追溯码管理」卡片式现场生码：选已出栏猪只 → 选零售部位 → 录重量 → 生码打印。
 * 追溯表归 warehouse、猪只表归 breed，本 Controller 走门店域写端点（ADR-0015），service 纯编排两域。</p>
 *
 * <p>权限串：picker / 生码走 {@code djs:store:trace:gen}（生码动作的取数前置）；打印走
 * {@code djs:store:trace:print}（前端 jsPDF 出码按钮门控，生码返回的 produce_code 直接打印，
 * 不另设取数端点）。</p>
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
     * 现场按需生码（猪只 + 部位 + 重量 → 生成 pork 追溯码，返回 produce_code 供前端打印）。
     */
    @SaCheckPermission("djs:store:trace:gen")
    @Log(title = "门店现场生码", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/gen")
    public R<String> gen(@Valid @RequestBody StoreTraceOnsiteBo bo) {
        return R.ok("生码成功", storeTraceService.genOnsiteCode(bo));
    }
}
