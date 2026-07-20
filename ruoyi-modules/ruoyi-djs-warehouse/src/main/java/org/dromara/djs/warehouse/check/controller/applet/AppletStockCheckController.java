package org.dromara.djs.warehouse.check.controller.applet;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.check.domain.bo.StockCheckLineBo;
import org.dromara.djs.warehouse.check.domain.query.StockCheckQuery;
import org.dromara.djs.warehouse.check.domain.vo.StockCheckHeaderVo;
import org.dromara.djs.warehouse.check.domain.vo.StockCheckRecordVo;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库存盘点 mp 端 Controller（WMS-STOCK-001）。
 *
 * <p>4 端点（path 前缀 {@code /applet/warehouse/check}，前端 http baseURL 已含 /djs）：</p>
 * <ul>
 *   <li>{@code GET  /overview}：多库一览——进行中盘点单列表（mp 前端按 8 静态库类卡片分组展示）</li>
 *   <li>{@code GET  /lines?checkId=}：某盘点单已录入的实盘 line 列表</li>
 *   <li>{@code POST /submit}：提交 / 修改实盘 line（自动算 sysStock + diffStock）</li>
 *   <li>{@code DELETE /line/{id}}：删除实盘 line（in_progress 可删）</li>
 * </ul>
 *
 * <p>权限串 {@code djs:applet:warehouse:check:{overview,list,submit,remove}}（menu 9261-9265）。</p>
 *
 * @author djs
 * @since WMS-STOCK-001
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/applet/warehouse/check")
public class AppletStockCheckController extends BaseController {

    private final IStockCheckService checkService;

    /**
     * 多库一览：返回当前所有「进行中」盘点单（header 聚合 + 盈亏计）。
     *
     * <p>mp 前端用 8 静态库类卡片（白条 / 冻品 / 红白脏 / 蔬菜鲜品 / 重口蔬菜鲜品 / 干货 / 原材料 / 药品）
     * 做分组入口；本端点提供"进行中盘点单"数据供卡片展示「待录入数」。</p>
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:check:overview")
    @GetMapping("/overview")
    public TableDataInfo<StockCheckHeaderVo> overview(StockCheckQuery query, PageQuery pageQuery) {
        if (query == null) {
            query = new StockCheckQuery();
        }
        query.setCheckStatus("in_progress");
        return checkService.queryHeaderPage(query, pageQuery);
    }

    /**
     * 某盘点单已录入的实盘 line 列表。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:check:list")
    @GetMapping("/lines")
    public R<List<StockCheckRecordVo>> lines(@RequestParam String checkId) {
        return R.ok(checkService.queryLinesByCheckId(checkId));
    }

    /**
     * 提交 / 修改实盘 line（mp 盘点员）。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:check:submit")
    @PostMapping("/submit")
    public R<Long> submit(@Valid @RequestBody StockCheckLineBo bo) {
        return R.ok(checkService.submitLine(bo));
    }

    /**
     * 删除实盘 line（mp 盘点员；in_progress 盘点单可删）。
     */
    @SaCheckLogin
    @SaCheckPermission("djs:applet:warehouse:check:remove")
    @DeleteMapping("/line/{id}")
    public R<Void> removeLine(@PathVariable Long id) {
        checkService.removeLine(id);
        return R.ok();
    }

}
