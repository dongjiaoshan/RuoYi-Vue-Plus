package org.dromara.djs.store.returns.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 「新增单位退回」BO（STR-RETURN-OPS-001，甲方 row213 第 5 条）。
 *
 * <p>与门店退回（{@code StoreReturnBatchBo}）的根本差别：<b>单位退回没有门店</b> ——
 * 退回对象是「退回单位配置」字典里的外单位，落 {@code return_unit} 新列，绝不塞 {@code store_id}
 * （那会污染门店盘点候选 / 门店退回分组 / store-daily 汇总）。</p>
 *
 * <p>单位退回一次性建成「已处理」态（甲方「退回状态默认为已处理」）：四个人员时间列全部填
 * 当前操作人与当前时刻，未丢弃的行同事务写入库（入库方式 {@code store_return_in}「门店退回」）。</p>
 *
 * @author djs
 * @since STR-RETURN-OPS-001
 */
@Data
public class StoreReturnUnitBo {

    /** 退回日期（甲方必填项；只到天，落 {@code return_date}）。 */
    @NotNull(message = "退回日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate returnDate;

    /** 退回单位（字典 {@code djs_return_unit}「退回单位配置」，值取自出库去向）。 */
    @NotBlank(message = "退回单位不能为空")
    @Size(max = 64, message = "退回单位长度不能超过 64")
    private String returnUnit;

    /** 退回明细行（至少 1 行；只有填了退回量的产品才会被提交上来）。 */
    @Valid
    @NotEmpty(message = "退回明细不能为空")
    private List<Item> items;

    /**
     * 单位退回明细单行。
     *
     * @author djs
     * @since STR-RETURN-OPS-001
     */
    @Data
    public static class Item {

        /** 产品 FK → {@code t_warehouse_product_info.id}。 */
        @NotNull(message = "产品不能为空")
        private Long productId;

        /**
         * 退回量（按产品单位：kg 产品为重量、非 kg 产品为份/把/盒/枚，落 {@code return_quantity}）。
         * 甲方第 6 条：支持两位小数（kg 三位，由前端限制，后端只做正数校验）。
         */
        @NotNull(message = "退回量不能为空")
        @Positive(message = "退回量必须大于 0")
        private BigDecimal returnQuantity;

        /** 入库库位 FK → {@code t_warehouse_location_info.id}（可空 → service 按入库产品默认库位兜底）。 */
        private Long locationId;

        /** 处置方式：0/null=产品入库（默认，写库存） / 1=产品丢弃（不入库）。 */
        private Integer isDiscard;
    }
}
