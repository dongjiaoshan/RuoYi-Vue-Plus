package org.dromara.djs.warehouse.pack.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 生产记录标损 / 修改损坏信息 BO（DENGBO-DAMAGE-001，契约 b）。
 *
 * <p>仓库产品生产页对一条逐件生产记录标记「损坏」：置 {@code is_damaged=1} + 写损坏凭证图 / 备注 /
 * 标损时间（标损时间由 service 端取 now，不由前端传）。可重复提交修改凭证图 / 备注。</p>
 *
 * @author djs
 * @since DENGBO-DAMAGE-001
 */
@Data
public class MarkDamageBo {

    /**
     * 生产记录 ID（FK → {@code t_warehouse_product_production.id}）。
     */
    @NotNull(message = "{pack.damage.id.required}")
    private Long id;

    /**
     * 损坏凭证图 OSS IDs CSV（可选）。
     */
    @Size(max = 500, message = "{pack.damage.evidence.size}")
    private String evidenceOssIds;

    /**
     * 损坏备注（可选）。
     */
    @Size(max = 500, message = "{pack.damage.remark.size}")
    private String remark;

}
