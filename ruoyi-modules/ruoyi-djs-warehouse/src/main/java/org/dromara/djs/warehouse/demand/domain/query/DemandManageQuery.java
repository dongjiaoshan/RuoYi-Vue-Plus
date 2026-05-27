package org.dromara.djs.warehouse.demand.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDate;

/**
 * 需求列表查询参数（WMS-DEMAND-001）。
 *
 * <p>4 业态列表共享本 Query；admin 端各业态 .vue 在调 API 时把 {@code productType}
 * 写死本业态字面量（如 {@code 'white_bar'}），实现"4 业态 4 个独立列表页 + 共用 API"。</p>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DemandManageQuery extends BaseEntity {

    /** 单号 LIKE 中部匹配。 */
    private String demandNo;

    /** 业态过滤；admin 4 业态列表页固定值（white_bar / vegetable / gift_box / other）。 */
    private String productType;

    /** 状态过滤（单选）。 */
    private String demandStatus;

    /** 门店 ID。 */
    private Long storeId;

    /** 需求日期起始。 */
    private LocalDate beginDate;

    /** 需求日期截止。 */
    private LocalDate endDate;
}
