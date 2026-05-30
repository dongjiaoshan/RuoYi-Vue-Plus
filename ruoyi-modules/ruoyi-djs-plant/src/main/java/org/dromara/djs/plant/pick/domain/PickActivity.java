package org.dromara.djs.plant.pick.domain;

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
 * 采摘活动实体（PLT-PLAN-002，游客体验采摘场景）。
 *
 * <p>对应表 {@code t_plant_pick_activity}（V202606101200，xlsx 无对应 sheet，doc/06 推断 11 业务字段）。</p>
 *
 * <p>业务定位：游客体验采摘活动管理（doc/10 §F-PLT-05 + admin 原型 page 61）。
 * 与 mp 工人"采摘任务"分离：</p>
 * <ul>
 *   <li>mp 工人采摘 → 写 {@code t_plant_plant_details.actual_yield}（D12 PLT-PICK-001）</li>
 *   <li>游客采摘活动 → 本表 + 标记 {@code plant_details.is_pick=1}（admin 端 PLT-PLAN-002）</li>
 * </ul>
 *
 * <p>{@code total_yield} 由活动结束后聚合当日 {@code plant_details.actual_yield} 回填。</p>
 *
 * @author djs
 * @since PLT-PLAN-002
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_pick_activity")
public class PickActivity extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 活动业务码 {@code PA + yyyyMMdd + 3 位}（inline 生成）。 */
    private String activityNo;

    /** 活动名称（自由文本，如"南瓜采摘节"）。 */
    private String activityName;

    /** 活动日期。 */
    private LocalDate activityDate;

    /** 活动状态（字典 {@code djs_pick_activity_status}：upcoming/ongoing/ended）。 */
    private String activityStatus;

    /** FK → {@code t_plant_crop_info.id}。 */
    private Long cropId;

    /** 冗余 from {@code crop_info.crop_name}。 */
    private String cropName;

    /** 涉及地块数（admin 填）。 */
    private Integer totalPlot;

    /** 当日采摘汇总（活动结束后从 plant_details.actual_yield 聚合回填）。 */
    private BigDecimal totalYield;

    /** 当日参与游客人数。 */
    private Integer visitorCount;

    /** 备注（BaseEntity 未带 remark，业务表自带）。 */
    private String remark;

    @TableLogic
    private String delFlag;

    private Long delUnique;
}
