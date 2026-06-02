package org.dromara.djs.store.split.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.store.split.domain.bo.StoreSplitBo;
import org.dromara.djs.store.split.domain.query.StoreSplitQuery;
import org.dromara.djs.store.split.domain.vo.StoreSplitVo;
import org.dromara.djs.store.split.service.IStoreSplitService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门店白条分割 admin 端 Controller（STR-SPLIT-001，admin only 无 mp）。
 *
 * <p>店长把仓库已分割入冻品库的白条 / 部位品在门店端再分一次：单页 BizTable 列表 + 录入弹窗 + 导出三段式。
 * 复用 warehouse 域 {@code t_warehouse_product_inhouse} 表，固定 {@code source='store'} 隔离仓库分割行。
 * 权限串 {@code djs:store:split:{list,query,add,export}}（menu 10300-10306）。</p>
 *
 * @author djs
 * @since STR-SPLIT-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/store/split")
public class StoreSplitController extends BaseController {

    private final IStoreSplitService splitService;

    /**
     * 门店分割记录分页列表（固定 {@code source='store'}）。
     */
    @SaCheckPermission("djs:store:split:list")
    @GetMapping("/list")
    public TableDataInfo<StoreSplitVo> list(StoreSplitQuery query, PageQuery pageQuery) {
        return splitService.queryPage(query, pageQuery);
    }

    /**
     * 门店再分录入：选部位 + 录重量 + 库位 + 可选源白条 → INSERT inhouse {@code source='store'}。
     */
    @SaCheckPermission("djs:store:split:add")
    @Log(title = "门店白条分割", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@Valid @RequestBody StoreSplitBo bo) {
        return R.ok(splitService.addSplit(bo));
    }

    /**
     * 导出门店分割明细（不分页，固定 {@code source='store'}）。
     */
    @SaCheckPermission("djs:store:split:export")
    @Log(title = "门店白条分割", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(StoreSplitQuery query, HttpServletResponse response) {
        List<StoreSplitVo> list = splitService.queryList(query);
        ExcelUtil.exportExcel(list, "门店分割明细", StoreSplitVo.class, response);
    }

}
