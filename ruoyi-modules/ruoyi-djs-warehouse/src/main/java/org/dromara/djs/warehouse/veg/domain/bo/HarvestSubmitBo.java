package org.dromara.djs.warehouse.veg.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * mp 端采摘重量录入 BO（WMS-VEG-001 /harvest）。
 *
 * <p>地块维度采摘过磅：INSERT 一条 record_type=1 的 handle_record，聚合到 vegetable_handle.picked_weight，
 * 推进状态 pending → processing；weighFinish=1 时将汇总 is_weighed 置 1（不动 is_finish，不推 done）。</p>
 *
 * <p>字段名严格对齐 mp 契约 {@code miniapp/src/api/warehouse/vegHandle.ts} 的 {@code HarvestSubmitBody}。</p>
 *
 * <p>跨层契约：snowflake Long 字段（plantingRecordId / weighUserId）前端传 string，Jackson 反序列化为 Long。</p>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Data
public class HarvestSubmitBo {

    /**
     * 上游种植记录 ID（FK → t_warehouse_planting_record.id）。
     */
    @NotNull(message = "{veg.planting_record_id.required}")
    private Long plantingRecordId;

    /**
     * 采摘产品 ID（V6 row17，FK → t_warehouse_product_info.id）。
     *
     * <p>可空：作物只配了一个产品时 mp 只做展示不回传，服务端按作物首个配置产品补齐；
     * 作物配了多个时 mp 必须传用户所选那个。</p>
     */
    private Long productId;

    /**
     * 采摘重量(kg)，≥ 0。
     *
     * <p>V6 row28 允许 0：现场「已经称完、但没人去点完成」时，工人补一条 0 kg 记录把地块称重状态收口。
     * 0 kg 只有在 {@code weighFinish=1} 时才有意义，服务端 {@code submitHarvest} 硬校验这一条；
     * 负数任何时候都不合法，由本注解拦。</p>
     */
    @NotNull(message = "请填写采摘重量")
    @DecimalMin(value = "0", message = "采摘重量不能为负数")
    private BigDecimal harvestWeight;

    /**
     * 地块是否称重完成 1=是 / 0=否（是时把该地块 weighStatus 推 done，即汇总 is_weighed=1）。
     */
    @NotNull(message = "{veg.weigh_finish.required}")
    private Integer weighFinish;

    /**
     * 称重人 userId（FK → sys_user.user_id）。
     */
    @NotNull(message = "{veg.weigh_user.required}")
    private Long weighUserId;

    /**
     * 采摘班组 ID 列表（FK → t_plant_work_team.id），row64 由单选升级为全量多选。
     * 全集写入 t_warehouse_handle_record_team 中间表；旧单列 handle_record.team_id 写多选第一个作过渡，
     * row39 班组绩效按 team_id GROUP BY 的统计口径不变。
     *
     * <p><b>条件必填，不能用 {@code @NotEmpty} 声明</b>（V6 row28）：{@code harvestWeight > 0} 时必填
     * （否则这次称重进不了班组绩效聚合），{@code harvestWeight = 0} 的收口记录不必填（0 kg 摊到任何组
     * 都是 0，绩效 SQL 本就 {@code record_weight > 0} 过滤）。判定在 {@code submitHarvest} 里做。</p>
     */
    private List<Long> teamIds;

}
