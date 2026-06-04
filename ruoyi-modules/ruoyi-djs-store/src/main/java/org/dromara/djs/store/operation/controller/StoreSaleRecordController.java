package org.dromara.djs.store.operation.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.core.ExcelResult;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.store.operation.domain.bo.StoreSaleRecordBo;
import org.dromara.djs.store.operation.domain.query.StoreSaleRecordQuery;
import org.dromara.djs.store.operation.domain.vo.StoreSaleImportVo;
import org.dromara.djs.store.operation.domain.vo.StoreSaleRecordVo;
import org.dromara.djs.store.operation.listener.StoreSaleImportListener;
import org.dromara.djs.store.operation.service.IStoreSaleRecordService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * 门店销售流水 Controller（STR-OP-001，admin 经营明细）。
 *
 * <p>权限串：{@code djs:storeSale:{list,query,add,remove,import}}（seed 在
 * {@code V202606141220__STR-OP-001-menu.sql}，menu_id 10120-10124）。</p>
 *
 * <p>数据来源：手录（{@code POST /add}，source='manual'）+ Excel 导入（{@code POST /importData}，
 * source='excel_import'）。V1 不做交易（无 POS / 微信支付）。</p>
 *
 * @author djs
 * @since STR-OP-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/store/sale")
public class StoreSaleRecordController extends BaseController {

    private final IStoreSaleRecordService saleRecordService;

    /**
     * 分页查询销售流水。
     */
    @SaCheckPermission("djs:storeSale:list")
    @GetMapping("/list")
    public TableDataInfo<StoreSaleRecordVo> list(StoreSaleRecordQuery query, PageQuery pageQuery) {
        return saleRecordService.queryPageList(query, pageQuery);
    }

    /**
     * 详情。
     */
    @SaCheckPermission("djs:storeSale:query")
    @GetMapping("/getInfo/{id}")
    public R<StoreSaleRecordVo> getInfo(@PathVariable Long id) {
        return R.ok(saleRecordService.queryById(id));
    }

    /**
     * 手录单条销售（source='manual'）。
     */
    @SaCheckPermission("djs:storeSale:add")
    @Log(title = "门店销售流水", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/add")
    public R<Long> add(@Validated @RequestBody StoreSaleRecordBo bo) {
        return R.ok(saleRecordService.addManual(bo));
    }

    /**
     * 批量软删。
     */
    @SaCheckPermission("djs:storeSale:remove")
    @Log(title = "门店销售流水", businessType = BusinessType.DELETE)
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(saleRecordService.deleteByIds(Arrays.asList(ids)));
    }

    /**
     * Excel 批量导入（source='excel_import'）；导入行须为目标门店已关联 SKU，校验失败返回错误统计。
     */
    @SaCheckPermission("djs:storeSale:import")
    @Log(title = "门店销售流水导入", businessType = BusinessType.IMPORT)
    @PostMapping(value = "/importData", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<String> importData(@RequestPart("file") MultipartFile file, @RequestParam Long storeId) throws Exception {
        ExcelResult<StoreSaleImportVo> result = ExcelUtil.importExcel(
            file.getInputStream(), StoreSaleImportVo.class, new StoreSaleImportListener(storeId));
        return R.ok(result.getAnalysis());
    }

    /**
     * 下载导入模板。
     */
    @SaCheckPermission("djs:storeSale:import")
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtil.exportExcel(new ArrayList<StoreSaleImportVo>(), "门店销售导入模板", StoreSaleImportVo.class, response);
    }
}
