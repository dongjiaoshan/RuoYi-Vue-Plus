package org.dromara.djs.plant.activity.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采摘活动实体（FIX-PLT-HARVEST-ACTIVITY-001）。
 *
 * <p>对应表 {@code t_plant_plant_activity}（V202606291500 建表 + V202607301300 DENGBO-R4 加去向列）：
 * 采摘重量录入流水表，每次 mp 采摘录入一行（per-event）。
 * 报表 {@code SUM(daily_weight) GROUP BY crop_id, activity_date}。</p>
 *
 * <p>DENGBO-R4（决策 A）起改 per-event：原 UNIQUE(crop,date,班组) 已 DROP，
 * 同 crop+date+班组允许多行（不同去向/地块）。{@code recordPickActivity} 每次 INSERT 一行，
 * 携带去向 {@code pickDest} / 地块 {@code plotId} / 记录人 {@code recorderId}。</p>
 *
 * @author djs
 * @since FIX-PLT-HARVEST-ACTIVITY-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_plant_activity")
public class PlantActivity extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 采摘日期。 */
    private LocalDate activityDate;

    /** FK → {@code t_plant_crop_info.id}。 */
    private Long cropId;

    /** 当次采摘重量（kg，per-event；与 pickWeight 同值，报表 SUM 口径不变）。 */
    private BigDecimal dailyWeight;

    /** 本次采摘重量（kg，DENGBO-R4 新列，语义同 dailyWeight 更明确）。 */
    private BigDecimal pickWeight;

    /** 采摘去向（字典 {@code djs_pick_dest}：sale/veg_fresh/platform/loss/feed；NULL=历史销售）。 */
    private String pickDest;

    /** DENGBO-R21/R24 销售量分摊结算轮次：0=未结算(当前批次)，&gt;0=已随第 N 次「录入完成」结算。 */
    private Integer settleRound;

    /** 果蔬成品 product_id（= {@code crop.related_product} 解析，可空）。 */
    private Long productId;

    /** 地块 id（{@code t_plant_plot_info.id}；非销售去向必填，销售去向为空）。 */
    private Long plotId;

    /** 记录人 userId（仓库相关人员；替代旧记录班组 activityBy）。 */
    private Long recorderId;

    /** 记录班组 FK → {@code t_plant_work_team.id}（DENGBO-R4 起由 recorderId 取代，保留兼容历史行）。 */
    private Long activityBy;

    @TableLogic
    private String delFlag;

    private Long delUnique;
}
