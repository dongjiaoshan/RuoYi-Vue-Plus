package org.dromara.djs.breed.med.record.controller;

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
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.breed.med.record.domain.bo.MedRecordBatchBo;
import org.dromara.djs.breed.med.record.domain.bo.MedRecordBo;
import org.dromara.djs.breed.med.record.domain.query.MedRecordQuery;
import org.dromara.djs.breed.med.record.domain.vo.MedRecordVo;
import org.dromara.djs.breed.med.record.domain.vo.UsableBatchVo;
import org.dromara.djs.breed.med.record.service.IMedRecordService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 用药治疗流水 Controller（BRD-MED-003）。
 *
 * <p>7 端点：</p>
 * <ul>
 *   <li>{@code GET    /list}            分页查询（admin + mp）</li>
 *   <li>{@code GET    /detail/master/{id}} 查批量 master 的所有 detail</li>
 *   <li>{@code GET    /getInfo/{id}}    详情</li>
 *   <li>{@code POST   /add}             单只用药</li>
 *   <li>{@code POST   /add-batch}       批量用药</li>
 *   <li>{@code GET    /usable-batch}    mp picker 数据源（3 天内已领可用批次）</li>
 *   <li>{@code POST   /export}          导出</li>
 *   <li>{@code DELETE /remove/{ids}}    软删（不回滚库存）</li>
 * </ul>
 *
 * @author djs
 * @since BRD-MED-003
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/breed/med-record")
public class MedRecordController extends BaseController {

    private final IMedRecordService medRecordService;

    @SaCheckPermission("djs:breed:med-record:list")
    @GetMapping("/list")
    public TableDataInfo<MedRecordVo> list(MedRecordQuery query, PageQuery pageQuery) {
        return medRecordService.queryPage(query, pageQuery);
    }

    @SaCheckPermission("djs:breed:med-record:list")
    @GetMapping("/getInfo/{id}")
    public R<MedRecordVo> getInfo(@PathVariable Long id) {
        return R.ok(medRecordService.queryById(id));
    }

    @SaCheckPermission("djs:breed:med-record:list")
    @GetMapping("/detail/master/{masterId}")
    public R<List<MedRecordVo>> detailByMaster(@PathVariable Long masterId) {
        return R.ok(medRecordService.queryByMasterId(masterId));
    }

    @SaCheckPermission("djs:breed:med-record:add")
    @Log(title = "用药治疗-单只", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Void> add(@Validated @RequestBody MedRecordBo bo) {
        return toAjax(medRecordService.addSingle(bo));
    }

    @SaCheckPermission("djs:breed:med-record:add")
    @Log(title = "用药治疗-批量", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add-batch")
    public R<Long> addBatch(@Validated @RequestBody MedRecordBatchBo bo) {
        return R.ok(medRecordService.addBatch(bo));
    }

    /**
     * mp picker：当前用户 3 天内已领 + 余量 > 0 的可用批次列表。
     *
     * <p>{@code mineOnly=true}（默认）按 LoginHelper.getUserId 过滤；
     * {@code false} 返全场（admin 端调）。</p>
     */
    @SaCheckPermission("djs:breed:med-record:list")
    @GetMapping("/usable-batch")
    public R<List<UsableBatchVo>> usableBatch(@RequestParam(defaultValue = "true") boolean mineOnly) {
        Long operatorId = mineOnly ? LoginHelper.getUserId() : null;
        return R.ok(medRecordService.listUsableBatches(operatorId));
    }

    @SaCheckPermission("djs:breed:med-record:export")
    @Log(title = "用药治疗", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(MedRecordQuery query, HttpServletResponse response) {
        List<MedRecordVo> list = medRecordService.queryList(query);
        ExcelUtil.exportExcel(list, "用药治疗", MedRecordVo.class, response);
    }

    @SaCheckPermission("djs:breed:med-record:remove")
    @Log(title = "用药治疗", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(medRecordService.deleteByIds(Arrays.asList(ids)));
    }

}
