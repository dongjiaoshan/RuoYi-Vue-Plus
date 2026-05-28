package org.dromara.djs.warehouse.veg.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * mp 端提交处理记录 BO（WMS-VEG-001）。
 *
 * <p>事务一次写入：</p>
 * <ol>
 *   <li>找到 / 创建 vegetable_handle 汇总行（按 planting_record_id 唯一）</li>
 *   <li>INSERT handle_record（record_type / record_weight / handle_target / location_id）</li>
 *   <li>聚合 UPDATE vegetable_handle（picked / handled / feed / stock_in / loss / send_platform）</li>
 *   <li>条件 INSERT stock_flow（handle_target=1 入库时）</li>
 *   <li>同步 planting_record.handle_status（pending → processing；isFinish=1 时 → done）</li>
 * </ol>
 *
 * <p>跨层契约 §1：snowflake Long 字段全链路 string（前端 mp 必传 string，Jackson 自动反序列化为 Long）。</p>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Data
public class HandleRecordSubmitBo {

    /**
     * 上游种植记录 ID（FK → t_warehouse_planting_record.id）。
     */
    @NotNull(message = "{veg.planting_record_id.required}")
    private Long plantingRecordId;

    /**
     * 记录类型 djs_record_type：1=采收 / 2=处理。
     */
    @NotNull(message = "{veg.record_type.required}")
    private Integer recordType;

    /**
     * 本次重量(kg)，> 0。
     */
    @NotNull(message = "{veg.record_weight.required}")
    @DecimalMin(value = "0.001", message = "{veg.record_weight.positive}")
    private BigDecimal recordWeight;

    /**
     * 处理目标 djs_handle_target：1=入库 / 2=月台 / 3=饲料（record_type=2 时必填，service 校验）。
     */
    private Integer handleTarget;

    /**
     * 入库库位 ID（FK → t_warehouse_location_info.id）。handle_target=1 时必填（service 校验）。
     */
    private Long locationId;

    /**
     * 是否完成（mp 显式提交 finish 时传 1，会将 vegetable_handle + planting_record 推进至 done）。
     */
    private Integer isFinish;

    /**
     * 凭证图 OSS IDs CSV（biz_type=warehouse_veg_handle）。
     */
    @Size(max = 500)
    private String proofOssIds;

    /**
     * 备注。
     */
    @Size(max = 500, message = "{veg.remark.size}")
    private String remark;

}
