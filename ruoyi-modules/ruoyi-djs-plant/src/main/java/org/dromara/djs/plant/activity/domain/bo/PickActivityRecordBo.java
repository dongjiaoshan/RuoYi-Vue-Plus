package org.dromara.djs.plant.activity.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采摘去向录入 BO（DENGBO-R4 决策 A）。
 *
 * <p>采摘活动录入弹窗：采摘日期 + 采摘重量 + 采摘去向(5 选 1) + 地块(去向≠销售必填) + 记录人。
 * 提交后 plant 侧 INSERT 一行 {@code t_plant_plant_activity}（per-event），非销售去向累加所选地块产量。
 * 仓库入账（入库/月台/损耗/饲喂）由编排端点（warehouse 模块）另行调用。</p>
 *
 * @author djs
 * @since DENGBO-R4
 */
@Data
public class PickActivityRecordBo {

    /** 作物 id（{@code t_plant_crop_info.id}）。 */
    @NotNull(message = "作物不能为空")
    private Long cropId;

    /** 采摘日期。 */
    @NotNull(message = "采摘日期不能为空")
    private LocalDate activityDate;

    /** 本次采摘重量 kg（> 0）。 */
    @NotNull(message = "请输入采摘重量")
    @DecimalMin(value = "0.001", message = "采摘重量必须大于 0")
    private BigDecimal pickWeight;

    /** 采摘去向（字典 {@code djs_pick_dest}：sale/veg_fresh/platform/loss/feed）。 */
    @NotNull(message = "请选择采摘去向")
    private String pickDest;

    /** 地块 id（非销售去向必填；销售去向传 null）。 */
    private Long plotId;

    /** 记录人 userId（仓库相关人员）。 */
    private Long recorderId;
}
