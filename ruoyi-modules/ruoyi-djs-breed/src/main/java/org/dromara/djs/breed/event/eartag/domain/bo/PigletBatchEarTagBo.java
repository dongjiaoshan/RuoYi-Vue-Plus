package org.dromara.djs.breed.event.eartag.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量贴耳标入参（BRD-EVENT-003）。
 *
 * <p>一次 transaction 内为某个 {@code farrowId} 创建 N 头仔猪 pig_info + N 条 pigletno 日志；
 * 任一失败全回滚。{@code piglets.size() + 已贴数 ≤ farrow.live_born}，service 强制校验。</p>
 *
 * @author djs
 * @since BRD-EVENT-003
 */
@Data
public class PigletBatchEarTagBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联分娩记录 ID。 */
    @NotNull(message = "{pigletno.farrow_id.required}")
    private Long farrowId;

    /** 打标人员 userId（可空；空则后端回落当前登录态）。 */
    private Long operatorId;

    /** 本批贴标的仔猪列表（每头一条 item）。 */
    @NotEmpty(message = "{pigletno.piglets.required}")
    @Size(max = 50, message = "{pigletno.piglets.too_many}")
    @Valid
    private List<PigletEarTagItem> piglets;
}
