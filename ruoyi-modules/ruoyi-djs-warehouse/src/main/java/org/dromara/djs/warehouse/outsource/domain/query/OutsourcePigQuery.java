package org.dromara.djs.warehouse.outsource.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.util.Date;

/**
 * 外购猪只列表查询入参（DJS-FIX-WMS-RALN）。
 *
 * @author djs
 * @since DJS-FIX-WMS-RALN
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OutsourcePigQuery extends BaseEntity {

    /**
     * 购买日期（按天精确匹配）。
     */
    private Date purchaseDate;

    /**
     * 到场时间（按天精确匹配）。
     */
    private Date arriveTime;

    /**
     * 供应商 ID（精确匹配）。
     */
    private Long supplierId;

    /**
     * 购买人（sys_user.user_id，精确匹配）。
     */
    private String buyer;

}
