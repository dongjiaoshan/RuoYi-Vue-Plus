package org.dromara.djs.warehouse.burn.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 燎毛「处理完成」入参 BO（FIX-WMS-MP-BURN-001 客户 6/11 新需求）。
 *
 * <p>mp 端点击「处理完成」按钮调 {@code POST /applet/warehouse/pigBurn/finish}，
 * 仅传白条 ID；Service 端校验燎毛中态 + 累计总重 ≤ 出栏重 + 整只/半只互斥后推 bar 到
 * {@code singed}（燎毛处理完成终态）。处理完成人取登录态。</p>
 *
 * @author djs
 * @since FIX-WMS-MP-BURN-001
 */
@Data
public class PigBurnFinishBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 待处理完成的白条 ID（{@code t_warehouse_bar_info.id}，snowflake；必填）。
     */
    @NotNull(message = "{burn.bar_info_id.required}")
    private Long barInfoId;

}
