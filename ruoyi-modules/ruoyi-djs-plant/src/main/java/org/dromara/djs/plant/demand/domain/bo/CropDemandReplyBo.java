package org.dromara.djs.plant.demand.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 需求回复入参 BO（V6-R153 种植端「回复」弹框）。
 *
 * <p>首次回复与修改回复走同一端点（甲方：无论什么状态都是回复，已回复支持后续修改）。</p>
 *
 * @author djs
 * @since V6-R153
 */
@Data
public class CropDemandReplyBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 需求 ID。 */
    @NotNull(message = "{plant.demand.id.required}")
    private Long id;

    /** 回复内容。 */
    @NotBlank(message = "{plant.demand.reply.required}")
    @Size(max = 1000, message = "{plant.demand.reply.size}")
    private String replyContent;
}
