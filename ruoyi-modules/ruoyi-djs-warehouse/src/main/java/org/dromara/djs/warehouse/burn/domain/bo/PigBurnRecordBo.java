package org.dromara.djs.warehouse.burn.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.warehouse.burn.domain.PigBurnRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 燎毛入库提交入参 BO（D12X-MP-BURN-IA-001）。
 *
 * <p>燎毛间 IA 重做：mp 列表页选「已出栏待燎毛白条」→ 入库子页按产品类型分别入库。
 * Service 层处理流程（入库语义，非旧的扣减出库）：</p>
 * <ol>
 *   <li>SELECT bar_info 校验 status IN ('pending_singe','singing')</li>
 *   <li>生成 burnId + INSERT 燎毛记录（burn_status='done'，operatorId=入库人）</li>
 *   <li>for each productTypeItem：INSERT {@code t_warehouse_product_inhouse} + INSERT
 *       {@code t_warehouse_stock_flow}（inout_type='IN'，flow_type='slaughter_burn'）</li>
 *   <li>UPDATE bar_info status → in_stock（乐观锁）+ 回填 in_weight（各类型合计）/ in_time / in_method=1</li>
 * </ol>
 *
 * <p>4 步同一 {@code @Transactional}，任一失败整体回滚。客户 §2.3 ⑤要求删拍照 → 无 proofOssIds。</p>
 *
 * @author djs
 * @since D12X-MP-BURN-IA-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PigBurnRecord.class, reverseConvertGenerate = false)
public class PigBurnRecordBo extends BaseEntity {

    /**
     * 燎毛记录 ID（编辑场景留空；本 ticket mp 仅 submit，不开放 update）。
     */
    private Long id;

    /**
     * 选中的待燎毛白条 ID（{@code t_warehouse_bar_info.id}，mp 列表→子页传入；必填）。
     */
    @NotNull(message = "{burn.bar_info_id.required}")
    private Long barInfoId;

    /**
     * 猪只耳号（冗余，service 可从 bar 反查；列表已带，提交一并回传）。
     */
    @Size(max = 32, message = "{burn.ear_no.size}")
    private String earNo;

    /**
     * 燎毛时间（mp 端默认 NOW）。
     */
    @NotNull(message = "{burn.burn_time.required}")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date burnTime;

    /**
     * 到场重量 kg（可选；填则 service 计算损耗 = arriveWeight - 各类型入库重量合计）。
     */
    @DecimalMin(value = "0.001", message = "{burn.arrive_weight.positive}")
    private BigDecimal arriveWeight;

    /**
     * 入白条库的库位 ID（限冻品库；必填）。
     */
    @NotNull(message = "{burn.location_id.required}")
    private Long locationId;

    /**
     * 入库人 ID（{@code sys_user.user_id}，EmployeePicker 选；snowflake；必填）。
     */
    @NotNull(message = "{burn.operator_id.required}")
    private Long operatorId;

    /**
     * 按产品类型分别入库的明细（整只 / 半只 / 猪头 / 猪蹄，至少 1 行；客户 §2.3 ②）。
     */
    @Valid
    @NotEmpty(message = "{burn.product_type_items.required}")
    private List<ProductTypeItem> productTypeItems;

    /**
     * 备注（可选）。
     */
    @Size(max = 500, message = "{burn.remark.size}")
    private String remark;

    /**
     * 单个产品类型入库明细。
     */
    @Data
    public static class ProductTypeItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 白条产品类型 ID（{@code t_warehouse_product_info.id}，productTypes 端点返回；必填）。
         */
        @NotNull(message = "{burn.product_id.required}")
        private Long productId;

        /**
         * 该类型入库重量 kg（必填，&gt; 0）。
         */
        @NotNull(message = "{burn.product_weight.required}")
        @DecimalMin(value = "0.001", message = "{burn.product_weight.positive}")
        private BigDecimal weight;

    }

}
