package org.dromara.djs.plant.dashboard.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 种植看板 - 双甘特图单条（PLT-DASH-001）。
 *
 * <p>一条 {@code t_plant_plant_details} 明细最多拆出 2 个甘特条：</p>
 * <ul>
 *   <li>种植段（{@code type=plant}，前端绿色）：{@code begin_actualdate ~ end_actualdate}，
 *       进度由 {@code plant_status} 映射。</li>
 *   <li>采摘段（{@code type=pick}，前端黄色）：{@code begin_harvestdate ~ end_harvestdate}，
 *       进度由 {@code harvest_status} 映射。</li>
 * </ul>
 *
 * <p>日期为 null 的段不画条（未开始种植 / 未开始采摘）。</p>
 *
 * @author djs
 * @since PLT-DASH-001
 */
@Data
public class GanttItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 甘特条 ID（{@code 明细 snowflake id + "-plant" / "-pick"}）。
     *
     * <p>本身是拼接 string——上游 snowflake id 19 位序列化为 string（跨层契约 1），
     * 不准 number，否则前端丢精度。</p>
     */
    private String id;

    /**
     * 甘特条文本（{@code 作物名-地块名}）。
     */
    private String text;

    /**
     * 段开始日期。
     */
    private LocalDate startDate;

    /**
     * 段结束日期。
     */
    private LocalDate endDate;

    /**
     * 进度（0~100）：种植段由 {@code plant_status} 映射，采摘段由 {@code harvest_status} 映射。
     */
    private Integer progress;

    /**
     * 段类型：{@code plant}（种植）/ {@code pick}（采摘）。
     */
    private String type;

}
