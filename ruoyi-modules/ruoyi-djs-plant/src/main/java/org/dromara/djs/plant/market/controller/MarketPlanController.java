package org.dromara.djs.plant.market.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.plant.market.domain.query.MarketPlanQuery;
import org.dromara.djs.plant.market.domain.vo.MarketPlanVo;
import org.dromara.djs.plant.market.service.IMarketPlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 果蔬上市计划 Controller（V6-R151，运营管理 → 农场信息）。
 *
 * <p>纯只读：一行 = 一条种植计划，上市 / 下架日期由该计划的采摘明细「实际优先、计划兜底」后 MIN/MAX 聚合而来。</p>
 * <ul>
 *   <li>GET  /list   分页列表（按上市日期降序，空上市日期排最后）</li>
 *   <li>POST /export 按同一筛选条件导出；列与列表一致，只少一个「作物图片」列（V6-R157 甲方点名去掉）</li>
 * </ul>
 *
 * <p>数据归属种植域，故实现放 {@code ruoyi-djs-plant}；URL 与权限串走运营口径
 * {@code /djs/ops/marketPlan} + {@code djs:ops:marketPlan:*}，与菜单 12020-12022 对齐。</p>
 *
 * @author djs
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/ops/marketPlan")
public class MarketPlanController extends BaseController {

    private final IMarketPlanService marketPlanService;

    /**
     * 分页查询果蔬上市计划。
     *
     * @param query     作物名称模糊 / 上市月份 / 下架月份（均可空）
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @SaCheckPermission("djs:ops:marketPlan:list")
    @GetMapping("/list")
    public TableDataInfo<MarketPlanVo> list(MarketPlanQuery query, PageQuery pageQuery) {
        return marketPlanService.queryPageList(query, pageQuery);
    }

    /**
     * 导出果蔬上市计划（FastExcel，列与列表一致，不含作物图片列）。
     *
     * @param query    与列表相同的筛选条件
     * @param response 响应
     */
    @SaCheckPermission("djs:ops:marketPlan:export")
    @Log(title = "运营-果蔬上市计划", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(MarketPlanQuery query, HttpServletResponse response) {
        List<MarketPlanVo> list = marketPlanService.queryList(query);
        ExcelUtil.exportExcel(list, "果蔬上市计划", MarketPlanVo.class, response);
    }
}
