package org.dromara.djs.warehouse.stat.service;

import org.dromara.djs.warehouse.stat.domain.vo.WarehouseAnnualRecordVo;
import org.dromara.djs.warehouse.stat.domain.vo.WarehouseCroppRecordVo;
import org.dromara.djs.warehouse.stat.domain.vo.WarehouseIndicatorRecordVo;
import org.dromara.djs.warehouse.stat.domain.vo.WarehouseMonthlyRecordVo;

import java.time.LocalDate;
import java.util.List;

/**
 * 仓库统计指标聚合 + 只读查询服务（WMS-STAT-001，邓博 admin row16/17/18）。
 *
 * @author djs
 * @since WMS-STAT-001
 */
public interface IWarehouseStatService {

    /**
     * 聚合落盘指定日期的仓库统计（日表 + 作物日表 UPSERT，并刷新该日所在月的月表汇总）。
     *
     * <p>由 {@link org.dromara.djs.warehouse.stat.job.WarehouseStatJob} 每晚跑批调用（T-1），
     * 也由手动 trigger-aggregate 端点调用（补算 / 验证）。UPSERT 幂等：同 tenant_id+stat_date 重跑覆盖。</p>
     *
     * @param targetDate 目标统计日（null = T-1 昨天）
     * @return 执行摘要（含 tenant / date / 落盘行数提示）
     */
    String aggregate(LocalDate targetDate);

    /**
     * 回补区间内每天的仓库生产指标并刷新涉及的月表。
     *
     * <p>同一头猪的第二个半扇可能隔几天才处理，它的数据要算回接收那天；定时任务每天回补最近一个月，
     * 历史区间可经手动端点一次性补算。</p>
     *
     * @param from 起始日（含）
     * @param to   截止日（含）
     * @return 回补摘要
     */
    String refreshProductionIndicators(LocalDate from, LocalDate to);

    /** 查找近期分割变更影响的窗口外领用日，供 warehouse-stat 回刷。 */
    List<LocalDate> findEarlierCutPickupDates(LocalDate from, LocalDate until);

    /** 查找近期标损、发货补录或需求调整影响的窗口外统计日。 */
    List<LocalDate> findEarlierVegIndicatorDates(LocalDate from, LocalDate until);

    /** 只使用上海业务当天的账面余额，重拍覆盖当天。 */
    void captureVegStockSnapshot(LocalDate targetDate);

    /** 服务在收工时间之后启动时，只补采尚未采集的业务当天。 */
    void captureMissingVegStockSnapshot(LocalDate targetDate);

    /**
     * 仓库日报表列表（row16）：按日期范围倒序返回全量（行数=天数，前端客户端分页）。
     *
     * @param from 起始日期（含，可空）
     * @param to   截止日期（含，可空）
     */
    List<WarehouseIndicatorRecordVo> listDaily(LocalDate from, LocalDate to);

    /**
     * 作物日报表列表（row17）：按日期范围 + JOIN 作物信息，日期倒序 + crop_id 升序（前端客户端分页）。
     *
     * @param from 起始日期（含，可空）
     * @param to   截止日期（含，可空）
     */
    List<WarehouseCroppRecordVo> listCrop(LocalDate from, LocalDate to);

    /**
     * 仓库月报表列表（row18）：按月份范围倒序返回全量（前端客户端分页）。
     *
     * @param monthFrom 起始月份 yyyy-MM（含，可空）
     * @param monthTo   截止月份 yyyy-MM（含，可空）
     */
    List<WarehouseMonthlyRecordVo> listMonthly(String monthFrom, String monthTo);

    /** 从月表基数汇总所选年份的指标。 */
    WarehouseAnnualRecordVo getAnnual(int year);
}
