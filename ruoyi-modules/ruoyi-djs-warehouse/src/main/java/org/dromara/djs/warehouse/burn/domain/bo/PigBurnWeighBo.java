package org.dromara.djs.warehouse.burn.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 燎毛「称重」入参 BO（mp 称重落库 = pending_singe → singing 推进）。
 *
 * <p>mp 端燎毛间称重提交时调 {@code POST /applet/warehouse/pigBurn/weigh}，传白条 ID +
 * 到场重量 + 称重人；Service 校验 bar 在待燎毛/燎毛中态 + 到场重 ≤ 出栏重 → 推 bar 到
 * {@code singing}（燎毛中），回填 {@code arrive_weight}。解决现状「mp 称重只做本地态不落库」。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-BURN-WEIGH-001
 */
@Data
public class PigBurnWeighBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 待称重的白条 ID（{@code t_warehouse_bar_info.id}，snowflake；必填）。
     */
    @NotNull(message = "{burn.bar_info_id.required}")
    private Long barInfoId;

    /**
     * 到场重量 kg（称重过磅值，&gt; 0；必填）。
     */
    @NotNull(message = "{burn.arrive_weight.required}")
    @DecimalMin(value = "0.001", message = "{burn.arrive_weight.positive}")
    private BigDecimal arriveWeight;

    /**
     * 称重人 ID（{@code sys_user.user_id}，snowflake；必填）。
     */
    @NotNull(message = "{burn.weigher_id.required}")
    private Long weigherId;

}
