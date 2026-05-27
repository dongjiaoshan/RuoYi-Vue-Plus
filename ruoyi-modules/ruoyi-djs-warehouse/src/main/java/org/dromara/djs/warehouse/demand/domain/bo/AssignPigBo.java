package org.dromara.djs.warehouse.demand.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 指定猪只入参（WMS-DEMAND-001，白条业态用）。
 *
 * <p>支持批量：admin 端 dialog 一次提交多个耳号；service 端 INSERT IGNORE 防重复
 * （UNIQUE(tenant_id, demand_id, ear_no, del_unique) 兜底）。</p>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
@Data
public class AssignPigBo {

    /** 耳号清单（至少 1 个）。 */
    @NotEmpty(message = "{demand.assign_pig.ear_nos.required}")
    private List<@Size(min = 1, max = 32) String> earNos;
}
