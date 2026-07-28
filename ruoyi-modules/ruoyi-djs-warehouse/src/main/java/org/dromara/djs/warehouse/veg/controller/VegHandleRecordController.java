package org.dromara.djs.warehouse.veg.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.veg.domain.query.VegHandleRecordQuery;
import org.dromara.djs.warehouse.veg.domain.vo.VegHandleRecordVo;
import org.dromara.djs.warehouse.veg.service.IVegHandleRecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 毛菜间处理记录 Controller（FIX-ADMIN-R130，仓库-admin 行130「毛菜间处理记录」只读菜单）。
 *
 * <p>挂「仓库-库存管理」9302 下、排在「有机饲喂记录」之后，仅查看无操作（不发 add/edit/del 权限）。
 * 列表 = 毛菜处理间处理流水 + 毛菜处理结算损耗 + 采摘活动流水三支 UNION。权限串
 * {@code djs:warehouse:vegHandleRecord:list}（菜单 perms 通配 {@code djs:warehouse:vegHandleRecord:*}，
 * Sa-Token vagueMatch 命中）。</p>
 *
 * @author djs
 * @since FIX-ADMIN-R130
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/warehouse/vegHandleRecord")
public class VegHandleRecordController extends BaseController {

    private final IVegHandleRecordService vegHandleRecordService;

    /**
     * 毛菜间处理记录分页列表：处理日期 / 作物名称 / 统计来源 / 处理方式 / 地块编号 / 处理量 / 记录人。
     *
     * <p>搜索：处理日期范围（dateFrom/dateTo）+ 作物名称模糊（cropName）+ 统计来源（statSource，1 毛菜处理间 /
     * 2 采摘活动）+ 处理方式（handleMethod，字典 djs_pick_dest）+ 地块编号模糊（plotCode）+ 记录人模糊（recorderName）。</p>
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     */
    @SaCheckPermission("djs:warehouse:vegHandleRecord:list")
    @GetMapping("/list")
    public TableDataInfo<VegHandleRecordVo> list(VegHandleRecordQuery query, PageQuery pageQuery) {
        return vegHandleRecordService.queryPage(query, pageQuery);
    }

    /**
     * 毛菜间处理记录导出：按当前搜索条件导出全量。
     *
     * @param query    查询条件
     * @param response HTTP 响应
     */
    @SaCheckPermission("djs:warehouse:vegHandleRecord:list")
    @Log(title = "毛菜间处理记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(VegHandleRecordQuery query, HttpServletResponse response) {
        List<VegHandleRecordVo> list = vegHandleRecordService.queryList(query);
        ExcelUtil.exportExcel(list, "毛菜间处理记录", VegHandleRecordVo.class, response);
    }
}
