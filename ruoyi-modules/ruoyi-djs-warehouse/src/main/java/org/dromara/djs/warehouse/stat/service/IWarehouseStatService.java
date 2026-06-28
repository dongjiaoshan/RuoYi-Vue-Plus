package org.dromara.djs.warehouse.stat.service;

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
}
