package org.dromara.djs.plant.farm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.farm.domain.query.FarmRecordsQuery;
import org.dromara.djs.plant.farm.domain.vo.FarmRecordsVo;
import org.dromara.djs.plant.farm.service.IFarmRecordsService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 农事记录 admin Controller（PLT-WORK-001 只读 + 导出）。
 *
 * <p>URL 前缀 {@code /djs/plant/farm}；菜单 8090 父；按钮权限沿用 PLT-WORK-002（D11）规划。
 * 本 ticket 只暴露 list / getInfo / export 3 个端点，admin 新增 / 编辑 / 删除 在 PLT-WORK-002 单独实施。</p>
 *
 * @author djs
 * @since PLT-WORK-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/plant/farm")
public class FarmRecordsController extends BaseController {

    private final IFarmRecordsService farmRecordsService;

    /** 分页查询（admin Tab 切换时 farm_type 单值过滤）。 */
    @SaCheckPermission("djs:plant:farm:list")
    @GetMapping("/list")
    public TableDataInfo<FarmRecordsVo> list(FarmRecordsQuery query, PageQuery pageQuery) {
        return farmRecordsService.queryPageList(query, pageQuery);
    }

    /** 导出。 */
    @SaCheckPermission("djs:plant:farm:export")
    @PostMapping("/export")
    public void export(FarmRecordsQuery query, HttpServletResponse response) {
        List<FarmRecordsVo> list = farmRecordsService.queryList(query);
        ExcelUtil.exportExcel(list, "农事记录", FarmRecordsVo.class, response);
    }

    /** 详情。 */
    @SaCheckPermission("djs:plant:farm:list")
    @GetMapping("/getInfo/{id}")
    public R<FarmRecordsVo> getInfo(@PathVariable Long id) {
        FarmRecordsVo vo = farmRecordsService.queryById(id);
        return R.ok(vo);
    }
}
