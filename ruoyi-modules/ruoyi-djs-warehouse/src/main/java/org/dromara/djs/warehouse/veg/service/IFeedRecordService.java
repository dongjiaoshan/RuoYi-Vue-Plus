package org.dromara.djs.warehouse.veg.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.veg.domain.bo.FeedRecordQuery;
import org.dromara.djs.warehouse.veg.domain.vo.FeedRecordVo;

import java.util.List;

/**
 * 有机饲喂记录 Service（WMS-FEED-RECORD-001，仓库-admin 行21「有机饲喂记录」只读菜单）。
 *
 * <p>over {@code t_warehouse_feed_log} 分页只读列表，覆盖两类来源（毛菜处理间 + 仓库领用）。</p>
 *
 * @author djs
 * @since WMS-FEED-RECORD-001
 */
public interface IFeedRecordService {

    /**
     * 有机饲喂记录分页列表（行21）。
     *
     * @param query     查询条件（作物名模糊 / 提供位置精确 / 日期范围）
     * @param pageQuery 分页参数
     * @return 分页结果（按饲喂时间倒序）
     */
    TableDataInfo<FeedRecordVo> queryPage(FeedRecordQuery query, PageQuery pageQuery);

    /**
     * 有机饲喂记录不分页列表（导出用，行21）。
     *
     * @param query 查询条件（作物名模糊 / 提供位置精确 / 日期范围）
     * @return 全量列表（按饲喂时间倒序）
     */
    List<FeedRecordVo> queryList(FeedRecordQuery query);
}
