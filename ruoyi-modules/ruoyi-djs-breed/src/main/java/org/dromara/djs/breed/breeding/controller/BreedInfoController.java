package org.dromara.djs.breed.breeding.controller;

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
import org.dromara.djs.breed.breeding.domain.bo.BreedInfoBo;
import org.dromara.djs.breed.breeding.domain.query.BreedInfoQuery;
import org.dromara.djs.breed.breeding.domain.vo.BreedInfoVo;
import org.dromara.djs.breed.breeding.service.IBreedInfoService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 育种信息（品种/品系）Controller（BRD-MD-001）。
 *
 * <p>权限串统一 {@code djs:breed:breeding:*}（与 BreedConfigController 共用，admin 4 tab 单页）。</p>
 *
 * @author djs
 * @since BRD-MD-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/breeding/info")
public class BreedInfoController extends BaseController {

    private final IBreedInfoService breedInfoService;

    /**
     * 分页查询品种/品系列表（admin 4 tab 按 breedStrain 过滤）。
     */
    @SaCheckPermission("djs:breed:breeding:list")
    @GetMapping("/list")
    public TableDataInfo<BreedInfoVo> list(BreedInfoQuery query, PageQuery pageQuery) {
        return breedInfoService.queryPageList(query, pageQuery);
    }

    /**
     * 导出。
     */
    @SaCheckPermission("djs:breed:breeding:export")
    @Log(title = "育种信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BreedInfoQuery query, HttpServletResponse response) {
        List<BreedInfoVo> list = breedInfoService.queryList(query);
        ExcelUtil.exportExcel(list, "育种信息", BreedInfoVo.class, response);
    }

    /**
     * 详情。
     */
    @SaCheckPermission("djs:breed:breeding:list")
    @GetMapping("/getInfo/{id}")
    public R<BreedInfoVo> getInfo(@PathVariable Long id) {
        return R.ok(breedInfoService.queryById(id));
    }

    /**
     * 新增。
     */
    @SaCheckPermission("djs:breed:breeding:add")
    @Log(title = "育种信息", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody BreedInfoBo bo) {
        return toAjax(breedInfoService.insertByBo(bo));
    }

    /**
     * 编辑。
     */
    @SaCheckPermission("djs:breed:breeding:edit")
    @Log(title = "育种信息", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/edit")
    public R<Void> edit(@Validated @RequestBody BreedInfoBo bo) {
        return toAjax(breedInfoService.updateByBo(bo));
    }

    /**
     * 软删（支持批量逗号）。
     */
    @SaCheckPermission("djs:breed:breeding:remove")
    @Log(title = "育种信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(breedInfoService.deleteWithValidByIds(Arrays.asList(ids)));
    }

}
