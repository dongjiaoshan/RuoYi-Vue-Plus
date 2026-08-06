package org.dromara.djs.plant.plan.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * admin 已种植地块后台调整入参 BO（V6-R36）。
 *
 * <p>入口：种植计划详情 → 地块明细 → 该行「修改」（仅已种植，即
 * {@code begin_actualdate IS NOT NULL} 的行显示）。弹框三行，对应本 BO 三个字段：</p>
 *
 * <ol>
 *   <li>{@link #plantState} 种植状态：{@code planted}=保持已种植 / {@code pending}=改回待种植</li>
 *   <li>{@link #beginActualdate} 种植日期（仅 planted 时提交）</li>
 *   <li>{@link #plantByIds} 种植班组多选（仅 planted 时提交）</li>
 * </ol>
 *
 * <p>三个互斥保存分支（服务端按「实际变化」判定，不看前端意图）：</p>
 * <ul>
 *   <li>改回待种植 → 明细回 pending、清种植/采摘日期、清种植班组、
 *       {@code change_type='admin'}，地块无其它在种明细时回空闲</li>
 *   <li>改种植日期（可连带班组）→ 重算计划采摘窗口、同步班组、{@code change_type='admin'}</li>
 *   <li>仅改班组 → 只动班组、{@code change_type='admin_team'}</li>
 *   <li>三项皆未变 → 不写库，返回 0</li>
 * </ul>
 *
 * @author djs
 * @since V6-R36
 */
@Data
public class PlantDetailAdjustBo {

    /** 待调整的计划明细 id（{@code t_plant_plant_details.id}）。 */
    @NotNull(message = "请选择要修改的地块明细")
    private Long detailId;

    /** 种植状态：{@code planted}=已种植（保持） / {@code pending}=待种植（回退）。 */
    @NotBlank(message = "请选择种植状态")
    @Pattern(regexp = "planted|pending", message = "种植状态只能是 planted 或 pending")
    private String plantState;

    /** 种植日期（{@code begin_actualdate}）；{@code plantState=pending} 时忽略。 */
    private LocalDate beginActualdate;

    /**
     * 种植班组全集（写中间表 role=plant，旧单列 {@code plant_by} 取第一个）；
     * {@code plantState=pending} 时忽略。传 null 表示「本次不动班组」，传空 list 表示清空班组。
     */
    private List<Long> plantByIds;
}
