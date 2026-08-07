package org.dromara.djs.warehouse.veg.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * mp 端果蔬处理录入 BO（WMS-VEG-001 /process）。
 *
 * <p>地块维度处理分流：INSERT 一条 record_type=2 的 handle_record（带 handle_target 去向），
 * 按去向聚合到 vegetable_handle（入库/月台累加 handled_weight，饲料只累加 feed_weight）；
 * processFinish=1 时将汇总 is_finish 置 1 + handle_status=done + pick_end_time=now，并推进 planting_record 至 done。</p>
 *
 * <p>字段名严格对齐 mp 契约 {@code miniapp/src/api/warehouse/vegHandle.ts} 的 {@code ProcessSubmitBody}。</p>
 *
 * <p>跨层契约：snowflake Long 字段（plantingRecordId / processUserId）前端传 string，Jackson 反序列化为 Long。</p>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Data
public class ProcessSubmitBo {

    /**
     * 上游种植记录 ID（FK → t_warehouse_planting_record.id）。
     */
    @NotNull(message = "{veg.planting_record_id.required}")
    private Long plantingRecordId;

    /**
     * 处理产品 ID（V6 row18，FK → t_warehouse_product_info.id）。
     *
     * <p>口径同 {@link HarvestSubmitBo#getProductId()}：单产品作物可不传，服务端补首个配置产品。</p>
     */
    private Long productId;

    /**
     * 处理重量(kg)，≥ 0。
     *
     * <p>V6 row29 允许 0：口径同 {@link HarvestSubmitBo#getHarvestWeight()} —— 0 kg 只有在
     * {@code processFinish=1}（把地块处理状态收口）时才有意义，服务端 {@code submitProcess} 硬校验；
     * 负数任何时候都不合法，由本注解拦。</p>
     *
     * <p><b>「留空 = 0」的归一在 mp 端做</b>（V6 row41）：本字段对 API 仍是必传，传 null 直接 400 ——
     * 客户端漏传字段是 bug，不该被静默当成 0 收下一条 0 kg 记录。</p>
     */
    @NotNull(message = "请填写处理重量")
    @DecimalMin(value = "0", message = "处理重量不能为负数")
    private BigDecimal processWeight;

    /**
     * 去向 djs_handle_target：1=蔬菜保鲜库(入库) / 2=蔬菜月台 / 3=有机饲料。
     *
     * <p><b>条件必填，不能用 {@code @NotNull} 声明</b>（V6 row41）：{@code processWeight > 0} 时必填
     * （有货就必须说清楚货去哪了），{@code processWeight = 0} 的收口记录不必填 —— 一分货都没走，
     * 三个去向桶加 0 完全等价，强制选一个只会让工人瞎点、给流水留假去向。传了就仍须 ∈ {1,2,3}。
     * 判定在 {@code submitProcess} 里做。</p>
     */
    private Integer handleTarget;

    /**
     * 地块是否处理完成 1=是 / 0=否（是时把该地块 processStatus 推 done，即汇总 is_finish=1 + handle_status=done）。
     */
    @NotNull(message = "{veg.process_finish.required}")
    private Integer processFinish;

    /**
     * 处理人 userId（FK → sys_user.user_id）。
     */
    @NotNull(message = "{veg.process_user.required}")
    private Long processUserId;

}
