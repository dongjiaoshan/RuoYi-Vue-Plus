package org.dromara.djs.warehouse.burn.domain.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.warehouse.burn.domain.PigBurnRecord;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 燎毛工序记录入参 BO（WMS-PIG-001）。
 *
 * <p>本 BO 用于 mp 端提交燎毛记录。Service 层处理流程：</p>
 * <ol>
 *   <li>校验 {@code earNo} 存在且 {@code current_status='END'}</li>
 *   <li>生成 {@code burnId} = {@code BURN+YYMMDD+4 位序号} + 计算 {@code lossWeight}</li>
 *   <li>INSERT 燎毛记录（burn_status='done'）</li>
 *   <li>UPDATE {@code t_warehouse_location_stock} SET {@code product_stock -= burnWeight} WHERE
 *       {@code ear_no/location_id} 匹配且 {@code product_stock ≥ burnWeight}（行锁，affectedRows==0 抛"库存不足"）</li>
 *   <li>INSERT {@code t_warehouse_stock_flow}（出库流水，flow_type='slaughter_burn'）</li>
 * </ol>
 *
 * <p>4 步同一 {@code @Transactional}，任一失败整体回滚。</p>
 *
 * @author djs
 * @since WMS-PIG-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PigBurnRecord.class, reverseConvertGenerate = false)
public class PigBurnRecordBo extends BaseEntity {

    /**
     * 燎毛记录 ID（编辑场景留空；本 ticket V1 mp 仅 submit，不开放 update）。
     */
    private Long id;

    /**
     * 猪只耳号（关联 {@code t_farm_pig_info.ear_no}；current_status 必须为 END）。
     *
     * <p>跨层契约 1：snowflake ID 走 string，业务实体走 earNo（业务码）。</p>
     */
    @NotBlank(message = "{burn.ear_no.required}")
    @Size(max = 32, message = "{burn.ear_no.size}")
    private String earNo;

    /**
     * 燎毛时间（mp 端默认 NOW）。
     */
    @NotNull(message = "{burn.burn_time.required}")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date burnTime;

    /**
     * 到场重量 kg（必填，&gt; 0）。
     */
    @NotNull(message = "{burn.arrive_weight.required}")
    @DecimalMin(value = "0.001", message = "{burn.arrive_weight.positive}")
    private BigDecimal arriveWeight;

    /**
     * 燎毛后重量 kg（必填，&gt; 0 且 ≤ arriveWeight；service 校验差值）。
     */
    @NotNull(message = "{burn.burn_weight.required}")
    @DecimalMin(value = "0.001", message = "{burn.burn_weight.positive}")
    private BigDecimal burnWeight;

    /**
     * 入白条库的库位 ID（必填）。
     */
    @NotNull(message = "{burn.location_id.required}")
    private Long locationId;

    /**
     * 凭证图 OSS IDs CSV（可选）。
     *
     * <p>{@code CameraUploadWithWatermark} 上传后 mp 端 join 成 CSV。</p>
     */
    @Size(max = 500, message = "{burn.proof_oss_ids.size}")
    private String proofOssIds;

    /**
     * 备注（可选）。
     */
    @Size(max = 500, message = "{burn.remark.size}")
    private String remark;

}
