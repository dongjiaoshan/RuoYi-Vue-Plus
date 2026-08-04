package org.dromara.djs.warehouse.veg.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.warehouse.veg.domain.bo.FeedDailyConfirmBo;
import org.dromara.djs.warehouse.veg.domain.bo.FeedRecordQuery;
import org.dromara.djs.warehouse.veg.domain.vo.FeedDailyVo;
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

    /**
     * 有机饲喂**按日汇总**分页（admin 行199 列表 / mp 行268 卡片）。
     *
     * <p>一天一行，当日各来源重量求和 + 左联日确认表带出框数 / 确认人。
     * 只吃日期范围条件；作物名 / 提供位置属明细维度，汇总层不参与筛选。</p>
     *
     * @param query     查询条件（仅 dateFrom / dateTo 生效）
     * @param pageQuery 分页参数
     * @return 分页结果（按日期倒序）
     */
    TableDataInfo<FeedDailyVo> queryDailyPage(FeedRecordQuery query, PageQuery pageQuery);

    /**
     * 录入 / 修改某日的仓库确认框数（mp 行268【框数录入】）。
     *
     * <p>按 {@code feedDate} upsert：该日无确认行则插入，有则更新（甲方允许录错后改，改则确认人 /
     * 确认时间一并刷成最后一次操作者）。</p>
     *
     * @param bo 录入内容（日期 / 框数 / 确认人）
     */
    void saveDailyConfirm(FeedDailyConfirmBo bo);
}
