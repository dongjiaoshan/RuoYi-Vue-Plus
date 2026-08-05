package org.dromara.djs.warehouse.veg.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.veg.domain.bo.FeedDailyConfirmBo;
import org.dromara.djs.warehouse.veg.domain.bo.FeedRecordQuery;
import org.dromara.djs.warehouse.veg.domain.vo.FeedDailyVo;
import org.dromara.djs.warehouse.veg.domain.vo.FeedRecordVo;
import org.dromara.djs.warehouse.veg.service.IFeedRecordService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 有机饲喂记录 Controller（WMS-FEED-RECORD-001，仓库-admin 行21「有机饲喂记录」只读菜单）。
 *
 * <p>挂「仓库-库存管理」9302 下，仅查看无操作（不发 add/edit/del 权限）。over {@code t_warehouse_feed_log}
 * 分页列表，覆盖毛菜处理间 + 仓库领用两类来源。权限串 {@code djs:warehouse:feed:record:list}（菜单 perms
 * 通配 {@code djs:warehouse:feed:record:*}，Sa-Token vagueMatch 命中）。</p>
 *
 * @author djs
 * @since WMS-FEED-RECORD-001
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/djs/warehouse/feedRecord")
public class FeedRecordController extends BaseController {

    private final IFeedRecordService feedRecordService;

    /**
     * 有机饲喂记录分页列表（行21）：日期 / 作物图 / 作物名 / 饲喂量 / 提供位置 / 操作人。
     *
     * <p>搜索：作物名称模糊（cropName）+ 提供位置 djs_feed_type（feedType）+ 可选日期范围。</p>
     *
     * @param query     查询条件
     * @param pageQuery 分页参数
     */
    @SaCheckPermission("djs:warehouse:feed:record:list")
    @GetMapping("/list")
    public TableDataInfo<FeedRecordVo> list(FeedRecordQuery query, PageQuery pageQuery) {
        return feedRecordService.queryPage(query, pageQuery);
    }

    /**
     * 有机饲喂记录导出（行21）：按当前搜索条件（作物名 / 提供位置 / 日期范围）导出全量。
     *
     * @param query    查询条件
     * @param response HTTP 响应
     */
    @SaCheckPermission("djs:warehouse:feed:record:list")
    @Log(title = "有机饲喂记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(FeedRecordQuery query, HttpServletResponse response) {
        List<FeedRecordVo> list = feedRecordService.queryList(query);
        ExcelUtil.exportExcel(list, "有机饲喂记录", FeedRecordVo.class, response);
    }

    /**
     * 有机饲喂**按日汇总**分页（admin 行199 主列表 / mp 行268 卡片）：
     * 日期 / 总重量 / 仓库确认框数 / 仓库确认人。
     *
     * <p>只吃日期范围；作物名与提供位置是明细维度，点「查看详情」走 {@link #list} 拉当日明细。</p>
     *
     * @param query     查询条件（仅 dateFrom / dateTo 生效）
     * @param pageQuery 分页参数
     */
    @SaCheckPermission("djs:warehouse:feed:record:list")
    @GetMapping("/daily")
    public TableDataInfo<FeedDailyVo> daily(FeedRecordQuery query, PageQuery pageQuery) {
        return feedRecordService.queryDailyPage(query, pageQuery);
    }

    /**
     * 录入某日的仓库确认框数（mp【框数录入】）。
     *
     * <p>按日期 upsert：撞已存在的日期就刷框数 / 确认人 / 确认时间成最后一次操作者。</p>
     *
     * <p><b>入口只此一个，且 mp 只对「未录入」的日期放出</b>——甲方要求录完即锁死、避免随意修改，
     * 所以 mp 卡片上已录入的框数是纯展示、无点击入口，admin 该页也是只读列表。
     * upsert 语义保留是为了并发首次录入兜底，<b>不是</b>给「录错重录」用的通道。
     * 录错的纠正路径当前只能走后台改库——这是已知缺口，改动前先与甲方确认。</p>
     *
     * @param bo 日期 / 框数 / 确认人
     */
    @SaCheckPermission("djs:warehouse:feed:record:list")
    @Log(title = "有机饲喂日确认", businessType = BusinessType.UPDATE)
    @PostMapping("/daily/confirm")
    public R<Void> saveDailyConfirm(@Validated @RequestBody FeedDailyConfirmBo bo) {
        feedRecordService.saveDailyConfirm(bo);
        return R.ok();
    }
}
