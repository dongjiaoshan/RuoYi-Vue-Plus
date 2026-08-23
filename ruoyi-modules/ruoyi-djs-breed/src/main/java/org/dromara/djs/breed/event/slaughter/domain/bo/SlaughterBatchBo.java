package org.dromara.djs.breed.event.slaughter.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量出栏事件录入入参（mp 出栏录入页「批量出栏」）。
 *
 * <p>形态：N 头猪各自一个出栏重量（{@link Item}），其余录入项（出栏日期 / 出栏去向 /
 * 出栏人员 / 出栏照片）整批共用。service 逐头组装 {@link SlaughterBo} 复用单只
 * {@code recordSlaughter}，每头都触发完整 side effect（状态机 → END(MARKET) +
 * {@code PigMarketingEvent} → 燎毛白条），整批同一事务原子提交（任一失败全回滚）。</p>
 *
 * <p>照片：{@code ossIds} 整批录一次，落库时**每头记录各写一份**（{@code oss_ids} 列逐行独立取值，
 * 非引用共享）——甲方口径「每个记录的出栏照片都使用同一张照片（以复制的方式进行存储到每一头记录上）」。</p>
 *
 * <p>猪只标识用 {@code pigId}（雪花 Long；mp 端批量选择页回传的是 pigId 而非耳号），
 * 与单只 BO 的 earNo/pigId 二选一不同——批量场景 mp 侧只有 pigId。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Data
public class SlaughterBatchBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待出栏猪只 + 各自出栏重量（至少 1 头）。 */
    @Valid
    @NotEmpty(message = "{slaughter.batch.empty}")
    private List<Item> items;

    /** 出栏日期（整批共用）。 */
    @NotNull(message = "{slaughter.date.required}")
    private LocalDateTime marketingDate;

    /** 出栏去向（字典 djs_market_dest，整批共用）。 */
    @NotBlank(message = "{slaughter.dest.required}")
    @Size(max = 32, message = "{slaughter.dest.size}")
    private String outDest;

    /** 目标门店 ID（如适用，整批共用）。 */
    private Long storeId;

    /** 出栏照片 ossIds（整批共用，强制至少 1 张；逐头复制落库）。 */
    @NotBlank(message = "{slaughter.photo.required}")
    @Size(max = 1024, message = "{slaughter.photo.size}")
    private String ossIds;

    /** 出栏人员 userId（整批共用，EmployeePicker 所选，雪花 string；可空）。 */
    @Size(max = 64, message = "{slaughter.operator.size}")
    private String operator;

    @Size(max = 500, message = "{slaughter.remark.size}")
    private String remark;

    /** 单头出栏明细：猪只 + 该头出栏重量。 */
    @Data
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 猪只 ID（雪花；mp 批量选择页回传）。 */
        @NotNull(message = "{slaughter.batch.pig_required}")
        private Long pigId;

        /**
         * 该头出栏重量 kg，必须落在 100–250（甲方口径：正常的猪只出栏重量）。
         *
         * <p>下沉到后端而不只留在录入页：前端校验绕过接口即失效，实测直传 50 / 500 都能落库，
         * 且会连带把 {@code t_warehouse_bar_info.marketing_weight} 写成同样离谱的值。</p>
         */
        @NotNull(message = "{slaughter.weight.required}")
        @DecimalMin(value = "100", message = "{slaughter.batch.weight_range}")
        @DecimalMax(value = "250", message = "{slaughter.batch.weight_range}")
        private BigDecimal outWeight;
    }
}
