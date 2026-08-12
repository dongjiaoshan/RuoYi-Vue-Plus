package org.dromara.djs.warehouse.thirdphase.controller;

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
import org.dromara.djs.warehouse.thirdphase.domain.bo.ThirdPhaseInBo;
import org.dromara.djs.warehouse.thirdphase.domain.query.ThirdPhaseInQuery;
import org.dromara.djs.warehouse.thirdphase.domain.vo.ThirdPhaseInVo;
import org.dromara.djs.warehouse.thirdphase.service.IThirdPhaseInService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 三期作物入库管理（V6 row88，admin「库存管理 → 三期作物入库管理」）。
 *
 * @author djs
 * @since V6-R88
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/warehouse/thirdPhaseIn")
public class ThirdPhaseInController extends BaseController {

    private final IThirdPhaseInService thirdPhaseInService;

    /**
     * 列表（日期区间 + 作物名模糊）。
     */
    @SaCheckPermission("djs:warehouse:thirdPhaseIn:list")
    @GetMapping("/list")
    public TableDataInfo<ThirdPhaseInVo> list(ThirdPhaseInQuery query, PageQuery pageQuery) {
        return thirdPhaseInService.queryPage(query, pageQuery);
    }

    /**
     * 新增入库：产品入库 + 记一条入库记录 + 给对应地块打【三期】标识（单事务）。
     */
    @SaCheckPermission("djs:warehouse:thirdPhaseIn:add")
    @Log(title = "仓库-三期作物入库", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Long> add(@Valid @RequestBody ThirdPhaseInBo bo) {
        return R.ok("入库成功", thirdPhaseInService.submit(bo));
    }

    /**
     * 导出：按当前筛选条件导全量，列与页面表格逐列一致（不含「操作」列）。
     */
    @SaCheckPermission("djs:warehouse:thirdPhaseIn:export")
    @Log(title = "仓库-三期作物入库管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(ThirdPhaseInQuery query, HttpServletResponse response) {
        List<ThirdPhaseInVo> list = thirdPhaseInService.queryList(query);
        ExcelUtil.exportExcel(list, "三期作物入库管理", ThirdPhaseInVo.class, response);
    }
}
