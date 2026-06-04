package org.dromara.djs.plant.pick.event;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采摘完成事件载荷（CROSS-FLOW-002）。
 *
 * <p>由 plant 域 {@code AppletPickServiceImpl#submitPick} 在 finish=true 流转到 completed
 * 时组装，随 {@link PlantPickedEvent} 发布。warehouse 域 listener 仅读本载荷字段，
 * <b>绝不</b>反查 plant 模块的 Service / Mapper（避免 warehouse→plant→warehouse 依赖环）。</p>
 *
 * <p>所有冗余名称字段（{@code plotName / cropName / teamName}）在 plant 域发 event 前已反查填好，
 * 仓库侧直接落库即可。</p>
 *
 * @author djs
 * @since CROSS-FLOW-002
 */
@Data
public class PlantPickedPayload {

    /** 地块 ID。 */
    private Long plotId;

    /** 作物 ID。 */
    private Long cropId;

    /** 地块名称（plant 域已反查，null 安全）。 */
    private String plotName;

    /** 作物名称（plant 域已反查，null 安全）。 */
    private String cropName;

    /** 实际种植开始日期（{@code PlantDetails.beginActualdate}，可为 null）。 */
    private LocalDate plantDate;

    /** 采摘完成日期（{@code PlantDetails.endHarvestdate}，finish 时由提交置入）。 */
    private LocalDate harvestDate;

    /** 累计采摘产量(kg)，权威值取 {@code PlantDetails.actualYield}（finish 时必非 null）。 */
    private BigDecimal harvestWeight;

    /** 采摘班组 ID（{@code PlantDetails.harvestBy}）。 */
    private Long teamId;

    /** 采摘班组名称（plant 域已反查，null 安全）。 */
    private String teamName;
}
