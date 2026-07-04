package org.dromara.djs.breed.med.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.med.domain.bo.MedUsageBo;
import org.dromara.djs.breed.med.domain.query.MedUsageQuery;
import org.dromara.djs.breed.med.domain.vo.MedUsageVo;
import org.dromara.djs.breed.med.service.IMedUsageService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 药品领用台账 Controller（BRD-MED-002）。
 *
 * <p>5 端点：list / getInfo / add / remove / today-stat；
 * 权限串 {@code djs:breed:med-usage:{list,add,remove}}（无 edit；领用台账只能新增/软删）。</p>
 *
 * @author djs
 * @since BRD-MED-002
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/med-usage")
public class MedUsageController extends BaseController {

    private final IMedUsageService medUsageService;

    /**
     * 分页查询。
     */
    @SaCheckPermission("djs:breed:med-usage:list")
    @GetMapping("/list")
    public TableDataInfo<MedUsageVo> list(MedUsageQuery query, PageQuery pageQuery) {
        return medUsageService.queryPageList(query, pageQuery);
    }

    /**
     * 导出。
     */
    @SaCheckPermission("djs:breed:med-usage:list")
    @Log(title = "药品领用", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(MedUsageQuery query, HttpServletResponse response) {
        List<MedUsageVo> list = medUsageService.queryList(query);
        ExcelUtil.exportExcel(list, "药品领用", MedUsageVo.class, response);
    }

    /**
     * 详情。
     */
    @SaCheckPermission("djs:breed:med-usage:list")
    @GetMapping("/getInfo/{id}")
    public R<MedUsageVo> getInfo(@PathVariable Long id) {
        return R.ok(medUsageService.queryById(id));
    }

    /**
     * 新增领用 / 退回 / 损耗（同步扣减或归还批次库存）。
     */
    @SaCheckPermission("djs:breed:med-usage:add")
    @Log(title = "药品领用", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody MedUsageBo bo) {
        return toAjax(medUsageService.insertByBo(bo));
    }

    /**
     * 软删（不回滚库存）。
     */
    @SaCheckPermission("djs:breed:med-usage:remove")
    @Log(title = "药品领用", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(medUsageService.deleteWithValidByIds(Arrays.asList(ids)));
    }

    /**
     * 今日汇总（小程序顶部卡片）。
     *
     * <p>返回 {@code {use, return, loss}} 三键的当日 usageQty 之和，缺省补 0。
     * {@code medicineId} 非空则按该药品分组（顶卡随选中药品显示自己的今日已领/退回/损耗）。</p>
     */
    @SaCheckPermission("djs:breed:med-usage:list")
    @GetMapping("/today-stat")
    public R<Map<String, BigDecimal>> todayStat(@RequestParam(required = false) Long medicineId) {
        return R.ok(medUsageService.todayStat(medicineId));
    }

}
