package org.dromara.djs.warehouse.veg.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采摘去向录入编排入参（DENGBO-R4 决策 A，mp 采摘活动录入弹窗）。
 *
 * <p>放 warehouse 模块、由 {@code AppletVegHandleController} 编排（warehouse → plant 单向依赖）：</p>
 * <ol>
 *   <li>调 plant {@code IPlantActivityService.recordPickActivity}：写 activity per-event 行 + 非销售去向累加地块产量</li>
 *   <li>非销售去向调 warehouse {@code IVegetableHandleService.recordPickDestination}：按去向写仓库台账</li>
 * </ol>
 *
 * @author djs
 * @since DENGBO-R4
 */
@Data
public class PickActivitySubmitBo {

    /** 作物 id（{@code t_plant_crop_info.id}）。 */
    @NotNull(message = "作物不能为空")
    private Long cropId;

    /** 作物名称（冗余快照，写仓库台账 remark / feed_log.crop_name 用，可空）。 */
    private String cropName;

    /** 采摘日期（结算-only 可空）。 */
    private LocalDate activityDate;

    /**
     * 本次采摘重量 kg。DENGBO-R24：录入完成结算-only 时可为空/0（仅触发销售量分摊、不录入新流水）；
     * 正常录入的 &gt;0 校验由 service 层 recordPickActivity 兜底（此处不加 @DecimalMin，否则挡住结算-only）。
     */
    private BigDecimal pickWeight;

    /** 采摘去向（字典 {@code djs_pick_dest}：sale/veg_fresh/platform/loss/feed）。 */
    @NotNull(message = "请选择采摘去向")
    private String pickDest;

    /** 地块 id（非销售去向必填；销售去向传 null）。 */
    private Long plotId;

    /** 记录人 userId（仓库相关人员）。 */
    private Long recorderId;

    /** DENGBO-R24 录入完成标志：1=本次录入后触发销售量分摊结算（前置：当前批次全部地块采摘完成）。 */
    private Integer finishFlag;
}
