package org.dromara.djs.warehouse.cut.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 阶段 3 出库完成 BO（WMS-PIG-002）。
 *
 * <p>service 同事务 3 步：</p>
 * <ol>
 *   <li>SELECT cut_record，校验 cut_status='cutting'</li>
 *   <li>UPDATE cut_record SET cut_status='done' / cut_done_time / drip_loss / acid_remove_minutes</li>
 *   <li>UPDATE bar_info SET status='cut_done' / out_time / out_weight=pickup_weight /
 *       out_method=2 / acid_remove_time / acid_remove_loss=drip_loss</li>
 * </ol>
 *
 * <p>滴水损耗由 service 自动计算（白条入库重量 in_weight − 出库重量 pickup_weight），
 * 前端不再录入，故本 BO 不含 dripLoss 字段。</p>
 *
 * @author djs
 * @since WMS-PIG-002
 */
@Data
public class PigCutDoneBo {

    /**
     * 分割记录 ID（cut_record.id）。
     */
    @NotNull(message = "{cut.cut_record_id.required}")
    private Long cutRecordId;

    /**
     * 凭证图 OSS IDs CSV（可选）。
     */
    @Size(max = 500, message = "{cut.proof_oss_ids.size}")
    private String proofOssIds;

    /**
     * 备注（可选）。
     */
    @Size(max = 500, message = "{cut.remark.size}")
    private String remark;

}
