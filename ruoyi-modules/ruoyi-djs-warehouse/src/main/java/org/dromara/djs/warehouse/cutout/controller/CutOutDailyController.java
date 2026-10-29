package org.dromara.djs.warehouse.cutout.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.date.DateUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.cutout.domain.query.CutOutDailyDetailQuery;
import org.dromara.djs.warehouse.cutout.domain.query.CutOutDailyQuery;
import org.dromara.djs.warehouse.cutout.domain.vo.CutOutDailyDetailVo;
import org.dromara.djs.warehouse.cutout.domain.vo.CutOutDailyVo;
import org.dromara.djs.warehouse.cutout.service.ICutOutDailyService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 猪肉日分割出库统计 Controller（V7-R6，挂【库存管理】）。
 *
 * <p>列表 = 统计日期 × 出库去向 / 出库门店 的出库总重量；行内「查看」打开右侧抽屉看逐条明细并可导出。
 * 统计范围：白条分割出库管理的「仓库出库」（按出库去向）与「门店需求」、白条发门店（按门店）。</p>
 *
 * <p>明细导出与明细列表同源同参（同一个 service 方法），导出的表 = 抽屉里的表。
 * 参数都是命令对象（WebDataBinder 绑定）：前端 {@code proxy.download} 走表单参数，
 * {@code @RequestBody} 收不到。</p>
 *
 * @author djs
 * @since V7-R6
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/djs/warehouse/cutOutDaily")
public class CutOutDailyController extends BaseController {

    private final ICutOutDailyService cutOutDailyService;

    /**
     * 列表分页（日期倒序）。
     *
     * @param query     筛选：统计日期区间 beginDate / endDate、出库去向 outDest（字典值或 store_demand）、出库门店 storeId
     * @param pageQuery 分页参数
     */
    @SaCheckPermission("djs:warehouse:cutOutDaily:list")
    @GetMapping("/list")
    public TableDataInfo<CutOutDailyVo> list(CutOutDailyQuery query, PageQuery pageQuery) {
        return cutOutDailyService.queryPage(query, pageQuery);
    }

    /**
     * 「查看」明细：产品编码 / 产品名称 / 出库量 / 单位 / 猪只耳号 / 出库时间。
     *
     * @param query 被点击那一行的 statDate + destType，再加 outDest（仓库出库）或 storeId（门店需求）
     */
    @SaCheckPermission("djs:warehouse:cutOutDaily:query")
    @GetMapping("/detail")
    public R<List<CutOutDailyDetailVo>> detail(@Validated CutOutDailyDetailQuery query) {
        return R.ok(cutOutDailyService.queryDetail(query));
    }

    /**
     * 明细导出（与明细列表同源同参）。
     */
    @SaCheckPermission("djs:warehouse:cutOutDaily:export")
    @Log(title = "猪肉日分割出库统计明细", businessType = BusinessType.EXPORT)
    @PostMapping("/detail/export")
    public void detailExport(@Validated CutOutDailyDetailQuery query, HttpServletResponse response) {
        List<CutOutDailyDetailVo> list = cutOutDailyService.queryDetail(query);
        ExcelUtil.exportExcel(list, "猪肉日分割出库明细_" + DateUtil.formatDate(query.getStatDate()),
            CutOutDailyDetailVo.class, response);
    }
}
