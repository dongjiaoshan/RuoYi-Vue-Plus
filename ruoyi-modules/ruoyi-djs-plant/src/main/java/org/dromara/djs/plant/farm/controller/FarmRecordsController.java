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

    // ============================================================
    // PLT-WORK-003 灾害记录独立子页（admin PC 只查，复用 t_plant_farm_records 灾害子集）
    //
    // 录入入口在 mp PLT-WORK-001 disaster 子页；admin 端不暴露 add/edit/remove。
    // 强制 farmType='disaster' 收口，与 PLT-WORK-002 的 5 Tab farmWorkTypes 多选隔离
    // （清空 farmWorkTypes 避免 IN 覆盖单值 eq）。enrich / 字典翻译全复用 list 路径。
    // ============================================================

    /** 灾害记录分页查询（强制 farm_type=disaster）。 */
    @SaCheckPermission("djs:plant:farm:disaster:list")
    @GetMapping("/disaster/list")
    public TableDataInfo<FarmRecordsVo> disasterList(FarmRecordsQuery query, PageQuery pageQuery) {
        forceDisaster(query);
        return farmRecordsService.queryPageList(query, pageQuery);
    }

    /** 灾害记录导出（强制 farm_type=disaster）。 */
    @SaCheckPermission("djs:plant:farm:disaster:export")
    @PostMapping("/disaster/export")
    public void disasterExport(FarmRecordsQuery query, HttpServletResponse response) {
        forceDisaster(query);
        List<FarmRecordsVo> list = farmRecordsService.queryList(query);
        ExcelUtil.exportExcel(list, "灾害记录", FarmRecordsVo.class, response);
    }

    /** 灾害记录详情。 */
    @SaCheckPermission("djs:plant:farm:disaster:list")
    @GetMapping("/disaster/getInfo/{id}")
    public R<FarmRecordsVo> disasterGetInfo(@PathVariable Long id) {
        FarmRecordsVo vo = farmRecordsService.queryById(id);
        return R.ok(vo);
    }

    /** 强制把查询收口到灾害类型：置 farmType=disaster + 清空 PLT-WORK-002 的多选 farmWorkTypes。 */
    private void forceDisaster(FarmRecordsQuery query) {
        query.setFarmType("disaster");
        query.setFarmWorkTypes(null);
    }
}
