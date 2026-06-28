package org.dromara.djs.warehouse.veg.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.djs.warehouse.veg.domain.bo.FeedRecordQuery;
import org.dromara.djs.warehouse.veg.domain.vo.FeedRecordVo;
import org.dromara.djs.warehouse.veg.service.IFeedRecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
