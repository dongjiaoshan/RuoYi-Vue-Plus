package org.dromara.djs.warehouse.demand.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 需求↔猪只关联实体（WMS-DEMAND-001，白条业态专用）。
 *
 * <p>耳号字段 {@code ear_no} 是业务码字符串（不是 pig_id snowflake），与 D7 BRD-LIST-001
 * PigSearchVo 对齐 — mp 端燎毛 / 分割工序通过耳号反查 demand。</p>
 *
 * <p>UNIQUE(tenant_id, demand_id, ear_no, del_unique) 保证同一需求不能重复指定同一头猪。</p>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_demand_pig")
public class DemandPig extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** FK {@code t_warehouse_demand_manage.id}。 */
    private Long demandId;

    /** 耳号（关联 {@code t_farm_pig_info.ear_no}）。 */
    private String earNo;

    /** 指定时间。 */
    private LocalDateTime assignedAt;

    /** 指定人，FK {@code sys_user.user_id}。 */
    private Long assignedBy;

    @TableLogic
    private String delFlag;

    private Long delUnique;
}
