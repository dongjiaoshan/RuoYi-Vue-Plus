package org.dromara.djs.warehouse.demand.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.warehouse.demand.domain.DemandPig;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 需求↔猪只关联 VO（WMS-DEMAND-001）。
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
@Data
@AutoMapper(target = DemandPig.class)
public class DemandPigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long demandId;

    private String earNo;

    private LocalDateTime assignedAt;

    private Long assignedBy;

    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "assignedBy")
    private String assignedByName;
}
