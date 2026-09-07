package org.dromara.djs.warehouse.flow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.flow.domain.query.InoutStatDetailQuery;
import org.dromara.djs.warehouse.flow.domain.query.InoutStatQuery;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatInDetailVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatInVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatOutDetailVo;
import org.dromara.djs.warehouse.flow.domain.vo.InoutStatOutVo;
import org.dromara.djs.warehouse.flow.service.IInoutStatService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 出入库统计 Controller（V6-R167）。
 *
 * <p>页面是两个 Tab：入库统计（产品 × 入库方式 × 供应商）/ 出库统计（产品 × 出库去向），
 * 各自「列表 + 导出」两个端点。compute-on-read 按日期区间 GROUP BY 既有出入库流水，
 * 无汇总表、无跑批（与兄弟页「出入库月汇总」同一套聚合口径）。</p>
 *
 * <p>日期区间放开后行数可能上万，两个列表<b>都分页</b>；导出走同一份 SQL 的全量方法，
 * 保证「导出的表 = 页面翻完所有页」。</p>
 *
 * <p>行内「查看详情」（V6-R186）各 Tab 再加「明细分页 + 明细导出」两个端点，走与汇总
 * <b>同一份</b> FROM / WHERE（见 {@code InoutStatMapper} 的片段常量），
 * 于是「明细逐条求和 = 汇总行的量」是构造上成立的。</p>
 *
 * <p>权限沿用本页已有的两个（不新建菜单按钮）：看列表 / 看明细都是
 * {@code djs:warehouse:inoutStat:list}，导出汇总 / 导出明细都是
 * {@code djs:warehouse:inoutStat:export}（两个 Tab 共用），
 * 甲方要的是「两个 Tab 都能导出」而不是「分开授权」。</p>
 *
 * <p>🔴 两个导出端点的参数是<b>命令对象</b> {@link InoutStatQuery}，不是 {@code @RequestParam}：
 * 前端 {@code proxy.download} 把数组序列化成 {@code flowTypes[0]=a} 索引形式，
 * 只有 WebDataBinder 命令对象绑定收得到，否则多选筛选在导出里静默失效。</p>
 *
 * @author djs
 * @since V6-R167
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/inoutStat")
public class InoutStatController extends BaseController {

    private final IInoutStatService inoutStatService;

    /**
     * 入库统计分页（甲方 row167 第 3 点）。
     *
     * @param query     筛选：日期区间 / 产品名称模糊 / 入库方式多选 / 产品类型多选 / 供应商
     * @param pageQuery 分页参数
     */
    @SaCheckPermission("djs:warehouse:inoutStat:list")
    @GetMapping("/in/list")
    public TableDataInfo<InoutStatInVo> inList(InoutStatQuery query, PageQuery pageQuery) {
        return inoutStatService.queryInPage(query, pageQuery);
    }

    /**
     * 入库统计导出（甲方 row167 第 5 点：导出内容与列表一致）。
     */
    @SaCheckPermission("djs:warehouse:inoutStat:export")
    @Log(title = "入库统计", businessType = BusinessType.EXPORT)
    @PostMapping("/in/export")
    public void inExport(InoutStatQuery query, HttpServletResponse response) {
        List<InoutStatInVo> list = inoutStatService.queryInList(query);
        ExcelUtil.exportExcel(list, "入库统计", InoutStatInVo.class, response);
    }

    /**
     * 出库统计分页（甲方 row167 第 4 点）。
     *
     * @param query     筛选：日期区间 / 产品名称模糊 / 出库去向多选 / 产品类型多选
     * @param pageQuery 分页参数
     */
    @SaCheckPermission("djs:warehouse:inoutStat:list")
    @GetMapping("/out/list")
    public TableDataInfo<InoutStatOutVo> outList(InoutStatQuery query, PageQuery pageQuery) {
        return inoutStatService.queryOutPage(query, pageQuery);
    }

    /**
     * 出库统计导出（甲方 row167 第 5 点）。
     */
    @SaCheckPermission("djs:warehouse:inoutStat:export")
    @Log(title = "出库统计", businessType = BusinessType.EXPORT)
    @PostMapping("/out/export")
    public void outExport(InoutStatQuery query, HttpServletResponse response) {
        List<InoutStatOutVo> list = inoutStatService.queryOutList(query);
        ExcelUtil.exportExcel(list, "出库统计", InoutStatOutVo.class, response);
    }

    /**
     * 入库统计行「查看详情」分页（甲方 row186 第 2 点）。
     *
     * @param query     分组键（产品编码 / 入库方式 / 供应商，前端从被点击的汇总行原样带）
     *                  + 列表当前筛选 + 弹窗自己的入库日期区间与入库记录人
     * @param pageQuery 分页参数
     */
    @SaCheckPermission("djs:warehouse:inoutStat:list")
    @GetMapping("/in/detail/list")
    public TableDataInfo<InoutStatInDetailVo> inDetailList(@Validated InoutStatDetailQuery query, PageQuery pageQuery) {
        return inoutStatService.queryInDetailPage(query, pageQuery);
    }

    /**
     * 入库明细导出（甲方 row186 第 4 点：出入库详情都支持导出）。
     */
    @SaCheckPermission("djs:warehouse:inoutStat:export")
    @Log(title = "入库统计明细", businessType = BusinessType.EXPORT)
    @PostMapping("/in/detail/export")
    public void inDetailExport(@Validated InoutStatDetailQuery query, HttpServletResponse response) {
        List<InoutStatInDetailVo> list = inoutStatService.queryInDetailList(query);
        ExcelUtil.exportExcel(list, "入库统计明细", InoutStatInDetailVo.class, response);
    }

    /**
     * 出库统计行「查看详情」分页（甲方 row186 第 3 点）。
     *
     * @param query     分组键（产品编码 / 出库去向）+ 列表当前筛选 + 弹窗自己的出库日期区间与出库记录人
     * @param pageQuery 分页参数
     */
    @SaCheckPermission("djs:warehouse:inoutStat:list")
    @GetMapping("/out/detail/list")
    public TableDataInfo<InoutStatOutDetailVo> outDetailList(@Validated InoutStatDetailQuery query, PageQuery pageQuery) {
        return inoutStatService.queryOutDetailPage(query, pageQuery);
    }

    /**
     * 出库明细导出（甲方 row186 第 4 点）。
     */
    @SaCheckPermission("djs:warehouse:inoutStat:export")
    @Log(title = "出库统计明细", businessType = BusinessType.EXPORT)
    @PostMapping("/out/detail/export")
    public void outDetailExport(@Validated InoutStatDetailQuery query, HttpServletResponse response) {
        List<InoutStatOutDetailVo> list = inoutStatService.queryOutDetailList(query);
        ExcelUtil.exportExcel(list, "出库统计明细", InoutStatOutDetailVo.class, response);
    }
}
